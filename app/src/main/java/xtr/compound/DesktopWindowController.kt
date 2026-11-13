package xtr.compound

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.Gravity
import android.view.PointerIcon
import android.view.Surface
import android.util.Log
import android.widget.Toast
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.core.view.ViewCompat
import xtr.compound.databinding.AppWindowBinding
import xtr.compound.server.RemoteServiceHelper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

/** A running app represented in the desktop window list. */
data class WindowEntry(
    var displayId: Int,
    val title: CharSequence,
    val packageName: String,
    var minimized: Boolean,
    var maximized: Boolean = false
)

/** Owns app-window views and their remote virtual displays. Call UI methods on the main thread. */
class DesktopWindowController(
    private val context: Context,
    private val container: ViewGroup
) {
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "desktop-window-ipc").apply { isDaemon = true }
    }
    private val records = mutableListOf<Record>()
    private var windowsChanged: Runnable? = null
    private var focusedDisplayChanged: ((Int) -> Unit)? = null
    @Volatile private var appWindowDensityMultiplier = AppearanceSettings.DEFAULT_DENSITY_MULTIPLIER
    @Volatile private var shutDown = false

    init {
        container.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> clampNormalWindows() }
    }

    private inner class Record(
        val entry: WindowEntry,
        val binding: AppWindowBinding,
        val intent: Intent,
        val state: DesktopWindowState
    ) {
        var helper: VirtualDisplayHelper? = null
        var surface: Surface? = null
        @Volatile var closed = false
        var creating = false
        var surfaceCallback: SurfaceHolder.Callback? = null
    }

    fun setOnWindowsChanged(listener: Runnable?) { windowsChanged = listener }

    /** Applies the Configure density multiplier to all hosted and subsequently launched apps. */
    @MainThread
    fun setAppWindowDensityMultiplier(multiplier: Float) {
        val quantized = AppearanceSettings.quantizeDensityMultiplier(multiplier)
        if (appWindowDensityMultiplier == quantized) return
        appWindowDensityMultiplier = quantized
        records.filterNot { it.closed }.forEach { it.helper?.setDensityMultiplier(quantized) }
    }

    /** Reports every explicit hosted-window focus, including touches on the current top window. */
    fun setOnFocusedDisplayChanged(listener: ((Int) -> Unit)?) { focusedDisplayChanged = listener }

    fun getWindows(): List<WindowEntry> = records.filterNot { it.closed }.map { it.entry.copy() }

    @MainThread
    fun launch(intent: Intent, title: CharSequence, icon: Drawable) {
        if (shutDown) return
        val component = intent.component
        val packageName = component?.packageName ?: intent.`package` ?: return
        val safeIntent = Intent(intent)
        val binding = AppWindowBinding.inflate(android.view.LayoutInflater.from(context), container, false)
        binding.title.text = title
        binding.title.icon = icon.mutate()
        val width = minOf(dp(400), container.width.takeIf { it > 0 } ?: dp(400))
        val height = minOf(dp(600), container.height.takeIf { it > 0 } ?: dp(600))
        val state = DesktopWindowState(width, height)
        val entry = WindowEntry(pendingDisplayId.getAndDecrement(), title, packageName, false)
        val record = Record(entry, binding, safeIntent, state)
        records += record

        val params = FrameLayout.LayoutParams(width, height, Gravity.TOP or Gravity.LEFT)
        container.addView(binding.root, params)
        binding.root.post {
            if (!record.closed) {
                val lp = binding.root.layoutParams as FrameLayout.LayoutParams
                lp.leftMargin = ((container.width - binding.root.width) / 2).coerceAtLeast(0)
                lp.topMargin = ((container.height - binding.root.height) / 2).coerceAtLeast(0)
                binding.root.layoutParams = lp
                record.state.moveTo(lp.leftMargin, lp.topMargin, container.width, container.height)
                binding.root.bringToFront()
            }
        }

        binding.title.setOnTouchListener(MovableFrameLayout(binding.root))
        binding.title.setOnClickListener { focus(record) }
        binding.surfaceView.setFocusable(true)
        binding.surfaceView.isFocusableInTouchMode = true
        val surfaceCallback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (record.closed) return
                record.surface = holder.surface
                val helper = record.helper
                if (helper != null) helper.attachSurface(holder.surface) else createDisplay(record, holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                record.helper?.resizeApp(width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                record.helper?.detachSurface()
                record.surface = null
            }
        }
        record.surfaceCallback = surfaceCallback
        binding.surfaceView.holder.addCallback(surfaceCallback)

        binding.minimizeButton.setOnClickListener { minimize(record) }
        binding.maximizeButton.setOnClickListener { toggleMaximize(record) }
        binding.closeButton.setOnClickListener { close(record) }
        binding.borderLeft.setOnTouchListener(TouchToResizeListener(binding.root, TouchToResizeListener.HORIZONTAL_LEFT))
        binding.borderRight.setOnTouchListener(TouchToResizeListener(binding.root, TouchToResizeListener.HORIZONTAL_RIGHT))
        binding.borderBottom.setOnTouchListener(TouchToResizeListener(binding.root, TouchToResizeListener.VERTICAL))
        configureResizeHandles(binding, enabled = true)
        binding.root.setOnClickListener { focus(record) }
        notifyWindowsChanged()
    }

    @MainThread
    fun focusDisplay(displayId: Int) { records.firstOrNull { it.entry.displayId == displayId && !it.closed }?.let(::focus) }

    @MainThread
    fun closeDisplay(displayId: Int) { records.firstOrNull { it.entry.displayId == displayId && !it.closed }?.let(::close) }

    @MainThread
    fun minimizeAll() { records.filterNot { it.closed }.forEach(::minimize) }

    @MainThread
    fun closeAll() {
        if (shutDown) return
        records.toList().forEach(::close)
        shutDown = true
        worker.shutdown()
    }

    /** Call when the bound privileged service dies; its windows and sessions are discarded. */
    @MainThread
    fun onServiceLost() {
        records.toList().forEach { close(it, releaseRemoteDisplay = false) }
    }

    private fun createDisplay(record: Record, surface: Surface) {
        if (shutDown || record.closed || record.creating || !surface.isValid) return
        record.creating = true
        RemoteServiceHelper.getInstance(context, object : RemoteServiceHelper.RemoteServiceConnectionCallback {
            override fun onServiceUnavailable(state: RemoteServiceHelper.State, message: String) {
                record.creating = false
                if (!record.closed) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    close(record)
                }
            }
            override fun onServiceConnected(service: IRemoteService) {
                if (shutDown || record.closed) return
                val width = record.binding.surfaceView.width
                val height = record.binding.surfaceView.height
                val densityMultiplier = appWindowDensityMultiplier
                try { worker.execute {
                    val helper = runCatching {
                        VirtualDisplayHelper.create(
                            service, width, height,
                            surface, record.intent, worker, densityMultiplier
                        )
                    }.onFailure { Log.e("CompoundWindows", "Could not launch ${record.entry.packageName}", it) }.getOrNull()
                    if (record.closed) {
                        helper?.closeAppOnCurrentThread()
                        return@execute
                    }
                    container.post {
                        record.creating = false
                        if (record.closed) {
                            helper?.closeApp()
                            return@post
                        }
                        if (helper == null) {
                            record.binding.root.contentDescription = "Could not start ${record.entry.title}"
                            Toast.makeText(context, "Could not start ${record.entry.title}", Toast.LENGTH_SHORT).show()
                            close(record)
                            return@post
                        }
                        record.helper = helper
                        helper.setDensityMultiplier(appWindowDensityMultiplier)
                        val currentSurface = record.surface
                        if (record.entry.minimized || currentSurface == null || !currentSurface.isValid) helper.detachSurface()
                        else if (currentSurface !== surface) helper.attachSurface(currentSurface)
                        helper.resizeApp(record.binding.surfaceView.width, record.binding.surfaceView.height)
                        record.entry.displayId = helper.displayId
                        record.binding.surfaceView.setOnTouchListener { view, event ->
                            if (event.actionMasked == MotionEvent.ACTION_DOWN) focus(record)
                            helper.onTouch(view, event)
                        }
                        record.binding.surfaceView.setOnGenericMotionListener(helper)
                        record.binding.surfaceView.setOnKeyListener(helper)
                        notifyWindowsChanged()
                    }
                } } catch (_: RejectedExecutionException) { }
            }
        })
    }

    private fun focus(record: Record) {
        if (record.closed) return
        if (record.entry.minimized) restore(record)
        records.remove(record)
        records.add(record)
        record.binding.root.bringToFront()
        record.binding.surfaceView.requestFocus()
        focusedDisplayChanged?.invoke(record.entry.displayId)
        notifyWindowsChanged()
    }

    private fun minimize(record: Record) {
        if (record.closed || record.entry.minimized) return
        record.state.minimize()
        record.entry.minimized = true
        record.helper?.detachSurface()
        record.binding.root.visibility = View.GONE
        notifyWindowsChanged()
    }

    private fun restore(record: Record) {
        if (record.closed || !record.entry.minimized) return
        record.state.restore()
        record.entry.minimized = false
        record.binding.root.visibility = View.VISIBLE
        record.binding.root.bringToFront()
        record.surface?.takeIf { it.isValid }?.let { record.helper?.attachSurface(it) }
        notifyWindowsChanged()
    }

    private fun toggleMaximize(record: Record) {
        if (record.closed) return
        val root = record.binding.root
        if (!record.state.maximized) {
            record.state.resizeTo(root.width, root.height, container.width, container.height)
            record.state.moveTo(root.x.toInt(), root.y.toInt(), container.width, container.height)
            record.state.toggleMaximized(container.width, container.height)
            val lp = root.layoutParams as FrameLayout.LayoutParams
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            lp.leftMargin = record.state.x
            lp.topMargin = record.state.y
            root.translationX = 0f
            root.translationY = 0f
            root.layoutParams = lp
            root.bringToFront()
        } else {
            record.state.toggleMaximized(container.width, container.height)
            val lp = root.layoutParams as FrameLayout.LayoutParams
            lp.width = record.state.width
            lp.height = record.state.height
            lp.leftMargin = record.state.x
            lp.topMargin = record.state.y
            root.translationX = 0f
            root.translationY = 0f
            root.layoutParams = lp
        }
        record.entry.maximized = record.state.maximized
        bindingMaximizeLabel(record)
        setWindowManipulationEnabled(record, !record.state.maximized)
        notifyWindowsChanged()
    }

    private fun close(record: Record, releaseRemoteDisplay: Boolean = true) {
        if (record.closed) return
        record.closed = true
        record.state.close()
        if (releaseRemoteDisplay) record.helper?.closeApp()
        record.helper = null
        record.entry.minimized = true
        record.binding.surfaceView.setOnTouchListener(null)
        record.binding.surfaceView.setOnGenericMotionListener(null)
        record.binding.surfaceView.setOnKeyListener(null)
        record.surfaceCallback?.let(record.binding.surfaceView.holder::removeCallback)
        record.surfaceCallback = null
        record.binding.root.visibility = View.GONE
        record.binding.root.parent?.let { (it as? ViewGroup)?.removeView(record.binding.root) }
        records.remove(record)
        notifyWindowsChanged()
    }

    private fun setWindowManipulationEnabled(record: Record, enabled: Boolean) {
        record.binding.title.setOnTouchListener(if (enabled) MovableFrameLayout(record.binding.root) else null)
        record.binding.borderLeft.setOnTouchListener(if (enabled) TouchToResizeListener(record.binding.root, TouchToResizeListener.HORIZONTAL_LEFT) else null)
        record.binding.borderRight.setOnTouchListener(if (enabled) TouchToResizeListener(record.binding.root, TouchToResizeListener.HORIZONTAL_RIGHT) else null)
        record.binding.borderBottom.setOnTouchListener(if (enabled) TouchToResizeListener(record.binding.root, TouchToResizeListener.VERTICAL) else null)
        configureResizeHandles(record.binding, enabled)
    }

    private fun configureResizeHandles(binding: AppWindowBinding, enabled: Boolean) {
        val handles = listOf(
            binding.borderLeft,
            binding.borderRight,
            binding.borderBottom,
            binding.resizeBottomLeft,
            binding.resizeBottomRight,
            binding.resizeTopLeft,
            binding.resizeTopRight
        )
        handles.forEach { handle ->
            handle.isClickable = false
            handle.isFocusable = enabled
            handle.importantForAccessibility = if (enabled) {
                View.IMPORTANT_FOR_ACCESSIBILITY_YES
            } else {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }
        binding.borderLeft.pointerIcon = if (enabled) PointerIcon.getSystemIcon(context, PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW) else null
        binding.borderRight.pointerIcon = if (enabled) PointerIcon.getSystemIcon(context, PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW) else null
        binding.borderBottom.pointerIcon = if (enabled) PointerIcon.getSystemIcon(context, PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW) else null
        binding.resizeBottomLeft.pointerIcon = if (enabled) PointerIcon.getSystemIcon(context, PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW) else null
        binding.resizeBottomRight.pointerIcon = if (enabled) PointerIcon.getSystemIcon(context, PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW) else null
        binding.resizeTopLeft.pointerIcon = if (enabled) PointerIcon.getSystemIcon(context, PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW) else null
        binding.resizeTopRight.pointerIcon = if (enabled) PointerIcon.getSystemIcon(context, PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW) else null

        binding.resizeBottomLeft.setOnTouchListener(if (enabled) TouchToResizeListener(binding.root, TouchToResizeListener.BOTTOM_LEFT) else null)
        binding.resizeBottomRight.setOnTouchListener(if (enabled) TouchToResizeListener(binding.root, TouchToResizeListener.BOTTOM_RIGHT) else null)
        binding.resizeTopLeft.setOnTouchListener(if (enabled) TouchToResizeListener(binding.root, TouchToResizeListener.TOP_LEFT) else null)
        binding.resizeTopRight.setOnTouchListener(if (enabled) TouchToResizeListener(binding.root, TouchToResizeListener.TOP_RIGHT) else null)
        handles.forEach { handle ->
            ViewCompat.setAccessibilityDelegate(handle, null)
            if (enabled) {
                when (handle) {
                    binding.borderLeft -> addResizeActions(handle, binding.root, TouchToResizeListener.HORIZONTAL_LEFT, width = true)
                    binding.borderRight -> addResizeActions(handle, binding.root, TouchToResizeListener.HORIZONTAL_RIGHT, width = true)
                    binding.borderBottom -> addResizeActions(handle, binding.root, TouchToResizeListener.VERTICAL, width = false)
                    binding.resizeBottomLeft -> addResizeActions(handle, binding.root, TouchToResizeListener.BOTTOM_LEFT, width = true, height = true)
                    binding.resizeBottomRight -> addResizeActions(handle, binding.root, TouchToResizeListener.BOTTOM_RIGHT, width = true, height = true)
                    binding.resizeTopLeft -> addResizeActions(handle, binding.root, TouchToResizeListener.TOP_LEFT, width = true, height = true)
                    binding.resizeTopRight -> addResizeActions(handle, binding.root, TouchToResizeListener.TOP_RIGHT, width = true, height = true)
                }
            }
        }
        binding.borderLeft.contentDescription = context.getString(R.string.resize_window_left_edge)
        binding.borderRight.contentDescription = context.getString(R.string.resize_window_right_edge)
        binding.borderBottom.contentDescription = context.getString(R.string.resize_window_bottom_edge)
    }

    private fun addResizeActions(handle: View, window: View, type: Int, width: Boolean, height: Boolean = false) {
        val step = dp(32)
        fun add(label: Int, dx: Int, dy: Int) {
            ViewCompat.addAccessibilityAction(handle, context.getString(label)) { _, _ ->
                val parent = window.parent as? ViewGroup ?: return@addAccessibilityAction false
                val bounds = WindowResizeGeometry.resize(
                    type, window.x, window.y, window.width, window.height, dx, dy,
                    parent.width, parent.height, TouchToResizeListener.MIN_SIZE
                )
                val lp = window.layoutParams as? FrameLayout.LayoutParams ?: return@addAccessibilityAction false
                if (bounds.width == lp.width && bounds.height == lp.height &&
                    bounds.x == lp.leftMargin && bounds.y == lp.topMargin
                ) return@addAccessibilityAction false
                lp.width = bounds.width
                lp.height = bounds.height
                lp.leftMargin = bounds.x
                lp.topMargin = bounds.y
                window.translationX = 0f
                window.translationY = 0f
                window.layoutParams = lp
                parent.requestLayout()
                true
            }
        }
        if (width) {
            val left = type == TouchToResizeListener.HORIZONTAL_LEFT || type == TouchToResizeListener.BOTTOM_LEFT || type == TouchToResizeListener.TOP_LEFT
            add(R.string.increase_window_width, if (left) -step else step, 0)
            add(R.string.decrease_window_width, if (left) step else -step, 0)
        }
        if (height) {
            val top = type == TouchToResizeListener.TOP_LEFT || type == TouchToResizeListener.TOP_RIGHT
            add(R.string.increase_window_height, 0, if (top) -step else step)
            add(R.string.decrease_window_height, 0, if (top) step else -step)
        } else if (!width) {
            add(R.string.increase_window_height, 0, step)
            add(R.string.decrease_window_height, 0, -step)
        }
    }

    private fun bindingMaximizeLabel(record: Record) {
        record.binding.maximizeButton.setIconResource(
            if (record.state.maximized) R.drawable.window_restore else R.drawable.window_maximized
        )
        record.binding.maximizeButton.contentDescription = context.getString(
            if (record.state.maximized) R.string.restore_window else R.string.maximize_window
        )
    }

    private fun clampNormalWindows() {
        if (container.width <= 0 || container.height <= 0) return
        records.filter { !it.closed && !it.state.maximized }.forEach { record ->
            val root = record.binding.root
            val width = root.width.takeIf { it > 0 } ?: record.state.width
            val height = root.height.takeIf { it > 0 } ?: record.state.height
            record.state.resizeTo(width, height, container.width, container.height)
            record.state.moveTo(root.x.toInt(), root.y.toInt(), container.width, container.height)
            val lp = root.layoutParams as? FrameLayout.LayoutParams ?: return@forEach
            if (lp.width != record.state.width || lp.height != record.state.height ||
                lp.leftMargin != record.state.x || lp.topMargin != record.state.y ||
                root.translationX != 0f || root.translationY != 0f) {
                lp.width = record.state.width
                lp.height = record.state.height
                lp.leftMargin = record.state.x
                lp.topMargin = record.state.y
                root.translationX = 0f
                root.translationY = 0f
                root.layoutParams = lp
            }
        }
    }

    private fun notifyWindowsChanged() { windowsChanged?.run() }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private val pendingDisplayId = AtomicInteger(-1)
    }
}
