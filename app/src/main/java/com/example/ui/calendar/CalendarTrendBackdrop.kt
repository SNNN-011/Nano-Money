package com.example.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.analysis.DailyAggregation

/**
 * Dua garis yang saling menindih di belakang grid kalender.
 *
 * Pengeluaran merah, pemasukan hijau. Sengaja redup (alpha rendah) dan tanpa
 * label: fungsinya memberi bentuk Bipolar di belakang tanggal, bukan elemen bacaan
 * utama. Angka dan tanggal tetap di atasnya.
 */
@Composable
fun CalendarTrendBackdrop(
    expenseTrends: List<DailyAggregation>,
    incomeTrends: List<DailyAggregation>,
    modifier: Modifier = Modifier
) {
    if (expenseTrends.isEmpty() && incomeTrends.isEmpty()) return

    Canvas(modifier = modifier) {
        val leftInset = 6f
        val bottomInset = 4f
        val usableWidth = (size.width - leftInset * 2).coerceAtLeast(1f)
        val usableHeight = (size.height - bottomInset * 2).coerceAtLeast(1f)

        // Skala dipakai bersama dua garis supaya perbandingannya jujur: kalau
        // masing-masing dinormalkan sendiri, pendapatan selalu terlihat lebih tinggi
        // dari pengeluaran padahal tidak.
        val maxAmount = maxOf(
            expenseTrends.maxOfOrNull { it.totalAmount } ?: 0L,
            incomeTrends.maxOfOrNull { it.totalAmount } ?: 0L
        ).toFloat().coerceAtLeast(1f)

        // Keranjang tanggal disamakan lebih dulu. Kalau income tidak punya entri
        // pada hari yang ada pengeluaran, titiknya harus tetap dibuat, kalau tidak
        // kedua garis akan menggeser dan tidak benar-benar saling menindih.
        val buckets = (expenseTrends.map { it.dateString } + incomeTrends.map { it.dateString })
            .distinct()
            .size
            .coerceAtLeast(1)

        val pointsFor: (List<DailyAggregation>) -> List<Offset> = { trends ->
            if (trends.isEmpty()) {
                emptyList()
            } else if (trends.size == 1) {
                val y = size.height - bottomInset -
                    (trends.first().totalAmount.toFloat() / maxAmount) * usableHeight
                listOf(Offset(leftInset + usableWidth / 2f, y))
            } else {
                val stepX = usableWidth / (trends.size - 1)
                trends.mapIndexed { index, agg ->
                    val x = leftInset + index * stepX
                    val y = size.height - bottomInset -
                        (agg.totalAmount.toFloat() / maxAmount) * usableHeight
                    Offset(x, y)
                }
            }
        }

        drawSeries(pointsFor(incomeTrends), CalPemasukan, 0.22f, 0.08f)
        // Pengeluaran digambar belakangan supaya tidak tertutup garis pemasukan.
        drawSeries(pointsFor(expenseTrends), CalPengeluaran, 0.28f, 0.10f)

        // Poin penanda hanya bila jumlahnya masih terbaca; mode "Semua Bulan"
        // bisa punya banyak titik dan akan berubah jadi kotak padat.
        if (buckets in 2..40) {
            pointsFor(expenseTrends).forEach { point ->
                drawCircle(color = CalTeksUtama.copy(alpha = 0.22f), radius = 2.dp.toPx(), center = point)
            }
            pointsFor(incomeTrends).forEach { point ->
                drawCircle(color = CalTeksUtama.copy(alpha = 0.16f), radius = 2.dp.toPx(), center = point)
            }
        }
    }
}

/** Menggambar satu garis beserta areanya, dengan warna dan tingkat transparansi sendiri. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    points: List<Offset>,
    color: Color,
    lineAlpha: Float,
    fillAlpha: Float
) {
    if (points.isEmpty()) return

    if (points.size > 1) {
        val path = Path()
        path.moveTo(points.first().x, points.first().y)
        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val cx = (p0.x + p1.x) / 2
            path.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
        }

        val fillPath = Path()
        fillPath.addPath(path)
        fillPath.lineTo(points.last().x, size.height)
        fillPath.lineTo(points.first().x, size.height)
        fillPath.close()

        drawPath(path = fillPath, color = color.copy(alpha = fillAlpha))
        drawPath(
            path = path,
            color = color.copy(alpha = lineAlpha),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    } else {
        drawCircle(color = color.copy(alpha = lineAlpha), radius = 3.dp.toPx(), center = points.first())
    }
}
