package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.local.AppDatabase
import java.util.Calendar

object NotificationScheduler {

    private const val TAG = "NotificationScheduler"

    fun scheduleTaskReminder(
        context: Context,
        taskId: String,
        title: String,
        workspaceName: String,
        priority: String,
        triggerAtMs: Long
    ) {
        if (triggerAtMs <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = TaskReminderReceiver.ACTION_TRIGGER_TASK_REMINDER
            putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, title)
            putExtra(TaskReminderReceiver.EXTRA_WORKSPACE_NAME, workspaceName)
            putExtra(TaskReminderReceiver.EXTRA_PRIORITY, priority)
            putExtra(TaskReminderReceiver.EXTRA_DUE_DATE_MS, triggerAtMs)
        }

        val requestCode = taskId.hashCode()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

        try {
            // AlarmClockInfo is the gold standard for reliable exact wakeup even in deep sleep/locked phone
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMs, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Scheduled AlarmClock for task $taskId at $triggerAtMs")
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
                }
            } catch (ex: Exception) {
                try {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
                } catch (e3: Exception) {
                    e3.printStackTrace()
                }
            }
        }
    }

    fun cancelTaskReminder(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = TaskReminderReceiver.ACTION_TRIGGER_TASK_REMINDER
        }
        val requestCode = taskId.hashCode()
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleDailyBriefing(context: Context, hourOfDay: Int = 8, minute: Int = 30) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = TaskReminderReceiver.ACTION_DAILY_BRIEFING
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, 8888, intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun schedulePeriodicBackgroundSync(context: Context, delayMs: Long = 5 * 60 * 1000L) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val triggerAtMs = System.currentTimeMillis() + delayMs
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = TaskReminderReceiver.ACTION_BACKGROUND_SYNC
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, 7777, intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun rescheduleAllActiveTasks(context: Context) {
        try {
            val db = AppDatabase.getDatabase(context)
            val activeTasks = db.taskDao().getAllActiveTasksSync()
            val now = System.currentTimeMillis()
            for (task in activeTasks) {
                if (task.dueDateMs > now) {
                    scheduleTaskReminder(
                        context = context,
                        taskId = task.id,
                        title = task.title,
                        workspaceName = "Kalynt Flow",
                        priority = "HIGH",
                        triggerAtMs = task.dueDateMs
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
