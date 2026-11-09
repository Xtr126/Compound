# Development, debugging, and instrumentation

## Build environment

Use JDK 21, the checked-in Gradle wrapper, Android SDK platform `platforms;android-37.0`, and build tools `36.0.0`. The project keeps minSdk 31 and targetSdk 35. Configure `ANDROID_HOME` or an untracked `local.properties`; never commit machine-specific SDK paths.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

Debug APKs are under `app/build/outputs/apk/debug/` and `app/build/outputs/apk/androidTest/debug/`. Unit-test reports are under `app/build/reports/tests/testDebugUnitTest/`; lint is `app/build/reports/lint-results-debug.html`. The GitHub Actions workflow builds, tests, lints and uploads APK/reports; it does not run privileged device instrumentation.

The Gradle daemon requests Java 21 through `gradle/gradle-daemon-jvm.properties`. Keep the wrapper checksum pinned. A successful incremental build is useful, but use a clean build when diagnosing stale generated AIDL/binding classes.

## Device setup

1. Connect an authorized Android device and verify `adb devices`. With multiple devices, add `-s SERIAL` to every adb invocation and use `ANDROID_SERIAL` for Gradle device tasks.
2. Install/start Shizuku using its own instructions and grant Compound access through onboarding. For root testing, use a genuinely rooted device and confirm Compound chooses the root backend. These are distinct test configurations.
3. Unlock the device before visible-window tests and keep Compound in front. A secure lock screen can leave the Activity measured and attached while preventing SurfaceView surface creation. Wake commands cannot dismiss a credential-protected lock.
4. Install through Package Manager rather than incremental IDE Apply Changes so privileged service code is refreshed:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

The tests add KEEP_SCREEN_ON to their Activity while it is visible; this does not unlock a locked device. Do not change the user's lock credentials, global timeout, density or rotation settings to make a test pass.

## Run instrumentation

Run the integration class against the installed APKs:

```sh
adb shell am instrument -w -e class xtr.compound.DesktopIntegrationTest xtr.compound.test/androidx.test.runner.AndroidJUnitRunner
```

Or let Gradle build/install/run the device suite:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Focused runs use `Class#method`, for example:

```sh
adb shell am instrument -w -e class 'xtr.compound.DesktopIntegrationTest#privilegedDisplayAttributionAndCleanupWithoutUi' xtr.compound.test/androidx.test.runner.AndroidJUnitRunner
```

| Test | Purpose / preconditions |
| --- | --- |
| `appearancePersistsClampsAndResetPreservesTaskScope` | Real preference defaults, clamping, reopening and reset; restores previous values. No Shizuku needed. |
| `appearanceDialogSlidersApplyImmediately` | Opens the real settings dialog, drives sliders with keyboard events, checks saved endpoints/reset and unchanged foreground alpha. Radius controls are exercised only when available. Waits for dialog OnShow before driving reset. |
| `privilegedDisplayAttributionAndCleanupWithoutUi` | Requires authorized Shizuku. Uses a SurfaceTexture to launch Settings, resize, detach/attach, enumerate, focus/remove its task and release twice; useful without visible SurfaceView composition. |
| `privilegedTaskAndMinimizeRestoreLifecycle` | Requires authorized Shizuku and an unlocked visible desktop. Checks real SurfaceView launch, task/display association, focus, maximize/restore bounds, minimize/restore retaining display ID, and task/display cleanup. |

Privileged tests use JUnit assumptions when Shizuku is absent or unauthorized. A skipped test is not a compatibility pass. They launch and remove a Settings task; run them on a development device. Preference tests restore saved settings, but do not promise recovery of pre-existing desktop window sessions: Activity destruction intentionally closes sessions.

Always read instrumentation's final `OK`, `FAILURES`, or crash output. `am instrument` can return a successful shell exit status even when tests fail. Preserve the output with the build/commit and record backend, Android API, device model, skip count, and final tested revision.

## Capture focused diagnostics

Start capture before reproducing, then stop it with Ctrl-C:

```sh
adb logcat -v threadtime -s CompoundWindows:V CompoundRemoteService:V RemoteServiceHelper:V AndroidRuntime:E TestRunner:I > compound-runtime.log
```

Avoid clearing all device logs or collecting unrelated app contents. Filter by timestamps/PIDs when a capture includes an earlier failure. Useful read-only checks:

```sh
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.model
adb shell ps -A
adb shell dumpsys window policy
adb shell dumpsys display
```

Inspect only the relevant keyguard, display or Compound records before sharing logs. For visual checks, capture while Compound is foregrounded:

```sh
adb exec-out screencap -p > compound-screen.png
```

A screenshot cannot establish live blur: compare moving/animated content over time on a blur-capable device. Record compositor support and the system blur-enabled state. Test both enabled and disabled states on a development device, restoring any changed system setting afterward. On unsupported devices, verify tint/opacity and the disabled radius explanation instead.

## Failure diagnosis

| Symptom | Check / fix |
| --- | --- |
| `The main shell was already created` during Activity startup | Configure libsu in `CompoundApplication` before any helper/test opens a shell. Do not move this back into MainActivity's static initialization. |
| No surface callback or display ID despite an attached measured workspace | Check secure keyguard and screen state first. Then inspect SurfaceView visibility, layout dimensions, pending creation and current service state. Do not treat a locked screen as a display-service regression. |
| DMS rejects `packageName must match the owner uid` | Shizuku's process is UID 2000 while its supplied Context names Compound. `VirtualDisplayUtils` uses a shell package/op-package/attribution Context for that backend. Keep normal/system-app attribution intact; root is separately handled by Android. |
| Blank window after minimize/restore | Ensure null surface detaches without releasing the virtual display; a new SurfaceView surface must reattach to the existing ID. Handle a replacement surface arriving while initial creation is still pending. |
| Restored size/position is wrong | Keep production geometry in `DesktopWindowState`; save normal bounds before maximize and clamp after workspace changes. Do not recreate the display to resize. |
| Task list is empty or focus/remove fails | Inspect compatibility method signatures and activity-type filtering. Unsupported Binder APIs must report errors, not silently return an empty list. Validate task IDs and exclude system/home/recents tasks. Never replace close with package force-stop. |
| Reflection says illegal access | Hidden Binder proxies can be private classes even when their methods are public. Resolve the known signature and make the method accessible in the privileged process. UI blur additionally uses narrowly scoped HiddenApiBypass exemptions. |
| Old service code or duplicate bindings | Install through Package Manager, bump VERSION_CODE for protocol changes, and inspect generation guards, unbind cleanup and binder-death handling. Shizuku shutdown uses reserved transaction 16777115, not an ordinary public Java method alone. |
| Live blur unavailable | Check hardware/system support before reflection. Inspect getViewRootImpl/createBackgroundBlurDrawable and Android/OEM signatures. Keep the tint fallback and saved radius. Do not restore screenshot blur. |
| Dialog tests race reset/show handlers | Wait for the main looper to become idle after showing the dialog, before driving buttons or reading layout. Keep tests separate from system focus/keyguard failures. |

## Extend coverage

Pure unit tests exercise geometry/state and known hidden task-interface shapes. Add a regression test to the production-used model/helper when fixing those paths. Device tests cover actual Binder identity and surfaces, but cannot replace the manual matrix in [validation.md](validation.md): root versus Shizuku shell, permission denial and restart, Android 12 versus recent Android, external tasks, input after resizing, and real compositor blur.

Record failures and gaps honestly. The current device lacks live blur support; successful fallback checks are not evidence that live blur composes correctly with SurfaceView on every OEM.
