package xtr.compound

import android.content.Intent
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

/** A small, self-contained appearance dialog so the app's existing layouts need not change. */
object CompoundSettingsDialog {
    private var activeController: Controller? = null
    private var activeDialog: AlertDialog? = null

    class Controller internal constructor(
        private val blurAvailable: () -> Boolean,
        private val blurSlider: Slider,
        private val availabilityText: TextView
    ) {
        fun refreshAvailability() {
            val available = blurAvailable()
            blurSlider.isEnabled = true
            availabilityText.visibility = if (available) View.GONE else View.VISIBLE
        }
    }

    /** Refresh the currently displayed dialog after the system's blur state changes. */
    fun refreshAvailability() {
        activeController?.refreshAvailability()
    }

    /** Dismiss the current dialog, if any, for the owning activity's teardown. */
    fun dismiss() {
        activeDialog?.dismiss()
        activeDialog = null
        activeController = null
    }

    fun show(
        activity: AppCompatActivity,
        settings: AppearanceSettings,
        blurAvailable: () -> Boolean,
        onChanged: () -> Unit,
        onSetup: () -> Unit
    ): Controller {
        dismiss()
        var dialogBlur: LiveBackgroundBlur? = null
        fun applyChanges() {
            onChanged()
            dialogBlur?.update(settings.opacityPercent, settings.blurRadiusDp)
        }
        val density = activity.resources.displayMetrics.density
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (8 * density).toInt(), (24 * density).toInt(), 0)
        }
        fun text(label: String, description: String? = null) = TextView(activity).apply {
            text = label
            contentDescription = description ?: label
            setTextColor(activity.getColor(R.color.compoundOnSurface))
            setPadding(0, (8 * density).toInt(), 0, (4 * density).toInt())
        }
        fun slider(description: String) = Slider(activity).apply {
            valueFrom = 0f
            valueTo = 100f
            stepSize = 1f
            contentDescription = description
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val scope = SwitchMaterial(activity).apply {
            text = "Show all Android tasks"
            contentDescription = "Show all Android tasks. Off applies the taskbar only to Compound."
            isChecked = settings.showAllTasks
            setOnCheckedChangeListener { _, checked ->
                settings.showAllTasks = checked
                applyChanges()
            }
        }
        content.addView(scope)
        content.addView(text("When off, Compound shows only its own windows."))
        content.addView(text("Background opacity"))
        val opacityValue = text("${settings.opacityPercent}%", "Opacity ${settings.opacityPercent} percent")
        val opacity = slider("Background opacity, percent").apply {
            value = settings.opacityPercent.toFloat()
            addOnChangeListener { _, value, fromUser ->
                val percent = value.toInt()
                opacityValue.text = "$percent%"
                opacityValue.contentDescription = "Opacity $percent percent"
                if (fromUser) {
                    settings.opacityPercent = percent
                    applyChanges()
                }
            }
        }
        content.addView(opacity)
        content.addView(opacityValue)

        content.addView(text("Background blur"))
        val blurValue = text("${settings.blurRadiusDp} dp", "Background blur ${settings.blurRadiusDp} dp")
        val blur = slider("Background blur radius, dp").apply {
            value = settings.blurRadiusDp.toFloat()
            addOnChangeListener { _, value, fromUser ->
                val radius = value.toInt()
                blurValue.text = "$radius dp"
                blurValue.contentDescription = "Background blur $radius dp"
                if (fromUser) {
                    settings.blurRadiusDp = radius
                    applyChanges()
                }
            }
        }
        content.addView(blur)
        content.addView(blurValue)
        val availability = text("Live blur is unavailable. Using legacy snapshot blur; backgrounds refresh when opened.")
        availability.contentDescription = availability.text
        content.addView(availability)

        val actions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(activity).apply {
            text = "Device settings"
            contentDescription = "Open device settings"
            setOnClickListener {
                activity.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Button(activity).apply {
            text = "Shizuku setup"
            contentDescription = "Open Shizuku setup"
            setOnClickListener { onSetup() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(actions)

        val scroll = ScrollView(activity).apply { addView(content) }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle("Compound settings")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .setNeutralButton("Reset appearance", null)
            .create()
        dialog.setOnShowListener {
            val dialogWindow = requireNotNull(dialog.window)
            dialogWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            val panel = dialogWindow.decorView.findViewById<android.widget.FrameLayout>(android.R.id.content)
            panel.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.TRANSPARENT)
                cornerRadius = 28 * density
            }
            panel.clipToOutline = true
            val background = android.widget.ImageView(activity).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                scaleType = android.widget.ImageView.ScaleType.FIT_XY
            }
            panel.addView(background, 0, android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val surfaceScrim = View(activity).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setBackgroundColor(activity.getColor(R.color.settingsDialogScrim))
            }
            panel.addView(surfaceScrim, 1, android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val legacy = LegacyBackgroundBlur(background, panel, activity.window, hideOverlay = false)
            dialogBlur = LiveBackgroundBlur(background, 28f, legacy).also { helper ->
                helper.onAvailabilityChanged = { refreshAvailability() }
                helper.update(settings.opacityPercent, settings.blurRadiusDp)
            }
            refreshAvailability()
            background.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) legacy.refresh()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                settings.resetAppearance()
                opacity.value = AppearanceSettings.DEFAULT_OPACITY.toFloat()
                blur.value = AppearanceSettings.DEFAULT_BLUR_RADIUS.toFloat()
                opacityValue.text = "${AppearanceSettings.DEFAULT_OPACITY}%"
                opacityValue.contentDescription = "Opacity ${AppearanceSettings.DEFAULT_OPACITY} percent"
                blurValue.text = "${AppearanceSettings.DEFAULT_BLUR_RADIUS} dp"
                blurValue.contentDescription = "Background blur ${AppearanceSettings.DEFAULT_BLUR_RADIUS} dp"
                applyChanges()
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
        return Controller({ blurAvailable() && dialogBlur?.isAvailable == true }, blur, availability).also {
            activeDialog = dialog
            activeController = it
            it.refreshAvailability()
        }
    }
}
