package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFF5F5F5),
    onPrimary = Color(0xFF0D0D0D),
    primaryContainer = Color(0xFF2A2A2A),
    onPrimaryContainer = Color(0xFFF5F5F5),
    secondary = Color(0xFF8A8A8A),
    onSecondary = Color(0xFF0D0D0D),
    secondaryContainer = Color(0xFF131313),
    onSecondaryContainer = Color(0xFFF5F5F5),
    background = Color(0xFF0D0D0D),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF131313),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF131313),
    onSurfaceVariant = Color(0xFF8A8A8A),
    outline = Color(0xFF2A2A2A),
    outlineVariant = Color(0xFF2A2A2A),
    tertiary = Color(0xFF5C5C5C),
    onTertiary = Color(0xFFF5F5F5)
  )

val SuccessAccent = Color(0xFF5DCAA5)

private val LightColorScheme =
  lightColorScheme(
    primary = KalyntPrimary,
    onPrimary = KalyntBackground,
    primaryContainer = Color(0xFFE5E5E1),
    onPrimaryContainer = TextPrimary,
    secondary = KalyntSecondary,
    onSecondary = KalyntBackground,
    secondaryContainer = SurfaceVariant,
    onSecondaryContainer = TextPrimary,
    tertiary = KalyntTertiary,
    onTertiary = KalyntBackground,
    background = KalyntBackground,
    onBackground = KalyntOnBackground,
    surface = KalyntSurface,
    onSurface = KalyntOnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = BorderColor,
    outlineVariant = BorderColor
  )

@Composable
fun KalyntFlowTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Disable dynamic to force our design language
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
