package android.app;

import android.os.IBinder;
import android.os.IInterface;

public interface IActivityManager extends IInterface {
    abstract class Stub extends android.os.Binder implements IActivityManager {
        public static IActivityManager asInterface(android.os.IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
    ContentProviderHolder getContentProviderExternal(String name, int userId,
                                                          IBinder token, String tag);
}
