package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CardCore
import com.example.ui.theme.CardVeil
import com.example.ui.theme.CornerRadius
import com.example.ui.theme.Cream
import com.example.ui.theme.EyebrowTextStyle
import com.example.ui.theme.Hairline
import com.example.ui.theme.HairlineStrong
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.Spacing
import com.example.ui.theme.StampTextStyle
import com.example.ui.theme.TagTextStyle

/**
 * Komponen dasar untuk palet editorial.
 *
 * Semua bentuk dan ukuran di sini mengikuti referensi portfolio
 * (`css/styles.css`); nama fungsinya sengaja mengikuti nama kelas CSS-nya
 * supaya gampang dicocokkan:
 *
 *  - [BezelCard]  -> `.bezel` + `.core`   (kartu dua lapis)
 *  - [EditorialButton] -> `.btn` + `.arrow` (pil dengan chip panah)
 *  - [EditorialPill] -> `.nav-links a` yang terpilih
 *  - [Eyebrow]    -> `.eyebrow`, `.kpi-label`, `.slip-kicker`
 *  - [Tag]        -> `.cap-tag`, `.work-tech li`
 *  - [Stamp]      -> `.slip-stamp`
 *  - [HairlineDivider] / [DashedDivider] -> `--line` dan `border-bottom: dashed`
 */

/** Lapisan luar bingkai: putih tipis di atas krem, sudut 32 dp. */
private val BezelShape = RoundedCornerShape(CornerRadius.bezel)
private val CoreShape = RoundedCornerShape(CornerRadius.bezelCore)

/**
 * Kartu dua lapis (`.bezel` + `.core`): bingkai tipis berisi inti terang yang
 * agak masuk ke dalam. Garis putih di tepi atas inti meniru
 * `inset 0 1px 0 rgba(255,255,255,.85)` di referensi.
 */
@Composable
fun BezelCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = Spacing.card,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(BezelShape)
            .background(CardVeil)
            .border(1.dp, Hairline, BezelShape)
            .padding(Spacing.bezel)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CoreShape)
                .background(CardCore)
                .drawBehind {
                    drawRect(
                        color = Color.White.copy(alpha = 0.85f),
                        size = Size(size.width, 1.dp.toPx())
                    )
                }
                .padding(contentPadding),
            content = content
        )
    }
}

/** Varian tombol: terisi tinta (aksi utama) atau transparan berbingkai. */
enum class EditorialButtonStyle { Filled, Ghost }

/**
 * Tombol pil (`.btn`). Varian [EditorialButtonStyle.Filled] memakai latar tinta
 * dengan chip panah krem di ujung kanan, persis tombol utama referensi.
 */
@Composable
fun EditorialButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: EditorialButtonStyle = EditorialButtonStyle.Filled,
    trailingArrow: Boolean = true,
    enabled: Boolean = true,
    accent: Color = Ink,
    testTag: String = ""
) {
    val container = if (style == EditorialButtonStyle.Filled) accent else Color.Transparent
    val contentColor = if (style == EditorialButtonStyle.Filled) Cream else accent

    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            // Tinggi minimum 48 dp: kalau chip panah dimatikan (tombol di dalam
            // dialog), tombol jadi terlalu pendek karena chip 30 dp itulah yang
            // biasanya menahan tingginya.
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(CornerRadius.pill))
            .background(container)
            .then(
                if (style == EditorialButtonStyle.Ghost) {
                    Modifier.border(1.dp, HairlineStrong, RoundedCornerShape(CornerRadius.pill))
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(
                start = 20.dp,
                end = if (trailingArrow) 7.dp else 20.dp,
                top = 7.dp,
                bottom = 7.dp
            )
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
        if (trailingArrow) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(contentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NorthEast,
                    contentDescription = null,
                    tint = container,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/**
 * Tautan pil seperti menu di referensi. Dipakai untuk pilihan filter yang
 * saling meniadakan; yang terpilih terisi tinta, yang lain hanya berbingkai.
 */
@Composable
fun EditorialPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Gaya teks label; default sama dengan pil nav di referensi. */
    textStyle: TextStyle? = null,
    /**
     * Jarak teks ke tepi pil. Dikecilkan kalau pil dibagi rata selebar baris,
     * supaya label terpanjang tidak turun baris di layar sempit.
     */
    contentPadding: Dp = 14.dp,
    testTag: String = ""
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CornerRadius.pill))
            .background(if (selected) Ink else Color.Transparent)
            .then(
                if (selected) Modifier
                else Modifier.border(1.dp, Hairline, RoundedCornerShape(CornerRadius.pill))
            )
            .clickable { onClick() }
            .padding(horizontal = contentPadding, vertical = 8.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = textStyle ?: MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Cream else InkMuted
        )
    }
}

/** Label mono kecil di atas sebuah blok. */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = InkMuted
) {
    Text(text = text, modifier = modifier, style = EyebrowTextStyle, color = color)
}

/** Chip label berbingkai tipis. */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = InkMuted,
    borderColor: Color = HairlineStrong
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(text = text, style = TagTextStyle, color = color)
    }
}

/** Stempel kecil berbingkai di kaki kartu (`.slip-stamp`). */
@Composable
fun Stamp(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = InkMuted
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text = text, style = StampTextStyle, color = color)
    }
}

/** Garis rambut pemisah (`1px solid --line`). */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier, color: Color = Hairline) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

/** Garis rambut putus-putus, seperti pemisah baris di `.slip-log`. */
@Composable
fun DashedDivider(modifier: Modifier = Modifier, color: Color = Hairline) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                )
            }
    )
}

/** Bar progres tipis dengan jalur bergaris rambut. */
@Composable
fun EditorialProgress(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    track: Color = Hairline,
    testTag: String = ""
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(track)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(color)
                .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
        )
    }
}
