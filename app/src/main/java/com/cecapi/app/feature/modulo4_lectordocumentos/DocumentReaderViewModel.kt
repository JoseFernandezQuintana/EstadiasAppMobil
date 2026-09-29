package com.cecapi.app.feature.modulo4_lectordocumentos

import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.voice.AssistantMode
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceMessages
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.feature.modulo1_aplicacionprincipal.CommandCatalog
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
    val capturaArmada: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DocumentReaderViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val repository: DocumentReaderRepository,
    private val cues: FeedbackCues,
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

    private val _captureRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val captureRequests: SharedFlow<Unit> = _captureRequests

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    private val _routes = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val routes: SharedFlow<String> = _routes

    // Guía de encuadre: solo se habla cuando la misma indicación se repite (evita ruido de un cuadro suelto).
    private var ultimaPista: FramingHint? = null
    private var pistaRepetida = 0
    private var pistaDicha: FramingHint? = null
    private var momentoPistaDicha = 0L

    init {
        observarFinDeParrafo()
        viewModelScope.launch {
            voiceEngine.recognizedSpeech.collect { speech -> onSpeech(speech.text) }
        }
        voiceEngine.speak(
            "Lector de texto. Apunta la cámara a un documento y toca el botón azul o di toma la foto. " + CommandCatalog.hint("cámara"),
            listenAfter = true,
        )
    }

    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = words.any { it in text }
        val hayDocumento = _uiState.value.parrafos.isNotEmpty()
        when {
            CommandCatalog.isRequest(spoken) -> voiceEngine.speak(CommandCatalog.READER, listenAfter = true)
            has("parrafo anterior", "anterior") -> anteriorParrafo()
            has("atras", "volver", "salir", "menu", "regresa") -> {
                detenerLectura()
                _back.tryEmit(Unit)
            }
            has("enfrente", "entorno", "describe", "alrededor") -> cambiarA(
                CecapiDestinations.ENVIRONMENT,
                "Cambiando a describir lo que hay enfrente.",
            )
            has("otra foto", "nueva foto", "otro documento", "nuevo documento", "otro papel") -> nuevaFoto(
                tomarYa = has("toma", "captura", "saca"),
            )
            has("otra vez", "repite", "repetir", "de nuevo", "desde el principio", "empieza") -> repetirLectura()
            has("siguiente", "adelante") -> siguienteParrafo()
            has("pausa", "pausar") -> pausarLectura()
            has("continua", "continuar", "sigue", "reanuda") -> continuarLectura()
            has("foto", "fotografia", "captura", "toma", "escanea") -> pedirCaptura()
            has("lee", "leer", "leelo") -> if (hayDocumento) repetirLectura() else pedirCaptura()
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak(
                    "No entendí. Di toma la foto, repite, siguiente párrafo o atrás. " + CommandCatalog.hint("cámara"),
                    listenAfter = true,
                )
            }
        }
    }

    private fun sesionIniciada(): Boolean {
        if (sessionRepository.currentUser.value != null) return true
        voiceEngine.speak(VoiceMessages.NEEDS_LOGIN)
        return false
    }

    fun onBotonCapturaPresionado(): Boolean {
        if (_uiState.value.capturaArmada) {
            _uiState.value = _uiState.value.copy(capturaArmada = false)
            return true
        }
        if (!sesionIniciada()) return false
        _uiState.value = _uiState.value.copy(capturaArmada = true)
        voiceEngine.speak("Vas a tomar una foto del documento. Toca otra vez para capturarla.")
        return false
    }

    fun pedirCaptura() {
        if (_uiState.value.isProcessing || !sesionIniciada()) return
        detenerLectura()
        _captureRequests.tryEmit(Unit)
    }

    /**
     * Recibe lo que ve la cámara en vivo y guía por voz ("mueve el teléfono a la izquierda", "acércalo un poco").
     * No interrumpe otra frase ni el micrófono abierto, y no repite la misma indicación muy seguido.
     */
    fun onFramingHint(pista: FramingHint) {
        val state = _uiState.value
        if (state.isProcessing || state.parrafos.isNotEmpty()) return

        pistaRepetida = if (pista == ultimaPista) pistaRepetida + 1 else 1
        ultimaPista = pista
        val necesarias = if (pista == FramingHint.SIN_TEXTO) CUADROS_SIN_TEXTO else CUADROS_PISTA
        if (pistaRepetida < necesarias) return
        if (voiceEngine.state.value != VoiceState.Idle) return
        if (pista == FramingHint.LISTO && pistaDicha == FramingHint.LISTO) return

        val ahora = SystemClock.elapsedRealtime()
        val espera = when {
            pista != pistaDicha -> ESPERA_CAMBIO_MS
            pista == FramingHint.SIN_TEXTO -> ESPERA_SIN_TEXTO_MS
            else -> ESPERA_REPETIR_MS
        }
        if (ahora - momentoPistaDicha < espera) return

        pistaDicha = pista
        momentoPistaDicha = ahora
        voiceEngine.speak(pista.mensaje)
    }

    private fun reiniciarGuia() {
        ultimaPista = null
        pistaRepetida = 0
        pistaDicha = null
        momentoPistaDicha = 0L
    }

    fun onCameraPermissionDenied() {
        voiceEngine.speak("Sin el permiso de la cámara no puedo leer. Actívalo en los ajustes de la aplicación.")
    }

    fun onCaptureFailed() {
        val message = "No pude tomar la foto. Revisa que la cámara esté libre e intenta de nuevo."
        _uiState.value = DocumentReaderUiState(errorMessage = message)
        cues.play(FeedbackCues.Cue.ERROR)
        voiceEngine.speak(message, listenAfter = true)
    }

    fun onPhotoCaptured(imageUri: Uri, rutaImagen: String) {
        val usuario = sessionRepository.currentUser.value ?: run {
            voiceEngine.speak(VoiceMessages.NEEDS_LOGIN)
            return
        }
        _uiState.value = _uiState.value.copy(
            isProcessing = true,
            errorMessage = null,
            estaLeyendo = false,
            estaPausado = false,
            capturaArmada = false,
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
                OcrOutcome.PocaLuz -> falloDeLectura("La imagen está muy oscura. Busca mejor iluminación e intenta de nuevo.")
                OcrOutcome.Borrosa -> falloDeLectura("La imagen salió borrosa. Sostén el teléfono firme y vuelve a intentar.")
                OcrOutcome.SinTexto -> falloDeLectura("No se detectó texto en la imagen. " + consejoSinTexto())
                is OcrOutcome.Error -> falloDeLectura("No se pudo leer el texto de la imagen. Intenta con mejor iluminación.")
            }
        }
    }

    /** Usa lo último que vio la cámara en vivo para decir cómo corregir la foto. */
    private fun consejoSinTexto(): String = when (val pista = ultimaPista) {
        null, FramingHint.LISTO, FramingHint.SIN_TEXTO -> FramingHint.SIN_TEXTO.mensaje
        else -> pista.mensaje
    }

    private fun falloDeLectura(message: String) {
        reiniciarGuia()
        _uiState.value = DocumentReaderUiState(errorMessage = message)
        cues.play(FeedbackCues.Cue.ERROR)
        voiceEngine.speak("$message Di toma la foto para intentarlo otra vez.", listenAfter = true)
    }

    fun onRepeatFromStartRequested() = repetirLectura()

    fun onPauseResumeToggle() {
        val state = _uiState.value
        if (state.estaPausado) {
            continuarLectura()
        } else if (state.estaLeyendo) {
            pausarLectura()
        } else if (state.parrafos.isNotEmpty()) {
            continuarLectura()
        }
    }

    fun onResetToCameraRequested() = nuevaFoto(tomarYa = false)

    fun onNavigateToMenuRequested(onNavigateToMenu: () -> Unit) {
        detenerLectura()
        voiceEngine.speak("Volviendo al menú principal.")
        _back.tryEmit(Unit)
        onNavigateToMenu()
    }

    private fun leerParrafo(indice: Int) {
        val lista = _uiState.value.parrafos
        var i = indice
        while (i < lista.size && VoiceText.forSpeech(lista[i]).isBlank()) i++
        val parrafo = lista.getOrNull(i)
        if (parrafo == null || voiceEngine.mode.value != AssistantMode.ACTIVE) {
            _uiState.value = _uiState.value.copy(estaLeyendo = false, estaPausado = false)
            return
        }
        _uiState.value = _uiState.value.copy(parrafoActual = i, estaLeyendo = true, estaPausado = false)
        voiceEngine.speak(parrafo)
    }

    private fun sinDocumento() {
        voiceEngine.speak("Todavía no he leído nada. Di toma la foto.", listenAfter = true)
    }

    private fun detenerLectura() {
        if (!_uiState.value.estaLeyendo) return
        _uiState.value = _uiState.value.copy(estaLeyendo = false)
        voiceEngine.stopSpeaking()
    }

    fun repetirLectura() {
        if (_uiState.value.parrafos.isEmpty()) return sinDocumento()
        voiceEngine.speak("Repetiendo lectura desde el inicio.")
        leerParrafo(0)
    }

    fun siguienteParrafo() {
        val state = _uiState.value
        if (state.parrafos.isEmpty()) return sinDocumento()
        val siguiente = state.parrafoActual + 1
        if (siguiente >= state.parrafos.size) {
            _uiState.value = state.copy(estaLeyendo = false)
            voiceEngine.speak("Ese era el último párrafo. Di repite para leer desde el principio.", listenAfter = true)
        } else {
            leerParrafo(siguiente)
        }
    }

    fun anteriorParrafo() {
        val state = _uiState.value
        if (state.parrafos.isEmpty()) return sinDocumento()
        if (state.parrafoActual <= 0) {
            _uiState.value = state.copy(estaLeyendo = false)
            voiceEngine.speak("Estás en el primer párrafo. Di repite para leerlo otra vez.", listenAfter = true)
        } else {
            leerParrafo(state.parrafoActual - 1)
        }
    }

    fun pausarLectura() {
        if (!_uiState.value.estaLeyendo) {
            voiceEngine.speak("No estoy leyendo en este momento.", listenAfter = true)
            return
        }
        detenerLectura()
        _uiState.value = _uiState.value.copy(estaPausado = true)
        voiceEngine.speak("En pausa. Di continúa para seguir.", listenAfter = true)
    }

    fun continuarLectura() {
        if (_uiState.value.parrafos.isEmpty()) return sinDocumento()
        _uiState.value = _uiState.value.copy(estaPausado = false)
        voiceEngine.speak("Reanudando lectura.")
        leerParrafo(_uiState.value.parrafoActual)
    }

    private fun nuevaFoto(tomarYa: Boolean) {
        detenerLectura()
        reiniciarGuia()
        _uiState.value = DocumentReaderUiState()
        if (tomarYa) {
            pedirCaptura()
        } else {
            voiceEngine.speak("Cámara lista. Apunta al nuevo papel y di toma la foto.", listenAfter = true)
        }
    }

    private fun cambiarA(route: String, speech: String) {
        detenerLectura()
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak(speech)
        _routes.tryEmit(route)
    }

    fun onCommandsRequested() {
        voiceEngine.speak(CommandCatalog.READER, listenAfter = true)
    }

    private fun observarFinDeParrafo() {
        viewModelScope.launch {
            var anterior: VoiceState = VoiceState.Idle
            voiceEngine.state.collect { actual ->
                val state = _uiState.value
                val parrafo = state.parrafos.getOrNull(state.parrafoActual)
                if (state.estaLeyendo && !state.estaPausado && parrafo != null) {
                    val hablado = VoiceText.forSpeech(parrafo)
                    if (actual is VoiceState.Speaking && actual.text != hablado) {
                        if (!actual.text.startsWith("Lectura pausada") &&
                            !actual.text.startsWith("En pausa") &&
                            !actual.text.startsWith("Reanudando") &&
                            !actual.text.startsWith("Repetiendo") &&
                            !actual.text.startsWith("Volviendo")
                        ) {
                            _uiState.value = state.copy(estaLeyendo = false)
                        }
                    } else if (actual is VoiceState.Idle && anterior == VoiceState.Speaking(hablado)) {
                        if (voiceEngine.mode.value != AssistantMode.ACTIVE) {
                            _uiState.value = state.copy(estaLeyendo = false)
                        } else {
                            siguienteAutomatico(state)
                        }
                    }
                }
                anterior = actual
            }
        }
    }

    private fun siguienteAutomatico(state: DocumentReaderUiState) {
        val siguiente = state.parrafoActual + 1
        if (siguiente < state.parrafos.size) {
            leerParrafo(siguiente)
        } else {
            _uiState.value = state.copy(estaLeyendo = false, estaPausado = false)
        }
    }

    private companion object {
        /** Cuadros seguidos (uno cada ~0.7 s) con la misma indicación antes de decirla. */
        const val CUADROS_PISTA = 2
        const val CUADROS_SIN_TEXTO = 4

        const val ESPERA_CAMBIO_MS = 2_500L
        const val ESPERA_REPETIR_MS = 5_000L
        const val ESPERA_SIN_TEXTO_MS = 10_000L
    }

    override fun onCleared() {
        if (_uiState.value.estaLeyendo) voiceEngine.stopSpeaking()
        super.onCleared()
    }
}
