package com.example.ui.common

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*
import com.example.util.PinUtils
import kotlinx.coroutines.launch

enum class PinMode { SETUP, CONFIRM, UNLOCK, CHANGE_OLD }

/**
 * Full-screen PIN entry. Works for setup (new PIN + confirm), unlock, and change-old-PIN flows.
 * Requires user to press "Konfirmasi" button after entering 4-6 digits — no auto-submit.
 */
@Composable
fun PinScreen(
    mode: PinMode,
    title: String? = null,
    subtitle: String? = null,
    onSuccess: (String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    // step 0 = enter first PIN, step 1 = confirm PIN (SETUP mode only)
    var step by remember { mutableStateOf(0) }
    var firstPin by remember { mutableStateOf("") }
    var currentInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    // Shake animation for wrong PIN
    val shakeOffset = remember { Animatable(0f) }
    // Pulse animation for verifying state
    val infiniteTransition = rememberInfiniteTransition(label = "verify-pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse-alpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse-scale"
    )

    val isUnlock = mode == PinMode.UNLOCK
    val isChange = mode == PinMode.CHANGE_OLD

    val resolvedTitle = title ?: when {
        isUnlock -> "Buka Kunci"
        isChange -> "Masukkan PIN Lama"
        step == 0 && mode == PinMode.SETUP -> "Buat PIN Baru"
        step == 1 && mode == PinMode.SETUP -> "Konfirmasi PIN"
        else -> "Masukkan PIN"
    }
    val resolvedSubtitle = if (isVerifying) "Memverifikasi..." else (subtitle ?: when {
        isUnlock -> "Masukkan PIN untuk membuka aplikasi"
        isChange -> "Verifikasi PIN lama sebelum mengganti"
        step == 0 -> "PIN 4–6 digit"
        step == 1 -> "Masukkan ulang PIN baru"
        else -> ""
    })

    suspend fun triggerShake() {
        val shake = listOf(12f, -12f, 8f, -8f, 4f, -4f, 0f)
        for (v in shake) {
            shakeOffset.snapTo(v)
            kotlinx.coroutines.delay(40)
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
                .offset(x = shakeOffset.value.dp)
                .padding(32.dp)
        ) {
            // --- Lock Icon ---
            Icon(
                imageVector = when {
                    isVerifying -> Icons.Default.Lock
                    isUnlock -> Icons.Default.Lock
                    else -> Icons.Default.LockReset
                },
                contentDescription = null,
                tint = if (isVerifying) SteelBlue.copy(alpha = pulseAlpha) else SteelBlue,
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        if (isVerifying) {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                    }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- Title ---
            Text(
                text = resolvedTitle,
                color = GhostWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (resolvedSubtitle.isNotEmpty()) {
                Text(
                    text = resolvedSubtitle,
                    color = GhostWhite.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(36.dp))
            } else {
                Spacer(modifier = Modifier.height(28.dp))
            }

            // --- PIN Dot Indicators ---
            val displayLen = currentInput.length
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                for (i in 0 until 6) {
                    val filled = i < displayLen
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) SteelBlue else SteelBlue.copy(alpha = 0.15f)
                            )
                            .then(
                                if (filled) Modifier.border(1.dp, SteelBlue.copy(alpha = 0.5f), CircleShape)
                                else Modifier
                            )
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            // --- Error Text ---
            AnimatedVisibility(visible = error != null) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = error ?: "",
                        color = ErrorRed,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // --- Numeric Keypad ---
            PinNumericKeypad(
                onDigit = { digit ->
                    if (isVerifying) return@PinNumericKeypad
                    if (error != null) error = null
                    if (currentInput.length < 6) {
                        currentInput += digit
                    }
                },
                onDelete = {
                    if (isVerifying) return@PinNumericKeypad
                    if (error != null) error = null
                    if (currentInput.isNotEmpty()) {
                        currentInput = currentInput.dropLast(1)
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // --- Konfirmasi / Submit Button ---
            val canSubmit = currentInput.length in 4..6 && !isVerifying
            val coroutineScope = rememberCoroutineScope()

            GlassSubmitButton(
                text = when {
                    isUnlock || isChange -> "Konfirmasi"
                    step == 0 && mode == PinMode.SETUP -> "Lanjut"
                    step == 1 -> "Simpan PIN"
                    else -> "Konfirmasi"
                },
                enabled = canSubmit,
                onClick = {
                    error = null
                    when {
                        isUnlock || isChange -> {
                            val input = currentInput
                            isVerifying = true
                            error = null
                            coroutineScope.launch {
                                val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    PinUtils.verifyPin(context, input)
                                }
                                isVerifying = false
                                if (ok) {
                                    onSuccess(input)
                                } else {
                                    error = "PIN salah"
                                    currentInput = ""
                                    triggerShake()
                                }
                            }
                        }
                        step == 0 && mode == PinMode.SETUP -> {
                            // Move to confirm step
                            firstPin = currentInput
                            currentInput = ""
                            step = 1
                        }
                        step == 1 && mode == PinMode.SETUP -> {
                            if (currentInput == firstPin) {
                                onSuccess(firstPin)
                            } else {
                                error = "PIN tidak cocok. Ulangi dari awal."
                                firstPin = ""
                                currentInput = ""
                                step = 0
                                coroutineScope.launch { triggerShake() }
                            }
                        }
                    }
                }
            )

            // --- Back / Cancel ---
            if (onBack != null) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onBack) {
                    Text(
                        text = "Kembali",
                        color = SteelBlue.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ==================== Keypad ====================

@Composable
private fun PinNumericKeypad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                row.forEach { digit ->
                    PinKeypadDigit(digit = digit, onClick = { onDigit(digit) })
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        // Last row: spacer, 0, delete
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.size(72.dp)) // empty placeholder
            PinKeypadDigit(digit = "0", onClick = { onDigit("0") })
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                GhostWhite.copy(alpha = 0.06f),
                                GhostWhite.copy(alpha = 0.01f)
                            )
                        )
                    )
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Hapus",
                    tint = GhostWhite.copy(alpha = 0.6f),
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PinKeypadDigit(
    digit: String,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(100),
        label = "keypad-press"
    )

    Box(
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GhostWhite.copy(alpha = 0.10f),
                        GhostWhite.copy(alpha = 0.03f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GhostWhite.copy(alpha = 0.18f),
                        GhostWhite.copy(alpha = 0.04f)
                    )
                ),
                shape = CircleShape
            )
            .clickable {
                pressed = true
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            color = GhostWhite,
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium
        )
    }
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(100)
            pressed = false
        }
    }
}

