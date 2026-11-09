package xtr.compound;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/** Drags a desktop window while keeping its title bar within the desktop bounds. */
public final class MovableFrameLayout implements View.OnTouchListener {
    private final View window;
    private float downRawX, downRawY, startX, startY;
    private boolean moved;
    private static final int DRAG_SLOP_PX = 8;

    public MovableFrameLayout(View window) { this.window = window; }

    @Override public boolean onTouch(View handle, MotionEvent event) {
        ViewGroup parent = (ViewGroup) window.getParent();
        if (parent == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startX = window.getX();
                startY = window.getY();
                moved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (Math.abs(dx) + Math.abs(dy) > DRAG_SLOP_PX) moved = true;
                if (moved) {
                    window.setX(clamp(startX + dx, 0, Math.max(0, parent.getWidth() - window.getWidth())));
                    window.setY(clamp(startY + dy, 0, Math.max(0, parent.getHeight() - window.getHeight())));
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!moved) handle.performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return false;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
