package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import androidx.compose.ui.window.Dialog

@Composable
fun LockScreenOverlay(
    context: Context,
    savedPin: String,
    isPinEnabled: Boolean,
    isBiometricEnabled: Boolean,
    onUnlock: () -> Unit,
    onTriggerBiometrics: () -> Unit
) {
    val securityPrefs = remember { com.example.util.SecurePrefsHelper.getEncryptedPrefs(context, "app_security_prefs") }
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    // Cooldown attempt states
    var failedAttempts by remember { mutableStateOf(securityPrefs.getInt("failed_pin_attempts", 0)) }
    var cooldownUntil by remember { mutableStateOf(securityPrefs.getLong("cooldown_until", 0L)) }
    var remainingCooldownSeconds by remember { mutableStateOf(0L) }

    // Dialog state
    var isResetPinDialogOpen by remember { mutableStateOf(false) }

    // Auto trigger biometrics if enabled and not currently cooled down
    LaunchedEffect(Unit) {
        if (isBiometricEnabled && remainingCooldownSeconds == 0L) {
            onTriggerBiometrics()
        }
    }

    // Cooldown countdown loop
    LaunchedEffect(cooldownUntil) {
        while (true) {
            val currentTime = System.currentTimeMillis()
            if (cooldownUntil > currentTime) {
                remainingCooldownSeconds = (cooldownUntil - currentTime) / 1000 + 1
            } else {
                remainingCooldownSeconds = 0
                if (failedAttempts >= 5) {
                    failedAttempts = 0
                    securityPrefs.edit().putInt("failed_pin_attempts", 0).putLong("cooldown_until", 0L).apply()
                }
            }
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightAbyss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = SteelBlue,
                modifier = Modifier.size(54.dp)
            )
            
            Spacer(modifier = Modifier.height(14.dp))
            
            Text(
                text = "Aplikasi Terkunci",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = GhostWhite
            )
            
            Text(
                text = if (isPinEnabled) "Masukkan PIN keamanan 4-angka Anda" else "Sensor sidik jari diperlukan untuk membuka",
                style = MaterialTheme.typography.bodyMedium,
                color = GhostWhite.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center
            )

            // If under cooldown, show beautiful lock banner
            if (remainingCooldownSeconds > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "Terlalu banyak percobaan salah.\nAplikasi terkunci. Silakan coba kembali dalam $remainingCooldownSeconds detik.",
                        color = Color.Red,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, lineHeight = 16.sp),
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    )
                }
            } else if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Red, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (isPinEnabled) {
                // PIN dots indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 1..4) {
                        val isFilled = enteredPin.length >= i
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) SteelBlue else GhostWhite.copy(alpha = 0.15f))
                                .border(1.dp, if (isFilled) SteelBlue else GhostWhite.copy(alpha = 0.3f), CircleShape)
                        )
                    }
                }

                // Lupa PIN Button (Using PremiumButton style)
                PremiumButton(
                    text = "Lupa PIN?",
                    onClick = { isResetPinDialogOpen = true },
                    isActive = false,
                    fillMaxWidth = false,
                    modifier = Modifier.padding(top = 20.dp),
                    horizontalPadding = 20.dp,
                    verticalPadding = 6.dp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Custom keypad matching general visual rules
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("BIO", "0", "DEL")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    keys.forEach { rowKeys ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowKeys.forEach { key ->
                                if (key == "BIO") {
                                    if (isBiometricEnabled) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(if (remainingCooldownSeconds == 0L) SteelBlue.copy(alpha = 0.12f) else GhostWhite.copy(alpha = 0.02f))
                                                .clickable(enabled = remainingCooldownSeconds == 0L) { onTriggerBiometrics() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Fingerprint,
                                                contentDescription = "Sidik Jari",
                                                tint = if (remainingCooldownSeconds == 0L) SteelBlue else GhostWhite.copy(alpha = 0.15f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(64.dp))
                                    }
                                } else if (key == "DEL") {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(GhostWhite.copy(alpha = 0.04f))
                                            .clickable(enabled = remainingCooldownSeconds == 0L) {
                                                if (enteredPin.isNotEmpty()) {
                                                    enteredPin = enteredPin.dropLast(1)
                                                    errorMessage = ""
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Backspace,
                                            contentDescription = "Hapus",
                                            tint = if (remainingCooldownSeconds == 0L) GhostWhite.copy(alpha = 0.7f) else GhostWhite.copy(alpha = 0.15f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(GhostWhite.copy(alpha = 0.05f))
                                            .clickable(enabled = remainingCooldownSeconds == 0L) {
                                                if (enteredPin.length < 4) {
                                                    enteredPin += key
                                                    if (enteredPin.length == 4) {
                                                        val freshPin = securityPrefs.getString("saved_pin", "") ?: ""
                                                        if (enteredPin == freshPin) {
                                                            failedAttempts = 0
                                                            cooldownUntil = 0L
                                                            securityPrefs.edit()
                                                                .putInt("failed_pin_attempts", 0)
                                                                .putLong("cooldown_until", 0L)
                                                                .apply()
                                                            onUnlock()
                                                        } else {
                                                            val nextFails = failedAttempts + 1
                                                            failedAttempts = nextFails
                                                            if (nextFails >= 5) {
                                                                val triggerTime = System.currentTimeMillis() + 30000
                                                                cooldownUntil = triggerTime
                                                                securityPrefs.edit()
                                                                    .putInt("failed_pin_attempts", nextFails)
                                                                    .putLong("cooldown_until", triggerTime)
                                                                    .apply()
                                                                errorMessage = "Terlalu banyak percobaan."
                                                            } else {
                                                                securityPrefs.edit()
                                                                    .putInt("failed_pin_attempts", nextFails)
                                                                    .apply()
                                                                errorMessage = "PIN Salah! Sisa percobaan: ${5 - nextFails}"
                                                            }
                                                            enteredPin = ""
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                                            color = if (remainingCooldownSeconds == 0L) GhostWhite else GhostWhite.copy(alpha = 0.15f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                PremiumButton(
                    text = "Buka dengan Biometrik",
                    onClick = onTriggerBiometrics,
                    isActive = true,
                    modifier = Modifier.width(240.dp)
                )
            }
        }
    }

    // FORGOT PIN / RESET PIN DIALOG FLOW
    if (isResetPinDialogOpen) {
        val storedQuestion = remember { securityPrefs.getString("security_question", "") ?: "" }
        val storedAnswer = remember { securityPrefs.getString("security_answer", "") ?: "" }

        var answerInput by remember { mutableStateOf("") }
        var isAnswerVerified by remember { mutableStateOf(false) }
        var newPinInput by remember { mutableStateOf("") }
        var dialogError by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { isResetPinDialogOpen = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightAbyss),
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            GhostWhite.copy(alpha = 0.2f),
                            GhostWhite.copy(alpha = 0.02f)
                        )
                    )
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(modifier = Modifier.background(TranslucentGlass)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = if (!isAnswerVerified) "Pemulihan PIN" else "Buat PIN Baru",
                            color = GhostWhite,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (storedQuestion.isEmpty()) {
                                Text(
                                    text = "Pertanyaan keamanan belum diatur pada perangkat ini. Silakan hubungi dukungan atau pasang ulang aplikasi.",
                                    color = GhostWhite.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            } else if (!isAnswerVerified) {
                                Text(
                                    text = "Jawab pertanyaan keamanan di bawah untuk mengatur ulang PIN Anda:",
                                    color = GhostWhite.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )

                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = GhostWhite.copy(alpha = 0.05f))
                                ) {
                                    Text(
                                        text = storedQuestion,
                                        color = SteelBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }

                                OutlinedTextField(
                                    value = answerInput,
                                    onValueChange = { answerInput = it },
                                    placeholder = { Text("Masukkan jawaban Anda...", color = GhostWhite.copy(alpha = 0.3f)) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SteelBlue,
                                        unfocusedBorderColor = GhostWhite.copy(alpha = 0.2f),
                                        focusedTextColor = GhostWhite
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = "Pertanyaan berhasil diverifikasi! Silakan masukkan 4 angka kode PIN baru Anda:",
                                    color = GhostWhite.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )

                                OutlinedTextField(
                                    value = newPinInput,
                                    onValueChange = { newValue ->
                                        if (newValue.length <= 4 && newValue.all { it.isDigit() }) {
                                            newPinInput = newValue
                                        }
                                    },
                                    singleLine = true,
                                    placeholder = { Text("••••", color = GhostWhite.copy(alpha = 0.25f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                    textStyle = androidx.compose.ui.text.TextStyle(color = GhostWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SteelBlue,
                                        unfocusedBorderColor = GhostWhite.copy(alpha = 0.2f),
                                        focusedTextColor = GhostWhite
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (dialogError.isNotEmpty()) {
                                Text(
                                    text = dialogError,
                                    color = Color.Red,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PremiumButton(
                                text = "Batal",
                                onClick = { isResetPinDialogOpen = false },
                                isActive = false,
                                modifier = Modifier.weight(1f),
                                horizontalPadding = 8.dp,
                                verticalPadding = 6.dp
                            )

                            if (storedQuestion.isNotEmpty()) {
                                PremiumButton(
                                    text = if (!isAnswerVerified) "Verifikasi" else "Simpan & Masuk",
                                    onClick = {
                                        if (!isAnswerVerified) {
                                            if (answerInput.trim().lowercase() == storedAnswer.trim().lowercase()) {
                                                isAnswerVerified = true
                                                dialogError = ""
                                            } else {
                                                dialogError = "Jawaban Salah! Pastikan penulisan Anda tepat."
                                            }
                                        } else {
                                            if (newPinInput.length != 4) {
                                                dialogError = "PIN baru harus terdiri dari 4 digit angka!"
                                            } else {
                                                // Update PIN
                                                securityPrefs.edit()
                                                    .putString("saved_pin", newPinInput)
                                                    .putInt("failed_pin_attempts", 0)
                                                    .putLong("cooldown_until", 0L)
                                                    .apply()

                                                // Unlock
                                                failedAttempts = 0
                                                cooldownUntil = 0L
                                                enteredPin = ""
                                                isResetPinDialogOpen = false
                                                onUnlock()
                                                Toast.makeText(context, "PIN di-reset dan aplikasi berhasil dibuka!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    isActive = true,
                                    modifier = Modifier.weight(1f),
                                    horizontalPadding = 8.dp,
                                    verticalPadding = 6.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
