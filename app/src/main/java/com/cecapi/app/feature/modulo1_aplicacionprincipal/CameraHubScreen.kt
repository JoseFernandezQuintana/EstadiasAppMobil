package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.DeviceSettings
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The "Cámara" card. Reading text and describing what is in front of the phone both use the camera,
 * so the person picks one here, by touch or by voice, instead of hunting for two separate modules.
 * The two modes themselves are still the text reader and the environment assistant.
 */
@HiltViewModel
class CameraHubViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val cues: FeedbackCues,
    deviceSettings: DeviceSettings,
) : ViewModel() {

    private val _routes = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val routes: SharedFlow<String> = _routes

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    // Stays alive under the text reader or the environment screen; ignore speech meant for them.
    private var screenActive = false

    init {
        viewModelScope.launch {
            val style = deviceSettings.addressStyle.first()
            voiceEngine.speak(
                style.pick(
                    "Cámara. ¿Quieres que lea un texto o que describa lo que hay enfrente?",
                    "Cámara. ¿Desea que lea un texto o que describa lo que hay enfrente?",
                ),
                listenAfter = true,
            )
        }
        viewModelScope.launch {
            voiceEngine.recognizedSpeech.collect { speech -> if (screenActive) onSpeech(speech.text) }
        }
    }

    fun setScreenActive(active: Boolean) {
        screenActive = active
    }

    fun openTextReader() = open(CecapiDestinations.DOCUMENT_READER, "Abriendo la cámara para leer texto.")

    fun openEnvironment() = open(CecapiDestinations.ENVIRONMENT, "Abriendo el asistente del entorno.")

    private fun open(route: String, speech: String) {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak(speech)
        _routes.tryEmit(route)
    }

    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        when {
            listOf("atras", "volver", "regresa", "menu", "inicio").any { it in text } -> _back.tryEmit(Unit)
            listOf("enfrente", "entorno", "descri", "alrededor", "que hay").any { it in text } -> openEnvironment()
            listOf("texto", "leer", "lee", "documento", "letra", "papel").any { it in text } -> openTextReader()
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak("No entendí. Di leer texto, o describir lo que hay enfrente.", listenAfter = true)
            }
        }
    }
}

@Composable
fun CameraHubScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: CameraHubViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.routes.collect(onOpen) }
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }
    DisposableEffect(Unit) {
        viewModel.setScreenActive(true)
        onDispose { viewModel.setScreenActive(false) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = CecapiTextMuted)
            }
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text("MENÚ", style = CecapiEyebrowStyle, color = CecapiTextMuted)
                Text("Cámara", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        Text(
            "¿Qué quieres hacer con la cámara?",
            style = MaterialTheme.typography.bodyLarge,
            color = CecapiTextMuted,
        )
        ModeCard(
            title = "Leer texto",
            subtitle = "Lee en voz alta documentos, carteles y etiquetas",
            accent = Color(0xFF4ADE80),
            help = "Leer texto. Apunta la cámara a un papel, un cartel o una etiqueta y te lo leo en voz alta. " +
                "Toca dos veces para abrir.",
            onClick = viewModel::openTextReader,
        )
        ModeCard(
            title = "Describir lo que hay enfrente",
            subtitle = "Te cuenta qué objetos y cosas ve la cámara",
            accent = Color(0xFFFB923C),
            help = "Describir lo que hay enfrente. Apunta la cámara y te digo qué objetos veo. Toca dos veces para abrir.",
            onClick = viewModel::openEnvironment,
        )
        Text(
            "También puedes decir: leer texto, o describir lo que hay enfrente.",
            style = MaterialTheme.typography.bodyMedium,
            color = CecapiTextMuted,
        )
    }
}

@Composable
private fun ModeCard(title: String, subtitle: String, accent: Color, help: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(CecapiSurface)
            .clickable(onClick = onClick)
            .semantics { contentDescription = help }
            .voiceHint(help)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(accent))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = CecapiTextMuted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
