package com.cecapi.app.feature.modulo6_aprendizaje

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
import java.text.Normalizer

private fun normalizar(texto: String): String =
    Normalizer.normalize(texto.lowercase().trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}"), "")

data class LearningUiState(
    val ejercicios: List<EjercicioEntity> = emptyList(),
    val indiceActual: Int = 0,
    val feedback: String? = null,
    val nivel: NivelAprendizajeEntity = NivelAprendizajeEntity(usuarioId = 0),
) {
    val ejercicioActual: EjercicioEntity? get() = ejercicios.getOrNull(indiceActual)
}

@HiltViewModel
class LearningViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val repository: LearningRepository,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _uiState = MutableStateFlow(LearningUiState())
    val uiState: StateFlow<LearningUiState> = _uiState.asStateFlow()

    init {
        voiceEngine.speak("Centro de aprendizaje. Toca el micrófono y responde cada ejercicio en voz alta.")
        viewModelScope.launch {
            sessionRepository.currentUser.filterNotNull().flatMapLatest { usuario ->
                repository.observeEjerciciosDelNivel(usuario.id)
            }.collect { ejercicios ->
                _uiState.value = _uiState.value.copy(ejercicios = ejercicios, indiceActual = 0, feedback = null)
                anunciarEjercicioActual()
            }
        }
        viewModelScope.launch {
            sessionRepository.currentUser.filterNotNull().flatMapLatest { usuario ->
                repository.observeNivel(usuario.id)
            }.collect { nivel -> _uiState.value = _uiState.value.copy(nivel = nivel) }
        }
        viewModelScope.launch {
            voiceEngine.recognizedSpeech.collect { speech -> onRespuestaDada(speech.text) }
        }
    }

    fun onMicTapped() {
        voiceEngine.startListening()
    }

    private fun anunciarEjercicioActual() {
        _uiState.value.ejercicioActual?.let { ejercicio ->
            voiceEngine.speak(ejercicio.instruccion)
        }
    }

    fun onRespuestaDada(respuesta: String) {
        val usuario = sessionRepository.currentUser.value ?: return
        val ejercicio = _uiState.value.ejercicioActual ?: return

        val esCorrecto = normalizar(respuesta).contains(normalizar(ejercicio.respuestaCorrecta))
        viewModelScope.launch { repository.registrarResultado(usuario.id, ejercicio.id, esCorrecto) }

        val mensaje = if (esCorrecto) "¡Correcto! Muy bien." else "No es correcto. La respuesta era: ${ejercicio.respuestaCorrecta}."
        voiceEngine.speak(mensaje)
        _uiState.value = _uiState.value.copy(feedback = mensaje)
    }

    fun onSiguienteEjercicio() {
        val state = _uiState.value
        val siguiente = (state.indiceActual + 1) % state.ejercicios.size.coerceAtLeast(1)
        _uiState.value = state.copy(indiceActual = siguiente, feedback = null)
        anunciarEjercicioActual()
    }
}
