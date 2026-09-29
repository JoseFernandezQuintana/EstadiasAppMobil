package com.cecapi.app.feature.modulo1_aplicacionprincipal

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.ModuleCarousel
import com.cecapi.app.core.ui.NoticeBanner
import com.cecapi.app.core.ui.OfflineBanner
import com.cecapi.app.core.ui.SuggestionChip
import com.cecapi.app.core.ui.TopAction
import com.cecapi.app.core.voice.VoiceMessages
import com.cecapi.app.core.voice.VoiceState

@Composable
fun HomeScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToModule: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val assistantName by viewModel.assistantName.collectAsState()
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicPermission = granted
        if (granted) viewModel.onMicTapped() else viewModel.onMicPermissionDenied()
    }

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is HomeNavEvent.GoToLogin -> onNavigateToLogin()
                is HomeNavEvent.GoToModule -> onNavigateToModule(event.route)
                HomeNavEvent.GoToSettings -> onNavigateToSettings()
                HomeNavEvent.ExitApp -> (context as? Activity)?.finishAffinity()
            }
        }
    }

    // Ask for the mic on entry so "hola" works from the first launch, without needing a tap.
    val entryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicPermission = granted
        if (!granted) viewModel.onMicPermissionDenied()
    }
    LaunchedEffect(Unit) {
        if (!hasMicPermission) entryPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Home stays alive under Login/Register; only react to speech while it is actually showing.
    DisposableEffect(Unit) {
        viewModel.setScreenActive(true)
        onDispose { viewModel.setScreenActive(false) }
    }

    // "Hola" (or the assistant's name) opens the mic, but only while this screen is showing.
    DisposableEffect(hasMicPermission) {
        viewModel.setWakeWordEnabled(hasMicPermission)
        onDispose { viewModel.setWakeWordEnabled(false) }
    }

    val listening = voiceState is VoiceState.Listening
    val menu = viewModel.menu

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp, top = 8.dp)) {
                Text(
                    text = "ASISTENTE PARA PERSONAS INVIDENTES",
                    style = CecapiEyebrowStyle,
                    color = CecapiTextMuted,
                )
                Text(
                    text = "Hola, estoy",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (listening) "escuchándote" else "esperándote",
                    style = MaterialTheme.typography.headlineLarge,
                    color = CecapiAccent,
                    fontWeight = FontWeight.Bold,
                )
            }
            TopAction(
                icon = Icons.Filled.Person,
                label = "Iniciar sesión",
                help = "Iniciar sesión. Abre la pantalla para entrar con tu usuario y contraseña. Toca dos veces para abrir.",
                onClick = onNavigateToLogin,
            )
        }

        // The microphone is the main control of the whole app, so it takes over most of the screen:
        // a huge target is easy to find by touch without seeing anything.
        MicPad(
            onDoubleTap = viewModel::onMicDoubleTap,
            listening = listening,
            hint = when {
                listening -> "Escuchando…"
                !hasMicPermission -> "Toca aquí para dar permiso"
                assistantName.isBlank() -> "Di \"hola\" o toca aquí"
                else -> "Di \"hola\" o \"$assistantName\", o toca aquí"
            },
            onClick = {
                if (hasMicPermission) {
                    viewModel.onMicTapped()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp),
        )

        // What the voice says is not repeated on screen. Only what needs attention stays visible.
        if (!hasMicPermission) {
            NoticeBanner(VoiceMessages.MIC_DENIED, modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp))
        } else if (voiceState is VoiceState.Error) {
            NoticeBanner(
                (voiceState as VoiceState.Error).message,
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp),
            )
        }
        if (!isOnline) {
            OfflineBanner(modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp))
        }

        Text(
            text = "MENÚ",
            style = CecapiEyebrowStyle,
            color = CecapiTextMuted,
            modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 12.dp),
        )
        ModuleCarousel(items = menu, onItemClick = viewModel::onMenuItemSelected)

        Text(
            text = "PUEDES DECIR",
            style = CecapiEyebrowStyle,
            color = CecapiTextMuted,
            modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 12.dp),
        )
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SuggestionChip("Ayuda") { viewModel.onSuggestionTapped("ayuda") }
            SuggestionChip("Menú") { viewModel.onSuggestionTapped("módulos disponibles") }
        }
        Row(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp)) {
            SuggestionChip("Estado del teléfono") { viewModel.onSuggestionTapped("estado del teléfono") }
        }
        // Far from the rest on purpose: closing the app by accident is annoying.
        Row(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp)) {
            SuggestionChip("Cerrar aplicación") { viewModel.onSuggestionTapped("cerrar la aplicación") }
        }
        Text(
            text = "Para ponerme nombre di: \"llámate Alice\"",
            style = MaterialTheme.typography.bodyMedium,
            color = CecapiTextMuted,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp),
        )
    }
}
