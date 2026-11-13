package xtr.compound

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource

@Composable
internal fun DesktopTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
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
        content = content
    )
}
