package xtr.compound.server;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ipc.RootService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;
import xtr.compound.BuildConfig;
import xtr.compound.IRemoteService;

/** Resolves the privileged service once and reports setup/connection state to the UI. */
public final class RemoteServiceHelper {
    public enum State {
        CONNECTING, CONNECTED, SHIZUKU_UNAVAILABLE, PERMISSION_REQUIRED,
        PERMISSION_DENIED, ERROR
    }

    public interface StateListener {
        void onStateChanged(State state);
    }

    public interface RemoteServiceConnectionCallback {
        void onServiceConnected(IRemoteService service);
        default void onServiceUnavailable(State state, String message) { }
    }

    private static final int REQUEST_CODE = 4701;
    private static final long CONNECTION_TIMEOUT_MS = 15_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final List<StateListener> STATE_LISTENERS = new ArrayList<>();
    private static final List<RemoteServiceConnectionCallback> PENDING_CALLBACKS = new ArrayList<>();
    private static int connectionGeneration;
    private static Context appContext;
    private static IRemoteService service;
    private static State state = State.SHIZUKU_UNAVAILABLE;
    private static String statusMessage = "Start Shizuku or grant Compound root access.";
    private static boolean initialized;
    private static boolean resolving;
    private static boolean binding;
    private static boolean permissionWasRequested;
    private static boolean systemApp;
    public static volatile boolean isRootService;
    private static ServiceConnection activeConnection;
    private static Shizuku.UserServiceArgs activeShizukuArgs;
    private static boolean activeShizukuService;
    private static IBinder activeBinder;
    private static IBinder.DeathRecipient activeDeathRecipient;
    private static final Runnable TIMEOUT = () -> {
        if (state != State.CONNECTING) return;
        boolean wasRoot = isRootService;
        releaseActiveConnection();
        binding = false;
        connectionGeneration++;
        if (wasRoot && Shizuku.pingBinder()) resolveShizuku();
        else setState(State.ERROR, "The privileged service did not connect. Retry to try again.");
    };

