package xtr.compound

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.ImageButton
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
    val minimized: Boolean = false
)

class RunningTasksAdapter(
    private val context: Context,
    private val focus: (TaskbarEntry) -> Unit,
    private val close: (TaskbarEntry) -> Unit
) : RecyclerView.Adapter<RunningTasksAdapter.Holder>() {
    private var entries = emptyList<TaskbarEntry>()
    private val icons = mutableMapOf<String, Drawable?>()

    fun submit(value: List<TaskbarEntry>) {
        if (entries == value) return
        entries = value
        notifyDataSetChanged()
    }

    override fun getItemCount() = entries.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56))
        }
        val label = TextView(context).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            maxLines = 1
            maxWidth = dp(200)
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(10), 0, dp(4), 0)
            compoundDrawablePadding = dp(6)
            isFocusable = true
        }
        val closeButton = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        row.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
        row.addView(closeButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        return Holder(row, label, closeButton)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = entries[position]
        val icon = icons.getOrPut(entry.packageName) {
            try { context.packageManager.getApplicationIcon(entry.packageName) }
            catch (_: Exception) { null }
        }?.constantState?.newDrawable()?.mutate()
        icon?.setBounds(0, 0, dp(24), dp(24))
        holder.label.setCompoundDrawables(icon, null, null, null)
        holder.label.text = entry.title + if (entry.minimized) " (minimized)" else ""
        holder.label.contentDescription = "Focus ${entry.title}"
        holder.label.setOnClickListener { focus(entry) }
        holder.label.tooltipText = entry.title
        holder.close.contentDescription = "Close ${entry.title}"
        holder.close.setOnClickListener { close(entry) }
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    class Holder(row: LinearLayout, val label: TextView, val close: ImageButton) : RecyclerView.ViewHolder(row)
}
