package xtr.compound

import org.junit.Assert.*
import org.junit.Test

class DesktopWindowStateTest {
    @Test fun minimizeKeepsWindowStateAndRestoreShowsIt() {
        val state = DesktopWindowState(400, 600, 20, 30)
        state.minimize()
        assertTrue(state.minimized)
        assertFalse(state.closed)
        state.restore()
        assertFalse(state.minimized)
    }

    @Test fun maximizeAndRestoreRecoverBoundsClampedToDesktop() {
        val state = DesktopWindowState(400, 500, 40, 50)
        state.toggleMaximized(1000, 800)
        assertTrue(state.maximized)
        assertEquals(1000, state.width)
        assertEquals(800, state.height)
        state.toggleMaximized(700, 600)
        assertFalse(state.maximized)
        assertEquals(400, state.width)
        assertEquals(500, state.height)
        assertEquals(40, state.x)
        assertEquals(50, state.y)
    }

    @Test fun repeatedMinimizeAndCloseAreIdempotent() {
        val state = DesktopWindowState(400, 600)
        state.minimize()
        state.minimize()
        state.close()
        state.close()
        state.restore()
        assertTrue(state.closed)
        assertTrue(state.minimized)
    }

    @Test fun moveAndResizeStayInsideDesktopAndRespectMinimum() {
        val state = DesktopWindowState(400, 600)
        state.moveTo(900, 700, 1000, 800)
        assertEquals(600, state.x)
        assertEquals(200, state.y)
        state.resizeTo(20, 2000, 1000, 800)
        assertEquals(160, state.width)
        assertEquals(800, state.height)
        assertTrue(state.x <= 1000 - state.width)
    }
}
