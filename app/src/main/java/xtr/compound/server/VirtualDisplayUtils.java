package xtr.compound.server;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.view.InputEvent;
import android.view.Surface;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class VirtualDisplayUtils {

    private static final int VIRTUAL_DISPLAY_FLAG_PUBLIC = android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY = android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
    private static final int VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH = 1 << 6;
    private static final int VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT = 1 << 7;
    private static final int VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 << 8;
    private static final int VIRTUAL_DISPLAY_FLAG_TRUSTED = 1 << 10;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP = 1 << 11;
    private static final int VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED = 1 << 12;
    private static final int VIRTUAL_DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED = 1 << 13;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_FOCUS = 1 << 14;
    private static final int VIRTUAL_DISPLAY_FLAG_DEVICE_DISPLAY_GROUP = 1 << 15;

    private final Context context;

    public VirtualDisplayUtils(Context context) {

        this.context = context;
    }

    public VirtualDisplay createVirtualDisplay(Surface surface, int width, int height, int dpi) throws Exception {
        // Create the virtual display
        return createNewVirtualDisplay(
                "Virtual", // Name of the virtual display
                width,       // Virtual display width
                height,      // Virtual display height
                dpi,         // DPI (dots per inch)
                surface,     // Surface where content will be rendered
                getFlags()  // flags for mirroring
        );
    }

    public VirtualDisplay createNewVirtualDisplay(String name, int width, int height, int dpi, Surface surface, int flags) throws Exception {
        Constructor<DisplayManager> ctor = android.hardware.display.DisplayManager.class.getDeclaredConstructor(Context.class);
        ctor.setAccessible(true);
        android.hardware.display.DisplayManager dm = ctor.newInstance(context);
        return dm.createVirtualDisplay(name, width, height, dpi, surface, flags);
    }

    private int getFlags() {
        int flags = VIRTUAL_DISPLAY_FLAG_PUBLIC
                | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                | VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH
                | VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT;
            flags |= VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            flags |= VIRTUAL_DISPLAY_FLAG_TRUSTED
                    | VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP
                    | VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED
                    | VIRTUAL_DISPLAY_FLAG_TOUCH_FEEDBACK_DISABLED;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                flags |= VIRTUAL_DISPLAY_FLAG_OWN_FOCUS
                        | VIRTUAL_DISPLAY_FLAG_DEVICE_DISPLAY_GROUP;
            }
        }
        return flags;
    }

    public static void releaseVirtualDisplay(VirtualDisplay virtualDisplay) {
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
    }

    public static void setDisplayId(InputEvent event, int displayId) {
        try {
            InputEvent.class.getMethod("setDisplayId", int.class).invoke(event, displayId);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
