package com.example.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.EditorialProgress
import com.example.ui.theme.Cream
import com.example.ui.theme.EyebrowTextStyle
import com.example.ui.theme.HairlineStrong
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.SerifFamily
import com.example.ui.theme.CornerRadius
import com.example.ui.theme.Spacing

/**
 * Layar muat aplikasi: krem, logo berbingkai, judul serif, dan satu garis
 * progres tipis. Menggantikan latar gelap + spinner cincin versi lama supaya
 * sama dengan palet editorial tab lain.
 */
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
                .background(Cream)
                .testTag("startup_screen"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                val logoShape = RoundedCornerShape(CornerRadius.xl)
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Logo Aplikasi",
                    modifier = Modifier
                        .size(84.dp)
                        .clip(logoShape)
                        .border(1.dp, HairlineStrong, logoShape)
                        .testTag("startup_logo")
                )

                Text(
                    text = "Nano Money",
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = SerifFamily,
                    color = Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("startup_title")
                )

                Text(
                    text = "MENCATAT KEUANGAN SEMUDAH CHAT TEMAN",
                    style = EyebrowTextStyle,
                    color = InkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("startup_tagline")
                )

                // Bar progres tipis; animasi mengisi dari kiri lalu diulang.
                val transition = rememberInfiniteTransition(label = "startup_line")
                val progress by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1400, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "startup_progress"
                )
                EditorialProgress(
                    progress = progress,
                    color = Ink,
                    modifier = Modifier
                        .width(140.dp)
                        .padding(top = Spacing.sm),
                    height = 3.dp,
                    testTag = "startup_loader"
                )
            }
        }
    }
}
