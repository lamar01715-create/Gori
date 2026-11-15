package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppleCalculatorDarkScheme =
  darkColorScheme(
    primary = Color(0xFFFF9F0A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A280A),
    onPrimaryContainer = Color(0xFFFFD580),
    secondary = Color(0xFFA5A5A5),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF333333),
    onSecondaryContainer = Color.White,
    background = Color(0xFF000000),
    onBackground = Color.White,
    surface = Color(0xFF1C1C1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFAEAEB2),
    outlineVariant = Color(0xFF3A3A3C),
    error = Color(0xFFFF453A),
  )

@Composable
fun CalculatorProTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = AppleCalculatorDarkScheme,
    typography = Typography,
    content = content,
  )
}
