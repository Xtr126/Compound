package xtr.compound

import android.content.Context

/** Persisted user-facing appearance preferences for Compound's taskbar and start menu. */
class AppearanceSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var opacityPercent: Int
        get() = preferences.getInt(KEY_OPACITY, DEFAULT_OPACITY).coerceIn(0, 100)
        set(value) { preferences.edit().putInt(KEY_OPACITY, value.coerceIn(0, 100)).apply() }

    var blurRadiusDp: Int
        get() = preferences.getInt(KEY_BLUR_RADIUS, DEFAULT_BLUR_RADIUS).coerceIn(0, 100)
        set(value) { preferences.edit().putInt(KEY_BLUR_RADIUS, value.coerceIn(0, 100)).apply() }

    /** When false, settings apply to Compound's own taskbar/menu only. */
    var showAllTasks: Boolean
        get() = preferences.getBoolean(KEY_ALL_TASKS, DEFAULT_ALL_TASKS)
        set(value) { preferences.edit().putBoolean(KEY_ALL_TASKS, value).apply() }

    /** Show only centered app icons in the taskbar. */
    var taskbarIconsOnly: Boolean
        get() = preferences.getBoolean(KEY_TASKBAR_ICONS_ONLY, DEFAULT_TASKBAR_ICONS_ONLY)
        set(value) { preferences.edit().putBoolean(KEY_TASKBAR_ICONS_ONLY, value).apply() }

    /** Use Android's current wallpaper behind the desktop instead of Compound's bundled image. */
    var useSystemWallpaper: Boolean
        get() = preferences.getBoolean(KEY_USE_SYSTEM_WALLPAPER, DEFAULT_USE_SYSTEM_WALLPAPER)
        set(value) { preferences.edit().putBoolean(KEY_USE_SYSTEM_WALLPAPER, value).apply() }
    /** Whether the start menu exposes a draggable top-right resize handle. */
    var startMenuResizeEnabled: Boolean
        get() = preferences.getBoolean(KEY_START_MENU_RESIZE_ENABLED, DEFAULT_START_MENU_RESIZE_ENABLED)
        set(value) { preferences.edit().putBoolean(KEY_START_MENU_RESIZE_ENABLED, value).apply() }

    var startMenuWidthDp: Int
        get() = preferences.getInt(KEY_START_MENU_WIDTH_DP, DEFAULT_START_MENU_WIDTH_DP).coerceIn(MIN_START_MENU_WIDTH_DP, MAX_START_MENU_SIZE_DP)
        set(value) { preferences.edit().putInt(KEY_START_MENU_WIDTH_DP, value.coerceIn(MIN_START_MENU_WIDTH_DP, MAX_START_MENU_SIZE_DP)).apply() }

    var startMenuHeightDp: Int
        get() = preferences.getInt(KEY_START_MENU_HEIGHT_DP, DEFAULT_START_MENU_HEIGHT_DP).coerceIn(MIN_START_MENU_HEIGHT_DP, MAX_START_MENU_SIZE_DP)
        set(value) { preferences.edit().putInt(KEY_START_MENU_HEIGHT_DP, value.coerceIn(MIN_START_MENU_HEIGHT_DP, MAX_START_MENU_SIZE_DP)).apply() }
    /** Density multiplier for Compound's own Compose desktop UI, without changing device density. */
    var launcherDensityMultiplier: Float
        get() = readDensityMultiplier(KEY_LAUNCHER_DENSITY_MULTIPLIER)
        set(value) { preferences.edit().putFloat(KEY_LAUNCHER_DENSITY_MULTIPLIER, quantizeDensityMultiplier(value)).apply() }

    /** Density multiplier used for the virtual displays that host app windows. */
    var appWindowDensityMultiplier: Float
        get() = readDensityMultiplier(KEY_APP_WINDOW_DENSITY_MULTIPLIER)
        set(value) { preferences.edit().putFloat(KEY_APP_WINDOW_DENSITY_MULTIPLIER, quantizeDensityMultiplier(value)).apply() }

    private fun readDensityMultiplier(key: String): Float =
        quantizeDensityMultiplier(preferences.getFloat(key, DEFAULT_DENSITY_MULTIPLIER))

    fun resetAppearance() {
        preferences.edit()
            .putInt(KEY_OPACITY, DEFAULT_OPACITY)
            .putInt(KEY_BLUR_RADIUS, DEFAULT_BLUR_RADIUS)
            .putBoolean(KEY_USE_SYSTEM_WALLPAPER, DEFAULT_USE_SYSTEM_WALLPAPER)
            .putBoolean(KEY_START_MENU_RESIZE_ENABLED, DEFAULT_START_MENU_RESIZE_ENABLED)
            .putInt(KEY_START_MENU_WIDTH_DP, DEFAULT_START_MENU_WIDTH_DP)
            .putInt(KEY_START_MENU_HEIGHT_DP, DEFAULT_START_MENU_HEIGHT_DP)
            .putFloat(KEY_LAUNCHER_DENSITY_MULTIPLIER, DEFAULT_DENSITY_MULTIPLIER)
            .putFloat(KEY_APP_WINDOW_DENSITY_MULTIPLIER, DEFAULT_DENSITY_MULTIPLIER)
            .apply()
    }

    companion object {
        const val DEFAULT_OPACITY = 80
        const val DEFAULT_BLUR_RADIUS = 25
        const val DEFAULT_ALL_TASKS = false
        const val DEFAULT_TASKBAR_ICONS_ONLY = false
        const val DEFAULT_USE_SYSTEM_WALLPAPER = false
        const val DEFAULT_START_MENU_RESIZE_ENABLED = false
        const val DEFAULT_START_MENU_WIDTH_DP = 620
        const val DEFAULT_START_MENU_HEIGHT_DP = 580
        const val MIN_START_MENU_WIDTH_DP = 320
        const val MIN_START_MENU_HEIGHT_DP = 280
        const val MAX_START_MENU_SIZE_DP = 4096
        const val DEFAULT_DENSITY_MULTIPLIER = 1.0f
        const val MIN_DENSITY_MULTIPLIER = 0.1f
        const val MAX_DENSITY_MULTIPLIER = 4.0f

        fun quantizeDensityMultiplier(value: Float): Float =
            (kotlin.math.round(value.coerceIn(MIN_DENSITY_MULTIPLIER, MAX_DENSITY_MULTIPLIER) * 10f) / 10f)
        private const val PREFS_NAME = "appearance_settings"
        private const val KEY_OPACITY = "opacity_percent"
        private const val KEY_BLUR_RADIUS = "blur_radius_dp"
        private const val KEY_ALL_TASKS = "show_all_tasks"
        private const val KEY_TASKBAR_ICONS_ONLY = "taskbar_icons_only"
        private const val KEY_USE_SYSTEM_WALLPAPER = "use_system_wallpaper"
        private const val KEY_START_MENU_RESIZE_ENABLED = "start_menu_resize_enabled"
        private const val KEY_START_MENU_WIDTH_DP = "start_menu_width_dp"
        private const val KEY_START_MENU_HEIGHT_DP = "start_menu_height_dp"
        private const val KEY_LAUNCHER_DENSITY_MULTIPLIER = "launcher_density_multiplier"
        private const val KEY_APP_WINDOW_DENSITY_MULTIPLIER = "app_window_density_multiplier"
    }
}
