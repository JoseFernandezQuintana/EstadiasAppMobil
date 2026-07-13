package com.cecapi.app.feature.modulo4_lectordocumentos

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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DocumentReaderUiState(
    val isProcessing: Boolean = false,
    val documentoId: Long? = null,
    val recognizedText: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class DocumentReaderViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val repository: DocumentReaderRepository,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _uiState = MutableStateFlow(DocumentReaderUiState())
    val uiState: StateFlow<DocumentReaderUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<DocumentoEscaneadoEntity>> = sessionRepository.currentUser
        .filterNotNull()
        .flatMapLatest { usuario -> repository.observeRecent(usuario.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        voiceEngine.speak("Cámara inteligente lista. Apunta a un documento y toca capturar para leerlo en voz alta.")
    }

    fun onPhotoCaptured(imageUri: Uri, rutaImagen: String) {
        val usuario = sessionRepository.currentUser.value ?: return
        _uiState.value = _uiState.value.copy(isProcessing = true, errorMessage = null)
        voiceEngine.speak("Procesando la imagen.")
        viewModelScope.launch {
            repository.processCapturedPhoto(usuario.id, imageUri, rutaImagen)
                .onSuccess { result ->
                    _uiState.value = DocumentReaderUiState(documentoId = result.documentoId, recognizedText = result.texto)
                    voiceEngine.speak(result.texto)
                    repository.logLectura(result.documentoId)
                }
                .onFailure {
                    val message = "No se pudo leer el texto de la imagen. Intenta con mejor iluminación."
                    _uiState.value = DocumentReaderUiState(errorMessage = message)
                    voiceEngine.speak(message)
                }
        }
    }

    fun onReadAgainRequested() {
        val state = _uiState.value
        if (state.recognizedText != null) {
            voiceEngine.speak(state.recognizedText)
            state.documentoId?.let { id -> viewModelScope.launch { repository.logLectura(id) } }
        }
    }
}
