package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
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

private enum class RegisterVoiceTarget { NOMBRE_COMPLETO, USUARIO, CONTRASENA, NONE }

data class RegisterUiState(
    val nombreCompleto: String = "",
    val nombreUsuario: String = "",
    val contrasena: String = "",
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _registerSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val registerSucceeded: SharedFlow<Unit> = _registerSucceeded

    private var voiceTarget = RegisterVoiceTarget.NOMBRE_COMPLETO

    init {
        voiceEngine.speak("Vamos a crear tu cuenta en CECAPI. Di tu nombre completo, o escríbelo abajo.")
        viewModelScope.launch {
            voiceEngine.recognizedSpeech.collect { speech -> onVoiceInput(speech.text.trim()) }
        }
    }

    private fun onVoiceInput(texto: String) {
        when (voiceTarget) {
            RegisterVoiceTarget.NOMBRE_COMPLETO -> {
                onNombreCompletoChange(texto)
                voiceTarget = RegisterVoiceTarget.USUARIO
                voiceEngine.speak("Ahora di el nombre de usuario que quieres usar.")
            }
            RegisterVoiceTarget.USUARIO -> {
                onNombreUsuarioChange(texto.replace(" ", "").lowercase())
                voiceTarget = RegisterVoiceTarget.CONTRASENA
                voiceEngine.speak("Ahora di tu contraseña.")
            }
            RegisterVoiceTarget.CONTRASENA -> {
                onContrasenaChange(texto.replace(" ", ""))
                voiceTarget = RegisterVoiceTarget.NONE
                submit()
            }
            RegisterVoiceTarget.NONE -> Unit
        }
    }

    fun onMicTapped() {
        voiceEngine.startListening()
    }

    fun onNombreCompletoChange(value: String) {
        _uiState.value = _uiState.value.copy(nombreCompleto = value, errorMessage = null)
    }

    fun onNombreUsuarioChange(value: String) {
        _uiState.value = _uiState.value.copy(nombreUsuario = value, errorMessage = null)
    }

    fun onContrasenaChange(value: String) {
        _uiState.value = _uiState.value.copy(contrasena = value, errorMessage = null)
    }

    fun submit() {
        val state = _uiState.value
        if (state.nombreCompleto.isBlank() || state.nombreUsuario.isBlank() || state.contrasena.isBlank()) {
            val message = "Falta llenar tu nombre, usuario o contraseña."
            _uiState.value = state.copy(errorMessage = message)
            voiceEngine.speak(message)
            return
        }
        if (state.contrasena.length < 4) {
            val message = "La contraseña debe tener al menos cuatro caracteres."
            _uiState.value = state.copy(errorMessage = message)
            voiceEngine.speak(message)
            return
        }

        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            when (
                val result = sessionRepository.register(
                    nombreUsuario = state.nombreUsuario,
                    contrasena = state.contrasena,
                    nombreCompleto = state.nombreCompleto,
                )
            ) {
                is RegisterResult.Success -> {
                    _uiState.value = state.copy(isSubmitting = false)
                    voiceEngine.speak("Cuenta creada. Bienvenido a CECAPI, ${result.usuario.nombreCompleto}.")
                    _registerSucceeded.tryEmit(Unit)
                }
                RegisterResult.UsernameTaken -> {
                    val message = "Ese nombre de usuario ya existe. Di o escribe otro."
                    _uiState.value = state.copy(isSubmitting = false, errorMessage = message, nombreUsuario = "")
                    voiceEngine.speak(message)
                    voiceTarget = RegisterVoiceTarget.USUARIO
                }
            }
        }
    }
}
