package xtr.compound

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

/** Resizes a window edge while preserving its opposite edge and desktop bounds. */
class TouchToResizeListener(private val window: View, private val type: Int) : View.OnTouchListener {
    private var startRawX = 0f
    private var startRawY = 0f
    private var startX = 0f
    private var startY = 0f
    private var startWidth = 0
    private var startHeight = 0

    override fun onTouch(handle: View, event: MotionEvent): Boolean {
        val parent = window.parent as? ViewGroup ?: return false
        val lp = window.layoutParams as? ViewGroup.MarginLayoutParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startRawX = event.rawX
                startRawY = event.rawY
                startX = window.x
                startY = window.y
                startWidth = window.width
                startHeight = window.height
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - startRawX).toInt()
                val dy = (event.rawY - startRawY).toInt()
                when (type) {
                    HORIZONTAL_RIGHT -> lp.width = (startWidth + dx).coerceIn(MIN_SIZE, (parent.width - startX).toInt().coerceAtLeast(MIN_SIZE))
                    HORIZONTAL_LEFT -> {
                        val left = (startX + dx).toInt().coerceIn(0, (startX + startWidth - MIN_SIZE).toInt().coerceAtLeast(0))
                        lp.width = (startWidth + startX.toInt() - left).coerceAtLeast(MIN_SIZE)
                        lp.leftMargin = left
                        window.translationX = 0f
                    }
                    VERTICAL -> lp.height = (startHeight + dy).coerceIn(MIN_SIZE, (parent.height - startY).toInt().coerceAtLeast(MIN_SIZE))
                }
                window.layoutParams = lp
                parent.requestLayout()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
        }
        return false
    }

    companion object {
        private const val MIN_SIZE = 160
        const val HORIZONTAL_LEFT = 0
        const val HORIZONTAL_RIGHT = 1
        const val VERTICAL = 2
    }
}
