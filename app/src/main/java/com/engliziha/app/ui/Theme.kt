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

// Direction: the logo's palette. Soft off-white paper, the speech-bubble blue for actions,
// the chart green for success and the book's peach kept only for "pay attention".

private val Light = lightColorScheme(
    primary = Color(0xFF2C78BD),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6ECFB),
    onPrimaryContainer = Color(0xFF0B3558),
    secondary = Color(0xFF3C9A68),
    background = Color(0xFFFBFAF7),
    onBackground = Color(0xFF1F2A37),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2A37),
    surfaceContainer = Color(0xFFF0F4F7),
    surfaceContainerHigh = Color(0xFFE3EAF0),
    surfaceVariant = Color(0xFFF0F4F7),
    onSurfaceVariant = Color(0xFF596573),
    outline = Color(0xFFCBD5DE),
    outlineVariant = Color(0xFFE4E9EE),
    error = Color(0xFFC0392F),
    onError = Color(0xFFFFFFFF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF83C7F1),
    onPrimary = Color(0xFF06304F),
    primaryContainer = Color(0xFF1B4A6E),
    onPrimaryContainer = Color(0xFFD6ECFB),
    secondary = Color(0xFF9BD9B3),
    background = Color(0xFF0F151B),
    onBackground = Color(0xFFE6EBF0),
    surface = Color(0xFF161E26),
    onSurface = Color(0xFFE6EBF0),
    surfaceContainer = Color(0xFF1D2731),
    surfaceContainerHigh = Color(0xFF27323E),
    surfaceVariant = Color(0xFF1D2731),
    onSurfaceVariant = Color(0xFF9BA8B5),
    outline = Color(0xFF3A4755),
    outlineVariant = Color(0xFF28333F),
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
    attention = Color(0xFFB96F0E),
    attentionContainer = Color(0xFFFDEBCF),
    onAttentionContainer = Color(0xFF4A2D00),
    success = Color(0xFF2F8F5B),
    successContainer = Color(0xFFDDF3E5),
    levels = mapOf(
        "A1" to Color(0xFF3C9A68), "A2" to Color(0xFF2A8F9B), "B1" to Color(0xFF2C78BD),
        "B2" to Color(0xFFC77A1E), "C1" to Color(0xFF24507E),
    ),
)

private val DarkExtra = Extra(
    attention = Color(0xFFF7C886),
    attentionContainer = Color(0xFF4A3410),
    onAttentionContainer = Color(0xFFFCE6BC),
    success = Color(0xFF9BD9B3),
    successContainer = Color(0xFF173A26),
    levels = mapOf(
        "A1" to Color(0xFF9BD9B3), "A2" to Color(0xFF7FD0DA), "B1" to Color(0xFF83C7F1),
        "B2" to Color(0xFFF7C886), "C1" to Color(0xFFB7CDE6),
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
