// IRemoteService.aidl
package xtr.compound;

// Declare any non-default types here with import statements
import android.view.Surface;
import android.content.Intent;
import android.view.InputEvent;
import android.graphics.Point;
import xtr.compound.server.RunningTask;

interface IRemoteService {
    /**
     * @param launchIntent Intent for launching application
     * @param surface Surface onto which display is rendered
     * @param width Virtual display width
     * @param height Virtual display height
     * @param dpi Display density
     * @return Display ID, or -1 when creation or launch fails
     */
    int renderAppToSurface(in Intent launchIntent, in Surface surface, int width, int height, int dpi);

    void resizeVirtualDisplay(int displayId, int width, int height, int dpi);

    void releaseVirtualDisplay(int displayId);

    void setVirtualDisplaySurface(int displayId, in Surface surface);

    List<RunningTask> getRunningTasks();

    boolean focusTask(int taskId);

    boolean removeTask(int taskId);

    void showPowerMenu();

    void injectInputEvent(in InputEvent event, int displayId);

    int getBaseDisplayDensity(int displayId);

    void getBaseDisplaySize(int displayId, out Point baseSize);
}
