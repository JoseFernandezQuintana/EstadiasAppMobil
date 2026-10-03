package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.VoiceState

/** La pantalla simple de alumno/usuario: ver a su educador (solo alumno) y reportar un problema. */
@Composable
fun SoporteScreen(
    onBack: () -> Unit,
    viewModel: SoporteViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val listening = voiceState is VoiceState.Listening
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    val esAlumno = state.rol == RolUsuario.ALUMNO

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .padding(bottom = 230.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTopBar(
            eyebrow = "AYUDA",
            title = if (esAlumno) "Mi educador" else "Reportar un problema",
            onBack = onBack,
            onCommands = viewModel::onCommandsRequested,
        )

        if (esAlumno) {
            val shape = RoundedCornerShape(20.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(CecapiSurface)
                    .voiceHint("Tu educador. También puedes decir: mi educador.")
                    .padding(20.dp),
            ) {
                Text("TU EDUCADOR", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                Text(
                    text = when {
                        state.cargandoEducador -> "Buscando…"
                        state.nombreEducador != null -> state.nombreEducador!!
                        else -> "Todavía no tienes un educador asignado."
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        BigBtn(
            icon = Icons.Filled.ReportProblem,
            label = if (state.esperandoMensaje) "Dime qué pasó…" else "Reportar un problema",
            variant = BigBtnVariant.Accent,
            enabled = !state.esperandoMensaje,
            onClick = viewModel::pedirMensaje,
        )

        if (state.misIncidencias.isNotEmpty()) {
            Text(
                "LO QUE HAS REPORTADO",
                style = MaterialTheme.typography.labelLarge,
                color = CecapiTextMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
            state.misIncidencias.take(5).forEach { incidencia ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CecapiSurface)
                        .padding(14.dp),
                ) {
                    Text(incidencia.mensaje, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        if (incidencia.resuelta) "Resuelto" else "En espera",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (incidencia.resuelta) CecapiAccent else CecapiTextMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Di reportar un problema, o toca aquí",
            onClick = viewModel::onMicTapped,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
