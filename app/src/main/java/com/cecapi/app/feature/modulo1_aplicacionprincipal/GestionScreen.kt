package com.cecapi.app.feature.modulo1_aplicacionprincipal

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.voiceHint

@Composable
fun GestionScreen(
    onBack: () -> Unit,
    viewModel: GestionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    val esEducador = state.rolObservador == RolUsuario.EDUCADOR
    val puedeEditar = viewModel.puedeEditar()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTopBar(
            eyebrow = "GESTIÓN",
            title = if (esEducador) "Mis alumnos" else "Mi institución",
            onBack = onBack,
            onCommands = viewModel::onCommandsRequested,
        )

        if (state.personas.isEmpty()) {
            Text(
                if (esEducador) "Todavía no tienes alumnos asignados." else "No hay nadie que mostrar.",
                style = MaterialTheme.typography.bodyLarge,
                color = CecapiTextMuted,
                modifier = Modifier.padding(8.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.personas, key = { it.id }) { persona ->
                    PersonaCard(
                        persona = persona,
                        mostrarOrigen = state.rolObservador == RolUsuario.ADMINISTRADOR,
                        editable = puedeEditar,
                        expandido = state.expandidoId == persona.id,
                        rolesAsignables = viewModel.rolesAsignables(),
                        educadoresDisponibles = { viewModel.educadoresPara(persona) },
                        onClick = { viewModel.onPersonaTocada(persona) },
                        onRolElegido = { rol -> viewModel.onRolElegido(persona, rol) },
                        onEducadorElegido = { educador -> viewModel.onEducadorElegido(persona, educador) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonaCard(
    persona: UsuarioEntity,
    mostrarOrigen: Boolean,
    editable: Boolean,
    expandido: Boolean,
    rolesAsignables: List<RolUsuario>,
    educadoresDisponibles: () -> List<UsuarioEntity>,
    onClick: () -> Unit,
    onRolElegido: (RolUsuario) -> Unit,
    onEducadorElegido: (UsuarioEntity?) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val rolLegible = persona.rol.replaceFirstChar { it.uppercase() }
    val detalle = buildString {
        append(rolLegible)
        if (mostrarOrigen && persona.origen.isNotBlank()) append(" · ${persona.origen}")
    }
    val help = if (editable) {
        "$rolLegible ${persona.nombreCompleto}. Toca para cambiarle el rol."
    } else {
        "$rolLegible ${persona.nombreCompleto}."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CecapiSurface)
            .then(if (editable) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = help }
            .voiceHint(help)
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(persona.nombreCompleto, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(detalle, style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted)
            }
        }

        if (expandido && editable) {
            Text(
                "Cambiar rol",
                style = MaterialTheme.typography.labelLarge,
                color = CecapiTextMuted,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rolesAsignables.forEach { rol ->
                    RolChip(
                        label = rol.codigo.replaceFirstChar { it.uppercase() },
                        selected = persona.rol == rol.codigo,
                        onClick = { onRolElegido(rol) },
                    )
                }
            }

            if (persona.rol == RolUsuario.ALUMNO.codigo) {
                val educadores = educadoresDisponibles()
                Text(
                    "Asignar educador",
                    style = MaterialTheme.typography.labelLarge,
                    color = CecapiTextMuted,
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                )
                if (educadores.isEmpty()) {
                    Text(
                        "No hay educadores en esta institución todavía.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CecapiTextMuted,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        educadores.forEach { educador ->
                            RolChip(
                                label = educador.nombreCompleto,
                                selected = persona.educadorId == educador.id,
                                onClick = { onEducadorElegido(educador) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RolChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) CecapiAccent.copy(alpha = 0.2f) else CecapiSurfaceElevated)
            .border(if (selected) 2.dp else 1.dp, if (selected) CecapiAccent else CecapiTextMuted.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (selected) "$label  ✓" else label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) CecapiAccent else MaterialTheme.colorScheme.onBackground,
        )
    }
}
