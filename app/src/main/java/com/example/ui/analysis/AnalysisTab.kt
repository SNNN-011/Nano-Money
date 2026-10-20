package com.example.ui.analysis

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.example.data.CategoryAggregation
import com.example.ui.components.EditorialPill
import com.example.ui.components.Eyebrow
import com.example.ui.components.HairlineDivider
import com.example.ui.config.FormatUtils
import com.example.ui.theme.BurntOrange
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.SerifFamily
import com.example.ui.theme.Spacing
import com.example.ui.util.CategoryIconMapper

/**
 * Ringkasan periode: angka utama, kategori terbesar, lalu rincian per kategori.
 *
 * Grafik tren tidak lagi dirender di sini. Satu-satunya pemakai komponen ini
 * adalah tab Kalender, dan grafiknya sudah jadi latar di belakang grid tanggal.
 */
@Composable
fun AnalysisTabContent(
    viewModel: AnalysisViewModel,
    modifier: Modifier = Modifier
) {
    val categoryAggregations by viewModel.categoryAggregations.collectAsState()
    val totalExpense by viewModel.totalExpense.collectAsState()
    val totalIncome by viewModel.totalIncome.collectAsState()
    val topCategory by viewModel.topCategory.collectAsState()
    val totalTransactions by viewModel.totalTransactions.collectAsState()

    SummaryStatsCard(
        totalIncome = totalIncome,
        totalExpense = totalExpense,
        topCategoryName = topCategory?.category ?: "-",
        totalTransactions = totalTransactions,
        aggregations = categoryAggregations,
        showCategoryList = true,
        modifier = modifier
    )
}

/** Pemilih periode: pil yang sama dengan filter riwayat di Dashboard. */
@Composable
fun PeriodFilterRow(
    selectedPeriod: AnalysisViewModel.PeriodFilter,
    onPeriodSelect: (AnalysisViewModel.PeriodFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("period_filter_row"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnalysisViewModel.PeriodFilter.values().forEach { period ->
            EditorialPill(
                text = period.displayName,
                selected = selectedPeriod == period,
                onClick = { onPeriodSelect(period) },
                textStyle = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun SummaryStatsCard(
    totalIncome: Long,
    totalExpense: Long,
    topCategoryName: String,
    totalTransactions: Int,
    aggregations: List<CategoryAggregation> = emptyList(),
    showCategoryList: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Eyebrow(text = "Ringkasan")

        Spacer(modifier = Modifier.height(Spacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            StatItem(
                title = "Pemasukan",
                value = FormatUtils.formatRupiah(totalIncome),
                valueColor = ForestGreen,
                modifier = Modifier.weight(1f)
            )
            StatItem(
                title = "Pengeluaran",
                value = FormatUtils.formatRupiah(totalExpense),
                valueColor = BurntOrange,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            StatItem(
                title = "Kategori terbesar",
                value = topCategoryName,
                valueColor = Ink,
                modifier = Modifier.weight(1f)
            )
            StatItem(
                title = "Transaksi",
                value = "${totalTransactions}x",
                valueColor = Ink,
                modifier = Modifier.weight(1f)
            )
        }

        // Rincian kategori menyusul di bawah angka, dipisah garis rambut —
        // bukan kartu kedua di dalam satu layar.
        if (showCategoryList && aggregations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.md))
            HairlineDivider()
            Spacer(modifier = Modifier.height(Spacing.sm))

            Eyebrow(text = "Rincian per kategori")

            aggregations.forEachIndexed { index, agg ->
                CategoryRow(aggregation = agg, totalExpense = totalExpense)
                if (index < aggregations.lastIndex) HairlineDivider()
            }
        }
    }
}

/** Satu angka ringkasan: label mono di atas, nilai serif di bawah. */
@Composable
fun StatItem(title: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Eyebrow(text = title)
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = SerifFamily,
            color = valueColor
        )
    }
}

/** Baris kategori: nama, porsi terhadap pengeluaran, lalu nominalnya. */
@Composable
private fun CategoryRow(aggregation: CategoryAggregation, totalExpense: Long) {
    val (_, cleanCategory) = CategoryIconMapper.extractEmojiAndLabel(aggregation.category)
    val percentage = if (totalExpense > 0) {
        (aggregation.totalAmount.toDouble() / totalExpense) * 100
    } else {
        0.0
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = cleanCategory,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = String.format("%.1f%%", percentage),
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
            modifier = Modifier.padding(end = Spacing.sm)
        )
        Text(
            text = FormatUtils.formatRupiahCompact(aggregation.totalAmount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Ink
        )
    }
}
