package com.cecapi.app.feature.modulo5_solicitudes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.feature.modulo1_aplicacionprincipal.CommandCatalog
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private fun etiquetaAmigable(clave: String): String = clave.replace("_", " ")

data class RequestsUiState(
    val plantillaSeleccionada: PlantillaSolicitudEntity? = null,
    val placeholders: List<String> = emptyList(),
    val indiceActual: Int = 0,
    val respuestas: Map<String, String> = emptyMap(),
    val textoGenerado: String? = null,
) {
    val preguntaActual: String?
        get() = placeholders.getOrNull(indiceActual)?.let { "¿Cuál es tu ${etiquetaAmigable(it)}?" }
}

@HiltViewModel
class RequestsViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val repository: RequestsRepository,
    private val cues: FeedbackCues,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    val plantillas: StateFlow<List<PlantillaSolicitudEntity>> = repository.observePlantillas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val solicitudesGeneradas: StateFlow<List<SolicitudGeneradaEntity>> = sessionRepository.currentUser
        .filterNotNull()
        .flatMapLatest { usuario -> repository.observeSolicitudes(usuario.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(RequestsUiState())
    val uiState: StateFlow<RequestsUiState> = _uiState.asStateFlow()

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    init {
        voiceEngine.speak(
            "Documentos. Elige una plantilla para comenzar. " + CommandCatalog.hint("documentos"),
            listenAfter = true,
        )
        // What is said while a template is being filled in is the answer, so the global commands (time, internet,
        // volume...) must not take it: "la fecha de hoy" is a date to write down, not a question about the phone.
        viewModelScope.launch {
            _uiState.collect { s -> voiceEngine.rawInput = s.plantillaSeleccionada != null && s.textoGenerado == null }
        }
        viewModelScope.launch {
            voiceEngine.recognizedSpeech.collect { speech -> onSpeech(speech.text) }
        }
    }

    /**
     * While a template is being filled in, whatever is said is the answer to the current question, except a few
     * control phrases that count only when they are the whole phrase (an answer may contain "cancelar" or
     * "otra vez"). Otherwise the person is choosing a template.
     */
    private fun onSpeech(spoken: String) {
        val state = _uiState.value
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        val answering = state.plantillaSeleccionada != null && state.textoGenerado == null
        when {
            CommandCatalog.isRequest(spoken) && (!answering || text.split(" ").size <= 4) ->
                voiceEngine.speak(CommandCatalog.DOCUMENTS, listenAfter = true)
            answering && text in CANCEL_PHRASES -> onReiniciar()
            answering && text in REPEAT_PHRASES -> voiceEngine.speak(state.preguntaActual ?: "", listenAfter = true)
            answering -> onAnswerProvided(spoken)
            state.plantillaSeleccionada != null && has(*CANCEL_PHRASES.toTypedArray()) -> onReiniciar()
            state.textoGenerado != null && has("repite", "otra vez", "lee", "leer") ->
                voiceEngine.speak(state.textoGenerado)
            has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio", "pantalla anterior") -> _back.tryEmit(Unit)
            else -> {
                val elegida = plantillas.value.firstOrNull { plantilla ->
                    VoiceText.normalize(plantilla.titulo).split(" ").filter { it.length > 3 }.let { phrases -> VoiceText.hasAny(text, phrases) }
                }
                if (elegida != null) {
                    onPlantillaSelected(elegida)
                } else {
                    cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                    voiceEngine.speak("No entendí. " + CommandCatalog.hint("documentos"), listenAfter = true)
                }
            }
        }
    }

    /** Call from a button: reads out this screen's commands. */
    fun onCommandsRequested() {
        voiceEngine.speak(CommandCatalog.DOCUMENTS, listenAfter = true)
    }

    fun onMicTapped() {
        voiceEngine.startListening()
    }

    fun onPlantillaSelected(plantilla: PlantillaSolicitudEntity) {
        val placeholders = extractPlaceholders(plantilla.cuerpoPlantilla)
        _uiState.value = RequestsUiState(plantillaSeleccionada = plantilla, placeholders = placeholders)
        if (placeholders.isEmpty()) {
            finalizarSolicitud(plantilla, emptyMap())
        } else {
            voiceEngine.speak("${plantilla.titulo}. ${_uiState.value.preguntaActual}", listenAfter = true)
        }
    }

    fun onAnswerProvided(respuesta: String) {
        val state = _uiState.value
        val plantilla = state.plantillaSeleccionada ?: return
        val clave = state.placeholders.getOrNull(state.indiceActual) ?: return

        val nuevasRespuestas = state.respuestas + (clave to respuesta.trim())
        val siguienteIndice = state.indiceActual + 1

        if (siguienteIndice >= state.placeholders.size) {
            finalizarSolicitud(plantilla, nuevasRespuestas)
        } else {
            _uiState.value = state.copy(respuestas = nuevasRespuestas, indiceActual = siguienteIndice)
            voiceEngine.speak(_uiState.value.preguntaActual ?: "", listenAfter = true)
        }
    }

    private fun finalizarSolicitud(plantilla: PlantillaSolicitudEntity, respuestas: Map<String, String>) {
        val usuario = sessionRepository.currentUser.value ?: run {
            // Nobody signed in: say so instead of ignoring the user in silence.
            voiceEngine.speak(com.cecapi.app.core.voice.VoiceMessages.NEEDS_LOGIN)
            return
        }
        viewModelScope.launch {
            val solicitud = repository.generarSolicitud(usuario.id, plantilla, respuestas)
            _uiState.value = _uiState.value.copy(textoGenerado = solicitud.textoFinal)
            voiceEngine.speak("Tu solicitud está lista. ${solicitud.textoFinal}")
        }
    }

    fun onReiniciar() {
        _uiState.value = RequestsUiState()
        voiceEngine.speak("Elige otra plantilla para comenzar.", listenAfter = true)
    }

    override fun onCleared() {
        voiceEngine.rawInput = false
        super.onCleared()
    }

    private companion object {
        val CANCEL_PHRASES = setOf("cancelar", "cancela", "reiniciar", "otra plantilla", "nueva plantilla")
        val REPEAT_PHRASES = setOf("repite", "repetir", "repite la pregunta", "otra vez", "de nuevo")
    }
}
