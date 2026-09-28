package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.NoticeBanner
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.SuggestionChip
import com.cecapi.app.core.ui.TopAction
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceMessages
import com.cecapi.app.core.voice.VoiceText
import com.cecapi.app.feature.modulo3_asistenteinteligente.ConsultaIaDao
import com.cecapi.app.feature.modulo3_asistenteinteligente.RespuestaIaDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

/** One saved conversation, shown newest first. */
data class ChatItem(val id: Long, val pregunta: String, val respuesta: String?, val fecha: Long)

/** What the person asked to delete and has not confirmed yet. */
enum class PendingDelete { NONE, ONE, ALL }

data class ChatsUiState(
    val current: Int = 0,
    val pending: PendingDelete = PendingDelete.NONE,
)

/**
 * Chats: the conversations the person had with the assistant, listed newest first, read aloud one at a time
 * and deleted by voice or touch. It reads the tables that exist today (a question and its answer);
 * when the chat structure in the database is redefined, only [chats] and the delete calls change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val cues: FeedbackCues,
    private val sessionRepository: SessionRepository,
    private val consultaDao: ConsultaIaDao,
    private val respuestaDao: RespuestaIaDao,
) : ViewModel() {

    private val chatsFlow: Flow<List<ChatItem>> = sessionRepository.currentUser
        .filterNotNull()
        .flatMapLatest { user ->
            consultaDao.observeRecent(user.id).map { consultas ->
                consultas.map { consulta ->
                    ChatItem(
                        id = consulta.id,
                        pregunta = consulta.textoPregunta,
                        respuesta = respuestaDao.findByConsulta(consulta.id)?.textoRespuesta,
                        fecha = consulta.fechaHora,
                    )
                }
            }
        }

    val chats: StateFlow<List<ChatItem>> = chatsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _state = MutableStateFlow(ChatsUiState())
    val state: StateFlow<ChatsUiState> = _state.asStateFlow()

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    init {
        viewModelScope.launch {
            voiceEngine.recognizedSpeech.collect { speech -> onSpeech(speech.text) }
        }
        if (sessionRepository.currentUser.value == null) {
            voiceEngine.speak(VoiceMessages.NEEDS_LOGIN)
        } else {
            viewModelScope.launch {
                // The list loads a moment after the screen opens; wait for its first real answer before counting.
                val total = chatsFlow.first().size
                voiceEngine.speak(
                    "Chats. " + countText(total) + " " + CommandCatalog.hint("chats"),
                    listenAfter = true,
                )
            }
        }
    }

    private fun countText(total: Int): String = when (total) {
        0 -> "Todavía no hay chats guardados. Cuando hables con la inteligencia artificial, aparecerán aquí."
        1 -> "Tienes un chat guardado."
        else -> "Tienes $total chats guardados. Di lee el último, para escuchar el más reciente."
    }

    /** The commands this screen answers, most specific first. */
    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        // "si" and "no" must be whole words: "siguiente" contains "si".
        val spokenWords = text.split(" ")
        fun hasWord(vararg words: String) = words.any { it in spokenWords }
        val pending = _state.value.pending
        when {
            CommandCatalog.isRequest(spoken) -> onCommandsRequested()
            pending != PendingDelete.NONE && hasWord("si", "claro", "dale", "ok", "okey", "supuesto", "afirmativo", "correcto", "seguro", "hazlo", "confirmo", "confirmar", "borralo", "borralos", "adelante") -> confirmDelete()
            pending != PendingDelete.NONE && hasWord("no", "nunca", "negativo", "olvidalo", "dejalo", "cancela", "cancelar") -> cancelDelete()
            has("borra todos", "borrar todos", "elimina todos", "eliminar todos", "vacia") -> askDeleteAll()
            has("borra este", "borrar este", "elimina este", "eliminar este", "borra el chat", "borra el actual") -> askDeleteCurrent()
            has("cuantos") -> speakCount()
            has("el ultimo", "la ultima", "lee el ultimo", "mas reciente", "abre el ultimo") -> readAt(0)
            has("siguiente", "mas antiguo") -> readAt(_state.value.current + 1)
            has("anterior", "mas nuevo") -> readAt(_state.value.current - 1)
            has("repite", "otra vez", "de nuevo", "lee") -> readAt(_state.value.current)
            has("nuevo chat", "nueva conversacion", "empezar un chat") -> {
                voiceEngine.speak("Para un chat nuevo, vuelve al menú y háblame. Cada conversación se guarda aquí sola.")
                _back.tryEmit(Unit)
            }
            has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio", "pantalla anterior") -> _back.tryEmit(Unit)
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak("No entendí. " + CommandCatalog.hint("chats"), listenAfter = true)
            }
        }
    }

    fun onCommandsRequested() {
        voiceEngine.speak(CommandCatalog.CHATS, listenAfter = true)
    }

    fun speakCount() {
        voiceEngine.speak(countText(chats.value.size), listenAfter = chats.value.isNotEmpty())
    }

    /** Reads chat number [index] (0 is the newest), keeping the position inside the list. */
    fun readAt(index: Int) {
        val list = chats.value
        if (list.isEmpty()) return speakCount()
        val target = index.coerceIn(0, list.lastIndex)
        _state.value = _state.value.copy(current = target, pending = PendingDelete.NONE)
        val chat = list[target]
        val position = "Chat ${target + 1} de ${list.size}, del ${DateFormat.getDateInstance(DateFormat.LONG).format(Date(chat.fecha))}. "
        val edge = when {
            target == index -> ""
            index < 0 -> " Este es el más reciente."
            else -> " Este es el más antiguo."
        }
        voiceEngine.speak(
            position + "Preguntaste: ${chat.pregunta}. " + (chat.respuesta?.let { "Respondí: $it" } ?: "No guardé la respuesta.") + edge,
            listenAfter = true,
        )
    }

    fun askDeleteCurrent() {
        if (chats.value.isEmpty()) return speakCount()
        _state.value = _state.value.copy(pending = PendingDelete.ONE)
        voiceEngine.speak("¿Borro el chat ${_state.value.current + 1}? Di sí o no.", listenAfter = true)
    }

    fun askDeleteAll() {
        if (chats.value.isEmpty()) return speakCount()
        _state.value = _state.value.copy(pending = PendingDelete.ALL)
        voiceEngine.speak("¿Borro los ${chats.value.size} chats? No se pueden recuperar. Di sí o no.", listenAfter = true)
    }

    fun cancelDelete() {
        _state.value = _state.value.copy(pending = PendingDelete.NONE)
        voiceEngine.speak("De acuerdo, no borré nada.", listenAfter = true)
    }

    fun confirmDelete() {
        val pending = _state.value.pending
        val user = sessionRepository.currentUser.value ?: return
        val target = chats.value.getOrNull(_state.value.current)
        _state.value = ChatsUiState()
        viewModelScope.launch {
            if (pending == PendingDelete.ALL) {
                consultaDao.deleteAllByUser(user.id)
                cues.play(FeedbackCues.Cue.SUCCESS)
                voiceEngine.speak("Listo, borré todos los chats.")
            } else if (pending == PendingDelete.ONE && target != null) {
                consultaDao.deleteById(target.id)
                cues.play(FeedbackCues.Cue.SUCCESS)
                voiceEngine.speak("Listo, borré ese chat.", listenAfter = true)
            }
        }
    }
}

