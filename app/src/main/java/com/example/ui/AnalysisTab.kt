package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import com.example.data.CategoryAggregation
import com.example.ui.theme.*
import androidx.compose.ui.platform.testTag

@Composable
fun AnalysisTabContent(
    viewModel: AnalysisViewModel,
    modifier: Modifier = Modifier
) {
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val categoryAggregations by viewModel.categoryAggregations.collectAsState()
    val totalExpense by viewModel.totalExpense.collectAsState()
    val averageDailyExpense by viewModel.averageDailyExpense.collectAsState()
    val totalIncome by viewModel.totalIncome.collectAsState()
    val topCategory by viewModel.topCategory.collectAsState()
    val totalTransactions by viewModel.totalTransactions.collectAsState()
    val dailyTrends by viewModel.dailyTrends.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PeriodFilterRow(
            selectedPeriod = selectedPeriod,
            onPeriodSelect = { viewModel.setPeriod(it) }
        )

        SummaryStatsCard(
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            averageDailyExpense = averageDailyExpense,
            topCategoryName = topCategory?.category ?: "-",
            totalTransactions = totalTransactions
        )

        CombinedChartsCard(
            dailyTrends = dailyTrends,
            aggregations = categoryAggregations,
            totalExpense = totalExpense
        )
    }
}

