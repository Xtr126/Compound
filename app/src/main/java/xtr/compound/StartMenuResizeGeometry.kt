package xtr.compound

/** Pure geometry for a top-right start-menu resize gesture. */
internal object StartMenuResizeGeometry {
    data class Size(val width: Int, val height: Int)

    fun resize(
        initialWidth: Int,
        initialHeight: Int,
        deltaX: Int,
        deltaY: Int,
        maxWidth: Int,
        maxHeight: Int,
        minWidth: Int,
        minHeight: Int
    ): Size {
        val widthLimit = maxWidth.coerceAtLeast(1)
        val heightLimit = maxHeight.coerceAtLeast(1)
        val widthMinimum = minWidth.coerceAtMost(widthLimit)
        val heightMinimum = minHeight.coerceAtMost(heightLimit)
        return Size(
            width = (initialWidth + deltaX).coerceIn(widthMinimum, widthLimit),
            height = (initialHeight - deltaY).coerceIn(heightMinimum, heightLimit)
        )
    }
}
