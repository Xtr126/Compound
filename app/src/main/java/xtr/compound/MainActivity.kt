package xtr.compound

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.provider.AlarmClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowInsets
import android.widget.Toast
import android.widget.ImageView
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import xtr.compound.databinding.ActivityFullscreenBinding
import xtr.compound.server.RemoteServiceHelper
import xtr.compound.server.RunningTask
import java.util.concurrent.Executors
import androidx.core.graphics.drawable.toDrawable

/** Desktop UI; Binder work runs on workers and is reconciled on the main thread. */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFullscreenBinding
    private lateinit var windows: DesktopWindowController
    private lateinit var appearance: AppearanceSettings
    private lateinit var taskbarBlur: LiveBackgroundBlur
    private lateinit var legacyMenuBlur: LegacyBackgroundBlur
    private lateinit var legacyTaskbarBlur: LegacyBackgroundBlur
    private lateinit var menuBlur: LiveBackgroundBlur
    private var taskbarEntries by mutableStateOf(emptyList<TaskbarEntry>())
    private var taskbarIconsOnly by mutableStateOf(false)
    private var launcherDensityMultiplier by mutableFloatStateOf(AppearanceSettings.DEFAULT_DENSITY_MULTIPLIER)
    private var taskbarServiceStatus by mutableStateOf<String?>(null)
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var service: IRemoteService? = null
    private var tasks = emptyList<RunningTask>()
    private var resumed = false
    private var refreshing = false
    private var destroyed = false
    private var onboardingShown = false
    private var setupDialog: AlertDialog? = null
    private var taskErrorShown = false
    private var startMenuPowerEnabled by mutableStateOf(false)
    private lateinit var startMenuResizeHandle: ImageView
    private var windowCount = 0
    private var activeTaskKey by mutableStateOf<String?>(null)
    private var lastFocusedHostedKey: String? = null
    private val missingTasks = mutableMapOf<Int, Int>()
    private val stateListener = RemoteServiceHelper.StateListener { state -> onServiceState(state) }
    private val poll = object : Runnable {
        override fun run() {
            if (!resumed) return
            refreshTasks()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        onboardingShown = savedInstanceState?.getBoolean("onboardingShown") ?: false
        appearance = AppearanceSettings(this)
        launcherDensityMultiplier = appearance.launcherDensityMultiplier
        windows = DesktopWindowController(this, binding.appsContainer)
        windows.setAppWindowDensityMultiplier(appearance.appWindowDensityMultiplier)
        windows.setOnFocusedDisplayChanged { displayId ->
            val key = "display:$displayId"
            activeTaskKey = key
        }
        windows.setOnWindowsChanged(Runnable {
            val count = windows.getWindows().size
            if (count > windowCount) binding.startMenu.visibility = View.INVISIBLE
            windowCount = count
            renderTaskbar(); refreshTasks()
            if (::legacyTaskbarBlur.isInitialized) legacyTaskbarBlur.refresh()
        })
        taskbarIconsOnly = appearance.taskbarIconsOnly
        binding.taskbar.visibility = View.VISIBLE
        binding.taskbar.setBackgroundColor(Color.TRANSPARENT)
        legacyTaskbarBlur = LegacyBackgroundBlur(binding.taskbarBackground, binding.taskbar, window)
        legacyMenuBlur = LegacyBackgroundBlur(binding.background, binding.startMenu, window)
        // These surfaces are views in this Activity window, so a cross-window blur drawable has no
        // separate underlying window to sample. PixelCopy+RenderEffect captures the hosted desktop.
        taskbarBlur = LiveBackgroundBlur(
            binding.taskbarBackground, legacy = legacyTaskbarBlur, allowLiveBlur = false)
        menuBlur = LiveBackgroundBlur(
            binding.background, 40f, legacyMenuBlur, allowLiveBlur = false)
        binding.taskbarBackground.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) legacyTaskbarBlur.refresh()
        }
        binding.background.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) legacyMenuBlur.refresh()
        }
        createStartMenuResizeHandle()
        applyAppearance()
        binding.appsContainer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyLauncherHostDensity()
        }
        binding.taskbar.setContent {
            DesktopTheme(launcherDensityMultiplier) {
                TaskbarContent(
                    entries = taskbarEntries,
                    iconsOnly = taskbarIconsOnly,
                    activeKey = activeTaskKey,
                    serviceStatus = taskbarServiceStatus,
                    onStart = {
                        binding.startMenu.visibility = if (binding.startMenu.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
                        legacyMenuBlur.refresh()
                    },
                    onStartLongPress = ::showSetup,
                    onBack = {
                        val target = windows.getWindows().lastOrNull { !it.minimized }
                        if (target == null) binding.startMenu.visibility = View.INVISIBLE
                        else withService { remote ->
                            remote.injectInputEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK), target.displayId)
                            remote.injectInputEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK), target.displayId)
                        }
                    },
                    onHome = { windows.minimizeAll(); binding.startMenu.visibility = View.INVISIBLE },
                    onRecents = ::refreshTasks,
                    onWifi = { launchSafely(Intent(Settings.Panel.ACTION_WIFI), Intent(Settings.ACTION_WIFI_SETTINGS)) },
                    onBattery = { launchSafely(Intent(Intent.ACTION_POWER_USAGE_SUMMARY), Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Intent(Settings.ACTION_SETTINGS)) },
                    onClock = { bottomHalf -> if (bottomHalf) openCalendar() else openClock() },
                    onNotifications = { withService { it.expandNotificationsPanel() } },
                    onFocus = ::focusEntry,
                    onClose = ::closeEntry
                )
            }
        }
        binding.startMenuContent.setContent {
            DesktopTheme(launcherDensityMultiplier) {
                StartMenuContent(
                    windows = windows,
                    powerEnabled = startMenuPowerEnabled,
                    onSettings = { launchSafely(Intent(Settings.ACTION_SETTINGS)) },
                    onAppearance = { showSettings(CompoundSettingsDialog.Section.APPEARANCE) },
                    onConfigure = { showSettings(CompoundSettingsDialog.Section.CONFIGURE) },
                    onFiles = {
                        launchSafely(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                        })
                    },
                    onPower = { withService { it.showPowerMenu() } }
                )
            }
        }
        RemoteServiceHelper.addStateListener(stateListener)
        RemoteServiceHelper.initialize(applicationContext)
    }

    private fun showSettings(section: CompoundSettingsDialog.Section) {
        CompoundSettingsDialog.show(
            activity = this,
            settings = appearance,
            onChanged = {
                applyAppearance()
                taskbarIconsOnly = appearance.taskbarIconsOnly
                renderTaskbar()
                refreshTasks()
            },
            onSetup = ::showSetup,
            onDeviceSettings = { launchSafely(Intent(Settings.ACTION_SETTINGS)) },
            section = section
        )
    }

    private fun openClock() {
        launchSafely(
            Intent(AlarmClock.ACTION_SHOW_ALARMS),
            Intent(AlarmClock.ACTION_SET_ALARM)
        )
    }

    private fun openCalendar() {
        launchSafely(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))
    }

    private fun applyAppearance() {
        taskbarBlur.update(appearance.opacityPercent, appearance.blurRadiusDp)
        menuBlur.update(appearance.opacityPercent, appearance.blurRadiusDp)
        launcherDensityMultiplier = appearance.launcherDensityMultiplier
        if (::windows.isInitialized) windows.setAppWindowDensityMultiplier(appearance.appWindowDensityMultiplier)
        if (::startMenuResizeHandle.isInitialized) {
            startMenuResizeHandle.visibility = if (appearance.startMenuResizeEnabled) View.VISIBLE else View.GONE
        }
        applyLauncherHostDensity()
    }

    private fun createStartMenuResizeHandle() {
        val density = effectiveLauncherDensity()
        startMenuResizeHandle = ImageView(this).apply {
            setImageResource(R.drawable.outline_resize_window_24)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.compoundOnSurface)
            )
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33777777),
                Color.TRANSPARENT.toDrawable(),
                null
            )
            contentDescription = getString(R.string.resize_start_menu_handle)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            visibility = if (appearance.startMenuResizeEnabled) View.VISIBLE else View.GONE
            pointerIcon = android.view.PointerIcon.getSystemIcon(
                this@MainActivity, android.view.PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW
            )
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        view.setTag(R.id.startMenuResizeGesture, intArrayOf(
                            binding.startMenu.width,
                            binding.startMenu.height,
                            event.rawX.toInt(),
                            event.rawY.toInt()
                        ))
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val start = view.getTag(R.id.startMenuResizeGesture) as? IntArray ?: return@setOnTouchListener true
                        val density = effectiveLauncherDensity()
                        val bounds = startMenuResizeBounds()
                        val size = StartMenuResizeGeometry.resize(
                            initialWidth = start[0], initialHeight = start[1],
                            deltaX = event.rawX.toInt() - start[2], deltaY = event.rawY.toInt() - start[3],
                            maxWidth = bounds.first, maxHeight = bounds.second,
                            minWidth = (AppearanceSettings.MIN_START_MENU_WIDTH_DP * density).toInt(),
                            minHeight = (AppearanceSettings.MIN_START_MENU_HEIGHT_DP * density).toInt()
                        )
                        binding.startMenu.layoutParams = binding.startMenu.layoutParams.apply {
                            width = size.width
                            height = size.height
                        }
                        appearance.startMenuWidthDp = (size.width / density).toInt()
                        appearance.startMenuHeightDp = (size.height / density).toInt()
                        true
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        view.performClick()
                        view.setTag(R.id.startMenuResizeGesture, null)
                        true
                    }
                    else -> true
                }
            }
            setOnClickListener { }
            layoutParams = FrameLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt(),
                android.view.Gravity.TOP or android.view.Gravity.END)
        }
        binding.startMenu.addView(startMenuResizeHandle)
    }

    private fun startMenuResizeBounds(): Pair<Int, Int> {
        val density = effectiveLauncherDensity()
        val baseDensity = resources.displayMetrics.density
        val scale = AppearanceSettings.quantizeDensityMultiplier(launcherDensityMultiplier)
        val maxMenuHeight = LauncherMenuGeometry.calculate(
            rootHeightPx = binding.root.height,
            workspaceHeightPx = binding.appsContainer.height,
            baseDensity = baseDensity,
            densityScale = scale,
            requestedHeightDp = AppearanceSettings.MAX_START_MENU_SIZE_DP
        ).heightPx
        return Pair(
            (binding.root.width - 20 * density).toInt().coerceAtLeast(1),
            maxMenuHeight
        )
    }

    /** Applies local launcher density to Compose and the pixel-sized Android containers. */
    private fun applyLauncherHostDensity() {
        if (!::binding.isInitialized) return
        val density = effectiveLauncherDensity()
        val taskbarParams = binding.taskbar.layoutParams
        if (taskbarParams != null) {
            val height = (60 * density).toInt().coerceAtLeast(1)
            if (taskbarParams.height != height) {
                taskbarParams.height = height
                binding.taskbar.layoutParams = taskbarParams
            }
        }
        if (::startMenuResizeHandle.isInitialized) {
            val handleParams = startMenuResizeHandle.layoutParams
            val size = (40 * density).toInt().coerceAtLeast(1)
            if (handleParams.width != size || handleParams.height != size) {
                handleParams.width = size
                handleParams.height = size
                startMenuResizeHandle.layoutParams = handleParams
            }
        }
        val rootWidth = binding.root.width
        val workspaceHeight = binding.appsContainer.height
        if (rootWidth <= 0 || workspaceHeight <= 0) return
        val baseDensity = resources.displayMetrics.density
        val scale = AppearanceSettings.quantizeDensityMultiplier(launcherDensityMultiplier)
        val horizontalMargin = (20 * density).toInt()
        val width = minOf((appearance.startMenuWidthDp * density).toInt(), (rootWidth - horizontalMargin).coerceAtLeast(1))
        val menuBounds = LauncherMenuGeometry.calculate(
            rootHeightPx = binding.root.height,
            workspaceHeightPx = workspaceHeight,
            baseDensity = baseDensity,
            densityScale = scale,
            requestedHeightDp = appearance.startMenuHeightDp
        )
        val height = menuBounds.heightPx
        val params = binding.startMenu.layoutParams as? ConstraintLayout.LayoutParams ?: return
        val margins = params as? android.view.ViewGroup.MarginLayoutParams
        var marginsChanged = false
        if (margins != null) {
            val left = (10 * density).toInt()
            val bottom = menuBounds.bottomMarginPx
            marginsChanged = margins.leftMargin != left || margins.bottomMargin != bottom
            margins.leftMargin = left
            margins.bottomMargin = bottom
        }
        val oldBottomToTop = params.bottomToTop
        val oldBottomToBottom = params.bottomToBottom
        params.bottomToTop = if (menuBounds.overlapsTaskbar) ConstraintLayout.LayoutParams.UNSET else R.id.taskbar
        params.bottomToBottom = if (menuBounds.overlapsTaskbar) ConstraintLayout.LayoutParams.PARENT_ID else ConstraintLayout.LayoutParams.UNSET
        binding.startMenu.translationZ = if (menuBounds.overlapsTaskbar) 10f * baseDensity else 0f
        if (params.width != width || params.height != height || marginsChanged) {
            params.width = width
            params.height = height
            binding.startMenu.layoutParams = params
        } else if (params.bottomToTop != oldBottomToTop || params.bottomToBottom != oldBottomToBottom) {
            binding.startMenu.layoutParams = params
        }
    }

    private fun effectiveLauncherDensity(): Float = resources.displayMetrics.density *
        AppearanceSettings.quantizeDensityMultiplier(launcherDensityMultiplier)

    private fun onServiceState(state: RemoteServiceHelper.State) {
        if (destroyed) return
        taskbarServiceStatus = RemoteServiceHelper.getStatusMessage()
        startMenuPowerEnabled = state == RemoteServiceHelper.State.CONNECTED
        if (state == RemoteServiceHelper.State.CONNECTED) {
            setupDialog?.dismiss()
            RemoteServiceHelper.getInstance(this) { connected ->
                if (!destroyed) { service = connected; taskErrorShown = false; refreshTasks() }
            }
        } else {
            if (service != null) {
                message(RemoteServiceHelper.getStatusMessage())
                windows.onServiceLost()
            }
            service = null
            tasks = emptyList()
            renderTaskbar()
            if (state != RemoteServiceHelper.State.CONNECTING && !onboardingShown) {
                onboardingShown = true
                showSetup()
            }
        }
    }

    private fun showSetup() {
        if (destroyed || isFinishing) return
        setupDialog?.dismiss()
        val state = RemoteServiceHelper.getState()
        setupDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.set_up_desktop_access)
            .setMessage(getString(R.string.compound_needs_root_or_shizuku_to_launch_and_manage_app_windows_root_is_tried_first, RemoteServiceHelper.getStatusMessage()))
            .setPositiveButton(if (state == RemoteServiceHelper.State.PERMISSION_REQUIRED || state == RemoteServiceHelper.State.PERMISSION_DENIED) "Grant access" else "Retry") { _, _ ->
                if (state == RemoteServiceHelper.State.PERMISSION_REQUIRED || state == RemoteServiceHelper.State.PERMISSION_DENIED) RemoteServiceHelper.requestPermission()
                else RemoteServiceHelper.retry(applicationContext)
            }
            .setNeutralButton(R.string.open_shizuku) { _, _ ->
                val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    ?: Intent(Intent.ACTION_VIEW, "https://shizuku.rikka.app/download/".toUri())
                launchSafely(intent)
            }
            .setNegativeButton(getString(R.string.later), null)
            .show()
    }

    private fun refreshTasks() {
        val remote = service ?: return
        if (!resumed || refreshing || destroyed) return
        refreshing = true
        worker.execute {
            val result = runCatching { remote.runningTasks.toList() }
            handler.post {
                refreshing = false
                if (!destroyed && service === remote) {
                    result.onSuccess { snapshot ->
                        tasks = snapshot
                        taskErrorShown = false
                        val activeDisplays = snapshot.map { it.displayId }.toSet()
                        val hosted = windows.getWindows().filter { it.displayId > 0 }
                        missingTasks.keys.retainAll(hosted.map { it.displayId }.toSet())
                        hosted.forEach { window ->
                            if (window.displayId in activeDisplays) missingTasks.remove(window.displayId)
                            else {
                                val misses = (missingTasks[window.displayId] ?: 0) + 1
                                missingTasks[window.displayId] = misses
                                // Allow startup/transient task transitions before removing an ended session.
                                if (misses >= 3) windows.closeDisplay(window.displayId)
                            }
                        }
                        renderTaskbar()
                    }
                        .onFailure {
                            if (!taskErrorShown) { message(
                                getString(
                                    R.string.task_list_unavailable,
                                    it.message ?: getString(R.string.unsupported_device_api)
                                )); taskErrorShown = true }
                        }
                }
            }
        }
    }

    private fun renderTaskbar() {
        val hosted = windows.getWindows()
        val entries = hosted.map { window ->
            TaskbarEntry("display:${window.displayId}", window.title.toString(), window.packageName,
                window.displayId, tasks.firstOrNull { it.displayId == window.displayId }?.taskId, true, window.minimized, window.maximized)
        }.toMutableList()
        if (appearance.showAllTasks) tasks.filter { task ->
            task.packageName != packageName && hosted.none { it.displayId == task.displayId }
        }.forEach { task ->
            entries.add(TaskbarEntry("task:${task.taskId}", task.title.ifBlank { task.packageName }, task.packageName,
                task.displayId, task.taskId, false))
        }
        val focusedHostedKey = hosted.lastOrNull { !it.minimized }?.let { "display:${it.displayId}" }
        if (focusedHostedKey != lastFocusedHostedKey) {
            lastFocusedHostedKey = focusedHostedKey
            if (focusedHostedKey != null) activeTaskKey = focusedHostedKey
            else if (activeTaskKey?.startsWith("display:") == true) activeTaskKey = null
        }
        if (activeTaskKey == null || entries.none { it.key == activeTaskKey }) {
            activeTaskKey = focusedHostedKey ?: entries.firstOrNull { !it.minimized }?.key
        }
        taskbarEntries = entries
    }

    private fun focusEntry(entry: TaskbarEntry) {
        activeTaskKey = entry.key
        if (entry.hosted) windows.focusDisplay(entry.displayId)
        else entry.taskId?.let { id -> withService { if (!it.focusTask(id)) error(getString(R.string.task_is_no_longer_available)) } }
        binding.startMenu.visibility = View.INVISIBLE
    }

    private fun closeEntry(entry: TaskbarEntry) {
        if (entry.hosted) windows.closeDisplay(entry.displayId)
        else entry.taskId?.let { id -> withService { if (!it.removeTask(id)) error(getString(R.string.task_is_no_longer_available)) } }
    }

    private fun withService(action: (IRemoteService) -> Unit) {
        val remote = service ?: run { showSetup(); return }
        worker.execute {
            val result = runCatching { action(remote) }
            handler.post {
                if (!destroyed) { result.exceptionOrNull()?.let { message(it.message ?: getString(R.string.operation_failed)) }; refreshTasks() }
            }
        }
    }

    private fun launchSafely(intent: Intent, vararg fallbacks: Intent) {
        for (candidate in listOf(intent, *fallbacks)) {
            if (runCatching { startActivity(candidate); true }.getOrDefault(false)) return
        }
        message(getString(R.string.no_app_can_open_this_action))
    }
    private fun message(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }

    override fun onResume() {
        super.onResume()
        resumed = true
        supportActionBar?.hide()
        binding.root.windowInsetsController?.hide(WindowInsets.Type.systemBars())
        handler.removeCallbacks(poll); handler.post(poll)
        RemoteServiceHelper.initialize(applicationContext)
    }
    override fun onPause() { resumed = false; handler.removeCallbacks(poll); super.onPause() }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!destroyed && ::taskbarBlur.isInitialized) {
            applyAppearance()
            legacyTaskbarBlur.refresh()
            legacyMenuBlur.refresh()
            CompoundSettingsDialog.refreshAppearance()
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("onboardingShown", onboardingShown)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        RemoteServiceHelper.removeStateListener(stateListener)
        setupDialog?.dismiss()
        CompoundSettingsDialog.dismiss()
        taskbarBlur.close(); menuBlur.close()
        windows.closeAll()
        worker.shutdown()
        super.onDestroy()
    }
}
