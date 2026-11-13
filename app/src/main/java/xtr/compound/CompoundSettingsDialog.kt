package xtr.compound

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Compose settings pages hosted in an AlertDialog window with the existing snapshot/live blur layer. */
object CompoundSettingsDialog {
    enum class Section { APPEARANCE, CONFIGURE }

    private var activeController: Controller? = null
    private var activeDialog: AlertDialog? = null

    private class Controller(
        private val blurAvailable: () -> Boolean,
        private val setAvailabilityMessageVisible: (Boolean) -> Unit,
        private val refreshTint: () -> Unit
    ) {
        fun refreshAvailability() {
            setAvailabilityMessageVisible(!blurAvailable())
        }

        fun refreshAppearance() {
            refreshTint()
            refreshAvailability()
        }
    }

    fun refreshAvailability() {
        activeController?.refreshAvailability()
    }

    fun refreshAppearance() {
        activeController?.refreshAppearance()
    }

    fun dismiss() {
        activeDialog?.dismiss()
        activeDialog = null
        activeController = null
    }

    fun show(
        activity: AppCompatActivity,
        settings: AppearanceSettings,
        onChanged: () -> Unit,
        onSetup: () -> Unit,
        onDeviceSettings: () -> Unit,
        section: Section = Section.APPEARANCE
    ) {
        dismiss()
        var dialogBlur: LiveBackgroundBlur? = null
        var availabilityMessageVisible by mutableStateOf(false)
        var resetVersion by mutableIntStateOf(0)
        var launcherDensityMultiplier by mutableFloatStateOf(settings.launcherDensityMultiplier)

        fun applyChanges() {
            onChanged()
            dialogBlur?.update(settings.opacityPercent, settings.blurRadiusDp)
        }

        lateinit var dialog: AlertDialog
        val content = ComposeView(activity).apply {
            setContent {
                DesktopTheme(launcherDensityMultiplier) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        SettingsPage(
                            section = section,
                            settings = settings,
                            availabilityMessageVisible = availabilityMessageVisible,
                            resetVersion = resetVersion,
                            onLauncherDensityMultiplierChanged = { launcherDensityMultiplier = it },
                            onChanged = ::applyChanges,
                            onSetup = onSetup,
                            onDeviceSettings = onDeviceSettings,
                            onReset = {
                                settings.resetAppearance()
                                launcherDensityMultiplier = settings.launcherDensityMultiplier
                                resetVersion++
                                applyChanges()
                            },
                            onClose = { dialog.dismiss() }
                        )
                    }
                }
            }
        }
        dialog = AlertDialog.Builder(activity)
            .setTitle(when (section) {
                Section.APPEARANCE -> activity.getString(R.string.appearance_settings)
                Section.CONFIGURE -> activity.getString(R.string.configure_settings)
            })
            .setView(content)
            .create()

        dialog.setOnShowListener {
            val dialogWindow = requireNotNull(dialog.window)
            dialogWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val panel = dialogWindow.decorView.findViewById<FrameLayout>(android.R.id.content)
            panel.background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 28 * activity.resources.displayMetrics.density
            }
            panel.clipToOutline = true
            val background = ImageView(activity).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                id = R.id.settingsDialogBackground
                scaleType = ImageView.ScaleType.FIT_XY
            }
            panel.addView(background, 0, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val legacy = LegacyBackgroundBlur(background, panel, activity.window, hideOverlay = false)
            dialogBlur = LiveBackgroundBlur(background, 28f, legacy).also { helper ->
                helper.onAvailabilityChanged = { refreshAvailability() }
                helper.update(settings.opacityPercent, settings.blurRadiusDp)
            }
            background.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) legacy.refresh()
            }
        }
        dialog.setOnDismissListener {
            dialogBlur?.close()
            dialogBlur = null
            if (activeDialog === dialog) {
                activeDialog = null
                activeController = null
            }
        }
        dialog.show()

        activeController = Controller(
            blurAvailable = { dialogBlur?.isAvailable == true },
            setAvailabilityMessageVisible = { availabilityMessageVisible = it },
            refreshTint = { dialogBlur?.update(settings.opacityPercent, settings.blurRadiusDp) }
        )
        activeDialog = dialog
        activeController?.refreshAvailability()
    }
}

@Composable
private fun SettingsPage(
    section: CompoundSettingsDialog.Section,
    settings: AppearanceSettings,
    availabilityMessageVisible: Boolean,
    onChanged: () -> Unit,
    onSetup: () -> Unit,
    onDeviceSettings: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    resetVersion: Int,
    onLauncherDensityMultiplierChanged: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .background(colorResource(R.color.settingsDialogControlsSurface), RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (section == CompoundSettingsDialog.Section.APPEARANCE) {
            AppearancePage(settings, availabilityMessageVisible, onChanged, resetVersion)
        } else {
            ConfigurePage(settings, onChanged, onDeviceSettings, onSetup, onLauncherDensityMultiplierChanged)
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            if (section == CompoundSettingsDialog.Section.APPEARANCE) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.reset_appearance)) }
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }
    }
}

