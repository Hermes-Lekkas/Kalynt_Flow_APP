package com.example.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.NoteEntity
import com.example.data.local.TaskEntity
import com.example.data.local.WorkspaceEntity
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KalyntFlowWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return KalyntFlowWidgetFactory(applicationContext)
    }
}

data class WidgetItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val badgeText: String,
    val badgeColor: Int,
    val iconRes: Int,
    val isTask: Boolean
)

class KalyntFlowWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private val items = mutableListOf<WidgetItem>()
    private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    private fun loadData() {
        items.clear()
        try {
            val db = AppDatabase.getDatabase(context)
            val workspaces: List<WorkspaceEntity> = runBlocking {
                try {
                    db.workspaceDao().getAllWorkspacesSync()
                } catch (e: Exception) {
                    emptyList()
                }
            }
            val workspaceMap = workspaces.associateBy { it.id }

            val activeTasks: List<TaskEntity> = runBlocking {
                try {
                    db.taskDao().getAllActiveTasksSync()
                } catch (e: Exception) {
                    emptyList()
                }
            }

            val notes: List<NoteEntity> = runBlocking {
                try {
                    db.noteDao().getAllNotesSync()
                } catch (e: Exception) {
                    emptyList()
                }
            }

            // 1. Add all active tasks
            for (task in activeTasks) {
                val wsName = workspaceMap[task.workspaceId]?.name ?: "General"
                val (badgeText, badgeColor) = getTaskBadge(task)
                val subtitle = if (task.dueDateMs > 0) {
                    "Due ${dateFormat.format(Date(task.dueDateMs))} • $wsName"
                } else {
                    "Task • $wsName"
                }
                items.add(
                    WidgetItem(
                        id = task.id,
                        title = task.title.ifBlank { "Untitled Task" },
                        subtitle = subtitle,
                        badgeText = badgeText,
                        badgeColor = badgeColor,
                        iconRes = R.drawable.ic_widget_check_empty,
                        isTask = true
                    )
                )
            }

            // 2. Add recent notes
            for (note in notes.take(15)) {
                val wsName = workspaceMap[note.workspaceId]?.name ?: "General"
                val preview = note.content.trim().lines().firstOrNull()?.take(40) ?: "Note"
                items.add(
                    WidgetItem(
                        id = note.id,
                        title = note.title.ifBlank { "Untitled Note" },
                        subtitle = "$preview • $wsName",
                        badgeText = "NOTE",
                        badgeColor = Color.parseColor("#818CF8"),
                        iconRes = R.drawable.ic_widget_note,
                        isTask = false
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getTaskBadge(task: TaskEntity): Pair<String, Int> {
        val titleLower = task.title.lowercase(Locale.ROOT)
        val descLower = task.description.lowercase(Locale.ROOT)
        val combined = "$titleLower $descLower"
        return when {
            combined.contains("urgent") || combined.contains("critical") || combined.contains("asap") || combined.contains("high") ->
                "HIGH" to Color.parseColor("#EF4444")
            combined.contains("medium") || combined.contains("review") || combined.contains("bug") ->
                "MED" to Color.parseColor("#F59E0B")
            else ->
                "LOW" to Color.parseColor("#10B981")
        }
    }

    override fun onDestroy() {
        items.clear()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position < 0 || position >= items.size) return null
        val item = items[position]
        val views = RemoteViews(context.packageName, R.layout.widget_task_item)

        views.setTextViewText(R.id.widget_item_title, item.title)
        views.setTextViewText(R.id.widget_item_subtitle, item.subtitle)
        views.setTextViewText(R.id.widget_item_badge, item.badgeText)
        views.setTextColor(R.id.widget_item_badge, item.badgeColor)
        views.setImageViewResource(R.id.widget_item_icon, item.iconRes)

        // Fill-in Intent for list item click
        val fillInIntent = Intent().apply {
            if (item.isTask) {
                putExtra("navigate_to", "tasks")
                putExtra("task_id", item.id)
            } else {
                putExtra("navigate_to", "notes")
                putExtra("note_id", item.id)
            }
        }
        views.setOnClickFillInIntent(R.id.widget_item_root, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
