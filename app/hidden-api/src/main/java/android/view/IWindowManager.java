package android.view;

import android.graphics.Point;
import android.os.IInterface;
import android.os.RemoteException;

public interface IWindowManager extends IInterface {
    int getBaseDisplayDensity(int displayId) throws RemoteException;
    void getBaseDisplaySize(int displayId, Point baseSize)  throws RemoteException;
}
