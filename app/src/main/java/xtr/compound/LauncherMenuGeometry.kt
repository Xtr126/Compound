package xtr.compound

import kotlin.math.roundToInt

/** Keeps the Start menu usable when a large local density scale consumes the workspace. */
object LauncherMenuGeometry {
    data class Bounds(val heightPx: Int, val bottomMarginPx: Int, val overlapsTaskbar: Boolean)

    fun calculate(
        rootHeightPx: Int,
        workspaceHeightPx: Int,
        baseDensity: Float,
        densityScale: Float,
        requestedHeightDp: Int,
        minimumWorkspaceHeightDp: Int = 280
    ): Bounds {
        val density = baseDensity.coerceAtLeast(0.1f)
        val scale = AppearanceSettings.quantizeDensityMultiplier(densityScale)
        val scaledMargins = (20f * density * scale).roundToInt()
        val workspaceHeight = (workspaceHeightPx - scaledMargins).coerceAtLeast(1)
        val minimumHeight = (minimumWorkspaceHeightDp * density).roundToInt()
        val overlapsTaskbar = workspaceHeight < minimumHeight
        val bottomMargin = if (overlapsTaskbar) (10f * density).roundToInt() else (10f * density * scale).roundToInt()
        val availableHeight = if (overlapsTaskbar) rootHeightPx - bottomMargin else workspaceHeight
        val requestedHeight = (requestedHeightDp * density * scale).roundToInt()
        return Bounds(
            heightPx = minOf(requestedHeight, availableHeight.coerceAtLeast(1)).coerceAtLeast(1),
            bottomMarginPx = bottomMargin,
            overlapsTaskbar = overlapsTaskbar
        )
    }
}
