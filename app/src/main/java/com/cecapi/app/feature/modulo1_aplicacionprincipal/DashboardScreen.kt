package com.cecapi.app.feature.modulo1_aplicacionprincipal

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.ModuleCarousel
import com.cecapi.app.core.ui.NoticeBanner
import com.cecapi.app.core.ui.OfflineBanner
import com.cecapi.app.core.ui.SuggestionChip
import com.cecapi.app.core.ui.TopAction
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.util.openTtsSettings
import com.cecapi.app.core.voice.VoiceMessages
import com.cecapi.app.core.voice.VoiceState

/** Signed-in home. Same layout and voice behavior as the public home, plus the user's own modules. */
@Composable
fun DashboardScreen(
    onModuleClick: (String) -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val enabledModules by viewModel.enabledModules.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val assistantName by viewModel.assistantName.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect(onModuleClick)
    }
    // Leaving the app also frees the phone: the screen stops being kept on and the mic is released.
    LaunchedEffect(Unit) {
        viewModel.exitEvents.collect { (context as? Activity)?.finishAffinity() }
    }
    LaunchedEffect(currentUser) {
        if (currentUser == null) onLoggedOut()
    }
    // Dashboard stays alive under the module screens; only react to speech while it is showing.
    DisposableEffect(Unit) {
        viewModel.setScreenActive(true)
        onDispose { viewModel.setScreenActive(false) }
    }

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
    // "Hola" (or the assistant's name) opens the mic while the dashboard is showing.
    DisposableEffect(hasMicPermission) {
        viewModel.setWakeWordEnabled(hasMicPermission)
        onDispose { viewModel.setWakeWordEnabled(false) }
    }

    val listening = voiceState is VoiceState.Listening
    val menu by viewModel.menu.collectAsState()

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
                Text("ASISTENTE PARA PERSONAS INVIDENTES", style = CecapiEyebrowStyle, color = CecapiTextMuted)
                Text(
                    text = "Hola,",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = currentUser?.nombreCompleto.orEmpty(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = CecapiAccent,
                    fontWeight = FontWeight.Bold,
                )
                currentUser?.let { user ->
                    Text(
                        text = buildString {
                            append(user.rol.replaceFirstChar { it.uppercase() })
                            if (user.origen.isNotBlank()) append(" · ${user.origen}")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = CecapiTextMuted,
                    )
                }
            }
            // Red on purpose: it is the one action here that ends the session, so it must look different.
            TopAction(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = "Cerrar sesión",
                help = "Cerrar sesión. Sale de tu cuenta y vuelve al inicio. Tus datos se conservan. Toca dos veces para cerrar.",
                onClick = viewModel::onLogout,
                tint = CecapiError,
            )
        }

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
                if (hasMicPermission) viewModel.onMicTapped() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp),
        )

        // What the voice says is not repeated on screen. Only what needs attention stays visible.
        if (!hasMicPermission) {
            NoticeBanner(VoiceMessages.MIC_DENIED, modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp))
        } else if (voiceState is VoiceState.Error) {
            val error = voiceState as VoiceState.Error
            NoticeBanner(
                error.message,
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp),
                actionLabel = if (error.openTtsSettings) "Elegir motor de voz" else null,
                onAction = if (error.openTtsSettings) {
                    { openTtsSettings(context) }
                } else null,
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

        // Only administrador, directivo and educador ever see this — never alumno or usuario.
        val rol = currentUser?.rol?.let(RolUsuario::fromCodigo)
        if (rol == RolUsuario.ADMINISTRADOR || rol == RolUsuario.DIRECTIVO || rol == RolUsuario.EDUCADOR) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CecapiSurfaceElevated)
                    .border(1.dp, CecapiAccent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .clickable(onClick = viewModel::onGestionSelected)
                    .semantics { contentDescription = "Gestión. Toca dos veces para administrar tu institución." }
                    .voiceHint(
                        when (rol) {
                            RolUsuario.EDUCADOR -> "Gestión. Ve la lista de tus alumnos."
                            else -> "Gestión. Administra las cuentas de tu institución."
                        },
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = CecapiAccent)
                Text(
                    "Gestión",
                    style = MaterialTheme.typography.titleMedium,
                    color = CecapiAccent,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        } else if (rol == RolUsuario.ALUMNO || rol == RolUsuario.USUARIO) {
            // El opuesto de Gestión: solo para alumno y usuario independiente.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CecapiSurfaceElevated)
                    .border(1.dp, CecapiAccent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .clickable(onClick = viewModel::onSoporteSelected)
                    .semantics { contentDescription = "Ayuda y soporte. Toca dos veces para abrir." }
                    .voiceHint(
                        if (rol == RolUsuario.ALUMNO) {
                            "Ayuda y soporte. Ve quién es tu educador, o reporta un problema."
                        } else {
                            "Ayuda y soporte. Reporta un problema con la aplicación."
                        },
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = CecapiAccent)
                Text(
                    "Ayuda y soporte",
                    style = MaterialTheme.typography.titleMedium,
                    color = CecapiAccent,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }

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
            SuggestionChip("Menú") { viewModel.onSuggestionTapped("módulos disponibles") }
            SuggestionChip("Repite") { viewModel.onSuggestionTapped("repite la solicitud anterior") }
        }
        Row(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp)) {
            SuggestionChip("Estado del teléfono") { viewModel.onSuggestionTapped("estado del teléfono") }
        }
        // Far from the rest on purpose: closing the app by accident is annoying.
        Row(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp)) {
            SuggestionChip("Cerrar aplicación") { viewModel.onSuggestionTapped("cerrar la aplicación") }
        }
    }
}
