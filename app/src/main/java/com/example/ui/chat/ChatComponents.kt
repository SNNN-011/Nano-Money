package com.example.ui.chat

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.FinancialRecord
import com.example.ui.theme.*
import com.example.ui.config.FormatUtils
import com.example.ui.util.CategoryIconMapper

@Composable
fun UserMessageBubble(text: String) {
    val bubbleShape = RoundedCornerShape(topStart = CornerRadius.lg, topEnd = CornerRadius.lg, bottomStart = CornerRadius.lg)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bubble_user"),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(GhostWhite.copy(alpha = 0.12f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                GhostWhite.copy(alpha = 0.25f),
                                GhostWhite.copy(alpha = 0.03f)
                            )
                        ),
                        shape = bubbleShape
                    )
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Text(
                    text = text,
                    color = GhostWhite,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun UserImageMessageBubble(bitmap: Bitmap) {
    val bubbleShape = RoundedCornerShape(topStart = CornerRadius.lg, topEnd = CornerRadius.lg, bottomStart = CornerRadius.lg)

    // Ukuran gambar ditetapkan eksplisit, dibatasi 250dp x 330dp dengan rasio
    // aslinya tetap. Sebelumnya lebar penuh gelembung (85% layar) sehingga foto
    // hampir menutupi seluruh layar.
    //
    // Image di Compose itu Spacer + drawBehind, dan Spacer hanya mengambil
    // maxWidth/maxHeight kalau sisi itu berukuran fixed. Jadi tidak bisa
    // mengandalkan fillMaxWidth (tingginya 0) atau aspectRatio + heightIn
    // (heightIn menurunkan plafon, bukan menaikkan tinggi). `size` dengan dua
    // nilai pasti adalah satu-satunya cara yang aman.
    val maxWidth = 250.dp
    val maxHeight = 330.dp
    val ratio = if (bitmap.height > 0) bitmap.width.toFloat() / bitmap.height.toFloat() else 1f
    val imageWidth = minOf(maxWidth, maxHeight * ratio)
    val imageHeight = imageWidth / ratio

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bubble_user_image"),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(GhostWhite.copy(alpha = 0.12f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                GhostWhite.copy(alpha = 0.25f),
                                GhostWhite.copy(alpha = 0.03f)
                            )
                        ),
                        shape = bubbleShape
                    )
                    .padding(6.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Struk Belanjaan",
                    modifier = Modifier
                        .size(width = imageWidth, height = imageHeight)
                        .clip(RoundedCornerShape(CornerRadius.md)),
                    contentScale = ContentScale.Fit,
                    // Medium memakai mipmap, jadi gambar yang diperkecil tetap
                    // tajam (bawaan Low = bilinear biasa, terlihat kabur).
                    filterQuality = FilterQuality.Medium
                )
            }
        }
    }
}

@Composable
fun AiMessageBubble(
    text: String,
    record: FinancialRecord?,
    isError: Boolean = false,
    onRetry: (() -> Unit)? = null
) {
    val bubbleShape = RoundedCornerShape(topStart = CornerRadius.lg, topEnd = CornerRadius.lg, bottomEnd = CornerRadius.lg)

    // Kegagalan diberi batas merah supaya beda jelas dari jawaban AI biasa.
    val bubbleBorder = if (isError) {
        Modifier.border(1.dp, NeonRed.copy(alpha = 0.45f), bubbleShape)
    } else {
        Modifier.border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    GhostWhite.copy(alpha = 0.2f),
                    GhostWhite.copy(alpha = 0.02f)
                )
            ),
            shape = bubbleShape
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bubble_ai"),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(if (isError) NeonRed.copy(alpha = 0.10f) else TranslucentGlass)
                    .then(bubbleBorder)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    if (isError) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = NeonRed,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                    }
                    Text(
                        text = text,
                        color = GhostWhite,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Tombol ulangi hanya untuk kegagalan yang teksnya masih bisa dikirim ulang.
            if (onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag("retry_ai_button"),
                    contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs),
                    colors = ButtonDefaults.textButtonColors(contentColor = NeonRed)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = "Coba Lagi",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Confirmation transaction card
            if (record != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                ConfirmationTransactionCard(record = record)
            }
        }
    }
}

