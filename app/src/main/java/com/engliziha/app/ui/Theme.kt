package com.engliziha.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engliziha.app.R

// Direction: a quiet study notebook. Warm paper, deep teal ink, and amber kept only for "pay attention".

private val Light = lightColorScheme(
    primary = Color(0xFF0F4C4A),
    onPrimary = Color(0xFFF6F1E7),
    primaryContainer = Color(0xFFCFE5DF),
    onPrimaryContainer = Color(0xFF07302F),
    secondary = Color(0xFF5B6B63),
    background = Color(0xFFF6F1E7),
    onBackground = Color(0xFF1B2526),
    surface = Color(0xFFFFFBF3),
    onSurface = Color(0xFF1B2526),
    surfaceContainer = Color(0xFFEFE8DA),
    surfaceContainerHigh = Color(0xFFE6DDCB),
    surfaceVariant = Color(0xFFEFE8DA),
    onSurfaceVariant = Color(0xFF5C6562),
    outline = Color(0xFFCFC5B0),
    outlineVariant = Color(0xFFE0D7C4),
    error = Color(0xFFB3372E),
    onError = Color(0xFFFFFFFF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD3C8),
    onPrimary = Color(0xFF05302D),
    primaryContainer = Color(0xFF174743),
    onPrimaryContainer = Color(0xFFCFEFE8),
    secondary = Color(0xFFA9B8B0),
    background = Color(0xFF0F1415),
    onBackground = Color(0xFFE8E3D8),
    surface = Color(0xFF161D1E),
    onSurface = Color(0xFFE8E3D8),
    surfaceContainer = Color(0xFF1E2728),
    surfaceContainerHigh = Color(0xFF283233),
    surfaceVariant = Color(0xFF1E2728),
    onSurfaceVariant = Color(0xFF9CA8A4),
    outline = Color(0xFF3A4647),
    outlineVariant = Color(0xFF2A3435),
    error = Color(0xFFFF9A8C),
    onError = Color(0xFF4A0B05),
)

@Immutable
data class Extra(
    val attention: Color,
    val attentionContainer: Color,
    val onAttentionContainer: Color,
    val success: Color,
    val successContainer: Color,
    val levels: Map<String, Color>,
) {
    fun level(code: String): Color = levels[code] ?: attention
}

private val LightExtra = Extra(
    attention = Color(0xFFB8740F),
    attentionContainer = Color(0xFFFBE7BF),
    onAttentionContainer = Color(0xFF4A2D00),
    success = Color(0xFF2E7D4F),
    successContainer = Color(0xFFD5EEDD),
    levels = mapOf(
        "A1" to Color(0xFF2E7D4F), "A2" to Color(0xFF0F6C78), "B1" to Color(0xFF2F5AA8),
        "B2" to Color(0xFF6A4BA3), "C1" to Color(0xFFA33B55),
    ),
)

private val DarkExtra = Extra(
    attention = Color(0xFFF2B95C),
    attentionContainer = Color(0xFF4A3410),
    onAttentionContainer = Color(0xFFFCE6BC),
    success = Color(0xFF7FD09C),
    successContainer = Color(0xFF173A26),
    levels = mapOf(
        "A1" to Color(0xFF7FD09C), "A2" to Color(0xFF6FC7D3), "B1" to Color(0xFF8FB0F0),
        "B2" to Color(0xFFBBA3EA), "C1" to Color(0xFFEE98AE),
    ),
)

val LocalExtra = staticCompositionLocalOf { LightExtra }

val Tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold),
)

private val Type = Typography(
    displaySmall = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

/** Numbers that line up in columns (scores, times). */
val Numeric = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum")

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalExtra provides if (dark) DarkExtra else LightExtra,
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = Type,
            shapes = AppShapes,
            content = content,
        )
    }
}
