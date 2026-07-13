package com.cecapi.app.feature.modulo7_entorno

import android.net.Uri
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
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _uiState = MutableStateFlow(EnvironmentUiState())
    val uiState: StateFlow<EnvironmentUiState> = _uiState.asStateFlow()

    init {
        voiceEngine.speak("Asistente del entorno listo. Apunta la cámara al frente y toca capturar para saber qué hay.")
    }

    fun onPhotoCaptured(imageUri: Uri, rutaImagen: String) {
        val usuario = sessionRepository.currentUser.value ?: return
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
                    voiceEngine.speak(message)
                }
        }
    }
}