@Composable
fun ConfirmationTransactionCard(record: FinancialRecord) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .testTag("confirmation_card"),
        shape = RoundedCornerShape(CornerRadius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        border = BorderStroke(1.dp, TranslucentBorder)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Header Pill: Room Saved Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(NeonGreen.copy(alpha = 0.15f))
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = NeonGreen,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Tersimpan",
                    color = NeonGreen,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            // Description and Amount
            Column {
                Text(
                    text = record.description,
                    color = GhostWhite,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                val isIncome = record.type == "income"
                Text(
                    text = "${if (isIncome) "+" else "-"} ${FormatUtils.formatRupiah(record.amount)}",
                    color = if (isIncome) NeonGreen else NeonRed,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                )
            }

            HorizontalDivider(color = GhostWhite.copy(alpha = 0.1f))

            // Metadata Detail Grid (Kategori, Tanggal, Notes)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Category
                val (parsedEmoji, cleanCategory) = CategoryIconMapper.extractEmojiAndLabel(record.category)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (parsedEmoji != null) {
                        Text(
                            text = parsedEmoji,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Icon(
                            imageVector = CategoryIconMapper.getIcon(record.category),
                            contentDescription = "Kategori",
                            tint = SteelBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = cleanCategory,
                        color = GhostWhite.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Date
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Tanggal",
                        tint = SteelBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = FormatUtils.formatDate(record.date),
                        color = GhostWhite.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun TypingIndicatorRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Status ini tidak terlihat mata, jadi harus dibacakan ke TalkBack.
            .semantics { stateDescription = "Asisten AI sedang menyiapkan jawaban" }
            .testTag("typing_indicator"),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = CornerRadius.lg,
                            topEnd = CornerRadius.lg,
                            bottomEnd = CornerRadius.lg
                        )
                    )
                    .background(SurfaceElevated)
            ) {
                TypingIndicatorBubble()
            }
        }
    }
}

@Composable
fun TypingIndicatorBubble() {
    val transition = rememberInfiniteTransition(label = "typing")
    val dot1Alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.2f at 0
                1.0f at 200
                0.2f at 400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1"
    )
    val dot2Alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.2f at 150
                1.0f at 350
                0.2f at 550
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2"
    )
    val dot3Alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.2f at 300
                1.0f at 500
                0.2f at 600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot3"
    )

    Row(
        modifier = Modifier
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GhostWhite.copy(alpha = dot1Alpha)))
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GhostWhite.copy(alpha = dot2Alpha)))
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GhostWhite.copy(alpha = dot3Alpha)))
    }
}

@Composable
fun ScanningReceiptIndicatorRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md)
            .semantics { stateDescription = "Sedang membaca struk belanja dengan AI" }
            .testTag("scanning_receipt_indicator"),
        contentAlignment = Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = CornerRadius.xl, bottomStart = CornerRadius.xl, bottomEnd = CornerRadius.xl)),
            colors = CardDefaults.cardColors(containerColor = SurfaceDeep),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SteelBlue.copy(alpha = 0.50f),
                        SteelBlue.copy(alpha = 0.10f)
                    )
                )
            )
        ) {
            val transition = rememberInfiniteTransition(label = "scanning_receipt")
            
            // Pulsing scale for active AI scanner icon
            val iconScale by transition.animateFloat(
                initialValue = 0.9f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scanner_icon_scale"
            )
            
            // Rotating high-tech scanning ring effect
            val ringRotation by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "scanner_ring_rotation"
            )

            // Sweep of neon laser line
            val laserOffset by transition.animateFloat(
                initialValue = -25f,
                targetValue = 25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scanner_laser_offset"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Futuristic Glowing Scanner Graphic
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceViolet),
                    contentAlignment = Alignment.Center
                ) {
                    // Moving scan line (laser) drawn behind/above
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .offset(y = laserOffset.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        SteelBlue,
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    
                    // Rotating futuristic frame
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .drawBehind {
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(
                                            SteelBlue.copy(alpha = 0.1f),
                                            SteelBlue,
                                            SteelBlue.copy(alpha = 0.7f),
                                            SteelBlue.copy(alpha = 0.1f)
                                        )
                                    ),
                                    startAngle = ringRotation,
                                    sweepAngle = 280f,
                                    useCenter = false,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 2.dp.toPx()
                                    )
                                )
                            }
                    )
                    
                    // Pulsing main receipt list icon
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = "Fitur OCR / Membaca Struk",
                        tint = SteelBlue,
                        modifier = Modifier
                            .size(24.dp)
                            .scale(iconScale)
                    )
                }

                Spacer(modifier = Modifier.width(Spacing.md))

                // Reassuring copy and mini progress bar
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Memproses struk belanja Anda...",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = GhostWhite
                            )
                        )
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Kecerdasan Buatan",
                            tint = SteelBlue,
                            modifier = Modifier
                                .size(14.dp)
                                .scale(iconScale)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Membaca nominal belanja, tanggal & rincian transaksi menggunakan AI. Mohon tidak menutup layar ini ya 😊",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = GhostWhite.copy(alpha = 0.75f)
                        )
                    )
                }
            }
        }
    }
}
