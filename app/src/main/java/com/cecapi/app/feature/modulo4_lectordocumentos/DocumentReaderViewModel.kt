package com.cecapi.app.feature.modulo4_lectordocumentos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val estaPausado: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
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
        voiceEngine.speak("Cámara inteligente lista. Apunta a un documento y toca el botón azul para capturarlo.")
    }

    fun onPhotoCaptured(imageUri: Uri, rutaImagen: String) {
        val usuario = sessionRepository.currentUser.value ?: return
        _uiState.value = _uiState.value.copy(
            isProcessing = true,
            errorMessage = null,
            estaLeyendo = false,
            estaPausado = false,
        )
        voiceEngine.speak("Procesando la imagen.")
        viewModelScope.launch {
            when (val outcome = repository.processCapturedPhoto(usuario.id, imageUri, rutaImagen)) {
                is OcrOutcome.Exito -> {
                    _uiState.value = DocumentReaderUiState(
                        isProcessing = false,
                        documentoId = outcome.documentoId,
                        recognizedText = outcome.textoCompleto,
                        parrafos = outcome.parrafos,
                    )
                    leerParrafo(0)
                    repository.logLectura(outcome.documentoId)
                }
                OcrOutcome.PocaLuz -> {
                    val message = "La imagen está muy oscura. Busca mejor iluminación e intenta de nuevo."
                    _uiState.value = DocumentReaderUiState(isProcessing = false, errorMessage = message)
                    voiceEngine.speak(message)
                }
                OcrOutcome.Borrosa -> {
                    val message = "La imagen salió borrosa. Sostén el teléfono firme y vuelve a intentar."
                    _uiState.value = DocumentReaderUiState(isProcessing = false, errorMessage = message)
                    voiceEngine.speak(message)
                }
                OcrOutcome.SinTexto -> {
                    val message = "No se detectó texto en la imagen."
                    _uiState.value = DocumentReaderUiState(isProcessing = false, errorMessage = message)
                    voiceEngine.speak(message)
                }
                is OcrOutcome.Error -> {
                    val message = "No se pudo leer el texto de la imagen. Intenta con mejor iluminación."
                    _uiState.value = DocumentReaderUiState(isProcessing = false, errorMessage = message)
                    voiceEngine.speak(message)
                }
            }
        }
    }

    /** Repite la lectura del documento desde el inicio (párrafo 0). */
    fun onRepeatFromStartRequested() {
        val state = _uiState.value
        if (state.parrafos.isNotEmpty()) {
            voiceEngine.speak("Repetiendo lectura desde el inicio.")
            leerParrafo(0)
            state.documentoId?.let { id -> viewModelScope.launch { repository.logLectura(id) } }
        }
    }

    /** Alterna entre pausar la lectura actual y continuar en la posición donde se pausó. */
    fun onPauseResumeToggle() {
        val state = _uiState.value
        if (state.estaPausado) {
            _uiState.value = state.copy(estaPausado = false, estaLeyendo = true)
            voiceEngine.speak("Reanudando lectura.")
            leerParrafo(state.parrafoActual)
        } else if (state.estaLeyendo) {
            voiceEngine.stopSpeaking()
            _uiState.value = state.copy(estaLeyendo = false, estaPausado = true)
            voiceEngine.speak("Lectura pausada.")
        } else if (state.parrafos.isNotEmpty()) {
            val indiceReanudar = if (state.parrafoActual >= state.parrafos.size) 0 else state.parrafoActual
            _uiState.value = state.copy(estaPausado = false, estaLeyendo = true)
            leerParrafo(indiceReanudar)
        }
    }

    /** Cancela la lectura actual y vuelve a la pantalla de la cámara a pantalla completa. */
    fun onResetToCameraRequested() {
        voiceEngine.stopSpeaking()
        voiceEngine.speak("Cámara lista para tomar foto.")
        _uiState.value = DocumentReaderUiState()
    }

    /** Detiene la voz y navega de regreso al menú principal. */
    fun onNavigateToMenuRequested(onNavigateToMenu: () -> Unit) {
        voiceEngine.stopSpeaking()
        voiceEngine.speak("Volviendo al menú principal.")
        onNavigateToMenu()
    }

    /** Dice en voz alta el párrafo [indice] y lo marca como el actual. */
    private fun leerParrafo(indice: Int) {
        val parrafo = _uiState.value.parrafos.getOrNull(indice) ?: return
        _uiState.value = _uiState.value.copy(parrafoActual = indice, estaLeyendo = true, estaPausado = false)
        voiceEngine.speak(parrafo)
    }

    private fun observarFinDeParrafo() {
        viewModelScope.launch {
            var anterior: VoiceState = VoiceState.Idle
            voiceEngine.state.collect { actual ->
                val state = _uiState.value
                val parrafo = state.parrafos.getOrNull(state.parrafoActual)
                if ((state.estaLeyendo && !state.estaPausado) && parrafo != null) {
                    if (actual is VoiceState.Speaking && actual.text != parrafo) {
                        if (!actual.text.startsWith("Lectura pausada") &&
                            !actual.text.startsWith("Reanudando") &&
                            !actual.text.startsWith("Repetiendo") &&
                            !actual.text.startsWith("Volviendo")
                        ) {
                            _uiState.value = state.copy(estaLeyendo = false)
                        }
                    } else if (actual is VoiceState.Idle && anterior == VoiceState.Speaking(parrafo)) {
                        val siguiente = state.parrafoActual + 1
                        if (siguiente < state.parrafos.size) {
                            leerParrafo(siguiente)
                        } else {
                            _uiState.value = state.copy(estaLeyendo = false, estaPausado = false)
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
