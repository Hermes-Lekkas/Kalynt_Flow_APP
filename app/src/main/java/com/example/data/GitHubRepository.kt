package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.data.local.NoteEntity
import com.example.data.local.TaskEntity
import com.example.data.local.WorkspaceMemberEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.KeyStore
import java.util.Date

data class GitHubConnectionState(
    val isConnected: Boolean = false,
    val username: String? = null,
    val error: String? = null
)

data class LinkedRepo(
    val id: String = "",
    val name: String = "",
    val fullName: String = "",
    val workspaceId: String = "",
    val workspaceName: String = "",
    val syncStatus: String = "not_synced", // "synced", "syncing", "error"
    val lastSyncedAt: Long = 0L,
    val openIssuesCount: Long = 0L,
    val openPrCount: Long = 0L,
    val error: String? = null
)

class GitHubRepository(private val context: Context) {
    private val TAG = "GitHubRepository"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val sharedPrefs: SharedPreferences = createEncryptedSharedPreferencesWithFallback(context)
    
    private val _connectionState = MutableStateFlow(GitHubConnectionState())
    val connectionState: StateFlow<GitHubConnectionState> = _connectionState.asStateFlow()

    init {
        checkConnectionStatus()
    }

    private fun createEncryptedSharedPreferencesWithFallback(ctx: Context): SharedPreferences {
        val prefFilename = "github_prefs"
        try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                ctx,
                prefFilename,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences on first attempt. Clearing corrupted state.", e)
            
            // 1. Delete MasterKey entry from Android Keystore if corrupted
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                    keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                }
            } catch (ksEx: Exception) {
                Log.e(TAG, "Failed to delete corrupted Keystore entry", ksEx)
            }

            // 2. Delete the corrupted preferences file
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    ctx.deleteSharedPreferences(prefFilename)
                } else {
                    ctx.getSharedPreferences(prefFilename, Context.MODE_PRIVATE).edit().clear().commit()
                }
            } catch (prefEx: Exception) {
                Log.e(TAG, "Failed to delete corrupted shared preferences file", prefEx)
            }

            // 3. Retry creating EncryptedSharedPreferences
            try {
                val newMasterKey = MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                return EncryptedSharedPreferences.create(
                    ctx,
                    prefFilename,
                    newMasterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (retryEx: Exception) {
                Log.e(TAG, "EncryptedSharedPreferences retry failed. Falling back to private SharedPreferences.", retryEx)
                // 4. Safe fallback to standard private preferences so the app never crashes
                return ctx.getSharedPreferences("github_prefs_safe", Context.MODE_PRIVATE)
            }
        }
    }

    fun saveToken(token: String) {
        try {
            sharedPrefs.edit().putString("github_token", token).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save token", e)
        }
    }

    fun getToken(): String? {
        return try {
            sharedPrefs.getString("github_token", null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get token", e)
            null
        }
    }
    
    fun clearToken() {
        try {
            sharedPrefs.edit().remove("github_token").apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear token", e)
        }
    }

    private fun checkConnectionStatus() {
        val uid = auth.currentUser?.uid ?: return
        val token = getToken()
        if (token == null) {
            _connectionState.value = GitHubConnectionState(isConnected = false)
            return
        }
        
        try {
            firestore.collection("users").document(uid).collection("githubConnection")
                .document("status")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        val status = snapshot.getString("status")
                        val username = snapshot.getString("githubUsername")
                        _connectionState.value = GitHubConnectionState(
                            isConnected = status == "connected",
                            username = username,
                            error = if (status == "error") "Sync error" else null
                        )
                    } else {
                        _connectionState.value = GitHubConnectionState(isConnected = false)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to listen for connection status", e)
            _connectionState.value = GitHubConnectionState(isConnected = false)
        }
    }
    
    suspend fun connect(username: String, scopes: List<String>) {
        val uid = auth.currentUser?.uid ?: return
        val connectionData = mapOf(
            "connectedAt" to Date(),
            "githubUsername" to username,
            "scopes" to scopes,
            "status" to "connected"
        )
        firestore.collection("users").document(uid).collection("githubConnection")
            .document("status")
            .set(connectionData).await()
        _connectionState.value = GitHubConnectionState(isConnected = true, username = username)
    }

    suspend fun disconnect() {
        val uid = auth.currentUser?.uid ?: return
        clearToken()
        
        try {
            firestore.collection("users").document(uid).collection("githubConnection")
                .document("status")
                .update("status", "revoked").await()
        } catch (e: Exception) {
            // connection might already be deleted or not present
        }
        _connectionState.value = GitHubConnectionState(isConnected = false)
    }

    fun getLinkedRepos(): Flow<List<LinkedRepo>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users").document(uid).collection("linkedRepos")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val repos = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(LinkedRepo::class.java)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed standard deserialization for repo ${doc.id}, using fallback", e)
                            try {
                                val issuesNum = doc.get("openIssuesCount")
                                val prsNum = doc.get("openPrCount")
                                val issuesCount = when (issuesNum) {
                                    is Number -> issuesNum.toLong()
                                    is String -> issuesNum.toLongOrNull() ?: 0L
                                    else -> 0L
                                }
                                val prsCount = when (prsNum) {
                                    is Number -> prsNum.toLong()
                                    is String -> prsNum.toLongOrNull() ?: 0L
                                    else -> 0L
                                }
                                val syncedTime = when (val t = doc.get("lastSyncedAt")) {
                                    is Number -> t.toLong()
                                    is String -> t.toLongOrNull() ?: 0L
                                    else -> 0L
                                }
                                LinkedRepo(
                                    id = doc.getString("id") ?: doc.id,
                                    name = doc.getString("name") ?: "",
                                    fullName = doc.getString("fullName") ?: "",
                                    workspaceId = doc.getString("workspaceId") ?: "",
                                    workspaceName = doc.getString("workspaceName") ?: "",
                                    syncStatus = doc.getString("syncStatus") ?: "not_synced",
                                    lastSyncedAt = syncedTime,
                                    openIssuesCount = issuesCount,
                                    openPrCount = prsCount,
                                    error = doc.getString("error")
                                )
                            } catch (inner: Exception) {
                                Log.e(TAG, "Fallback parsing failed for doc ${doc.id}", inner)
                                null
                            }
                        }
                    }
                    trySend(repos)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun linkRepository(repo: GitHubRepo, workspaceId: String, workspaceName: String) {
        val uid = auth.currentUser?.uid ?: return
        val linkedRepo = LinkedRepo(
            id = repo.id.toString(),
            name = repo.name,
            fullName = repo.full_name,
            workspaceId = workspaceId,
            workspaceName = workspaceName,
            syncStatus = "not_synced",
            lastSyncedAt = 0L,
            openIssuesCount = 0L,
            openPrCount = 0L
        )
        firestore.collection("users").document(uid).collection("linkedRepos").document(repo.id.toString())
            .set(linkedRepo).await()
    }

    suspend fun unlinkRepository(repoId: String) {
        val uid = auth.currentUser?.uid ?: return
        
        // 1. Delete from users/{uid}/linkedRepos/{repoId}
        firestore.collection("users").document(uid).collection("linkedRepos").document(repoId).delete().await()
        
        // 2. Clean up associated tasks and notes
        try {
            val tasksSnap = firestore.collection("tasks").get().await()
            val batch = firestore.batch()
            var deletedCount = 0
            for (doc in tasksSnap.documents) {
                if (doc.id.startsWith("github_issue_${repoId}_")) {
                    batch.delete(doc.reference)
                    deletedCount++
                }
            }
            
            val notesSnap = firestore.collection("notes").get().await()
            for (doc in notesSnap.documents) {
                if (doc.id.startsWith("github_pr_${repoId}_")) {
                    batch.delete(doc.reference)
                    deletedCount++
                }
            }
            
            if (deletedCount > 0) {
                batch.commit().await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncRepository(repoId: String, fullName: String, workspaceId: String) {
        val uid = auth.currentUser?.uid ?: return
        val userEmail = if (auth.currentUser != null) {
            val email = auth.currentUser?.email
            if (!email.isNullOrBlank()) email else "guest_${auth.currentUser?.uid?.take(8)}@kalyntflow.app"
        } else "guest@kalyntflow.app"
        
        val repoDocRef = firestore.collection("users").document(uid).collection("linkedRepos").document(repoId)
        
        // Update status to syncing
        repoDocRef.update("syncStatus", "syncing").await()
        
        try {
            val token = getToken() ?: throw Exception("GitHub token is missing. Please reconnect.")
            val authHeader = "Bearer $token"
            
            val parts = fullName.split("/")
            if (parts.size != 2) throw Exception("Invalid repository name format")
            val owner = parts[0]
            val repoName = parts[1]
            
            // Fetch issues/PRs from GitHub API
            val response = GitHubClient.api.getRepoIssues(authHeader, owner, repoName, state = "open")
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string() ?: "Unknown API error"
                throw Exception("Failed to fetch issues: $errorBody")
            }
            
            val gitHubIssues = response.body() ?: emptyList()
            
            val issuesList = gitHubIssues.filter { it.pull_request == null }
            val prsList = gitHubIssues.filter { it.pull_request != null }
            
            // Fetch workspace members to map assignees
            val members = mutableListOf<WorkspaceMemberEntity>()
            try {
                val membersSnap = firestore.collection("workspace_members")
                    .whereEqualTo("workspaceId", workspaceId)
                    .get().await()
                for (doc in membersSnap.documents) {
                    val m = doc.toObject(WorkspaceMemberEntity::class.java)
                    if (m != null) members.add(m)
                }
            } catch (e: Exception) {
                // ignore
            }
            
            val batch = firestore.batch()
            
            // Map issues to Tasks
            for (issue in issuesList) {
                val taskId = "github_issue_${repoId}_${issue.number}"
                
                var assignedName = ""
                var assignedEmail = ""
                val githubAssignee = issue.assignees.firstOrNull()?.login
                if (githubAssignee != null) {
                    val matchedMember = members.find { 
                        it.name.equals(githubAssignee, ignoreCase = true) || 
                        it.email.substringBefore("@").equals(githubAssignee, ignoreCase = true)
                    }
                    if (matchedMember != null) {
                        assignedName = matchedMember.name
                        assignedEmail = matchedMember.email
                    } else {
                        assignedName = githubAssignee
                    }
                }
                
                val labelsStr = if (issue.labels.isNotEmpty()) {
                    "Labels: " + issue.labels.joinToString(", ") { it.name } + "\n\n"
                } else ""
                
                val createdDateMs = try {
                    if (issue.created_at != null) {
                        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        format.parse(issue.created_at)?.time ?: System.currentTimeMillis()
                    } else {
                        System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
                
                val task = TaskEntity(
                    id = taskId,
                    title = issue.title,
                    description = labelsStr + (issue.body ?: "No description provided.") + "\n\n[GitHub Issue #${issue.number}](${issue.html_url})",
                    isCompleted = issue.state == "closed",
                    workspaceId = workspaceId,
                    assignedToName = assignedName,
                    assignedToEmail = assignedEmail,
                    dueDateMs = createdDateMs,
                    memberEmails = listOf(userEmail),
                    timestamp = System.currentTimeMillis()
                )
                
                val docRef = firestore.collection("tasks").document(taskId)
                batch.set(docRef, task)
            }
            
            // Map PRs to Notes
            for (pr in prsList) {
                val noteId = "github_pr_${repoId}_${pr.number}"
                
                val createdDateMs = try {
                    if (pr.created_at != null) {
                        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        format.parse(pr.created_at)?.time ?: System.currentTimeMillis()
                    } else {
                        System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
                
                val note = NoteEntity(
                    id = noteId,
                    title = pr.title,
                    content = (pr.body ?: "No description provided.") + "\n\n[GitHub Pull Request #${pr.number}](${pr.html_url})",
                    workspaceId = workspaceId,
                    memberEmails = listOf(userEmail),
                    timestamp = System.currentTimeMillis(),
                    dueDateMs = createdDateMs
                )
                
                val docRef = firestore.collection("notes").document(noteId)
                batch.set(docRef, note)
            }
            
            batch.commit().await()
            
            // Update repository entry with success
            repoDocRef.set(
                mapOf(
                    "syncStatus" to "synced",
                    "lastSyncedAt" to System.currentTimeMillis(),
                    "openIssuesCount" to issuesList.size.toLong(),
                    "openPrCount" to prsList.size.toLong(),
                    "error" to null
                ),
                SetOptions.merge()
            ).await()
            
        } catch (e: Exception) {
            e.printStackTrace()
            repoDocRef.set(
                mapOf(
                    "syncStatus" to "error",
                    "error" to (e.message ?: "Failed to sync repository")
                ),
                SetOptions.merge()
            ).await()
        }
    }
}
