package xtr.compound

import kotlin.math.roundToInt

/** Keeps the Start menu usable when a large local density scale consumes the workspace. */
object LauncherMenuGeometry {
    const val MINIMUM_WORKSPACE_HEIGHT_DP = 280

    data class Bounds(val heightPx: Int, val bottomMarginPx: Int, val overlapsTaskbar: Boolean)

    /** Caps the scaled taskbar while keeping a usable workspace and a positive layout size. */
    fun capTaskbarHeight(
        requestedHeightPx: Int,
        rootHeightPx: Int,
        fallbackRootHeightPx: Int,
        baseDensity: Float,
        minimumWorkspaceHeightDp: Int = MINIMUM_WORKSPACE_HEIGHT_DP
    ): Int {
        val density = baseDensity.takeIf { it.isFinite() && it > 0f } ?: 1f
        val rootHeight = (rootHeightPx.takeIf { it > 0 } ?: fallbackRootHeightPx).coerceAtLeast(1)
        val minimumWorkspaceHeight = (minimumWorkspaceHeightDp.coerceAtLeast(0) * density)
            .roundToInt().coerceAtLeast(1)
        val maxTaskbarHeight = (rootHeight - minimumWorkspaceHeight).coerceAtLeast(1)
        return requestedHeightPx.coerceAtLeast(1).coerceAtMost(maxTaskbarHeight)
    }

    fun calculate(
        rootHeightPx: Int,
        workspaceHeightPx: Int,
        baseDensity: Float,
        densityScale: Float,
        requestedHeightDp: Int,
        minimumWorkspaceHeightDp: Int = MINIMUM_WORKSPACE_HEIGHT_DP
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
