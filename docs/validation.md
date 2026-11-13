# Validation checklist

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` with an installed JDK supported by the checked-in Gradle and Android Gradle Plugin. CI uses the same commands. Unit tests cover pure state/geometry/policy; they cannot establish privileged Binder or compositor compatibility.

## Device scenarios

Repeat on Android 12 and a recent Android version, with root and with Shizuku running as shell where available:

1. Open with no privilege provider: setup should explain install/start/retry; Later leaves the launcher usable without crashing. Deny permission, reopen setup, then grant permission. Start multiple launches during connection and verify one binding. Stop/restart Shizuku and recover through setup.
2. Launch two apps and multiple tasks from one package. Compound-only scope lists hosted windows, including minimized ones. All-tasks scope adds accessible external tasks but excludes home/recents/Compound. Focus external tasks on their existing display. Close one task without force-stopping its package; stale IDs report failure and refresh.
3. Enter text or navigate within an app, minimize it, restore from the taskbar and verify state is retained. Repeat maximize/restore after dragging and resizing; confirm restored bounds remain within the workspace. Verify touch coordinates near all corners after resize. Close during launch, close twice, and lose the service with windows open.
4. Move animated content behind the taskbar/start menu and verify blur is live. Adjust radius to zero and maximum; adjust opacity through both endpoints, keeping icons/text readable. In the settings dialog, verify the backdrop remains visible at zero opacity. Check minimized/maximized state icons against both light and dark tints. Restart Compound and verify persistence; reset appearance. Disable system window blur and confirm fallback/explanation, then re-enable it and verify saved radius is restored.
5. Pause/resume and destroy/recreate the Activity. Confirm task polling stops while paused and resources/listeners are cleaned on destruction. Window sessions are closed when the desktop Activity is destroyed; no process-death session recovery is promised.
6. Switch taskbar display between app names and icons only with zero, one, and several tasks. Confirm names come from application labels, icons are centered in the taskbar app area, the active indicator moves when another task is selected, and long-press closes a task in icon-only mode.
7. Tap the taskbar Wi-Fi button and confirm the system Wi-Fi panel opens; on devices without a panel, confirm Wi-Fi settings opens. Tap the battery button and confirm the battery/power overview opens, falling back to battery saver settings and then general Settings if unavailable.
8. Open the Start menu from the taskbar, scroll the app list and grid, and launch an app from each. Check the device settings, Appearance, Configure, files, and power actions. Verify that the power action is disabled while disconnected and that long-pressing Start opens setup.
9. Check both overlays in light and dark mode, at narrow and wide workspace widths, and with keyboard or screen-reader navigation. Verify that Recents refreshes and scrolls the task list to the beginning. Tap the upper and lower portions of the clock to open Clock and Calendar respectively.
10. Open Appearance and verify that both sliders apply immediately and reset preserves taskbar preferences. Open Configure and verify that task scope and icon-only mode apply immediately and persist across reopening.
11. Enable the start-menu resize handle, drag its top-right corner inward and outward, and verify size persistence after reopening Compound. Test clamping at narrow and short desktop bounds, then reset appearance and verify that the handle is hidden and the default size returns.

## Recorded results

Results from the implementation run are recorded below before delivery. A build pass alone must not be described as an Android 12/root runtime pass.

### Settings dialog migration — 2026-11-13

- Debug APK, all 17 unit tests, lint, and instrumentation APK build passed.
- Waydroid x86_64 / Android 13 / API 33 with Shizuku running as shell reported **OK (10 tests)**. The suite opened both dialogs from the Start menu, exercised the slider endpoints and reset, and verified that taskbar preferences persisted across reopening.
- Android 12, newer Android versions, root/system-app mode, and live compositor blur were outside this device run.

### Compose migration — 2026-11-13

- Tested the Start menu with the existing View taskbar first. Opened both app layouts and Compound settings, and launched Settings on a virtual display.
- With both Compose hosts active, manually opened setup from Start and minimized, restored, and closed Settings from the taskbar. Restore preserved the task and display IDs.
- Debug APK, all 17 unit tests, lint, and the instrumentation APK build passed. Waydroid x86_64 / Android 13 / API 33 with Shizuku running as shell reported **OK (9 tests)**, including Compose taskbar semantics, snapshot blur, and privileged display and window lifecycle.
- Android 12, recent Android, root/system-app mode, and live compositor blur remain unverified for this migration.

### Implementation run — 2026-11-09

- Local debug build, nine unit tests, lint and instrumentation APK compilation passed. See the PR for the final CI result.
- Connected device: Samsung SM-A155F, Android 16 / API 36, Shizuku running as shell. No rooted device or Android 12 device was available.
- Passed on-device: real preference persistence/clamping/reset semantics; offscreen shell-attributed display creation, resize, detach/attach, task enumeration/focus/removal and idempotent display release; visible SurfaceView launch, focus, maximize/restore bounds, minimize/restore retaining its display, and final task/display cleanup.
- The slider UI test reached both opacity endpoints through keyboard input and persisted the values. Its reset assertion raced the asynchronous dialog OnShow handler. The harness now waits for the main looper after opening; that final harness change was not rerun after the request to finish with current device coverage. Preference-level reset already passed.
- The device reports live blur unavailable. Live compositor appearance and enabled radius slider interaction remain unverified. Android 12, root/system-app mode, permission denial/restart, external-task UI, and resized touch-coordinate checks remain manual follow-up work.
- Debugging found and fixed libsu startup ordering and shell display-attribution mismatch. Locked-screen attempts were distinguished from actual framework failures. See [development.md](development.md) for reproduction commands and diagnosis.

### PR #1 follow-up — 2026-11-09

- Required debug build, all nine unit tests, lint and instrumentation APK compilation passed with the Java 21 Gradle daemon.
- Samsung SM-A155F, Android 16 / API 36, authorized Shizuku shell: final instrumentation run **OK (6 tests)**. Covers preferences, settings opacity/radius endpoints and reset, settings background tint updates, forced legacy snapshot capture/opacity/cleanup, Binder exception Parcel encoding, offscreen privileged display/task operations, visible maximize/restore/minimize lifecycle, and an in-app night-mode switch retaining the Activity and display ID.
- The initial screen-off attempt failed visible-surface checks. After waking the device, a synthetic-view alpha assertion exposed the test container's enter animation; the test disables that animation. The final run passed all six tests.
- Live blur is unavailable on this device. Enabled compositor blur, Android 12, root/system-app mode, denial/restart, and resized input remain unverified. Snapshot blur is static between refreshes and may briefly hide taskbar/menu overlays during capture; protected content may not be captured.
- CI's failed job log confirmed setup-android's default `tools` package caused SDK setup to exit before Gradle. The workflow now requests `platform-tools` explicitly; a fresh hosted CI run is still required.

### Visibility regression follow-up — 2026-11-09

Snapshot installation can request layout without changing bounds. Refreshing on every such layout could repeatedly hide the taskbar/menu while recapturing. Refresh listeners now require a real geometry change. Captures cancel queued frame work and restore alpha on disable/close or after a 250 ms timeout; stale copy results are discarded. Required build, nine unit tests, lint and instrumentation APK compilation passed. Samsung SM-A155F / API 36 / Shizuku shell: **OK (7 tests)**, including a new real-desktop check that the taskbar and open start menu remain visible across unchanged layout requests. Live compositor blur remains unavailable on this device.

### Snapshot opacity follow-up — 2026-11-10

- Debug build, unit tests, lint, and Android test APK compilation passed. The focused `desktopControlsRemainVisibleAfterFallbackLayout` instrumentation test passed on Waydroid x86_64 / Android 13 / API 33. It checks the transparent taskbar container, separate snapshot/tint layers, opaque snapshot pixels at 0/50/100% tint opacity, and unchanged taskbar/menu controls.
- On-device settings sliders reached 0%, 50%, and 100% opacity and 0 dp blur; the dialog reports live blur unavailable and uses the snapshot fallback. Live compositor blur and behavior on other Android devices remain unverified.
- The repository no longer pins the Gradle daemon to JDK 21. The final build and unit/lint checks use the installed JDK 25.
