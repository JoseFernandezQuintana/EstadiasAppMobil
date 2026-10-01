package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RankingUiState(
    val individual: List<RankingFila> = emptyList(),
    val instituciones: List<RankingInstitucion> = emptyList(),
    val miId: Long? = null,
    val miApodo: String = "",
    val esperandoApodo: Boolean = false,
)

/**
 * Clasificación: la tabla individual (apodo, no el nombre real — hay menores) y la de instituciones
 * compitiendo entre ellas. Los puntos vienen de Actividades de sonidos; alguien sin puntos (vibración
 * todavía no da puntos, o nunca ha jugado) simplemente no aparece en ninguna de las dos listas.
 */
@HiltViewModel
class RankingViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val rankingDao: RankingDao,
    private val usuarioDao: UsuarioDao,
    private val cues: FeedbackCues,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RankingUiState())
    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    init {
        val usuario = sessionRepository.currentUser.value
        _uiState.value = _uiState.value.copy(miId = usuario?.id, miApodo = usuario?.apodo.orEmpty())

        viewModelScope.launch {
            rankingDao.observeIndividual().collect { lista -> _uiState.value = _uiState.value.copy(individual = lista) }
        }
        viewModelScope.launch {
            rankingDao.observeInstituciones().collect { lista -> _uiState.value = _uiState.value.copy(instituciones = lista) }
        }
        viewModelScope.launch {
            voiceEngine.recognizedSpeech.collect { speech -> onSpeech(speech.text) }
        }

        voiceEngine.speak(
            "Clasificación. Aquí ves tu lugar y el de tu institución. Di mi lugar, para saber en qué " +
                "posición vas, o mi apodo, para cambiar cómo te muestro aquí.",
            listenAfter = true,
        )
    }

    private fun onSpeech(spoken: String) {
        if (_uiState.value.esperandoApodo) {
            capturarApodo(spoken)
            return
        }
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        when {
            has("mi apodo", "cambiar apodo", "cambiar mi apodo", "poner apodo", "nuevo apodo") -> pedirApodo()
            has("mi lugar", "mi posicion", "mi puesto", "en que lugar voy", "como voy") -> speakMiLugar()
            has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio") -> _back.tryEmit(Unit)
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak("No entendí. Di mi lugar, mi apodo, o atrás.", listenAfter = true)
            }
        }
    }

    private fun speakMiLugar() {
        val id = _uiState.value.miId
        val lugar = _uiState.value.individual.indexOfFirst { it.usuarioId == id }
        if (lugar == -1) {
            voiceEngine.speak("Todavía no tienes puntos en Actividades. Practica con los sonidos para aparecer aquí.", listenAfter = true)
            return
        }
        val fila = _uiState.value.individual[lugar]
        voiceEngine.speak("Vas en el lugar ${lugar + 1}, con ${fila.puntosTotales} puntos.", listenAfter = true)
    }

    fun pedirApodo() {
        _uiState.value = _uiState.value.copy(esperandoApodo = true)
        voiceEngine.rawInput = true
        voiceEngine.speak("Dime el apodo que quieres usar.", listenAfter = true)
    }

    private fun capturarApodo(spoken: String) {
        voiceEngine.rawInput = false
        _uiState.value = _uiState.value.copy(esperandoApodo = false)
        onApodoElegido(spoken.trim())
    }

    /** Also called from the on-screen text field. */
    fun onApodoElegido(nuevoApodo: String) {
        val id = _uiState.value.miId ?: return
        val limpio = nuevoApodo.trim().take(24)
        if (limpio.isBlank()) {
            voiceEngine.speak("No escuché ningún apodo. Sigues apareciendo con tu nombre.", listenAfter = true)
            return
        }
        _uiState.value = _uiState.value.copy(miApodo = limpio)
        viewModelScope.launch {
            usuarioDao.actualizarApodo(id, limpio)
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Listo, ahora apareces como $limpio.", listenAfter = true)
        }
    }

    fun onCommandsRequested() {
        voiceEngine.speak(
            "Di mi lugar, para saber tu posición. Di mi apodo, para cambiar cómo te muestro. Atrás, para volver al menú.",
            listenAfter = true,
        )
    }

    override fun onCleared() {
        voiceEngine.rawInput = false
        super.onCleared()
    }
}
