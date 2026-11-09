package xtr.compound.server;

import java.lang.reflect.Method;

/** Reflection shape compatibility for the ActivityTaskManager AIDL variants in Android 12–16. */
final class ActivityTaskManagerCompat {
    private ActivityTaskManagerCompat() { }

    static Method findGetTasks(Class<?> type) throws NoSuchMethodException {
        Class<?>[][] signatures = {
                {int.class, boolean.class, boolean.class, int.class},
                {int.class, boolean.class, boolean.class},
                {int.class, boolean.class}
        };
        for (Class<?>[] signature : signatures) {
            try { return type.getMethod("getTasks", signature); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException("No supported ActivityTaskManager.getTasks signature");
    }

    static Object[] getTasksArguments(Method method) {
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        args[0] = 100;
        for (int i = 1; i < types.length; i++) {
            if (types[i] == boolean.class) args[i] = Boolean.FALSE;
            else args[i] = Integer.valueOf(-1);
        }
        return args;
    }

    static Method findMoveTaskToFront(Class<?> type) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (!"moveTaskToFront".equals(method.getName())) continue;
            Class<?>[] p = method.getParameterTypes();
            if (p.length == 5 && !p[0].isPrimitive() && p[1] == String.class
                    && p[2] == int.class && p[3] == int.class
                    && "android.os.Bundle".equals(p[4].getName())) return method;
        }
        throw new NoSuchMethodException("No supported ActivityTaskManager.moveTaskToFront signature");
    }

    static Method findSetFocusedTask(Class<?> type) {
        try { return type.getMethod("setFocusedTask", int.class); }
        catch (NoSuchMethodException ignored) { return null; }
    }

    static boolean isSupportedActivityType(int activityType) {
        return activityType == 1; // ActivityManager.ACTIVITY_TYPE_STANDARD
    }

    static Object[] moveTaskArguments(Method method, String callingPackage, int taskId) {
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        boolean assignedTaskId = false;
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (type == int.class || type == Integer.class) {
                args[i] = assignedTaskId ? 0 : taskId;
                assignedTaskId = true;
            } else if (type == String.class) args[i] = callingPackage;
            else if (type == boolean.class) args[i] = false;
            else if (type == long.class) args[i] = 0L;
            else args[i] = null;
        }
        return assignedTaskId ? args : new Object[0];
    }
}
