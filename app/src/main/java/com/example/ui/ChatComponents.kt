package com.example.ui

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FinancialRecord
import com.example.ui.theme.*
import com.example.ui.util.CategoryIconMapper

@Composable
fun UserMessageBubble(text: String) {
    val bubbleShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp)
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
                    .background(Color(0xFFFFFFFF).copy(alpha = 0.12f))
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
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = text,
                    color = GhostWhite,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun UserImageMessageBubble(bitmap: Bitmap) {
    val bubbleShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp)
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
                    .background(Color(0xFFFFFFFF).copy(alpha = 0.12f))
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
                        .sizeIn(maxWidth = 160.dp, maxHeight = 160.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
fun AiMessageBubble(text: String, record: FinancialRecord?) {
    val bubbleShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp)
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
                    .background(TranslucentGlass)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                GhostWhite.copy(alpha = 0.2f),
                                GhostWhite.copy(alpha = 0.02f)
                            )
                        ),
                        shape = bubbleShape
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = text,
                    color = GhostWhite,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }

            // Confirmation transaction card
            if (record != null) {
                Spacer(modifier = Modifier.height(8.dp))
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        border = BorderStroke(1.dp, TranslucentBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Pill: Room Saved Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(NeonGreen.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
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
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Description and Amount
            Column {
                Text(
                    text = record.description,
                    fontWeight = FontWeight.Bold,
                    color = GhostWhite,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                val isIncome = record.type == "income"
                Text(
                    text = "${if (isIncome) "+" else "-"} ${FormatUtils.formatRupiah(record.amount)}",
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isIncome) NeonGreen else NeonRed,
                    fontSize = 18.sp
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
                            fontSize = 14.sp
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
                        color = GhostWhite.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
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
                        color = GhostWhite.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
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
            .testTag("typing_indicator"),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp))
                    .background(Color(0xFF1E1E1E))
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GhostWhite.copy(alpha = dot1Alpha)))
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GhostWhite.copy(alpha = dot2Alpha)))
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GhostWhite.copy(alpha = dot3Alpha)))
    }
}