@Composable
private fun AppearancePage(
    settings: AppearanceSettings,
    availabilityMessageVisible: Boolean,
    onChanged: () -> Unit,
    resetVersion: Int
) {
    var opacity by remember(settings, resetVersion) { mutableFloatStateOf(settings.opacityPercent.toFloat()) }
    var radius by remember(settings, resetVersion) { mutableFloatStateOf(settings.blurRadiusDp.toFloat()) }
    var useSystemWallpaper by remember(settings, resetVersion) { mutableStateOf(settings.useSystemWallpaper) }
    var resizeHandleEnabled by remember(settings, resetVersion) { mutableStateOf(settings.startMenuResizeEnabled) }

    SettingSwitch(
        label = stringResource(R.string.use_system_wallpaper),
        description = stringResource(R.string.use_system_wallpaper_description),
        checked = useSystemWallpaper,
        onCheckedChange = {
            useSystemWallpaper = it
            settings.useSystemWallpaper = it
            onChanged()
        }
    )
    SettingSwitch(
        label = stringResource(R.string.resize_start_menu),
        description = stringResource(R.string.resize_start_menu_description),
        checked = resizeHandleEnabled,
        onCheckedChange = {
            resizeHandleEnabled = it
            settings.startMenuResizeEnabled = it
            onChanged()
        }
    )


    Text(stringResource(R.string.background_opacity), style = MaterialTheme.typography.titleSmall)
    val opacityDescription = stringResource(R.string.background_opacity_semantics)
    val opacityValueDescription = stringResource(R.string.opacity_percent_description, opacity.toInt())
    Slider(
        value = opacity,
        onValueChange = {
            opacity = it
            val percent = it.toInt()
            if (settings.opacityPercent != percent) {
                settings.opacityPercent = percent
                onChanged()
            }
        },
        valueRange = 0f..100f,
        steps = 99,
        modifier = Modifier.semantics {
            contentDescription = opacityDescription
        }
    )
    Text(
        text = stringResource(R.string.opacity_percent_value, opacity.toInt()),
        modifier = Modifier.semantics {
            contentDescription = opacityValueDescription
        }
    )

    Text(stringResource(R.string.background_blur), style = MaterialTheme.typography.titleSmall)
    val blurDescription = stringResource(R.string.background_blur_radius_semantics)
    val radiusDescription = stringResource(R.string.blur_dp_description, radius.toInt())
    Slider(
        value = radius,
        onValueChange = {
            radius = it
            val value = it.toInt()
            if (settings.blurRadiusDp != value) {
                settings.blurRadiusDp = value
                onChanged()
            }
        },
        valueRange = 0f..100f,
        steps = 99,
        modifier = Modifier.semantics {
            contentDescription = blurDescription
        }
    )
    Text(
        text = stringResource(R.string.blur_dp_value, radius.toInt()),
        modifier = Modifier.semantics {
            contentDescription = radiusDescription
        }
    )
    if (availabilityMessageVisible) {
        Text(stringResource(R.string.live_blur_unavailable))
    }
}

@Composable
private fun ConfigurePage(
    settings: AppearanceSettings,
    onChanged: () -> Unit,
    onDeviceSettings: () -> Unit,
    onSetup: () -> Unit,
    onLauncherDensityMultiplierChanged: (Float) -> Unit
) {
    var showAllTasks by remember(settings) { mutableStateOf(settings.showAllTasks) }
    var iconsOnly by remember(settings) { mutableStateOf(settings.taskbarIconsOnly) }
    var launcherDensity by remember(settings) { mutableFloatStateOf(settings.launcherDensityMultiplier) }
    var appWindowDensity by remember(settings) { mutableFloatStateOf(settings.appWindowDensityMultiplier) }

    SettingSwitch(
        label = stringResource(R.string.show_all_android_tasks),
        description = stringResource(R.string.show_all_android_tasks_description),
        checked = showAllTasks,
        onCheckedChange = {
            showAllTasks = it
            settings.showAllTasks = it
            onChanged()
        }
    )
    DensitySlider(
        label = stringResource(R.string.launcher_density),
        semanticsLabel = stringResource(R.string.launcher_density_semantics),
        value = launcherDensity,
        onValueChange = { value ->
            launcherDensity = value
            settings.launcherDensityMultiplier = value
            onLauncherDensityMultiplierChanged(value)
            onChanged()
        }
    )
    DensitySlider(
        label = stringResource(R.string.app_window_density),
        semanticsLabel = stringResource(R.string.app_window_density_semantics),
        value = appWindowDensity,
        onValueChange = { value ->
            appWindowDensity = value
            settings.appWindowDensityMultiplier = value
            onChanged()
        }
    )
    Text(stringResource(R.string.show_all_android_tasks_help), style = MaterialTheme.typography.bodySmall)
    SettingSwitch(
        label = stringResource(R.string.show_taskbar_icons_only),
        description = stringResource(R.string.show_taskbar_icons_only_description),
        checked = iconsOnly,
        onCheckedChange = {
            iconsOnly = it
            settings.taskbarIconsOnly = it
            onChanged()
        }
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onDeviceSettings, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.device_settings))
        }
        Button(onClick = onSetup, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.shizuku_setup))
        }
    }
}

@Composable
private fun DensitySlider(
    label: String,
    semanticsLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    val valueText = stringResource(R.string.density_multiplier_value, value)
    val valueDescription = stringResource(R.string.density_multiplier_description, label, value)
    Slider(
        value = value,
        onValueChange = { raw ->
            onValueChange(AppearanceSettings.quantizeDensityMultiplier(raw))
        },
        valueRange = AppearanceSettings.MIN_DENSITY_MULTIPLIER..AppearanceSettings.MAX_DENSITY_MULTIPLIER,
        steps = 38,
        modifier = Modifier.semantics { contentDescription = semanticsLabel }
    )
    Text(valueText, modifier = Modifier.semantics { contentDescription = valueDescription })
}

@Composable
private fun SettingSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
