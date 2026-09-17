package com.abbeysbite.app.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * System-font typography: large, clear, generous line heights.
 * Display/headline sizes are deliberately big — the app leans on type and
 * whitespace rather than decoration.
 */
@Composable
fun AppTypography(): Typography {
    val system = FontFamily.Default
    return Typography(
        displayLarge = TextStyle(
            fontFamily = system, fontWeight = FontWeight.Bold,
            fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.5).sp,
        ),
        displayMedium = TextStyle(
            fontFamily = system, fontWeight = FontWeight.Bold,
            fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.4).sp,
        ),
        displaySmall = TextStyle(
            fontFamily = system, fontWeight = FontWeight.Bold,
            fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.3).sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = system, fontWeight = FontWeight.Bold,
            fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.25).sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = system, fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = system, fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.1).sp,
        ),
        titleLarge = TextStyle(
            fontFamily = system, fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp, lineHeight = 24.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = system, fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp, lineHeight = 22.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = system, fontWeight = FontWeight.Medium,
            fontSize = 14.sp, lineHeight = 20.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = system, fontWeight = FontWeight.Normal,
            fontSize = 16.sp, lineHeight = 24.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = system, fontWeight = FontWeight.Normal,
            fontSize = 14.sp, lineHeight = 21.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = system, fontWeight = FontWeight.Normal,
            fontSize = 12.sp, lineHeight = 17.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = system, fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp, lineHeight = 20.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = system, fontWeight = FontWeight.Medium,
            fontSize = 13.sp, lineHeight = 18.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = system, fontWeight = FontWeight.Medium,
            fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.3.sp,
        ),
    )
}
