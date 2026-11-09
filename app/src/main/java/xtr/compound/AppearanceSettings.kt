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

    fun resetAppearance() {
        preferences.edit()
            .putInt(KEY_OPACITY, DEFAULT_OPACITY)
            .putInt(KEY_BLUR_RADIUS, DEFAULT_BLUR_RADIUS)
            .apply()
    }

    companion object {
        const val DEFAULT_OPACITY = 80
        const val DEFAULT_BLUR_RADIUS = 25
        const val DEFAULT_ALL_TASKS = false
        private const val PREFS_NAME = "appearance_settings"
        private const val KEY_OPACITY = "opacity_percent"
        private const val KEY_BLUR_RADIUS = "blur_radius_dp"
        private const val KEY_ALL_TASKS = "show_all_tasks"
    }
}
