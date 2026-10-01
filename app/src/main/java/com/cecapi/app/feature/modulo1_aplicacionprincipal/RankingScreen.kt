package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.voiceHint

@Composable
fun RankingScreen(
    onBack: () -> Unit,
    viewModel: RankingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }
    var apodoTexto by remember(state.miApodo) { mutableStateOf(state.miApodo) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTopBar(
            eyebrow = "ACTIVIDADES",
            title = "Clasificación",
            onBack = onBack,
            onCommands = viewModel::onCommandsRequested,
        )

        // Apodo: para no mostrar el nombre real en una lista pública, sobre todo habiendo menores.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CecapiSurface)
                .padding(16.dp),
        ) {
            Text("TU APODO EN LA LISTA", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = apodoTexto,
                    onValueChange = { apodoTexto = it },
                    modifier = Modifier.weight(1f).voiceHint("Tu apodo. También puedes decir: mi apodo."),
                    placeholder = { Text("Un apodo, no tu nombre real") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.onApodoElegido(apodoTexto) }),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
                )
                Button(
                    onClick = { viewModel.onApodoElegido(apodoTexto) },
                    colors = ButtonDefaults.buttonColors(containerColor = CecapiAccent),
                ) { Text("Guardar", color = MaterialTheme.colorScheme.onPrimary) }
            }
        }

        Text("TABLA INDIVIDUAL", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
        if (state.individual.isEmpty()) {
            Text(
                "Todavía nadie tiene puntos aquí. Practica en Actividades, en los sonidos, para aparecer.",
                style = MaterialTheme.typography.bodyMedium,
                color = CecapiTextMuted,
            )
        } else {
            state.individual.forEachIndexed { index, fila ->
                RankingRow(
                    posicion = index + 1,
                    nombre = fila.nombreMostrado,
                    detalle = "Nivel ${fila.nivelActual}",
                    puntos = fila.puntosTotales,
                    destacado = fila.usuarioId == state.miId,
                )
            }
        }

        Text(
            "INSTITUCIONES",
            style = MaterialTheme.typography.labelLarge,
            color = CecapiTextMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (state.instituciones.isEmpty()) {
            Text(
                "Todavía ninguna institución tiene puntos.",
                style = MaterialTheme.typography.bodyMedium,
                color = CecapiTextMuted,
            )
        } else {
            state.instituciones.forEachIndexed { index, institucion ->
                RankingRow(
                    posicion = index + 1,
                    nombre = institucion.origen,
                    detalle = "${institucion.miembros} " + if (institucion.miembros == 1) "persona" else "personas",
                    puntos = institucion.puntosTotales,
                    destacado = false,
                )
            }
        }
    }
}

@Composable
private fun RankingRow(posicion: Int, nombre: String, detalle: String, puntos: Int, destacado: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(CecapiSurfaceElevated)
            .let { if (destacado) it.border(2.dp, CecapiAccent, shape) else it }
            .voiceHint("Lugar $posicion. $nombre, $detalle, $puntos puntos.")
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(CecapiAccent.copy(alpha = 0.18f)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$posicion", style = MaterialTheme.typography.labelLarge, color = CecapiAccent)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(nombre, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(detalle, style = MaterialTheme.typography.bodySmall, color = CecapiTextMuted)
        }
        Text("$puntos pts", style = MaterialTheme.typography.titleMedium, color = CecapiAccent)
    }
}