// ==================== Submit Button ====================

@Composable
private fun GlassSubmitButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .height(52.dp)
            .clip(RoundedCornerShape(CornerRadius.xl))
            .background(
                brush = if (enabled) {
                    Brush.verticalGradient(
                        colors = listOf(SteelBlue, SteelBlue.copy(alpha = 0.7f))
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            GhostWhite.copy(alpha = 0.05f),
                            GhostWhite.copy(alpha = 0.02f)
                        )
                    )
                }
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = if (enabled) {
                        listOf(SteelBlue.copy(alpha = 0.6f), SteelBlue.copy(alpha = 0.2f))
                    } else {
                        listOf(GhostWhite.copy(alpha = 0.08f), GhostWhite.copy(alpha = 0.02f))
                    }
                ),
                shape = RoundedCornerShape(CornerRadius.xl)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) GhostWhite else GhostWhite.copy(alpha = 0.3f),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ==================== Dialog Wrappers ====================

@Composable
fun PinSetupDialog(
    onDismiss: () -> Unit,
    onPinSet: () -> Unit
) {
    val context = LocalContext.current
    // If user already has a PIN, require old PIN verification first
    var oldPinVerified by remember { mutableStateOf(!PinUtils.hasPin(context)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightAbyss),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(GhostWhite.copy(alpha = 0.15f), GhostWhite.copy(alpha = 0.02f))
                )
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TranslucentGlass)
            ) {
                if (!oldPinVerified) {
                    PinScreen(
                        mode = PinMode.CHANGE_OLD,
                        onSuccess = { oldPinVerified = true },
                        onBack = onDismiss
                    )
                } else {
                    PinScreen(
                        mode = PinMode.SETUP,
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
