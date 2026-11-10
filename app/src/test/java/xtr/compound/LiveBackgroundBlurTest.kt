package xtr.compound

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveBackgroundBlurTest {
    @Test
    fun liveTintKeepsBlurVisibleAndScalesWithOpacity() {
        val none = LiveBackgroundBlur.liveTint(0, nightMode = true)
        val default = LiveBackgroundBlur.liveTint(80, nightMode = true)
        val maximum = LiveBackgroundBlur.liveTint(100, nightMode = true)

        assertEquals(0, none ushr 24)
        assertEquals(0x51, default ushr 24)
        assertEquals(0x66, maximum ushr 24)
    }

    @Test
    fun liveTintUsesLightAndDarkThemeColors() {
        val lightTint = LiveBackgroundBlur.liveTint(100, nightMode = false)
        val darkTint = LiveBackgroundBlur.liveTint(100, nightMode = true)

        assertEquals(0xF4, (lightTint shr 16) and 0xFF)
        assertEquals(0xF5, (lightTint shr 8) and 0xFF)
        assertEquals(0xF8, lightTint and 0xFF)
        assertEquals(0x18, (darkTint shr 16) and 0xFF)
        assertEquals(0x1A, (darkTint shr 8) and 0xFF)
        assertEquals(0x20, darkTint and 0xFF)
    }

    @Test
    fun fallbackRetainsConfiguredOpacity() {
        assertEquals(0xCC, LiveBackgroundBlur.fallbackTint(80, nightMode = true) ushr 24)
    }

    @Test
    fun fallbackTintUsesThemeColorAndKeepsOpacityControl() {
        val light = LiveBackgroundBlur.fallbackTint(80, nightMode = false)
        val dark = LiveBackgroundBlur.fallbackTint(80, nightMode = true)

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
