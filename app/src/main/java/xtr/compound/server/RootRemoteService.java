package xtr.compound.server;

import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.ipc.RootService;

class RootRemoteService extends RootService {
    private RemoteService mService = null;

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        if (mService == null) {
            mService = new RemoteService(this);
        }
        return mService;
    }

}