package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.model.ModuloCecapi
import com.cecapi.app.core.navigation.CecapiDestinations
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val permisosModuloDao: PermisosModuloDao,
) : ViewModel() {

    val currentUser: StateFlow<UsuarioEntity?> = sessionRepository.currentUser

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    val enabledModules: StateFlow<List<ModuloCecapi>> = sessionRepository.currentUser
        .filterNotNull()
        .flatMapLatest { usuario -> permisosModuloDao.observeByUser(usuario.id) }
        .map { permisos ->
            permisos.filter { it.habilitado }.mapNotNull { ModuloCecapi.fromStorageCode(it.moduloCodigo) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModuloCecapi.entries.toList())

    private val _navEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<String> = _navEvents

    init {
        voiceEngine.speak("Módulos disponibles. Toca una tarjeta o di su nombre.")
        viewModelScope.launch {
            voiceEngine.recognizedSpeech.collect { speech ->
                val text = speech.text.lowercase()
                if (text.contains("configuración") || text.contains("configuracion") || text.contains("ajustes")) {
                    voiceEngine.speak("Abriendo configuración de voz.")
                    _navEvents.tryEmit(CecapiDestinations.SETTINGS)
                    return@collect
                }
                val match = enabledModules.value.firstOrNull { modulo ->
                    text.contains(modulo.title.lowercase()) || text.contains(modulo.subtitle.lowercase())
                }
                if (match != null) onModuleSelected(match)
            }
        }
    }

    fun onMicTapped() {
        voiceEngine.startListening()
    }

    fun onModuleSelected(modulo: ModuloCecapi) {
        voiceEngine.speak(modulo.voicePrompt)
        _navEvents.tryEmit(modulo.route)
    }

    fun onLogout() {
        voiceEngine.speak("Cerrando sesión.")
        sessionRepository.logout()
    }
}
