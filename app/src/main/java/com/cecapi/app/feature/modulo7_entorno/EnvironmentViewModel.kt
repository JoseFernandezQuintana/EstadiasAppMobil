package com.cecapi.app.feature.modulo7_entorno

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceMessages
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EnvironmentUiState(
    val isProcessing: Boolean = false,
    val descripcion: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class EnvironmentViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val repository: EnvironmentRepository,
    private val cues: FeedbackCues,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _uiState = MutableStateFlow(EnvironmentUiState())
    val uiState: StateFlow<EnvironmentUiState> = _uiState.asStateFlow()

    // The screen owns the camera, so voice commands reach it through these.
    private val _captureRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val captureRequests: SharedFlow<Unit> = _captureRequests

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    private val _routes = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val routes: SharedFlow<String> = _routes

    init {
        viewModelScope.launch {
            voiceEngine.recognizedSpeech.collect { speech -> onSpeech(speech.text) }
        }
        voiceEngine.speak(
            "Descripción del entorno. Apunta la cámara al frente y di qué hay enfrente. " +
                CommandCatalog.hint("cámara"),
            listenAfter = true,
        )
    }

    /** The commands this screen answers, most specific first. */
    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        when {
            CommandCatalog.isRequest(spoken) -> voiceEngine.speak(CommandCatalog.ENVIRONMENT, listenAfter = true)
            has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio", "pantalla anterior") -> _back.tryEmit(Unit)
            has("leer texto", "lee", "leer", "texto", "documento") -> {
                cues.play(FeedbackCues.Cue.NAVIGATE)
                voiceEngine.speak("Cambiando al lector de texto.")
                _routes.tryEmit(CecapiDestinations.DOCUMENT_READER)
            }
            has("otra vez", "repite", "repetir", "de nuevo", "dilo") -> repetir()
            has("enfrente", "describe", "descripcion", "foto", "fotografia", "captura", "toma", "analiza",
                "que hay", "que ves", "entorno") -> pedirCaptura()
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak(
                    "No entendí. Di qué hay enfrente, repite, o atrás. " + CommandCatalog.hint("cámara"),
                    listenAfter = true,
                )
            }
        }
    }

    /** Descriptions are saved to the person's history, so a signed-in user is needed. */
    private fun sesionIniciada(): Boolean {
        if (sessionRepository.currentUser.value != null) return true
        voiceEngine.speak(VoiceMessages.NEEDS_LOGIN)
        return false
    }

    /** Also what the on-screen button and the "qué hay enfrente" chip call. */
    fun pedirCaptura() {
        if (_uiState.value.isProcessing || !sesionIniciada()) return
        _captureRequests.tryEmit(Unit)
    }

    fun repetir() {
        val descripcion = _uiState.value.descripcion
        if (descripcion == null) {
            voiceEngine.speak("Todavía no he descrito nada. Di qué hay enfrente.", listenAfter = true)
        } else {
            voiceEngine.speak(descripcion)
        }
    }

    fun onCommandsRequested() {
        voiceEngine.speak(CommandCatalog.ENVIRONMENT, listenAfter = true)
    }

    fun onCameraPermissionDenied() {
        voiceEngine.speak("Sin el permiso de la cámara no puedo describir lo que hay enfrente. Actívalo en los ajustes de la aplicación.")
    }

    fun onCaptureFailed() {
        val message = "No pude tomar la foto. Revisa que la cámara esté libre e intenta de nuevo."
        _uiState.value = EnvironmentUiState(errorMessage = message)
        cues.play(FeedbackCues.Cue.ERROR)
        voiceEngine.speak(message, listenAfter = true)
    }

    fun onPhotoCaptured(imageUri: Uri, rutaImagen: String) {
        val usuario = sessionRepository.currentUser.value ?: run {
            // Nobody signed in: say so instead of ignoring the user in silence.
            voiceEngine.speak(VoiceMessages.NEEDS_LOGIN)
            return
        }
        _uiState.value = EnvironmentUiState(isProcessing = true)
        voiceEngine.speak("Analizando el entorno.")
        viewModelScope.launch {
            repository.processCapturedPhoto(usuario.id, imageUri, rutaImagen)
                .onSuccess { result ->
                    _uiState.value = EnvironmentUiState(descripcion = result.descripcion)
                    voiceEngine.speak(result.descripcion)
                }
                .onFailure {
                    val message = "No pude analizar la imagen. Intenta de nuevo."
                    _uiState.value = EnvironmentUiState(errorMessage = message)
                    cues.play(FeedbackCues.Cue.ERROR)
                    voiceEngine.speak(message, listenAfter = true)
                }
        }
    }
}
