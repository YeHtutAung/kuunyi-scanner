package com.kuunyi.scanner.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.kuunyi.scanner.R

val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val interTight = GoogleFont("Inter Tight")

val InterTightFamily = FontFamily(
    Font(googleFont = interTight, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = interTight, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = interTight, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = interTight, fontProvider = googleFontProvider, weight = FontWeight.Bold),
    Font(googleFont = interTight, fontProvider = googleFontProvider, weight = FontWeight.ExtraBold),
)
