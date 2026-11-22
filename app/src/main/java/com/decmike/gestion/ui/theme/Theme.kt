package com.decmike.gestion.ui.theme

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

private val Light = lightColorScheme(
    primary = Color(0xFF1E3A5F),
    secondary = Color(0xFFE0A526),
    tertiary = Color(0xFF2E7D6B),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF9CC3F0),
    secondary = Color(0xFFFFC857),
    tertiary = Color(0xFF7ED3BC),
)

val RightEarColor = Color(0xFFD32F2F)
val LeftEarColor = Color(0xFF1976D2)

@Composable
fun GestionTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
