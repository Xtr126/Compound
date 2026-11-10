# Validation checklist

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` with JDK 21. CI uses the same commands. Unit tests cover pure state/geometry/policy; they cannot establish privileged Binder or compositor compatibility.

## Device scenarios

Repeat on Android 12 and a recent Android version, with root and with Shizuku running as shell where available:

1. Open with no privilege provider: setup should explain install/start/retry; Later leaves the launcher usable without crashing. Deny permission, reopen setup, then grant permission. Start multiple launches during connection and verify one binding. Stop/restart Shizuku and recover through setup.
2. Launch two apps and multiple tasks from one package. Compound-only scope lists hosted windows, including minimized ones. All-tasks scope adds accessible external tasks but excludes home/recents/Compound. Focus external tasks on their existing display. Close one task without force-stopping its package; stale IDs report failure and refresh.
3. Enter text or navigate within an app, minimize it, restore from the taskbar and verify state is retained. Repeat maximize/restore after dragging and resizing; confirm restored bounds remain within the workspace. Verify touch coordinates near all corners after resize. Close during launch, close twice, and lose the service with windows open.
4. Move animated content behind the taskbar/start menu and verify blur is live. Adjust radius to zero and maximum; adjust opacity through both endpoints, keeping icons/text readable. Restart Compound and verify persistence; reset appearance. Disable system window blur and confirm fallback/explanation, then re-enable it and verify saved radius is restored.
5. Pause/resume and destroy/recreate the Activity. Confirm task polling stops while paused and resources/listeners are cleaned on destruction. Window sessions are closed when the desktop Activity is destroyed; no process-death session recovery is promised.
6. Switch taskbar display between app names and icons only with zero, one, and several tasks. Confirm names come from application labels, icons are centered in the taskbar app area, the active indicator moves when another task is selected, and long-press closes a task in icon-only mode.

## Recorded results

Results from the implementation run are recorded below before delivery. A build pass alone must not be described as an Android 12/root runtime pass.

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
