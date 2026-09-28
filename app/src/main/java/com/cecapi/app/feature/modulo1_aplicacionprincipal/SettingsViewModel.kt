package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.util.StorageReport
import com.cecapi.app.core.util.StorageUsage
import com.cecapi.app.core.util.VolumeControl
import com.cecapi.app.core.voice.DeviceSettings
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.notifications.NotificationAccess
import com.cecapi.app.service.BackgroundListening
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

/**
 * Configuración: the technical side (volume, notification access, listening outside the app, account).
 * How the assistant sounds and what it calls the person lives in Personalización.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val deviceSettings: DeviceSettings,
    private val volumeControl: VolumeControl,
    private val cues: FeedbackCues,
    private val notificationAccess: NotificationAccess,
    private val backgroundListening: BackgroundListening,
    private val sessionRepository: SessionRepository,
    private val storageReport: StorageReport,
) : ViewModel() {

    val simpleMode: StateFlow<Boolean> = deviceSettings.simpleMode.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val announceNotifications: StateFlow<Boolean> = deviceSettings.announceNotifications.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val listenOutsideApp: StateFlow<Boolean> = deviceSettings.backgroundListening.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The signed-in user, or null when Configuración was opened from the home screen. */
    val currentUser: StateFlow<UsuarioEntity?> = sessionRepository.currentUser

    private val _loggedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loggedOut: SharedFlow<Unit> = _loggedOut

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    // Turning listening outside the app on needs a permission the screen asks for, so the screen does the switching.
    private val _listenOutsideRequests = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val listenOutsideRequests: SharedFlow<Boolean> = _listenOutsideRequests

    private val _storage = MutableStateFlow<StorageUsage?>(null)
    val storage: StateFlow<StorageUsage?> = _storage.asStateFlow()

    /** True while the person has been asked whether to delete the old photos and has not answered. */
    private val _pendingClean = MutableStateFlow(false)
    val pendingClean: StateFlow<Boolean> = _pendingClean.asStateFlow()

    init {
        refreshStorage()
        viewModelScope.launch {
            voiceEngine.recognizedSpeech.collect { speech -> onSpeech(speech.text) }
        }
        voiceEngine.speak(
            "Configuración. Aquí están el volumen, las notificaciones, escuchar fuera de la aplicación y tu cuenta. " +
                "Mantén presionado cualquier control para que te explique para qué sirve. " +
                CommandCatalog.hint("configuración"),
            listenAfter = true,
        )
    }

    /**
     * The commands this screen answers. Volume and anything that names notifications are global commands
     * (they never reach here), so the notification switch is worded as "avisos".
     */
    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        val enable = when {
            listOf("desactiv", "apaga", "quita", "sin ", "no quiero").let { phrases -> VoiceText.hasAny(text, phrases) } -> false
            listOf("activ", "enciende", "prende", "pon ", "quiero").let { phrases -> VoiceText.hasAny(text, phrases) } -> true
            else -> null
        }
        // "si" and "no" must be whole words: "siguiente" contains "si".
        val spokenWords = text.split(" ")
        fun hasWord(vararg words: String) = words.any { it in spokenWords }
        val cleaning = _pendingClean.value
        when {
            CommandCatalog.isRequest(spoken) -> voiceEngine.speak(CommandCatalog.SETTINGS, listenAfter = true)
            cleaning && hasWord("si", "claro", "dale", "ok", "okey", "supuesto", "afirmativo", "correcto", "seguro", "hazlo", "confirmo", "borralas", "adelante") -> confirmClean()
            cleaning && hasWord("no", "nunca", "negativo", "olvidalo", "dejalo", "cancela", "cancelar") -> cancelClean()
            has("cuanto espacio", "espacio libre", "almacenamiento", "cuanto ocupa") -> speakStorage()
            has("libera espacio", "liberar espacio", "libera memoria", "limpia el espacio", "limpiar espacio", "borra las fotos", "borrar las fotos") ->
                askClean()
            has("cerrar sesion", "cierra sesion", "cierra mi sesion", "salir de la cuenta") ->
                if (currentUser.value != null) onLogout() else voiceEngine.speak("No hay una sesión iniciada.", listenAfter = true)
            has("fuera de la app", "fuera de la aplicacion", "segundo plano") && enable != null ->
                _listenOutsideRequests.tryEmit(enable)
            has("modo simple") && enable != null -> onSimpleModeChanged(enable)
            has("aviso") && enable != null -> onAnnounceNotificationsChanged(enable)
            has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio", "pantalla anterior") -> _back.tryEmit(Unit)
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak("No entendí. " + CommandCatalog.hint("configuración"), listenAfter = true)
            }
        }
    }

    /** Call from the header button: reads out this screen's commands. */
    fun onCommandsRequested() {
        voiceEngine.speak(CommandCatalog.SETTINGS, listenAfter = true)
    }

    fun refreshStorage() {
        viewModelScope.launch { _storage.value = storageReport.read() }
    }

    fun speakStorage() {
        viewModelScope.launch {
            val usage = storageReport.read()
            _storage.value = usage
            voiceEngine.speak(storageReport.describe(usage), listenAfter = true)
        }
    }

    /** Offers to delete the photos older than a month. Nothing is deleted until the person says yes. */
    fun askClean() {
        viewModelScope.launch {
            val usage = storageReport.read()
            _storage.value = usage
            if (usage.oldPhotoCount == 0) {
                voiceEngine.speak(
                    "No hay fotos de más de ${StorageReport.OLD_PHOTO_DAYS} días que borrar.",
                    listenAfter = true,
                )
            } else {
                _pendingClean.value = true
                voiceEngine.speak(
                    "Puedo borrar ${usage.oldPhotoCount} fotos de más de ${StorageReport.OLD_PHOTO_DAYS} días y liberar " +
                        "${storageReport.format(usage.oldPhotoBytes)}. Solo se borran las fotos; el texto que leí sigue guardado. " +
                        "¿Las borro? Di sí o no.",
                    listenAfter = true,
                )
            }
        }
    }

    fun confirmClean() {
        _pendingClean.value = false
        viewModelScope.launch {
            val (count, bytes) = storageReport.deleteOldPhotos()
            _storage.value = storageReport.read()
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Listo, borré $count fotos y liberé ${storageReport.format(bytes)}.", listenAfter = true)
        }
    }

    fun cancelClean() {
        _pendingClean.value = false
        voiceEngine.speak("De acuerdo, no borré nada.", listenAfter = true)
    }

    fun describeStorage(usage: StorageUsage): String = storageReport.describe(usage)

    fun currentVolumePercent(): Int = volumeControl.percent()

    fun onVolumeChanged(percent: Int) {
        volumeControl.setPercent(percent)
        voiceEngine.speak(volumeControl.levelText())
    }

    fun isNotificationAccessEnabled(): Boolean = notificationAccess.isEnabled()

    fun onOpenNotificationSettings() {
        voiceEngine.speak("Voy a abrir los ajustes. Busca CECAPI en la lista y actívalo. Después regresa a la aplicación.")
        notificationAccess.openSettings()
    }

    fun onAnnounceNotificationsChanged(enabled: Boolean) {
        viewModelScope.launch { deviceSettings.setAnnounceNotifications(enabled) }
        voiceEngine.speak(
            if (enabled) "Te avisaré cuando llegue una notificación." else "Ya no te avisaré cuando llegue una notificación.",
        )
    }

    /** Starts or stops the service that keeps listening for "hola" after the app is closed. */
    fun onListenOutsideAppChanged(enabled: Boolean) {
        viewModelScope.launch {
            deviceSettings.setBackgroundListening(enabled)
            if (enabled) backgroundListening.start() else backgroundListening.stop()
        }
        voiceEngine.speak(
            if (enabled) {
                "Listo. Seguiré escuchando aunque cierres la aplicación. Verás un aviso fijo. Di para, para detenerme."
            } else {
                "Listo. Ya no escucharé cuando la aplicación esté cerrada."
            },
        )
    }

    fun onSimpleModeChanged(enabled: Boolean) {
        viewModelScope.launch { deviceSettings.setSimpleMode(enabled) }
        voiceEngine.speak(if (enabled) "Modo simple activado." else "Modo simple desactivado.")
    }

    fun onLogout() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        sessionRepository.logout()
        _loggedOut.tryEmit(Unit)
    }
}
