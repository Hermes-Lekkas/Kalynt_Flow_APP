package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.auth.AuthState
import com.example.auth.AuthViewModel
import com.example.notifications.NotificationHelper
import com.example.notifications.PermissionHelper
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.MainScreen
import com.example.ui.theme.KalyntFlowTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
  private lateinit var authViewModel: AuthViewModel
  private val currentIntentFlow = MutableStateFlow<Intent?>(null)

  private val notificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      NotificationHelper.createNotificationChannels(this)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    currentIntentFlow.value = intent
    
    try {
      FirebaseApp.initializeApp(this)
      FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        DebugAppCheckProviderFactory.getInstance()
      )
    } catch (e: Exception) {
      // Handle missing or invalid firebase app gracefully
    }
    
    // Proactively initialize notification channels on start
    NotificationHelper.createNotificationChannels(this)

    // Request notification permission on Android 13+ if not granted yet
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (!PermissionHelper.hasNotificationPermission(this)) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
    
    authViewModel = AuthViewModel(this)

    setContent {
      KalyntFlowTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          val authState by authViewModel.authState.collectAsState()
          val currentIntent by currentIntentFlow.collectAsState()
          val navigateTo = currentIntent?.getStringExtra("navigate_to")
          val openAddDialog = currentIntent?.getBooleanExtra("open_add_dialog", false) ?: false
          val taskId = currentIntent?.getStringExtra("task_id")
          val workspaceId = currentIntent?.getStringExtra("workspace_id")
          
          when (authState) {
            is AuthState.Authenticated -> {
              MainScreen(
                authViewModel = authViewModel,
                initialRoute = navigateTo,
                openAddDialog = openAddDialog,
                taskId = taskId,
                workspaceId = workspaceId
              )
            }

            else -> {
              AuthScreen(authViewModel)
            }
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    currentIntentFlow.value = intent
  }

  override fun onResume() {
    super.onResume()
    // Re-query active subscriptions every time the user returns to the app
    // (e.g. after visiting the Play Store subscription page)
    com.example.data.BillingManager.getInstance(this).refreshPurchases()
    // Refresh widgets
    com.example.widget.KalyntFlowQuickWidgetProvider.updateAllWidgets(this)
    com.example.widget.KalyntFlowTasksWidgetProvider.updateAllWidgets(this)
  }
}


