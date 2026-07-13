package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.model.ModuloCecapi
import com.cecapi.app.core.util.PasswordHasher
import com.cecapi.app.core.voice.VoiceEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

@Singleton
class SessionRepository @Inject constructor(
    private val usuarioDao: UsuarioDao,
    private val permisosModuloDao: PermisosModuloDao,
    private val configuracionUsuarioDao: ConfiguracionUsuarioDao,
    private val voiceEngine: VoiceEngine,
) {
    private val _currentUser = MutableStateFlow<UsuarioEntity?>(null)
    val currentUser: StateFlow<UsuarioEntity?> = _currentUser.asStateFlow()

    suspend fun login(nombreUsuario: String, contrasena: String): LoginResult {
        val usuario = usuarioDao.findByUsername(nombreUsuario.trim())
            ?: return LoginResult.InvalidCredentials
        val hashed = PasswordHasher.hash(contrasena)
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
        val usuario = UsuarioEntity(
            nombreUsuario = nombreUsuario.trim(),
            contrasenaHash = PasswordHasher.hash(contrasena),
            nombreCompleto = nombreCompleto.trim(),
        )
        val nuevoId = usuarioDao.insert(usuario)
        if (nuevoId <= 0) return RegisterResult.UsernameTaken

        val usuarioCreado = usuario.copy(id = nuevoId)
        completeLogin(usuarioCreado)
        return RegisterResult.Success(usuarioCreado)
    }

    fun logout() {
        _currentUser.value = null
    }

    /** Shared by login and register: sets up defaults for a brand-new account and is a
     * harmless no-op for a returning one, then applies saved voice settings and opens the session. */
    private suspend fun completeLogin(usuario: UsuarioEntity) {
        ensureDefaultPermisos(usuario.id)
        val config = ensureDefaultConfiguracion(usuario.id)
        voiceEngine.applyVoiceSettings(config.velocidadVoz, config.volumen)
        _currentUser.value = usuario
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
}
