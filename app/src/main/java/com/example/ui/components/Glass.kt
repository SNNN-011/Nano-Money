package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CornerRadius
import com.example.ui.theme.Ink

/**
 * Material kaca dari Glassy UI (tema terang), dipindahkan ke Compose.
 *
 * Angkanya diambil apa adanya dari token pustaka itu supaya hasilnya sama:
 * isi kaca jernih `rgba(255,255,255,.18)`, tinted `.55`, kontrol `.72`,
 * overlay dialog `.82`; rim putih `.55`; kilau atas `.90`; track sakelar
 * `rgba(118,118,128,.20)`; sudut 16/24.
 *
 * Satu hal yang tidak bisa ditiru: Glassy UI memblur isi layar di belakang
 * kaca. Compose tidak punya blur latar, jadi di sini kaca hanya tembus
 * pandang. Karena latar aplikasi krem terang, tepi kaca diberi garis tinta
 * tipis (`GlassEdge`) supaya bentuknya tetap terbaca.
 */
val GlassClearFill: Color = Color.White.copy(alpha = 0.18f)
val GlassTintedFill: Color = Color.White.copy(alpha = 0.55f)
val GlassControlFill: Color = Color.White.copy(alpha = 0.72f)
val GlassOverlayFill: Color = Color.White.copy(alpha = 0.82f)
val GlassRim: Color = Color.White.copy(alpha = 0.55f)
val GlassRimHighlight: Color = Color.White.copy(alpha = 0.90f)
val GlassEdge: Color = Ink.copy(alpha = 0.10f)
private val GlassTrackColor: Color = Color(0xFF767680).copy(alpha = 0.20f)

/** Tingkat beku kaca. [Overlay] paling pekat supaya teks dialog tetap terbaca. */
enum class GlassPreset { Clear, Tinted, Control, Overlay }

/**
 * Permukaan kaca: bayangan lembut, isi tembus pandang, kilau di tepi atas,
 * dan garis tipis sebagai batas bentuk. [tint] dipakai tombol utama: kacanya
 * diwarnai tinta, bukan putih.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    preset: GlassPreset = GlassPreset.Tinted,
    shape: Shape = RoundedCornerShape(CornerRadius.lg),
    tint: Color? = null,
    raised: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val fill = tint ?: when (preset) {
        GlassPreset.Clear -> GlassClearFill
        GlassPreset.Tinted -> GlassTintedFill
        GlassPreset.Control -> GlassControlFill
        GlassPreset.Overlay -> GlassOverlayFill
    }
    // Kaca berwarna tinta tidak boleh dikilau seterang kaca putih, nanti
    // bagian atas tombol jadi kelabu dan teks kremnya kehilangan kontras.
    val sheen = if (tint != null) Color.White.copy(alpha = 0.20f) else GlassRimHighlight.copy(alpha = 0.55f)

    Box(
        modifier = modifier
            .shadow(
                elevation = if (raised) 18.dp else 4.dp,
                shape = shape,
                ambientColor = Ink.copy(alpha = 0.12f),
                spotColor = Ink.copy(alpha = 0.18f)
            )
            .clip(shape)
            .background(fill)
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(sheen, Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.40f
                    )
                )
            }
            .border(
                width = 1.dp,
                color = if (tint != null) GlassRim else GlassEdge,
                shape = shape
            ),
        content = content
    )
}

/** Versi [GlassSurface] untuk komponen yang menggambar sendiri (menu, pil). */
@Composable
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(CornerRadius.md),
    fill: Color = GlassOverlayFill,
    raised: Boolean = true
): Modifier = this
    .shadow(
        elevation = if (raised) 12.dp else 0.dp,
        shape = shape,
        ambientColor = Ink.copy(alpha = 0.12f),
        spotColor = Ink.copy(alpha = 0.18f)
    )
    .clip(shape)
    .background(fill)
    .border(1.dp, GlassEdge, shape)

/**
 * Sakelar kaca: track abu tembus pandang, isi tinta saat menyala, dan kenop
 * putih yang berubah jadi kaca bening selagi ditekan — sama seperti
 * `glass-switch` di Glassy UI (ukuran `md`: 60x28, kenop 36x24).
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = ""
) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "glassSwitchProgress"
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val trackWidth = 60.dp
    val trackHeight = 28.dp
    val knobWidth = 36.dp
    val knobHeight = 24.dp
    val inset = 2.dp
    val travel: Dp = trackWidth - knobWidth - inset * 2

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .alpha(if (enabled) 1f else 0.4f)
            .shadow(
                elevation = 2.dp,
                shape = CircleShape,
                ambientColor = Ink.copy(alpha = 0.10f),
                spotColor = Ink.copy(alpha = 0.14f)
            )
            .clip(CircleShape)
            .background(GlassTrackColor)
            .background(Ink.copy(alpha = progress))
            .border(1.dp, GlassEdge, CircleShape)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interaction,
                indication = null
            )
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = inset + travel * progress, y = inset)
                .size(width = knobWidth, height = knobHeight)
                .shadow(
                    elevation = 2.dp,
                    shape = CircleShape,
                    ambientColor = Ink.copy(alpha = 0.16f),
                    spotColor = Ink.copy(alpha = 0.22f)
                )
                .clip(CircleShape)
                .background(
                    if (pressed) {
                        // Di bawah jari, kenop jadi kaca: isi track ikut tembus.
                        SolidColor(Color.White.copy(alpha = 0.55f))
                    } else {
                        Brush.verticalGradient(listOf(Color.White, Color(0xFFF2EDE4)))
                    }
                )
                .border(1.dp, GlassRim, CircleShape)
        )
    }
}
