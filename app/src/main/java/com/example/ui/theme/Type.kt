package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp

private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = emptyList()
)

private val InterFont = GoogleFont("Inter")

val InterFamily = FontFamily(
    Font(googleFont = InterFont, fontProvider = googleFontProvider, weight = FontWeight.Light),
    Font(googleFont = InterFont, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = InterFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = InterFont, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = InterFont, fontProvider = googleFontProvider, weight = FontWeight.Bold),
)

val Typography = Typography(
    // Display — hero numbers, splash
    displayLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    // Headline — section titles
    headlineLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    // Title — card titles, list headers
    titleLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    // Body — content text
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
    ),
    // Label — chips, buttons, captions
    labelLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
)

// ── Tipografi editorial (referensi portfolio) ────────────────────────────────
// Tiga keluarga yang sama dengan referensi: Fraunces (judul & angka besar),
// Instrument Sans (teks UI), JetBrains Mono (label kecil). Dipasang lewat
// provider font Google yang sudah dipakai Inter di atas, jadi tidak ada
// dependency baru dan ukuran APK tidak bertambah.

private val FrauncesFont = GoogleFont("Fraunces")
private val InstrumentSansFont = GoogleFont("Instrument Sans")
private val JetBrainsMonoFont = GoogleFont("JetBrains Mono")

/** --serif: judul dan angka besar. Italic hanya untuk penekanan. */
val SerifFamily = FontFamily(
    Font(googleFont = FrauncesFont, fontProvider = googleFontProvider, weight = FontWeight.Light),
    Font(googleFont = FrauncesFont, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = FrauncesFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = FrauncesFont, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(
        googleFont = FrauncesFont,
        fontProvider = googleFontProvider,
        weight = FontWeight.Normal,
        style = FontStyle.Italic
    ),
)

/** --font: seluruh teks UI, tombol, dan isi. */
val SansFamily = FontFamily(
    Font(googleFont = InstrumentSansFont, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = InstrumentSansFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = InstrumentSansFont, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = InstrumentSansFont, fontProvider = googleFontProvider, weight = FontWeight.Bold),
)

/** --mono: label kecil (eyebrow, tag, stempel, tanggal). */
val MonoFamily = FontFamily(
    Font(googleFont = JetBrainsMonoFont, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = JetBrainsMonoFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
)

/** .eyebrow / .kpi-label — label mono di atas sebuah blok. */
val EyebrowTextStyle = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.7.sp,
)

/** .cap-tag / .work-tech li — chip label di dalam kartu. */
val TagTextStyle = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    letterSpacing = 0.4.sp,
)

/** .slip-stamp — stempel kecil berbingkai. */
val StampTextStyle = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.9.sp,
)

/**
 * Skala tipe untuk layar yang sudah memakai palet editorial.
 *
 * Ukuran diambil dari referensi (clamp-nya dipadatkan ke nilai tetap yang aman
 * di layar ponsel): h1 hero 2.6-5.6rem, h2 seksi 2-3.4rem, kpi 2.4rem.
 * Berat Fraunces di referensi 360-420, sedangkan provider font hanya mengenal
 * langkah 300/400/500, jadi dipakai Light/Normal/Medium.
 */
val EditorialTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Light,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Light,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 19.sp,
        lineHeight = 25.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp,
    ),
)
