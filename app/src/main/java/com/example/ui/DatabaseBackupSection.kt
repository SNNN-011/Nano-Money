package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.BackupHelper
import com.example.util.BackupScheduler
import com.example.util.GoogleDriveHelper
import com.example.util.NotificationScheduler
import com.example.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale







@Composable
fun DatabaseBackupSection(
    isAutoBackupEnabled: Boolean,
    onAutoBackupEnabledChanged: (Boolean) -> Unit,
    autoBackupInterval: String,
    onAutoBackupIntervalChanged: (String) -> Unit,
    autoBackupHour: Int,
    onAutoBackupHourChanged: (Int) -> Unit,
    autoBackupMinute: Int,
    onAutoBackupMinuteChanged: (Int) -> Unit,
    autoBackupDayOfWeek: Int,
    onAutoBackupDayOfWeekChanged: (Int) -> Unit,
    backupList: List<File>,
    onBackupListChanged: (List<File>) -> Unit,
    connectedGoogleAccount: GoogleSignInAccount?,
    onConnectedGoogleAccountChanged: (GoogleSignInAccount?) -> Unit,
    isDriveProcessing: Boolean,
    onIsDriveProcessingChanged: (Boolean) -> Unit,
    driveBackupList: List<GoogleDriveHelper.DriveBackupFile>,
    onDriveBackupListChanged: (List<GoogleDriveHelper.DriveBackupFile>) -> Unit,
    selectedBackupToRestore: File?,
    onSelectedBackupToRestoreChange: (File?) -> Unit,
    selectedDriveBackupToRestore: GoogleDriveHelper.DriveBackupFile?,
    onSelectedDriveBackupToRestoreChange: (GoogleDriveHelper.DriveBackupFile?) -> Unit,
    selectedLocalBackupToUpload: File?,
    onSelectedLocalBackupToUploadChange: (File?) -> Unit,
    driveErrorDetailMessage: String?,
    onDriveErrorDetailMessageChange: (String?) -> Unit,
    onConnectGoogleDrive: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showBackupHelpInfo by remember { mutableStateOf(false) }
    var showDisconnectConfirmation by remember { mutableStateOf(false) }
    var backupFileToDelete by remember { mutableStateOf<File?>(null) }
    var driveBackupFileToDelete by remember { mutableStateOf<GoogleDriveHelper.DriveBackupFile?>(null) }
    var tempBackupHour by remember(autoBackupHour) { mutableStateOf(autoBackupHour) }
    var tempBackupMinute by remember(autoBackupMinute) { mutableStateOf(autoBackupMinute) }
    var tempBackupDayOfWeek by remember(autoBackupDayOfWeek) { mutableStateOf(autoBackupDayOfWeek) }

    fun getDayNameIndonesian(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            java.util.Calendar.SUNDAY -> "Minggu"
            java.util.Calendar.MONDAY -> "Senin"
            java.util.Calendar.TUESDAY -> "Selasa"
            java.util.Calendar.WEDNESDAY -> "Rabu"
            java.util.Calendar.THURSDAY -> "Kamis"
            java.util.Calendar.FRIDAY -> "Jumat"
            java.util.Calendar.SATURDAY -> "Sabtu"
            else -> "Minggu"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("database_backup_card"),
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
            // Header of Backup
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        text = "Cadangkan & Pulihkan Database",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        ),
                        color = GhostWhite
                    )
                }

                IconButton(
                    onClick = { showBackupHelpInfo = !showBackupHelpInfo },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Panduan Informasi Pencadangan",
                        tint = if (showBackupHelpInfo) SteelBlue else GhostWhite.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = "Amankan database catatan transaksi Anda dari kegagalan penyimpanan lokal. Salinan database dapat dicadangkan secara harian atau mingguan ke penyimpanan aman lokal.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                color = GhostWhite.copy(alpha = 0.55f)
            )

            if (showBackupHelpInfo) {
                AlertDialog(
                    onDismissRequest = { showBackupHelpInfo = false },
                    title = { Text("Mulai Pencadangan Data", color = GhostWhite, fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            text = "Fitur ini memungkinkan Anda menyimpan seluruh data transaksi ke Google Drive secara otomatis maupun manual, sehingga data tetap aman meski aplikasi dihapus atau ganti perangkat.\n\n• Backup Otomatis: Data dicadangkan secara berkala sesuai jadwal yang Anda pilih (Harian/Mingguan).\n\n⚠️ PENTING: Jika HP Anda membatasi latar belakang secara agresif (tipe Xiaomi, Oppo, Vivo, Samsung, dll.), sistem dapat mematikan back up otomatis saat aplikasi ditutup. Pastikan izin 'Mulai Otomatis' (Autostart) aktif dan atur mode hemat baterai ke 'Tanpa Batasan' (No Restrictions) untuk aplikasi ini.\n\n• Backup Manual: Ketuk tombol 'Cadangkan Sekarang' kapan saja untuk menyimpan data terbaru.\n• Pulihkan Data: Pilih file cadangan dari daftar untuk mengembalikan data ke kondisi tersebut.\n\nJika Anda tidak bisa login ke Google Drive atau mengalami masalah sinkronisasi, silakan hubungi developer untuk bantuan lebih lanjut.",
                            color = GhostWhite.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showBackupHelpInfo = false }) {
                            Text("Mengerti", color = SteelBlue, fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = MidnightAbyss,
                    titleContentColor = GhostWhite,
                    textContentColor = GhostWhite
                )
            }

            // Auto Backup Toggle Item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pencadangan Otomatis",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = GhostWhite
                    )
                    Text(
                        text = if (isAutoBackupEnabled) "Pencadangan otomatis aktif ($autoBackupInterval)" else "Cadangkan database secara berkala",
                        style = MaterialTheme.typography.bodySmall,
                        color = GhostWhite.copy(alpha = 0.5f)
                    )
                }
                PremiumSwitch(
                    checked = isAutoBackupEnabled,
                    onCheckedChange = { checked ->
                        onAutoBackupEnabledChanged(checked)
                        BackupScheduler.schedulePeriodicBackup(context, checked, autoBackupInterval, autoBackupHour, autoBackupMinute, autoBackupDayOfWeek, forceUpdate = true)
                        Toast.makeText(context, if (checked) "Pencadangan otomatis aktif!" else "Pencadangan otomatis mati!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Choose Backup Schedule (Interval and Time) under a single collapsible header if auto backup is enabled
            if (isAutoBackupEnabled) {
                val securityPrefs = remember { context.getSharedPreferences("security_prefs", android.content.Context.MODE_PRIVATE) }
                
                var lastLocalTime by remember { mutableStateOf(securityPrefs.getLong("last_auto_backup_local_time", 0L)) }
                var lastLocalStatus by remember { mutableStateOf(securityPrefs.getString("last_auto_backup_local_status", "Belum ada riwayat") ?: "Belum ada riwayat") }
                var lastDriveTime by remember { mutableStateOf(securityPrefs.getLong("last_auto_backup_drive_time", 0L)) }
                var lastDriveStatus by remember { mutableStateOf(securityPrefs.getString("last_auto_backup_drive_status", "Belum ada riwayat") ?: "Belum ada riwayat") }

                DisposableEffect(securityPrefs) {
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                        when (key) {
                            "last_auto_backup_local_time" -> {
                                lastLocalTime = prefs.getLong(key, 0L)
                            }
                            "last_auto_backup_local_status" -> {
                                lastLocalStatus = prefs.getString(key, "Belum ada riwayat") ?: "Belum ada riwayat"
                            }
                            "last_auto_backup_drive_time" -> {
                                lastDriveTime = prefs.getLong(key, 0L)
                            }
                            "last_auto_backup_drive_status" -> {
                                lastDriveStatus = prefs.getString(key, "Belum ada riwayat") ?: "Belum ada riwayat"
                            }
                        }
                    }
                    securityPrefs.registerOnSharedPreferenceChangeListener(listener)
                    onDispose {
                        securityPrefs.unregisterOnSharedPreferenceChangeListener(listener)
                    }
                }

                fun formatTimestamp(timestamp: Long): String {
                    if (timestamp == 0L) return "Belum pernah"
                    val sdf = java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale("id", "ID"))
                    return sdf.format(java.util.Date(timestamp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GhostWhite.copy(alpha = 0.03f), shape = RoundedCornerShape(12.dp))
                        .border(1.dp, GhostWhite.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = SteelBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Riwayat Pencadangan Otomatis",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = SteelBlue
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pencadangan Lokal:",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                    color = GhostWhite.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = lastLocalStatus ?: "Belum ada riwayat",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = if (lastLocalStatus != null && lastLocalStatus.startsWith("Sukses")) Color.Green.copy(alpha = 0.8f) else if (lastLocalStatus != null && lastLocalStatus.startsWith("Gagal")) Color.Red.copy(alpha = 0.8f) else GhostWhite.copy(alpha = 0.5f)
                                )
                            }
                            Text(
                                text = formatTimestamp(lastLocalTime),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = GhostWhite.copy(alpha = 0.4f)
                            )
                        }

                        HorizontalDivider(color = GhostWhite.copy(alpha = 0.05f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Mengunggah Cloud (Drive):",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                    color = GhostWhite.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = lastDriveStatus ?: "Belum ada riwayat",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = if (lastDriveStatus != null && lastDriveStatus.startsWith("Sukses")) Color.Green.copy(alpha = 0.8f) else if (lastDriveStatus != null && lastDriveStatus.startsWith("Gagal")) Color.Red.copy(alpha = 0.8f) else GhostWhite.copy(alpha = 0.5f)
                                )
                            }
                            Text(
                                text = formatTimestamp(lastDriveTime),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = GhostWhite.copy(alpha = 0.4f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                var showJadwalCollapse by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Title/Row for Jadwal Pencadangan (Collapsible)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showJadwalCollapse = !showJadwalCollapse }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Jadwal Pencadangan",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = GhostWhite
                                )
                                Text(
                                    text = if (autoBackupInterval == "daily") {
                                        "Setiap Hari pukul ${String.format("%02d:%02d", autoBackupHour, autoBackupMinute)} WIB"
                                    } else {
                                        "Setiap Hari ${getDayNameIndonesian(autoBackupDayOfWeek)} pukul ${String.format("%02d:%02d", autoBackupHour, autoBackupMinute)} WIB"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GhostWhite.copy(alpha = 0.5f)
                                )
                            }
                            IconButton(
                                onClick = { showJadwalCollapse = !showJadwalCollapse },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (showJadwalCollapse) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Pilih Jadwal Pencadangan",
                                    tint = GhostWhite.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    // Collapsed content show Interval and Waktu side by side
                    if (showJadwalCollapse) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MidnightAbyss.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp))
                                    .border(1.dp, GhostWhite.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Left Side: Siklus Pencadangan Switch (Daily / Weekly Group) with smooth animations
                                Column(
                                    modifier = Modifier.weight(1.2f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Siklus",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = GhostWhite.copy(alpha = 0.7f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(32.dp)
                                            .background(MidnightAbyss, shape = RoundedCornerShape(16.dp))
                                            .border(1.dp, GhostWhite.copy(alpha = 0.15f), shape = RoundedCornerShape(16.dp))
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                val newInterval = if (autoBackupInterval == "daily") "weekly" else "daily"
                                                onAutoBackupIntervalChanged(newInterval)
                                                BackupScheduler.schedulePeriodicBackup(context, true, newInterval, autoBackupHour, autoBackupMinute, autoBackupDayOfWeek, forceUpdate = true)
                                                Toast.makeText(
                                                    context, 
                                                    "Siklus pencadangan diatur: ${if (newInterval == "daily") "Harian" else "Mingguan"}", 
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                    ) {
                                        // Animated sliding thumb background
                                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                            val width = maxWidth
                                            val thumbWidth = width / 2
                                            val targetOffset = if (autoBackupInterval == "weekly") thumbWidth else 0.dp
                                            val animatedOffset by animateDpAsState(
                                                targetValue = targetOffset,
                                                animationSpec = spring(stiffness = Spring.StiffnessLow),
                                                label = "thumbOffset"
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .offset(x = animatedOffset)
                                                    .width(thumbWidth)
                                                    .fillMaxHeight()
                                                    .padding(2.dp)
                                                    .background(
                                                        brush = Brush.verticalGradient(
                                                            colors = listOf(SteelBlue, SteelBlue.copy(alpha = 0.7f))
                                                        ),
                                                        shape = RoundedCornerShape(14.dp)
                                                    )
                                                    .border(0.5.dp, GhostWhite.copy(alpha = 0.25f), shape = RoundedCornerShape(14.dp))
                                            )
                                        }

                                        // Foreground text elements layer
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier.weight(1f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val textColor by animateColorAsState(
                                                    targetValue = if (autoBackupInterval == "daily") MidnightAbyss else GhostWhite.copy(alpha = 0.5f),
                                                    label = "dailyTextColor"
                                                )
                                                Text(
                                                    text = "HARIAN",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    ),
                                                    color = textColor
                                                )
                                            }
                                            Box(
                                                modifier = Modifier.weight(1f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val textColor by animateColorAsState(
                                                    targetValue = if (autoBackupInterval == "weekly") MidnightAbyss else GhostWhite.copy(alpha = 0.5f),
                                                    label = "weeklyTextColor"
                                                )
                                                Text(
                                                    text = "MINGGUAN",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    ),
                                                    color = textColor
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Right Side: Time Picker (Hour & Minute with tiny arrows and direct keyboard input)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Sesi Mulai",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = GhostWhite.copy(alpha = 0.7f)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        // Hour Input Column
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    tempBackupHour = (tempBackupHour + 1) % 24
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowUp,
                                                    contentDescription = "Tambah Jam",
                                                    tint = SteelBlue,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            // Editable Hour Box
                                            var hourTextState by remember(tempBackupHour) { mutableStateOf(String.format("%02d", tempBackupHour)) }
                                            BasicTextField(
                                                value = hourTextState,
                                                onValueChange = { input ->
                                                    val filtered = input.filter { it.isDigit() }.take(2)
                                                    hourTextState = filtered
                                                    if (filtered.isNotEmpty()) {
                                                        val num = filtered.toIntOrNull()
                                                        if (num != null && num in 0..23) {
                                                            tempBackupHour = num
                                                        }
                                                    }
                                                },
                                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                    color = GhostWhite,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                                ),
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Number,
                                                    imeAction = ImeAction.Done
                                                ),
                                                singleLine = true,
                                                cursorBrush = SolidColor(SteelBlue),
                                                modifier = Modifier
                                                    .width(36.dp)
                                                    .height(28.dp)
                                                    .background(MidnightAbyss, shape = RoundedCornerShape(6.dp))
                                                    .border(1.dp, GhostWhite.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp))
                                                    .wrapContentHeight(Alignment.CenterVertically)
                                            )

                                            IconButton(
                                                onClick = {
                                                    tempBackupHour = if (tempBackupHour - 1 < 0) 23 else tempBackupHour - 1
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Kurang Jam",
                                                    tint = SteelBlue,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        // Separator Colon
                                        Text(
                                            text = ":",
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                            color = GhostWhite,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )

                                        // Minute Input Column
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    tempBackupMinute = (tempBackupMinute + 1) % 60
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowUp,
                                                    contentDescription = "Tambah Menit",
                                                    tint = SteelBlue,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            // Editable Minute Box
                                            var minuteTextState by remember(tempBackupMinute) { mutableStateOf(String.format("%02d", tempBackupMinute)) }
                                            BasicTextField(
                                                value = minuteTextState,
                                                onValueChange = { input ->
                                                    val filtered = input.filter { it.isDigit() }.take(2)
                                                    minuteTextState = filtered
                                                    if (filtered.isNotEmpty()) {
                                                        val num = filtered.toIntOrNull()
                                                        if (num != null && num in 0..59) {
                                                            tempBackupMinute = num
                                                        }
                                                    }
                                                },
                                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                    color = GhostWhite,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                                ),
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Number,
                                                    imeAction = ImeAction.Done
                                                ),
                                                singleLine = true,
                                                cursorBrush = SolidColor(SteelBlue),
                                                modifier = Modifier
                                                    .width(36.dp)
                                                    .height(28.dp)
                                                    .background(MidnightAbyss, shape = RoundedCornerShape(6.dp))
                                                    .border(1.dp, GhostWhite.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp))
                                                    .wrapContentHeight(Alignment.CenterVertically)
                                            )

                                            IconButton(
                                                onClick = {
                                                    tempBackupMinute = if (tempBackupMinute - 1 < 0) 59 else tempBackupMinute - 1
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Kurang Menit",
                                                    tint = SteelBlue,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (autoBackupInterval == "weekly") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MidnightAbyss.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp))
                                        .border(1.dp, GhostWhite.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Hari Pencadangan Mingguan",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = GhostWhite.copy(alpha = 0.7f)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val daysList = listOf(
                                            java.util.Calendar.MONDAY to "Sen",
                                            java.util.Calendar.TUESDAY to "Sel",
                                            java.util.Calendar.WEDNESDAY to "Rab",
                                            java.util.Calendar.THURSDAY to "Kam",
                                            java.util.Calendar.FRIDAY to "Jum",
                                            java.util.Calendar.SATURDAY to "Sab",
                                            java.util.Calendar.SUNDAY to "Min"
                                        )
                                        daysList.forEach { (dayVal, label) ->
                                            val isSelected = tempBackupDayOfWeek == dayVal
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(
                                                        color = if (isSelected) SteelBlue else MidnightAbyss,
                                                        shape = RoundedCornerShape(18.dp)
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSelected) SteelBlue else GhostWhite.copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(18.dp)
                                                    )
                                                    .clickable {
                                                        tempBackupDayOfWeek = dayVal
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    ),
                                                    color = if (isSelected) MidnightAbyss else GhostWhite
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            val hasBackupChanges = tempBackupHour != autoBackupHour || tempBackupMinute != autoBackupMinute || (autoBackupInterval == "weekly" && tempBackupDayOfWeek != autoBackupDayOfWeek)
                            Button(
                                onClick = {
                                    onAutoBackupHourChanged(tempBackupHour)
                                    onAutoBackupMinuteChanged(tempBackupMinute)
                                    if (autoBackupInterval == "weekly") {
                                        onAutoBackupDayOfWeekChanged(tempBackupDayOfWeek)
                                    }
                                    BackupScheduler.schedulePeriodicBackup(context, true, autoBackupInterval, tempBackupHour, tempBackupMinute, if (autoBackupInterval == "weekly") tempBackupDayOfWeek else autoBackupDayOfWeek, forceUpdate = true)
                                    val timeStr = String.format("%02d:%02d", tempBackupHour, tempBackupMinute)
                                    val toastMsg = if (autoBackupInterval == "weekly") {
                                        "Jadwal pencadangan otomatis diatur ke hari ${getDayNameIndonesian(tempBackupDayOfWeek)} pukul $timeStr WIB!"
                                    } else {
                                        "Jadwal pencadangan otomatis diatur ke pukul $timeStr WIB!"
                                    }
                                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (hasBackupChanges) SteelBlue else TranslucentGlass,
                                    contentColor = if (hasBackupChanges) MidnightAbyss else GhostWhite
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = if (!hasBackupChanges) BorderStroke(1.dp, GhostWhite.copy(alpha = 0.15f)) else null,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (hasBackupChanges) Icons.Default.Check else Icons.Default.DateRange,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (hasBackupChanges) {
                                            "SIMPAN PERUBAHAN JADWAL"
                                        } else {
                                            if (autoBackupInterval == "weekly") {
                                                "JADWAL AKTIF (${getDayNameIndonesian(autoBackupDayOfWeek)}, Pukul ${String.format("%02d:%02d", autoBackupHour, autoBackupMinute)} WIB)"
                                            } else {
                                                "JADWAL AKTIF (Pukul ${String.format("%02d:%02d", autoBackupHour, autoBackupMinute)} WIB)"
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(com.example.ui.theme.SteelBlue.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp))
                        .border(1.dp, com.example.ui.theme.SteelBlue.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = com.example.ui.theme.SteelBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Optimasi Latar Belakang & Baterai",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = com.example.ui.theme.SteelBlue)
                            )
                        }

                        Text(
                            text = "Beberapa sistem HP (terutama Xiaomi, Oppo, Vivo, Samsung, dll.) secara agresif membatasi aktivitas latar belakang untuk menghemat baterai, yang dapat menghentikan proses backup otomatis saat aplikasi ditutup.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                            color = GhostWhite.copy(alpha = 0.75f)
                        )

                        Text(
                            text = "💡 Petunjuk: Pastikan izin 'Mulai Otomatis' (Autostart) aktif untuk aplikasi ini dan atur pembatasan baterai ke 'Tanpa Batasan' (No Restrictions) di pengaturan sistem.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 15.sp),
                            color = GhostWhite.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Button(
                            onClick = {
                                try {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Tidak dapat membuka pengaturan baterai secara otomatis. Silakan buka Pengaturan HP > Aplikasi > Penghemat Baterai secara manual.", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(34.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = com.example.ui.theme.SteelBlue.copy(alpha = 0.15f),
                                contentColor = GhostWhite
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "BUKA PENGATURAN BATERAI HP",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = GhostWhite.copy(alpha = 0.08f))

            // Buttons Layout for immediate action
            PremiumButton(
                text = "CADANGKAN SEKARANG (MANUAL)",
                onClick = {
                    coroutineScope.launch {
                        when (val result = BackupHelper.performBackup(context, isAuto = false)) {
                            is BackupHelper.BackupResult.Success -> {
                                onBackupListChanged(BackupHelper.getBackups(context))
                                Toast.makeText(context, "Database berhasil dicadangkan secara lokal!", Toast.LENGTH_SHORT).show()
                                
                                // Jika akun Google terhubung, unggah juga ke Drive otomatis
                                if (connectedGoogleAccount != null) {
                                    Toast.makeText(context, "Mengunggah cadangan ke Google Drive...", Toast.LENGTH_SHORT).show()
                                    onIsDriveProcessingChanged(true)
                                    val backupDir = BackupHelper.getBackupDirectory(context)
                                    val backupFile = java.io.File(backupDir, result.fileName)
                                    when (val uploadRes = GoogleDriveHelper.uploadBackupToDrive(context, backupFile)) {
                                        is GoogleDriveHelper.DriveResult.Success -> {
                                            Toast.makeText(context, "Berhasil disinkronkan ke Google Drive!", Toast.LENGTH_LONG).show()
                                            val listRes = GoogleDriveHelper.listBackupsFromDrive(context)
                                            if (listRes is GoogleDriveHelper.DriveResult.Success) {
                                                onDriveBackupListChanged(listRes.data)
                                            }
                                        }
                                        is GoogleDriveHelper.DriveResult.Error -> {
                                            Toast.makeText(context, "Gagal mengunggah ke Drive: ${uploadRes.message}", Toast.LENGTH_LONG).show()
                                            onDriveErrorDetailMessageChange(uploadRes.message)
                                        }
                                    }
                                    onIsDriveProcessingChanged(false)
                                }
                            }
                            is BackupHelper.BackupResult.Error -> {
                                Toast.makeText(context, "Gagal mencadangkan database: ${result.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                isActive = true,
                icon = Icons.Default.Download,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Backup copy list
            Text(
                text = "Salinan Cadangan yang Tersedia (${backupList.size}):",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = GhostWhite
            )

            if (backupList.isEmpty()) {
                Text(
                    text = "Belum ada salinan cadangan. Silakan buat cadangan manual atau aktifkan pencadangan otomatis.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = GhostWhite.copy(alpha = 0.35f)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    backupList.take(5).forEach { file ->
                        val format = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
                        val dateStr = format.format(java.util.Date(file.lastModified()))
                        val isAutoFromPrefix = file.name.startsWith("auto_")
                        val sizeInKb = file.length() / 1024

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(GhostWhite.copy(alpha = 0.03f), shape = RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isAutoFromPrefix) SteelBlue.copy(alpha = 0.15f) else Color.Magenta.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isAutoFromPrefix) "Otomatis" else "Manual",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                            color = if (isAutoFromPrefix) SteelBlue else Color.Magenta
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$sizeInKb KB",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = GhostWhite.copy(alpha = 0.4f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = GhostWhite
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (connectedGoogleAccount != null) {
                                    IconButton(
                                        onClick = { onSelectedLocalBackupToUploadChange(file) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudUpload,
                                            contentDescription = "Unggah ke Google Drive",
                                            tint = SteelBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                PremiumButton(
                                    text = "PULIHKAN",
                                    onClick = { onSelectedBackupToRestoreChange(file) },
                                    isActive = true,
                                    fillMaxWidth = false,
                                    horizontalPadding = 10.dp,
                                    verticalPadding = 4.dp
                                )

                                IconButton(
                                    onClick = {
                                        backupFileToDelete = file
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus Cadangan",
                                        tint = Color.Red.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Google Drive Cloud Sync Section
            HorizontalDivider(color = GhostWhite.copy(alpha = 0.08f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (connectedGoogleAccount != null) Icons.Default.CloudDone else Icons.Default.Cloud,
                        contentDescription = null,
                        tint = if (connectedGoogleAccount != null) SteelBlue else GhostWhite.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (connectedGoogleAccount != null) "Terhubung Cloud ☁️" else "Sinkronisasi Google Drive",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                        color = GhostWhite
                    )
                }

                if (connectedGoogleAccount != null) {
                    IconButton(
                        onClick = { showDisconnectConfirmation = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Putuskan Google Drive",
                            tint = Color.Red.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (showDisconnectConfirmation) {
                AlertDialog(
                    onDismissRequest = { showDisconnectConfirmation = false },
                    title = { Text("Putuskan Koneksi Google Drive?", color = GhostWhite, fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            text = "Anda akan keluar dari akun Google yang terhubung. Backup otomatis akan berhenti dan Anda perlu login ulang untuk menggunakan fitur sinkronisasi Drive.",
                            color = GhostWhite.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDisconnectConfirmation = false
                                GoogleDriveHelper.signOut(context) {
                                    onConnectedGoogleAccountChanged(null)
                                    onDriveBackupListChanged(emptyList())
                                    Toast.makeText(context, "Akses Google Drive terputus.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Putuskan", color = Color.Red.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDisconnectConfirmation = false }) {
                            Text("Batal", color = GhostWhite.copy(alpha = 0.6f))
                        }
                    },
                    containerColor = MidnightAbyss,
                    titleContentColor = GhostWhite,
                    textContentColor = GhostWhite
                )
            }

            if (connectedGoogleAccount == null) {
                Text(
                    text = "Hubungkan akun Google Drive Anda untuk secara manual atau otomatis mengunggah cadangan database Anda. Cadangan eksternal terisolasi ini dijamin aman dan aman dari uninstall.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    color = GhostWhite.copy(alpha = 0.55f)
                )

                PremiumButton(
                    text = "HUBUNGKAN GOOGLE DRIVE",
                    onClick = onConnectGoogleDrive,
                    isActive = true,
                    icon = Icons.Default.Cloud,
                    modifier = Modifier.fillMaxWidth().testTag("connect_google_drive_button")
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Akun: ${connectedGoogleAccount.email ?: "Terhubung"}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = SteelBlue),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PremiumButton(
                            text = "UNGGAH MANUAL LOKAL TERBARU",
                            onClick = {
                                coroutineScope.launch {
                                    onIsDriveProcessingChanged(true)
                                    val latestLocal = BackupHelper.getBackups(context).firstOrNull()
                                    if (latestLocal != null) {
                                        when (val uploaded = GoogleDriveHelper.uploadBackupToDrive(context, latestLocal)) {
                                            is GoogleDriveHelper.DriveResult.Success -> {
                                                Toast.makeText(context, "Sukses mengunggah cadangan terbaru ke Drive!", Toast.LENGTH_LONG).show()
                                                val listRes = GoogleDriveHelper.listBackupsFromDrive(context)
                                                if (listRes is GoogleDriveHelper.DriveResult.Success) {
                                                    onDriveBackupListChanged(listRes.data)
                                                }
                                            }
                                            is GoogleDriveHelper.DriveResult.Error -> {
                                                onDriveErrorDetailMessageChange(uploaded.message)
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Silakan buat cadangan lokal terlebih dahulu sebelum mengunggah.", Toast.LENGTH_LONG).show()
                                    }
                                    onIsDriveProcessingChanged(false)
                                }
                            },
                            isActive = !isDriveProcessing,
                            icon = Icons.Default.CloudUpload,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    onIsDriveProcessingChanged(true)
                                    when (val listRes = GoogleDriveHelper.listBackupsFromDrive(context)) {
                                        is GoogleDriveHelper.DriveResult.Success -> {
                                            onDriveBackupListChanged(listRes.data)
                                            Toast.makeText(context, "Berhasil memuat daftar cadangan Google Drive.", Toast.LENGTH_SHORT).show()
                                        }
                                        is GoogleDriveHelper.DriveResult.Error -> {
                                            onDriveErrorDetailMessageChange(listRes.message)
                                        }
                                    }
                                    onIsDriveProcessingChanged(false)
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Muat Ulang Drive",
                                tint = GhostWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (isDriveProcessing) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = SteelBlue)
                        }
                    }

                    if (driveBackupList.isEmpty() && !isDriveProcessing) {
                        Text(
                            text = "Belum ada cadangan di Google Drive Anda.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = GhostWhite.copy(alpha = 0.35f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            driveBackupList.forEach { driveFile ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(GhostWhite.copy(alpha = 0.03f), shape = RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = driveFile.name,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = GhostWhite,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Ukuran: ${driveFile.sizeBytes / 1024} KB | ID: ${driveFile.id.take(8)}...",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                            color = GhostWhite.copy(alpha = 0.45f)
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        PremiumButton(
                                            text = "UNDUH & PULIHKAN",
                                            onClick = { onSelectedDriveBackupToRestoreChange(driveFile) },
                                            isActive = true,
                                            fillMaxWidth = false,
                                            horizontalPadding = 8.dp,
                                            verticalPadding = 4.dp
                                        )

                                        IconButton(
                                            onClick = {
                                                driveBackupFileToDelete = driveFile
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Hapus Cadangan Drive",
                                                tint = Color.Red.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (backupFileToDelete != null) {
        AlertDialog(
            onDismissRequest = { backupFileToDelete = null },
            containerColor = MidnightAbyss,
            title = {
                Text(
                    text = "Hapus Cadangan Lokal",
                    color = GhostWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus salinan cadangan lokal '${backupFileToDelete?.name}'? Berkas yang dihapus tidak dapat dipulihkan lagi.",
                    color = GhostWhite.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumButton(
                        text = "Batal",
                        onClick = { backupFileToDelete = null },
                        isActive = false,
                        modifier = Modifier.weight(1f),
                        horizontalPadding = 8.dp,
                        verticalPadding = 6.dp
                    )
                    PremiumButton(
                        text = "HAPUS",
                        onClick = {
                            val file = backupFileToDelete
                            backupFileToDelete = null
                            if (file != null) {
                                val deleted = file.delete()
                                if (deleted) {
                                    onBackupListChanged(BackupHelper.getBackups(context))
                                    Toast.makeText(context, "Salinan cadangan dihapus.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Gagal menghapus salinan.", Toast.LENGTH_SHORT).show()
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
        )
    }

    if (driveBackupFileToDelete != null) {
        AlertDialog(
            onDismissRequest = { driveBackupFileToDelete = null },
            containerColor = MidnightAbyss,
            title = {
                Text(
                    text = "Hapus Cadangan Google Drive",
                    color = GhostWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus salinan cadangan '${driveBackupFileToDelete?.name}' dari akun Google Drive Anda? Tindakan ini bersifat permanen.",
                    color = GhostWhite.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumButton(
                        text = "Batal",
                        onClick = { driveBackupFileToDelete = null },
                        isActive = false,
                        modifier = Modifier.weight(1f),
                        horizontalPadding = 8.dp,
                        verticalPadding = 6.dp
                    )
                    PremiumButton(
                        text = "HAPUS DARI CLOUD",
                        onClick = {
                            val driveFile = driveBackupFileToDelete
                            driveBackupFileToDelete = null
                            if (driveFile != null) {
                                coroutineScope.launch {
                                    onIsDriveProcessingChanged(true)
                                    when (val delRes = GoogleDriveHelper.deleteBackupFromDrive(context, driveFile.id)) {
                                        is GoogleDriveHelper.DriveResult.Success -> {
                                            Toast.makeText(context, "Berhasil menghapus berkas di Google Drive.", Toast.LENGTH_SHORT).show()
                                            val syncList = GoogleDriveHelper.listBackupsFromDrive(context)
                                            if (syncList is GoogleDriveHelper.DriveResult.Success) {
                                                onDriveBackupListChanged(syncList.data)
                                            }
                                        }
                                        is GoogleDriveHelper.DriveResult.Error -> {
                                            onDriveErrorDetailMessageChange(delRes.message)
                                        }
                                    }
                                    onIsDriveProcessingChanged(false)
                                }
                            }
                        },
                        isActive = true,
                        modifier = Modifier.weight(1.3f),
                        horizontalPadding = 8.dp,
                        verticalPadding = 6.dp
                    )
                }
            }
        )
    }
}
