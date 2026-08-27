package com.example.notifications

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object BackgroundSyncManager {

    private const val TAG = "BackgroundSyncManager"
    private const val PREFS_NAME = "kalynt_notifications_prefs"
    private const val KEY_NOTIFIED_IDS = "notified_comment_ids"
    private const val KEY_LAST_SYNC_TIME = "last_sync_timestamp"

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeWorkspaceListeners = mutableMapOf<String, ListenerRegistration>()
    private var workspacesListener: ListenerRegistration? = null
    private var isStarted = false

    fun start(context: Context) {
        if (isStarted) return
        isStarted = true
        Log.d(TAG, "Starting BackgroundSyncManager...")

        // Ensure notification channels exist
        NotificationHelper.createNotificationChannels(context)

        // Reschedule any pending tasks in Room
        applicationScope.launch {
            try {
                NotificationScheduler.rescheduleAllActiveTasks(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling tasks on start", e)
            }
        }

        // Schedule periodic background sync alarm
        NotificationScheduler.schedulePeriodicBackgroundSync(context)

        // Listen for Firebase Auth state changes
        try {
            FirebaseAuth.getInstance().addAuthStateListener { auth ->
                val user = auth.currentUser
                val isGuest = user == null || user.isAnonymous || user.email.isNullOrBlank() || user.email?.contains("guest") == true || user.email?.contains("kalyntflow.app") == true
                if (user != null && !isGuest && !user.email.isNullOrBlank()) {
                    Log.d(TAG, "User logged in (${user.email}). Attaching background Firestore listeners...")
                    attachFirestoreListeners(context.applicationContext, user.email ?: "")
                } else {
                    Log.d(TAG, "Guest or signed out. Detaching background Firestore listeners...")
                    detachFirestoreListeners()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up FirebaseAuth state listener", e)
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getNotifiedIds(context: Context): MutableSet<String> {
        val prefs = getPrefs(context)
        val set = prefs.getStringSet(KEY_NOTIFIED_IDS, null) ?: emptySet()
        return set.toMutableSet()
    }

    fun isAlreadyNotified(context: Context, commentId: String): Boolean {
        return getNotifiedIds(context).contains(commentId)
    }

    fun markNotified(context: Context, commentId: String) {
        val set = getNotifiedIds(context)
        set.add(commentId)
        // Keep set size bounded to last 500 IDs
        val trimmed = if (set.size > 500) set.toList().takeLast(400).toSet() else set
        getPrefs(context).edit().putStringSet(KEY_NOTIFIED_IDS, trimmed).apply()
    }

    private fun attachFirestoreListeners(context: Context, myEmail: String) {
        detachFirestoreListeners()
        if (myEmail.isBlank()) return

        try {
            val db = FirebaseFirestore.getInstance()

            // Observe only the user's workspaces to dynamically attach comment listeners
            workspacesListener = db.collection("workspaces")
                .whereArrayContains("memberEmails", myEmail)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        Log.e(TAG, "Error observing workspaces: ${error?.message}")
                        return@addSnapshotListener
                    }

                    val currentWsIds = snapshot.documents.map { it.id }.toSet()

                    // Cleanup removed workspaces
                    val removed = activeWorkspaceListeners.keys.filter { it !in currentWsIds }
                    for (id in removed) {
                        activeWorkspaceListeners[id]?.remove()
                        activeWorkspaceListeners.remove(id)
                    }

                    // Attach listener to each workspace comments collection
                    for (doc in snapshot.documents) {
                        val workspaceId = doc.id
                        val workspaceName = doc.getString("name") ?: "Workspace"

                        if (!activeWorkspaceListeners.containsKey(workspaceId)) {
                            val listener = db.collection("workspaces")
                                .document(workspaceId)
                                .collection("comments")
                                .orderBy("timestamp")
                                .addSnapshotListener { commentSnap, commentErr ->
                                    if (commentErr != null || commentSnap == null) return@addSnapshotListener

                                    val now = System.currentTimeMillis()
                                    // Process new comments
                                    for (change in commentSnap.documentChanges) {
                                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                            val cDoc = change.document
                                            val commentId = cDoc.getString("id") ?: cDoc.id
                                            val authorEmail = cDoc.getString("authorEmail") ?: ""
                                            val authorName = cDoc.getString("authorName") ?: "Team Member"
                                            val content = cDoc.getString("content") ?: ""
                                            val timestamp = cDoc.getLong("timestamp") ?: now

                                            // Ignore own comments
                                            if (authorEmail.isNotBlank() && authorEmail.equals(myEmail, ignoreCase = true)) {
                                                continue
                                            }

                                            // Ignore comments older than 24 hours
                                            if (now - timestamp > 86400000L) {
                                                continue
                                            }

                                            if (!isAlreadyNotified(context, commentId)) {
                                                markNotified(context, commentId)
                                                Log.d(TAG, "Posting background notification for new comment $commentId from $authorName")
                                                NotificationHelper.showTeamMessageNotification(
                                                    context = context,
                                                    workspaceId = workspaceId,
                                                    workspaceName = workspaceName,
                                                    senderName = authorName,
                                                    messageText = content,
                                                    messageId = commentId
                                                )
                                            }
                                        }
                                    }
                                }
                            activeWorkspaceListeners[workspaceId] = listener
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach background Firestore listeners", e)
        }
    }

    private fun detachFirestoreListeners() {
        try {
            workspacesListener?.remove()
            workspacesListener = null
            for (listener in activeWorkspaceListeners.values) {
                listener.remove()
            }
            activeWorkspaceListeners.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error detaching Firestore listeners", e)
        }
    }

    suspend fun syncOnce(context: Context) {
        Log.d(TAG, "Executing background sync pass...")
        val currentUser = try { FirebaseAuth.getInstance().currentUser } catch (e: Exception) { null }
        val myEmail = currentUser?.email ?: ""

        // 1. Reschedule all active local tasks
        try {
            NotificationScheduler.rescheduleAllActiveTasks(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error rescheduling tasks during sync", e)
        }

        // 2. If user is logged in, query Firestore for recent comments across user workspaces
        if (myEmail.isNotBlank()) {
            try {
                val db = FirebaseFirestore.getInstance()
                val wsSnap = db.collection("workspaces")
                    .whereArrayContains("memberEmails", myEmail)
                    .get()
                    .await()
                val now = System.currentTimeMillis()
                val cutoff = now - (6 * 3600 * 1000L) // last 6 hours

                for (doc in wsSnap.documents) {
                    val workspaceId = doc.id
                    val workspaceName = doc.getString("name") ?: "Workspace"

                    val commentsSnap = db.collection("workspaces")
                        .document(workspaceId)
                        .collection("comments")
                        .whereGreaterThan("timestamp", cutoff)
                        .get()
                        .await()

                    for (cDoc in commentsSnap.documents) {
                        val commentId = cDoc.getString("id") ?: cDoc.id
                        val authorEmail = cDoc.getString("authorEmail") ?: ""
                        val authorName = cDoc.getString("authorName") ?: "Team Member"
                        val content = cDoc.getString("content") ?: ""

                        if (authorEmail.isNotBlank() && authorEmail.equals(myEmail, ignoreCase = true)) {
                            continue
                        }

                        if (!isAlreadyNotified(context, commentId)) {
                            markNotified(context, commentId)
                            NotificationHelper.showTeamMessageNotification(
                                context = context,
                                workspaceId = workspaceId,
                                workspaceName = workspaceName,
                                senderName = authorName,
                                messageText = content,
                                messageId = commentId
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during Firestore comment background sync", e)
            }
        }
    }
}
