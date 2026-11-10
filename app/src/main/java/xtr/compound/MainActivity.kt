package xtr.compound

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowInsets
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import xtr.compound.databinding.ActivityFullscreenBinding
import xtr.compound.server.RemoteServiceHelper
import xtr.compound.server.RunningTask
import java.util.concurrent.Executors

/** Desktop UI; Binder work runs on workers and is reconciled on the main thread. */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFullscreenBinding
    private lateinit var windows: DesktopWindowController
    private lateinit var appearance: AppearanceSettings
    private lateinit var taskbarBlur: LiveBackgroundBlur
    private lateinit var legacyMenuBlur: LegacyBackgroundBlur
    private lateinit var legacyTaskbarBlur: LegacyBackgroundBlur
    private lateinit var menuBlur: LiveBackgroundBlur
    private lateinit var taskbar: RunningTasksAdapter
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
    private var windowCount = 0
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
        windows = DesktopWindowController(this, binding.appsContainer)
        windows.setOnWindowsChanged(Runnable {
            val count = windows.getWindows().size
            if (count > windowCount) binding.startMenu.root.visibility = View.INVISIBLE
            windowCount = count
            renderTaskbar(); refreshTasks()
            if (::legacyTaskbarBlur.isInitialized) legacyTaskbarBlur.refresh()
        })
        binding.startMenu.appsList.adapter = AppsGridAdapter(this, R.layout.app_list_item, windows)
        binding.startMenu.appsGrid.adapter = AppsGridAdapter(this, R.layout.app_grid_item, windows)
        taskbar = RunningTasksAdapter(this, ::focusEntry, ::closeEntry)
        binding.taskbar.taskbarApps.adapter = taskbar
        binding.taskbar.root.visibility = View.VISIBLE
        binding.taskbar.root.setBackgroundColor(Color.TRANSPARENT)
        binding.startMenu.root.setCardBackgroundColor(Color.TRANSPARENT)
        legacyTaskbarBlur = LegacyBackgroundBlur(binding.taskbarBackground, binding.taskbar.root, window)
        legacyMenuBlur = LegacyBackgroundBlur(binding.startMenu.background, binding.startMenu.root, window)
        // These surfaces are views in this Activity window, so a cross-window blur drawable has no
        // separate underlying window to sample. PixelCopy+RenderEffect captures the hosted desktop.
        taskbarBlur = LiveBackgroundBlur(
            binding.taskbarBackground, legacy = legacyTaskbarBlur, allowLiveBlur = false)
        menuBlur = LiveBackgroundBlur(
            binding.startMenu.background, 40f, legacyMenuBlur, allowLiveBlur = false)
        binding.taskbarBackground.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) legacyTaskbarBlur.refresh()
        }
        binding.startMenu.background.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) legacyMenuBlur.refresh()
        }
        taskbarBlur.onAvailabilityChanged = { refreshAppearanceAvailability() }
        menuBlur.onAvailabilityChanged = { refreshAppearanceAvailability() }
        applyAppearance()
        binding.appsContainer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val density = resources.displayMetrics.density
            val compact = binding.root.width / density < 720
            (binding.taskbar.imageButton4.parent as View).visibility = if (compact) View.GONE else View.VISIBLE
            listOf(binding.taskbar.startMenuButton, binding.taskbar.backButton,
                binding.taskbar.homeButton, binding.taskbar.recentsButton).forEachIndexed { index, button ->
                val desired = ((if (index == 0) 60 else 48) * density).toInt()
                if (button.layoutParams.width != desired) {
                    button.layoutParams = button.layoutParams.apply { width = desired }
                }
            }
            val params = binding.startMenu.root.layoutParams
            val width = minOf((620 * density).toInt(), (binding.root.width - 20 * density).toInt()).coerceAtLeast(1)
            val height = minOf((580 * density).toInt(), (binding.appsContainer.height - 20 * density).toInt()).coerceAtLeast(1)
            if (params.width != width || params.height != height) {
                params.width = width; params.height = height
                binding.startMenu.root.layoutParams = params
            }
        }
        setupActions()
        RemoteServiceHelper.addStateListener(stateListener)
        RemoteServiceHelper.initialize(applicationContext)
    }

    private fun setupActions() {
        binding.taskbar.startMenuButton.imageTintList = binding.startMenu.powerButton.iconTint
        binding.taskbar.startMenuButton.setOnClickListener {
            binding.startMenu.root.visibility = if (binding.startMenu.root.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
            legacyMenuBlur.refresh()
        }
        binding.taskbar.startMenuButton.setOnLongClickListener { showSetup(); true }
        binding.startMenu.settingsButton.setOnClickListener {
            CompoundSettingsDialog.show(this, appearance, {
                getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
            }, {
                applyAppearance(); renderTaskbar(); refreshTasks()
            }, ::showSetup)
        }
        binding.startMenu.settingsButton.setOnClickListener { launchSafely(Intent(Settings.ACTION_SETTINGS)) }
        binding.startMenu.folderButton.setOnClickListener {
            launchSafely(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            })
        }
        binding.startMenu.powerButton.setOnClickListener { withService { it.showPowerMenu() } }
        binding.taskbar.homeButton.setOnClickListener { windows.minimizeAll(); binding.startMenu.root.visibility = View.INVISIBLE }
        binding.taskbar.backButton.setOnClickListener {
            val target = windows.getWindows().lastOrNull { !it.minimized }
            if (target == null) binding.startMenu.root.visibility = View.INVISIBLE
            else withService { remote ->
                remote.injectInputEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK), target.displayId)
                remote.injectInputEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK), target.displayId)
            }
        }
        binding.taskbar.recentsButton.setOnClickListener { refreshTasks(); binding.taskbar.taskbarApps.smoothScrollToPosition(0) }
    }

    private fun applyAppearance() {
        taskbarBlur.update(appearance.opacityPercent, appearance.blurRadiusDp)
        menuBlur.update(appearance.opacityPercent, appearance.blurRadiusDp)
    }

    private fun refreshAppearanceAvailability() {
        if (!destroyed) CompoundSettingsDialog.refreshAvailability()
    }

    private fun onServiceState(state: RemoteServiceHelper.State) {
        if (destroyed) return
        binding.taskbar.startMenuButton.tooltipText = RemoteServiceHelper.getStatusMessage()
        binding.startMenu.powerButton.isEnabled = state == RemoteServiceHelper.State.CONNECTED
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
        if (!::taskbar.isInitialized) return
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
        taskbar.submit(entries)
    }

    private fun focusEntry(entry: TaskbarEntry) {
        if (entry.hosted) windows.focusDisplay(entry.displayId)
        else entry.taskId?.let { id -> withService { if (!it.focusTask(id)) error(getString(R.string.task_is_no_longer_available)) } }
        binding.startMenu.root.visibility = View.INVISIBLE
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

    private fun launchSafely(intent: Intent) {
        try { startActivity(intent) } catch (_: Exception) { message(getString(R.string.no_app_can_open_this_action)) }
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
