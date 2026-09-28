package com.cecapi.app.feature.modulo4_lectordocumentos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.ui.GALLERY_WORDS
import com.cecapi.app.core.util.StorageReport
import com.cecapi.app.core.voice.AssistantMode
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
    val capturaArmada: Boolean = false,
)

@HiltViewModel
class DocumentReaderViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val repository: DocumentReaderRepository,
    private val cues: FeedbackCues,
    private val storageReport: StorageReport,
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

    // The screen owns the camera, so voice commands reach it through these.
    private val _captureRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val captureRequests: SharedFlow<Unit> = _captureRequests

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    // Asks the screen to open the system picture picker (it owns the activity result launcher).
    private val _pickRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val pickRequests: SharedFlow<Unit> = _pickRequests

    private val _routes = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val routes: SharedFlow<String> = _routes

    init {
        observarFinDeParrafo()
        viewModelScope.launch {
            voiceEngine.recognizedSpeech.collect { speech -> onSpeech(speech.text) }
        }
        voiceEngine.speak(
            "Lector de texto. Apunta la cámara a un papel y di toma la foto. " + CommandCatalog.hint("cámara"),
            listenAfter = true,
        )
    }

    /** The commands the reader answers, most specific first. */
    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        val hayDocumento = _uiState.value.parrafos.isNotEmpty()
        when {
            CommandCatalog.isRequest(spoken) -> voiceEngine.speak(CommandCatalog.READER, listenAfter = true)
            has("parrafo anterior", "anterior") -> anteriorParrafo()
            has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio", "pantalla anterior") -> {
                detenerLectura()
                _back.tryEmit(Unit)
            }
            has("enfrente", "entorno", "describe", "alrededor") -> cambiarA(
                CecapiDestinations.ENVIRONMENT,
                "Cambiando a describir lo que hay enfrente.",
            )
            has(*GALLERY_WORDS) -> pedirGaleria()
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
                    "No entendí. Di toma la foto, repite, siguiente párrafo o atrás. " +
                        CommandCatalog.hint("cámara"),
                    listenAfter = true,
                )
            }
        }
    }

    /** Reading needs a signed-in person because every document is saved to their history. */
    private fun sesionIniciada(): Boolean {
        if (sessionRepository.currentUser.value == null) {
            voiceEngine.speak(VoiceMessages.NEEDS_LOGIN)
            return false
        }
        return hayEspacio()
    }

    /** Every photo is kept on the phone, so with almost no room left it is better to say so than to fill it. */
    private fun hayEspacio(): Boolean {
        if (!storageReport.criticallyLow()) return true
        cues.play(FeedbackCues.Cue.WARNING)
        voiceEngine.speak(
            "Queda muy poco espacio en el teléfono y no puedo guardar más fotos. " +
                "Ve a configuración y di libera espacio, o limpia la caché.",
        )
        return false
    }

    /**
     * Toque en el botón de cámara. El primer toque solo anuncia la acción por
     * voz y "arma" la captura; el segundo toque es el que realmente toma la
     * foto. Devuelve true cuando el llamador debe disparar la captura.
     */
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

    /** "Toma la foto" by voice: no second tap needed, the person already meant it. */
    fun pedirCaptura() {
        if (_uiState.value.isProcessing || !sesionIniciada()) return
        detenerLectura()
        _captureRequests.tryEmit(Unit)
    }

    fun onCameraPermissionDenied() {
        voiceEngine.speak("Sin el permiso de la cámara no puedo leer. Actívalo en los ajustes de la aplicación.")
    }

    /**
     * "Elige una foto": opens the system picture picker. The person picks one picture and the app sees only
     * that one; there is no permission to the whole gallery. Said out loud first so it is always their decision.
     */
    fun pedirGaleria() {
        if (_uiState.value.isProcessing || !sesionIniciada()) return
        detenerLectura()
        voiceEngine.speak("Voy a abrir tus fotos. Elige la imagen que quieres que lea; solo veré esa.")
        _pickRequests.tryEmit(Unit)
    }

    fun onPickCancelled() {
        voiceEngine.speak("No elegiste ninguna foto. Di toma la foto, o elige una foto.", listenAfter = true)
    }

    fun onPickFailed() {
        cues.play(FeedbackCues.Cue.ERROR)
        voiceEngine.speak("No pude abrir esa imagen. Prueba con otra.", listenAfter = true)
    }

    fun onCaptureFailed() {
        val message = "No pude tomar la foto. Revisa que la cámara esté libre e intenta de nuevo."
        _uiState.value = DocumentReaderUiState(errorMessage = message)
        cues.play(FeedbackCues.Cue.ERROR)
        voiceEngine.speak(message, listenAfter = true)
    }

    fun onPhotoCaptured(imageUri: Uri, rutaImagen: String) {
        val usuario = sessionRepository.currentUser.value ?: run {
            // Nobody signed in: say so instead of ignoring the user in silence.
            voiceEngine.speak(VoiceMessages.NEEDS_LOGIN)
            return
        }
        _uiState.value = _uiState.value.copy(
            isProcessing = true,
            errorMessage = null,
            estaLeyendo = false,
            capturaArmada = false,
        )
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
                OcrOutcome.PocaLuz -> falloDeLectura(
                    "La imagen está muy oscura. Busca mejor iluminación e intenta de nuevo.",
                )
                OcrOutcome.Borrosa -> falloDeLectura(
                    "La imagen salió borrosa. Sostén el teléfono firme y vuelve a intentar.",
                )
                OcrOutcome.SinTexto -> falloDeLectura("No se detectó texto en la imagen.")
                is OcrOutcome.Error -> falloDeLectura(
                    "No se pudo leer el texto de la imagen. Intenta con mejor iluminación.",
                )
            }
        }
    }

    private fun falloDeLectura(message: String) {
        _uiState.value = DocumentReaderUiState(errorMessage = message)
        cues.play(FeedbackCues.Cue.ERROR)
        voiceEngine.speak("$message Di toma la foto para intentarlo otra vez.", listenAfter = true)
    }

    /** Dice en voz alta el párrafo [indice] y lo marca como el actual. */
    private fun leerParrafo(indice: Int) {
        val lista = _uiState.value.parrafos
        // A paragraph with nothing to pronounce (only symbols) would never start, so skip it.
        var i = indice
        while (i < lista.size && VoiceText.forSpeech(lista[i]).isBlank()) i++
        val parrafo = lista.getOrNull(i)
        if (parrafo == null || voiceEngine.mode.value != AssistantMode.ACTIVE) {
            _uiState.value = _uiState.value.copy(estaLeyendo = false)
            return
        }
        _uiState.value = _uiState.value.copy(parrafoActual = i, estaLeyendo = true)
        voiceEngine.speak(parrafo)
    }

    private fun sinDocumento() {
        voiceEngine.speak("Todavía no he leído nada. Di toma la foto.", listenAfter = true)
    }

    /** Silences the reading without letting the paragraph observer move on to the next one. */
    private fun detenerLectura() {
        if (!_uiState.value.estaLeyendo) return
        _uiState.value = _uiState.value.copy(estaLeyendo = false)
        voiceEngine.stopSpeaking()
    }

    fun repetirLectura() {
        if (_uiState.value.parrafos.isEmpty()) return sinDocumento()
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
        voiceEngine.speak("En pausa. Di continúa para seguir.", listenAfter = true)
    }

    fun continuarLectura() {
        if (_uiState.value.parrafos.isEmpty()) return sinDocumento()
        leerParrafo(_uiState.value.parrafoActual)
    }

    /** Clears the last document so the next photo starts fresh. */
    private fun nuevaFoto(tomarYa: Boolean) {
        detenerLectura()
        _uiState.value = DocumentReaderUiState()
        if (tomarYa) {
            pedirCaptura()
        } else {
            voiceEngine.speak("Listo. Apunta la cámara al nuevo papel y di toma la foto.", listenAfter = true)
        }
    }

    private fun cambiarA(route: String, speech: String) {
        detenerLectura()
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak(speech)
        _routes.tryEmit(route)
    }

    /** Reads out the commands for this screen; the button in the header calls it. */
    fun onCommandsRequested() {
        voiceEngine.speak(CommandCatalog.READER, listenAfter = true)
    }

    /**
     * TextToSpeech no avisa directamente cuándo termina: VoiceEngine pasa de
     * Speaking a Idle. Solo avanzamos si lo que terminó fue el párrafo actual;
     * stopSpeaking() también deja el estado en Idle, por eso se revisa estaLeyendo.
     * VoiceEngine limpia el texto antes de hablarlo, así que se compara contra el texto ya limpio.
     */
    private fun observarFinDeParrafo() {
        viewModelScope.launch {
            var anterior: VoiceState = VoiceState.Idle
            voiceEngine.state.collect { actual ->
                val state = _uiState.value
                val parrafo = state.parrafos.getOrNull(state.parrafoActual)
                if (state.estaLeyendo && parrafo != null) {
                    val hablado = VoiceText.forSpeech(parrafo)
                    if (actual is VoiceState.Speaking && actual.text != hablado) {
                        // Otro módulo habló encima de la lectura: la damos por interrumpida.
                        _uiState.value = state.copy(estaLeyendo = false)
                    } else if (actual is VoiceState.Idle && anterior == VoiceState.Speaking(hablado)) {
                        if (voiceEngine.mode.value != AssistantMode.ACTIVE) {
                            // "Silencio" o "para" cortaron la voz: no seguir leyendo.
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
            _uiState.value = state.copy(estaLeyendo = false)
        }
    }

    override fun onCleared() {
        if (_uiState.value.estaLeyendo) voiceEngine.stopSpeaking()
        super.onCleared()
    }
}
