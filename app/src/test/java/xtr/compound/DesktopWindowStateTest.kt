package xtr.compound

import org.junit.Assert.*
import org.junit.Test

class DesktopWindowStateTest {
    @Test fun densityMultiplierUsesBaseDpiAndClampsToSupportedRange() {
        assertEquals(420, DisplayDensity.dpi(420, 1.0f))
        assertEquals(42, DisplayDensity.dpi(420, 0.1f))
        assertEquals(1680, DisplayDensity.dpi(420, 4.0f))
        assertEquals(1, DisplayDensity.dpi(0, 0.1f))
        assertEquals(1680, DisplayDensity.dpi(420, 10f))
    }

    @Test fun densityMultipliersAreQuantizedToTenths() {
        assertEquals(1.0f, AppearanceSettings.quantizeDensityMultiplier(1.04f), 0f)
        assertEquals(1.1f, AppearanceSettings.quantizeDensityMultiplier(1.06f), 0f)
        assertEquals(0.1f, AppearanceSettings.quantizeDensityMultiplier(0f), 0f)
        assertEquals(4.0f, AppearanceSettings.quantizeDensityMultiplier(8f), 0f)
    }

    @Test fun bottomRightCornerResizeChangesBothDimensionsWithinDesktop() {
        val bounds = WindowResizeGeometry.resize(
            TouchToResizeListener.BOTTOM_RIGHT,
            startX = 100f, startY = 80f, startWidth = 300, startHeight = 200,
            dx = 700, dy = 500, parentWidth = 800, parentHeight = 600, minSize = 160
        )
        assertEquals(WindowResizeGeometry.Bounds(100, 80, 700, 520), bounds)
    }

    @Test fun bottomLeftCornerResizeKeepsRightEdgeAndRespectsMinimum() {
        val bounds = WindowResizeGeometry.resize(
            TouchToResizeListener.BOTTOM_LEFT,
            startX = 100f, startY = 80f, startWidth = 300, startHeight = 200,
            dx = 250, dy = 40, parentWidth = 800, parentHeight = 600, minSize = 160
        )
        assertEquals(WindowResizeGeometry.Bounds(240, 80, 160, 240), bounds)
    }

    @Test fun topRightCornerResizeChangesBothDimensionsAndKeepsBottomEdge() {
        val bounds = WindowResizeGeometry.resize(
            TouchToResizeListener.TOP_RIGHT,
            startX = 100f, startY = 80f, startWidth = 300, startHeight = 200,
            dx = 100, dy = -30, parentWidth = 800, parentHeight = 600, minSize = 160
        )
        assertEquals(WindowResizeGeometry.Bounds(100, 50, 400, 230), bounds)
        assertEquals(280, bounds.y + bounds.height)
    }

    @Test fun topLeftCornerResizeKeepsBottomAndRightEdgesAndClampsToMinimum() {
        val bounds = WindowResizeGeometry.resize(
            TouchToResizeListener.TOP_LEFT,
            startX = 100f, startY = 80f, startWidth = 300, startHeight = 200,
            dx = 250, dy = 300, parentWidth = 800, parentHeight = 600, minSize = 160
        )
        assertEquals(WindowResizeGeometry.Bounds(240, 120, 160, 160), bounds)
        assertEquals(400, bounds.x + bounds.width)
        assertEquals(280, bounds.y + bounds.height)
    }

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
