package xtr.compound

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import android.view.View
import com.google.android.material.slider.Slider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import xtr.compound.server.RemoteServiceHelper

/** Device tests exercise real preferences, surfaces and shell-privileged Binder calls. */
@RunWith(AndroidJUnit4::class)
class DesktopIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun appearancePersistsClampsAndResetPreservesTaskScope() {
        val prefs = context.getSharedPreferences("appearance_settings", Context.MODE_PRIVATE)
        val old = HashMap(prefs.all)
        try {
            prefs.edit().clear().commit()
            val settings = AppearanceSettings(context)
            assertEquals(80, settings.opacityPercent)
            assertEquals(25, settings.blurRadiusDp)
            assertFalse(settings.showAllTasks)
            assertFalse(settings.taskbarIconsOnly)
            settings.opacityPercent = -10
            settings.blurRadiusDp = 120
            settings.showAllTasks = true
            settings.taskbarIconsOnly = true
            val reopened = AppearanceSettings(context)
            assertEquals(0, reopened.opacityPercent)
            assertEquals(100, reopened.blurRadiusDp)
            assertTrue(reopened.showAllTasks)
            assertTrue(reopened.taskbarIconsOnly)
            settings.resetAppearance()
            assertEquals(80, reopened.opacityPercent)
            assertEquals(25, reopened.blurRadiusDp)
            assertTrue(reopened.showAllTasks)
            assertTrue("Reset appearance must preserve taskbar presentation", reopened.taskbarIconsOnly)
        } finally {
            val edit = prefs.edit().clear()
            old.forEach { (key, value) -> when (value) {
                is Int -> edit.putInt(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is String -> edit.putString(key, value)
            } }
            edit.commit()
        }
    }

