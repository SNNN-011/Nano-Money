package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.style.TextAlign

private val DarkColorScheme = darkColorScheme(
    primary = NeonViolet,
    secondary = Comet,
    tertiary = CelestialLight,
    background = MidnightAbyss,
    surface = MidnightAbyss,
    onPrimary = GhostWhite,
    onBackground = GhostWhite,
    onSurface = Comet,
    surfaceVariant = TranslucentForm,
    onSurfaceVariant = ArcticMist,
    error = Color(0xFFFF6B6B),
    onError = GhostWhite
)

private val LightColorScheme = DarkColorScheme // Only Dark Theme for AuthKit

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    icon: ImageVector? = null,
    testTag: String = "",
    fillMaxWidth: Boolean = true,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 10.dp
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = TranslucentGlass),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    GhostWhite.copy(alpha = 0.2f),
                    GhostWhite.copy(alpha = 0.02f)
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .padding(4.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (isActive) {
                        Modifier.background(
                            brush = Brush.verticalGradient(
                                colors = listOf(SteelBlue, SteelBlue.copy(alpha = 0.7f))
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    } else {
                        Modifier.background(Color.Transparent)
                    }
                )
                .clickable { onClick() }
                .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = (if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isActive) MidnightAbyss else GhostWhite.copy(alpha = 0.8f),
                        modifier = Modifier.padding(end = 6.dp).size(16.dp)
                    )
                }
                Text(
                    text = text,
                    color = if (isActive) MidnightAbyss else GhostWhite.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun PremiumSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 36.dp else 0.dp,
        label = "thumbOffset"
    )
    
    Card(
        modifier = modifier
            .width(80.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = TranslucentGlass),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    GhostWhite.copy(alpha = 0.2f),
                    GhostWhite.copy(alpha = 0.02f)
                )
            )
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(3.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // Background labels
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "OFF",
                    color = GhostWhite.copy(alpha = 0.35f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "ON",
                    color = GhostWhite.copy(alpha = 0.35f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.Center
                )
            }
            
            // Sliding thumb
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .width(38.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (checked) SteelBlue else Comet.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (checked) "ON" else "OFF",
                    color = MidnightAbyss,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


