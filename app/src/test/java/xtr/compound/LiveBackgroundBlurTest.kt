package xtr.compound

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveBackgroundBlurTest {
    @Test
    fun themeTintFollowsThemeAndOpacity() {
        val light = LiveBackgroundBlur.themeTint(80, nightMode = false)
        val dark = LiveBackgroundBlur.themeTint(80, nightMode = true)

        assertEquals(0xCC, light ushr 24)
        assertEquals(0xF4, (light shr 16) and 0xFF)
        assertEquals(0xF5, (light shr 8) and 0xFF)
        assertEquals(0xF8, light and 0xFF)
        assertEquals(0xCC, dark ushr 24)
        assertEquals(0x18, (dark shr 16) and 0xFF)
        assertEquals(0x1A, (dark shr 8) and 0xFF)
        assertEquals(0x20, dark and 0xFF)
    }
}
