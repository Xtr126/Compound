package xtr.compound.server;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.lang.reflect.Method;

public class ActivityTaskManagerCompatTest {
    static class Android12Shape {
        public void getTasks(int maxNum, boolean filterOnlyVisibleRecents) { }
    }

    static class Android13Shape {
        public void getTasks(int maxNum, boolean filterOnlyVisibleRecents, boolean keepIntentExtra) { }
    }

    static class Android16Shape {
        public void getTasks(int maxNum, boolean filterOnlyVisibleRecents, boolean keepIntentExtra, int displayId) { }
        public void moveTaskToFront(AppThread appThread, String callingPackage, int taskId, int flags, android.os.Bundle options) { }
    }

    interface AppThread { }

    static class UnsupportedShape {
        public void getTasks(String unsupported) { }
        public void getTasks(int max, int unknownMeaning) { }
    }

    @Test public void selectsLongestSupportedTaskListingSignatureAndBuildsTypedArguments() throws Exception {
        Method method = ActivityTaskManagerCompat.findGetTasks(Android16Shape.class);
        assertEquals(4, method.getParameterCount());
        assertArrayEquals(new Object[]{100, false, false, -1}, ActivityTaskManagerCompat.getTasksArguments(method));
    }

    @Test public void supportsAndroid12And13TaskListingShapes() throws Exception {
        Method android12 = ActivityTaskManagerCompat.findGetTasks(Android12Shape.class);
        Method android13 = ActivityTaskManagerCompat.findGetTasks(Android13Shape.class);
        assertArrayEquals(new Object[]{100, false}, ActivityTaskManagerCompat.getTasksArguments(android12));
        assertArrayEquals(new Object[]{100, false, false}, ActivityTaskManagerCompat.getTasksArguments(android13));
    }

    @Test public void buildsMoveToFrontArgumentsUsingPrivilegedCallingPackage() throws Exception {
        Method method = ActivityTaskManagerCompat.findMoveTaskToFront(Android16Shape.class);
        assertArrayEquals(new Object[]{null, "com.android.shell", 42, 0, null},
                ActivityTaskManagerCompat.moveTaskArguments(method, "com.android.shell", 42));
    }

    @Test public void unsupportedTaskListingIsExplicit() {
        assertThrows(NoSuchMethodException.class, () -> ActivityTaskManagerCompat.findGetTasks(UnsupportedShape.class));
    }
}
