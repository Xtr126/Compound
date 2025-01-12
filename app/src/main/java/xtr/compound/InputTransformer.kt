package xtr.compound

import android.graphics.Point
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties

class InputTransformer(
    private val mService: IRemoteService,
    private val viewWidth: Int,
    private val viewHeight: Int,
    private val displayId: Int
) {
    private val displaySize: Point
        get() {
            val baseSize = Point()
            mService.getBaseDisplaySize(displayId, baseSize)
            return baseSize
        }

    fun transformTouchEvent(event: MotionEvent): MotionEvent {
        val pointerProperties = arrayOfNulls<PointerProperties>(event.pointerCount)
        val pointerCoords = arrayOfNulls<PointerCoords>(event.pointerCount)
        for (i in 0..< event.pointerCount) {
            pointerProperties[i] = PointerProperties()
            pointerCoords[i] = PointerCoords()
            event.getPointerProperties(i, pointerProperties[i])
            event.getPointerCoords(i, pointerCoords[i])
            pointerCoords[i]!!.x = pointerCoords[i]!!.x / viewWidth * displaySize.x
            pointerCoords[i]!!.y = pointerCoords[i]!!.y / viewHeight * displaySize.y
        }
        return MotionEvent.obtain(
            event.downTime,
            event.eventTime,
            event.action,
            event.pointerCount,
            pointerProperties,
            pointerCoords,
            event.metaState,
            event.buttonState,
            event.xPrecision,
            event.yPrecision,
            event.deviceId,
            event.edgeFlags,
            event.source,
            event.flags
        )
    }
}