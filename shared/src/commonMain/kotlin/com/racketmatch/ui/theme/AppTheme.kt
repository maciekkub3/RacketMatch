package com.racketmatch.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WimbledonColors = lightColorScheme(
    primary                = Color(0xFF006633),
    onPrimary              = Color(0xFFF7F5F0),
    primaryContainer       = Color(0xFFCCE8D9),
    onPrimaryContainer     = Color(0xFF003D1F),
    secondary              = Color(0xFF6B2D82),
    onSecondary            = Color(0xFFFFFFFF),
    secondaryContainer     = Color(0xFFE8D5F0),
    onSecondaryContainer   = Color(0xFF3D1650),
    tertiary               = Color(0xFFC9A84C),
    onTertiary             = Color(0xFFFFFFFF),
    tertiaryContainer      = Color(0xFFF5E6C4),
    onTertiaryContainer    = Color(0xFF5C4200),
    background             = Color(0xFFDFDCD6),
    onBackground           = Color(0xFF1A1918),
    surface                = Color(0xFFFFFFFF),
    onSurface              = Color(0xFF1A1918),
    surfaceVariant         = Color(0xFFE6E3DD),
    onSurfaceVariant       = Color(0xFF6B6865),
    surfaceTint            = Color(0xFF006633),
    inverseSurface         = Color(0xFF1A1918),
    inverseOnSurface       = Color(0xFFF7F5F0),
    inversePrimary         = Color(0xFF80C89F),
    outline                = Color(0xFFC5C2BC),
    outlineVariant         = Color(0xFFDAD7D1),
    error                  = Color(0xFFC0392B),
    onError                = Color(0xFFFFFFFF),
    errorContainer         = Color(0xFFFFDAD6),
    onErrorContainer       = Color(0xFF410002),
    surfaceContainerLowest = Color(0xFFDFDCD6),
    surfaceContainerLow    = Color(0xFFFFFFFF),
    surfaceContainer       = Color(0xFFF2EFE9),
    surfaceContainerHigh   = Color(0xFFE6E3DD),
    surfaceContainerHighest= Color(0xFFDAD7D1),
)

private val WimbledonTypography = Typography(
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

private val WimbledonShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(6.dp),
    medium     = RoundedCornerShape(8.dp),
    large      = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WimbledonColors,
        typography  = WimbledonTypography,
        shapes      = WimbledonShapes,
        content     = content
    )
}
