package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.util.SecurityUtil

@Composable
fun SecurityViolationScreen(
    securityStatus: SecurityUtil.SecurityStatus
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightAbyss)
            .padding(24.dp)
            .testTag("security_violation_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Decorative cosmic glow spots
        val glowBrush = Brush.radialGradient(
            colors = listOf(NeonRed.copy(alpha = 0.12f), Color.Transparent),
            radius = 1200f
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(glowBrush)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = TranslucentForm.copy(alpha = 0.85f)),
            border = BorderStroke(
                width = 1.3.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(NeonRed.copy(alpha = 0.25f), NeonRed.copy(alpha = 0.05f))
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Warning badge header
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(NeonRed.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                        .border(1.dp, NeonRed.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (securityStatus == SecurityUtil.SecurityStatus.ROOTED) Icons.Default.Security else Icons.Default.PhoneAndroid,
                        contentDescription = "Peringatan Keamanan",
                        tint = NeonRed,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Alert Title
                Text(
                    text = if (securityStatus == SecurityUtil.SecurityStatus.ROOTED) {
                        "Perangkat Terdeteksi Root"
                    } else {
                        "Emulator Dinonaktifkan"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = GhostWhite,
                        fontFamily = FontFamily.SansSerif
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("security_title")
                )

                // Indication category label
                Box(
                    modifier = Modifier
                        .background(NeonRed.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (securityStatus == SecurityUtil.SecurityStatus.ROOTED) "KEAMANAN: TINGGI" else "BUILD RELEASE: EMULATOR BLOCKED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = NeonRed,
                            letterSpacing = 0.8.sp
                        )
                    )
                }

                // Warning Description Text
                Text(
                    text = if (securityStatus == SecurityUtil.SecurityStatus.ROOTED) {
                        "Untuk melindungi data keuangan pribadi Anda dari risiko pencurian kredensial, manipulasi transaksi, dan celah keamanan sistem, aplikasi ini tidak diizinkan berjalan pada perangkat yang telah di-root."
                    } else {
                        "Aplikasi dalam build release ini dikonfigurasi untuk mencegah pengujian di lingkungan emulator. Silakan jalankan aplikasi pada perangkat fisik asli untuk menggunakannya."
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = GhostWhite.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        fontFamily = FontFamily.SansSerif
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Divider(color = GhostWhite.copy(alpha = 0.1f), thickness = 1.dp)

                // Recommendation/Notice Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = InterstellarGray,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (securityStatus == SecurityUtil.SecurityStatus.ROOTED) {
                            "Status root: Terverifikasi"
                        } else {
                            "Status emulator: Terdeteksi"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = InterstellarGray,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}
