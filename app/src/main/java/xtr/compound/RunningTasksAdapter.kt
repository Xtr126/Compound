package xtr.compound

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** One row represents a hosted window or an external Android task, never a package process. */
data class TaskbarEntry(
    val key: String,
    val title: String,
    val packageName: String,
    val displayId: Int,
    val taskId: Int?,
    val hosted: Boolean,
    val minimized: Boolean = false,
    val maximized: Boolean = false
)

class RunningTasksAdapter(
    private val context: Context,
    private val focus: (TaskbarEntry) -> Unit,
    private val close: (TaskbarEntry) -> Unit
) : RecyclerView.Adapter<RunningTasksAdapter.Holder>() {
    private var entries = emptyList<TaskbarEntry>()
    private val icons = mutableMapOf<String, Drawable?>()
    private val labels = mutableMapOf<String, String>()
    private var iconOnly = false
    private var activeKey: String? = null
    private var attachedRecycler: RecyclerView? = null
    private var originalPadding: IntArray? = null

    private val layoutListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        updateIconCentering(view as RecyclerView)
    }

    fun setIconsOnly(value: Boolean) {
        if (iconOnly == value) return
        iconOnly = value
        attachedRecycler?.let(::updateIconCentering)
        notifyDataSetChanged()
    }

    fun setActiveEntry(key: String) {
        if (activeKey == key) return
        val oldPosition = entries.indexOfFirst { it.key == activeKey }
        val newPosition = entries.indexOfFirst { it.key == key }
        activeKey = key
        if (oldPosition >= 0) notifyItemChanged(oldPosition)
        if (newPosition >= 0) notifyItemChanged(newPosition)
    }

    fun submit(value: List<TaskbarEntry>) {
        if (entries == value) return
        entries = value
        if (entries.none { it.key == activeKey }) {
            activeKey = entries.firstOrNull { !it.minimized }?.key
        }
        notifyDataSetChanged()
        attachedRecycler?.let(::updateIconCentering)
    }

    override fun getItemCount() = entries.size

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecycler = recyclerView
        originalPadding = intArrayOf(recyclerView.paddingLeft, recyclerView.paddingTop,
            recyclerView.paddingRight, recyclerView.paddingBottom)
        recyclerView.addOnLayoutChangeListener(layoutListener)
        updateIconCentering(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        recyclerView.removeOnLayoutChangeListener(layoutListener)
        originalPadding?.let { recyclerView.setPadding(it[0], it[1], it[2], it[3]) }
        originalPadding = null
        attachedRecycler = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56))
            isFocusable = true
            isClickable = true
        }
        val contents = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52))
        }
        val icon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = null
        }
        contents.addView(icon, LinearLayout.LayoutParams(dp(32), dp(32)))
        val label = TextView(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            maxWidth = dp(200)
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(10), 0, dp(4), 0)
        }
        contents.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
        val stateIcon = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        contents.addView(stateIcon, LinearLayout.LayoutParams(dp(18), dp(18)))
        val closeButton = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Close task"
        }
        contents.addView(closeButton, LinearLayout.LayoutParams(dp(40), dp(48)))
        val indicator = View(context).apply {
            background = GradientDrawable().apply {
                setColor(resolvePrimaryColor())
                cornerRadius = dp(2).toFloat()
            }
            visibility = View.INVISIBLE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val indicatorParams = LinearLayout.LayoutParams(dp(20), dp(3)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = 0
        }
        row.addView(contents)
        row.addView(indicator, indicatorParams)
        return Holder(row, contents, icon, label, stateIcon, closeButton, indicator)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = entries[position]
        val appLabel = appLabel(entry)
        val icon = icons.getOrPut(entry.packageName) {
            try { context.packageManager.getApplicationIcon(entry.packageName) }
            catch (_: Exception) { null }
        }?.constantState?.newDrawable()?.mutate()
        holder.icon.setImageDrawable(icon)
        val stateResource = when {
            entry.minimized -> R.drawable.window_minimized
            entry.maximized -> R.drawable.window_maximized
            else -> null
        }
        holder.stateIcon.setImageDrawable(stateResource?.let {
            androidx.appcompat.content.res.AppCompatResources.getDrawable(context, it)?.mutate()
        })
        holder.label.text = appLabel + if (entry.minimized) " (minimized)" else ""
        val active = entry.key == activeKey
        holder.indicator.visibility = if (iconOnly && active) View.VISIBLE else View.INVISIBLE
        holder.label.visibility = if (iconOnly) View.GONE else View.VISIBLE
        holder.stateIcon.visibility = if (iconOnly || stateResource == null) View.GONE else View.VISIBLE
        holder.close.visibility = if (iconOnly) View.GONE else View.VISIBLE
        holder.contents.gravity = if (iconOnly) Gravity.CENTER else Gravity.CENTER_VERTICAL
        holder.row.layoutParams = (holder.row.layoutParams as? RecyclerView.LayoutParams
            ?: RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56))).apply {
            width = if (iconOnly) dp(64) else ViewGroup.LayoutParams.WRAP_CONTENT
            height = dp(56)
        }
        val stateDescription = when {
            entry.minimized -> ", minimized"
            entry.maximized -> ", maximized"
            else -> ""
        }
        holder.row.contentDescription = "$appLabel$stateDescription${if (active) ", active task" else ""}" +
            if (iconOnly) ". Long press to close" else ""
        holder.row.tooltipText = appLabel
        holder.row.setOnClickListener { focus(entry) }
        holder.row.setOnLongClickListener {
            if (iconOnly) { close(entry); true } else false
        }
        holder.close.contentDescription = "Close $appLabel"
        holder.close.setOnClickListener { close(entry) }
    }

    private fun appLabel(entry: TaskbarEntry): String {
        labels[entry.packageName]?.let { return it }
        val resolved = runCatching {
            val app = context.packageManager.getApplicationInfo(entry.packageName, 0)
            context.packageManager.getApplicationLabel(app).toString().takeIf { it.isNotBlank() }
        }.getOrNull()
        if (resolved != null) labels[entry.packageName] = resolved
        return resolved ?: entry.title.takeIf { it.isNotBlank() } ?: entry.packageName
    }

    private fun updateIconCentering(recyclerView: RecyclerView) {
        if (!iconOnly) {
            originalPadding?.let { recyclerView.setPadding(it[0], it[1], it[2], it[3]) }
            return
        }
        val horizontalInset = ((recyclerView.width - entries.size * dp(64)) / 2).coerceAtLeast(0)
        recyclerView.setPadding(horizontalInset, recyclerView.paddingTop, horizontalInset, recyclerView.paddingBottom)
    }

    private fun resolvePrimaryColor(): Int {
        val attrs = intArrayOf(android.R.attr.colorAccent)
        val typed = context.obtainStyledAttributes(attrs)
        return try { typed.getColor(0, 0xFF6750A4.toInt()) } finally { typed.recycle() }
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    class Holder(
        val row: LinearLayout,
        val contents: LinearLayout,
        val icon: ImageView,
        val label: TextView,
        val stateIcon: ImageView,
        val close: ImageButton,
        val indicator: View
    ) : RecyclerView.ViewHolder(row)
}
