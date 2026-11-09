package xtr.compound

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
            settings.opacityPercent = -10
            settings.blurRadiusDp = 120
            settings.showAllTasks = true
            val reopened = AppearanceSettings(context)
            assertEquals(0, reopened.opacityPercent)
            assertEquals(100, reopened.blurRadiusDp)
            assertTrue(reopened.showAllTasks)
            settings.resetAppearance()
            assertEquals(80, reopened.opacityPercent)
            assertEquals(25, reopened.blurRadiusDp)
            assertTrue(reopened.showAllTasks)
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
                // AlertDialog's OnShow handler installs the reset action asynchronously.
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val field = CompoundSettingsDialog::class.java.getDeclaredField("activeDialog").apply { isAccessible = true }
                    val dialog = field.get(CompoundSettingsDialog) as androidx.appcompat.app.AlertDialog
                    val root = dialog.window!!.decorView
                    fun findSlider(view: View, description: String): Slider? {
                        if (view is Slider && view.contentDescription == description) return view
                        if (view is android.view.ViewGroup) {
                            for (i in 0 until view.childCount) findSlider(view.getChildAt(i), description)?.let { return it }
                        }
                        return null
                    }
                    val opacity = requireNotNull(findSlider(root, "Taskbar and menu opacity, percent"))
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
                    setProgress(opacity, 0)
                    assertEquals(0, settings.opacityPercent)
                    android.util.Log.i("CompoundWindows", "Live blur available=${blur.isEnabled}")
                    if (blur.isEnabled) {
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
            scenario.onActivity { activity ->
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
                    assertEquals(container.width, root.width)
                    assertEquals(container.height, root.height)
                    root.findViewById<android.view.View>(R.id.maximizeButton).performClick()
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val container = activity.findViewById<FrameLayout>(R.id.appsContainer)
                    val root = container.getChildAt(container.childCount - 1)
                    assertEquals(normalWidth, root.width)
                    assertEquals(normalHeight, root.height)
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