    private static final Shizuku.OnBinderReceivedListener BINDER_RECEIVED = () -> MAIN.post(() -> resolveShizuku());
    private static final Shizuku.OnBinderDeadListener BINDER_DEAD = () -> MAIN.post(() -> {
        if (isRootService || systemApp) return;
        MAIN.removeCallbacks(TIMEOUT);
        connectionGeneration++;
        if (activeBinder != null && activeDeathRecipient != null) {
            try { activeBinder.unlinkToDeath(activeDeathRecipient, 0); } catch (RuntimeException ignored) { }
        }
        activeConnection = null;
        activeShizukuArgs = null;
        activeBinder = null;
        activeDeathRecipient = null;
        activeShizukuService = false;
        service = null;
        binding = false;
        if (!systemApp) setState(State.SHIZUKU_UNAVAILABLE, "Shizuku stopped. Start it and tap Retry.");
    });
    private static final Shizuku.OnRequestPermissionResultListener PERMISSION_RESULT = (requestCode, grantResult) -> MAIN.post(() -> {
        if (requestCode != REQUEST_CODE) return;
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            permissionWasRequested = false;
            bindShizukuService();
        } else {
            setState(State.PERMISSION_DENIED, "Shizuku access was denied. Grant Compound access in Shizuku, then retry.");
        }
    });

    private RemoteServiceHelper() { }

    public static void initialize(Context context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> initialize(context));
            return;
        }
        appContext = context.getApplicationContext();
        if (!initialized) {
            initialized = true;
            try {
                Shizuku.addBinderReceivedListenerSticky(BINDER_RECEIVED);
                Shizuku.addBinderDeadListener(BINDER_DEAD);
                Shizuku.addRequestPermissionResultListener(PERMISSION_RESULT);
            } catch (RuntimeException ignored) {
                // Shizuku APIs are only available after its binder has arrived.
            }
        }
        if (isSystemApp(appContext)) {
            systemApp = true;
            connectSystemApp();
        } else {
            resolveRootFirst();
        }
    }

    public static void addStateListener(StateListener listener) {
        if (listener == null) return;
        MAIN.post(() -> {
            if (!STATE_LISTENERS.contains(listener)) STATE_LISTENERS.add(listener);
            listener.onStateChanged(state);
        });
    }

    public static void removeStateListener(StateListener listener) {
        if (listener != null) MAIN.post(() -> STATE_LISTENERS.remove(listener));
    }

    public static State getState() { return state; }
    public static String getStatusMessage() { return statusMessage; }

    public static void requestPermission() {
        MAIN.post(() -> {
            if (appContext == null) {
                setState(State.ERROR, "Compound is not ready to request Shizuku permission. Retry setup.");
                return;
            }
            if (!Shizuku.pingBinder()) {
                setState(State.SHIZUKU_UNAVAILABLE, "Start Shizuku, then tap Retry.");
                return;
            }
            try {
                requestShizukuPermissionOrBind();
            } catch (RuntimeException e) {
                permissionWasRequested = false;
                setState(State.ERROR, "Could not request Shizuku permission: " + messageOf(e));
            }
        });
    }

    public static void retry(Context context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> retry(context));
            return;
        }
        if (service != null && service.asBinder().isBinderAlive()) {
            setState(State.CONNECTED, statusMessage);
            return;
        }
        releaseActiveConnection();
        connectionGeneration++;
        MAIN.removeCallbacks(TIMEOUT);
        service = null;
        binding = false;
        resolving = false;
        permissionWasRequested = false;
        initialize(context);
    }

    public static boolean isSystemApp(Context context) {
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
            return (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static void getInstance(Context context, RemoteServiceConnectionCallback callback) {
        if (callback != null) MAIN.post(() -> {
            if (service != null) callback.onServiceConnected(service);
            else {
                if (!PENDING_CALLBACKS.contains(callback)) PENDING_CALLBACKS.add(callback);
                initialize(context);
            }
        });
        else initialize(context);
    }

    private static void resolveRootFirst() {
        if (resolving || binding || service != null || appContext == null) return;
        resolving = true;
        setState(State.CONNECTING, "Checking root access, then Shizuku.");
        WORKER.execute(() -> {
            try {
                Shell.getShell(shell -> {
                    final boolean root = shell != null && shell.isRoot();
                    MAIN.post(() -> {
                        resolving = false;
                        if (root) bindRootService();
                        else resolveShizuku();
                    });
                });
            } catch (Throwable error) {
                MAIN.post(() -> {
                    resolving = false;
                    resolveShizuku();
                });
            }
        });
    }

    private static void resolveShizuku() {
        if (systemApp || resolving) return;
        if (service != null || binding) return;
        if (!Shizuku.pingBinder()) {
            setState(State.SHIZUKU_UNAVAILABLE, "No root access detected. Start Shizuku, then tap Retry.");
            return;
        }
        try {
            if (Shizuku.isPreV11()) {
                permissionWasRequested = false;
                setState(State.ERROR, "This Shizuku version is too old. Update Shizuku to version 11 or later.");
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                permissionWasRequested = false;
                bindShizukuService();
            } else {
                permissionWasRequested = false;
                setState(State.PERMISSION_REQUIRED, "Grant Compound permission in Shizuku.");
            }
        } catch (RuntimeException e) {
            permissionWasRequested = false;
            setState(State.ERROR, "Could not check Shizuku permission: " + messageOf(e));
        }
    }

    /** Must be called from the explicit Grant access action; Shizuku may show user-facing UI. */
    private static void requestShizukuPermissionOrBind() {
        if (Shizuku.isPreV11()) {
            permissionWasRequested = false;
            setState(State.ERROR, "This Shizuku version is too old. Update Shizuku to version 11 or later.");
        } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            permissionWasRequested = false;
            bindShizukuService();
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            permissionWasRequested = false;
            setState(State.PERMISSION_DENIED,
                    "Shizuku permission was denied before. Grant Compound access from Shizuku, then retry.");
        } else {
            permissionWasRequested = true;
            setState(State.PERMISSION_REQUIRED, "Waiting for Shizuku permission.");
            Shizuku.requestPermission(REQUEST_CODE);
        }
    }

    private static void connectSystemApp() {
        if (service != null) return;
        isRootService = false;
        service = new RemoteService(appContext);
        onConnected(service);
    }

    private static void bindRootService() {
        if (binding || service != null) return;
        binding = true;
        isRootService = true;
        int generation = ++connectionGeneration;
        setState(State.CONNECTING, "Connecting to the root service.");
        MAIN.removeCallbacks(TIMEOUT);
        MAIN.postDelayed(TIMEOUT, CONNECTION_TIMEOUT_MS);
        RemoteServiceConnection connection = new RemoteServiceConnection(null, generation);
        activeConnection = connection;
        activeShizukuArgs = null;
        activeShizukuService = false;
        try {
            RootService.bind(new Intent(appContext, RootRemoteService.class), connection);
        } catch (RuntimeException e) {
            MAIN.removeCallbacks(TIMEOUT);
            releaseActiveConnection();
            binding = false;
            if (Shizuku.pingBinder()) resolveShizuku();
            else setState(State.ERROR, "Could not bind root service: " + messageOf(e));
        }
    }

    private static void bindShizukuService() {
        if (binding || service != null) return;
        if (!Shizuku.pingBinder()) {
            setState(State.SHIZUKU_UNAVAILABLE, "Shizuku stopped. Start it and tap Retry.");
            return;
        }
        binding = true;
        isRootService = false;
        int generation = ++connectionGeneration;
        setState(State.CONNECTING, "Connecting to Shizuku.");
        MAIN.removeCallbacks(TIMEOUT);
        MAIN.postDelayed(TIMEOUT, CONNECTION_TIMEOUT_MS);
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                new ComponentName(appContext, RemoteService.class.getName()))
                .daemon(false).processNameSuffix("service").debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE);
        RemoteServiceConnection connection = new RemoteServiceConnection(null, generation);
        activeConnection = connection;
        activeShizukuArgs = args;
        activeShizukuService = true;
        try {
            Shizuku.bindUserService(args, connection);
        } catch (RuntimeException e) {
            MAIN.removeCallbacks(TIMEOUT);
            releaseActiveConnection();
            binding = false;
            setState(State.ERROR, "Could not bind Shizuku service: " + messageOf(e));
        }
    }

    private static void onConnected(IRemoteService connected) {
        service = connected;
        binding = false;
        MAIN.removeCallbacks(TIMEOUT);
        setState(State.CONNECTED, isRootService ? "Connected with root access." : "Privileged service connected.");
        List<RemoteServiceConnectionCallback> callbacks = new ArrayList<>(PENDING_CALLBACKS);
        PENDING_CALLBACKS.clear();
        for (RemoteServiceConnectionCallback callback : callbacks) callback.onServiceConnected(connected);
    }

    private static void releaseActiveConnection() {
        ServiceConnection connection = activeConnection;
        Shizuku.UserServiceArgs args = activeShizukuArgs;
        boolean shizukuService = activeShizukuService;
        IBinder binder = activeBinder;
        IBinder.DeathRecipient deathRecipient = activeDeathRecipient;
        activeConnection = null;
        activeShizukuArgs = null;
        activeShizukuService = false;
        activeBinder = null;
        activeDeathRecipient = null;
        if (binder != null && deathRecipient != null) {
            try { binder.unlinkToDeath(deathRecipient, 0); } catch (RuntimeException ignored) { }
        }
        if (connection == null) return;
        if (shizukuService) {
            if (args != null && Shizuku.pingBinder()) {
                try { Shizuku.unbindUserService(args, connection, true); }
                catch (RuntimeException e) { android.util.Log.w("RemoteServiceHelper", "Could not unbind Shizuku service", e); }
            }
        } else {
            try { RootService.unbind(connection); }
            catch (RuntimeException e) { android.util.Log.w("RemoteServiceHelper", "Could not unbind root service", e); }
        }
    }

    private static void onConnectionLost(int generation, String reason) {
        if (generation != connectionGeneration) return;
        boolean wasRoot = isRootService;
        releaseActiveConnection();
        service = null;
        binding = false;
        MAIN.removeCallbacks(TIMEOUT);
        connectionGeneration++;
        if (wasRoot && Shizuku.pingBinder()) resolveShizuku();
        else setState(State.ERROR, reason);
    }

    private static void setState(State newState, String message) {
        state = newState;
        statusMessage = message;
        for (StateListener listener : new ArrayList<>(STATE_LISTENERS)) listener.onStateChanged(newState);
        if (newState != State.CONNECTING && newState != State.CONNECTED && !PENDING_CALLBACKS.isEmpty()) {
            List<RemoteServiceConnectionCallback> callbacks = new ArrayList<>(PENDING_CALLBACKS);
            PENDING_CALLBACKS.clear();
            for (RemoteServiceConnectionCallback callback : callbacks) callback.onServiceUnavailable(newState, message);
        }
    }

    private static String messageOf(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    public static class RemoteServiceConnection implements ServiceConnection {
        private final RemoteServiceConnectionCallback callback;
        private final int generation;
        public RemoteServiceConnection(RemoteServiceConnectionCallback callback) { this(callback, connectionGeneration); }
        private RemoteServiceConnection(RemoteServiceConnectionCallback callback, int generation) {
            this.callback = callback;
            this.generation = generation;
        }

        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            MAIN.post(() -> {
                if (generation != connectionGeneration) return;
                IBinder.DeathRecipient deathRecipient = () -> MAIN.post(() ->
                        onConnectionLost(generation, "Privileged service binder died. Tap Retry to reconnect."));
                try {
                    binder.linkToDeath(deathRecipient, 0);
                    activeBinder = binder;
                    activeDeathRecipient = deathRecipient;
                } catch (android.os.RemoteException e) {
                    onConnectionLost(generation, "Privileged service binder died before connecting. Tap Retry.");
                    return;
                }
                IRemoteService connected = IRemoteService.Stub.asInterface(binder);
                onConnected(connected);
                if (callback != null) callback.onServiceConnected(connected);
            });
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            MAIN.post(() -> onConnectionLost(generation,
                    "Privileged service disconnected. Tap Retry to reconnect."));
        }
    }
}