@Composable
fun ChatsScreen(
    onBack: () -> Unit,
    viewModel: ChatsViewModel = hiltViewModel(),
) {
    val chats by viewModel.chats.collectAsState()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTopBar(
            eyebrow = "MENÚ",
            title = "Chats",
            onBack = onBack,
            onCommands = viewModel::onCommandsRequested,
        )

        if (state.pending != PendingDelete.NONE) {
            NoticeBanner(
                if (state.pending == PendingDelete.ALL) "¿Borrar los ${chats.size} chats? Di sí o no." else "¿Borrar este chat? Di sí o no.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionChip(label = "sí", onClick = viewModel::confirmDelete)
                SuggestionChip(label = "no", onClick = viewModel::cancelDelete)
            }
        }

        if (chats.isEmpty()) {
            Text(
                "Todavía no hay chats guardados. Cuando hables con la inteligencia artificial, aparecerán aquí.",
                style = MaterialTheme.typography.bodyLarge,
                color = CecapiTextMuted,
                modifier = Modifier.padding(8.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(chats, key = { _, chat -> chat.id }) { index, chat ->
                ChatCard(
                    chat = chat,
                    selected = index == state.current,
                    onClick = { viewModel.readAt(index) },
                )
            }
        }

        if (chats.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TopAction(
                    icon = Icons.Filled.Delete,
                    label = "Borrar este",
                    help = "Borrar este chat. Te pido confirmación antes de borrarlo.",
                    onClick = viewModel::askDeleteCurrent,
                    tint = CecapiError,
                )
                TopAction(
                    icon = Icons.Filled.Delete,
                    label = "Borrar todos",
                    help = "Borrar todos los chats. Te pido confirmación antes de borrarlos.",
                    onClick = viewModel::askDeleteAll,
                    tint = CecapiError,
                )
            }
        }
    }
}

@Composable
private fun ChatCard(chat: ChatItem, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(chat.fecha))
    val help = "Chat del $date. Preguntaste: ${chat.pregunta}. Toca dos veces para que te lo lea."
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(CecapiSurface)
            .border(if (selected) 3.dp else 1.dp, if (selected) CecapiAccent else CecapiTextMuted.copy(alpha = 0.3f), shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = help }
            .voiceHint(help)
            .padding(16.dp),
    ) {
        Text(date, style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted)
        Text(
            chat.pregunta,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
        )
        chat.respuesta?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted, maxLines = 2)
        }
    }
}
