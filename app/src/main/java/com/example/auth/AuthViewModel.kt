package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val uid: String, val email: String?, val displayName: String?) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(context: Context? = null) : ViewModel() {
    private var auth: FirebaseAuth? = null
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        try {
            auth = FirebaseAuth.getInstance()
            val user = auth?.currentUser
            if (user != null) {
                _authState.value = AuthState.Authenticated(user.uid, user.email, user.displayName)
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        } catch (e: Exception) {
            Log.w("AuthViewModel", "Firebase auth initialization bypassed: ${e.message}")
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun continueAsGuest() {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val firebaseAuth = auth
                if (firebaseAuth != null) {
                    try {
                        val result = firebaseAuth.signInAnonymously().await()
                        val user = result.user
                        if (user != null) {
                            val virtualEmail = "guest_${user.uid.take(8)}@kalyntflow.app"
                            _authState.value = AuthState.Authenticated(user.uid, virtualEmail, "Guest ${user.uid.take(4).uppercase()}")
                            return@launch
                        }
                    } catch (e: Exception) {
                        Log.i("AuthViewModel", "Anonymous sign in unavailable, initializing local guest session: ${e.message}")
                    }
                }
                // Fallback if auth is null, user is null, or anonymous auth is disabled
                _authState.value = AuthState.Authenticated("guest_uid", "guest@kalyntflow.app", "Guest User")
            } catch (e: Exception) {
                Log.i("AuthViewModel", "Local guest fallback initialized")
                _authState.value = AuthState.Authenticated("guest_uid", "guest@kalyntflow.app", "Guest User")
            }
        }
    }

    suspend fun signInWithGoogle(context: Context, webClientId: String) {
        val fallbackClientId = "1009661461742-tjuv4hbhfo41ficvdur5h2ho8bteu5ai.apps.googleusercontent.com"
        val clientId = webClientId.ifBlank {
            try {
                val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                if (resId != 0) context.getString(resId) else fallbackClientId
            } catch (e: Exception) { fallbackClientId }
        }.ifBlank { fallbackClientId }

        val firebaseAuth = auth
        if (firebaseAuth == null) {
            _authState.value = AuthState.Error("Firebase is not initialized. Make sure google-services.json is configured.")
            return
        }
        _authState.value = AuthState.Loading

        try {
            // Find activity to ensure window token is valid for the Google Account Picker UI
            val activity = findActivity(context) ?: (context as? android.app.Activity)
            val uiContext = activity ?: context
            val credentialManager = CredentialManager.create(uiContext)

            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId)
                .build()

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context = uiContext, request = request)
            handleSignInResult(result)
        } catch (e: GetCredentialCancellationException) {
            Log.i("AuthViewModel", "Google Sign-In prompt canceled by user")
            _authState.value = AuthState.Unauthenticated
        } catch (e: NoCredentialException) {
            Log.w("AuthViewModel", "No Google account available on device or emulator", e)
            _authState.value = AuthState.Error("No Google Account detected. Please ensure a Google Account is added under device Settings, or tap 'Explore as Guest' below.")
        } catch (e: GetCredentialException) {
            Log.e("AuthViewModel", "CredentialManager exception: ${e.type} - ${e.message}", e)
            val msg = e.message ?: "Google Sign-In is unavailable on this device."
            if (msg.contains("canceled", ignoreCase = true) || msg.contains("cancelled", ignoreCase = true)) {
                _authState.value = AuthState.Unauthenticated
            } else {
                _authState.value = AuthState.Error("Google Sign-In notice: $msg. You can tap 'Explore as Guest' to proceed instantly.")
            }
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Sign in failed with exception", e)
            val msg = e.localizedMessage ?: e.message ?: "Google Sign-In failed"
            if (msg.contains("canceled", ignoreCase = true) || msg.contains("cancelled", ignoreCase = true)) {
                _authState.value = AuthState.Unauthenticated
            } else {
                _authState.value = AuthState.Error("Google Sign-In notice: $msg. You can tap 'Explore as Guest' to proceed instantly.")
            }
        }
    }

    private fun findActivity(context: Context): android.app.Activity? {
        var current = context
        while (current is android.content.ContextWrapper) {
            if (current is android.app.Activity) return current
            current = current.baseContext
        }
        return null
    }

    private suspend fun handleSignInResult(result: GetCredentialResponse) {
        val firebaseAuth = auth ?: return
        val credential = result.credential
        if (credential is androidx.credentials.CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
                val user = authResult.user
                if (user != null) {
                    _authState.value = AuthState.Authenticated(user.uid, user.email, user.displayName)
                } else {
                    _authState.value = AuthState.Unauthenticated
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Received an invalid google id token response", e)
                _authState.value = AuthState.Error(e.localizedMessage ?: "Google Sign-In failed")
            }
        } else {
            _authState.value = AuthState.Error("Unexpected credential type: ${credential.type}")
        }
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            // ignore
        }
        _authState.value = AuthState.Unauthenticated
    }
}

