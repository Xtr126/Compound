package xtr.compound

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Density

@Composable
internal fun DesktopTheme(densityMultiplier: Float = AppearanceSettings.DEFAULT_DENSITY_MULTIPLIER, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDensity = LocalDensity.current
    val scaledDensity = remember(systemDensity, densityMultiplier) {
        Density(
            density = systemDensity.density * AppearanceSettings.quantizeDensityMultiplier(densityMultiplier),
            fontScale = systemDensity.fontScale
        )
    }
    val colors = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }
    MaterialTheme(
        colorScheme = colors.copy(
            surface = colorResource(R.color.compoundSurface),
            onSurface = colorResource(R.color.compoundOnSurface),
            onSurfaceVariant = colorResource(R.color.compoundOnSurfaceVariant)
        ),
        content = {
            CompositionLocalProvider(LocalDensity provides scaledDensity, content = content)
        }
    )
}
