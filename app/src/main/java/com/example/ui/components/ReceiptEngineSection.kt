package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GhostWhite
import com.example.ui.theme.TranslucentGlass
import com.example.util.ReceiptEngine

/**
 * Kartu pengaturan "Mesin baca struk".
 *
 * Dipasang di tab Ekspor & Impor karena aplikasi tidak punya tab Pengaturan
 * tersendiri. Default-nya Gemini supaya perilaku lama tidak berubah.
 */
@Composable
fun ReceiptEngineSection(
    selected: ReceiptEngine,
    onSelect: (ReceiptEngine) -> Unit,
    isTypeLlmReady: Boolean,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier, padding = 20.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ReceiptLong,
                    contentDescription = null,
                    tint = GhostWhite.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Mesin Baca Struk",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = GhostWhite
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Pilih AI yang dipakai saat kamu memotret struk atau screenshot bukti transaksi. " +
                    "Parsing transaksi dari chat teks tetap memakai Gemini.",
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = GhostWhite.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            EngineOption(
                title = "Gemini",
                subtitle = "Default. Kunci API tersimpan di server, tidak ada di aplikasi.",
                selected = selected == ReceiptEngine.GEMINI,
                enabled = true,
                onClick = { onSelect(ReceiptEngine.GEMINI) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            EngineOption(
                title = "TypeLLM",
                subtitle = if (isTypeLlmReady) {
                    "Output terstruktur dengan tipe data pasti. Batas 12 baris item per permintaan."
                } else {
                    "TYPELLM_API_KEY belum diisi di local.properties — belum bisa dipakai."
                },
                selected = selected == ReceiptEngine.TYPELLM,
                enabled = isTypeLlmReady,
                onClick = { onSelect(ReceiptEngine.TYPELLM) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = GhostWhite.copy(alpha = 0.35f),
                    modifier = Modifier.size(14.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Kalau TypeLLM gagal (kunci habis kuota, tidak valid, atau jaringan bermasalah), " +
                        "aplikasi otomatis memakai Gemini supaya struk tetap terbaca.",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                    color = GhostWhite.copy(alpha = 0.35f)
                )
            }
        }
    }
}

@Composable
private fun EngineOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.45f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("receipt_engine_$title")
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                TranslucentGlass.copy(alpha = 0.10f)
            } else {
                TranslucentGlass.copy(alpha = 0.04f)
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                GhostWhite.copy(alpha = 0.28f)
            } else {
                GhostWhite.copy(alpha = 0.08f)
            }
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = { if (enabled) onClick() },
                enabled = enabled,
                colors = RadioButtonDefaults.colors(
                    selectedColor = GhostWhite,
                    unselectedColor = GhostWhite.copy(alpha = 0.3f)
                ),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = GhostWhite.copy(alpha = contentAlpha)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                    color = GhostWhite.copy(alpha = if (enabled) 0.45f else 0.3f)
                )
            }
        }
    }
}