package com.example

import android.app.Application
import android.util.Log
import com.example.notifications.NotificationHelper
import com.example.notifications.NotificationScheduler
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class KalyntFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Ensure Firebase is initialized safely
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.e("KalyntFlowApp", "Firebase initialization failed: ${e.message}", e)
        }

        // Initialize Firebase App Check with error handling
        try {
            val appCheck = FirebaseAppCheck.getInstance()
            if (BuildConfig.DEBUG) {
                try {
                    val factoryClass = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                    val getInstanceMethod = factoryClass.getMethod("getInstance")
                    val factory = getInstanceMethod.invoke(null) as? AppCheckProviderFactory
                    if (factory != null) {
                        appCheck.installAppCheckProviderFactory(factory)
                    }
                } catch (e: Exception) {
                    Log.w("KalyntFlowApp", "Debug App Check provider not available: ${e.message}")
                }
            } else {
                appCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            }
        } catch (e: Exception) {
            Log.e("KalyntFlowApp", "App Check initialization failed: ${e.message}", e)
        }

        // Proactive Security & Cryptographic Integrity Verification & Policy Enforcement
        com.example.security.SecurityHardening.checkSecurityStatus(this)
        com.example.security.SecurityHardening.enforceIntegrityPolicy(this)

        // Initialize Notification Channels, Daily Priority Briefing, and Background Sync
        NotificationHelper.createNotificationChannels(this)
        NotificationScheduler.scheduleDailyBriefing(this, 8, 30)
        com.example.notifications.BackgroundSyncManager.start(this)
    }
}
