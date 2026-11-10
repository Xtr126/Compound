/home/hp/.bash_profile: line 6: /home/hp/.cargo/env: No such file or directory
package xtr.compound.server;

import android.os.IBinder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Known-signature bridge to the privileged status bar Binder service. */
final class StatusBarCompat {
    private StatusBarCompat() { }

    static void expandNotificationsPanel() throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Method getService = serviceManager.getMethod("getService", String.class);
        IBinder binder = (IBinder) getService.invoke(null, "statusbar");
        if (binder == null) throw new IllegalStateException("Android status bar service is unavailable");

        Class<?> serviceInterface = Class.forName("com.android.internal.statusbar.IStatusBarService");
        Class<?> stub = Class.forName("com.android.internal.statusbar.IStatusBarService$Stub");
        Method asInterface = stub.getMethod("asInterface", IBinder.class);
        Object service = asInterface.invoke(null, binder);
        if (service == null) throw new IllegalStateException("Could not connect to Android status bar service");
        Method expand;
        try { expand = serviceInterface.getMethod("expandNotificationsPanel"); }
        catch (NoSuchMethodException e) {
            throw new UnsupportedOperationException("This Android version does not support expanding the notification panel", e);
        }
        expand.setAccessible(true);
        try { expand.invoke(service); }
        catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw e;
        }
    }
}
