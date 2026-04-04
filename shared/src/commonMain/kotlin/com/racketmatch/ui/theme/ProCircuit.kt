package com.racketmatch.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Global theme toggle — readable from any composable; Compose tracks it automatically. */
object ThemeState {
    var isDark by mutableStateOf(false)
}

object ProCircuit {
    // Primary brand
    val Lime        get() = if (ThemeState.isDark) Color(0xFF9EC80A) else Color(0xFF006633)
    // Backgrounds
    val Bg          get() = if (ThemeState.isDark) Color(0xFF111110) else Color(0xFFDFDCD6)
    val SurfaceLow  get() = if (ThemeState.isDark) Color(0xFF1E1E1C) else Color(0xFFFFFFFF)
    val SurfaceHigh get() = if (ThemeState.isDark) Color(0xFF282826) else Color(0xFFF2EFE9)
    val SurfaceVar  get() = if (ThemeState.isDark) Color(0xFF323230) else Color(0xFFE6E3DD)
    // Text
    val OnBg        get() = if (ThemeState.isDark) Color(0xFFF0EDE5) else Color(0xFF1A1918)
    val OnSurface   get() = if (ThemeState.isDark) Color(0xFF8A8780) else Color(0xFF6B6865)
    // Accents — deep navy (replaces purple) + a lighter tint in dark mode
    val Tertiary    get() = if (ThemeState.isDark) Color(0xFF9EC80A) else Color(0xFF006633)
    val Error       get() = if (ThemeState.isDark) Color(0xFFE06050) else Color(0xFFC0392B)
    val Outline     get() = if (ThemeState.isDark) Color(0xFF454543) else Color(0xFFB8B4AE)
}
