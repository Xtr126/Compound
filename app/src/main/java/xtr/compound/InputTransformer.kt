package xtr.compound

import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties

/** Maps view-local pointer coordinates to the current virtual display dimensions. */
class InputTransformer(
    viewWidth: Int,
    viewHeight: Int
) {
    @Volatile private var viewWidth = viewWidth.coerceAtLeast(1)
    @Volatile private var viewHeight = viewHeight.coerceAtLeast(1)
    @Volatile private var displayWidth = viewWidth.coerceAtLeast(1)
    @Volatile private var displayHeight = viewHeight.coerceAtLeast(1)

    fun updateViewSize(width: Int, height: Int) {
        viewWidth = width.coerceAtLeast(1)
        viewHeight = height.coerceAtLeast(1)
        displayWidth = width.coerceAtLeast(1)
        displayHeight = height.coerceAtLeast(1)
    }

    fun transformTouchEvent(event: MotionEvent): MotionEvent {
        val width = viewWidth
        val height = viewHeight
        val targetWidth = displayWidth
        val targetHeight = displayHeight
        val properties = Array(event.pointerCount) { i -> PointerProperties().also { event.getPointerProperties(i, it) } }
        val coords = Array(event.pointerCount) { i -> PointerCoords().also { event.getPointerCoords(i, it) } }
        for (i in coords.indices) {
            coords[i].x = coords[i].x * targetWidth / width
            coords[i].y = coords[i].y * targetHeight / height
        }
        return MotionEvent.obtain(
            event.downTime, event.eventTime, event.action, event.pointerCount,
            properties, coords, event.metaState, event.buttonState,
            event.xPrecision, event.yPrecision, event.deviceId,
            event.edgeFlags, event.source, event.flags
        )
    }
}
