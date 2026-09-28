package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.model.ModuloCecapi
import com.cecapi.app.core.ui.MenuItem
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.util.ConnectivityObserver
import com.cecapi.app.core.util.PhoneStatusReader
import com.cecapi.app.core.util.VolumeControl
import com.cecapi.app.core.voice.AssistantPreferences
import com.cecapi.app.core.voice.DeviceSettings
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.IntentFallback
import com.cecapi.app.core.voice.VoiceMessages
import com.cecapi.app.notifications.NotificationReader
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceMemory
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.core.voice.WakeWordController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val permisosModuloDao: PermisosModuloDao,
    private val phoneStatusReader: PhoneStatusReader,
    private val wakeWordController: WakeWordController,
    private val voiceMemory: VoiceMemory,
    private val volumeControl: VolumeControl,
    private val cues: FeedbackCues,
    private val notificationReader: NotificationReader,
    private val intentFallback: IntentFallback,
    private val deviceSettings: DeviceSettings,
    private val connectivityObserver: ConnectivityObserver,
    assistantPreferences: AssistantPreferences,
) : ViewModel() {

    private var holdingWakeWord = false

    val currentUser: StateFlow<UsuarioEntity?> = sessionRepository.currentUser

    val assistantName: StateFlow<String> = assistantPreferences.assistantName.stateIn(
        viewModelScope, SharingStarted.Eagerly, "",
    )

    val isOnline: StateFlow<Boolean> = connectivityObserver.observe().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), true,
    )

    private val _exitEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val exitEvents: SharedFlow<Unit> = _exitEvents

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    val enabledModules: StateFlow<List<ModuloCecapi>> = sessionRepository.currentUser
        .filterNotNull()
        .flatMapLatest { usuario -> permisosModuloDao.observeByUser(usuario.id) }
        .map { permisos ->
            permisos.filter { it.habilitado }
                .mapNotNull { ModuloCecapi.fromStorageCode(it.moduloCodigo) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModuloCecapi.entries.toList())

    /** The main menu for this account: Cámara, Documentos, Personalización and Configuración. */
    val menu: StateFlow<List<MenuItem>> = enabledModules
        .map { ModuleVoice.menu(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModuleVoice.menu(ModuloCecapi.entries))

    private val _navEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<String> = _navEvents

    // This ViewModel outlives its screen while a module is open on top of it, so it must
    // ignore speech that belongs to that module.
    private var screenActive = false

    init {
        viewModelScope.launch {
            // Connection status is only mentioned when there is a problem.
            val online = connectivityObserver.observe().first()
            voiceEngine.speak(welcomeMessage() + if (online) "" else " $OFFLINE_NOTICE")
        }
        viewModelScope.launch {
            var wasOnline: Boolean? = null
            connectivityObserver.observe().collect { online ->
                if (wasOnline == true && !online && screenActive) { cues.play(FeedbackCues.Cue.WARNING); voiceEngine.speak(OFFLINE_NOTICE) }
                wasOnline = online
            }
        }
        viewModelScope.launch {
            voiceEngine.recognizedSpeech.collect { speech ->
                if (screenActive) interpretCommand(speech.text.lowercase())
            }
        }
    }

    /** Handles "repite..." (again the last request, or the last answer); anything else is remembered and run. */
    private enum class PendingConfirm { EXIT, LOGOUT }

    private var pendingConfirm: PendingConfirm? = null

    /** Answers the "¿Cierro...? Di sí o no." question. Anything else is a normal command and cancels it. */
    private fun handlePendingConfirm(text: String): Boolean {
        val action = pendingConfirm ?: return false
        pendingConfirm = null
        return when {
            VoiceText.isNo(text) -> {
                voiceEngine.speak("De acuerdo, no cierro nada.")
                true
            }
            VoiceText.isYes(text) -> {
                if (action == PendingConfirm.EXIT) exitApp() else onLogout()
                true
            }
            else -> false
        }
    }

    private fun interpretCommand(text: String) {
        if (handlePendingConfirm(text)) return
        when (voiceMemory.repeatKind(text)) {
            VoiceMemory.Repeat.RESPONSE ->
                voiceEngine.speak(voiceEngine.lastSpoken ?: "Todavía no he dicho nada.")
            VoiceMemory.Repeat.REQUEST -> {
                val previous = voiceMemory.lastRequest
                if (previous == null) voiceEngine.speak("Todavía no me has pedido nada.") else runCommand(previous)
            }
            null -> {
                voiceMemory.remember(text)
                runCommand(text)
            }
        }
    }

    private fun runCommand(text: String, allowFallback: Boolean = true) {
        // "dime más": go deeper on the last thing the AI answered.
        intentFallback.deepQuestionFor(text)?.let { question ->
            askAi(question, deep = true)
            return
        }
        phoneStatusReader.answer(text)?.let { status ->
            voiceEngine.speak(status)
            return
        }
        volumeControl.handle(text)?.let { message ->
            voiceEngine.speak(message)
            return
        }
        if (CommandCatalog.isRequest(text)) {
            voiceEngine.speak(CommandCatalog.DASHBOARD)
            return
        }
        notificationReader.handle(text)?.let { message ->
            voiceEngine.speak(message)
            return
        }
        // Leaving is hard to undo for someone who cannot see the screen, and a misheard word must not do it.
        if (SessionCommands.isExitApp(text)) {
            pendingConfirm = PendingConfirm.EXIT
            voiceEngine.speak("¿Cierro la aplicación? Di sí o no.", listenAfter = true)
            return
        }
        if (SessionCommands.isLogout(text)) {
            pendingConfirm = PendingConfirm.LOGOUT
            voiceEngine.speak("¿Cierro tu sesión? Di sí o no.", listenAfter = true)
            return
        }
        if (ModuleVoice.isListRequest(text)) {
            voiceEngine.speak(ModuleVoice.spokenList(menu.value))
            return
        }
        ModuleVoice.directCameraRoute(text)?.let { route ->
            openRoute(
                route,
                if (route == CecapiDestinations.ENVIRONMENT) "Abriendo el asistente del entorno." else "Abriendo la cámara para leer texto.",
            )
            return
        }
        val key = ModuleVoice.matchKey(text, menu.value)
        if (key != null) {
            onMenuKey(key)
        } else {
            notUnderstood(text, allowFallback)
        }
    }

    /**
     * Not one of our commands. If a resolver is registered (the AI team's "traductor de frases") and there is
     * internet, it gets one chance to turn the phrase into a command we know; otherwise we say so.
     */
    private fun notUnderstood(text: String, allowFallback: Boolean) {
        if (allowFallback && intentFallback.resolver != null && isOnline.value) {
            askAi(text, deep = false)
        } else {
            sayNotUnderstood()
        }
    }

    /** Asks the AI backend. It answers with a command to run, or a short answer to read aloud. */
    private fun askAi(question: String, deep: Boolean) {
        val resolver = intentFallback.resolver ?: return sayNotUnderstood()
        viewModelScope.launch {
            val reply = runCatching { resolver(question, deep) }.getOrNull()
            val command = reply?.command
            val answer = reply?.answer
            when {
                command != null -> runCommand(command, allowFallback = false)
                answer != null -> {
                    intentFallback.lastQuestion = question
                    voiceEngine.speak(answer + if (reply.hasMore) " Si quieres saber más, di dime más." else "")
                }
                else -> sayNotUnderstood()
            }
        }
    }

    // Never stay silent: the user cannot see whether the phrase was understood.
    private fun sayNotUnderstood() {
        cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
        voiceEngine.speak("No entendí ese comando. Di lista de comandos para escuchar lo que puedo hacer.")
    }

    fun setScreenActive(active: Boolean) {
        screenActive = active
    }

    /** "Hola" (or the assistant's name) opens the mic; needs the mic permission, which the screen checks. */
    fun setWakeWordEnabled(enabled: Boolean) {
        if (enabled == holdingWakeWord) return
        holdingWakeWord = enabled
        if (enabled) wakeWordController.acquire() else wakeWordController.release()
    }

    override fun onCleared() {
        setWakeWordEnabled(false)
        super.onCleared()
    }

    /**
     * Neutral welcome for everyone: name, role (and where they come from, if known), and what is
     * different inside. Wording per role can be customized later without changing this structure.
     */
    private suspend fun welcomeMessage(): String {
        val style = deviceSettings.addressStyle.first()
        val usuario = sessionRepository.currentUser.value
        // The name the person chose to be called wins over the one on the account.
        val nombre = deviceSettings.preferredName.first().ifBlank { usuario?.nombreCompleto.orEmpty() }
        val rol = usuario?.rol ?: RolUsuario.USUARIO.codigo
        val origen = usuario?.origen.orEmpty()
        val identidad = buildString {
            append(style.pick("Tu rol es $rol", "Su rol es $rol"))
            if (origen.isNotBlank() && rol != RolUsuario.ADMINISTRADOR.codigo) append(", de $origen")
            append(".")
        }
        return "Bienvenido $nombre. $identidad " +
            style.pick("Aquí tienes tu menú. Di menú para escucharlo.", "Aquí tiene su menú. Diga menú para escucharlo.")
    }

    fun onMicTapped() {
        voiceEngine.startListening()
    }

    /** The user refused the mic permission: say why nothing will happen instead of staying silent. */
    fun onMicPermissionDenied() {
        voiceEngine.speak(VoiceMessages.MIC_DENIED)
    }

    /** Same as saying [command] out loud; used by the on-screen suggestion chips. */
    fun onSuggestionTapped(command: String) {
        interpretCommand(command.lowercase())
    }

    fun onModuleSelected(modulo: ModuloCecapi) {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak(modulo.voicePrompt)
        _navEvents.tryEmit(modulo.route)
    }

    fun onSettingsSelected() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak("Abriendo la configuración.")
        _navEvents.tryEmit(CecapiDestinations.SETTINGS)
    }

    /** A card of the main menu was tapped (or named out loud). */
    fun onMenuItemSelected(item: MenuItem) = onMenuKey(item.key)

    private fun onMenuKey(key: String) {
        when (key) {
            MenuItem.SETTINGS_KEY -> onSettingsSelected()
            MenuItem.CAMERA_KEY -> openRoute(CecapiDestinations.CAMERA_HUB, "Abriendo la cámara.")
            MenuItem.PERSONALIZATION_KEY -> openRoute(CecapiDestinations.PERSONALIZATION, "Abriendo la personalización.")
            MenuItem.CHATS_KEY -> openRoute(CecapiDestinations.CHATS, "Abriendo los chats.")
            else -> ModuloCecapi.fromStorageCode(key)?.let(::onModuleSelected)
        }
    }

    private fun openRoute(route: String, speech: String) {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        voiceEngine.speak(speech)
        _navEvents.tryEmit(route)
    }

    /** No speech here: the home screen opens next and says "Sesión cerrada" itself. */
    fun onLogout() {
        cues.play(FeedbackCues.Cue.NAVIGATE)
        sessionRepository.logout()
    }

    /** Says goodbye, waits for it to finish, then closes the app so the phone is free again. */
    private fun exitApp() {
        voiceEngine.speak("Cerrando la aplicación. Hasta luego.")
        viewModelScope.launch {
            delay(300)
            withTimeoutOrNull(6_000) { voiceEngine.state.first { it !is VoiceState.Speaking } }
            _exitEvents.emit(Unit)
        }
    }

    /** Called by the screen's own "Cerrar aplicación" chip. */
    fun onExitRequested() = exitApp()

    private companion object {
        const val OFFLINE_NOTICE =
            "Ahora no tienes conexión a internet, así que mis respuestas pueden ser menos precisas."
    }
}
