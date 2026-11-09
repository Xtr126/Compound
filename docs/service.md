# Privileged task service

Compound connects to its privileged service in this order: preinstalled system app, root, then Shizuku. The app reports connection and permission states through `RemoteServiceHelper`; when root is unavailable, Shizuku must be running and Compound must be granted permission. The `Retry` action should rerun root detection before checking Shizuku.

The root and Shizuku service use the same AIDL interface. Shizuku's user service receives the framework-created `Context` constructor on API 13 and later; the no-argument constructor remains for older Shizuku versions. The service's `destroy()` cleans up its virtual displays and exits the Shizuku user-service process. The root service uses the same cleanup path when Android destroys it.

## Tasks

Task enumeration and focus/removal run inside the privileged service through the hidden `ActivityTaskManager` binder. Reflection is limited to known `getTasks` signatures and the `moveTaskToFront` shape used by supported Android versions. If the binder or method shape is unavailable, the AIDL call raises Binder-marshallable `IllegalStateException` (transport failures may still raise `RemoteException`) so the UI can report that task management is unavailable. It must not present a failed query as an empty task list.

The service omits Compound itself and tasks whose activity type is not explicitly standard, which excludes home, recents, assistant, and unknown system task types. It marks a task as Compound-owned only when the task's display and package match a virtual display launched by Compound. Closing a Compound window removes matching tasks for that display and releases the virtual display. A task removal request validates that the task is still present before removing it. A focus request targets a current task ID.

The task API is hidden and OEM builds can differ. Validate enumeration, focus, removal, virtual-display cleanup, and Shizuku service teardown on Android 12–16 with both root and Shizuku shell backends. If an Android release changes a reflected method signature, add its exact signature to the compatibility helper and a unit test; do not parse `dumpsys` output as a fallback.
