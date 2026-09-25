package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.AddressStyle
import com.cecapi.app.core.voice.AssistantPreferences
import com.cecapi.app.core.voice.DeviceSettings
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Personalización: how the assistant sounds (voice, speed, pitch), how it addresses the person
 * (tú or usted, the name to greet, the assistant's own name) and how sounds and vibration feel.
 * Everything is stored on the phone, so it works before anyone signs in.
 */
@HiltViewModel
class PersonalizationViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val deviceSettings: DeviceSettings,
    private val assistantPreferences: AssistantPreferences,
    private val cues: FeedbackCues,
) : ViewModel() {

    val speechRate: StateFlow<Float> = deviceSettings.speechRate.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)
    val pitch: StateFlow<Float> = deviceSettings.pitch.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)
    val voiceName: StateFlow<String> = deviceSettings.voiceName.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val addressStyle: StateFlow<AddressStyle> = deviceSettings.addressStyle.stateIn(viewModelScope, SharingStarted.Eagerly, AddressStyle.TU)
    val preferredName: StateFlow<String> = deviceSettings.preferredName.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val assistantName: StateFlow<String> = assistantPreferences.assistantName.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val soundCues: StateFlow<Boolean> = deviceSettings.soundCues.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val vibrationCues: StateFlow<Boolean> = deviceSettings.vibrationCues.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val vibrationLevel: StateFlow<Int> = deviceSettings.vibrationLevel.stateIn(viewModelScope, SharingStarted.Eagerly, 2)

    init {
        voiceEngine.speak(
            "Personalización. Aquí eliges mi voz, cómo te llamo, cómo me llamas y cómo se sienten los avisos. " +
                "Mantén presionado cualquier control para que te explique para qué sirve.",
        )
    }

    /** The phone's Spanish voices; empty until the speech engine has started, so the screen asks again. */
    fun voices(): List<VoiceOption> = voiceEngine.spanishVoices()

    fun onVoiceChosen(name: String) {
        voiceEngine.applyVoiceProfile(name, pitch.value)
        viewModelScope.launch { deviceSettings.setVoiceName(name) }
        voiceEngine.speak("Así sueno con esta voz.")
    }

    fun onSpeechRateChanged(rate: Float) {
        voiceEngine.applyVoiceSettings(rate, 1.0f) // apply first so the confirmation already uses the new speed
        viewModelScope.launch { deviceSettings.setSpeechRate(rate) }
        voiceEngine.speak(
            when {
                rate < 0.9f -> "Velocidad lenta."
                rate > 1.1f -> "Velocidad rápida."
                else -> "Velocidad normal."
            },
        )
    }

    fun onPitchChanged(value: Float) {
        voiceEngine.applyVoiceProfile(voiceName.value, value)
        viewModelScope.launch { deviceSettings.setPitch(value) }
        voiceEngine.speak(
            when {
                value < 0.9f -> "Tono grave."
                value > 1.1f -> "Tono agudo."
                else -> "Tono normal."
            },
        )
    }

    fun onTestVoice() {
        voiceEngine.speak("Así suena tu asistente con esta configuración.")
    }

    fun onAddressStyleChosen(style: AddressStyle) {
        voiceEngine.addressStyle = style // so the very next sentence already uses it
        viewModelScope.launch { deviceSettings.setAddressStyle(style) }
        voiceEngine.speak(style.pick("De acuerdo, te hablaré de tú.", "De acuerdo, le hablaré de usted."))
    }

    fun onPreferredNameSaved(name: String) {
        val clean = name.trim()
        viewModelScope.launch { deviceSettings.setPreferredName(clean) }
        voiceEngine.speak(
            if (clean.isEmpty()) {
                "Listo, te saludaré con el nombre de tu cuenta."
            } else {
                "Listo, te voy a llamar $clean."
            },
        )
    }

    fun onAssistantNameSaved(name: String) {
        val clean = name.trim()
        viewModelScope.launch { assistantPreferences.setAssistantName(clean) }
        voiceEngine.speak(
            if (clean.isEmpty()) {
                "Listo, ya no tengo nombre. Di hola para hablar conmigo."
            } else {
                "Listo, ahora me llamo $clean. Di hola o $clean para hablar conmigo."
            },
        )
    }

    fun onSoundCuesChanged(enabled: Boolean) {
        viewModelScope.launch { deviceSettings.setSoundCues(enabled) }
        voiceEngine.speak(if (enabled) "Sonidos activados." else "Sonidos desactivados.")
    }

    fun onVibrationCuesChanged(enabled: Boolean) {
        cues.vibrationEnabled = enabled
        viewModelScope.launch { deviceSettings.setVibrationCues(enabled) }
        if (enabled) cues.play(FeedbackCues.Cue.TAP)
        voiceEngine.speak(if (enabled) "Vibraciones activadas." else "Vibraciones desactivadas.")
    }

    fun onVibrationLevelChanged(level: Int) {
        cues.vibrationLevel = level // felt right away
        viewModelScope.launch { deviceSettings.setVibrationLevel(level) }
        cues.play(FeedbackCues.Cue.SUCCESS)
        voiceEngine.speak(
            when (level) {
                1 -> "Vibración suave."
                3 -> "Vibración fuerte."
                else -> "Vibración normal."
            },
        )
    }

    fun onTestCue(cue: FeedbackCues.Cue, description: String) {
        cues.play(cue)
        voiceEngine.speak(description)
    }
}
