package com.example.ui.theme

import androidx.compose.ui.unit.dp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    /** Padding dalam kartu besar; sebelumnya literal 20.dp di tab penyimpanan. */
    val card = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    /** Padding bingkai double-bezel (0.75rem). */
    val bezel = 12.dp
}

object CornerRadius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    /** Sudut kartu besar; sebelumnya literal 20.dp di tab penyimpanan. */
    val card = 20.dp
    val xl = 24.dp

    /**
     * Double-bezel dari referensi portfolio: bingkai luar 2rem dan inti dalam
     * 1.25rem (.bezel / .core di styles.css). Selisih keduanya sama dengan
     * [Spacing.bezel] supaya inti terlihat "masuk" ke dalam bingkai.
     */
    val bezel = 32.dp
    val bezelCore = 20.dp
    /** Sudut pil (tombol, chip, kolom cari) — 999px di referensi. */
    val pill = 999.dp
}
