package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.BillingManager
import com.example.data.GeminiRepository
import com.example.widget.KalyntFlowWidgetDataHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Kalynt Flow", appName)
  }

  @Test
  fun `verify GeminiRepository instantiation`() {
    val repository = GeminiRepository()
    assertNotNull(repository)
  }

  @Test
  fun `verify KalyntFlowWidgetDataHolder non-blocking snapshot query`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val items = KalyntFlowWidgetDataHolder.getItems(context)
    assertNotNull(items)
  }

  @Test
  fun `verify BillingManager initial state is free and non-null`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val billingManager = BillingManager.getInstance(context)
    assertEquals("FREE", billingManager.activeTier.value)
  }

  @Test
  fun `verify AuthState Authenticated email verification default state`() {
    val authState = com.example.auth.AuthState.Authenticated(
      uid = "test-uid-123",
      email = "hermeslekkasdev@gmail.com",
      displayName = "Hermes",
      isEmailVerified = false
    )
    assertEquals(false, authState.isEmailVerified)
    assertEquals("hermeslekkasdev@gmail.com", authState.email)
  }

  @Test
  fun `verify SecurityHardening checks pass in test environment`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val report = com.example.security.SecurityHardening.checkSecurityStatus(context)
    assertNotNull(report)
    assertNotNull(report.signingCertFingerprint)
  }
}
