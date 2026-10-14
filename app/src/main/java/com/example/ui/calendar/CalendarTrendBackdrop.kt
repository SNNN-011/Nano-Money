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
import com.example.ui.theme.SteelBlue

/**
 * Grafik tren yang digambar di belakang grid kalender.
 *
 * Sengaja dibuat redup (alpha rendah) dan tanpa label: fungsinya memberi kesan
 * bentuk pengeluaran di belakang tanggal, bukan menjadi elemen bacaan utama. Angka
 * dan tanggal tetap di atasnya, jadi keterbacaan tidak boleh terganggu.
 */
@Composable
fun CalendarTrendBackdrop(
    trends: List<DailyAggregation>,
    modifier: Modifier = Modifier
) {
    if (trends.isEmpty()) return

    Canvas(modifier = modifier) {
        val lineAlpha = 0.28f
        val fillAlpha = 0.10f
        val width = size.width
        val height = size.height
        val maxAmount = (trends.maxOfOrNull { it.totalAmount }?.toFloat() ?: 0f).coerceAtLeast(1f)

        // Sisakan ruang di kiri dan bawah supaya garis tidak menempel tepi card.
        val leftInset = 6f
        val bottomInset = 4f
        val usableWidth = (width - leftInset * 2).coerceAtLeast(1f)
        val usableHeight = (height - bottomInset * 2).coerceAtLeast(1f)

        val points = if (trends.size > 1) {
            val stepX = usableWidth / (trends.size - 1)
            trends.mapIndexed { index, agg ->
                val x = leftInset + index * stepX
                val y = height - bottomInset - (agg.totalAmount.toFloat() / maxAmount) * usableHeight
                Offset(x, y)
            }
        } else {
            val x = leftInset + usableWidth / 2f
            val y = height - bottomInset - (trends.first().totalAmount.toFloat() / maxAmount) * usableHeight
            listOf(Offset(x, y))
        }

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
            fillPath.lineTo(points.last().x, height)
            fillPath.lineTo(points.first().x, height)
            fillPath.close()

            drawPath(
                path = fillPath,
                color = SteelBlue.copy(alpha = fillAlpha)
            )
            drawPath(
                path = path,
                color = SteelBlue.copy(alpha = lineAlpha),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        } else {
            drawCircle(
                color = SteelBlue.copy(alpha = lineAlpha),
                radius = 3.dp.toPx(),
                center = points.first()
            )
        }

        // Titik penanda hanya digambar kalau jumlahnya masih masuk akal, supaya
        // mode "Semua Bulan" yang punya banyak titik tidak jadi kotak padat.
        if (points.size in 2..40) {
            points.forEach { point ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.22f),
                    radius = 2.dp.toPx(),
                    center = point
                )
            }
        }
    }
}
