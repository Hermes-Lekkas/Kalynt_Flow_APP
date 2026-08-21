package com.example

import android.app.Application
import com.example.notifications.NotificationHelper
import com.example.notifications.NotificationScheduler
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class KalyntFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Ensure Firebase is initialized
        FirebaseApp.initializeApp(this)

        // Initialize Firebase App Check
        val appCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }

        // Initialize Notification Channels, Daily Priority Briefing, and Background Sync
        NotificationHelper.createNotificationChannels(this)
        NotificationScheduler.scheduleDailyBriefing(this, 8, 30)
        com.example.notifications.BackgroundSyncManager.start(this)
    }
}
