package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import com.example.util.BackupHelper
import com.example.util.BackupScheduler
import java.io.File
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import android.widget.Toast
import com.example.ui.theme.*
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MonthSelectChip(
    month: YearMonth,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale("id", "ID")) }
    val formattedName = remember(month) { month.format(formatter) }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("month_chip_${month.year}_${month.monthValue}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) SteelBlue.copy(alpha = 0.25f) else TranslucentGlass
        ),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = if (isSelected) {
                    listOf(SteelBlue.copy(alpha = 0.6f), SteelBlue.copy(alpha = 0.2f))
                } else {
                    listOf(GhostWhite.copy(alpha = 0.15f), GhostWhite.copy(alpha = 0.02f))
                }
            )
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formattedName,
                color = if (isSelected) GhostWhite else GhostWhite.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Dipilih" else "Tidak dipilih",
                tint = if (isSelected) SteelBlue else GhostWhite.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun EksporImporTabContent(
    isWideScreen: Boolean,
    availableMonths: List<YearMonth>,
    onImportCsvFile: () -> Unit,
    onExportCsvFile: (List<YearMonth>) -> Unit,
    onExportPdfFile: (List<YearMonth>) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMonths by remember(availableMonths) { mutableStateOf(availableMonths.toSet()) }
    var isMonthsExpanded by remember { mutableStateOf(false) }

    val contentBody = @Composable {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Column {
                Text(
                    text = "Laporan & Ekspor Data",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = GhostWhite
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ekspor laporan keuangan resmi ke berkas PDF atau ekspor data mentah transaksi Anda ke lembar kerja Excel (.csv) sesuai bulan yang dipilih.",
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = GhostWhite.copy(alpha = 0.5f)
                )
            }

            // UNIFIED PDF, CSV EXPORTS & CSV IMPOR CARD
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
                    // Header inside card (Clickable to Toggle Months)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isMonthsExpanded = !isMonthsExpanded }
                            .padding(vertical = 4.dp),
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
                                text = "Pilih Periode Laporan Keuangan",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 17.sp, 
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.2.sp
                                ),
                                color = GhostWhite
                            )
                        }

                        Icon(
                            imageVector = if (isMonthsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isMonthsExpanded) "Sembunyikan" else "Tampilkan Semua",
                            tint = SteelBlue,
                            modifier = Modifier.size(20.dp)
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
                                onClick = { selectedMonths = availableMonths.toSet() },
                                isActive = true,
                                fillMaxWidth = false,
                                testTag = "select_all_months",
                                horizontalPadding = 12.dp,
                                verticalPadding = 6.dp
                            )

                            PremiumButton(
                                text = "Kosongkan",
                                onClick = { selectedMonths = emptySet() },
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
                                                selectedMonths = if (selectedMonths.contains(month)) {
                                                    selectedMonths - month
                                                } else {
                                                    selectedMonths + month
                                                }
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
                                .clickable { isMonthsExpanded = true }
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

            Spacer(modifier = Modifier.height(16.dp))

            // SECURITY SETTINGS CARD
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            var isSetPinDialogOpen by remember { mutableStateOf(false) }
            var pinInputText by remember { mutableStateOf("") }
            val securityPrefs = remember { context.getSharedPreferences("app_security_prefs", android.content.Context.MODE_PRIVATE) }
            var isPinEnabled by remember { mutableStateOf(securityPrefs.getBoolean("pin_enabled", false)) }
            var savedPin by remember { mutableStateOf(securityPrefs.getString("saved_pin", "") ?: "") }
            var isBiometricEnabled by remember { mutableStateOf(securityPrefs.getBoolean("biometric_enabled", false)) }
            var isReminderEnabled by remember { mutableStateOf(securityPrefs.getBoolean("reminder_enabled", false)) }

            val securityQuestions = remember {
                listOf(
                    "Apa nama hewan peliharaan pertama Anda?",
                    "Apa nama kota tempat Anda lahir?",
                    "Apa nama sekolah dasar (SD) Anda?",
                    "Siapa nama guru favorit masa kecil Anda?"
                )
            }
            var selectedQuestionIndex by remember { mutableStateOf(0) }
            var securityAnswerInput by remember { mutableStateOf("") }
            var isQuestionDropdownExpanded by remember { mutableStateOf(false) }
            var pinDialogErrorText by remember { mutableStateOf("") }

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
                        text = "Aktifkan kunci pengaman PIN atau Biometrik (Sidik Jari/Metode Wajah) untuk mencegah orang lain melihat catatan keuangan Anda.",
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
                                if (!checked) {
                                    isPinEnabled = false
                                    isBiometricEnabled = false
                                    securityPrefs.edit()
                                        .putBoolean("pin_enabled", false)
                                        .putBoolean("biometric_enabled", false)
                                        .apply()
                                    Toast.makeText(context, "Kunci PIN dinonaktifkan", Toast.LENGTH_SHORT).show()
                                } else {
                                    pinInputText = ""
                                    securityAnswerInput = ""
                                    pinDialogErrorText = ""
                                    selectedQuestionIndex = 0
                                    isSetPinDialogOpen = true
                                }
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
                                text = "Kunci Sidik Jari / Biometrik",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBiometricAllowed) GhostWhite else GhostWhite.copy(alpha = 0.4f)
                                )
                            )
                            Text(
                                text = if (isBiometricAllowed) {
                                    "Gunakan sensor sidik jari perangkat untuk masuk dengan cepat"
                                } else {
                                    "PIN harus aktif terlebih dahulu untuk mengaktifkan Biometrik"
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
                                    isBiometricEnabled = checked
                                    securityPrefs.edit().putBoolean("biometric_enabled", checked).apply()
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // DAILY NOTIFICATION REMINDER CARD
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
                    // Header of Notification
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

                    // Reminder Toggle Item
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pengingat Jam 8 Malam",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = GhostWhite
                            )
                            Text(
                                text = "Kirim notifikasi setiap pukul 20:00 WIB",
                                style = MaterialTheme.typography.bodySmall,
                                color = GhostWhite.copy(alpha = 0.5f)
                            )
                        }
                        PremiumSwitch(
                            checked = isReminderEnabled,
                            onCheckedChange = { checked ->
                                isReminderEnabled = checked
                                securityPrefs.edit().putBoolean("reminder_enabled", checked).apply()
                                com.example.util.NotificationScheduler.scheduleDailyReminder(context, checked)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // SQLITE DATABASE BACKUP AND RESTORE CARD
            var isAutoBackupEnabled by remember { mutableStateOf(securityPrefs.getBoolean("auto_backup_enabled", false)) }
            var autoBackupInterval by remember { mutableStateOf(securityPrefs.getString("auto_backup_interval", "daily") ?: "daily") }
            var backupList by remember { mutableStateOf(emptyList<File>()) }
            var selectedBackupToRestore by remember { mutableStateOf<File?>(null) }

            // Fetch backups when entered
            LaunchedEffect(Unit) {
                backupList = BackupHelper.getBackups(context)
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
                    var showBackupHelpInfo by remember { mutableStateOf(false) }

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
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SteelBlue.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, SteelBlue.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = SteelBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Panduan & Informasi Pencadangan",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = GhostWhite
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "📍 Lokasi Penyimpanan Berkas:",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                        color = SteelBlue
                                    )
                                    Text(
                                        text = "Cadangan database disimpan dalam folder berkas internal terisolasi di perangkat Anda:\n/data/user/0/${context.packageName}/files/backups/\nBerkas ini terisolasi aman demi privasi keamanan keuangan Anda.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 15.sp),
                                        color = GhostWhite.copy(alpha = 0.75f)
                                    )
                                    Text(
                                        text = "💡 Kapan Anda Harus Mengeklik 'PULIHKAN'?",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                        color = SteelBlue
                                    )
                                    Text(
                                        text = "• Saat data keuangan saat ini rusak, tidak sengaja terhapus, atau terjadi kesalahan input masal yang ingin Anda batalkan ke kondisi waktu pencadangan sebelumnya.\n• Saat beralih ke perangkat atau instalasi baru, setelah Anda menyalin berkas cadangan manual (.db) Anda ke direktori data aplikasi.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 15.sp),
                                        color = GhostWhite.copy(alpha = 0.75f)
                                    )

                                    Text(
                                        text = "🛡️ Cara Mengamankan Cadangan Secara Eksternal:",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                        color = SteelBlue
                                    )
                                    Text(
                                        text = "Guna menjaga keamanan jangka panjang, sangat disarankan untuk melakukan Ekspor berkala sebagai Laporan CSV atau PDF (pada tab di atas) dan menyimpannya di luar perangkat seperti Google Drive, Email pribadi, atau komputer.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 15.sp),
                                        color = GhostWhite.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        }
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
                                isAutoBackupEnabled = checked
                                securityPrefs.edit().putBoolean("auto_backup_enabled", checked).apply()
                                BackupScheduler.schedulePeriodicBackup(context, checked, autoBackupInterval)
                                Toast.makeText(context, if (checked) "Pencadangan otomatis aktif!" else "Pencadangan otomatis mati!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    // Choose Backup Interval if auto backup is enabled
                    if (isAutoBackupEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Siklus Pencadangan",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = GhostWhite
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PremiumButton(
                                    text = "HARIAN",
                                    onClick = {
                                        autoBackupInterval = "daily"
                                        securityPrefs.edit().putString("auto_backup_interval", "daily").apply()
                                        BackupScheduler.schedulePeriodicBackup(context, true, "daily")
                                        Toast.makeText(context, "Siklus pencadangan diatur: Harian", Toast.LENGTH_SHORT).show()
                                    },
                                    isActive = autoBackupInterval == "daily",
                                    fillMaxWidth = false,
                                    horizontalPadding = 12.dp,
                                    verticalPadding = 6.dp
                                )
                                PremiumButton(
                                    text = "MINGGUAN",
                                    onClick = {
                                        autoBackupInterval = "weekly"
                                        securityPrefs.edit().putString("auto_backup_interval", "weekly").apply()
                                        BackupScheduler.schedulePeriodicBackup(context, true, "weekly")
                                        Toast.makeText(context, "Siklus pencadangan diatur: Mingguan", Toast.LENGTH_SHORT).show()
                                    },
                                    isActive = autoBackupInterval == "weekly",
                                    fillMaxWidth = false,
                                    horizontalPadding = 12.dp,
                                    verticalPadding = 6.dp
                                )
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
                                        backupList = BackupHelper.getBackups(context)
                                        Toast.makeText(context, "Database berhasil dicadangkan: ${result.fileName}", Toast.LENGTH_SHORT).show()
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
                                        PremiumButton(
                                            text = "PULIHKAN",
                                            onClick = { selectedBackupToRestore = file },
                                            isActive = true,
                                            fillMaxWidth = false,
                                            horizontalPadding = 10.dp,
                                            verticalPadding = 4.dp
                                        )

                                        IconButton(
                                            onClick = {
                                                val deleted = file.delete()
                                                if (deleted) {
                                                    backupList = BackupHelper.getBackups(context)
                                                    Toast.makeText(context, "Salinan cadangan dihapus.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Gagal menghapus salinan.", Toast.LENGTH_SHORT).show()
                                                }
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
                }
            }

            // Restore Confirmation Dialog
            if (selectedBackupToRestore != null) {
                AlertDialog(
                    onDismissRequest = { selectedBackupToRestore = null },
                    containerColor = MidnightAbyss,
                    title = {
                        Text(
                            text = "Konfirmasi Pemulihan",
                            color = GhostWhite,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "Perhatian: Memulihkan database akan menimpa seluruh data keuangan Anda saat ini dengan salinan cadangan ini.\n\nAplikasi akan memproses data dan memuat ulang secara otomatis setelah pemulihan selesai. Apakah Anda yakin ingin melanjutkan?",
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
                                onClick = { selectedBackupToRestore = null },
                                isActive = false,
                                modifier = Modifier.weight(1f),
                                horizontalPadding = 8.dp,
                                verticalPadding = 6.dp
                            )
                            PremiumButton(
                                text = "PULIHKAN & MULAI ULANG",
                                onClick = {
                                    val backupToRestore = selectedBackupToRestore
                                    selectedBackupToRestore = null
                                    if (backupToRestore != null) {
                                        val success = BackupHelper.restoreBackup(context, backupToRestore)
                                        if (success) {
                                            Toast.makeText(context, "Database berhasil dipulihkan!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Gagal memulihkan database", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                isActive = true,
                                modifier = Modifier.weight(1.5f),
                                horizontalPadding = 8.dp,
                                verticalPadding = 6.dp
                            )
                        }
                    }
                )
            }

            // SET PIN DIALOG
            if (isSetPinDialogOpen) {
                AlertDialog(
                    onDismissRequest = { isSetPinDialogOpen = false },
                    title = { Text("Atur PIN Kunci & Pemulihan", color = GhostWhite, fontWeight = FontWeight.Bold) },
                    containerColor = MidnightAbyss,
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Masukkan 4 angka kode PIN baru Anda:", color = GhostWhite.copy(alpha = 0.7f), fontSize = 13.sp)
                            OutlinedTextField(
                                value = pinInputText,
                                onValueChange = { newValue ->
                                    if (newValue.length <= 4 && newValue.all { it.isDigit() }) {
                                        pinInputText = newValue
                                    }
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(color = GhostWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("••••", color = GhostWhite.copy(alpha = 0.25f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SteelBlue,
                                    unfocusedBorderColor = GhostWhite.copy(alpha = 0.2f),
                                    focusedTextColor = GhostWhite
                                )
                            )

                            HorizontalDivider(color = GhostWhite.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

                            Text("Pilih pertanyaan keamanan untuk bantuan pemulihan jika Anda lupa PIN:", color = GhostWhite.copy(alpha = 0.7f), fontSize = 13.sp)
                            
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = securityQuestions[selectedQuestionIndex],
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth().clickable { isQuestionDropdownExpanded = true },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SteelBlue,
                                        unfocusedBorderColor = GhostWhite.copy(alpha = 0.2f),
                                        focusedTextColor = GhostWhite,
                                        unfocusedTextColor = GhostWhite
                                    ),
                                    trailingIcon = {
                                        IconButton(onClick = { isQuestionDropdownExpanded = !isQuestionDropdownExpanded }) {
                                            Icon(
                                                imageVector = if (isQuestionDropdownExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = SteelBlue
                                            )
                                        }
                                    }
                                )
                                DropdownMenu(
                                    expanded = isQuestionDropdownExpanded,
                                    onDismissRequest = { isQuestionDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth().background(MidnightAbyss)
                                ) {
                                    securityQuestions.forEachIndexed { index, question ->
                                        DropdownMenuItem(
                                            text = { Text(question, color = GhostWhite, fontSize = 12.sp) },
                                            onClick = {
                                                selectedQuestionIndex = index
                                                isQuestionDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            OutlinedTextField(
                                value = securityAnswerInput,
                                onValueChange = { securityAnswerInput = it },
                                placeholder = { Text("Jawaban Anda...", color = GhostWhite.copy(alpha = 0.3f)) },
                                label = { Text("Jawaban Jawaban Pemulihan PIN", color = GhostWhite.copy(alpha = 0.6f)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SteelBlue,
                                    unfocusedBorderColor = GhostWhite.copy(alpha = 0.2f),
                                    focusedTextColor = GhostWhite,
                                    unfocusedTextColor = GhostWhite
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (pinDialogErrorText.isNotEmpty()) {
                                Text(
                                    pinDialogErrorText,
                                    color = Color.Red,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PremiumButton(
                                text = "Batal",
                                onClick = { isSetPinDialogOpen = false },
                                isActive = false,
                                modifier = Modifier.weight(1f),
                                horizontalPadding = 8.dp,
                                verticalPadding = 6.dp
                            )
                            PremiumButton(
                                text = "Simpan",
                                onClick = {
                                    if (pinInputText.length != 4) {
                                        pinDialogErrorText = "PIN harus terdiri dari 4 digit angka!"
                                    } else if (securityAnswerInput.trim().isEmpty()) {
                                        pinDialogErrorText = "Jawaban pemulihan tidak boleh kosong!"
                                    } else {
                                        isPinEnabled = true
                                        savedPin = pinInputText
                                        securityPrefs.edit()
                                            .putBoolean("pin_enabled", true)
                                            .putString("saved_pin", pinInputText)
                                            .putString("security_question", securityQuestions[selectedQuestionIndex])
                                            .putString("security_answer", securityAnswerInput.trim().lowercase())
                                            .apply()
                                        isSetPinDialogOpen = false
                                        Toast.makeText(context, "Kunci PIN berhasil diatur!", Toast.LENGTH_SHORT).show()
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

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // MAIN SCROLL WRAPPER
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (isWideScreen) 840.dp else Float.MAX_VALUE.dp)
                .verticalScroll(rememberScrollState())
                .padding(if (isWideScreen) 24.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            contentBody()
        }
    }
}
