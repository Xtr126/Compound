package xtr.compound

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
