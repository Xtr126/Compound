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

    fun resetAppearance() {
        preferences.edit()
            .putInt(KEY_OPACITY, DEFAULT_OPACITY)
            .putInt(KEY_BLUR_RADIUS, DEFAULT_BLUR_RADIUS)
            .putBoolean(KEY_USE_SYSTEM_WALLPAPER, DEFAULT_USE_SYSTEM_WALLPAPER)
            .apply()
    }

    companion object {
        const val DEFAULT_OPACITY = 80
        const val DEFAULT_BLUR_RADIUS = 25
        const val DEFAULT_ALL_TASKS = false
        const val DEFAULT_TASKBAR_ICONS_ONLY = false
        const val DEFAULT_USE_SYSTEM_WALLPAPER = false
        private const val PREFS_NAME = "appearance_settings"
        private const val KEY_OPACITY = "opacity_percent"
        private const val KEY_BLUR_RADIUS = "blur_radius_dp"
        private const val KEY_ALL_TASKS = "show_all_tasks"
        private const val KEY_TASKBAR_ICONS_ONLY = "taskbar_icons_only"
        private const val KEY_USE_SYSTEM_WALLPAPER = "use_system_wallpaper"
    }
}
