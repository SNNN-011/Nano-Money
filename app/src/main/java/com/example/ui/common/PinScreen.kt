package com.example.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.EditorialButton
import com.example.ui.components.EditorialButtonStyle
import com.example.ui.components.EditorialDialog
import com.example.ui.components.Eyebrow
import com.example.ui.theme.*
import com.example.util.PinUtils
import kotlinx.coroutines.launch

enum class PinMode { SETUP, CONFIRM, UNLOCK, CHANGE_OLD }

/**
 * Layar PIN: dipakai untuk memilih PIN baru, membuka kunci aplikasi, dan
 * memverifikasi PIN lama. Pengguna menekan "Konfirmasi" setelah 4-6 digit.
 *
 * [embedded] = true saat dipakai di dalam dialog: latar transparan dan tinggi
 * mengikuti isi, jadi kartu dialog tidak lagi memaksa 85% tinggi layar.
 */
@Composable
fun PinScreen(
    mode: PinMode,
    title: String? = null,
    subtitle: String? = null,
    onSuccess: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    embedded: Boolean = false
) {
    val context = LocalContext.current
    // step 0 = enter first PIN, step 1 = confirm PIN (SETUP mode only)
    var step by remember { mutableStateOf(0) }
    var firstPin by remember { mutableStateOf("") }
    var currentInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    // H3: lockout PIN — sisa waktu terkunci (ms); 5 gagal -> 30 dtk, 10 -> 5 mnt, 15 -> 30 mnt
    var lockRemainingMs by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        lockRemainingMs = PinUtils.lockoutRemainingMillis(context)
    }
    // hitung mundur sampai lockout berakhir (tick 1 detik)
    LaunchedEffect(lockRemainingMs) {
        if (lockRemainingMs > 0) {
            kotlinx.coroutines.delay(1000)
            lockRemainingMs = PinUtils.lockoutRemainingMillis(context)
        }
    }
    // Goyang saat PIN salah.
    val shakeOffset = remember { Animatable(0f) }

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
        step == 0 -> "PIN 4-6 digit"
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

    val container = if (embedded) Modifier.fillMaxWidth() else Modifier.fillMaxSize().background(Cream)

    Box(
        modifier = container.testTag("pin_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .offset(x = shakeOffset.value.dp)
                .padding(horizontal = if (embedded) 0.dp else Spacing.xxl)
        ) {
            Eyebrow(text = "Nano Money")

            Text(
                text = resolvedTitle,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Medium,
                color = Ink,
                textAlign = TextAlign.Center
            )

            if (resolvedSubtitle.isNotEmpty()) {
                Text(
                    text = resolvedSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    textAlign = TextAlign.Center
                )
            }

            // Titik penanda jumlah digit: kosong = garis rambut, terisi = tinta.
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = Spacing.xs)
            ) {
                for (i in 0 until 6) {
                    val filled = i < currentInput.length
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (filled) Ink else Color.Transparent)
                            .border(1.dp, if (filled) Ink else HairlineStrong, CircleShape)
                    )
                }
            }

            AnimatedVisibility(visible = error != null) {
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = BurntOrange,
                    textAlign = TextAlign.Center
                )
            }

            // H3: info sisa waktu lockout
            if (lockRemainingMs > 0) {
                Text(
                    text = "Terlalu banyak percobaan. Coba lagi dalam ${formatLockRemaining(lockRemainingMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = BurntOrange,
                    textAlign = TextAlign.Center
                )
            }

            PinNumericKeypad(
                onDigit = { digit ->
                    if (isVerifying || lockRemainingMs > 0) return@PinNumericKeypad
                    if (error != null) error = null
                    if (currentInput.length < 6) {
                        currentInput += digit
                    }
                },
                onDelete = {
                    if (isVerifying || lockRemainingMs > 0) return@PinNumericKeypad
                    if (error != null) error = null
                    if (currentInput.isNotEmpty()) {
                        currentInput = currentInput.dropLast(1)
                    }
                }
            )

            val canSubmit = currentInput.length in 4..6 && !isVerifying && lockRemainingMs <= 0
            val coroutineScope = rememberCoroutineScope()

            EditorialButton(
                text = when {
                    isUnlock || isChange -> "Konfirmasi"
                    step == 0 && mode == PinMode.SETUP -> "Lanjut"
                    step == 1 -> "Simpan PIN"
                    else -> "Konfirmasi"
                },
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
                                    // H3: refresh sisa waktu lockout setelah gagal
                                    lockRemainingMs = PinUtils.lockoutRemainingMillis(context)
                                    triggerShake()
                                }
                            }
                        }
                        step == 0 && mode == PinMode.SETUP -> {
                            // Lanjut ke langkah konfirmasi.
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
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSubmit,
                trailingArrow = false,
                expanded = true,
                testTag = "pin_submit_button"
            )

            if (onBack != null) {
                EditorialButton(
                    text = "Batal",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    style = EditorialButtonStyle.Ghost,
                    trailingArrow = false,
                    expanded = true,
                    accent = InkMuted,
                    testTag = "pin_cancel_button"
                )
            }
        }
    }
}