    @Test fun taskbarUsesApplicationLabelAndMarksActiveIconOnlyEntry() {
        val appLabel = context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(context.packageName, 0)
        ).toString()
        val entry = TaskbarEntry("task:123", "A different activity title", context.packageName,
            displayId = 0, taskId = 123, hosted = false)
        val adapter = RunningTasksAdapter(context, {}, {})
        adapter.submit(listOf(entry))
        adapter.setIconsOnly(true)
        adapter.setActiveEntry(entry.key)
        instrumentation.runOnMainSync {
            val parent = androidx.recyclerview.widget.RecyclerView(context)
            val holder = adapter.onCreateViewHolder(parent, 0)
            adapter.onBindViewHolder(holder, 0)
            assertEquals(appLabel, holder.label.text.toString())
            assertEquals(View.GONE, holder.label.visibility)
            assertEquals(View.VISIBLE, holder.indicator.visibility)
            assertTrue(holder.row.contentDescription.toString().contains("active task"))
            assertTrue("Long press must remain discoverable for closing icon-only entries",
                holder.row.contentDescription.toString().contains("Long press to close"))
        }
    }

    @Test fun taskbarDoesNotCacheFallbackTitlesForMissingPackages() {
        val adapter = RunningTasksAdapter(context, {}, {})
        val entries = listOf(
            TaskbarEntry("first", "First activity", "missing.example.package", 0, 1, false),
            TaskbarEntry("second", "Second activity", "missing.example.package", 0, 2, false)
        )
        adapter.submit(entries)
        instrumentation.runOnMainSync {
            val parent = androidx.recyclerview.widget.RecyclerView(context)
            val first = adapter.onCreateViewHolder(parent, 0)
            adapter.onBindViewHolder(first, 0)
            val second = adapter.onCreateViewHolder(parent, 0)
            adapter.onBindViewHolder(second, 1)
            assertEquals("First activity", first.label.text.toString())
            assertEquals("Second activity", second.label.text.toString())
        }
    }

    @Test fun appearanceDialogSlidersApplyImmediately() {
        val settings = AppearanceSettings(context)
        val oldOpacity = settings.opacityPercent
        val oldBlur = settings.blurRadiusDp
        val oldScope = settings.showAllTasks
        try {
            settings.showAllTasks = true
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    activity.findViewById<View>(R.id.settingsButton).performClick()
                }
                // AlertDialog's OnShow handler installs the background and reset action asynchronously.
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val field = CompoundSettingsDialog::class.java.getDeclaredField("activeDialog").apply { isAccessible = true }
                    val dialog = field.get(CompoundSettingsDialog) as androidx.appcompat.app.AlertDialog
                    val root = dialog.window!!.decorView
                    val panel = root.findViewById<FrameLayout>(android.R.id.content)
                    val dialogBackground = panel.getChildAt(0) as android.widget.ImageView
                    assertNotNull("Settings must own a blur/tint background", dialogBackground.background)
                    fun findSlider(view: View, description: String): Slider? {
                        if (view is Slider && view.contentDescription == description) return view
                        if (view is android.view.ViewGroup) {
                            for (i in 0 until view.childCount) findSlider(view.getChildAt(i), description)?.let { return it }
                        }
                        return null
                    }
                    val opacity = requireNotNull(findSlider(root, "Background opacity, percent"))
                    val blur = requireNotNull(findSlider(root, "Background blur radius, dp"))
                    fun setProgress(slider: Slider, value: Int) {
                        slider.isFocusableInTouchMode = true
                        slider.requestFocus()
                        val increase = value > slider.value
                        val rtl = slider.layoutDirection == View.LAYOUT_DIRECTION_RTL
                        val key = if (increase != rtl) android.view.KeyEvent.KEYCODE_DPAD_RIGHT else android.view.KeyEvent.KEYCODE_DPAD_LEFT
                        repeat(102) {
                            slider.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key))
                            slider.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key))
                        }
                        assertEquals("Keyboard progress must update the slider", value.toFloat(), slider.value, 0f)
                    }
                    setProgress(opacity, 100)
                    assertEquals(100, settings.opacityPercent)
                    (dialogBackground.background as? android.graphics.drawable.GradientDrawable)?.let {
                        assertEquals(255, it.color!!.defaultColor ushr 24)
                    }
                    setProgress(opacity, 0)
                    assertEquals(0, settings.opacityPercent)
                    (dialogBackground.background as? android.graphics.drawable.GradientDrawable)?.let {
                        assertEquals(0, it.color!!.defaultColor ushr 24)
                    }
                    assertTrue("Legacy fallback must keep radius adjustable", blur.isEnabled)
                    run {
                        setProgress(blur, 100); assertEquals(100, settings.blurRadiusDp)
                        setProgress(blur, 0); assertEquals(0, settings.blurRadiusDp)
                    }
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).performClick()
                    assertEquals(80, settings.opacityPercent)
                    assertEquals(25, settings.blurRadiusDp)
                    assertTrue("Reset appearance must preserve taskbar scope", settings.showAllTasks)
                    assertEquals(1f, activity.findViewById<View>(R.id.startMenuButton).alpha, 0f)
                    dialog.dismiss()
                }
            }
        } finally {
            settings.opacityPercent = oldOpacity
            settings.blurRadiusDp = oldBlur
            settings.showAllTasks = oldScope
        }
    }

    /** Background redraws must not repeatedly hide the taskbar or an open start menu. */
    @Test fun desktopControlsRemainVisibleAfterFallbackLayout() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val expectedSnapshotColor = Color.rgb(210, 35, 50)
            scenario.onActivity { activity ->
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            // Allow the initial capture to finish or time out before testing steady visibility.
            Thread.sleep(600)
            var taskbarSnapshot: android.graphics.drawable.Drawable? = null
            var menuSnapshot: android.graphics.drawable.Drawable? = null
            scenario.onActivity { activity ->
                assertEquals(1f, activity.findViewById<View>(R.id.taskbar).alpha, 0f)
                (activity.findViewById<View>(R.id.appsContainer).parent as View)
                    .setBackgroundColor(expectedSnapshotColor)
                val taskbarHelperField = MainActivity::class.java.getDeclaredField("legacyTaskbarBlur")
                    .apply { isAccessible = true }
                (taskbarHelperField.get(activity) as LegacyBackgroundBlur).refresh()
            }
            Thread.sleep(600)
            scenario.onActivity { activity ->
                val background = activity.findViewById<android.widget.ImageView>(R.id.taskbarBackground)
                taskbarSnapshot = background.drawable
                assertTrue("Taskbar should snapshot the Activity window behind its in-window surface",
                    taskbarSnapshot is android.graphics.drawable.BitmapDrawable)
                assertEquals(expectedSnapshotColor,
                    (taskbarSnapshot as android.graphics.drawable.BitmapDrawable).bitmap.getPixel(
                        background.width / 2, background.height / 2))
                activity.findViewById<View>(R.id.startMenuButton).performClick()
            }
            Thread.sleep(600)
            scenario.onActivity { activity ->
                val menu = activity.findViewById<View>(R.id.startMenu)
                val menuBackground = activity.findViewById<android.widget.ImageView>(R.id.background)
                menuSnapshot = menuBackground.drawable
                assertTrue("Start menu should snapshot the Activity window behind its in-window surface",
                    menuSnapshot is android.graphics.drawable.BitmapDrawable)
                assertEquals(expectedSnapshotColor,
                    (menuSnapshot as android.graphics.drawable.BitmapDrawable).bitmap.getPixel(
                        menuBackground.width / 2, menuBackground.height / 2))
                val taskbarBackground = activity.findViewById<android.widget.ImageView>(R.id.taskbarBackground)
                repeat(4) { taskbarBackground.requestLayout() }
                repeat(4) { menu.requestLayout(); menuBackground.requestLayout() }
            }
            Thread.sleep(600)
            scenario.onActivity { activity ->
                val taskbar = activity.findViewById<View>(R.id.taskbar)
                val menu = activity.findViewById<View>(R.id.startMenu)
                val taskbarBackground = activity.findViewById<android.widget.ImageView>(R.id.taskbarBackground)
                val menuBackground = activity.findViewById<android.widget.ImageView>(R.id.background)
                assertEquals(View.VISIBLE, taskbar.visibility)
                assertEquals(1f, taskbar.alpha, 0f)
                assertEquals(View.VISIBLE, menu.visibility)
                assertEquals(1f, menu.alpha, 0f)
                assertSame("Unchanged bounds must not recapture the taskbar", taskbarSnapshot,
                    taskbarBackground.drawable)
                assertSame("Unchanged bounds must not recapture the start menu", menuSnapshot,
                    menuBackground.drawable)
            }
            Thread.sleep(600)
            scenario.onActivity { activity ->
                assertEquals(1f, activity.findViewById<View>(R.id.startMenu).alpha, 0f)
                assertEquals(1f, activity.findViewById<View>(R.id.taskbar).alpha, 0f)
            }
        }
    }

    /** Service-side failures must survive Parcel's exception encoding used by Binder. */
    @Test fun taskServiceErrorsSurviveBinderParcel() {
        val method = xtr.compound.server.RemoteService::class.java.getDeclaredMethod(
            "remoteError", String::class.java, Throwable::class.java).apply { isAccessible = true }
        val error = method.invoke(null, "Task query failed", UnsupportedOperationException("OEM API")) as Exception
        val parcel = android.os.Parcel.obtain()
        try {
            parcel.writeException(error)
            parcel.setDataPosition(0)
            try {
                parcel.readException()
                fail("Task failure must not decode as success")
            } catch (received: IllegalStateException) {
                assertTrue(received.message!!.contains("Task query failed"))
            }
        } finally { parcel.recycle() }
    }

    /** Force the legacy path even on a live-blur device and check capture, tint and cleanup. */
    @Test fun legacyBlurCapturesAndUpdatesOpacity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var image: android.widget.ImageView
            lateinit var overlay: FrameLayout
            lateinit var helper: LegacyBackgroundBlur
            scenario.onActivity { activity ->
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                overlay = FrameLayout(activity)
                image = android.widget.ImageView(activity)
                overlay.addView(image, FrameLayout.LayoutParams(120, 120))
                activity.findViewById<FrameLayout>(R.id.appsContainer).layoutTransition = null
                activity.findViewById<FrameLayout>(R.id.appsContainer).addView(overlay,
                    FrameLayout.LayoutParams(120, 120))
                helper = LegacyBackgroundBlur(image, overlay, activity.window)
            }
            try {
                instrumentation.waitForIdleSync()
                scenario.onActivity { helper.update(true, android.graphics.Color.BLACK, 25f) }
                var captured = false
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (!captured && System.nanoTime() < deadline) {
                    scenario.onActivity { captured = image.drawable is android.graphics.drawable.BitmapDrawable }
                    if (!captured) Thread.sleep(50)
                }
                assertTrue("Legacy PixelCopy must produce a snapshot", captured)
                scenario.onActivity {
                    assertEquals(1f, overlay.alpha, 0f)
                    assertEquals(1f, image.alpha, 0f)
                    assertEquals(255, (image.foreground as android.graphics.drawable.ColorDrawable).alpha)
                    helper.update(true, android.graphics.Color.TRANSPARENT, 0f)
                    assertEquals(0, (image.foreground as android.graphics.drawable.ColorDrawable).alpha)
                    helper.refresh()
                    helper.close() // An in-flight result must not reinstall the snapshot.
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    assertNull(image.drawable)
                    assertNull(image.foreground)
                    assertEquals(1f, overlay.alpha, 0f)
                    assertEquals(1f, image.alpha, 0f)
                }
            } finally {
                scenario.onActivity {
                    helper.close()
                    (overlay.parent as? android.view.ViewGroup)?.removeView(overlay)
                }
            }
        }
    }

    @Test fun privilegedDisplayAttributionAndCleanupWithoutUi() {
        assumeTrue("Needs a running, authorized Shizuku server",
            runCatching { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false))
        val ready = CountDownLatch(1)
        var remote: IRemoteService? = null
        instrumentation.runOnMainSync {
            RemoteServiceHelper.getInstance(context) { remote = it; ready.countDown() }
        }
        assertTrue(ready.await(20, TimeUnit.SECONDS))
        val service = requireNotNull(remote)
        val texture = android.graphics.SurfaceTexture(false)
        texture.setDefaultBufferSize(640, 480)
        val surface = android.view.Surface(texture)
        var displayId = -1
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                setClassName("com.android.settings", "com.android.settings.Settings")
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            displayId = service.renderAppToSurface(intent, surface, 640, 480, service.getBaseDisplayDensity(0))
            assertTrue("Shell-attributed virtual display must launch", displayId > 0)
            service.resizeVirtualDisplay(displayId, 800, 600, service.getBaseDisplayDensity(0))
            service.setVirtualDisplaySurface(displayId, null)
            service.setVirtualDisplaySurface(displayId, surface)
            val task = service.runningTasks.firstOrNull { it.displayId == displayId }
            assertNotNull("Launched task must be discoverable", task)
            assertTrue(service.focusTask(task!!.taskId))
            assertTrue(service.removeTask(task.taskId))
        } finally {
            if (displayId > 0) {
                service.releaseVirtualDisplay(displayId)
                service.releaseVirtualDisplay(displayId)
            }
            surface.release()
            texture.release()
        }
    }

    @Test fun privilegedTaskAndMinimizeRestoreLifecycle() {
        assumeTrue("Needs a running, authorized Shizuku server",
            runCatching { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false))
        val ready = CountDownLatch(1)
        var remote: IRemoteService? = null
        instrumentation.runOnMainSync {
            RemoteServiceHelper.initialize(context)
            RemoteServiceHelper.getInstance(context) { remote = it; ready.countDown() }
        }
        assertTrue("Service did not connect", ready.await(20, TimeUnit.SECONDS))
        val service = requireNotNull(remote)
        val listed = service.runningTasks
        assertTrue(listed.none { it.packageName == context.packageName })
        assertTrue(service.getBaseDisplayDensity(0) > 0)
        assertFalse(service.removeTask(Int.MAX_VALUE))
        assertFalse(service.focusTask(Int.MAX_VALUE))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var controller: DesktopWindowController? = null
            var originalActivity: MainActivity? = null
            scenario.onActivity { activity ->
                originalActivity = activity
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                // Use a separate controller to isolate this test's owned display from user windows.
                val container = activity.findViewById<FrameLayout>(R.id.appsContainer)
                controller = DesktopWindowController(activity, container)
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    setClassName("com.android.settings", "com.android.settings.Settings")
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                controller!!.launch(intent, "Settings test", activity.getDrawable(android.R.drawable.ic_menu_preferences)!!)
            }
            try {
                var displayId = -1
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
                while (displayId < 0 && System.nanoTime() < deadline) {
                    scenario.onActivity { displayId = controller!!.getWindows().firstOrNull()?.displayId ?: -1 }
                    if (displayId < 0) Thread.sleep(100)
                }
                scenario.onActivity { activity ->
                    val container = activity.findViewById<FrameLayout>(R.id.appsContainer)
                    android.util.Log.i("CompoundWindows", "Test workspace ${container.width}x${container.height}, attached=${container.isAttachedToWindow}, windows=${controller!!.getWindows()}")
                }
                assertTrue("Virtual display did not launch", displayId > 0)
                val settingsTask = service.runningTasks.firstOrNull {
                    it.displayId == displayId && it.packageName == "com.android.settings"
                }
                assertNotNull("Settings task must be hosted on its assigned display", settingsTask)
                assertTrue("Shell service must be able to focus a current task", service.focusTask(settingsTask!!.taskId))
                var normalWidth = 0
                var normalHeight = 0
                scenario.onActivity { activity ->
                    val container = activity.findViewById<FrameLayout>(R.id.appsContainer)
                    val root = container.getChildAt(container.childCount - 1)
                    normalWidth = root.width; normalHeight = root.height
                    root.findViewById<android.view.View>(R.id.maximizeButton).performClick()
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val container = activity.findViewById<FrameLayout>(R.id.appsContainer)
                    val root = container.getChildAt(container.childCount - 1)
                    assertTrue(controller!!.getWindows().single().maximized)
                    assertEquals(container.width, root.width)
                    assertEquals(container.height, root.height)
                    root.findViewById<android.view.View>(R.id.maximizeButton).performClick()
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val container = activity.findViewById<FrameLayout>(R.id.appsContainer)
                    val root = container.getChildAt(container.childCount - 1)
                    assertFalse(controller!!.getWindows().single().maximized)
                    assertEquals(normalWidth, root.width)
                    assertEquals(normalHeight, root.height)
                }
                scenario.onActivity { activity ->
                    activity.delegate.localNightMode = if (
                        activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                    ) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    assertSame("Night mode must retain the Activity and its hosted controllers", originalActivity, activity)
                    assertEquals(displayId, controller!!.getWindows().single().displayId)
                }
                scenario.onActivity { controller!!.minimizeAll(); assertTrue(controller!!.getWindows().single().minimized) }
                instrumentation.waitForIdleSync()
                scenario.onActivity { controller!!.focusDisplay(displayId) }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val restored = controller!!.getWindows().single()
                    assertFalse(restored.minimized)
                    assertEquals("Restore must retain the display", displayId, restored.displayId)
                    controller!!.closeDisplay(displayId)
                    controller!!.closeDisplay(displayId)
                    assertTrue(controller!!.getWindows().isEmpty())
                }
                val displayManager = context.getSystemService(android.hardware.display.DisplayManager::class.java)
                val closeDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (displayManager.getDisplay(displayId) != null && System.nanoTime() < closeDeadline) Thread.sleep(100)
                assertNull("Close must release the virtual display", displayManager.getDisplay(displayId))
                assertTrue("Close must remove the hosted task", service.runningTasks.none { it.displayId == displayId })
            } finally { scenario.onActivity { controller!!.closeAll() } }
        }
    }
}
