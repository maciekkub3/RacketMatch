package com.racketmatch.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ProCircuitColors = darkColorScheme(
    primary                = Color(0xFFF4FFC6),
    onPrimary              = Color(0xFF546600),
    primaryContainer       = Color(0xFFD1FC00),
    onPrimaryContainer     = Color(0xFF4C5D00),
    secondary              = Color(0xFFE1E3E5),
    onSecondary            = Color(0xFF4F5254),
    secondaryContainer     = Color(0xFF444749),
    onSecondaryContainer   = Color(0xFFCFD0D3),
    tertiary               = Color(0xFFFFEB9C),
    onTertiary             = Color(0xFF665600),
    tertiaryContainer      = Color(0xFFFCDC43),
    onTertiaryContainer    = Color(0xFF5C4E00),
    background             = Color(0xFF0C0E0F),
    onBackground           = Color(0xFFF6F6F7),
    surface                = Color(0xFF0C0E0F),
    onSurface              = Color(0xFFF6F6F7),
    surfaceVariant         = Color(0xFF232628),
    onSurfaceVariant       = Color(0xFFAAABAC),
    surfaceTint            = Color(0xFFF4FFC6),
    inverseSurface         = Color(0xFFF9F9FA),
    inverseOnSurface       = Color(0xFF545556),
    inversePrimary         = Color(0xFF546600),
    outline                = Color(0xFF747577),
    outlineVariant         = Color(0xFF464849),
    error                  = Color(0xFFFF7351),
    onError                = Color(0xFF450900),
    errorContainer         = Color(0xFFB92902),
    onErrorContainer       = Color(0xFFFFD2C8),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow    = Color(0xFF111415),
    surfaceContainer       = Color(0xFF171A1B),
    surfaceContainerHigh   = Color(0xFF1D2021),
    surfaceContainerHighest= Color(0xFF232628),
)

private val ProCircuitTypography = Typography(
    displayLarge  = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Black,     fontSize = 57.sp, lineHeight = 64.sp,  letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall  = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,      fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium= TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,      fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,      fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge    = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,      fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium   = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold,  fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall    = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium,    fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge     = TextStyle(fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium    = TextStyle(fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall     = TextStyle(fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge    = TextStyle(fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Bold,     fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.5.sp),
    labelMedium   = TextStyle(fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Bold,     fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall    = TextStyle(fontFamily = AppBodyFontFamily, fontWeight = FontWeight.Bold,     fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 1.sp),
)

private val ProCircuitShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ProCircuitColors,
        typography  = ProCircuitTypography,
        shapes      = ProCircuitShapes,
        content     = content
    )
}
