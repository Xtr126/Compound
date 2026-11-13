package xtr.compound

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/** Compose implementation of the desktop taskbar. Callbacks keep privileged and launch actions in MainActivity. */
@Composable
fun TaskbarContent(
    entries: List<TaskbarEntry>,
    iconsOnly: Boolean,
    activeKey: String?,
    serviceStatus: String?,
    onStart: () -> Unit,
    onStartLongPress: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRecents: () -> Unit,
    onWifi: () -> Unit,
    onBattery: () -> Unit,
    onClock: (bottomHalf: Boolean) -> Unit,
    onNotifications: () -> Unit,
    onFocus: (TaskbarEntry) -> Unit,
    onClose: (TaskbarEntry) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clockDescription = stringResource(R.string.clock_calendar_shortcut)
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Transparent),
        contentAlignment = Alignment.CenterStart
    ) {
        val compact = maxWidth < 720.dp
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            TaskbarButton(
                icon = R.drawable.blissos_logo_black,
                description = stringResource(R.string.open_start_menu),
                width = 60.dp,
                onClick = onStart,
                onLongClick = onStartLongPress,
                iconSize = 24.dp,
                tooltip = serviceStatus
            )
            TaskbarButton(R.drawable.ic_sysbar_back, stringResource(R.string.back_in_focused_app), 48.dp, onBack)
            TaskbarButton(R.drawable.ic_sysbar_home, stringResource(R.string.minimize_all_windows), 48.dp, onHome)
            TaskbarButton(R.drawable.ic_sysbar_recent, stringResource(R.string.refresh_running_tasks), 48.dp, {
                onRecents()
                scope.launch { listState.animateScrollToItem(0) }
            })
            Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 10.dp)
                .background(MaterialTheme.colorScheme.onSurface))
            LazyRow(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (iconsOnly) Arrangement.Center else Arrangement.Start
            ) {
                items(entries, key = { it.key }) { entry ->
                    TaskbarEntryItem(
                        entry = entry,
                        active = entry.key == activeKey,
                        iconsOnly = iconsOnly,
                        onClick = { onFocus(entry) },
                        onClose = { onClose(entry) }
                    )
                }
            }
            if (!compact) {
                Row(Modifier.fillMaxHeight().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TaskbarButton(R.drawable.baseline_wifi_24, stringResource(R.string.open_wifi_settings), 55.dp, onWifi, iconSize = 24.dp)
                    TaskbarButton(R.drawable.baseline_battery_charging_full_24, stringResource(R.string.open_battery_settings), 50.dp, onBattery, iconSize = 24.dp)
                    Box(
                        Modifier.fillMaxHeight()
                            .pointerInput(onClock) { detectTapGestures { point -> onClock(point.y >= size.height / 2f) } }
                            .semantics {
                                contentDescription = clockDescription
                                onClick {
                                    onClock(false)
                                    true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = rememberClockText(),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 2.dp),
                            maxLines = 2
                        )
                    }
                    TaskbarButton(R.drawable.baseline_chat_bubble_outline_24, stringResource(R.string.open_notifications), 64.dp, onNotifications, iconSize = 24.dp)
                }
            }
        }
    }
}

@Composable
private fun rememberClockText(): String {
    val context = LocalContext.current
    var time by remember(context) {
        mutableStateOf(android.text.format.DateFormat.format("HH:mm\ndd-MMM-yy", java.util.Date()).toString())
    }
    LaunchedEffect(context) {
        while (true) {
            time = android.text.format.DateFormat.format("HH:mm\ndd-MMM-yy", java.util.Date()).toString()
            delay(1_000)
        }
    }
    return time
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TaskbarEntryItem(
    entry: TaskbarEntry,
    active: Boolean,
    iconsOnly: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val icon = remember(context, entry.packageName) { applicationIcon(context, entry.packageName) }
    val appName = remember(entry.packageName, entry.title) {
        val label = runCatching {
            val app = context.packageManager.getApplicationInfo(entry.packageName, 0)
            context.packageManager.getApplicationLabel(app).toString()
        }.getOrNull()
        label?.takeIf { it.isNotBlank() } ?: entry.title.takeIf { it.isNotBlank() } ?: entry.packageName
    }
    val stateDescription = when {
        entry.minimized -> "minimized"
        entry.maximized -> "maximized"
        else -> null
    }
    Column(
        modifier = Modifier
            .width(if (iconsOnly) 64.dp else 200.dp)
            .height(56.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (iconsOnly) onClose else null
            )
            .semantics {
                contentDescription = buildString {
                    append(appName)
                    if (stateDescription != null) append(", $stateDescription")
                    if (active) append(", active task")
                    if (iconsOnly) append(". Long press to close")
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (iconsOnly) Arrangement.Center else Arrangement.Start
        ) {
            AndroidView(
                factory = { viewContext ->
                    ImageView(viewContext).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = null
                    }
                },
                modifier = Modifier.size(32.dp),
                update = { it.setImageDrawable(icon) }
            )
            if (!iconsOnly) {
                Text(
                    text = appName + if (entry.minimized) " (minimized)" else "",
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 10.dp, end = 4.dp)
                )
                val stateIcon = when {
                    entry.minimized -> R.drawable.window_minimized
                    entry.maximized -> R.drawable.window_maximized
                    else -> null
                }
                if (stateIcon != null) androidx.compose.foundation.Image(
                    painter = painterResource(stateIcon),
                    contentDescription = stateDescription,
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
                Box(
                    Modifier.size(40.dp, 48.dp).combinedClickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { contentDescription = "Close $appName" })
                }
            }
        }
        Box(
            Modifier.width(20.dp).height(3.dp)
                .background(if (iconsOnly && active) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TaskbarButton(
    icon: Int,
    description: String,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    tooltip: String? = null
) {
    Box(
        Modifier.width(width).fillMaxHeight()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics {
                contentDescription = if (tooltip.isNullOrBlank()) description else "$description. $tooltip"
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
        )
    }
}

private fun applicationIcon(context: Context, packageName: String): Drawable? = runCatching {
    context.packageManager.getApplicationIcon(packageName)
}.getOrNull()
