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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.Normalizer

// =============================================================================
// AVISO PARA EL EQUIPO: este archivo cambió respecto a la primera versión.
// Ahora, además de hablar y escuchar, el ViewModel también manda a reproducir
// el sonido espacial de cada ejercicio (a través de AudioSpatialPlayer), y solo
// dice la instrucción en voz alta la PRIMERA vez que aparece un nivel nuevo,
// no en cada ejercicio (así lo pide la tarea: "instrucción al iniciar cada
// nivel", no en cada intento).
// =============================================================================

private fun normalizar(texto: String): String =
    Normalizer.normalize(texto.lowercase().trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}"), "")

data class LearningUiState(
    val ejercicios: List<EjercicioEntity> = emptyList(),
    val indiceActual: Int = 0,
    val feedback: String? = null,
    val nivel: NivelAprendizajeEntity = NivelAprendizajeEntity(usuarioId = 0),
    val reproduciendo: Boolean = false, // true mientras suena el audio del ejercicio
) {
    val ejercicioActual: EjercicioEntity? get() = ejercicios.getOrNull(indiceActual)
}

@HiltViewModel
class LearningViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val repository: LearningRepository,
    private val audioPlayer: AudioSpatialPlayer,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _uiState = MutableStateFlow(LearningUiState())
    val uiState: StateFlow<LearningUiState> = _uiState.asStateFlow()

    // Recuerda el último nivel cuya instrucción ya se dijo en voz alta, para no
    // repetirla en cada ejercicio, solo cuando el usuario entra a un nivel nuevo.
    private var ultimoNivelAnunciado = -1

    init {
        viewModelScope.launch {
            repository.seedEjerciciosSiVacio()
        }

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
        if (_uiState.value.reproduciendo) return // no escuchar mientras suena el audio
        voiceEngine.startListening()
    }

    /** Dice la instrucción SOLO si es un nivel nuevo, y siempre reproduce el sonido del ejercicio. */
    private fun anunciarEjercicioActual() {
        val ejercicio = _uiState.value.ejercicioActual ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(reproduciendo = true)

            val esNivelNuevo = ejercicio.nivel != ultimoNivelAnunciado
            if (esNivelNuevo) {
                voiceEngine.speak(ejercicio.instruccion)
                voiceState.first { it !is VoiceState.Speaking } // espera a que termine de hablar
                ultimoNivelAnunciado = ejercicio.nivel
            }

            audioPlayer.reproducir(ejercicio)
            _uiState.value = _uiState.value.copy(reproduciendo = false)
        }
    }

    /** Vuelve a reproducir el mismo sonido, sin repetir la instrucción hablada. */
    fun onRepetirSonido() {
        val ejercicio = _uiState.value.ejercicioActual ?: return
        if (_uiState.value.reproduciendo) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(reproduciendo = true)
            audioPlayer.reproducir(ejercicio)
            _uiState.value = _uiState.value.copy(reproduciendo = false)
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

    override fun onCleared() {
        super.onCleared()
        audioPlayer.detener() // por si sales de la pantalla mientras algo sonaba
    }
}
