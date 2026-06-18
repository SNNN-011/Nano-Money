package com.example.ui

import android.app.Application
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.FinancialRecord
import com.example.ui.theme.*
import com.example.R
import java.util.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer

enum class TrackerTab {
    BERANDA,
    TRANSAKSI_BARU,
    KALENDER,
    CHAT,
    EKSPOR_IMPOR
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FinancialTrackerScreen(
    application: Application,
    showStartupSplash: Boolean = true,
    viewModel: FinancialTrackerViewModel = viewModel(factory = FinancialTrackerViewModel.provideFactory(application)),
    calendarViewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.provideFactory(application)),
    chatViewModel: ChatViewModel = viewModel(factory = ChatViewModel.provideFactory(application)),
    analysisViewModel: AnalysisViewModel = viewModel(factory = AnalysisViewModel.provideFactory(application))
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    // Observe flows from ViewModel
    val totalIncome by viewModel.totalIncome.collectAsState()
    val totalExpense by viewModel.totalExpense.collectAsState()
    val currentBalance by viewModel.currentBalance.collectAsState()
    val filteredRecords by viewModel.filteredRecords.collectAsState()
    val allRecords by viewModel.allRecords.collectAsState()
    val availableMonths = remember(allRecords) {
        if (allRecords.isEmpty()) {
            listOf(java.time.YearMonth.now())
        } else {
            allRecords.map { record ->
                val date = java.time.Instant.ofEpochMilli(record.date)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                java.time.YearMonth.of(date.year, date.monthValue)
            }.distinct().sortedDescending()
        }
    }
    val filterType by viewModel.filterType.collectAsState()
    val sortByNewest by viewModel.sortByNewest.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val recurringTransactions by viewModel.recurringTransactions.collectAsState()

    // Form states
    val formDescription by viewModel.formDescription.collectAsState()
    val formAmount by viewModel.formAmount.collectAsState()
    val formType by viewModel.formType.collectAsState()
    val formCategory by viewModel.formCategory.collectAsState()
    val formDate by viewModel.formDate.collectAsState()
    val formNotes by viewModel.formNotes.collectAsState()

    // Errors
    val descriptionError by viewModel.descriptionError.collectAsState()
    val amountError by viewModel.amountError.collectAsState()
    val dateError by viewModel.dateError.collectAsState()

    val incomeCategoriesState by viewModel.incomeCategories.collectAsState()
    val expenseCategoriesState by viewModel.expenseCategories.collectAsState()

    val monthlyBudgetLimit by viewModel.monthlyBudgetLimit.collectAsState()
    val monthlySpendingTotal by viewModel.monthlySpendingTotal.collectAsState()
    val selectedBudgetOffset by viewModel.selectedBudgetOffset.collectAsState()
    val categoryBudgets by viewModel.categoryBudgets.collectAsState()
    val categorySpending by viewModel.categorySpending.collectAsState()

    var showBudgetDialog by remember { mutableStateOf(false) }

