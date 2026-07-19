package com.example.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*
import com.example.util.PinUtils

enum class PinMode { SETUP, CONFIRM, UNLOCK, CHANGE_OLD }

@Composable
fun PinScreen(
    mode: PinMode,
    title: String? = null,
    subtitle: String? = null,
    onSuccess: (String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf(0) }

    val isUnlock = mode == PinMode.UNLOCK
    val isChange = mode == PinMode.CHANGE_OLD

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
                .padding(32.dp)
        ) {
            Icon(
                imageVector = if (isUnlock) Icons.Default.Lock else Icons.Default.LockReset,
                contentDescription = null,
                tint = SteelBlue,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = title ?: when {
                    isUnlock -> "Buka Kunci"
                    isChange -> "Masukkan PIN Lama"
                    step == 0 -> "Buat PIN"
                    else -> "Konfirmasi PIN"
                },
                color = GhostWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle ?: when {
                    isUnlock -> "Masukkan PIN untuk membuka aplikasi"
                    isChange -> ""
                    step == 0 -> "Buat PIN 4-6 digit"
                    else -> "Masukkan ulang PIN baru"
                },
                color = GhostWhite.copy(alpha = 0.6f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // PIN dots display
            val displayPin = if (step == 1) confirmPin else pin
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                for (i in 0 until 6) {
                    val filled = i < displayPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) SteelBlue else SteelBlue.copy(alpha = 0.2f)
                            )
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error ?: "",
                    color = ErrorRed,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Numeric keypad
            NumericKeypad(
                onDigit = { digit ->
                    error = null
                    val currentPin = if (step == 1) confirmPin else pin
                    if (currentPin.length < 6) {
                        val newPin = currentPin + digit
                        if (step == 1) {
                            confirmPin = newPin
                            if (newPin.length >= 4 && newPin.length <= 6) {
                                if (pin == newPin) {
                                    onSuccess(pin)
                                } else {
                                    error = "PIN tidak cocok. Coba lagi."
                                    confirmPin = ""
                                    pin = ""
                                    step = 0
                                }
                            }
                        } else {
                            pin = newPin
                            if (newPin.length >= 4 && newPin.length <= 6) {
                                if (mode == PinMode.SETUP || mode == PinMode.CONFIRM) {
                                    step = 1
                                } else if (isUnlock || isChange) {
                                    if (PinUtils.verifyPin(context, newPin)) {
                                        onSuccess(newPin)
                                    } else {
                                        error = "PIN salah"
                                        pin = ""
                                    }
                                }
                            }
                        }
                    }
                },
                onDelete = {
                    error = null
                    if (step == 1) {
                        if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                    } else {
                        if (pin.isNotEmpty()) pin = pin.dropLast(1)
                    }
                }
            )

            if (onBack != null) {
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = onBack) {
                    Text("Kembali", color = SteelBlue)
                }
            }
        }
    }
}

@Composable
private fun NumericKeypad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                row.forEach { digit ->
                    KeypadButton(digit = digit, onClick = { onDigit(digit) })
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.width(64.dp))
            KeypadButton(digit = "0", onClick = { onDigit("0") })
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = "Hapus",
                    tint = GhostWhite.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun KeypadButton(
    digit: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GhostWhite.copy(alpha = 0.08f),
                        GhostWhite.copy(alpha = 0.02f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GhostWhite.copy(alpha = 0.15f),
                        GhostWhite.copy(alpha = 0.03f)
                    )
                ),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            color = GhostWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PinSetupDialog(
    onDismiss: () -> Unit,
    onPinSet: () -> Unit
) {
    val context = LocalContext.current
    var oldPinVerified by remember { mutableStateOf(!PinUtils.hasPin(context)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightAbyss),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(GhostWhite.copy(alpha = 0.2f), GhostWhite.copy(alpha = 0.02f))
                )
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TranslucentGlass)
            ) {
                if (!oldPinVerified) {
                    PinScreen(
                        mode = PinMode.CHANGE_OLD,
                        title = "Masukkan PIN Lama",
                        onSuccess = { oldPinVerified = true },
                        onBack = onDismiss
                    )
                } else {
                    PinScreen(
                        mode = PinMode.SETUP,
                        title = if (PinUtils.hasPin(context)) "Buat PIN Baru" else "Buat PIN",
                        subtitle = "PIN baru 4-6 digit",
                        onSuccess = { newPin ->
                            PinUtils.savePin(context, newPin)
                            onPinSet()
                        },
                        onBack = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
fun ChangePinDialog(
    onDismiss: () -> Unit,
    onPinChanged: () -> Unit
) {
    PinSetupDialog(onDismiss = onDismiss, onPinSet = onPinChanged)
}

@Composable
fun PinUnlockScreen(
    onUnlocked: () -> Unit,
    onExit: () -> Unit
) {
    PinScreen(
        mode = PinMode.UNLOCK,
        onSuccess = { onUnlocked() },
        onBack = onExit
    )
}