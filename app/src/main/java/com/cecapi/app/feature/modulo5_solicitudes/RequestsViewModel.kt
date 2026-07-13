package com.cecapi.app.feature.modulo5_solicitudes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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

    init {
        voiceEngine.speak("Centro de solicitudes. Elige una plantilla para comenzar.")
        viewModelScope.launch {
            voiceEngine.recognizedSpeech.collect { speech ->
                if (_uiState.value.plantillaSeleccionada != null && _uiState.value.textoGenerado == null) {
                    onAnswerProvided(speech.text)
                }
            }
        }
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
            voiceEngine.speak("${plantilla.titulo}. ${_uiState.value.preguntaActual}")
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
            voiceEngine.speak(_uiState.value.preguntaActual ?: "")
        }
    }

    private fun finalizarSolicitud(plantilla: PlantillaSolicitudEntity, respuestas: Map<String, String>) {
        val usuario = sessionRepository.currentUser.value ?: return
        viewModelScope.launch {
            val solicitud = repository.generarSolicitud(usuario.id, plantilla, respuestas)
            _uiState.value = _uiState.value.copy(textoGenerado = solicitud.textoFinal)
            voiceEngine.speak("Tu solicitud está lista. ${solicitud.textoFinal}")
        }
    }

    fun onReiniciar() {
        _uiState.value = RequestsUiState()
        voiceEngine.speak("Elige otra plantilla para comenzar.")
    }
}
