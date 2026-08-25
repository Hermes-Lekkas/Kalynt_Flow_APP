package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import com.example.data.GitHubClient
import com.example.data.GitHubRepo
import com.example.data.GitHubRepository
import com.example.data.LinkedRepo
import com.example.data.local.WorkspaceEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GitHubViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GitHubRepository(application)
    private val appRepository = AppRepository(application)
    
    val connectionState = repository.connectionState.stateIn(
        viewModelScope, 
        SharingStarted.WhileSubscribed(5000), 
        com.example.data.GitHubConnectionState()
    )
    
    val linkedRepos: StateFlow<List<LinkedRepo>> = repository.getLinkedRepos().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    
    val workspaces: StateFlow<List<WorkspaceEntity>> = appRepository.allWorkspaces.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )
    
    private val _availableRepos = MutableStateFlow<List<GitHubRepo>>(emptyList())
    val availableRepos: StateFlow<List<GitHubRepo>> = _availableRepos.asStateFlow()
    
    private val _isFetchingRepos = MutableStateFlow(false)
    val isFetchingRepos: StateFlow<Boolean> = _isFetchingRepos.asStateFlow()
    
    private val _fetchingError = MutableStateFlow<String?>(null)
    val fetchingError: StateFlow<String?> = _fetchingError.asStateFlow()

    private val _isSyncingAll = MutableStateFlow(false)
    val isSyncingAll: StateFlow<Boolean> = _isSyncingAll.asStateFlow()

    init {
        // Automatically trigger sync for all linked repos on cold-start/screen initialization
        viewModelScope.launch {
            try {
                connectionState.collect { state ->
                    if (state.isConnected) {
                        syncAll()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun signInWithGitHub(activity: Activity) {
        val provider = OAuthProvider.newBuilder("github.com")
        provider.addCustomParameter("scope", "repo")
        
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        
        if (currentUser != null) {
            val pendingResultTask = auth.pendingAuthResult
            if (pendingResultTask != null) {
                pendingResultTask.addOnSuccessListener { authResult ->
                    val credential = authResult.credential as? com.google.firebase.auth.OAuthCredential
                    val token = credential?.accessToken
                    val username = authResult.additionalUserInfo?.username ?: "GitHub User"
                    if (token != null) {
                        repository.saveToken(token)
                        viewModelScope.launch {
                            repository.connect(username, listOf("repo"))
                        }
                    }
                }.addOnFailureListener { err ->
                    _fetchingError.value = "Link failed: ${err.localizedMessage}"
                }
            } else {
                currentUser.startActivityForLinkWithProvider(activity, provider.build())
                    .addOnSuccessListener { authResult ->
                        val credential = authResult.credential as? com.google.firebase.auth.OAuthCredential
                        val token = credential?.accessToken
                        val username = authResult.additionalUserInfo?.username ?: "GitHub User"
                        if (token != null) {
                            repository.saveToken(token)
                            viewModelScope.launch {
                                repository.connect(username, listOf("repo"))
                            }
                        }
                    }
                    .addOnFailureListener { err ->
                        _fetchingError.value = "Link failed: ${err.localizedMessage}"
                    }
            }
        } else {
            val pendingResultTask = auth.pendingAuthResult
            if (pendingResultTask != null) {
                pendingResultTask.addOnSuccessListener { authResult ->
                    val credential = authResult.credential as? com.google.firebase.auth.OAuthCredential
                    val token = credential?.accessToken
                    val username = authResult.additionalUserInfo?.username ?: "GitHub User"
                    if (token != null) {
                        repository.saveToken(token)
                        viewModelScope.launch {
                            repository.connect(username, listOf("repo"))
                        }
                    }
                }.addOnFailureListener { err ->
                    _fetchingError.value = "Sign-in failed: ${err.localizedMessage}"
                }
            } else {
                auth.startActivityForSignInWithProvider(activity, provider.build())
                    .addOnSuccessListener { authResult ->
                        val credential = authResult.credential as? com.google.firebase.auth.OAuthCredential
                        val token = credential?.accessToken
                        val username = authResult.additionalUserInfo?.username ?: "GitHub User"
                        if (token != null) {
                            repository.saveToken(token)
                            viewModelScope.launch {
                                repository.connect(username, listOf("repo"))
                            }
                        }
                    }
                    .addOnFailureListener { err ->
                        _fetchingError.value = "Sign-in failed: ${err.localizedMessage}"
                    }
            }
        }
    }
    
    fun disconnect() {
        viewModelScope.launch {
            repository.disconnect()
            _availableRepos.value = emptyList()
        }
    }
    
    fun fetchUserRepos() {
        viewModelScope.launch {
            _isFetchingRepos.value = true
            _fetchingError.value = null
            try {
                val token = repository.getToken() ?: throw Exception("GitHub token is missing")
                val allRepos = mutableListOf<GitHubRepo>()
                var page = 1
                var hasMore = true
                while (hasMore && page <= 10) {
                    val response = GitHubClient.api.getUserRepos("Bearer $token", perPage = 100, page = page)
                    if (response.isSuccessful) {
                        val repos = response.body() ?: emptyList()
                        allRepos.addAll(repos)
                        if (repos.size < 100) {
                            hasMore = false
                        } else {
                            page++
                        }
                    } else {
                        if (page == 1) {
                            val errText = response.errorBody()?.string() ?: "Failed to fetch repositories"
                            _fetchingError.value = errText
                        }
                        hasMore = false
                    }
                }
                _availableRepos.value = allRepos
            } catch (e: Exception) {
                e.printStackTrace()
                _fetchingError.value = e.message ?: "Network error"
            } finally {
                _isFetchingRepos.value = false
            }
        }
    }
    
    fun linkRepo(repo: GitHubRepo, workspaceId: String, workspaceName: String) {
        viewModelScope.launch {
            repository.linkRepository(repo, workspaceId, workspaceName)
            // Immediately sync newly linked repository
            repository.syncRepository(repo.id.toString(), repo.full_name, workspaceId)
        }
    }
    
    fun unlinkRepo(repoId: String) {
        viewModelScope.launch {
            repository.unlinkRepository(repoId)
        }
    }
    
    fun syncRepo(repoId: String, fullName: String, workspaceId: String) {
        viewModelScope.launch {
            repository.syncRepository(repoId, fullName, workspaceId)
        }
    }
    
    fun syncAll() {
        viewModelScope.launch {
            if (_isSyncingAll.value) return@launch
            _isSyncingAll.value = true
            val repos = linkedRepos.value
            for (repo in repos) {
                try {
                    repository.syncRepository(repo.id, repo.fullName, repo.workspaceId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _isSyncingAll.value = false
        }
    }

    fun clearError() {
        _fetchingError.value = null
    }
}
