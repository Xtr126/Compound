/home/hp/.bash_profile: line 6: /home/hp/.cargo/env: No such file or directory
package xtr.compound

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

private data class LaunchableApp(
    val packageName: String,
    val className: String,
    val title: String,
    val icon: Drawable
)

/** Compose implementation hosted by the Activity's existing overlay ComposeView. */
@Composable
fun StartMenuContent(
    windows: DesktopWindowController,
    powerEnabled: Boolean,
    onSettings: () -> Unit,
    onAppearance: () -> Unit,
    onConfigure: () -> Unit,
    onFiles: () -> Unit,
    onPower: () -> Unit
) {
    val context = LocalContext.current
    val apps = remember(context) {
        val packageManager = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryIntentActivities(query, 0)
            .filterNot { it.activityInfo.packageName == context.packageName }
            .map { info: ResolveInfo ->
                LaunchableApp(
                    info.activityInfo.packageName,
                    info.activityInfo.name,
                    info.loadLabel(packageManager).toString(),
                    info.activityInfo.loadIcon(packageManager)
                )
            }
    }
    val cardColor = colorResource(R.color.cardBackgroundTranslucent)
    val onCard = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Card(
            modifier = Modifier.width(70.dp).wrapContentHeight().padding(start = 10.dp, bottom = 20.dp),
            shape = RoundedCornerShape(40.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MenuAction(R.drawable.outline_settings_24, stringResource(R.string.device_settings), onSettings)
                MenuAction(R.drawable.ic_palette_24, stringResource(R.string.appearance_settings), onAppearance)
                MenuAction(R.drawable.ic_tune_24, stringResource(R.string.configure_settings), onConfigure)
                MenuAction(R.drawable.baseline_folder_open_24, stringResource(R.string.open_files), onFiles)
                MenuAction(android.R.drawable.ic_lock_power_off, stringResource(R.string.power_menu), onPower, enabled = powerEnabled)
            }
        }

        Card(
            modifier = Modifier.width(200.dp).fillMaxHeight().padding(20.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(apps, key = { "${it.packageName}/${it.className}" }) { app ->
                    TextButton(
                        onClick = {
                            launchApp(context, app, windows)
                        },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                    ) {
                        AppIcon(app, 24)
                        Text(
                            app.title,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = onCard
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(top = 20.dp, bottom = 20.dp, end = 20.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(apps, key = { "grid-${it.packageName}/${it.className}" }) { app ->
                    TextButton(
                        onClick = {
                            launchApp(context, app, windows)
                        },
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon(app, 40)
                            Text(app.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = onCard)
                        }
                    }
                }
            }
        }
    }
}

private fun launchApp(context: android.content.Context, app: LaunchableApp, windows: DesktopWindowController) {
    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
    intent.component = ComponentName(app.packageName, app.className)
    windows.launch(intent, app.title, app.icon)
}

@Composable
private fun MenuAction(icon: Int, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    val iconTint = MaterialTheme.colorScheme.onSurface
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = if (enabled) iconTint else iconTint.copy(alpha = 0.38f)
        )
    }
}

@Composable
private fun AppIcon(app: LaunchableApp, sizeDp: Int) {
    val bitmap = remember(app.icon) { app.icon.toBitmap().asImageBitmap() }
    androidx.compose.foundation.Image(bitmap, contentDescription = null, modifier = Modifier.size(sizeDp.dp))
}
