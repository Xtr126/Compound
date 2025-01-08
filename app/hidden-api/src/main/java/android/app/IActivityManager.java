package android.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import java.util.List;

public interface IActivityManager extends IInterface {
    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum)
            throws RemoteException;

    void registerProcessObserver(IProcessObserver observer)
            throws RemoteException;

    abstract class Stub extends Binder implements IActivityManager {

        public static IActivityManager asInterface(IBinder obj) {
            throw new RuntimeException("STUB");
        }
    }
}