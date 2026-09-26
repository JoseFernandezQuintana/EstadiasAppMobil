package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.AddressStyle
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceOption
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Personalización: the assistant's voice, how it talks to the person, and how the avisos feel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalizationScreen(
    onBack: () -> Unit,
    viewModel: PersonalizationViewModel = hiltViewModel(),
) {
    val savedRate by viewModel.speechRate.collectAsState()
    val savedPitch by viewModel.pitch.collectAsState()
    val voiceName by viewModel.voiceName.collectAsState()
    val style by viewModel.addressStyle.collectAsState()
    val savedPreferred by viewModel.preferredName.collectAsState()
    val savedAssistant by viewModel.assistantName.collectAsState()
    val soundCues by viewModel.soundCues.collectAsState()
    val vibrationCues by viewModel.vibrationCues.collectAsState()
    val savedLevel by viewModel.vibrationLevel.collectAsState()

    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    var rate by remember(savedRate) { mutableFloatStateOf(savedRate) }
    var pitch by remember(savedPitch) { mutableFloatStateOf(savedPitch) }
    var level by remember(savedLevel) { mutableFloatStateOf(savedLevel.toFloat()) }
    var preferred by remember(savedPreferred) { mutableStateOf(savedPreferred) }
    var assistant by remember(savedAssistant) { mutableStateOf(savedAssistant) }

    // The speech engine may still be starting when this screen opens: ask again for a few seconds.
    var voices by remember { mutableStateOf(emptyList<VoiceOption>()) }
    LaunchedEffect(Unit) {
        repeat(20) {
            voices = viewModel.voices()
            if (voices.isNotEmpty()) return@LaunchedEffect
            delay(300)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTopBar(
            eyebrow = "MENÚ",
            title = "Personalización",
            onBack = onBack,
            onCommands = viewModel::onCommandsRequested,
        )

        Section("MI VOZ", "Cómo suena el asistente") {
            ChoiceRow(
                label = "Automática",
                detail = "La que el teléfono trae por defecto",
                selected = voiceName.isEmpty(),
                help = "Voz automática. Uso la voz que trae tu teléfono. Toca dos veces para elegirla.",
                onClick = { viewModel.onVoiceChosen("") },
            )
            if (voices.isEmpty()) {
                Text(
                    "Buscando las voces en español de tu teléfono…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CecapiTextMuted,
                )
            }
            voices.forEach { option ->
                ChoiceRow(
                    label = option.label,
                    detail = if (option.needsInternet) "Necesita internet" else "Funciona sin internet",
                    selected = voiceName == option.name,
                    help = "${option.label}. ${if (option.needsInternet) "Necesita internet." else "Funciona sin internet."} " +
                        "Toca dos veces para elegirla y escucharla.",
                    onClick = { viewModel.onVoiceChosen(option.name) },
                )
            }
            SliderRow(
                label = "Velocidad",
                valueText = "${(rate * 4).roundToInt() / 4f}x",
                value = rate,
                range = 0.5f..2.0f,
                steps = 5,
                onChange = { rate = it },
                onFinished = { viewModel.onSpeechRateChanged(rate) },
                help = "Velocidad. Desliza a la izquierda para que hable más lento y a la derecha para que hable más rápido.",
            )
            SliderRow(
                label = "Tono",
                valueText = when {
                    pitch < 0.9f -> "Grave"
                    pitch > 1.1f -> "Agudo"
                    else -> "Normal"
                },
                value = pitch,
                range = 0.5f..2.0f,
                steps = 5,
                onChange = { pitch = it },
                onFinished = { viewModel.onPitchChanged(pitch) },
                help = "Tono. Desliza a la izquierda para una voz más grave y a la derecha para una más aguda.",
            )
            ActionButton("Probar voz", "Probar voz. Me escuchas hablar con esta voz, velocidad y tono.") { viewModel.onTestVoice() }
        }

        Section("CÓMO TE HABLO", "Trato, y el nombre con el que te saludo") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceRow(
                    label = "Tú",
                    detail = "Trato cercano",
                    selected = style == AddressStyle.TU,
                    help = "Tú. Te hablo de tú, con un trato cercano. Toca dos veces para elegirlo.",
                    onClick = { viewModel.onAddressStyleChosen(AddressStyle.TU) },
                    modifier = Modifier.weight(1f),
                )
                ChoiceRow(
                    label = "Usted",
                    detail = "Trato formal",
                    selected = style == AddressStyle.USTED,
                    help = "Usted. Le hablo de usted, con un trato formal. Toca dos veces para elegirlo.",
                    onClick = { viewModel.onAddressStyleChosen(AddressStyle.USTED) },
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = preferred,
                onValueChange = { preferred = it.take(30) },
                label = { Text("Cómo quieres que te llame") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .voiceHint(
                        help = "Cómo quieres que te llame. Escribe tu nombre o apodo y con ese te saludo al entrar. " +
                            "Si lo dejas vacío uso el de tu cuenta.",
                        focusLabel = "Campo del nombre con el que te saludo. Escribe cómo quieres que te llame.",
                    ),
            )
            ActionButton("Guardar cómo te llamo", "Guardar cómo te llamo. Guarda el nombre que escribiste.") {
                viewModel.onPreferredNameSaved(preferred)
            }
        }

        Section("CÓMO ME LLAMAS", "El nombre con el que puedes hablarme") {
            OutlinedTextField(
                value = assistant,
                onValueChange = { assistant = it.take(30) },
                label = { Text("Nombre del asistente (opcional)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .voiceHint(
                        help = "Nombre del asistente. Escribe el nombre con el que quieres llamarme. " +
                            "Siempre puedes decir hola, y además responderé a este nombre.",
                        focusLabel = "Campo del nombre del asistente. Escribe cómo quieres llamarme.",
                    ),
            )
            ActionButton("Guardar mi nombre", "Guardar mi nombre. Guarda el nombre que escribiste.") {
                viewModel.onAssistantNameSaved(assistant)
            }
        }

        Section("AVISOS", "Sonidos y vibraciones para saber qué pasó sin mirar la pantalla") {
            SwitchRow(
                label = "Sonidos",
                description = "Pitidos al escuchar, al acertar y al fallar",
                checked = soundCues,
                onChange = viewModel::onSoundCuesChanged,
                help = "Sonidos. Activa o desactiva los pitidos que te avisan cuando empiezo a escucharte, " +
                    "cuando algo sale bien y cuando algo falla.",
            )
            SwitchRow(
                label = "Vibración",
                description = "Vibra al escuchar, al acertar y al fallar",
                checked = vibrationCues,
                onChange = viewModel::onVibrationCuesChanged,
                help = "Vibración. Activa o desactiva las vibraciones de la aplicación. No cambia la vibración de las llamadas.",
            )
            SliderRow(
                label = "Fuerza de la vibración",
                valueText = when (level.roundToInt()) {
                    1 -> "Suave"
                    3 -> "Fuerte"
                    else -> "Normal"
                },
                value = level,
                range = 1f..3f,
                steps = 1,
                onChange = { level = it },
                onFinished = { viewModel.onVibrationLevelChanged(level.roundToInt()) },
                help = "Fuerza de la vibración. Elige suave, normal o fuerte. En algunos teléfonos no se nota la diferencia.",
            )
            Text("Probar avisos", style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("Te escucho", "Aviso de que empiezo a escucharte. Un pitido corto y una vibración.") {
                    viewModel.onTestCue(FeedbackCues.Cue.LISTENING, "Te escucho.")
                }
                SmallButton("Éxito", "Aviso de que algo salió bien. Dos pulsos, el segundo más largo.") {
                    viewModel.onTestCue(FeedbackCues.Cue.SUCCESS, "Éxito.")
                }
                SmallButton("Error", "Aviso de que algo falló. Tres vibraciones seguidas.") {
                    viewModel.onTestCue(FeedbackCues.Cue.ERROR, "Error.")
                }
                SmallButton("Atención", "Aviso de algo que requiere tu atención, como no tener internet. Dos vibraciones largas.") {
                    viewModel.onTestCue(FeedbackCues.Cue.WARNING, "Atención.")
                }
                SmallButton("Notificación", "Aviso de que llegó una notificación. Tres toques rápidos.") {
                    viewModel.onTestCue(FeedbackCues.Cue.INCOMING, "Llegó una notificación.")
                }
            }
        }
    }
}

/** A selectable option: highlighted with a thick accent border when chosen, and it explains itself when held. */
@Composable
private fun ChoiceRow(
    label: String,
    detail: String,
    selected: Boolean,
    help: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CecapiSurfaceElevated)
            .border(if (selected) BorderStroke(3.dp, CecapiAccent) else BorderStroke(1.dp, CecapiTextMuted.copy(alpha = 0.4f)), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (selected) "$label, seleccionada. $detail" else "$label. $detail" }
            .voiceHint(help)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (selected) "$label  ✓" else label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) CecapiAccent else MaterialTheme.colorScheme.onBackground,
        )
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted)
    }
}
