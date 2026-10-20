package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.BurntOrange
import com.example.ui.theme.CardCore
import com.example.ui.theme.CardVeil
import com.example.ui.theme.CornerRadius
import com.example.ui.theme.Cream
import com.example.ui.theme.CreamRaised
import com.example.ui.theme.EditorialTypography
import com.example.ui.theme.EyebrowTextStyle
import com.example.ui.theme.Hairline
import com.example.ui.theme.HairlineStrong
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.SerifFamily
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
    testTag: String = "",
    /**
     * Lebar penuh dengan label di kiri dan chip panah di kanan, seperti
     * `.hero-actions .btn { width: 100%; justify-content: space-between }`
     * di referensi. Tanpa ini, tombol selebar layar menyisakan ruang kosong
     * besar di kanan label.
     */
    expanded: Boolean = false
) {
    val container = if (style == EditorialButtonStyle.Filled) accent else Color.Transparent
    val contentColor = if (style == EditorialButtonStyle.Filled) Cream else accent

    Row(
        modifier = modifier
            .then(if (expanded) Modifier.fillMaxWidth() else Modifier)
            .alpha(if (enabled) 1f else 0.5f)
            // Tinggi minimum 44 dp: kalau chip panah dimatikan (tombol di dalam
            // dialog), tombol jadi terlalu pendek karena chip 26 dp itulah yang
            // biasanya menahan tingginya.
            .heightIn(min = 44.dp)
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
                start = 16.dp,
                end = if (trailingArrow) 6.dp else 16.dp,
                top = 6.dp,
                bottom = 6.dp
            )
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        // Tanpa chip panah, labelnya ditaruh di tengah pil. Sebelumnya label
        // selalu menempel kiri, jadi tombol selebar separuh dialog kelihatan
        // seperti pil raksasa dengan teks nyempil di pinggir.
        horizontalArrangement = when {
            expanded && trailingArrow -> Arrangement.SpaceBetween
            trailingArrow -> Arrangement.spacedBy(11.dp)
            else -> Arrangement.Center
        }
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
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(contentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NorthEast,
                    contentDescription = null,
                    tint = container,
                    modifier = Modifier.size(13.dp)
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

/**
 * Dialog dengan isi bertipografi editorial.
 *
 * `MaterialTheme` yang dipasang di luar `Dialog` tidak menyeberang ke isi
 * dialog (dialog dikomposisi di jendela sendiri), jadi skala tipenya
 * dipasang di dalam lambda `Dialog` lewat komponen ini.
 */
@Composable
fun EditorialDialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        MaterialTheme(typography = EditorialTypography) {
            content()
        }
    }
}

/**
 * Permukaan dialog editorial: krem, bingkai garis rambut, sudut bezel, judul
 * serif. Dipakai dialog-dialog tab Simpan supaya bentuknya sama dengan dialog
 * kelola kategori dan dialog konfirmasi lain.
 */
@Composable
fun EditorialDialogCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        shape = RoundedCornerShape(CornerRadius.bezel),
        colors = CardDefaults.cardColors(containerColor = CreamRaised),
        border = BorderStroke(1.dp, Hairline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Medium,
                color = Ink
            )
            content()
        }
    }
}

// ── Isian ─────────────────────────────────────────────────────────────────────
// Dipakai bersama tab Chat (lembar konfirmasi AI) dan dialog-dialog editorial,
// supaya dua isian sejenis tidak pernah kelihatan beda bentuk.

// Tinggi baku kontrol editorial. Tanpa ini kotak isian Material tampil ~52 dp,
// jadi lebih jangkung dari pil jenis (34 dp) dan tombol (44 dp) di sebelahnya.
private val FieldHeight = 44.dp

/** Isian berlabel: label mono kecil di atas kolom isian. */
@Composable
fun LabeledField(
    label: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Eyebrow(text = label)
        content()
    }
}

/** Isian teks editorial: latar krem, bingkai rambut, fokus jingga. */
@Composable
fun EditorialTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    testTag: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val shape = RoundedCornerShape(CornerRadius.md)
    var focused by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
        cursorBrush = SolidColor(BurntOrange),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
            .fillMaxWidth()
            // Kotaknya digambar sendiri, bukan OutlinedTextField: isian Material
            // punya tinggi bawaan 56 dp dan memotong baris teks kalau dipaksa
            // 44 dp. Bentuknya disamakan dengan TapField supaya isian teks dan
            // isian ketuk di form yang sama selalu tampil sama.
            .height(FieldHeight)
            .clip(shape)
            // Latar isian memakai --bg-2 (CreamRaised), bukan latar halaman,
            // supaya kotaknya tetap terbaca saat form tidak dibungkus kartu.
            .background(CreamRaised)
            .border(1.dp, if (focused) BurntOrange else HairlineStrong, shape)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .testTag(testTag),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = InkMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                }
                innerTextField()
            }
        }
    )
}

/** Isian yang cuma bisa diketuk: buka daftar pilihan atau pemilih tanggal. */
@Composable
fun TapField(
    text: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FieldHeight)
            // Isian ketuk dulu 56 dp, jadi tampil lebih jangkung daripada
            // isian teks di form yang sama; sekarang dua-duanya 44 dp.
            .clip(RoundedCornerShape(CornerRadius.md))
            .background(CreamRaised)
            .border(1.dp, HairlineStrong, RoundedCornerShape(CornerRadius.md))
            .clickable(onClickLabel = text, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = InkMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Warna baku kolom isian editorial, dipakai semua isian. */
@Composable
fun editorialFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    disabledTextColor = InkMuted,
    focusedPlaceholderColor = InkMuted,
    unfocusedPlaceholderColor = InkMuted,
    disabledPlaceholderColor = InkMuted,
    focusedBorderColor = BurntOrange,
    unfocusedBorderColor = HairlineStrong,
    disabledBorderColor = Hairline,
    focusedContainerColor = Cream,
    unfocusedContainerColor = Cream,
    disabledContainerColor = Cream,
    cursorColor = BurntOrange
)

/**
 * Baris sakelar: judul dan keterangan di kiri, [Switch] di kanan.
 *
 * Tab Simpan punya tiga sakelar (PIN, sinkronisasi Firestore, cadangan
 * otomatis). Ketiganya memakai baris ini supaya tidak ada satu pun yang
 * tampil beda dari yang lain.
 */
@Composable
fun EditorialToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Ink
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Cream,
                checkedTrackColor = Ink,
                uncheckedThumbColor = CreamRaised,
                uncheckedTrackColor = HairlineStrong,
                uncheckedBorderColor = HairlineStrong
            ),
            modifier = if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier
        )
    }
}
