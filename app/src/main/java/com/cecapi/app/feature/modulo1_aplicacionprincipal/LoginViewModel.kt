package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.VoiceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class VoiceInputTarget { USERNAME, PASSWORD, NONE }

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _loginSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loginSucceeded: SharedFlow<Unit> = _loginSucceeded

    private val _navigateToRegister = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToRegister: SharedFlow<Unit> = _navigateToRegister

    private var voiceTarget = VoiceInputTarget.USERNAME

    init {
        voiceEngine.speak(
            "Para iniciar sesión en CECAPI, di tu usuario o escríbelo. Luego di tu contraseña. " +
                "Si no tienes cuenta, di crear cuenta.",
        )
        viewModelScope.launch {
            voiceEngine.recognizedSpeech.collect { speech ->
                val texto = speech.text.trim()
                if (voiceTarget == VoiceInputTarget.USERNAME && texto.lowercase().contains("crear cuenta")) {
                    voiceEngine.speak("Vamos a crear tu cuenta.")
                    _navigateToRegister.tryEmit(Unit)
                    return@collect
                }
                when (voiceTarget) {
                    VoiceInputTarget.USERNAME -> {
                        onUsernameChange(texto)
                        voiceTarget = VoiceInputTarget.PASSWORD
                        voiceEngine.speak("Ahora di tu contraseña.")
                    }
                    VoiceInputTarget.PASSWORD -> {
                        onPasswordChange(texto.replace(" ", ""))
                        voiceTarget = VoiceInputTarget.NONE
                        submit()
                    }
                    VoiceInputTarget.NONE -> Unit
                }
            }
        }
    }

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(username = value, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun onMicTapped() {
        voiceEngine.startListening()
    }

    fun submit() {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank()) {
            val message = "Falta el usuario o la contraseña."
            _uiState.value = state.copy(errorMessage = message)
            voiceEngine.speak(message)
            return
        }
        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            when (sessionRepository.login(state.username, state.password)) {
                is LoginResult.Success -> {
                    _uiState.value = state.copy(isSubmitting = false)
                    voiceEngine.speak("Sesión iniciada correctamente. Bienvenido a CECAPI. Di un comando para continuar.")
                    _loginSucceeded.tryEmit(Unit)
                }
                LoginResult.InvalidCredentials -> {
                    val message = "Usuario o contraseña incorrectos. Intenta de nuevo."
                    _uiState.value = state.copy(isSubmitting = false, errorMessage = message)
                    voiceEngine.speak(message)
                    voiceTarget = VoiceInputTarget.USERNAME
                }
            }
        }
    }
}
