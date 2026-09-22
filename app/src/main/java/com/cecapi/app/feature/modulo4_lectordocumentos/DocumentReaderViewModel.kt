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
    val parrafos: List<String> = emptyList(),
    val parrafoActual: Int = 0,
    val estaLeyendo: Boolean = false,
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
        observarFinDeParrafo()
        voiceEngine.speak("Cámara inteligente lista. Apunta a un documento y toca capturar para leerlo en voz alta.")
    }

    fun onPhotoCaptured(imageUri: Uri, rutaImagen: String) {
        val usuario = sessionRepository.currentUser.value ?: return
        _uiState.value = _uiState.value.copy(isProcessing = true, errorMessage = null, estaLeyendo = false)
        voiceEngine.speak("Procesando la imagen.")
        viewModelScope.launch {
            when (val outcome = repository.processCapturedPhoto(usuario.id, imageUri, rutaImagen)) {
                is OcrOutcome.Exito -> {
                    _uiState.value = DocumentReaderUiState(
                        documentoId = outcome.documentoId,
                        recognizedText = outcome.textoCompleto,
                        parrafos = outcome.parrafos,
                    )
                    leerParrafo(0)
                    repository.logLectura(outcome.documentoId)
                }
                OcrOutcome.PocaLuz -> {
                    val message = "La imagen está muy oscura. Busca mejor iluminación e intenta de nuevo."
                    _uiState.value = DocumentReaderUiState(errorMessage = message)
                    voiceEngine.speak(message)
                }
                OcrOutcome.Borrosa -> {
                    val message = "La imagen salió borrosa. Sostén el teléfono firme y vuelve a intentar."
                    _uiState.value = DocumentReaderUiState(errorMessage = message)
                    voiceEngine.speak(message)
                }
                OcrOutcome.SinTexto -> {
                    val message = "No se detectó texto en la imagen."
                    _uiState.value = DocumentReaderUiState(errorMessage = message)
                    voiceEngine.speak(message)
                }
                is OcrOutcome.Error -> {
                    val message = "No se pudo leer el texto de la imagen. Intenta con mejor iluminación."
                    _uiState.value = DocumentReaderUiState(errorMessage = message)
                    voiceEngine.speak(message)
                }
            }
        }
    }

    fun onReadAgainRequested() {
        val state = _uiState.value
        if (state.parrafos.isNotEmpty()) {
            leerParrafo(0)
            state.documentoId?.let { id -> viewModelScope.launch { repository.logLectura(id) } }
        }
    }

    /** Dice en voz alta el párrafo [indice] y lo marca como el actual. */
    private fun leerParrafo(indice: Int) {
        val parrafo = _uiState.value.parrafos.getOrNull(indice) ?: return
        _uiState.value = _uiState.value.copy(parrafoActual = indice, estaLeyendo = true)
        voiceEngine.speak(parrafo)
    }

    /**
     * TextToSpeech no avisa directamente cuándo termina: VoiceEngine pasa de
     * Speaking a Idle. Solo avanzamos si lo que terminó fue el párrafo actual;
     * stopSpeaking() también deja el estado en Idle, por eso se revisa estaLeyendo.
     */
    private fun observarFinDeParrafo() {
        viewModelScope.launch {
            var anterior: VoiceState = VoiceState.Idle
            voiceEngine.state.collect { actual ->
                val state = _uiState.value
                val parrafo = state.parrafos.getOrNull(state.parrafoActual)
                if (state.estaLeyendo && parrafo != null) {
                    if (actual is VoiceState.Speaking && actual.text != parrafo) {
                        // Otro módulo habló encima de la lectura: la damos por interrumpida.
                        _uiState.value = state.copy(estaLeyendo = false)
                    } else if (actual is VoiceState.Idle && anterior == VoiceState.Speaking(parrafo)) {
                        val siguiente = state.parrafoActual + 1
                        if (siguiente < state.parrafos.size) {
                            leerParrafo(siguiente)
                        } else {
                            _uiState.value = state.copy(estaLeyendo = false)
                        }
                    }
                }
                anterior = actual
            }
        }
    }

    override fun onCleared() {
        if (_uiState.value.estaLeyendo) voiceEngine.stopSpeaking()
        super.onCleared()
    }
}
