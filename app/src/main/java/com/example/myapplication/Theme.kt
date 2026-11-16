package com.example.myapplication

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Светлая палитра
private val LightColors = lightColorScheme(
    primary = Color(0xFF6200EE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2FD),
    onPrimaryContainer = Color(0xFF6200EE),

    background = Color(0xFFF7F7FA),
    onBackground = Color.Black,

    surface = Color.White,
    onSurface = Color.Black,

    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color.Gray
)

// Темная палитра
private val DarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),

    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),

    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),

    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFB0B0B0)
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}


// ================= ЦВЕТА СТАТУСОВ (светлая тема = прежние значения) =================
@Composable
fun isAppDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable fun acceptedBg(): Color = if (isAppDark()) Color(0xFF1E3A29) else Color(0xFFC8E6C9)
@Composable fun allAcceptedBg(): Color = if (isAppDark()) Color(0xFF1E3A29) else Color(0xFFCBEFD1)
@Composable fun rejectedBg(): Color = if (isAppDark()) Color(0xFF4A2326) else Color(0xFFFFCDD2)
@Composable fun reworkBg(): Color = if (isAppDark()) Color(0xFF4A4120) else Color(0xFFFFF9C4)

@Composable fun successText(): Color = if (isAppDark()) Color(0xFFA5D6A7) else Color(0xFF2E7D32)
@Composable fun reworkAccent(): Color = if (isAppDark()) Color(0xFFFFB74D) else Color(0xFFF9A825)
@Composable fun warnText(): Color = reworkAccent()
@Composable fun statusLabelColor(): Color = if (isAppDark()) MaterialTheme.colorScheme.onSurface else Color.DarkGray
@Composable fun subtleText(): Color = if (isAppDark()) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
