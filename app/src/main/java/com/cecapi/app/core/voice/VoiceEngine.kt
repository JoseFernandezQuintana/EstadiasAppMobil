package com.cecapi.app.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single shared wrapper around Android's [SpeechRecognizer] and [TextToSpeech].
 *
 * Every module (voice assistant, OCR, requests, learning, environment...) reads
 * [state] and calls [speak]/[startListening] instead of creating its own engine
 * instance, because only one SpeechRecognizer/TextToSpeech session is reliable
 * per process. This is also what lets the app "narrate" every screen transition
 * automatically, which is the core accessibility requirement for CECAPI users.
 */
@Singleton
class VoiceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _recognizedSpeech = MutableSharedFlow<RecognizedSpeech>(extraBufferCapacity = 4)
    val recognizedSpeech: SharedFlow<RecognizedSpeech> = _recognizedSpeech.asSharedFlow()

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private val pendingSpeech = ArrayDeque<String>()

    // Backed by Módulo 1's `configuracion_usuario` table (see SessionRepository.aplicarConfiguracion).
    private var currentRate = 1.0f
    private var currentVolume = 1.0f

    private var speechRecognizer: SpeechRecognizer? = null

    init {
        textToSpeech = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                textToSpeech?.language = Locale("es", "MX")
                textToSpeech?.setSpeechRate(currentRate)
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // state already set to Speaking by speak()
                    }

                    override fun onDone(utteranceId: String?) {
                        if (_state.value is VoiceState.Speaking) {
                            _state.value = VoiceState.Idle
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.value = VoiceState.Idle
                    }
                })
                while (pendingSpeech.isNotEmpty()) {
                    speakInternal(pendingSpeech.removeFirst())
                }
            }
        }
    }

    /** Reads [text] aloud. Interrupts whatever is currently being spoken. */
    fun speak(text: String) {
        if (!ttsReady) {
            pendingSpeech.addLast(text)
            return
        }
        speakInternal(text)
    }

    private fun speakInternal(text: String) {
        _state.value = VoiceState.Speaking(text)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, currentVolume) }
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, UUID.randomUUID().toString())
    }

    /**
     * Applies the user's saved preferences from Módulo 1 (`configuracion_usuario`).
     * [velocidadVoz] is a TTS rate multiplier (0.5x–2.0x); [volumen] is 0.0–1.0.
     */
    fun applyVoiceSettings(velocidadVoz: Float, volumen: Float) {
        currentRate = velocidadVoz.coerceIn(0.5f, 2.0f)
        currentVolume = volumen.coerceIn(0.0f, 1.0f)
        if (ttsReady) {
            textToSpeech?.setSpeechRate(currentRate)
        }
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        if (_state.value is VoiceState.Speaking) {
            _state.value = VoiceState.Idle
        }
    }

    /**
     * Starts listening for a single utterance. Caller must have already
     * requested RECORD_AUDIO at the Activity/Compose layer; this class does
     * not handle permission prompts since that is UI-scoped, not engine-scoped.
     */
    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = VoiceState.Error("El reconocimiento de voz no está disponible en este dispositivo.")
            return
        }
        stopListening()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = VoiceState.Listening
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                _state.value = VoiceState.Idle
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                _state.value = VoiceState.Idle
                if (!text.isNullOrBlank()) {
                    _recognizedSpeech.tryEmit(RecognizedSpeech(text, System.currentTimeMillis()))
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        if (_state.value is VoiceState.Listening) {
            _state.value = VoiceState.Idle
        }
    }

    fun release() {
        stopListening()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
