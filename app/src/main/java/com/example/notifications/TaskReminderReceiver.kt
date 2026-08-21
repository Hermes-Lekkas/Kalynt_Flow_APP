package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.data.local.AppDatabase
import com.example.widget.KalyntFlowTasksWidgetProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TaskReminderReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            ACTION_TRIGGER_TASK_REMINDER -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                val title = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Task Reminder"
                val workspaceName = intent.getStringExtra(EXTRA_WORKSPACE_NAME) ?: "General"
                val priority = intent.getStringExtra(EXTRA_PRIORITY) ?: "MEDIUM"
                val dueDateMs = intent.getLongExtra(EXTRA_DUE_DATE_MS, 0L)

                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val task = db.taskDao().getTaskByIdSync(taskId)
                        // Only show notification if task exists and is not completed
                        if (task != null && !task.isCompleted) {
                            NotificationHelper.showTaskReminder(
                                context = context,
                                taskId = taskId,
                                title = task.title.ifBlank { title },
                                workspaceName = workspaceName,
                                priority = priority,
                                dueDateMs = dueDateMs
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_COMPLETE_TASK_NOTIFICATION -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, taskId.hashCode())

                NotificationHelper.cancelNotification(context, notificationId)

                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        db.taskDao().completeTaskSync(taskId)
                        try {
                            FirebaseFirestore.getInstance()
                                .collection("tasks")
                                .document(taskId)
                                .update("isCompleted", true)
                                .await()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        KalyntFlowTasksWidgetProvider.updateAllWidgets(context)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_SNOOZE_TASK -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
                val title = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Task Reminder"
                val workspaceName = intent.getStringExtra(EXTRA_WORKSPACE_NAME) ?: "General"
                val priority = intent.getStringExtra(EXTRA_PRIORITY) ?: "MEDIUM"
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, taskId.hashCode())

                NotificationHelper.cancelNotification(context, notificationId)

                // Snooze for 15 minutes
                val snoozeTimeMs = System.currentTimeMillis() + (15 * 60 * 1000L)
                NotificationScheduler.scheduleTaskReminder(
                    context = context,
                    taskId = taskId,
                    title = title,
                    workspaceName = workspaceName,
                    priority = priority,
                    triggerAtMs = snoozeTimeMs
                )
                Toast.makeText(context, "Reminder snoozed for 15 minutes", Toast.LENGTH_SHORT).show()
            }

            ACTION_DAILY_BRIEFING -> {
                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val activeTasks = db.taskDao().getAllActiveTasksSync()
                        if (activeTasks.isNotEmpty()) {
                            NotificationHelper.showDailyDigest(
                                context = context,
                                pendingCount = activeTasks.size,
                                topTaskTitles = activeTasks.map { it.title }
                            )
                        }
                        // Reschedule for next day
                        NotificationScheduler.scheduleDailyBriefing(context, 8, 30)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_BACKGROUND_SYNC -> {
                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        BackgroundSyncManager.syncOnce(context)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        // Reschedule periodic background sync
                        NotificationScheduler.schedulePeriodicBackgroundSync(context)
                        pendingResult.finish()
                    }
                }
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                val pendingResult = goAsync()
                receiverScope.launch {
                    try {
                        NotificationScheduler.rescheduleAllActiveTasks(context)
                        NotificationScheduler.scheduleDailyBriefing(context, 8, 30)
                        NotificationScheduler.schedulePeriodicBackgroundSync(context)
                        BackgroundSyncManager.start(context)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER_TASK_REMINDER = "com.example.ACTION_TRIGGER_TASK_REMINDER"
        const val ACTION_COMPLETE_TASK_NOTIFICATION = "com.example.ACTION_COMPLETE_TASK_NOTIFICATION"
        const val ACTION_SNOOZE_TASK = "com.example.ACTION_SNOOZE_TASK"
        const val ACTION_DAILY_BRIEFING = "com.example.ACTION_DAILY_BRIEFING"
        const val ACTION_BACKGROUND_SYNC = "com.example.ACTION_BACKGROUND_SYNC"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_WORKSPACE_NAME = "extra_workspace_name"
        const val EXTRA_PRIORITY = "extra_priority"
        const val EXTRA_DUE_DATE_MS = "extra_due_date_ms"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
