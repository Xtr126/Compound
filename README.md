# Compound
Compound is a sleek modern desktop for Android with custom app window decorations and window management.
![image](https://github.com/user-attachments/assets/25c09157-7c6d-4b46-8c8e-b2957bed84cf)

It hosts apps on virtual displays, with draggable/resizable windows and a running-app taskbar. Android 12 (API 31) or newer is required.

## Setup

Install the APK from the latest GitHub Release, or download the debug APK from a pull request's **Android build** GitHub Actions artifact. You can also build it locally. Compound uses its system-app service when installed with the necessary platform privileges; otherwise it tries root first, then Shizuku.

For non-root devices, [install Shizuku](https://shizuku.rikka.app/download/), open it, and follow its wireless-debugging startup instructions. Open Compound, choose **Grant access**, and accept Shizuku's permission dialog. Non-root Shizuku normally needs restarting after a device reboot. Setup explains missing permissions or connection errors and offers retry. Open it again from Compound settings or by holding the start-menu button.

## Desktop controls

- Launch apps from the start menu. Drag their title bars, resize from the left or right edge and top or bottom edge, or drag any corner to resize width and height together. Screen-reader users can focus a resize handle and use its custom actions to change the window by 32 dp.
- Minimize preserves the app session; select its taskbar entry to restore it.
- Taskbar entries use each app's display name. In Compound settings, enable **Show taskbar icons only** to center icons in the taskbar; the active task has a bar beneath its icon. In icon-only mode, long-press an icon to close that task.
- Maximize toggles between workspace size and the saved normal bounds.
- Close removes the hosted task(s) and releases its virtual display. Closing an external task removes that task, without force-stopping its package.
- The Home button minimizes all Compound windows. Back targets the focused hosted app; Recents refreshes the running-task list.
- The taskbar Wi-Fi button opens Android's Wi-Fi panel, falling back to Wi-Fi settings when the panel is unavailable. The battery button opens the system battery/power overview, falling back to battery saver settings and then general Settings if needed.

Open **Compound settings** using the start-menu gear. Taskbar scope defaults to **Compound windows**; opt into **All Android tasks** to also focus and close accessible tasks on their existing displays. Home/recents and Compound itself are excluded. Android/OEM task permissions can restrict these operations.

Appearance sliders apply immediately to the taskbar, start-menu and Compound settings dialog backgrounds:

| Setting | Range | Default |
| --- | --- | --- |
| Background opacity | 0–100% | 80% |
| Blur radius | 0–100 dp | 25 dp |

Opacity changes the background tint only. Zero radius turns blur off. Settings persist across restarts, and **Reset appearance** restores the opacity and blur defaults; taskbar scope stays as selected. Device settings remain available separately.

Live blur uses Android's compositor through a hidden framework helper. Device support, system blur settings, hidden-API restrictions, and SurfaceView composition affect availability. When unavailable, Compound uses legacy snapshot blur with working opacity and radius controls. Snapshots refresh when the menu opens or its background changes size; capture failure keeps a translucent tint. See [appearance notes](docs/appearance.md), [service notes](docs/service.md), and [window notes](docs/windows.md).

## Build and validation

Use JDK 21 and an Android SDK with `platforms;android-37.0` and build tools 36.0.0. Set `sdk.dir` in your untracked `local.properties` or configure `ANDROID_HOME`.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The APK is written to `app/build/outputs/apk/debug/`. GitHub Actions runs these checks on pull requests, validates the Gradle wrapper, caches Gradle dependencies, and uploads the APK and diagnostic reports. Pushing a `v*` tag (for example, `v1.1`) runs the same checks and publishes a GitHub Release with the debug-signed APK and generated release notes. Update `versionName` and `versionCode` in `app/build.gradle.kts` before tagging a new app version. No signing secrets are needed for the debug build.

Privileged and compositor behavior needs device testing in addition to unit tests. See [the validation checklist](docs/validation.md) for coverage and reproduction steps.

For device setup, focused instrumentation runs, log capture, and common failure diagnosis, see the [development guide](docs/development.md).

Window controls show a restore icon while maximized. In task-name mode, taskbar entries show distinct minimized and maximized state icons. Dark-mode changes preserve hosted sessions.
