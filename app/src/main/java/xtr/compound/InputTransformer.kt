package xtr.compound

import android.graphics.Point
import android.view.InputEvent
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import android.view.WindowManagerGlobal

class InputTransformer(
    private val viewWidth: Int,
    private val viewHeight: Int,
    private val displayId: Int
) {
    private val displaySize: Point
        get() {
            val baseSize = Point()
            WindowManagerGlobal.getWindowManagerService().getBaseDisplaySize(displayId, baseSize)
            return baseSize
        }


    fun transformTouchEvent(event: MotionEvent): MotionEvent {
        setDisplayId(event, displayId)

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


    companion object {
        fun setDisplayId(event: InputEvent, displayId: Int) {
            try {
                InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
                    .invoke(event, displayId)
            } catch (_: NoSuchMethodException) {
            }
        }
    }
}