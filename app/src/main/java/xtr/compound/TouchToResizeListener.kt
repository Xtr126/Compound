package xtr.compound

import android.view.MotionEvent
import android.view.View

class TouchToResizeListener(private val parentView: View, private val type: Int) : View.OnTouchListener {
    private var initialX = 0
    private var initialY = 0
    private var initialWidth = 0
    private var initialHeight = 0

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Record initial touch position and dimensions
                if (type == HORIZONTAL) {
                    initialX = event.rawX.toInt()
                    initialWidth = parentView.width
                } else {
                    initialY = event.rawY.toInt()
                    initialHeight = parentView.height
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (type == HORIZONTAL) {
                    val deltaX = event.rawX.toInt() - initialX
                    val newWidth = initialWidth + deltaX
                    parentView.layoutParams.width = newWidth.coerceAtLeast(100) // Minimum width
                } else {
                    val deltaY = event.rawY.toInt() - initialY
                    val newHeight = initialHeight + deltaY
                    parentView.layoutParams.height = newHeight.coerceAtLeast(100) // Minimum height
                }
                parentView.requestLayout()
                return true
            }
        }
        return false
    }
    companion object {
        const val HORIZONTAL = 0;
        const val VERTICAL = 1;
    }
}
