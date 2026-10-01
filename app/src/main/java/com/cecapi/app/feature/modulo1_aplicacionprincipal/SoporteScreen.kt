package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.voiceHint

/** La pantalla simple de alumno/usuario: ver a su educador (solo alumno) y reportar un problema. */
@Composable
fun SoporteScreen(
    onBack: () -> Unit,
    viewModel: SoporteViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    val esAlumno = state.rol == RolUsuario.ALUMNO

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp),
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

        Text(
            "Si algo no funciona en la aplicación, o tienes un problema con alguien de tu institución, " +
                "puedes reportarlo aquí. Di reportar un problema, o toca el botón.",
            style = MaterialTheme.typography.bodyMedium,
            color = CecapiTextMuted,
        )

        Button(
            onClick = viewModel::pedirMensaje,
            enabled = !state.esperandoMensaje,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .voiceHint("Reportar un problema. Te voy a pedir que digas qué pasó."),
            colors = ButtonDefaults.buttonColors(containerColor = CecapiAccent),
        ) {
            Text(
                if (state.esperandoMensaje) "Dime qué pasó…" else "Reportar un problema",
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }

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
}
