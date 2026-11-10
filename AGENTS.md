# Working on Compound

Compound hosts Android applications on virtual displays inside a desktop Activity. The app uses Kotlin/Java, ViewBinding, AIDL, Gradle 9.5.1 and AGP 9.2.1. Use an installed JDK supported by the checked-in Gradle and Android Gradle Plugin; the build does not pin a daemon JDK. MinSdk is 31, compileSdk 37 and targetSdk 35. Keep SDK/dependency upgrades separate unless required to fix a demonstrated issue.

## Architecture and privileges

- MainActivity coordinates onboarding, taskbar, settings, and lifecycle. Launcher entries are separate from running tasks.
- DesktopWindowController owns window state, bounds, surfaces and display ownership. Minimize preserves the session; restore reattaches its surface. Maximize/restore must retain normal bounds.
- RemoteServiceHelper resolves system-app mode, otherwise root first and Shizuku second. Permission requests require user interaction. Surface unavailable/denied/error states and never leave callbacks silently pending.
- RemoteService executes privileged task/display/input calls. Keep these off the UI thread. The UI must not invoke privileged framework services directly.
- Use actual task IDs for focus/remove; never force-stop a package to close one task. All-device task visibility is opt-in, with Compound windows the default. Exclude home/recents and Compound itself.
- Keep framework reflection in compatibility helpers. Check known signatures, handle unsupported APIs explicitly, and do not parse dumpsys output or copy Binder transaction numbers for task APIs. Hidden API stubs must remain compileOnly.
- Background blur is compositor/device dependent. Maintain an opacity fallback, restore original backgrounds and remove listeners on detach. The `HiddenApiBypass` dependency is used only for narrowly scoped `View`, `ViewRootImpl` and `BackgroundBlurDrawable` access. Use live blur when available and the on-demand legacy snapshot fallback otherwise.

## Validation

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. CI runs the same checks and uploads APK/reports. Do not commit local.properties, SDK paths, build output, or caches.

Use meaningful tests for state transitions, geometry, settings and task filtering. Validate root and Shizuku (including shell identity), denial/restart, stale task IDs, minimize/restore without relaunch, resizing/input, and enabled/disabled live blur on Android 12 and a recent version. Report unavailable device coverage honestly; a successful build does not establish privileged runtime compatibility.

See `docs/development.md` for debugging, instrumentation commands and known pitfalls. Bump the app version code when changing the service protocol so Shizuku does not reuse an incompatible service.

Update README and relevant docs/ guides with behavior, setup, API limitations and manual checks whenever those change. Document contributions from each delegated workstream. Do not commit or push unrelated user changes.
