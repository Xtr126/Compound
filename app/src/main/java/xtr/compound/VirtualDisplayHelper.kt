package xtr.compound

import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import java.util.concurrent.Executor

/** Owns one remote display. Surface detach preserves the display and running task. */
class VirtualDisplayHelper private constructor(
    private val service: IRemoteService,
    val displayId: Int,
    private var width: Int,
    private var height: Int,
    private val dpi: Int,
    private val executor: Executor
) : View.OnTouchListener, View.OnGenericMotionListener, View.OnKeyListener {
    private var inputTransformer = InputTransformer(width, height)
    private var closed = false

    fun attachSurface(surface: Surface) {
        if (closed || displayId < 0) return
        try { executor.execute { if (!closed) runCatching { service.setVirtualDisplaySurface(displayId, surface) } } }
        catch (_: java.util.concurrent.RejectedExecutionException) { }
    }

    fun detachSurface() {
        if (closed || displayId < 0) return
        try { executor.execute { if (!closed) runCatching { service.setVirtualDisplaySurface(displayId, null) } } }
        catch (_: java.util.concurrent.RejectedExecutionException) { }
    }

    fun closeApp() {
        if (closed) return
        closed = true
        try { executor.execute { runCatching { service.releaseVirtualDisplay(displayId) } } }
        catch (_: java.util.concurrent.RejectedExecutionException) { CLEANUP_EXECUTOR.execute { runCatching { service.releaseVirtualDisplay(displayId) } } }
    }

    /** Releases immediately when called by the worker that created the display. */
    fun closeAppOnCurrentThread() {
        if (closed) return
        closed = true
        runCatching { service.releaseVirtualDisplay(displayId) }
    }

    fun resizeApp(newWidth: Int, newHeight: Int) {
        width = newWidth.coerceAtLeast(1)
        height = newHeight.coerceAtLeast(1)
        inputTransformer.updateViewSize(width, height)
        if (!closed && displayId >= 0) try {
            executor.execute { if (!closed) runCatching { service.resizeVirtualDisplay(displayId, width, height, dpi) } }
        } catch (_: java.util.concurrent.RejectedExecutionException) { }
    }

    override fun onGenericMotion(v: View, event: MotionEvent): Boolean = injectMotion(event, true)
    override fun onTouch(v: View, event: MotionEvent): Boolean = injectMotion(event, true)

    private fun injectMotion(event: MotionEvent, transform: Boolean): Boolean {
        if (closed) return true
        val copy = if (transform) inputTransformer.transformTouchEvent(event) else MotionEvent.obtain(event)
        try { executor.execute {
            try { if (!closed) service.injectInputEvent(copy, displayId) }
            catch (_: Exception) { }
            finally { copy.recycle() }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { copy.recycle() }
        return true
    }

    override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
        if (closed || event == null) return true
        val copy = KeyEvent(event)
        try { executor.execute {
            try { if (!closed) service.injectInputEvent(copy, displayId) }
            catch (_: Exception) { }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { }
        return true
    }

    companion object {
        private val CLEANUP_EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "desktop-window-cleanup").apply { isDaemon = true }
        }

        fun create(
            service: IRemoteService,
            width: Int,
            height: Int,
            surface: Surface,
            launchIntent: android.content.Intent,
            executor: Executor
        ): VirtualDisplayHelper {
            val dpi = service.getBaseDisplayDensity(Display.DEFAULT_DISPLAY)
            val id = service.renderAppToSurface(launchIntent, surface, width.coerceAtLeast(1), height.coerceAtLeast(1), dpi)
            require(id > 0) { "Could not create virtual display" }
            return VirtualDisplayHelper(service, id, width, height, dpi, executor)
        }
    }
}
