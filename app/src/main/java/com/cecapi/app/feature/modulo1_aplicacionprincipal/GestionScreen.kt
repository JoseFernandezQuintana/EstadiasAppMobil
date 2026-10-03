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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiSuccess
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.CecapiWarning
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.NoticeBanner
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.voiceHint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.cecapi.app.core.voice.VoiceState

@Composable
fun GestionScreen(
    onBack: () -> Unit,
    viewModel: GestionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val listening = voiceState is VoiceState.Listening
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    val esEducador = state.rolObservador == RolUsuario.EDUCADOR
    val puedeEditar = viewModel.puedeEditar()
    // La persona que se está editando tiene su propia pantalla, en vez de abrirse inline en la
    // tarjeta: validar, cambiar rol y asignar educador juntos podían llenar la tarjeta de chips chicos.
    val personaEnEdicion = state.personas.firstOrNull { it.id == state.expandidoId }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .padding(bottom = 230.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (personaEnEdicion != null) {
            PersonaEditScreen(
                persona = personaEnEdicion,
                puedeEditar = puedeEditar,
                puedeValidar = viewModel.puedeValidar(personaEnEdicion),
                rolesAsignables = viewModel.rolesAsignables(),
                educadoresDisponibles = { viewModel.educadoresPara(personaEnEdicion) },
                onBack = { viewModel.onPersonaTocada(personaEnEdicion) },
                onValidar = { viewModel.onValidar(personaEnEdicion) },
                onRolElegido = { rol -> viewModel.onRolElegido(personaEnEdicion, rol) },
                onEducadorElegido = { educador -> viewModel.onEducadorElegido(personaEnEdicion, educador) },
            )
        } else {
            ScreenTopBar(
                eyebrow = "GESTIÓN",
                title = if (esEducador) "Mis alumnos" else "Mi institución",
                onBack = onBack,
                onCommands = viewModel::onCommandsRequested,
            )

            if (state.incidencias.isNotEmpty()) {
                Text("REPORTES RECIBIDOS", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
                val formatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale("es", "MX")) }
                state.incidencias.forEach { incidencia ->
                    IncidenciaCard(incidencia = incidencia, fecha = formatter.format(Date(incidencia.fechaReporte)), onResolver = { viewModel.onResolverIncidencia(incidencia) })
                }
            }

            Text(
                if (esEducador) "MIS ALUMNOS" else "PERSONAS DE LA INSTITUCIÓN",
                style = MaterialTheme.typography.labelLarge,
                color = CecapiTextMuted,
            )
            if (state.personas.isEmpty()) {
                Text(
                    if (esEducador) "Todavía no tienes alumnos asignados." else "No hay nadie que mostrar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CecapiTextMuted,
                    modifier = Modifier.padding(8.dp),
                )
            } else {
                // Dos por fila para que la lista quepa sin tanto scroll.
                state.personas.chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        fila.forEach { persona ->
                            PersonaCard(
                                persona = persona,
                                mostrarOrigen = state.rolObservador == RolUsuario.ADMINISTRADOR,
                                editable = puedeEditar || viewModel.puedeValidar(persona),
                                onClick = { viewModel.onPersonaTocada(persona) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (fila.size == 1) Row(modifier = Modifier.weight(1f)) {}
                    }
                }
            }
        }
    }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Di cuántas personas, o toca aquí",
            onClick = viewModel::onMicTapped,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Pantalla propia para validar, cambiar rol y asignar educador. */
@Composable
private fun PersonaEditScreen(
    persona: UsuarioEntity,
    puedeEditar: Boolean,
    puedeValidar: Boolean,
    rolesAsignables: List<RolUsuario>,
    educadoresDisponibles: () -> List<UsuarioEntity>,
    onBack: () -> Unit,
    onValidar: () -> Unit,
    onRolElegido: (RolUsuario) -> Unit,
    onEducadorElegido: (UsuarioEntity?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTopBar(
            eyebrow = "GESTIÓN",
            title = persona.nombreCompleto,
            onBack = onBack,
            onCommands = {},
        )

        if (!persona.validado) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, CecapiWarning, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "PENDIENTE DE VALIDACIÓN",
                    style = MaterialTheme.typography.labelMedium,
                    color = CecapiWarning,
                )
                if (puedeValidar) {
                    BigBtn(icon = Icons.Filled.Check, label = "Validar persona", variant = BigBtnVariant.Success, onClick = onValidar)
                }
            }
        }

        if (puedeEditar) {
            Text("CAMBIAR ROL", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted)
            rolesAsignables.chunked(2).forEach { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    fila.forEach { rol ->
                        val label = rol.codigo.replaceFirstChar { it.uppercase() }
                        BigBtn(
                            icon = Icons.Filled.Check,
                            label = label,
                            variant = if (persona.rol == rol.codigo) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                            half = true,
                            onClick = { onRolElegido(rol) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (fila.size == 1) Row(modifier = Modifier.weight(1f)) {}
                }
            }
        }

        if (puedeEditar && persona.rol == RolUsuario.ALUMNO.codigo) {
            val educadores = educadoresDisponibles()
            Text("ASIGNAR EDUCADOR", style = MaterialTheme.typography.labelLarge, color = CecapiTextMuted, modifier = Modifier.padding(top = 8.dp))
            if (educadores.isEmpty()) {
                Text(
                    "No hay educadores en esta institución todavía.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CecapiTextMuted,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    educadores.forEach { educador ->
                        BigBtn(
                            icon = Icons.Filled.Person,
                            label = educador.nombreCompleto,
                            variant = if (persona.educadorId == educador.id) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                            onClick = { onEducadorElegido(educador) },
                        )
                    }
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
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
        modifier = modifier
            .clip(shape)
            .background(CecapiSurface)
            .then(if (editable) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = help }
            .voiceHint(help)
            .padding(14.dp),
    ) {
        if (!persona.validado) {
            Text(
                "PENDIENTE",
                style = MaterialTheme.typography.labelSmall,
                color = CecapiWarning,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CecapiWarning.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Text(
            persona.nombreCompleto,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = if (!persona.validado) 6.dp else 0.dp),
        )
        Text(detalle, style = MaterialTheme.typography.bodySmall, color = CecapiTextMuted)
    }
}

/** Un reporte de Ayuda y soporte, visible solo para quien debe atenderlo (administrador o directivo). */
@Composable
private fun IncidenciaCard(incidencia: IncidenciaEntity, fecha: String, onResolver: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CecapiSurface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(incidencia.mensaje, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(fecha, style = MaterialTheme.typography.labelSmall, color = CecapiTextMuted)
        if (incidencia.resuelta) {
            Text("RESUELTO", style = MaterialTheme.typography.labelMedium, color = CecapiSuccess)
        } else {
            BigBtn(icon = Icons.Filled.Check, label = "Marcar resuelto", variant = BigBtnVariant.Success, half = true, onClick = onResolver)
        }
    }
}
