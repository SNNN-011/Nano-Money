package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun SecuritySettingsSection(
    isPinEnabled: Boolean,
    onPinToggle: (Boolean) -> Unit,
    isBiometricEnabled: Boolean,
    onBiometricToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = TranslucentForm.copy(alpha = 0.65f)),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(GhostWhite.copy(alpha = 0.15f), GhostWhite.copy(alpha = 0.02f))
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header of Security
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(SteelBlue, SteelBlue.copy(alpha = 0.4f))
                            ),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Aman & Kunci Aplikasi",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    ),
                    color = GhostWhite
                )
            }

            Text(
                text = "Aktifkan kunci pengaman PIN atau Biometrik Sidik Jari untuk mencegah orang lain melihat catatan keuangan Anda.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                color = GhostWhite.copy(alpha = 0.55f)
            )

            // PIN Toggle Item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kunci PIN 4-Angka",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = GhostWhite
                    )
                    Text(
                        text = if (isPinEnabled) "PIN aktif" else "Amankan aplikasi dengan PIN rahasia",
                        style = MaterialTheme.typography.bodySmall,
                        color = GhostWhite.copy(alpha = 0.5f)
                    )
                }
                PremiumSwitch(
                    checked = isPinEnabled,
                    onCheckedChange = { checked ->
                        onPinToggle(checked)
                    }
                )
            }

            HorizontalDivider(color = GhostWhite.copy(alpha = 0.08f))

            // Biometrics Toggle Item
            val isBiometricAllowed = isPinEnabled
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kunci Sidik Jari",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isBiometricAllowed) GhostWhite else GhostWhite.copy(alpha = 0.4f)
                        )
                    )
                    Text(
                        text = if (isBiometricAllowed) {
                            "Gunakan sensor sidik jari perangkat untuk masuk dengan cepat"
                        } else {
                            "PIN harus aktif terlebih dahulu untuk mengaktifkan Kunci Sidik Jari"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isBiometricAllowed) GhostWhite.copy(alpha = 0.5f) else GhostWhite.copy(alpha = 0.25f)
                    )
                }
                PremiumSwitch(
                    checked = isBiometricEnabled && isBiometricAllowed,
                    onCheckedChange = { checked ->
                        if (!isBiometricAllowed) {
                            Toast.makeText(context, "Untuk menggunakan fitur sidik jari, Anda harus mengaktifkan PIN terlebih dahulu!", Toast.LENGTH_LONG).show()
                        } else {
                            onBiometricToggle(checked)
                        }
                    }
                )
            }
        }
    }
}
