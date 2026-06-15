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
fun UnifiedExportCard(
    isWideScreen: Boolean,
    availableMonths: List<YearMonth>,
    selectedMonths: Set<YearMonth>,
    onSelectedMonthsChanged: (Set<YearMonth>) -> Unit,
    isMonthsExpanded: Boolean,
    onIsMonthsExpandedChanged: (Boolean) -> Unit,
    onExportPdfFile: (List<YearMonth>) -> Unit,
    onExportCsvFile: (List<YearMonth>) -> Unit,
    onImportCsvFile: () -> Unit
) {
    var showPdfHelpInfo by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = TranslucentForm.copy(alpha = 0.65f)),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(GhostWhite.copy(alpha = 0.15f), GhostWhite.copy(alpha = 0.02f))
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header inside card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onIsMonthsExpandedChanged(!isMonthsExpanded) }.weight(1f)
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
                        text = "Pilih Periode Laporan Keuangan",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        ),
                        color = GhostWhite
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { showPdfHelpInfo = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info Ekspor PDF",
                            tint = GhostWhite.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Icon(
                        imageVector = if (isMonthsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isMonthsExpanded) "Sembunyikan" else "Tampilkan Semua",
                        tint = SteelBlue,
                        modifier = Modifier.size(20.dp).clickable { onIsMonthsExpandedChanged(!isMonthsExpanded) }
                    )
                }
            }

            if (showPdfHelpInfo) {
                AlertDialog(
                    onDismissRequest = { showPdfHelpInfo = false },
                    title = { Text("Informasi Ekspor PDF", color = GhostWhite, fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            text = "Ekspor laporan keuangan Anda dalam format PDF yang rapi dan profesional.\n\n• Pilih satu atau beberapa bulan sekaligus untuk dijadikan laporan.\n• Setiap bulan ditampilkan di halaman terpisah agar mudah dibaca.\n• Laporan mencakup ringkasan statistik, grafik arus kas, pie chart kategori pengeluaran, dan tabel transaksi lengkap.\n• File PDF langsung tersimpan di penyimpanan perangkat Anda dan bisa dibagikan kapan saja.",
                            color = GhostWhite.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showPdfHelpInfo = false }) {
                            Text("Mengerti", color = SteelBlue, fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = MidnightAbyss,
                    titleContentColor = GhostWhite,
                    textContentColor = GhostWhite
                )
            }

            if (isMonthsExpanded) {
                Text(
                    text = "Tentukan bulan transaksi yang ingin di-ekspor atau di-cetak sebagai laporan keuangan. Anda bisa memilih satu, beberapa, atau semua bulan sekaligus.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                    color = GhostWhite.copy(alpha = 0.55f)
                )

                // Month Selector Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PremiumButton(
                        text = "Pilih Semua",
                        onClick = { onSelectedMonthsChanged(availableMonths.toSet()) },
                        isActive = true,
                        fillMaxWidth = false,
                        testTag = "select_all_months",
                        horizontalPadding = 12.dp,
                        verticalPadding = 6.dp
                    )

                    PremiumButton(
                        text = "Kosongkan",
                        onClick = { onSelectedMonthsChanged(emptySet()) },
                        isActive = false,
                        fillMaxWidth = false,
                        testTag = "clear_all_months",
                        horizontalPadding = 12.dp,
                        verticalPadding = 6.dp
                    )
                }

                // Render Selectable Months
                val chunkedMonths = availableMonths.chunked(if (isWideScreen) 3 else 2)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    chunkedMonths.forEach { rowMonths ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowMonths.forEach { month ->
                                MonthSelectChip(
                                    month = month,
                                    isSelected = selectedMonths.contains(month),
                                    onClick = {
                                        val updated = if (selectedMonths.contains(month)) {
                                            selectedMonths - month
                                        } else {
                                            selectedMonths + month
                                        }
                                        onSelectedMonthsChanged(updated)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            val emptySpaces = (if (isWideScreen) 3 else 2) - rowMonths.size
                            if (emptySpaces > 0) {
                                repeat(emptySpaces) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } else {
                // Collapsed state month summary indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onIsMonthsExpandedChanged(true) }
                        .background(GhostWhite.copy(alpha = 0.03f), shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val formatter = remember { DateTimeFormatter.ofPattern("MMM yyyy", Locale("id", "ID")) }
                    val displaySelectedStr = if (selectedMonths.isEmpty()) {
                        "Tidak ada bulan terpilih"
                    } else {
                        selectedMonths.sorted().joinToString(", ") { it.format(formatter) }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Periode terpilih (${selectedMonths.size} bulan):",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = SteelBlue
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = displaySelectedStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = GhostWhite.copy(alpha = 0.7f)
                        )
                    }

                    Text(
                        text = "Ubah",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = SteelBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = GhostWhite.copy(alpha = 0.08f))

            // Buttons Layout (PDF Wide, CSV side-by-side)
            PremiumButton(
                text = "UNDUH LAPORAN PDF",
                onClick = { onExportPdfFile(selectedMonths.toList()) },
                isActive = selectedMonths.isNotEmpty(),
                icon = Icons.Default.PictureAsPdf,
                testTag = "export_pdf_button",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PremiumButton(
                    text = "Ekspor Excel",
                    onClick = { onExportCsvFile(selectedMonths.toList()) },
                    isActive = selectedMonths.isNotEmpty(),
                    icon = Icons.Default.Assessment,
                    testTag = "export_csv_button",
                    modifier = Modifier.weight(1f),
                    horizontalPadding = 12.dp,
                    verticalPadding = 10.dp
                )

                PremiumButton(
                    text = "Impor Excel",
                    onClick = onImportCsvFile,
                    isActive = true,
                    icon = Icons.Default.Download,
                    testTag = "import_csv_button",
                    modifier = Modifier.weight(1f),
                    horizontalPadding = 12.dp,
                    verticalPadding = 10.dp
                )
            }
        }
    }
}

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

@Composable
fun NotificationReminderSection(
    isReminderEnabled: Boolean,
    onReminderToggle: (Boolean) -> Unit,
    reminderHour: Int,
    onReminderHourChanged: (Int) -> Unit,
    reminderMinute: Int,
    onReminderMinuteChanged: (Int) -> Unit
) {
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
                    text = "Pengingat Harian Keuangan",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    ),
                    color = GhostWhite
                )
            }

            Text(
                text = "Aktifkan pengingat notifikasi harian untuk membantu Anda konsisten mencatat keuangan pribadi setiap sore/malam.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                color = GhostWhite.copy(alpha = 0.55f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Aktifkan Pengingat",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = GhostWhite
                    )
                    Text(
                        text = "Kirim notifikasi pukul ${String.format("%02d:%02d", reminderHour, reminderMinute)} WIB",
                        style = MaterialTheme.typography.bodySmall,
                        color = GhostWhite.copy(alpha = 0.5f)
                    )
                }

                if (isReminderEnabled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        // Hour Column
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            IconButton(
                                onClick = {
                                    val newHour = (reminderHour + 1) % 24
                                    onReminderHourChanged(newHour)
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Tambah Jam",
                                    tint = SteelBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Editable Hour Box
                            var hourTextState by remember(reminderHour) { mutableStateOf(String.format("%02d", reminderHour)) }
                            BasicTextField(
                                value = hourTextState,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }.take(2)
                                    hourTextState = filtered
                                    if (filtered.isNotEmpty()) {
                                        val num = filtered.toIntOrNull()
                                        if (num != null && num in 0..23) {
                                            onReminderHourChanged(num)
                                        }
                                    }
                                },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
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
                                    .width(32.dp)
                                    .height(24.dp)
                                    .background(MidnightAbyss, shape = RoundedCornerShape(6.dp))
                                    .border(1.dp, GhostWhite.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp))
                                    .wrapContentHeight(Alignment.CenterVertically)
                            )

                            IconButton(
                                onClick = {
                                    val newHour = if (reminderHour - 1 < 0) 23 else reminderHour - 1
                                    onReminderHourChanged(newHour)
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Kurang Jam",
                                    tint = SteelBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Separator Colon
                        Text(
                            text = ":",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = GhostWhite,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )

                        // Minute Column
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            IconButton(
                                onClick = {
                                    val newMin = (reminderMinute + 1) % 60
                                    onReminderMinuteChanged(newMin)
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Tambah Menit",
                                    tint = SteelBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Editable Minute Box
                            var minuteTextState by remember(reminderMinute) { mutableStateOf(String.format("%02d", reminderMinute)) }
                            BasicTextField(
                                value = minuteTextState,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }.take(2)
                                    minuteTextState = filtered
                                    if (filtered.isNotEmpty()) {
                                        val num = filtered.toIntOrNull()
                                        if (num != null && num in 0..59) {
                                            onReminderMinuteChanged(num)
                                        }
                                    }
                                },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
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
                                    .width(32.dp)
                                    .height(24.dp)
                                    .background(MidnightAbyss, shape = RoundedCornerShape(6.dp))
                                    .border(1.dp, GhostWhite.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp))
                                    .wrapContentHeight(Alignment.CenterVertically)
                            )

                            IconButton(
                                onClick = {
                                    val newMin = if (reminderMinute - 1 < 0) 59 else reminderMinute - 1
                                    onReminderMinuteChanged(newMin)
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Kurang Menit",
                                    tint = SteelBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                PremiumSwitch(
                    checked = isReminderEnabled,
                    onCheckedChange = { checked ->
                        onReminderToggle(checked)
                    }
                )
            }
        }
    }
}

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
                            text = "Fitur ini memungkinkan Anda menyimpan seluruh data transaksi ke Google Drive secara otomatis maupun manual, sehingga data tetap aman meski aplikasi dihapus atau ganti perangkat.\n\n• Backup Otomatis: Data dicadangkan secara berkala sesuai jadwal yang Anda pilih (Harian/Mingguan).\n• Backup Manual: Ketuk tombol 'Cadangkan Sekarang' kapan saja untuk menyimpan data terbaru.\n• Pulihkan Data: Pilih file cadangan dari daftar untuk mengembalikan data ke kondisi tersebut.\n\nJika Anda tidak bisa login ke Google Drive atau mengalami masalah sinkronisasi, silakan hubungi developer untuk bantuan lebih lanjut.",
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
                        BackupScheduler.schedulePeriodicBackup(context, checked, autoBackupInterval, autoBackupHour, autoBackupMinute)
                        Toast.makeText(context, if (checked) "Pencadangan otomatis aktif!" else "Pencadangan otomatis mati!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Choose Backup Schedule (Interval and Time) under a single collapsible header if auto backup is enabled
            if (isAutoBackupEnabled) {
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
                                    text = "Setiap ${if (autoBackupInterval == "daily") "Hari" else "Minggu"} pukul ${String.format("%02d:%02d", autoBackupHour, autoBackupMinute)} WIB",
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
                                            BackupScheduler.schedulePeriodicBackup(context, true, newInterval, autoBackupHour, autoBackupMinute)
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
                                                val newHour = (autoBackupHour + 1) % 24
                                                onAutoBackupHourChanged(newHour)
                                                BackupScheduler.schedulePeriodicBackup(context, true, autoBackupInterval, newHour, autoBackupMinute)
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
                                        var hourTextState by remember(autoBackupHour) { mutableStateOf(String.format("%02d", autoBackupHour)) }
                                        BasicTextField(
                                            value = hourTextState,
                                            onValueChange = { input ->
                                                val filtered = input.filter { it.isDigit() }.take(2)
                                                hourTextState = filtered
                                                if (filtered.isNotEmpty()) {
                                                    val num = filtered.toIntOrNull()
                                                    if (num != null && num in 0..23) {
                                                        onAutoBackupHourChanged(num)
                                                        BackupScheduler.schedulePeriodicBackup(context, true, autoBackupInterval, num, autoBackupMinute)
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
                                                val newHour = if (autoBackupHour - 1 < 0) 23 else autoBackupHour - 1
                                                onAutoBackupHourChanged(newHour)
                                                BackupScheduler.schedulePeriodicBackup(context, true, autoBackupInterval, newHour, autoBackupMinute)
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
                                                val newMin = (autoBackupMinute + 1) % 60
                                                onAutoBackupMinuteChanged(newMin)
                                                BackupScheduler.schedulePeriodicBackup(context, true, autoBackupInterval, autoBackupHour, newMin)
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
                                        var minuteTextState by remember(autoBackupMinute) { mutableStateOf(String.format("%02d", autoBackupMinute)) }
                                        BasicTextField(
                                            value = minuteTextState,
                                            onValueChange = { input ->
                                                val filtered = input.filter { it.isDigit() }.take(2)
                                                minuteTextState = filtered
                                                if (filtered.isNotEmpty()) {
                                                    val num = filtered.toIntOrNull()
                                                    if (num != null && num in 0..59) {
                                                        onAutoBackupMinuteChanged(num)
                                                        BackupScheduler.schedulePeriodicBackup(context, true, autoBackupInterval, autoBackupHour, num)
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
                                                val newMin = if (autoBackupMinute - 1 < 0) 59 else autoBackupMinute - 1
                                                onAutoBackupMinuteChanged(newMin)
                                                BackupScheduler.schedulePeriodicBackup(context, true, autoBackupInterval, autoBackupHour, newMin)
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
