package com.example.ui.theme

import androidx.compose.ui.graphics.Color

val MidnightAbyss = Color(0xFF0D0C15) // Deep indigo-black
val GhostWhite = Color(0xFFFFFFFF)
val StormGray = Color(0xFF2F3334)
val Comet = Color(0xFFD8ECF8)
val ArcticMist = Color(0xFFD1E4FA)
val CelestialLight = Color(0xFFB6D9FC)
val AzureGlow = Color(0xFFC7D3EA)
val SlateDew = Color(0xFF3F4959)
val WhisperBlue = Color(0xFF9DA7BA)
val NeonViolet = Color(0xFF663AF3)
val InterstellarGray = Color(0xFF81899B)

// Translucent specific colors for shadows and borders
val TranslucentBorder = Color(0xFFBAD7F7).copy(alpha = 0.12f)
val TranslucentGlass = Color(0xFFFFFFFF).copy(alpha = 0.05f) // Adjusted to a subtle white edge-glow/glass
val TranslucentForm = Color(0xFF0D0C15).copy(alpha = 0.70f)
val TranslucentInput = Color(0xFFFFFFFF).copy(alpha = 0.04f)

// New colors for Add Transaction redesign
val CyanGradientStart = Color(0xFF00F0FF)
val CyanGradientEnd = Color(0xFFB9FbFF)
val BlueGradientStart = Color(0xFF2979FF)
val BlueGradientEnd = Color(0xFFE3F2FD)
val MutedExpense = Color(0xFFB0BEC5)
val CyanGlow = Color(0x6600F0FF)
val BlueGlow = Color(0x662979FF)
val ErrorRed = Color(0xFFFF5252)
val NeonGreen = Color(0xFF00FF85)
val NeonRed = Color(0xFFFF0D5C)

// Steel Blue Custom colors
val SteelBlue = Color(0xFF8DA9C4)
val SteelBlueGlow = Color(0x668DA9C4)

// Permukaan (surface) yang sebelumnya ditulis sebagai warna literal di layar
// chat dan tab penyimpanan. Dipakai supaya warna kartu/gelenbung hanya punya
// satu sumber, bukan angka hex yang tersebar.
val SurfaceElevated = Color(0xFF1E1E1E) // kartu & gelenbung AI/typing
val SurfaceDeep = Color(0xFF13111C) // kartu indikator pemindaian struk
val SurfaceViolet = Color(0xFF221A3B) // kotak ikon di kartu pemindaian
val SurfaceDisabled = Color(0xFF0F0F14) // kolom input saat nonaktif

// ── Palet editorial ──────────────────────────────────────────────────────────
// Diambil langsung dari referensi portfolio
// (C:\Users\yasin\portfolio\css\styles.css, blok :root). Tab Dashboard memakai
// palet ini lebih dulu; tab lain menyusul, jadi nilai gelap di atas sengaja
// tidak dihapus supaya layar yang belum di-redesign tidak ikut berubah.
val Cream = Color(0xFFF3EEE4) // --bg: latar halaman
val CreamRaised = Color(0xFFFBF7EF) // --bg-2: latar kolom isian
val Ink = Color(0xFF18130E) // --fg: teks utama
val InkMuted = Color(0xFF6B6357) // --muted: teks sekunder
val Hairline = Color(0xFF18130E).copy(alpha = 0.12f) // --line
val HairlineStrong = Color(0xFF18130E).copy(alpha = 0.22f) // --line-strong
val BurntOrange = Color(0xFFD63F1A) // --accent
val ForestGreen = Color(0xFF264136) // --accent-2
val Amber = Color(0xFFB8922E) // --accent-3
val CardVeil = Color(0xFFFFFFFF).copy(alpha = 0.55f) // --card: bingkai bezel
val CardCore = Color(0xFFFFFDF8) // --card-core: inti bezel


