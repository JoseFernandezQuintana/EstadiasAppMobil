package com.cecapi.app.core.voice

/**
 * Global state of the shared voice engine. Every screen observes this instead of
 * managing its own SpeechRecognizer/TextToSpeech instance, because Android only
 * allows meaningful use of one recognizer/TTS engine per process at a time.
 */
sealed interface VoiceState {
    data object Idle : VoiceState
    data object Listening : VoiceState
    data class Speaking(val text: String) : VoiceState
    data class Error(val message: String) : VoiceState
}

/** A finished, high-confidence speech-to-text result ready to be interpreted as a command. */
data class RecognizedSpeech(val text: String, val timestampMillis: Long)
