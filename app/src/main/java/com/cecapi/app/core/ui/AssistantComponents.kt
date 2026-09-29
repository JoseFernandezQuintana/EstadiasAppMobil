package com.cecapi.app.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiBackground
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.CecapiWarning

/**
 * The microphone is the main control of the whole app, so it takes over half the screen:
 * a huge target is easy to find by touch without seeing anything. Used on the home screen
 * and on the signed-in dashboard so both feel the same.
 */
@Composable
fun MicPad(
    listening: Boolean,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDoubleTap: (() -> Unit)? = null,
) {
    val pulse by rememberInfiniteTransition(label = "mic-pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "mic-pulse-scale",
    )
    val shape = RoundedCornerShape(36.dp)
    val padHeight = LocalConfiguration.current.screenHeightDp.dp * 0.5f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(padHeight)
            .clip(shape)
            .background(if (listening) CecapiAccent else CecapiSurfaceElevated)
            .border(3.dp, CecapiAccent, shape)
            // Two raw taps in a row silences the assistant, for someone navigating by touch instead of
            // TalkBack (TalkBack's own double-tap is its activation gesture and keeps working through
            // the semantics onClick below, unchanged).
            .pointerInput(onDoubleTap) {
                detectTapGestures(onTap = { onClick() }, onDoubleTap = { onDoubleTap?.invoke() })
            }
            .semantics {
                contentDescription = "Micrófono. Toca dos veces para hablar con el asistente."
                onClick(label = "Hablar") { onClick(); true }
            }
            .voiceHint(
                "Este es el micrófono. Tócalo para hablarme y dime lo que necesitas, " +
                    "por ejemplo: módulos disponibles, o estado del teléfono.",
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                tint = if (listening) CecapiBackground else CecapiAccent,
                modifier = Modifier
                    .size(150.dp)
                    .graphicsLayer {
                        val scale = if (listening) pulse else 1f
                        scaleX = scale
                        scaleY = scale
                    },
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.titleMedium,
                color = if (listening) CecapiBackground else CecapiAccent,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
            )
        }
    }
}

/**
 * A large labeled icon button for a screen's header (sign in, sign out...). Pass a different [tint]
 * for actions that must stand out, like the red "Cerrar sesión".
 */
@Composable
fun TopAction(
    icon: ImageVector,
    label: String,
    help: String,
    onClick: () -> Unit,
    tint: Color = CecapiAccent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = help }
            .voiceHint(help)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f))
                .border(2.dp, tint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(34.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (tint == CecapiAccent) CecapiTextMuted else tint,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** A phrase the user can say, also tappable. Shown under "PUEDES DECIR". Tall enough to hit (48 dp). */
@Composable
fun SuggestionChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(CecapiSurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "\"$label\"", color = CecapiAccent, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Something that needs the user's attention and is not repeated by voice on screen. */
@Composable
fun NoticeBanner(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CecapiWarning.copy(alpha = 0.15f))
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(text = text, color = CecapiWarning, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Shown only while there is no internet: answers will be less precise. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    NoticeBanner("Sin conexión a internet. Mis respuestas pueden ser menos precisas.", modifier)
}
