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

@Entity(tableName = "usuarios")
data class UsuarioEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "nombre_usuario")
    val nombreUsuario: String,
    @ColumnInfo(name = "contrasena_hash")
    val contrasenaHash: String,
    @ColumnInfo(name = "nombre_completo")
    val nombreCompleto: String,
    @ColumnInfo(name = "fecha_registro")
    val fechaRegistro: Long = System.currentTimeMillis(),
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
    @Query("SELECT * FROM usuarios WHERE nombre_usuario = :nombreUsuario LIMIT 1")
    suspend fun findByUsername(nombreUsuario: String): UsuarioEntity?

    @Query("SELECT * FROM usuarios WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): UsuarioEntity?

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
