package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class KalyntFlowTasksWidgetProvider : AppWidgetProvider() {

    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        updateAllWidgets(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        suspend fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_tasks_summary)

            // 1. Header click - Open Kalynt Flow to Tasks
            val rootIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.example.ACTION_OPEN_TASKS"
                data = Uri.parse("kalynt://widget/tasks_root")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "tasks")
            }
            val rootPendingIntent = PendingIntent.getActivity(
                context, 200, rootIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_tasks_root, rootPendingIntent)

            // 2. Add Task in Header
            val addTaskIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.example.ACTION_ADD_TASK_HEADER"
                data = Uri.parse("kalynt://widget/add_task_header")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "tasks")
                putExtra("open_add_dialog", true)
            }
            val addTaskPendingIntent = PendingIntent.getActivity(
                context, 201, addTaskIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_header_add_task, addTaskPendingIntent)

            // 3. Query Database for Tasks Count
            try {
                val db = AppDatabase.getDatabase(context)
                var activeTasks = db.taskDao().getActiveTasksSync()
                var totalCount = db.taskDao().getActiveTasksCountSync()

                if (activeTasks.isEmpty()) {
                    try {
                        val auth = FirebaseAuth.getInstance()
                        val user = auth.currentUser
                        val email = if (user != null) {
                            if (!user.email.isNullOrBlank()) user.email else "guest_${user.uid.take(8)}@kalyntflow.app"
                        } else null
                        if (email != null) {
                            val snap = FirebaseFirestore.getInstance()
                                .collection("tasks")
                                .whereArrayContains("memberEmails", email)
                                .get()
                                .await()
                            val cloudTasks = snap.toObjects(com.example.data.local.TaskEntity::class.java)
                            if (cloudTasks.isNotEmpty()) {
                                db.taskDao().syncAllTasks(cloudTasks)
                                activeTasks = db.taskDao().getActiveTasksSync()
                                totalCount = db.taskDao().getActiveTasksCountSync()
                            }
                        }
                    } catch (e: Exception) {
                        // ignore network fallback errors
                    }
                }

                views.setTextViewText(
                    R.id.widget_task_count_badge,
                    if (totalCount == 1) "1 item" else "$totalCount items"
                )
            } catch (e: Exception) {
                views.setTextViewText(R.id.widget_task_count_badge, "Items")
            }

            // 4. Set up Scrollable RemoteViews Adapter via KalyntFlowWidgetService
            val serviceIntent = Intent(context, KalyntFlowWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_tasks_list, serviceIntent)
            views.setEmptyView(R.id.widget_tasks_list, R.id.widget_tasks_empty)

            // 5. Template PendingIntent for list items (supports task/note click deep links)
            val itemClickIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.example.ACTION_ITEM_CLICK"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val itemClickPendingIntent = PendingIntent.getActivity(
                context,
                250,
                itemClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_tasks_list, itemClickPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_tasks_list)
        }

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, KalyntFlowTasksWidgetProvider::class.java)
                val ids = appWidgetManager.getAppWidgetIds(componentName)
                if (ids != null && ids.isNotEmpty()) {
                    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                    scope.launch {
                        for (id in ids) {
                            updateAppWidget(context, appWidgetManager, id)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore widget errors
            }
        }
    }
}