@Composable
fun PeriodFilterRow(
    selectedPeriod: AnalysisViewModel.PeriodFilter,
    onPeriodSelect: (AnalysisViewModel.PeriodFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("period_filter_row"),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnalysisViewModel.PeriodFilter.values().forEach { period ->
            val isSelected = selectedPeriod == period
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { onPeriodSelect(period) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = period.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = if (isSelected) GhostWhite else GhostWhite.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .height(2.5.dp)
                            .fillMaxWidth(0.35f)
                            .background(
                                color = if (isSelected) GhostWhite else Color.Transparent,
                                shape = RoundedCornerShape(1.5.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryStatsCard(
    totalIncome: Double,
    totalExpense: Double,
    averageDailyExpense: Double,
    topCategoryName: String,
    totalTransactions: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = TranslucentGlass),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(GhostWhite.copy(alpha = 0.1f), GhostWhite.copy(alpha = 0.02f))
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Ringkasan Statistik",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = GhostWhite,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatItem(
                    title = "Total Pemasukan",
                    value = FormatUtils.formatRupiah(totalIncome),
                    valueColor = CalPemasukan,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    title = "Total Pengeluaran",
                    value = FormatUtils.formatRupiah(totalExpense),
                    valueColor = CalPengeluaran,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatItem(
                    title = "Kategori Terbesar",
                    value = topCategoryName,
                    valueColor = GhostWhite,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    title = "Total Transaksi",
                    value = "${totalTransactions}x",
                    valueColor = GhostWhite,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun StatItem(title: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = GhostWhite.copy(alpha = 0.6f))
        Text(text = value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = valueColor)
    }
}

val EaseInOutQuad = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)
val EaseInOutSine = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

@Composable
fun CombinedChartsCard(
    dailyTrends: List<DailyAggregation>,
    aggregations: List<CategoryAggregation>,
    totalExpense: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = TranslucentGlass),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(GhostWhite.copy(alpha = 0.1f), GhostWhite.copy(alpha = 0.02f))
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Line Chart Column
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Tren",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = GhostWhite,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )

                    if (dailyTrends.isEmpty()) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Icon(Icons.Default.Warning, contentDescription = null, tint = GhostWhite.copy(alpha = 0.3f), modifier = Modifier.size(32.dp))
                        Text("Belum ada", color = GhostWhite.copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        Spacer(modifier = Modifier.height(32.dp))
                    } else {
                        val progress = remember { Animatable(0f) }
                        LaunchedEffect(dailyTrends) {
                            progress.snapTo(0f)
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 1200, easing = EaseInOutSine)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .padding(horizontal = 4.dp)
                        ) {
                            val maxAmount = (dailyTrends.maxOfOrNull { it.totalAmount }?.toFloat() ?: 0f).coerceAtLeast(1f)
                            val width = size.width
                            val height = size.height

                            val linesCount = 4
                            for (i in 0..linesCount) {
                                val y = height - (height * (i.toFloat() / linesCount))
                                drawLine(
                                    color = GhostWhite.copy(alpha = 0.1f),
                                    start = Offset(0f, y),
                                    end = Offset(width, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }

                            if (dailyTrends.size > 1) {
                                val stepX = width / (dailyTrends.size - 1)
                                val points = dailyTrends.mapIndexed { index, agg ->
                                    val x = index * stepX
                                    val scaledY = (agg.totalAmount.toFloat() / maxAmount) * height * progress.value
                                    val y = height - scaledY
                                    Offset(x, y)
                                }

                                val path = androidx.compose.ui.graphics.Path()
                                path.moveTo(points.first().x, points.first().y)

                                for (i in 0 until points.size - 1) {
                                    val p0 = points[i]
                                    val p1 = points[i + 1]
                                    val cx = (p0.x + p1.x) / 2
                                    path.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                                }

                                val fillPath = androidx.compose.ui.graphics.Path()
                                fillPath.addPath(path)
                                fillPath.lineTo(points.last().x, height)
                                fillPath.lineTo(points.first().x, height)
                                fillPath.close()

                                val primaryColor = SteelBlue
                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent),
                                        startY = 0f,
                                        endY = height
                                    )
                                )

                                drawPath(
                                    path = path,
                                    color = primaryColor,
                                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                )

                                points.forEach { point ->
                                    drawCircle(
                                        color = MidnightAbyss,
                                        radius = 3.dp.toPx(),
                                        center = point
                                    )
                                    drawCircle(
                                        color = primaryColor,
                                        radius = 3.dp.toPx(),
                                        center = point,
                                        style = Stroke(width = 1.5.dp.toPx())
                                    )
                                }
                            } else if (dailyTrends.size == 1) {
                                val agg = dailyTrends.first()
                                val scaledY = (agg.totalAmount.toFloat() / maxAmount) * height * progress.value
                                val center = Offset(width / 2f, height - scaledY)
                                drawCircle(
                                    color = SteelBlue,
                                    radius = 3.dp.toPx(),
                                    center = center
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (dailyTrends.size > 1) {
                                Text(dailyTrends.first().dateString, fontSize = 8.sp, color = GhostWhite.copy(alpha = 0.5f))
                                Text(dailyTrends.last().dateString, fontSize = 8.sp, color = GhostWhite.copy(alpha = 0.5f))
                            } else if (dailyTrends.size == 1) {
                                Text(dailyTrends.first().dateString, fontSize = 8.sp, color = GhostWhite.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            }
                        }
                    }
                }

                // Donut Chart Column
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Kategori",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = GhostWhite,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )

                    if (aggregations.isEmpty()) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Icon(Icons.Default.Warning, contentDescription = null, tint = GhostWhite.copy(alpha = 0.3f), modifier = Modifier.size(32.dp))
                        Text("Belum ada", color = GhostWhite.copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        Spacer(modifier = Modifier.height(32.dp))
                    } else {
                        val donutColors = listOf(
                            Color(0xFFC2E0FF), // Ice blue
                            Color(0xFF93C9FF), // Biru muda
                            Color(0xFF5BA8F5), // Biru terang
                            Color(0xFF2E86DE), // Biru utama (sesuai tone app)
                            Color(0xFF1E5FAD), // Biru medium
                            Color(0xFF1A3A6B)  // Biru navy gelap
                        )

                        val progress = remember { Animatable(0f) }
                        LaunchedEffect(aggregations) {
                            progress.snapTo(0f)
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 1000, easing = EaseInOutQuad)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(contentAlignment = Alignment.Center) {
                            Canvas(modifier = Modifier.size(120.dp)) {
                                var startAngle = -90f
                                aggregations.forEachIndexed { index, agg ->
                                    val sweepAngle = ((agg.totalAmount / totalExpense) * 360f).toFloat() * progress.value
                                    val gap = if (sweepAngle > 3f) 3f else 0f
                                    val actualSweep = sweepAngle - gap

                                    val baseStroke = size.width * 0.25f
                                    val color = donutColors[index % donutColors.size]

                                    drawArc(
                                        color = color,
                                        startAngle = startAngle + (gap / 2f),
                                        sweepAngle = actualSweep,
                                        useCenter = false,
                                        style = Stroke(width = baseStroke, cap = StrokeCap.Butt),
                                        size = Size(size.width, size.height)
                                    )
                                    startAngle += sweepAngle
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Total",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 10.sp,
                                        color = GhostWhite.copy(alpha = 0.5f)
                                    )
                                )
                                Text(
                                    text = FormatUtils.formatRupiahCompact(totalExpense),
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GhostWhite
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (aggregations.isNotEmpty()) {
                val donutColors = listOf(
                    Color(0xFFC2E0FF), // Ice blue
                    Color(0xFF93C9FF), // Biru muda
                    Color(0xFF5BA8F5), // Biru terang
                    Color(0xFF2E86DE), // Biru utama (sesuai tone app)
                    Color(0xFF1E5FAD), // Biru medium
                    Color(0xFF1A3A6B)  // Biru navy gelap
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = GhostWhite.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    aggregations.forEachIndexed { index, agg ->
                        val color = donutColors[index % donutColors.size]
                        val percentage = if (totalExpense > 0) (agg.totalAmount / totalExpense) * 100 else 0.0

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))

                            val (_, cleanCat) = com.example.ui.util.CategoryIconMapper.extractEmojiAndLabel(agg.category)
                            Text(
                                text = cleanCat,
                                color = GhostWhite,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = String.format("%.1f%%", percentage),
                                color = GhostWhite.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = FormatUtils.formatRupiahCompact(agg.totalAmount),
                                color = GhostWhite,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }
    }
}
