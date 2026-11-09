package xtr.compound.server;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.Display;
import android.view.InputEvent;
import android.view.Surface;
import android.view.WindowManagerGlobal;

import androidx.annotation.Keep;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import xtr.compound.IRemoteService;
import xtr.compound.BuildConfig;

/** Privileged binder service used by root, Shizuku, and system-app execution paths. */
public class RemoteService extends IRemoteService.Stub {
    private static final String TAG = "CompoundRemoteService";
    public static final int WINDOWING_MODE_FULLSCREEN = 1;

    private final Context context;
    private final Map<Integer, VirtualDisplay> virtualDisplays = new HashMap<>();
    private final Map<Integer, OwnedWindow> ownedWindows = new HashMap<>();
    private final String ownPackage;
    private boolean destroyed;

    private static final class OwnedWindow {
        final String packageName;
        final String title;
        OwnedWindow(String packageName, String title) {
            this.packageName = packageName;
            this.title = title;
        }
    }

    /** Required by Shizuku before API 13; its Context constructor is preferred on API 13+. */
    public RemoteService() {
        this(resolveFallbackContext());
    }

    @Keep
    public RemoteService(Context context) {
        this.context = context;
        this.ownPackage = BuildConfig.APPLICATION_ID;
    }

    private static Context resolveFallbackContext() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object application = activityThread.getDeclaredMethod("currentApplication").invoke(null);
            if (application instanceof Context) return (Context) application;
        } catch (ReflectiveOperationException ignored) { }
        return null;
    }

    @Override
    public synchronized int renderAppToSurface(Intent launchIntent, Surface surface, int width, int height, int dpi) {
        if (destroyed || context == null || launchIntent == null || launchIntent.getComponent() == null) return -1;
        VirtualDisplay virtualDisplay = null;
        try {
            virtualDisplay = new VirtualDisplayUtils(context).createVirtualDisplay(surface, width, height, dpi);
            if (virtualDisplay == null || virtualDisplay.getDisplay() == null) return -1;
            int displayId = virtualDisplay.getDisplay().getDisplayId();
            virtualDisplays.put(displayId, virtualDisplay);
            String packageName = launchIntent.getComponent().getPackageName();
            String title = launchIntent.getComponent().getClassName();
            ownedWindows.put(displayId, new OwnedWindow(packageName, title));
            if (!startApplication(launchIntent, displayId)) {
                releaseVirtualDisplay(displayId);
                return -1;
            }
            return displayId;
        } catch (Exception e) {
            Log.e(TAG, "Could not create virtual display or launch app", e);
            if (virtualDisplay != null) VirtualDisplayUtils.releaseVirtualDisplay(virtualDisplay);
            return -1;
        }
    }

    private boolean startApplication(Intent launchIntent, int displayId) {
        List<String> command = new ArrayList<>();
        command.add("am");
        command.add("start-activity");
        command.add("-n");
        command.add(launchIntent.getComponent().flattenToString());
        if (launchIntent.getAction() != null) {
            command.add("-a");
            command.add(launchIntent.getAction());
        }
        command.add("--windowingMode");
        command.add(String.valueOf(WINDOWING_MODE_FULLSCREEN));
        command.add("--display");
        command.add(String.valueOf(displayId));
        if (launchIntent.getCategories() != null) {
            for (String category : launchIntent.getCategories()) {
                command.add("-c");
                command.add(category);
            }
        }
        try {
            java.lang.Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append('\n');
            }
            String result = output.toString();
            return process.exitValue() == 0 && !result.contains("Error:") && !result.contains("Exception");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            Log.e(TAG, "Could not start app on display " + displayId, e);
            return false;
        }
    }

    @Override public synchronized void resizeVirtualDisplay(int displayId, int width, int height, int dpi) {
        VirtualDisplay display = virtualDisplays.get(displayId);
        if (display != null) display.resize(width, height, dpi);
    }

    @Override public synchronized void setVirtualDisplaySurface(int displayId, Surface surface) {
        VirtualDisplay display = virtualDisplays.get(displayId);
        if (display != null) display.setSurface(surface);
    }

    /** Releases only Compound-created tasks attached to this virtual display. */
    @Override public synchronized void releaseVirtualDisplay(int displayId) {
        VirtualDisplay display = virtualDisplays.remove(displayId);
        OwnedWindow owner = ownedWindows.remove(displayId);
        if (owner != null) {
            try {
                for (RunningTask task : queryRunningTasks()) {
                    if (task.displayId == displayId && owner.packageName.equals(task.packageName)) {
                        try { removeTaskInternal(task.taskId); }
                        catch (RemoteException e) { Log.w(TAG, "Could not remove owned task " + task.taskId, e); }
                    }
                }
            } catch (RemoteException e) {
                Log.w(TAG, "Could not remove owned task before releasing display " + displayId, e);
            }
        }
        VirtualDisplayUtils.releaseVirtualDisplay(display);
    }

    @Override public List<RunningTask> getRunningTasks() throws RemoteException {
        return queryRunningTasks();
    }

    @Override public synchronized boolean focusTask(int taskId) throws RemoteException {
        Object manager = requireActivityTaskManager();
        if (!isSafeTaskId(taskId)) return false;
        try {
            Method focusedTask = ActivityTaskManagerCompat.findSetFocusedTask(manager.getClass());
            if (focusedTask != null) {
                focusedTask.setAccessible(true);
                focusedTask.invoke(manager, taskId);
            }
            else {
                Method method = ActivityTaskManagerCompat.findMoveTaskToFront(manager.getClass());
                method.setAccessible(true);
                Object[] args = ActivityTaskManagerCompat.moveTaskArguments(method, callingPackage(), taskId);
                if (args.length == 0) throw new NoSuchMethodException("No task ID parameter");
                method.invoke(manager, args);
            }
            return true;
        } catch (InvocationTargetException e) {
            throw remoteError("ActivityTaskManager rejected focusTask", e.getCause());
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw remoteError("ActivityTaskManager cannot focus tasks on this Android version", e);
        }
    }

    @Override public synchronized boolean removeTask(int taskId) throws RemoteException {
        return isSafeTaskId(taskId) && removeTaskInternal(taskId);
    }

    private boolean isSafeTaskId(int taskId) throws RemoteException {
        for (RunningTask task : queryRunningTasks()) if (task.taskId == taskId) return true;
        return false;
    }

    private boolean removeTaskInternal(int taskId) throws RemoteException {
        Object manager = requireActivityTaskManager();
        try {
            Method method = manager.getClass().getMethod("removeTask", int.class);
            method.setAccessible(true);
            Object result = method.invoke(manager, taskId);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (InvocationTargetException e) {
            throw remoteError("ActivityTaskManager rejected removeTask", e.getCause());
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw remoteError("ActivityTaskManager cannot remove tasks on this Android version", e);
        }
    }

    private synchronized List<RunningTask> queryRunningTasks() throws RemoteException {
        List<RunningTask> tasks = new ArrayList<>();
        Object manager = requireActivityTaskManager();
        try {
            Object raw = invokeGetTasks(manager);
            if (!(raw instanceof Collection)) throw new RemoteException("ActivityTaskManager.getTasks returned an unsupported result");
            for (Object info : (Collection<?>) raw) {
                int taskId = intField(info, "taskId", -1);
                int displayId = intField(info, "displayId", Display.DEFAULT_DISPLAY);
                if (taskId < 0 || isHomeOrRecents(info)) continue;
                ComponentName component = componentField(info, "topActivity");
                if (component == null) component = componentField(info, "baseActivity");
                if (component == null) continue;
                String packageName = component.getPackageName();
                if (ownPackage.equals(packageName)) continue;
                String title = taskTitle(info, component);
                OwnedWindow owner = ownedWindows.get(displayId);
                boolean compoundOwned = owner != null && owner.packageName.equals(packageName);
                tasks.add(new RunningTask(taskId, displayId, packageName, title, compoundOwned));
            }
        } catch (InvocationTargetException e) {
            throw remoteError("ActivityTaskManager rejected getTasks", e.getCause());
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw remoteError("ActivityTaskManager task enumeration is unsupported", e);
        }
        return tasks;
    }

    private Object invokeGetTasks(Object manager) throws ReflectiveOperationException {
        Method method = ActivityTaskManagerCompat.findGetTasks(manager.getClass());
        method.setAccessible(true);
        return method.invoke(manager, ActivityTaskManagerCompat.getTasksArguments(method));
    }

    // Runs in the unrestricted root/shell UserService; failures are reported to the UI.
    @android.annotation.SuppressLint("BlockedPrivateApi")
    private Object getActivityTaskManager() {
        try {
            Class<?> at = Class.forName("android.app.ActivityTaskManager");
            Method getService = at.getDeclaredMethod("getService");
            getService.setAccessible(true);
            return getService.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "ActivityTaskManager hidden service is unavailable", e);
            return null;
        }
    }

    private Object requireActivityTaskManager() throws RemoteException {
        Object manager = getActivityTaskManager();
        if (manager == null) throw new RemoteException("ActivityTaskManager binder is unavailable in this privileged process");
        return manager;
    }

    private static RemoteException remoteError(String message, Throwable cause) {
        RemoteException error = new RemoteException(message + (cause == null ? "" : ": " + cause.getMessage()));
        if (cause != null) {
            try { error.initCause(cause); } catch (IllegalStateException ignored) { }
        }
        return error;
    }

    private static int intField(Object object, String name, int fallback) {
        try {
            Field field = object.getClass().getField(name);
            field.setAccessible(true);
            return field.getInt(object);
        } catch (ReflectiveOperationException | RuntimeException ignored) { return fallback; }
    }

    private static ComponentName componentField(Object object, String name) {
        try {
            Field field = object.getClass().getField(name);
            field.setAccessible(true);
            Object value = field.get(object);
            return value instanceof ComponentName ? (ComponentName) value : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private static boolean isHomeOrRecents(Object info) {
        int type = intField(info, "topActivityType", -1);
        if (type < 0) {
            try {
                Method getActivityType = info.getClass().getMethod("getActivityType");
                getActivityType.setAccessible(true);
                type = (int) getActivityType.invoke(info);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                try {
                    Field configurationField = info.getClass().getField("configuration");
                    configurationField.setAccessible(true);
                    Object configuration = configurationField.get(info);
                    Field windowConfigurationField = configuration.getClass().getField("windowConfiguration");
                    windowConfigurationField.setAccessible(true);
                    Object windowConfiguration = windowConfigurationField.get(configuration);
                    Method getActivityType = windowConfiguration.getClass().getMethod("getActivityType");
                    getActivityType.setAccessible(true);
                    type = (int) getActivityType.invoke(windowConfiguration);
                } catch (ReflectiveOperationException | RuntimeException ignoredAgain) { return true; }
            }
        }
        // Standard activity tasks are type 1; omit home, recents, assistant, undefined,
        // and any new system task types until their behavior is explicitly supported.
        return !ActivityTaskManagerCompat.isSupportedActivityType(type);
    }

    private static String callingPackage() {
        return android.os.Process.myUid() == 0 ? "android" : "com.android.shell";
    }

    private static String taskTitle(Object info, ComponentName component) {
        try {
            Field field = info.getClass().getField("taskDescription");
            field.setAccessible(true);
            Object description = field.get(info);
            if (description != null) {
                Method getLabel = description.getClass().getMethod("getLabel");
                getLabel.setAccessible(true);
                Object label = getLabel.invoke(description);
                if (label != null) return label.toString();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return component.getClassName();
    }

    @Override public void showPowerMenu() {
        try { new ProcessBuilder("input", "keyevent", "--longpress", "POWER").start(); }
        catch (IOException e) { Log.e(TAG, "Could not show power menu", e); }
    }

    @Override public void injectInputEvent(InputEvent event, int displayId) {
        if (event == null) return;
        VirtualDisplayUtils.setDisplayId(event, displayId);
        Input.injectInputEvent(event);
    }

    @Override public int getBaseDisplayDensity(int displayId) throws RemoteException {
        return WindowManagerGlobal.getWindowManagerService().getBaseDisplayDensity(displayId);
    }

    @Override public void getBaseDisplaySize(int displayId, Point baseSize) throws RemoteException {
        WindowManagerGlobal.getWindowManagerService().getBaseDisplaySize(displayId, baseSize);
    }

    /** Handles Shizuku's reserved UserService transaction (AIDL ID 16777114 + FIRST_CALL_TRANSACTION). */
    public synchronized void destroy() {
        shutdown();
        int uid = android.os.Process.myUid();
        if (uid == 0 || uid == 2000) System.exit(0);
    }

    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags)
            throws RemoteException {
        if (code == 16777115) {
            destroy();
            if (reply != null) reply.writeNoException();
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }

    public synchronized void shutdown() {
        if (destroyed) return;
        destroyed = true;
        for (Integer displayId : new ArrayList<>(virtualDisplays.keySet())) releaseVirtualDisplay(displayId);
    }
}
