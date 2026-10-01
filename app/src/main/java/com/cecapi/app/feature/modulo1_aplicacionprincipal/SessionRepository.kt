package com.cecapi.app.feature.modulo1_aplicacionprincipal

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cecapi.app.core.data.AccountEraser
import com.cecapi.app.core.model.ModuloCecapi
import com.cecapi.app.core.util.PasswordHasher
import com.cecapi.app.core.voice.VoiceEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LoginResult {
    data class Success(val usuario: UsuarioEntity) : LoginResult
    data object InvalidCredentials : LoginResult
}

sealed interface RegisterResult {
    data class Success(val usuario: UsuarioEntity) : RegisterResult
    data object UsernameTaken : RegisterResult
}

private val Context.sessionStore by preferencesDataStore(name = "remembered_session")

@Singleton
class SessionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usuarioDao: UsuarioDao,
    private val permisosModuloDao: PermisosModuloDao,
    private val configuracionUsuarioDao: ConfiguracionUsuarioDao,
    private val accountEraser: AccountEraser,
    private val voiceEngine: VoiceEngine,
) {
    private val _currentUser = MutableStateFlow<UsuarioEntity?>(null)
    val currentUser: StateFlow<UsuarioEntity?> = _currentUser.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        // Opening the app from the same device it was used on before signs the person straight in,
        // without asking for the password again — the phone's own lock screen is the security boundary,
        // the same trade-off every app that "stays signed in" makes. Cleared the moment they log out.
        scope.launch { restoreRememberedSession() }
    }

    private suspend fun restoreRememberedSession() {
        val rememberedId = context.sessionStore.data.first()[KEY_REMEMBERED_USER_ID] ?: return
        val usuario = usuarioDao.findById(rememberedId) ?: return
        _currentUser.value = usuario
    }

    suspend fun login(nombreUsuario: String, contrasena: String): LoginResult {
        val usuario = usuarioDao.findByUsername(nombreUsuario.trim())
            ?: return LoginResult.InvalidCredentials
        val hashed = PasswordHasher.hash(contrasena.trim())
        if (usuario.contrasenaHash != hashed) {
            return LoginResult.InvalidCredentials
        }
        completeLogin(usuario)
        return LoginResult.Success(usuario)
    }

    /** Creates a new CECAPI account. Anyone opening the app can self-register — there is
     * no institutional approval step in v1.0 (see Módulo 13 in database/schema.sql for
     * the future admin-managed accounts flow). */
    suspend fun register(nombreUsuario: String, contrasena: String, nombreCompleto: String): RegisterResult {
        val nombreNormalizado = nombreUsuario.trim().uppercase()
        // The table has no unique index on the username, so the duplicate check has to be explicit.
        if (usuarioDao.findByUsername(nombreNormalizado) != null) return RegisterResult.UsernameTaken
        val usuario = UsuarioEntity(
            nombreUsuario = nombreNormalizado,
            contrasenaHash = PasswordHasher.hash(contrasena.trim()),
            nombreCompleto = nombreCompleto.trim(),
        )
        val nuevoId = usuarioDao.insert(usuario)
        if (nuevoId <= 0) return RegisterResult.UsernameTaken

        val usuarioCreado = usuario.copy(id = nuevoId)
        completeLogin(usuarioCreado)
        return RegisterResult.Success(usuarioCreado)
    }

    private var logoutNoticePending = false

    fun logout() {
        _currentUser.value = null
        logoutNoticePending = true
        scope.launch { context.sessionStore.edit { it.remove(KEY_REMEMBERED_USER_ID) } }
    }

    /** Deletes the signed-in person's account and all their data, then signs out. False when nobody is signed in. */
    suspend fun deleteCurrentAccount(): Boolean {
        val user = _currentUser.value ?: return false
        accountEraser.erase(user.id)
        logout()
        return true
    }

    /** True once after a logout, so the home screen can say "Sesión cerrada" as it opens. */
    fun consumeLogoutNotice(): Boolean = logoutNoticePending.also { logoutNoticePending = false }

    /** Shared by login and register: sets up defaults for a brand-new account and is a
     * harmless no-op for a returning one, then applies saved voice settings and opens the session. */
    private suspend fun completeLogin(usuario: UsuarioEntity) {
        ensureDefaultPermisos(usuario.id)
        // Voice speed and cues are per device now (DeviceSettings); the per-user row is kept only so
        // older accounts and the database schema stay intact.
        ensureDefaultConfiguracion(usuario.id)
        _currentUser.value = usuario
        context.sessionStore.edit { it[KEY_REMEMBERED_USER_ID] = usuario.id }
    }

    /** First login for a user: grant access to all six functional modules by default. */
    private suspend fun ensureDefaultPermisos(usuarioId: Long) {
        if (permisosModuloDao.countByUser(usuarioId) > 0) return
        val defaults = ModuloCecapi.entries.map { modulo ->
            PermisosModuloEntity(usuarioId = usuarioId, moduloCodigo = modulo.storageCode, habilitado = true)
        }
        permisosModuloDao.insertAll(defaults)
    }

    private suspend fun ensureDefaultConfiguracion(usuarioId: Long): ConfiguracionUsuarioEntity {
        val existente = configuracionUsuarioDao.findByUser(usuarioId)
        if (existente != null) return existente
        val nueva = ConfiguracionUsuarioEntity(usuarioId = usuarioId)
        configuracionUsuarioDao.upsert(nueva)
        return nueva
    }

    private companion object {
        val KEY_REMEMBERED_USER_ID = longPreferencesKey("remembered_user_id")
    }
}
