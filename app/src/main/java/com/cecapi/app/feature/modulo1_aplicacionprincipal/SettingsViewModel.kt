package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.VoiceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val configuracionUsuarioDao: ConfiguracionUsuarioDao,
) : ViewModel() {

    private val usuarioId: Long?
        get() = sessionRepository.currentUser.value?.id

    val configuracion: StateFlow<ConfiguracionUsuarioEntity> = sessionRepository.currentUser
        .filterNotNull()
        .flatMapLatest { usuario -> configuracionUsuarioDao.observeByUser(usuario.id) }
        .filterNotNull()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ConfiguracionUsuarioEntity(usuarioId = 0),
        )

    init {
        voiceEngine.speak("Configuración de voz. Ajusta la velocidad, el volumen o el modo simple.")
    }

    fun onVelocidadChange(velocidad: Float) {
        guardar(configuracion.value.copy(velocidadVoz = velocidad))
    }

    fun onVolumenChange(volumen: Float) {
        guardar(configuracion.value.copy(volumen = volumen))
    }

    fun onModoSimpleChange(activo: Boolean) {
        guardar(configuracion.value.copy(modoSimple = activo))
    }

    fun onProbarVoz() {
        voiceEngine.speak("Así suena tu asistente con esta configuración.")
    }

    private fun guardar(nuevaConfiguracion: ConfiguracionUsuarioEntity) {
        val id = usuarioId ?: return
        val conId = nuevaConfiguracion.copy(usuarioId = id)
        voiceEngine.applyVoiceSettings(conId.velocidadVoz, conId.volumen)
        viewModelScope.launch {
            configuracionUsuarioDao.upsert(conId)
        }
    }
}
