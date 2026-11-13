package xtr.compound

import org.junit.Assert.assertEquals
import org.junit.Test

class StartMenuResizeGeometryTest {
    @Test fun topRightDragChangesWidthAndHeightAndClampsToWorkspace() {
        val resized = StartMenuResizeGeometry.resize(
            initialWidth = 620,
            initialHeight = 580,
            deltaX = 180,
            deltaY = -120,
            maxWidth = 700,
            maxHeight = 650,
            minWidth = 320,
            minHeight = 280
        )

        assertEquals(StartMenuResizeGeometry.Size(700, 650), resized)
    }

    @Test fun draggingInwardRespectsMinimumSizeEvenOnSmallWorkspace() {
        val resized = StartMenuResizeGeometry.resize(
            initialWidth = 620,
            initialHeight = 580,
            deltaX = -800,
            deltaY = 900,
            maxWidth = 300,
            maxHeight = 240,
            minWidth = 320,
            minHeight = 280
        )

        assertEquals(StartMenuResizeGeometry.Size(300, 240), resized)
    }
}
