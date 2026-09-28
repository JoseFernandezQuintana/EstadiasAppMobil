package com.cecapi.app.feature.modulo6_aprendizaje

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.feature.modulo1_aplicacionprincipal.CommandCatalog
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

enum class ActivityMode { AUDIO, VIBRATION }

/** One exercise, whether it is a sound or a vibration pattern. */
data class ActivityItem(
    val id: Long,
    val title: String,
    val instruction: String,
    val answer: String,
    val sound: EjercicioEntity? = null,
    val vibration: EjercicioVibracionEntity? = null,
)

data class ActivitiesUiState(
    val mode: ActivityMode = ActivityMode.AUDIO,
    val level: Int = 1,
    val items: List<ActivityItem> = emptyList(),
    val index: Int = 0,
    val feedback: String? = null,
    val correct: Boolean? = null,
    /** The instruction, the sound or the vibration is playing: answers are not taken yet. */
    val busy: Boolean = false,
) {
    val current: ActivityItem? get() = items.getOrNull(index)
}

/**
 * Actividades: ear training. A sound (or a vibration pattern) plays and the person says where it came from
 * or what it felt like. Answers are checked, saved for the signed-in user, and the level goes up with points.
 * It also works without signing in; results are simply not saved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LearningViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val cues: FeedbackCues,
    private val sessionRepository: SessionRepository,
    private val repository: LearningRepository,
    private val vibrationRepository: VibrationRepository,
    private val audioPlayer: AudioSpatialPlayer,
    private val vibrationEngine: VibrationEngine,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _state = MutableStateFlow(ActivitiesUiState())
    val state: StateFlow<ActivitiesUiState> = _state.asStateFlow()

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    private val mode = MutableStateFlow(ActivityMode.AUDIO)
    private val level = MutableStateFlow(1)
    private var exerciseJob: Job? = null

    // The highest level the person has reached (saved progress), to announce when it goes up.
    private var savedLevel = 1

    private val itemsFlow: Flow<List<ActivityItem>> = combine(mode, level) { m, l -> m to l }
        .flatMapLatest { (m, l) ->
            if (m == ActivityMode.AUDIO) {
                repository.observeEjerciciosPorNivel(l).map { list ->
                    list.map { ActivityItem(it.id, it.titulo, it.instruccion, it.respuestaCorrecta, sound = it) }
                }
            } else {
                vibrationRepository.observeEjerciciosDelNivel(1).map { list ->
                    list.map { ActivityItem(it.id, it.titulo, it.instruccion, it.respuestaCorrecta, vibration = it) }
                }
            }
        }

    init {
        viewModelScope.launch {
            repository.seedEjerciciosSiVacio()
            vibrationRepository.seedEjerciciosSiVacio()
            sessionRepository.currentUser.value?.let { user ->
                savedLevel = repository.observeNivel(user.id).first().nivelActual
                level.value = savedLevel
            }
            _state.value = _state.value.copy(level = level.value)
            voiceEngine.speak(
                "Actividades para entrenar el oído. Escucharás un sonido y dirás de dónde viene. " +
                    "Di vibración para practicar con vibraciones. " + CommandCatalog.hint("actividades"),
            )
            itemsFlow.collect { list -> onItemsLoaded(list) }
        }
        viewModelScope.launch {
            voiceEngine.recognizedSpeech.collect { speech -> onSpeech(speech.text) }
        }
    }

    private suspend fun onItemsLoaded(list: List<ActivityItem>) {
        val previous = _state.value
        if (list.map { it.id } == previous.items.map { it.id } && previous.mode == mode.value) return
        _state.value = previous.copy(
            mode = mode.value,
            level = level.value,
            items = list,
            index = 0,
            feedback = null,
            correct = null,
        )
        if (list.isEmpty()) return
        // Let the welcome finish before the first exercise.
        speakAndWait("")
        startExercise(intro = "${where()} ")
    }

    private fun where(): String {
        val s = _state.value
        val place = if (s.mode == ActivityMode.AUDIO) "Nivel ${s.level}" else "Vibración"
        return "$place, ejercicio ${s.index + 1} de ${s.items.size}."
    }

    /** The commands this screen answers. "Atrás" is not one: it is also an answer in level 3. */
    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        val chosenLevel = LEVEL_COMMAND.find(text)?.groupValues?.let { it[1].ifEmpty { it[2] } }
        when {
            CommandCatalog.isRequest(spoken) -> {
                stopStimulus()
                voiceEngine.speak(CommandCatalog.ACTIVITIES, listenAfter = true)
            }
            has("vibracion", "vibraciones", "vibrar", "vibra", "vibrador", "con vibracion") -> setMode(ActivityMode.VIBRATION)
            has("sonidos", "sonido", "audio", "audios", "escuchar sonidos", "oido", "oidos") -> setMode(ActivityMode.AUDIO)
            chosenLevel != null -> setLevel(levelNumber(chosenLevel))
            has("repite", "repetir", "repiteme", "otra vez", "de nuevo", "escuchalo", "ponlo otra vez", "vuelve a poner", "vuelve a sonar", "no alcance a oir", "no escuche") -> repeat()
            has("siguiente", "proximo", "continua", "continuar", "sigue", "el que sigue", "otro ejercicio", "otro", "adelante", "pasa al siguiente", "pasa") -> next()
            has("volver", "vuelve", "salir", "menu", "regresar", "regresa", "terminar", "termina", "ya no quiero", "inicio") -> {
                stopStimulus()
                _back.tryEmit(Unit)
            }
            _state.value.busy -> Unit
            else -> answer(spoken)
        }
    }

    private fun levelNumber(word: String): Int = when (word) {
        "uno", "1", "primer" -> 1
        "dos", "2", "segundo" -> 2
        else -> 3
    }

    fun setMode(newMode: ActivityMode) {
        if (newMode == mode.value) return
        stopStimulus()
        mode.value = newMode
    }

    fun setLevel(newLevel: Int) {
        if (mode.value != ActivityMode.AUDIO) mode.value = ActivityMode.AUDIO
        if (newLevel == level.value) return
        stopStimulus()
        level.value = newLevel
    }

    fun onMicTapped() {
        stopStimulus()
        voiceEngine.startListening()
    }

    fun onCommandsRequested() {
        stopStimulus()
        voiceEngine.speak(CommandCatalog.ACTIVITIES, listenAfter = true)
    }

    /** Plays the sound or the vibration again, without repeating the instruction. */
    fun repeat() {
        if (_state.value.current == null) return
        startExercise(intro = "")
    }

    fun next() {
        val s = _state.value
        if (s.items.isEmpty()) return
        _state.value = s.copy(index = (s.index + 1) % s.items.size, feedback = null, correct = null)
        startExercise(intro = "${where()} ")
    }

    private fun startExercise(intro: String) {
        val item = _state.value.current ?: return
        stopStimulus()
        exerciseJob = viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val talk = (intro + if (intro.isBlank()) "" else item.instruction).trim()
            if (talk.isNotEmpty()) speakAndWait(talk)
            delay(300)
            item.sound?.let { audioPlayer.reproducir(it) }
            item.vibration?.let { vibrationEngine.reproducirPatron(it.patronMs.aPatronMs(), it.intensidad) }
            delay(500)
            _state.value = _state.value.copy(busy = false)
            voiceEngine.startListening()
        }
    }

    private fun stopStimulus() {
        exerciseJob?.cancel()
        audioPlayer.detener()
        vibrationEngine.detener()
        _state.value = _state.value.copy(busy = false)
    }

    private suspend fun speakAndWait(text: String) {
        if (text.isNotBlank()) voiceEngine.speak(text)
        delay(250)
        withTimeoutOrNull(20_000) { voiceEngine.state.first { it !is VoiceState.Speaking } }
    }

    private fun answer(spoken: String) {
        val item = _state.value.current ?: return
        val correct = matches(spoken, item.answer)
        val message = (if (correct) "Correcto" else "Incorrecto") + ", la respuesta era ${spokenAnswer(item.answer)}."
        cues.play(if (correct) FeedbackCues.Cue.SUCCESS else FeedbackCues.Cue.ERROR)
        _state.value = _state.value.copy(feedback = message, correct = correct)
        viewModelScope.launch {
            val user = sessionRepository.currentUser.value
            var levelUp = ""
            if (user != null) {
                if (item.sound != null) {
                    repository.registrarResultado(user.id, item.id, correct)
                    val nowLevel = repository.observeNivel(user.id).first().nivelActual
                    if (nowLevel > savedLevel) {
                        savedLevel = nowLevel
                        levelUp = " Subiste al nivel $nowLevel."
                    }
                } else {
                    vibrationRepository.registrarResultado(user.id, item.id, correct)
                }
            }
            voiceEngine.speak(
                "$message$levelUp Di siguiente para continuar, o repite para escucharlo otra vez.",
                listenAfter = true,
            )
        }
    }

    /** How the answer is said aloud: the code stores "centro", the recordings say "ambos lados". */
    private fun spokenAnswer(answer: String): String = if (VoiceText.normalize(answer) == "centro") "ambos lados" else answer

    /**
     * Every answer accepts the several ways people say it, and small recognizer slips. "Centro" and "ambos
     * lados" are the same answer. Movement ("de izquierda a derecha") is judged by the order of the two sides,
     * or by where the sound went ("hacia la derecha").
     */
    private fun matches(spoken: String, expected: String): Boolean {
        val text = VoiceText.normalize(spoken)
        val want = VoiceText.normalize(expected)
        fun said(vararg words: String) = VoiceText.hasAny(text, *words)
        val left = text.indexOf("izquierd")
        val right = text.indexOf("derech")
        return when (want) {
            "de izquierda a derecha" -> when {
                left >= 0 && right >= 0 -> left < right
                else -> said("hacia la derecha", "a la derecha", "hacia el lado derecho", "se fue a la derecha", "va a la derecha")
            }
            "de derecha a izquierda" -> when {
                left >= 0 && right >= 0 -> right < left
                else -> said("hacia la izquierda", "a la izquierda", "hacia el lado izquierdo", "se fue a la izquierda", "va a la izquierda")
            }
            "izquierda" -> said("izquierda", "izquierdo", "lado izquierdo", "oido izquierdo") && !said("derecha", "derecho")
            "derecha" -> said("derecha", "derecho", "lado derecho", "oido derecho") && !said("izquierda", "izquierdo")
            "centro" -> said(
                "centro", "ambos", "los dos", "medio", "en medio", "al centro", "de los dos lados", "por los dos lados",
                "los dos lados", "ambos lados", "ambos oidos", "los dos oidos", "de ambos lados", "parejo",
            )
            "cerca" -> said("cerca", "cercano", "cerquita", "pegado", "muy cerca", "fuerte", "de cerca")
            "lejos" -> said("lejos", "lejano", "distante", "alejado", "a lo lejos", "muy lejos", "bajito", "de lejos", "debil")
            "enfrente" -> said("enfrente", "frente", "adelante", "al frente", "de frente", "delante", "por delante")
            "atras" -> said("atras", "detras", "espalda", "de atras", "por atras", "por detras", "a mi espalda", "atrasito")
            "corto" -> said("corto", "cortos", "cortito", "breve", "breves", "rapido", "rapidos") && !said("largo", "largos")
            "largo" -> said("largo", "largos", "prolongado", "extenso", "lento", "sostenido") && !said("corto", "cortos", "breve")
            "mixto" -> said("mixto", "mezcla", "mezclado", "combinado", "alternado", "variado", "corto y largo", "largo y corto") ||
                (said("corto", "cortos", "breve") && said("largo", "largos"))
            else -> want in text
        }
    }

    override fun onCleared() {
        exerciseJob?.cancel()
        audioPlayer.detener()
        vibrationEngine.detener()
        super.onCleared()
    }

    private companion object {
        val LEVEL_COMMAND = Regex("(?:nivel (uno|dos|tres|1|2|3)|(primer|segundo|tercer) nivel)")
    }
}
