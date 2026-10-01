package com.cecapi.app.feature.modulo6_aprendizaje

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiSuccess
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.TopAction
import com.cecapi.app.core.ui.VoiceCaptionBubble
import com.cecapi.app.core.ui.VoiceMicButton
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.VoiceState

/**
 * One full-screen card per activity type (sonidos, vibración), swiped between like a carousel. The dots
 * below the title are also tappable for someone who does not want to swipe, and the two work the same as
 * before by voice ("sonidos", "vibración") — swiping is one more way in, not the only way.
 */
@Composable
fun LearningScreen(
    onBack: () -> Unit,
    viewModel: LearningViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsState()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    // Leaving the app (home button, screen lock) must stop the sound and the vibration.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.onAppStopped() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.onMicTapped() }

    val modes = ActivityMode.entries
    val pagerState = rememberPagerState(initialPage = state.mode.ordinal) { modes.size }

    // A voice command ("vibración"...) changes state.mode: follow it with the pager, unless the person is
    // the one mid-swipe right now (otherwise a swipe in progress would get yanked back).
    LaunchedEffect(state.mode) {
        val target = state.mode.ordinal
        if (pagerState.currentPage != target && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(target)
        }
    }
    // A finished swipe (or a tap on a dot) changes the mode, the same as the old buttons did.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val swiped = modes[page]
            if (swiped != state.mode) viewModel.setMode(swiped)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTopBar(
            eyebrow = "MENÚ",
            title = "Actividades",
            onBack = onBack,
            onCommands = viewModel::onCommandsRequested,
        )

        PageDots(
            count = modes.size,
            current = pagerState.currentPage,
            labels = listOf("Sonidos", "Vibración"),
            onSelect = { page -> viewModel.setMode(modes[page]) },
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .semantics {
                    contentDescription = "Tarjetas de actividades. Desliza a la izquierda o a la derecha para " +
                        "cambiar entre sonidos y vibración."
                },
        ) { page ->
            ActivityPage(
                pageMode = modes[page],
                state = state,
                voiceState = voiceState,
                onMicTapped = {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) viewModel.onMicTapped() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onMicDoubleTap = viewModel::onMicDoubleTap,
                onRepeat = viewModel::repeat,
                onNext = viewModel::next,
                onSetLevel = viewModel::setLevel,
            )
        }
    }
}

/** The dots under the title: show which of the two cards is showing, and jump straight to one by touch. */
@Composable
private fun PageDots(count: Int, current: Int, labels: List<String>, onSelect: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        repeat(count) { i ->
            val selected = i == current
            val label = labels.getOrNull(i) ?: "Tarjeta ${i + 1}"
            Box(
                modifier = Modifier
                    .size(if (selected) 14.dp else 10.dp)
                    .clip(CircleShape)
                    .background(if (selected) CecapiAccent else CecapiTextMuted.copy(alpha = 0.4f))
                    .clickable { onSelect(i) }
                    .semantics { contentDescription = if (selected) "$label, mostrando" else label }
                    .voiceHint("$label. Toca para mostrar esta tarjeta."),
            )
        }
        Text(
            labels.getOrNull(current) ?: "",
            style = MaterialTheme.typography.labelLarge,
            color = CecapiTextMuted,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * One full card: the exercise (title, instructions, feedback) and its controls. [pageMode] is which card
 * this is; while it is not the active mode in [state] (mid-swipe, or state has not caught up yet) it shows
 * a light placeholder instead of the other card's stale exercise, since the ViewModel only ever loads one
 * mode's exercises at a time.
 */
@Composable
private fun ActivityPage(
    pageMode: ActivityMode,
    state: ActivitiesUiState,
    voiceState: VoiceState,
    onMicTapped: () -> Unit,
    onMicDoubleTap: () -> Unit,
    onRepeat: () -> Unit,
    onNext: () -> Unit,
    onSetLevel: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.mode != pageMode) {
            Text(
                "Preparando…",
                style = MaterialTheme.typography.bodyLarge,
                color = CecapiTextMuted,
                modifier = Modifier.padding(8.dp),
            )
            return@Column
        }

        if (pageMode == ActivityMode.AUDIO) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                (1..3).forEach { level ->
                    ChoiceButton(
                        label = "Nivel $level",
                        selected = state.level == level,
                        help = "Nivel $level. También puedes decir: nivel " + listOf("uno", "dos", "tres")[level - 1] + ".",
                        onClick = { onSetLevel(level) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        val item = state.current
        if (item == null) {
            Text(
                "Preparando los ejercicios…",
                style = MaterialTheme.typography.bodyLarge,
                color = CecapiTextMuted,
                modifier = Modifier.padding(8.dp),
            )
            return@Column
        }

        val shape = RoundedCornerShape(24.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(CecapiSurface)
                .border(2.dp, CecapiAccent.copy(alpha = 0.5f), shape)
                .padding(20.dp),
        ) {
            Text(
                "Ejercicio ${state.index + 1} de ${state.items.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = CecapiTextMuted,
            )
            Text(item.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        }

        VoiceCaptionBubble(
            text = when {
                state.busy -> "Escucha con atención…"
                state.feedback != null -> state.feedback ?: ""
                else -> (voiceState as? VoiceState.Speaking)?.text ?: item.instruction
            },
        )

        state.correct?.let { correct ->
            Text(
                if (correct) "Correcto" else "Incorrecto",
                style = MaterialTheme.typography.titleLarge,
                color = if (correct) CecapiSuccess else CecapiError,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopAction(
                icon = Icons.Filled.Replay,
                label = "Repetir",
                help = "Repetir. Vuelve a reproducir el sonido o la vibración. También puedes decir: repite.",
                onClick = onRepeat,
            )
            VoiceMicButton(
                isListening = voiceState is VoiceState.Listening,
                onDoubleTap = onMicDoubleTap,
                onClick = onMicTapped,
            )
            TopAction(
                icon = Icons.Filled.SkipNext,
                label = "Siguiente",
                help = "Siguiente. Pasa al siguiente ejercicio. También puedes decir: siguiente.",
                onClick = onNext,
            )
        }
    }
}

@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    help: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(CecapiSurfaceElevated)
            .border(
                if (selected) BorderStroke(3.dp, CecapiAccent) else BorderStroke(1.dp, CecapiTextMuted.copy(alpha = 0.4f)),
                shape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (selected) "$label, seleccionado. $help" else "$label. $help" }
            .voiceHint(help)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (selected) "$label  ✓" else label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) CecapiAccent else MaterialTheme.colorScheme.onBackground,
        )
    }
}
