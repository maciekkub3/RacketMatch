package com.racketmatch.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Global theme toggle — readable from any composable; Compose tracks it automatically. */
object ThemeState {
    var isDark by mutableStateOf(false)
}

/**
 * Design tokens — ported from the Claude Design "RacketMatch Redesign" system
 * (lime / forest / warm-beige editorial palette, Geist-inspired).
 *
 * Legacy names (Lime/Bg/SurfaceLow/…) kept for backward compatibility with
 * existing call-sites; new names (LimeInk/Forest/Ink2/…) mirror the CSS
 * variables in the design prototype.
 */
object ProCircuit {
    // ── Brand accents ─────────────────────────────────────────────────────
    /** Primary brand accent — lime `#C9F040` (was Wimbledon green). */
    val Lime         get() = if (ThemeState.isDark) Color(0xFFC9F040) else Color(0xFFC9F040)
    /** Hover / secondary-lime / positive-delta text. */
    val Lime2        get() = if (ThemeState.isDark) Color(0xFFB5DC2E) else Color(0xFFB5DC2E)
    /** Ink color used on lime surfaces (text on lime buttons). */
    val LimeInk      get() = Color(0xFF0F1A08)

    /** Deep brand green used for hero/dark cards. */
    val Forest       get() = if (ThemeState.isDark) Color(0xFF1A5236) else Color(0xFF0F3E28)
    val Forest2      get() = if (ThemeState.isDark) Color(0xFF21603F) else Color(0xFF164A30)
    val ForestInk    get() = Color(0xFFE9F5ED)

    /** Secondary accent — used for some avatar tones. */
    val Blue         get() = Color(0xFF5691F0)

    // ── Backgrounds & surfaces ────────────────────────────────────────────
    val Bg           get() = if (ThemeState.isDark) Color(0xFF0B0D0A) else Color(0xFFEDEBE5)
    val Bg2          get() = if (ThemeState.isDark) Color(0xFF111411) else Color(0xFFE5E3DD)
    val SurfaceLow   get() = if (ThemeState.isDark) Color(0xFF171A16) else Color(0xFFFFFFFF)
    val SurfaceHigh  get() = if (ThemeState.isDark) Color(0xFF1E211C) else Color(0xFFF2EFE9)
    val SurfaceVar   get() = if (ThemeState.isDark) Color(0xFF323230) else Color(0xFFE6E3DD)

    // ── Ink / text ────────────────────────────────────────────────────────
    val Ink          get() = if (ThemeState.isDark) Color(0xFFF1F4EE) else Color(0xFF0E1410)
    val Ink2         get() = if (ThemeState.isDark) Color(0xA8F1F4EE) else Color(0xFF5A6159)
    val Ink3         get() = if (ThemeState.isDark) Color(0x66F1F4EE) else Color(0xFF9AA096)

    /** Alias for Ink — preserves existing `OnBg` / `OnSurface` API. */
    val OnBg         get() = Ink
    val OnSurface    get() = Ink2

    // ── Strokes / outlines ────────────────────────────────────────────────
    val Stroke       get() = if (ThemeState.isDark) Color(0x14FFFFFF) else Color(0x140E1410)
    val Stroke2      get() = if (ThemeState.isDark) Color(0x24FFFFFF) else Color(0x240E1410)
    val Outline      get() = Stroke2

    // ── Status / feedback ─────────────────────────────────────────────────
    /** Red used for loss / negative ELO delta. */
    val LossRed      get() = Color(0xFFFF7563)
    val Error        get() = if (ThemeState.isDark) Color(0xFFE06050) else Color(0xFFC0392B)

    // ── Nav-bar active tab ────────────────────────────────────────────────
    /**
     * Tertiary = background of active pill / segmented selector.
     * In the design this is "pill-dark" — near-black in light mode, lime in dark.
     */
    val Tertiary     get() = if (ThemeState.isDark) Lime else Ink
    /** Text/icon color on top of [Tertiary]. */
    val TertiaryInk  get() = if (ThemeState.isDark) LimeInk else Color(0xFFFFFFFF)
}
