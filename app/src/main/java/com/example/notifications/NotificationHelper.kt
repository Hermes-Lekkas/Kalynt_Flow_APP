package com.example.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("MissingPermission")
object NotificationHelper {

    private const val TAG = "NotificationHelper"

    const val CHANNEL_TASK_REMINDERS = "channel_task_reminders"
    const val CHANNEL_DAILY_BRIEFING = "channel_daily_briefing"
    const val CHANNEL_WORKSPACE_UPDATES = "channel_workspace_updates"
    const val CHANNEL_CHAT_MESSAGES = "channel_chat_messages"
    const val CHANNEL_AI_ASSISTANT = "channel_ai_assistant"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            // Channel 1: Task Reminders & Deadlines (IMPORTANCE_HIGH for heads-up alert)
            val taskChannel = NotificationChannel(
                CHANNEL_TASK_REMINDERS,
                "Task Reminders & Deadlines",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alerts for scheduled task deadlines and reminders"
                enableLights(true)
                lightColor = Color.parseColor("#818CF8")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // Channel 2: Daily Morning Digest
            val briefingChannel = NotificationChannel(
                CHANNEL_DAILY_BRIEFING,
                "Daily Priority Digest",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily morning summaries of your upcoming tasks and priorities"
                enableLights(true)
                lightColor = Color.parseColor("#38BDF8")
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // Channel 3: Workspace Updates
            val workspaceChannel = NotificationChannel(
                CHANNEL_WORKSPACE_UPDATES,
                "Workspace & Team Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Updates regarding team members, workspaces, and notes"
                enableLights(true)
                lightColor = Color.parseColor("#34D399")
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // Channel 4: Team Chat & Direct Messages
            val chatChannel = NotificationChannel(
                CHANNEL_CHAT_MESSAGES,
                "Team Chat & Discussions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time team chat messages, mentions, and channel discussions"
                enableLights(true)
                lightColor = Color.parseColor("#6366F1")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // Channel 5: AI Copilot & Assistant Updates
            val aiChannel = NotificationChannel(
                CHANNEL_AI_ASSISTANT,
                "AI Assistant & Summaries",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Smart AI responses, automated task generation, and workspace briefs"
                enableLights(true)
                lightColor = Color.parseColor("#A855F7")
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            manager.createNotificationChannel(taskChannel)
            manager.createNotificationChannel(briefingChannel)
            manager.createNotificationChannel(workspaceChannel)
            manager.createNotificationChannel(chatChannel)
            manager.createNotificationChannel(aiChannel)
        }
    }

    private fun getAppLargeIcon(context: Context) = try {
        BitmapFactory.decodeResource(context.resources, R.drawable.kalynt_flow_main_icon)
    } catch (e: Exception) {
        null
    }

    fun showTaskReminder(
        context: Context,
        taskId: String,
        title: String,
        workspaceName: String,
        priority: String,
        dueDateMs: Long
    ): Boolean {
        createNotificationChannels(context)

        val notificationId = taskId.hashCode()
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dueStr = if (dueDateMs > 0) "Due at ${timeFormat.format(Date(dueDateMs))}" else "Due soon"

        // 1. Content Intent (Opens task in App)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("kalynt://notification/task/$taskId")
            putExtra("navigate_to", "tasks")
            putExtra("task_id", taskId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Action: Complete Task
        val completeIntent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = TaskReminderReceiver.ACTION_COMPLETE_TASK_NOTIFICATION
            putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(TaskReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Action: Snooze 15 minutes
        val snoozeIntent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = TaskReminderReceiver.ACTION_SNOOZE_TASK
            putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, title)
            putExtra(TaskReminderReceiver.EXTRA_WORKSPACE_NAME, workspaceName)
            putExtra(TaskReminderReceiver.EXTRA_PRIORITY, priority)
            putExtra(TaskReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_TASK_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_kalynt_flow)
            .setLargeIcon(getAppLargeIcon(context))
            .setContentTitle(title)
            .setContentText("$dueStr • $workspaceName [${priority.uppercase()}]")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$dueStr\nWorkspace: $workspaceName\nPriority: ${priority.uppercase()}")
                    .setSummaryText("Kalynt Flow Reminder")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(Color.parseColor("#1D1D1B"))
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_widget_check_circle, "Mark Done", completePendingIntent)
            .addAction(R.drawable.ic_stat_kalynt_flow, "Snooze 15m", snoozePendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Log.d(TAG, "Successfully posted task reminder notification #$notificationId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post task reminder notification: ${e.message}", e)
            false
        }
    }

    fun showDailyDigest(
        context: Context,
        pendingCount: Int,
        topTaskTitles: List<String>
    ): Boolean {
        createNotificationChannels(context)

        val notificationId = 9999
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("kalynt://notification/daily_digest")
            putExtra("navigate_to", "tasks")
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("Today's Priorities ($pendingCount pending)")
            .setSummaryText("Daily Briefing")

        for (title in topTaskTitles.take(5)) {
            inboxStyle.addLine("• $title")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_BRIEFING)
            .setSmallIcon(R.drawable.ic_stat_kalynt_flow)
            .setLargeIcon(getAppLargeIcon(context))
            .setContentTitle("Kalynt Flow Daily Briefing")
            .setContentText("You have $pendingCount pending items to tackle today.")
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(Color.parseColor("#1D1D1B"))
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Log.d(TAG, "Successfully posted daily digest notification")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post daily digest notification: ${e.message}", e)
            false
        }
    }

    fun showTeamMessageNotification(
        context: Context,
        workspaceId: String,
        workspaceName: String,
        senderName: String,
        messageText: String,
        messageId: String
    ): Boolean {
        createNotificationChannels(context)

        val notificationId = (messageId.hashCode()).let { if (it == 0) 1001 else it }

        // 1. Content Intent (Opens workspace in App and automatically sets workspaceId)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("kalynt://notification/workspace/$workspaceId")
            putExtra("navigate_to", "team")
            putExtra("workspace_id", workspaceId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CHAT_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_kalynt_flow)
            .setLargeIcon(getAppLargeIcon(context))
            .setContentTitle("New message in #$workspaceName")
            .setContentText("$senderName: $messageText")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(messageText)
                    .setBigContentTitle("$senderName in #$workspaceName")
                    .setSummaryText("Team Discussion")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(Color.parseColor("#1D1D1B"))
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Log.d(TAG, "Successfully posted team message notification #$notificationId for $senderName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post team message notification: ${e.message}", e)
            false
        }
    }

    fun showAiChatMessageNotification(
        context: Context,
        sessionId: String,
        sessionTitle: String,
        aiResponseText: String,
        messageId: String = "ai_${System.currentTimeMillis()}"
    ): Boolean {
        createNotificationChannels(context)

        val notificationId = (messageId.hashCode()).let { if (it == 0) 2001 else it }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("kalynt://notification/chat/$sessionId")
            putExtra("navigate_to", "chat")
            putExtra("session_id", sessionId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cleanSnippet = aiResponseText.take(200) + if (aiResponseText.length > 200) "..." else ""

        val notification = NotificationCompat.Builder(context, CHANNEL_AI_ASSISTANT)
            .setSmallIcon(R.drawable.ic_stat_kalynt_flow)
            .setLargeIcon(getAppLargeIcon(context))
            .setContentTitle("AI Copilot: $sessionTitle")
            .setContentText(cleanSnippet)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(aiResponseText)
                    .setBigContentTitle("AI Copilot Response")
                    .setSummaryText("Kalynt Flow AI")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(Color.parseColor("#1D1D1B"))
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Log.d(TAG, "Successfully posted AI chat notification #$notificationId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post AI chat notification: ${e.message}", e)
            false
        }
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

