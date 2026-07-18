package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CornerRadius
import com.example.ui.theme.GhostWhite
import com.example.ui.theme.Spacing
import com.example.ui.theme.TranslucentGlass

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(CornerRadius.lg),
    background: Color = TranslucentGlass,
    borderAlpha: Pair<Float, Float> = 0.18f to 0.02f,
    padding: Dp = Spacing.lg,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GhostWhite.copy(alpha = borderAlpha.first),
                        GhostWhite.copy(alpha = borderAlpha.second)
                    )
                ),
                shape = shape
            )
            .padding(padding),
        content = content
    )
}
