package xtr.compound

import kotlin.math.roundToInt

/** Converts a user multiplier into an Android display density, preserving the default at 1x. */
internal object DisplayDensity {
    fun dpi(baseDpi: Int, multiplier: Float): Int =
        (baseDpi.coerceAtLeast(1) * AppearanceSettings.quantizeDensityMultiplier(multiplier)).roundToInt()
            .coerceAtLeast(1)
}
