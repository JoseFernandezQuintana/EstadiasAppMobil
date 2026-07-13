package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.util.ConnectivityObserver
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeNavEvent {
    data object GoToLogin : HomeNavEvent
    data class GoToModule(val route: String) : HomeNavEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    val isOnline: StateFlow<Boolean> = connectivityObserver.observe().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), true,
    )

    private val _navEvents = MutableSharedFlow<HomeNavEvent>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<HomeNavEvent> = _navEvents

    init {
        voiceEngine.speak(
            "Hola, estoy escuchándote. Toca el micrófono para hablar. " +
                "Puedes decir iniciar sesión, ayuda o cámara.",
        )
        viewModelScope.launch {
            voiceEngine.recognizedSpeech.collect { speech ->
                interpretCommand(speech.text.lowercase())
            }
        }
    }

    fun onMicTapped() {
        voiceEngine.startListening()
    }

    fun onSuggestionTapped(command: String) {
        interpretCommand(command.lowercase())
    }

    private fun interpretCommand(text: String) {
        when {
            "iniciar sesión" in text || "iniciar sesion" in text -> _navEvents.tryEmit(HomeNavEvent.GoToLogin)
            "ayuda" in text -> voiceEngine.speak(
                "CECAPI te ayuda a escuchar tus documentos, hacer solicitudes y practicar el uso del teléfono. " +
                    "Di iniciar sesión para comenzar.",
            )
            "cámara" in text || "camara" in text -> _navEvents.tryEmit(HomeNavEvent.GoToLogin)
            else -> voiceEngine.speak("No entendí ese comando. Di iniciar sesión o ayuda.")
        }
    }

    override fun onCleared() {
        voiceEngine.stopListening()
        super.onCleared()
    }
}
