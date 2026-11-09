package xtr.compound

/** UI-independent geometry and visibility state for a desktop app window. */
internal class DesktopWindowState(
    width: Int,
    height: Int,
    x: Int = 0,
    y: Int = 0
) {
    var width: Int = width
        private set
    var height: Int = height
        private set
    var x: Int = x
        private set
    var y: Int = y
        private set
    var minimized: Boolean = false
        private set
    var maximized: Boolean = false
        private set
    var closed: Boolean = false
        private set

    private var restoreWidth = width
    private var restoreHeight = height
    private var restoreX = x
    private var restoreY = y

    fun moveTo(x: Int, y: Int, parentWidth: Int, parentHeight: Int) {
        if (closed || maximized) return
        this.x = x.coerceIn(0, (parentWidth - width).coerceAtLeast(0))
        this.y = y.coerceIn(0, (parentHeight - height).coerceAtLeast(0))
        restoreX = this.x
        restoreY = this.y
    }

    fun resizeTo(width: Int, height: Int, parentWidth: Int, parentHeight: Int) {
        if (closed || maximized) return
        this.width = width.coerceIn(MIN_SIZE, parentWidth.coerceAtLeast(MIN_SIZE))
        this.height = height.coerceIn(MIN_SIZE, parentHeight.coerceAtLeast(MIN_SIZE))
        x = x.coerceIn(0, (parentWidth - this.width).coerceAtLeast(0))
        y = y.coerceIn(0, (parentHeight - this.height).coerceAtLeast(0))
        rememberRestoreBounds()
    }

    fun toggleMaximized(parentWidth: Int, parentHeight: Int) {
        if (closed) return
        if (maximized) {
            width = restoreWidth.coerceIn(MIN_SIZE, parentWidth.coerceAtLeast(MIN_SIZE))
            height = restoreHeight.coerceIn(MIN_SIZE, parentHeight.coerceAtLeast(MIN_SIZE))
            x = restoreX.coerceIn(0, (parentWidth - width).coerceAtLeast(0))
            y = restoreY.coerceIn(0, (parentHeight - height).coerceAtLeast(0))
            maximized = false
        } else {
            rememberRestoreBounds()
            x = 0
            y = 0
            width = parentWidth.coerceAtLeast(MIN_SIZE)
            height = parentHeight.coerceAtLeast(MIN_SIZE)
            maximized = true
        }
    }

    fun minimize() { if (!closed) minimized = true }
    fun restore() { if (!closed) minimized = false }
    fun close() { closed = true; minimized = true }

    private fun rememberRestoreBounds() {
        restoreWidth = width
        restoreHeight = height
        restoreX = x
        restoreY = y
    }

    companion object { const val MIN_SIZE = 160 }
}
