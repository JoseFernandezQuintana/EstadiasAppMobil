package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cecapi.app.core.voice.FeedbackCues
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.core.voice.VoiceState
import com.cecapi.app.core.voice.VoiceText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GestionUiState(
    val rolObservador: RolUsuario = RolUsuario.USUARIO,
    val personas: List<UsuarioEntity> = emptyList(),
    /** La fila que está desplegada mostrando sus acciones; null = ninguna. */
    val expandidoId: Long? = null,
    /** Solo administrador (todas) y directivo (las de su institución); vacío para los demás roles. */
    val incidencias: List<IncidenciaEntity> = emptyList(),
)

/**
 * Gestión: lo que ve y puede hacer un administrador, directivo o educador con las cuentas que les
 * corresponden, según [RolePermissions]. Un alumno o un usuario nunca llegan a esta pantalla.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GestionViewModel @Inject constructor(
    private val voiceEngine: VoiceEngine,
    private val sessionRepository: SessionRepository,
    private val usuarioDao: UsuarioDao,
    private val incidenciaDao: IncidenciaDao,
    private val cues: FeedbackCues,
) : ViewModel() {

    val voiceState: StateFlow<VoiceState> = voiceEngine.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceState.Idle,
    )

    private val _expandidoId = MutableStateFlow<Long?>(null)

    private val personasFlow = sessionRepository.currentUser
        .filterNotNull()
        .flatMapLatest { usuario ->
            when (RolUsuario.fromCodigo(usuario.rol)) {
                RolUsuario.ADMINISTRADOR -> usuarioDao.observeTodos()
                RolUsuario.DIRECTIVO -> usuarioDao.observeUsuariosDeInstitucion(usuario.origen)
                RolUsuario.EDUCADOR -> usuarioDao.observeAlumnosDeEducador(usuario.id)
                // Nunca debería llegar aquí (la pantalla está oculta para estos roles), pero por si acaso.
                RolUsuario.ALUMNO, RolUsuario.USUARIO -> flowOf(emptyList())
            }
        }

    private val incidenciasFlow = sessionRepository.currentUser
        .filterNotNull()
        .flatMapLatest { usuario ->
            when (RolUsuario.fromCodigo(usuario.rol)) {
                RolUsuario.ADMINISTRADOR -> incidenciaDao.observeTodas()
                RolUsuario.DIRECTIVO -> incidenciaDao.observeDeInstitucion(usuario.origen)
                else -> flowOf(emptyList())
            }
        }

    val uiState: StateFlow<GestionUiState> = combine(
        sessionRepository.currentUser.filterNotNull(),
        personasFlow,
        _expandidoId,
        incidenciasFlow,
    ) { usuario, personas, expandidoId, incidencias ->
        GestionUiState(
            rolObservador = RolUsuario.fromCodigo(usuario.rol),
            personas = personas,
            expandidoId = expandidoId,
            incidencias = incidencias,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GestionUiState())

    private val _back = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val back: SharedFlow<Unit> = _back

    init {
        viewModelScope.launch {
            voiceEngine.recognizedSpeech.collect { speech -> onSpeech(speech.text) }
        }
        // El detalle de cuántas personas se dice a petición ("cuántas personas"), no de entrada.
        voiceEngine.speak("Gestión. " + CommandCatalog.hint("gestión"), listenAfter = true)
    }

    private fun onSpeech(spoken: String) {
        val text = VoiceText.normalize(spoken)
        fun has(vararg words: String) = VoiceText.hasAny(text, *words)
        when {
            CommandCatalog.isRequest(spoken) -> voiceEngine.speak(CommandCatalog.GESTION, listenAfter = true)
            has("cuantas personas", "cuantos hay", "cuantos alumnos") -> speakCount()
            has("atras", "volver", "vuelve", "regresa", "regresar", "salir", "menu", "inicio", "pantalla anterior") -> _back.tryEmit(Unit)
            else -> {
                cues.play(FeedbackCues.Cue.NOT_UNDERSTOOD)
                voiceEngine.speak("No entendí. " + CommandCatalog.hint("gestión"), listenAfter = true)
            }
        }
    }

    private fun speakCount() {
        val state = uiState.value
        val palabra = if (state.rolObservador == RolUsuario.EDUCADOR) "alumnos" else "personas"
        voiceEngine.speak("Tienes ${state.personas.size} $palabra.", listenAfter = true)
    }

    fun onCommandsRequested() {
        voiceEngine.speak(CommandCatalog.GESTION, listenAfter = true)
    }

    fun onMicTapped() = voiceEngine.startListening()

    /** Two quick taps on the mic silence the assistant, for someone using touch instead of voice. */
    fun onMicDoubleTap() = voiceEngine.mute()

    /** True solo para administrador y directivo: un educador ve su lista pero no cambia roles. */
    fun puedeEditar(): Boolean =
        uiState.value.rolObservador == RolUsuario.ADMINISTRADOR || uiState.value.rolObservador == RolUsuario.DIRECTIVO

    fun onPersonaTocada(persona: UsuarioEntity) {
        val propio = sessionRepository.currentUser.value?.id == persona.id
        // Un educador no cambia roles, pero igual necesita abrir la tarjeta para validar a su alumno.
        if (propio || (!puedeEditar() && !puedeValidar(persona))) return
        _expandidoId.value = if (_expandidoId.value == persona.id) null else persona.id
    }

    /** Roles que [observador] puede asignar: un directivo nunca otorga administrador. */
    fun rolesAsignables(): List<RolUsuario> = when (uiState.value.rolObservador) {
        RolUsuario.ADMINISTRADOR -> listOf(RolUsuario.ADMINISTRADOR, RolUsuario.DIRECTIVO, RolUsuario.EDUCADOR, RolUsuario.ALUMNO)
        RolUsuario.DIRECTIVO -> listOf(RolUsuario.DIRECTIVO, RolUsuario.EDUCADOR, RolUsuario.ALUMNO)
        else -> emptyList()
    }

    /** Educadores que se le pueden asignar a un alumno: de su misma institución. */
    fun educadoresPara(alumno: UsuarioEntity): List<UsuarioEntity> =
        uiState.value.personas.filter { it.rol == RolUsuario.EDUCADOR.codigo && it.origen.equals(alumno.origen, ignoreCase = true) }

    fun onRolElegido(persona: UsuarioEntity, nuevoRol: RolUsuario) {
        if (!puedeEditar()) return
        viewModelScope.launch {
            usuarioDao.cambiarRol(persona.id, nuevoRol.codigo)
            // Un rol que ya no es alumno no debería seguir con un educador asignado.
            if (nuevoRol != RolUsuario.ALUMNO) usuarioDao.asignarEducador(persona.id, null)
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("${persona.nombreCompleto} ahora es ${nuevoRol.codigo}.")
        }
        _expandidoId.value = null
    }

    fun onEducadorElegido(alumno: UsuarioEntity, educador: UsuarioEntity?) {
        if (!puedeEditar()) return
        viewModelScope.launch {
            usuarioDao.asignarEducador(alumno.id, educador?.id)
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak(
                if (educador != null) "${alumno.nombreCompleto} ahora está con ${educador.nombreCompleto}."
                else "Se quitó el educador de ${alumno.nombreCompleto}.",
            )
        }
        _expandidoId.value = null
    }

    /** Administrador valida directivo (o a cualquiera); directivo valida educador; educador valida alumno. */
    fun puedeValidar(persona: UsuarioEntity): Boolean {
        val observador = sessionRepository.currentUser.value ?: return false
        return RolePermissions.puedeValidar(observador, persona)
    }

    fun onValidar(persona: UsuarioEntity) {
        if (!puedeValidar(persona)) return
        viewModelScope.launch {
            usuarioDao.validar(persona.id)
            cues.play(FeedbackCues.Cue.SUCCESS)
            voiceEngine.speak("Validé a ${persona.nombreCompleto}.")
        }
    }

    fun onResolverIncidencia(incidencia: IncidenciaEntity) {
        viewModelScope.launch {
            incidenciaDao.marcarResuelta(incidencia.id)
            cues.play(FeedbackCues.Cue.SUCCESS)
        }
    }
}
