/home/hp/.bash_profile: line 6: /home/hp/.cargo/env: No such file or directory
package xtr.compound.server;

import android.view.WindowManagerGlobal;

import java.lang.reflect.Method;

/** Checked access to WindowManager's system global-actions operation. */
final class GlobalActionsCompat {
    private GlobalActionsCompat() { }

    static void show() throws ReflectiveOperationException {
        Object windowManager = WindowManagerGlobal.getWindowManagerService();
        if (windowManager == null) throw new IllegalStateException("Window manager service is unavailable");
        Method showGlobalActions = windowManager.getClass().getMethod("showGlobalActions");
        showGlobalActions.setAccessible(true);
        showGlobalActions.invoke(windowManager);
    }
}
