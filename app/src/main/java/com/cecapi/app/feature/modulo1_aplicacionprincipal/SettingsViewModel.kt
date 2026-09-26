package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.util.VolumeControl
import com.cecapi.app.core.voice.DeviceSettings
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.notifications.NotificationAccess
import com.cecapi.app.service.BackgroundListening
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    init {
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
        fun has(vararg words: String) = words.any { it in text }
        val enable = when {
            listOf("desactiv", "apaga", "quita", "sin ", "no quiero").any { it in text } -> false
            listOf("activ", "enciende", "prende", "pon ", "quiero").any { it in text } -> true
            else -> null
        }
        when {
            CommandCatalog.isRequest(spoken) -> voiceEngine.speak(CommandCatalog.SETTINGS, listenAfter = true)
            has("cerrar sesion", "cierra sesion", "cierra mi sesion", "salir de la cuenta") ->
                if (currentUser.value != null) onLogout() else voiceEngine.speak("No hay una sesión iniciada.", listenAfter = true)
            has("fuera de la app", "fuera de la aplicacion", "segundo plano") && enable != null ->
                _listenOutsideRequests.tryEmit(enable)
            has("modo simple") && enable != null -> onSimpleModeChanged(enable)
            has("aviso") && enable != null -> onAnnounceNotificationsChanged(enable)
            has("atras", "volver", "salir", "menu", "regresa") -> _back.tryEmit(Unit)
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
