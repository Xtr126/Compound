package xtr.compound.server;

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.display.VirtualDisplay;
import android.os.RemoteException;
import android.util.Log;
import android.view.Display;
import android.view.InputEvent;
import android.view.Surface;
import android.view.WindowManagerGlobal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import xtr.compound.IRemoteService;

public class RemoteService extends IRemoteService.Stub {
    private final Context context;
    private final List<VirtualDisplay> virtualDisplays = new ArrayList<>();
    public static final int WINDOWING_MODE_FULLSCREEN = 1;
    public RemoteService(Context context) {
        this.context = context;
    }

    @Override
    public int renderAppToSurface(Intent launchIntent, Surface surface, int width, int height, int dpi) {
        try {
            VirtualDisplayUtils virtualDisplayHelper = new VirtualDisplayUtils(context);
            VirtualDisplay virtualDisplay = virtualDisplayHelper.createVirtualDisplay(surface, width, height, dpi);
            virtualDisplay.setSurface(surface);
            virtualDisplays.add(virtualDisplay);
            int displayId = virtualDisplay.getDisplay().getDisplayId();
            startApplication(launchIntent, displayId);
            return displayId;
        } catch (Exception e) {
            Log.e("renderAppToSurface", e.getMessage(), e);
        }
        return 0;
    }

    private void startApplication(Intent launchIntent, int displayId) {

        final String[] cmdArrayBase = {
                "am", "start-activity",
                "-n", launchIntent.getComponent().flattenToString(),
                "-a", launchIntent.getAction(),
                "--windowingMode", String.valueOf(WINDOWING_MODE_FULLSCREEN),
                "--display", String.valueOf(displayId)
        };
        String[] categories = launchIntent.getCategories().toArray(new String[0]);

        final String[] cmdArray;
        if (categories.length > 0) {
            cmdArray = new String[cmdArrayBase.length + categories.length*2];
            System.arraycopy(cmdArrayBase, 0, cmdArray, 0, cmdArrayBase.length);
            for (int i = 0; i < categories.length; i++) {
                cmdArray[cmdArrayBase.length + i*2] = "-c";
                cmdArray[cmdArrayBase.length + (i*2) + 1] = categories[i];
            }
        } else {
            cmdArray = cmdArrayBase;
        }

        try {
            Runtime.getRuntime().exec(cmdArray);
        } catch (IOException ignored) {
        }

    }

    @Override
    public void resizeVirtualDisplay(int displayId, int width, int height, int dpi) {
        for (VirtualDisplay virtualDisplay : virtualDisplays) {
            if (virtualDisplay.getDisplay().getDisplayId() == displayId) {
                virtualDisplay.resize(width, height, dpi);
                return;
            }
        }
    }

    @Override
    public void releaseVirtualDisplay(int displayId) {
        for (VirtualDisplay virtualDisplay : virtualDisplays) {
            if (virtualDisplay.getDisplay().getDisplayId() == displayId) {
                virtualDisplays.remove(virtualDisplay);
                VirtualDisplayUtils.releaseVirtualDisplay(virtualDisplay);
                return;
            }
        }
    }

    @Override
    public void showPowerMenu() {
        try {
            Runtime.getRuntime().exec("input keyevent --longpress POWER");
        } catch (IOException ignored) {
        }
    }

    @Override
    public void injectInputEvent(InputEvent event, int displayId) {
        VirtualDisplayUtils.setDisplayId(event, displayId);
        Input.injectInputEvent(event);
    }

    @Override
    public int getBaseDisplayDensity(int displayId) throws RemoteException {
        return WindowManagerGlobal.getWindowManagerService().getBaseDisplayDensity(Display.DEFAULT_DISPLAY);
    }

    @Override
    public void getBaseDisplaySize(int displayId, Point baseSize) throws RemoteException {
        WindowManagerGlobal.getWindowManagerService().getBaseDisplaySize(displayId, baseSize);
    }
}
