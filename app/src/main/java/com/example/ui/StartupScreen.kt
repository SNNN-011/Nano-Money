package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*

@Composable
fun StartupScreen(
    visible: Boolean,
    onFinished: () -> Unit = {}
) {
    var isLaunching by remember { mutableStateOf(visible) }

    LaunchedEffect(visible) {
        if (visible) {
            isLaunching = true
            kotlinx.coroutines.delay(2200)
            isLaunching = false
            onFinished()
        } else {
            isLaunching = false
        }
    }

    AnimatedVisibility(
        visible = isLaunching,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(durationMillis = 650))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MidnightAbyss)
                .testTag("startup_screen"),
            contentAlignment = Alignment.Center
        ) {
            // Ambient atmospheric radial glow spots matching the main dashboard background perfectly
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(SteelBlueGlow.copy(alpha = 0.12f), Color.Transparent),
                            center = Offset(500f, 300f),
                            radius = 1000f
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(SteelBlueGlow.copy(alpha = 0.08f), Color.Transparent),
                            center = Offset(100f, 1600f),
                            radius = 1200f
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(NeonViolet.copy(alpha = 0.08f), Color.Transparent),
                            center = Offset(1500f, 1000f),
                            radius = 900f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo
                val logoPainter = painterResource(id = R.drawable.logo)
                Image(
                    painter = logoPainter,
                    contentDescription = "Logo Aplikasi",
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .testTag("startup_logo")
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Title
                Text(
                    text = "Nano Money",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 30.sp,
                        letterSpacing = 1.5.sp
                    ),
                    color = GhostWhite,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag("startup_title")
                )

                // Slogan
                Text(
                    text = "mencatat keuangan semudah chat teman".uppercase(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontSize = 11.sp,
                        lineHeight = 18.sp
                    ),
                    color = GhostWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(bottom = 36.dp)
                        .testTag("startup_tagline")
                )

                // Modern premium custom rotating loader
                val infiniteTransition = rememberInfiniteTransition(label = "startup_loader_anim")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rotation"
                )
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.82f,
                    targetValue = 1.22f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )

                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("startup_loader"),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing background core
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale, alpha = pulseAlpha)
                            .background(SteelBlue.copy(alpha = 0.35f), shape = CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(GhostWhite, shape = CircleShape)
                    )

                    // Spinning outer split-arc ring
                    Canvas(
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer(rotationZ = rotation)
                    ) {
                        val strokeWidth = 3.dp.toPx()
                        // Main arc using steel blue highlights
                        drawArc(
                            color = GhostWhite,
                            startAngle = 0f,
                            sweepAngle = 110f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        // Supporting transparent arc
                        drawArc(
                            color = SteelBlue,
                            startAngle = 180f,
                            sweepAngle = 110f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}
