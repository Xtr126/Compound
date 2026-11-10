package xtr.compound

/** Calculates resized bounds from the window geometry captured at the start of a drag. */
internal object WindowResizeGeometry {
    data class Bounds(val x: Int, val y: Int, val width: Int, val height: Int)

    fun resize(
        type: Int,
        startX: Float,
        startY: Float,
        startWidth: Int,
        startHeight: Int,
        dx: Int,
        dy: Int,
        parentWidth: Int,
        parentHeight: Int,
        minSize: Int
    ): Bounds {
        val left = type == TouchToResizeListener.HORIZONTAL_LEFT || type == TouchToResizeListener.BOTTOM_LEFT || type == TouchToResizeListener.TOP_LEFT
        val right = type == TouchToResizeListener.HORIZONTAL_RIGHT || type == TouchToResizeListener.BOTTOM_RIGHT || type == TouchToResizeListener.TOP_RIGHT
        val top = type == TouchToResizeListener.TOP_LEFT || type == TouchToResizeListener.TOP_RIGHT
        val bottom = type == TouchToResizeListener.VERTICAL || type == TouchToResizeListener.BOTTOM_LEFT || type == TouchToResizeListener.BOTTOM_RIGHT
        val x = startX.toInt()
        val y = startY.toInt()
        var newX = x
        var newY = y
        var width = startWidth
        var height = startHeight

        if (left) {
            val fixedRight = x + startWidth
            newX = (x + dx).coerceIn(0, (fixedRight - minSize).coerceAtLeast(0))
            width = (fixedRight - newX).coerceAtLeast(minSize)
        } else if (right) {
            width = (startWidth + dx).coerceIn(minSize, (parentWidth - startX).toInt().coerceAtLeast(minSize))
        }
        if (bottom) {
            height = (startHeight + dy).coerceIn(minSize, (parentHeight - startY).toInt().coerceAtLeast(minSize))
        } else if (top) {
            val fixedBottom = y + startHeight
            newY = (y + dy).coerceIn(0, (fixedBottom - minSize).coerceAtLeast(0))
            height = (fixedBottom - newY).coerceAtLeast(minSize)
        }
        return Bounds(newX, newY, width, height)
    }
}
