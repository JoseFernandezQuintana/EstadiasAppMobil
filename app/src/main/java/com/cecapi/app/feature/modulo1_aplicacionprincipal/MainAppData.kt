package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// ---- Módulo 1: Aplicación Principal Accesible -----------------------------
// Tables: usuarios, configuracion_usuario, permisos_modulos (see docs/database/schema.sql)

/** Who is signing in. Stored by [codigo] in `usuarios.rol`; anyone who self-registers is a plain [USUARIO]. */
enum class RolUsuario(val codigo: String) {
    ADMINISTRADOR("administrador"),
    DIRECTIVO("directivo"),
    EDUCADOR("educador"),
    ALUMNO("alumno"),
    USUARIO("usuario"),
    ;

    companion object {
        fun fromCodigo(codigo: String): RolUsuario = entries.find { it.codigo == codigo } ?: USUARIO
    }
}

@Entity(tableName = "usuarios")
data class UsuarioEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Always stored in UPPERCASE so "Jorge", "jorge" and "JORGE" are the same account. */
    @ColumnInfo(name = "nombre_usuario")
    val nombreUsuario: String,
    @ColumnInfo(name = "contrasena_hash")
    val contrasenaHash: String,
    @ColumnInfo(name = "nombre_completo")
    val nombreCompleto: String,
    @ColumnInfo(name = "fecha_registro")
    val fechaRegistro: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "rol")
    val rol: String = RolUsuario.USUARIO.codigo,
    /** Institution or place the person comes from (e.g. "CECAPI"); empty when they signed up on their own. */
    @ColumnInfo(name = "origen")
    val origen: String = "",
    /** For an alumno: the id of their assigned educador (another row in this same table). Null until assigned,
     * and always null for every other role — an educador, directivo or usuario has no "own teacher". */
    @ColumnInfo(name = "educador_id")
    val educadorId: Long? = null,
)

@Entity(
    tableName = "configuracion_usuario",
    foreignKeys = [
        ForeignKey(
            entity = UsuarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["usuario_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("usuario_id", unique = true)],
)
data class ConfiguracionUsuarioEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "usuario_id")
    val usuarioId: Long,
    @ColumnInfo(name = "velocidad_voz")
    val velocidadVoz: Float = 1.0f,
    @ColumnInfo(name = "volumen")
    val volumen: Float = 1.0f,
    @ColumnInfo(name = "modo_simple")
    val modoSimple: Boolean = true,
)

@Entity(
    tableName = "permisos_modulos",
    foreignKeys = [
        ForeignKey(
            entity = UsuarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["usuario_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("usuario_id")],
)
data class PermisosModuloEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "usuario_id")
    val usuarioId: Long,
    @ColumnInfo(name = "modulo_codigo")
    val moduloCodigo: String,
    @ColumnInfo(name = "habilitado")
    val habilitado: Boolean = true,
)

@Dao
interface UsuarioDao {
    @Query("SELECT * FROM usuarios WHERE nombre_usuario = :nombreUsuario COLLATE NOCASE LIMIT 1")
    suspend fun findByUsername(nombreUsuario: String): UsuarioEntity?

    @Query("SELECT * FROM usuarios WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): UsuarioEntity?

    /** For an educador: the alumnos assigned to them, nobody else's. */
    @Query("SELECT * FROM usuarios WHERE educador_id = :educadorId ORDER BY nombre_completo")
    fun observeAlumnosDeEducador(educadorId: Long): Flow<List<UsuarioEntity>>

    /** For a directivo: everyone in their institution, every role. An educador's panel narrows this itself. */
    @Query("SELECT * FROM usuarios WHERE origen = :origen COLLATE NOCASE ORDER BY rol, nombre_completo")
    fun observeUsuariosDeInstitucion(origen: String): Flow<List<UsuarioEntity>>

    /** For an administrador: literally everyone — every institution and every independent usuario. */
    @Query("SELECT * FROM usuarios ORDER BY origen, rol, nombre_completo")
    fun observeTodos(): Flow<List<UsuarioEntity>>

    @Query("UPDATE usuarios SET educador_id = :educadorId WHERE id = :alumnoId")
    suspend fun asignarEducador(alumnoId: Long, educadorId: Long?)

    @Query("UPDATE usuarios SET rol = :rol WHERE id = :usuarioId")
    suspend fun cambiarRol(usuarioId: Long, rol: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(usuario: UsuarioEntity): Long
}

@Dao
interface ConfiguracionUsuarioDao {
    @Query("SELECT * FROM configuracion_usuario WHERE usuario_id = :usuarioId LIMIT 1")
    fun observeByUser(usuarioId: Long): Flow<ConfiguracionUsuarioEntity?>

    @Query("SELECT * FROM configuracion_usuario WHERE usuario_id = :usuarioId LIMIT 1")
    suspend fun findByUser(usuarioId: Long): ConfiguracionUsuarioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ConfiguracionUsuarioEntity)

    @Update
    suspend fun update(config: ConfiguracionUsuarioEntity)
}

@Dao
interface PermisosModuloDao {
    @Query("SELECT * FROM permisos_modulos WHERE usuario_id = :usuarioId")
    fun observeByUser(usuarioId: Long): Flow<List<PermisosModuloEntity>>

    @Query("SELECT COUNT(*) FROM permisos_modulos WHERE usuario_id = :usuarioId")
    suspend fun countByUser(usuarioId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(permisos: List<PermisosModuloEntity>)
}
