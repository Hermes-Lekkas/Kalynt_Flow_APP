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

class KalyntFlowQuickWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        updateAllWidgets(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_actions)

            // 1. Root click - Open Kalynt Flow
            val rootIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.example.ACTION_OPEN_HUB"
                data = Uri.parse("kalynt://widget/hub")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val rootPendingIntent = PendingIntent.getActivity(
                context, 100, rootIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, rootPendingIntent)

            // 2. Add Task Button
            val addTaskIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.example.ACTION_ADD_TASK"
                data = Uri.parse("kalynt://widget/add_task")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "tasks")
                putExtra("open_add_dialog", true)
            }
            val addTaskPendingIntent = PendingIntent.getActivity(
                context, 101, addTaskIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_add_task, addTaskPendingIntent)

            // 3. Add Note Button
            val addNoteIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.example.ACTION_ADD_NOTE"
                data = Uri.parse("kalynt://widget/add_note")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "notes")
                putExtra("open_add_dialog", true)
            }
            val addNotePendingIntent = PendingIntent.getActivity(
                context, 102, addNoteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_add_note, addNotePendingIntent)

            // 4. AI Copilot Button
            val copilotIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.example.ACTION_OPEN_COPILOT"
                data = Uri.parse("kalynt://widget/copilot")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "chat")
            }
            val copilotPendingIntent = PendingIntent.getActivity(
                context, 103, copilotIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_copilot, copilotPendingIntent)

            // 5. Workspaces Button
            val wsIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.example.ACTION_OPEN_WORKSPACES"
                data = Uri.parse("kalynt://widget/workspaces")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "workspaces")
            }
            val wsPendingIntent = PendingIntent.getActivity(
                context, 104, wsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_workspaces, wsPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, KalyntFlowQuickWidgetProvider::class.java)
                val ids = appWidgetManager.getAppWidgetIds(componentName)
                if (ids != null && ids.isNotEmpty()) {
                    for (id in ids) {
                        updateAppWidget(context, appWidgetManager, id)
                    }
                }
            } catch (e: Exception) {
                // Ignore widget manager errors if any
            }
        }
    }
}