// ==================== Keypad ====================

/** Tombol angka 56 dp: cukup besar untuk jempol, tapi kartu dialog tetap ringkas. */
private val KeySize = 56.dp

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
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth()
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { digit ->
                    PinKeypadDigit(digit = digit, onClick = { onDigit(digit) })
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.size(KeySize))
            PinKeypadDigit(digit = "0", onClick = { onDigit("0") })
            Box(
                modifier = Modifier
                    .size(KeySize)
                    .clip(CircleShape)
                    .background(CreamRaised)
                    .border(1.dp, HairlineStrong, CircleShape)
                    .clickable(onClick = onDelete)
                    .testTag("pin_keypad_delete"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Hapus",
                    tint = InkMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
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
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(100),
        label = "keypad-press"
    )

    Box(
        modifier = Modifier
            .size(KeySize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(CreamRaised)
            .border(1.dp, HairlineStrong, CircleShape)
            .clickable {
                pressed = true
                onClick()
            }
            .testTag("pin_key_$digit"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = Ink
        )
    }
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(100)
            pressed = false
        }
    }
}

// ==================== Dialog Wrappers ====================

/**
 * Bingkai dialog PIN: krem, garis rambut, sudut bezel. Isinya mengikuti tinggi
 * konten (tidak lagi `fillMaxHeight(0.85f)` yang membuat dialog memenuhi layar).
 */
@Composable
private fun PinDialogFrame(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    EditorialDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .padding(Spacing.md),
            shape = RoundedCornerShape(CornerRadius.bezel),
            colors = CardDefaults.cardColors(containerColor = CreamRaised),
            border = BorderStroke(1.dp, Hairline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.card)
            ) {
                content()
            }
        }
    }
}

@Composable
fun PinSetupDialog(
    onDismiss: () -> Unit,
    onPinSet: () -> Unit
) {
    val context = LocalContext.current
    // Kalau PIN sudah ada, verifikasi PIN lama dulu.
    var oldPinVerified by remember { mutableStateOf(!PinUtils.hasPin(context)) }

    PinDialogFrame(onDismiss = onDismiss) {
        if (!oldPinVerified) {
            PinScreen(
                mode = PinMode.CHANGE_OLD,
                onSuccess = { oldPinVerified = true },
                onBack = onDismiss,
                embedded = true
            )
        } else {
            PinScreen(
                mode = PinMode.SETUP,
                onSuccess = { newPin ->
                    PinUtils.savePin(context, newPin)
                    onPinSet()
                },
                onBack = onDismiss,
                embedded = true
            )
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

/** H3: format sisa lockout jadi mm:ss (pembulatan ke atas). */
private fun formatLockRemaining(ms: Long): String {
    val totalSeconds = (ms + 999L) / 1000L
    return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

/**
 * H4a: dialog mematikan PIN — wajib verifikasi PIN lama dulu.
 * Memakai ulang PinScreen CHANGE_OLD supaya aturan lockout H3 ikut berlaku.
 */
@Composable
fun PinDisableDialog(
    onDismiss: () -> Unit,
    onPinDisabled: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    PinDialogFrame(onDismiss = onDismiss) {
        PinScreen(
            mode = PinMode.CHANGE_OLD,
            subtitle = "Verifikasi PIN lama untuk menonaktifkan PIN",
            onSuccess = { pin ->
                // PinScreen sudah memverifikasi; disablePinIfVerified adalah pagar kedua
                // di PinUtils. Dijalankan di IO agar hash PBKDF2 tidak nge-jank main thread.
                scope.launch {
                    val disabled = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        PinUtils.disablePinIfVerified(context, pin)
                    }
                    if (disabled) onPinDisabled() else onDismiss()
                }
            },
            onBack = onDismiss,
            embedded = true
        )
    }
}
