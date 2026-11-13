package xtr.compound

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import xtr.compound.server.RemoteServiceHelper

/** Device tests exercise real preferences, surfaces and shell-privileged Binder calls. */
@RunWith(AndroidJUnit4::class)
class DesktopIntegrationTest {
    @get:Rule val composeRule = createEmptyComposeRule()
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
            assertFalse(settings.useSystemWallpaper)
            settings.opacityPercent = -10
            settings.blurRadiusDp = 120
            settings.showAllTasks = true
            settings.taskbarIconsOnly = true
            settings.useSystemWallpaper = true
            val reopened = AppearanceSettings(context)
            assertEquals(0, reopened.opacityPercent)
            assertEquals(100, reopened.blurRadiusDp)
            assertTrue(reopened.showAllTasks)
            assertTrue(reopened.taskbarIconsOnly)
            assertTrue(reopened.useSystemWallpaper)
            settings.resetAppearance()
            assertEquals(80, reopened.opacityPercent)
            assertEquals(25, reopened.blurRadiusDp)
            assertTrue(reopened.showAllTasks)
            assertTrue("Reset appearance must preserve taskbar presentation", reopened.taskbarIconsOnly)
            assertFalse("Reset appearance restores the bundled wallpaper", reopened.useSystemWallpaper)
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
        withTaskbar(listOf(entry), iconsOnly = true, activeKey = entry.key) {
            composeRule.onNode(hasContentDescription("$appLabel, active task. Long press to close"))
                .assertExists()
            composeRule.onNode(hasText(appLabel), useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test fun taskbarDoesNotCacheFallbackTitlesForMissingPackages() {
        val entries = listOf(
            TaskbarEntry("first", "First activity", "missing.example.package", 0, 1, false),
            TaskbarEntry("second", "Second activity", "missing.example.package", 0, 2, false)
        )
        withTaskbar(entries, iconsOnly = false, activeKey = null) {
            composeRule.onNode(hasContentDescription("First activity"), useUnmergedTree = true).assertExists()
            composeRule.onNode(hasContentDescription("Second activity"), useUnmergedTree = true).assertExists()
        }
    }

    private fun withTaskbar(
        entries: List<TaskbarEntry>,
        iconsOnly: Boolean,
        activeKey: String?,
        assertions: () -> Unit
    ) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContentView(ComposeView(activity).apply {
                    setContent { testTaskbar(entries, iconsOnly, activeKey) }
                })
            }
            assertions()
        }
    }

    @Composable
    private fun testTaskbar(entries: List<TaskbarEntry>, iconsOnly: Boolean, activeKey: String?) {
        TaskbarContent(
            entries = entries,
            iconsOnly = iconsOnly,
            activeKey = activeKey,
            serviceStatus = null,
            onStart = {},
            onStartLongPress = {},
            onBack = {},
            onHome = {},
            onRecents = {},
            onWifi = {},
            onBattery = {},
            onClock = {},
            onNotifications = {},
            onFocus = {},
            onClose = {}
        )
    }

    @Test fun appearanceDialogSlidersApplyImmediately() {
        val settings = AppearanceSettings(context)
        val oldOpacity = settings.opacityPercent
        val oldBlur = settings.blurRadiusDp
        val oldScope = settings.showAllTasks
        val oldSystemWallpaper = settings.useSystemWallpaper
        try {
            settings.showAllTasks = true
            settings.useSystemWallpaper = false
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                clickDesktopControl(R.string.open_start_menu)
                clickDesktopControl(R.string.appearance_settings)
                composeRule.onNode(hasText("Show all Android tasks")).assertDoesNotExist()
                composeRule.onNode(hasText("Use system wallpaper")).performClick()
                assertTrue(settings.useSystemWallpaper)
                scenario.onActivity { activity ->
                    assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER != 0)
                    val desktopRoot = activity.findViewById<View>(R.id.appsContainer).parent as View
                    val background = desktopRoot.background as? android.graphics.drawable.ColorDrawable
                    assertNotNull("System wallpaper mode should leave the Activity surface transparent", background)
                    assertEquals(Color.TRANSPARENT, background!!.color)
                }
                composeRule.onNode(hasText("Use system wallpaper")).performClick()
                assertFalse(settings.useSystemWallpaper)
                scenario.onActivity { activity ->
                    assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                    val desktopRoot = activity.findViewById<View>(R.id.appsContainer).parent as View
                    assertFalse("Bundled wallpaper should be restored when disabled",
                        desktopRoot.background is android.graphics.drawable.ColorDrawable)
                }
                val opacity = composeRule.onNode(hasContentDescription("Background opacity, percent"))
                val blur = composeRule.onNode(hasContentDescription("Background blur radius, dp"))
                for (percent in listOf(100, 0)) {
                    opacity.performSemanticsAction(SemanticsActions.SetProgress) { it(percent.toFloat()) }
                    assertEquals(percent, settings.opacityPercent)
                    scenario.onActivity { activity ->
                        val field = CompoundSettingsDialog::class.java.getDeclaredField("activeDialog")
                            .apply { isAccessible = true }
                        val dialog = field.get(CompoundSettingsDialog) as Dialog
                        val background = dialog.findViewById<View>(R.id.settingsDialogBackground)
                        assertNotNull("Settings must own a blur/tint background", background.background)
                        (background.background as? android.graphics.drawable.GradientDrawable)?.let {
                            assertEquals(percent * 255 / 100, it.color!!.defaultColor ushr 24)
                        }
                        assertEquals(1f, activity.findViewById<View>(R.id.startMenu).alpha, 0f)
                    }
                }
                blur.assertIsEnabled()
                for (radius in listOf(100, 0)) {
                    blur.performSemanticsAction(SemanticsActions.SetProgress) { it(radius.toFloat()) }
                    assertEquals(radius, settings.blurRadiusDp)
                }
                composeRule.onNode(hasText("Reset appearance")).performClick()
                assertEquals(AppearanceSettings.DEFAULT_OPACITY, settings.opacityPercent)
                assertEquals(AppearanceSettings.DEFAULT_BLUR_RADIUS, settings.blurRadiusDp)
                assertTrue("Reset appearance must preserve taskbar scope", settings.showAllTasks)
                composeRule.onNode(hasText("Close")).performClick()
            }
        } finally {
            settings.opacityPercent = oldOpacity
            settings.blurRadiusDp = oldBlur
            settings.showAllTasks = oldScope
            settings.useSystemWallpaper = oldSystemWallpaper
        }
    }

    @Test fun configureDialogAppliesTaskbarSettings() {
        val settings = AppearanceSettings(context)
        val oldScope = settings.showAllTasks
        val oldIconsOnly = settings.taskbarIconsOnly
        try {
            settings.showAllTasks = false
            settings.taskbarIconsOnly = false
            ActivityScenario.launch(MainActivity::class.java).use {
                clickDesktopControl(R.string.open_start_menu)
                clickDesktopControl(R.string.configure_settings)
                composeRule.onNode(hasText("Reset appearance")).assertDoesNotExist()
                composeRule.onNode(hasText("Show all Android tasks")).performClick()
                assertTrue(settings.showAllTasks)
                composeRule.onNode(hasText("Show taskbar icons only")).performClick()
                assertTrue(settings.taskbarIconsOnly)
                composeRule.onNode(hasText("Close")).performClick()
                clickDesktopControl(R.string.configure_settings)
                composeRule.onNode(hasText("Show all Android tasks")).assertIsOn()
                composeRule.onNode(hasText("Show taskbar icons only")).assertIsOn().performClick()
                assertFalse(settings.taskbarIconsOnly)
            }
        } finally {
            settings.showAllTasks = oldScope
            settings.taskbarIconsOnly = oldIconsOnly
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
                assertEquals("Taskbar container must not overlay the snapshot with a fixed tint",
                    Color.TRANSPARENT,
                    (activity.findViewById<View>(R.id.taskbar).background as ColorDrawable).color)
                taskbarSnapshot = background.drawable
                assertTrue("Taskbar should layer the snapshot and tint separately",
                    taskbarSnapshot is LayerDrawable)
                val snapshotBitmap = ((taskbarSnapshot as LayerDrawable).getDrawable(0) as BitmapDrawable).bitmap
                assertEquals(expectedSnapshotColor,
                    snapshotBitmap.getPixel(background.width / 2, background.height / 2))
                val taskbarHelperField = MainActivity::class.java.getDeclaredField("legacyTaskbarBlur")
                    .apply { isAccessible = true }
                val taskbarHelper = taskbarHelperField.get(activity) as LegacyBackgroundBlur
                listOf(0, 50, 100).forEach { opacity ->
                    taskbarHelper.update(true, LiveBackgroundBlur.fallbackTint(opacity, false), 25f)
                    assertSnapshotTintOpacity(background, opacity)
                }
                taskbarHelper.update(true, LiveBackgroundBlur.fallbackTint(50, false), 0f)
                assertSnapshotTintOpacity(background, 50)
            }
            clickDesktopControl(R.string.open_start_menu)
            Thread.sleep(600)
            scenario.onActivity { activity ->
                val menu = activity.findViewById<View>(R.id.startMenu)
                val menuBackground = activity.findViewById<android.widget.ImageView>(R.id.background)
                menuSnapshot = menuBackground.drawable
                assertTrue("Start menu should layer the snapshot and tint separately",
                    menuSnapshot is LayerDrawable)
                val snapshotBitmap = ((menuSnapshot as LayerDrawable).getDrawable(0) as BitmapDrawable).bitmap
                assertEquals(expectedSnapshotColor,
                    snapshotBitmap.getPixel(menuBackground.width / 2, menuBackground.height / 2))
                val menuHelperField = MainActivity::class.java.getDeclaredField("legacyMenuBlur")
                    .apply { isAccessible = true }
                val menuHelper = menuHelperField.get(activity) as LegacyBackgroundBlur
                listOf(0, 50, 100).forEach { opacity ->
                    menuHelper.update(true, LiveBackgroundBlur.fallbackTint(opacity, false), 25f)
                    assertSnapshotTintOpacity(menuBackground, opacity)
                }
                menuHelper.update(true, LiveBackgroundBlur.fallbackTint(50, false), 0f)
                assertSnapshotTintOpacity(menuBackground, 50)
                assertEquals(1f, activity.findViewById<View>(R.id.startMenu).alpha, 0f)
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

    private fun clickDesktopControl(description: Int) {
        composeRule.onNode(hasContentDescription(context.getString(description), substring = true))
            .performClick()
    }

    private fun assertSnapshotTintOpacity(image: android.widget.ImageView, opacity: Int) {
        val layers = image.drawable as LayerDrawable
        val bitmap = (layers.getDrawable(0) as BitmapDrawable).bitmap
        val tint = layers.getDrawable(1) as ColorDrawable
        assertEquals("Snapshot layer stays opaque at $opacity% tint opacity", 0xFF,
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) ushr 24)
        assertEquals("Tint layer follows $opacity% opacity", opacity * 255 / 100, tint.color ushr 24)
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
                    scenario.onActivity { captured = image.drawable is LayerDrawable }
                    if (!captured) Thread.sleep(50)
                }
                assertTrue("Legacy PixelCopy must produce a snapshot", captured)
                scenario.onActivity {
                    assertEquals(1f, overlay.alpha, 0f)
                    assertEquals(1f, image.alpha, 0f)
                    assertSnapshotTintOpacity(image, 100)
                    helper.update(true, android.graphics.Color.TRANSPARENT, 0f)
                    assertSnapshotTintOpacity(image, 0)
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
