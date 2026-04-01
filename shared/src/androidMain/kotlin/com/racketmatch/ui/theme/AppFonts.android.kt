package com.racketmatch.ui.theme

import androidx.annotation.ArrayRes
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.racketmatch.R

@ArrayRes
private val FONT_CERTS: Int = R.array.com_google_android_gms_fonts_certs

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = FONT_CERTS
)

actual val AppFontFamily: FontFamily = FontFamily(
    Font(googleFont = GoogleFont("Lexend"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Lexend"), fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("Lexend"), fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont("Lexend"), fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = GoogleFont("Lexend"), fontProvider = provider, weight = FontWeight.ExtraBold),
    Font(googleFont = GoogleFont("Lexend"), fontProvider = provider, weight = FontWeight.Black),
)

actual val AppBodyFontFamily: FontFamily = FontFamily(
    Font(googleFont = GoogleFont("Plus Jakarta Sans"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Plus Jakarta Sans"), fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("Plus Jakarta Sans"), fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont("Plus Jakarta Sans"), fontProvider = provider, weight = FontWeight.Bold),
)