    var showCategoryDialog by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<FinancialRecord?>(null) }

    // Handle alert states
    var alertTitle by remember { mutableStateOf<String?>(null) }
    var alertMessage by remember { mutableStateOf<String?>(null) }

    // UI Events observation
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is FinancialTrackerViewModel.UiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is FinancialTrackerViewModel.UiEvent.ShowAlert -> {
                    alertTitle = event.title
                    alertMessage = event.message
                }
            }
        }
    }

    // Sync CalendarMonth to AnalysisViewModel
    LaunchedEffect(calendarViewModel.currentMonthYear) {
        calendarViewModel.currentMonthYear.collect { ym ->
            analysisViewModel.setCalendarMonth(ym)
        }
    }

    // Startup Screen State
    var isLaunching by remember { mutableStateOf(showStartupSplash) }
    LaunchedEffect(Unit) {
        if (showStartupSplash) {
            kotlinx.coroutines.delay(2200)
        }
        isLaunching = false
    }

    // Interactive Date Picker state
    var showDatePickerDialog by remember { mutableStateOf(false) }

    // Tab navigation state
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: TrackerTab.BERANDA.name

    var monthsToExport by remember { mutableStateOf<List<java.time.YearMonth>>(emptyList()) }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    viewModel.generatePdfReport(context, outputStream, monthsToExport)
                }
                Toast.makeText(context, "Laporan PDF berhasil disimpan!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal membuat PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/comma-separated-values")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val csvString = viewModel.getExportCsvString(monthsToExport)
                    outputStream.write(csvString.toByteArray())
                }
                Toast.makeText(context, "Data berhasil diekspor ke Excel (CSV)!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Ekspor Excel gagal: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val csvString = inputStream.bufferedReader().use { it.readText() }
                    viewModel.importFromCsvString(csvString)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal mengimpor file Excel: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (alertTitle != null && alertMessage != null) {
        MessageAlertDialog(
            title = alertTitle ?: "",
            message = alertMessage ?: "",
            onDismissRequest = {
                alertTitle = null
                alertMessage = null
            }
        )
    }

    if (showDatePickerDialog) {
        CustomDatePickerDialog(
            initialTimeMs = formDate,
            onDateSelected = { timestamp ->
                viewModel.formDate.value = timestamp
                showDatePickerDialog = false
            },
            onDismissRequest = { showDatePickerDialog = false }
        )
    }

    val currentRecordToDelete = recordToDelete
    if (currentRecordToDelete != null) {
        DeleteTransactionDialog(
            record = currentRecordToDelete,
            onConfirm = {
                viewModel.deleteRecord(currentRecordToDelete)
                recordToDelete = null
            },
            onDismiss = { recordToDelete = null }
        )
    }

    if (showCategoryDialog) {
        val currentCategories = if (formType == "income") incomeCategoriesState else expenseCategoriesState
        CategoryManagementDialog(
            formType = formType,
            currentCategories = currentCategories,
            onAddCategory = { viewModel.addCategory(formType, it) },
            onDeleteCategory = { viewModel.deleteCategory(formType, it) },
            onDismissRequest = { showCategoryDialog = false }
        )
    }

    if (showBudgetDialog) {
        MonthlyBudgetDialog(
            currentBudgetLimit = monthlyBudgetLimit,
            categoryBudgets = categoryBudgets,
            expenseCategories = expenseCategoriesState,
            onConfirm = { limit, updatedCategoryBudgets ->
                viewModel.updateMonthlyBudgetLimit(limit)
                updatedCategoryBudgets.forEach { (cat, valDouble) ->
                    viewModel.updateCategoryBudget(cat, valDouble)
                }
                expenseCategoriesState.forEach { cat ->
                    if (!updatedCategoryBudgets.containsKey(cat)) {
                        viewModel.updateCategoryBudget(cat, 0.0)
                    }
                }
                showBudgetDialog = false
            },
            onDismiss = { showBudgetDialog = false }
        )
    }

    // Responsive Adaptive Layout parameters
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightAbyss)
    ) {
        val isWideScreen = maxWidth > 720.dp

        // Varied atmospheric ambient lights depending on current tab
        AnimatedContent(
            targetState = currentRoute,
            transitionSpec = {
                fadeIn(animationSpec = androidx.compose.animation.core.tween(700)) togetherWith fadeOut(animationSpec = androidx.compose.animation.core.tween(700))
            },
            label = "gradient_transition"
        ) { route ->
            when (route) {
                TrackerTab.BERANDA.name -> {
                    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(colors = listOf(SteelBlueGlow.copy(alpha = 0.08f), Color.Transparent), center = Offset(500f, 300f), radius = 1000f)))
                    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(colors = listOf(SteelBlueGlow.copy(alpha = 0.06f), Color.Transparent), center = Offset(100f, 1600f), radius = 1200f)))
                    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(colors = listOf(SteelBlueGlow.copy(alpha = 0.04f), Color.Transparent), center = Offset(1500f, 1000f), radius = 900f)))
                }
                TrackerTab.TRANSAKSI_BARU.name -> {
                    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(colors = listOf(SteelBlueGlow.copy(alpha = 0.12f), Color.Transparent), center = Offset(800f, 0f), radius = 900f)))
                    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(colors = listOf(NeonViolet.copy(alpha = 0.1f), Color.Transparent), center = Offset(0f, 1500f), radius = 800f)))
                }
                TrackerTab.KALENDER.name -> {
                    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(colors = listOf(NeonViolet.copy(alpha = 0.12f), Color.Transparent), center = Offset(0f, 0f), radius = 800f)))
                    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(colors = listOf(SteelBlueGlow.copy(alpha = 0.1f), Color.Transparent), center = Offset(800f, 1500f), radius = 1100f)))
                }
                TrackerTab.CHAT.name -> {
                    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(colors = listOf(SteelBlueGlow.copy(alpha = 0.11f), Color.Transparent), center = Offset(800f, 1500f), radius = 800f)))
                    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(colors = listOf(NeonViolet.copy(alpha = 0.1f), Color.Transparent), center = Offset(800f, 0f), radius = 900f)))
                    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(colors = listOf(SteelBlueGlow.copy(alpha = 0.08f), Color.Transparent), center = Offset(0f, 1500f), radius = 600f)))
                }
                TrackerTab.EKSPOR_IMPOR.name -> {
                    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(colors = listOf(NeonViolet.copy(alpha = 0.09f), Color.Transparent), center = Offset(0f, 1500f), radius = 900f)))
                    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(colors = listOf(SteelBlueGlow.copy(alpha = 0.12f), Color.Transparent), center = Offset(800f, 0f), radius = 800f)))
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(colors = listOf(SteelBlueGlow.copy(alpha = 0.08f), Color.Transparent), radius = 600f)))
                    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(colors = listOf(NeonViolet.copy(alpha = 0.1f), Color.Transparent), radius = 800f)))
                }
            }
        }

        Scaffold(
            modifier = Modifier.navigationBarsPadding(),
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "Nano Money ",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = GhostWhite,
                                    letterSpacing = 1.sp
                                )
                                AnimatedContent(
                                    targetState = currentRoute,
                                    transitionSpec = {
                                        fadeIn(animationSpec = androidx.compose.animation.core.tween(700)) togetherWith fadeOut(animationSpec = androidx.compose.animation.core.tween(700))
                                    },
                                    label = "title_transition"
                                ) { route ->
                                    Text(
                                        text = when (route) {
                                            TrackerTab.BERANDA.name -> "DASHBOARD"
                                            TrackerTab.TRANSAKSI_BARU.name -> if (viewModel.editingRecordId != null) "EDIT TRANSAKSI" else "TRANSAKSI BARU"
                                            TrackerTab.KALENDER.name -> "KALENDER"
                                            TrackerTab.CHAT.name -> "AI CHATBOT"
                                            TrackerTab.EKSPOR_IMPOR.name -> "CADANGKAN & PEMULIHAN"
                                            else -> ""
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SteelBlue,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MidnightAbyss.copy(alpha = 0.95f),
                            titleContentColor = GhostWhite
                        )
                    )
                    HorizontalDivider(color = GhostWhite.copy(alpha = 0.05f), thickness = 1.dp)
                }
            },
            bottomBar = {
                Column {
                    HorizontalDivider(color = GhostWhite.copy(alpha = 0.05f), thickness = 1.dp)
                NavigationBar(
                    containerColor = MidnightAbyss.copy(alpha = 0.95f),
                    tonalElevation = 0.dp,
                    modifier = Modifier.testTag("app_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = currentRoute == TrackerTab.BERANDA.name,
                        onClick = { 
                            focusManager.clearFocus()
                            navController.navigate(TrackerTab.BERANDA.name) {
                                popUpTo(TrackerTab.BERANDA.name) { 
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.AccountBalance, contentDescription = "Dashboard", modifier = Modifier.size(20.dp)) },
                        label = { Text("Dashboard", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SteelBlue,
                            selectedTextColor = SteelBlue,
                            unselectedIconColor = GhostWhite.copy(alpha = 0.5f),
                            unselectedTextColor = GhostWhite.copy(alpha = 0.5f),
                            indicatorColor = TranslucentGlass
                        ),
                        modifier = Modifier.testTag("nav_beranda")
                    )
                    NavigationBarItem(
                        selected = currentRoute == TrackerTab.CHAT.name,
                        onClick = {
                            focusManager.clearFocus()
                            navController.navigate(TrackerTab.CHAT.name) {
                                popUpTo(TrackerTab.BERANDA.name) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat", modifier = Modifier.size(20.dp)) },
                        label = { Text("Chat", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SteelBlue,
                            selectedTextColor = SteelBlue,
                            unselectedIconColor = GhostWhite.copy(alpha = 0.5f),
                            unselectedTextColor = GhostWhite.copy(alpha = 0.5f),
                            indicatorColor = TranslucentGlass
                        ),
                        modifier = Modifier.testTag("nav_chat")
                    )
                    NavigationBarItem(
                        selected = currentRoute == TrackerTab.TRANSAKSI_BARU.name,
                        onClick = {
                            focusManager.clearFocus()
                            if (viewModel.editingRecordId == null) {
                                viewModel.resetForm()
                            }
                            navController.navigate(TrackerTab.TRANSAKSI_BARU.name) {
                                popUpTo(TrackerTab.BERANDA.name) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.AddCircleOutline, contentDescription = "Transaksi Baru", modifier = Modifier.size(20.dp)) },
                        label = { Text("Tambah", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SteelBlue,
                            selectedTextColor = SteelBlue,
                            unselectedIconColor = GhostWhite.copy(alpha = 0.5f),
                            unselectedTextColor = GhostWhite.copy(alpha = 0.5f),
                            indicatorColor = TranslucentGlass
                        ),
                        modifier = Modifier.testTag("nav_transaksi_baru")
                    )
                    NavigationBarItem(
                        selected = currentRoute == TrackerTab.KALENDER.name,
                        onClick = {
                            focusManager.clearFocus()
                            navController.navigate(TrackerTab.KALENDER.name) {
                                popUpTo(TrackerTab.BERANDA.name) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = "Kalender", modifier = Modifier.size(20.dp)) },
                        label = { Text("Kalender", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SteelBlue,
                            selectedTextColor = SteelBlue,
                            unselectedIconColor = GhostWhite.copy(alpha = 0.5f),
                            unselectedTextColor = GhostWhite.copy(alpha = 0.5f),
                            indicatorColor = TranslucentGlass
                        ),
                        modifier = Modifier.testTag("nav_kalender")
                    )
                    NavigationBarItem(
                        selected = currentRoute == TrackerTab.EKSPOR_IMPOR.name,
                        onClick = { 
                            focusManager.clearFocus()
                            navController.navigate(TrackerTab.EKSPOR_IMPOR.name) {
                                popUpTo(TrackerTab.BERANDA.name) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.ImportExport, contentDescription = "Ekspor Impor", modifier = Modifier.size(20.dp)) },
                        label = { Text("Simpan", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SteelBlue,
                            selectedTextColor = SteelBlue,
                            unselectedIconColor = GhostWhite.copy(alpha = 0.5f),
                            unselectedTextColor = GhostWhite.copy(alpha = 0.5f),
                            indicatorColor = TranslucentGlass
                        ),
                        modifier = Modifier.testTag("nav_ekspor_impor")
                    )
                }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues)
                    .then(if (currentRoute != TrackerTab.CHAT.name) Modifier.imePadding() else Modifier)
            ) {
                NavHost(navController = navController, startDestination = TrackerTab.BERANDA.name) {
                    composable(TrackerTab.BERANDA.name) {
                        BerandaTabContent(
                            isWideScreen = isWideScreen,
                            totalIncome = totalIncome,
                            totalExpense = totalExpense,
                            currentBalance = currentBalance,
                            monthlySpendingTotal = monthlySpendingTotal,
                            monthlyBudgetLimit = monthlyBudgetLimit,
                            categoryBudgets = categoryBudgets,
                            categorySpending = categorySpending,
                            selectedBudgetOffset = selectedBudgetOffset,
                            onBudgetOffsetChange = { viewModel.changeBudgetMonthOffset(it) },
                            onSetBudgetClick = { showBudgetDialog = true },
                            filteredRecords = filteredRecords,
                            filterType = filterType,
                            sortByNewest = sortByNewest,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                            onFilterSelected = { viewModel.updateFilterType(it) },
                            onSortToggled = { viewModel.toggleSortByNewest() },
                            onEditRecord = { record ->
                                viewModel.setEditingRecord(record)
                                navController.navigate(TrackerTab.TRANSAKSI_BARU.name)
                            },
                            onDeleteRecord = { record ->
                                recordToDelete = record
                            },
                            onSeedSampleData = { viewModel.seedSampleData() },
                            recurringTransactions = recurringTransactions,
                            incomeCategories = incomeCategoriesState,
                            expenseCategories = expenseCategoriesState,
                            onAddRecurring = { desc, amt, type, cat, day, notes ->
                                viewModel.addRecurringTransaction(desc, amt, type, cat, day, notes)
                            },
                            onDeleteRecurring = { viewModel.deleteRecurringTransaction(it) },
                            onToggleRecurringActive = { viewModel.toggleRecurringTransactionActive(it) }
                        )
                    }

                    composable(TrackerTab.TRANSAKSI_BARU.name) {
                        TransaksiBaruTabContent(
                            isWideScreen = isWideScreen,
                            formDescription = formDescription,
                            formAmount = formAmount,
                            formType = formType,
                            formCategory = formCategory,
                            formDate = formDate,
                            formNotes = formNotes,
                            descriptionError = descriptionError,
                            amountError = amountError,
                            dateError = dateError,
                            isEditing = viewModel.editingRecordId != null,
                            categoriesState = if (formType == "income") incomeCategoriesState else expenseCategoriesState,
                            onManageCategoriesClick = { showCategoryDialog = true },
                            onDescriptionChanged = { viewModel.formDescription.value = it },
                            onAmountChanged = { viewModel.formAmount.value = it },
                            onTypeChanged = { viewModel.formType.value = it },
                            onCategoryChanged = { viewModel.formCategory.value = it },
                            onDateClick = { showDatePickerDialog = true },
                            onNotesChanged = { viewModel.formNotes.value = it },
                            onSaveSuccess = { 
                                navController.navigate(TrackerTab.BERANDA.name) {
                                    popUpTo(TrackerTab.BERANDA.name) { inclusive = true }
                                }
                            },
                            onReset = { 
                                if (!navController.popBackStack()) {
                                    navController.navigate(TrackerTab.BERANDA.name) {
                                        popUpTo(TrackerTab.BERANDA.name) { inclusive = true }
                                    }
                                }
                            },
                            onSaveRecord = { viewModel.validateAndSave() }
                        )
                    }

                    composable(TrackerTab.KALENDER.name) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            CalendarTabContent(
                                calendarViewModel = calendarViewModel,
                                mainViewModel = viewModel,
                                analysisViewModel = analysisViewModel,
                                onNavigateToNewTransaction = { localDate ->
                                    val zoneId = java.time.ZoneId.systemDefault()
                                    val ms = localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
                                    viewModel.resetForm()
                                    viewModel.formDate.value = ms
                                    navController.navigate(TrackerTab.TRANSAKSI_BARU.name)
                                },
                                onNavigateToEditTransaction = { record ->
                                    viewModel.setEditingRecord(record)
                                    navController.navigate(TrackerTab.TRANSAKSI_BARU.name)
                                },
                                onDeleteRecord = { record ->
                                    recordToDelete = record
                                }
                            )
                        }
                    }

                    composable(TrackerTab.CHAT.name) {
                        ChatScreen(viewModel = chatViewModel)
                    }

                    composable(TrackerTab.EKSPOR_IMPOR.name) {
                        EksporImporTabContent(
                            isWideScreen = isWideScreen,
                            availableMonths = availableMonths,
                            onImportCsvFile = {
                                try {
                                    importCsvLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "application/octet-stream", "text/plain"))
                                } catch (e: Exception) {
                                    try {
                                        importCsvLauncher.launch(arrayOf("*/*"))
                                    } catch (err: Exception) {
                                        Toast.makeText(context, "Gagal memulai pemilih file Excel.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onExportCsvFile = { selectedMonths ->
                                monthsToExport = selectedMonths
                                try {
                                    exportCsvLauncher.launch("keuangan_excel_${System.currentTimeMillis()}.csv")
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Gagal memulai pembuat berkas Excel.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onExportPdfFile = { selectedMonths ->
                                monthsToExport = selectedMonths
                                try {
                                    exportPdfLauncher.launch("Laporan_Keuangan_NanoMoney_${System.currentTimeMillis()}.pdf")
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Gagal memulai pembuat berkas PDF.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }

        // Startup splash / launcher screen overlay
        StartupScreen(visible = isLaunching)
    }
}
