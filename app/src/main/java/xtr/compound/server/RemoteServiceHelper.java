package xtr.compound.server;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ipc.RootService;

import rikka.shizuku.Shizuku;
import xtr.compound.BuildConfig;
import xtr.compound.IRemoteService;

public class RemoteServiceHelper {

    private static IRemoteService mService = null;
    public static boolean isRootService = true;

    public interface RemoteServiceConnectionCallback {
        void onServiceConnected(IRemoteService service);
    }
    public static class RemoteServiceConnection implements ServiceConnection {
        RemoteServiceConnectionCallback cb;
        public RemoteServiceConnection(RemoteServiceConnectionCallback cb){
            this.cb = cb;
        }
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            cb.onServiceConnected(IRemoteService.Stub.asInterface(service));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    }

    private static void bindShizukuService(Context context, RemoteServiceConnection connection) {
        Shizuku.UserServiceArgs userServiceArgs =
            new Shizuku.UserServiceArgs(new ComponentName(context, RemoteService.class.getName()))
                    .daemon(false)
                    .processNameSuffix("service")
                    .debuggable(BuildConfig.DEBUG)
                    .version(BuildConfig.VERSION_CODE);
        Shizuku.bindUserService(userServiceArgs, connection);
    }

    public static boolean isSystemApp(Context context) {
        try {
            String packageName = context.getPackageName();
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            return (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    
    private static void getInstanceAsSystemApp(Context context, RemoteServiceConnectionCallback callback) {
        if (mService == null) {
            mService = new RemoteService(context.getApplicationContext());
        }
        if (callback != null) callback.onServiceConnected(mService);
    }

    /**
     * Get instance of remote service
     * @param context App context
     * @param callback Callback when instance of remote service is obtained
     * Check if we are a system app
     * If system app -> create instance of service in same process
     * If root is available try to start server and obtain instance
     * or if Shizuku is available try to obtain service instance
     */
    public static void getInstance(Context context, RemoteServiceConnectionCallback callback) {
        if (isSystemApp(context)) {
            getInstanceAsSystemApp(context, callback);
        } else {
            if (mService != null) {
                if (callback != null) callback.onServiceConnected(mService);
            } else {
                RemoteServiceConnection connection = new RemoteServiceConnection(callback);
                Boolean hasRootAccess = Shell.isAppGrantedRoot();
                if (hasRootAccess == null || !hasRootAccess) {
                    isRootService = false;
                    if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
                        if (callback != null) bindShizukuService(context, connection);
                } else {
                    isRootService = true;
                    Intent intent = new Intent(context, RootRemoteService.class);
                    if (callback != null) RootService.bind(intent, connection);
                }
            }
        }
    }
}
