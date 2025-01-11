package xtr.compound;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class MovableFrameLayout implements View.OnTouchListener {
    private final View frameLayout;
    private float dX, dY;

    public MovableFrameLayout(View frameLayout) {
        this.frameLayout = frameLayout;
    }

    public boolean onTouch(View view, MotionEvent motionEvent){
        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams)frameLayout.getLayoutParams();

        int action = motionEvent.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                dX = frameLayout.getX() - motionEvent.getRawX();
                dY = frameLayout.getY() - motionEvent.getRawY();
                return true; // Consumed
            }
            case MotionEvent.ACTION_MOVE: {
                int viewWidth = frameLayout.getWidth();
                int viewHeight = frameLayout.getHeight();

                View viewParent = (View)frameLayout.getParent();
                int parentWidth = viewParent.getWidth();
                int parentHeight = viewParent.getHeight();

                float newX = motionEvent.getRawX() + dX;
                newX = Math.max(layoutParams.leftMargin, newX); // Don't allow the FAB past the left hand side of the parent
                newX = Math.min(parentWidth - viewWidth - layoutParams.rightMargin, newX); // Don't allow the FAB past the right hand side of the parent

                float newY = motionEvent.getRawY() + dY;
                newY = Math.max(layoutParams.topMargin, newY); // Don't allow the FAB past the top of the parent
                newY = Math.min(parentHeight - viewHeight - layoutParams.bottomMargin, newY); // Don't allow the FAB past the bottom of the parent

                frameLayout.animate()
                        .x(newX)
                        .y(newY)
                        .setDuration(0)
                        .start();
                return true; // Consumed
            }
            default:
                return frameLayout.onTouchEvent(motionEvent);
        }
    }
}