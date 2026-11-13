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

    fun resetAppearance() {
        preferences.edit()
            .putInt(KEY_OPACITY, DEFAULT_OPACITY)
            .putInt(KEY_BLUR_RADIUS, DEFAULT_BLUR_RADIUS)
            .putBoolean(KEY_START_MENU_RESIZE_ENABLED, DEFAULT_START_MENU_RESIZE_ENABLED)
            .putInt(KEY_START_MENU_WIDTH_DP, DEFAULT_START_MENU_WIDTH_DP)
            .putInt(KEY_START_MENU_HEIGHT_DP, DEFAULT_START_MENU_HEIGHT_DP)
            .apply()
    }

    companion object {
        const val DEFAULT_OPACITY = 80
        const val DEFAULT_BLUR_RADIUS = 25
        const val DEFAULT_ALL_TASKS = false
        const val DEFAULT_TASKBAR_ICONS_ONLY = false
        const val DEFAULT_START_MENU_RESIZE_ENABLED = false
        const val DEFAULT_START_MENU_WIDTH_DP = 620
        const val DEFAULT_START_MENU_HEIGHT_DP = 580
        const val MIN_START_MENU_WIDTH_DP = 320
        const val MIN_START_MENU_HEIGHT_DP = 280
        const val MAX_START_MENU_SIZE_DP = 4096
        private const val PREFS_NAME = "appearance_settings"
        private const val KEY_OPACITY = "opacity_percent"
        private const val KEY_BLUR_RADIUS = "blur_radius_dp"
        private const val KEY_ALL_TASKS = "show_all_tasks"
        private const val KEY_TASKBAR_ICONS_ONLY = "taskbar_icons_only"
        private const val KEY_START_MENU_RESIZE_ENABLED = "start_menu_resize_enabled"
        private const val KEY_START_MENU_WIDTH_DP = "start_menu_width_dp"
        private const val KEY_START_MENU_HEIGHT_DP = "start_menu_height_dp"
    }
}
