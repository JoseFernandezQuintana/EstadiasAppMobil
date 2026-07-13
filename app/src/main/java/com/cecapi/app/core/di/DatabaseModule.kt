package com.cecapi.app.core.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cecapi.app.core.data.AppDatabase
import com.cecapi.app.core.util.PasswordHasher
import com.cecapi.app.feature.modulo3_asistenteinteligente.ConsultaIaDao
import com.cecapi.app.feature.modulo3_asistenteinteligente.ContextoConversacionDao
import com.cecapi.app.feature.modulo3_asistenteinteligente.RespuestaIaDao
import com.cecapi.app.feature.modulo4_lectordocumentos.DocumentoEscaneadoDao
import com.cecapi.app.feature.modulo4_lectordocumentos.HistorialLecturaDao
import com.cecapi.app.feature.modulo4_lectordocumentos.TextoExtraidoDao
import com.cecapi.app.feature.modulo7_entorno.DescripcionEntornoDao
import com.cecapi.app.feature.modulo7_entorno.EscaneoEntornoDao
import com.cecapi.app.feature.modulo7_entorno.ObjetoDetectadoDao
import com.cecapi.app.feature.modulo6_aprendizaje.EjercicioDao
import com.cecapi.app.feature.modulo6_aprendizaje.EjercicioEntity
import com.cecapi.app.feature.modulo6_aprendizaje.NivelAprendizajeDao
import com.cecapi.app.feature.modulo6_aprendizaje.ResultadoEjercicioDao
import com.cecapi.app.feature.modulo1_aplicacionprincipal.ConfiguracionUsuarioDao
import com.cecapi.app.feature.modulo1_aplicacionprincipal.PermisosModuloDao
import com.cecapi.app.feature.modulo1_aplicacionprincipal.UsuarioDao
import com.cecapi.app.feature.modulo1_aplicacionprincipal.UsuarioEntity
import com.cecapi.app.feature.modulo5_solicitudes.DatoSolicitudDao
import com.cecapi.app.feature.modulo5_solicitudes.PlantillaSolicitudDao
import com.cecapi.app.feature.modulo5_solicitudes.PlantillaSolicitudEntity
import com.cecapi.app.feature.modulo5_solicitudes.SolicitudGeneradaDao
import com.cecapi.app.feature.modulo2_asistentevoz.ComandoVozDao
import com.cecapi.app.feature.modulo2_asistentevoz.HistorialComandoDao
import com.cecapi.app.feature.modulo2_asistentevoz.RespuestaAuditivaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        databaseProvider: Provider<AppDatabase>,
    ): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "cecapi.db")
        .addCallback(object : androidx.room.RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    seedDemoData(databaseProvider.get())
                }
            }
        })
        .build()

    private suspend fun seedDemoData(database: AppDatabase) {
        val usuarioId = database.usuarioDao().insert(
            UsuarioEntity(
                nombreUsuario = "cecapi",
                contrasenaHash = PasswordHasher.hash("1234"),
                nombreCompleto = "Usuario CECAPI",
            ),
        )
        if (usuarioId <= 0) return

        database.plantillaSolicitudDao().insertAll(
            listOf(
                PlantillaSolicitudEntity(
                    titulo = "Constancia de estudios",
                    descripcion = "Para trámites que requieren comprobar que estudias en CECAPI.",
                    cuerpoPlantilla = "Solicito una constancia de estudios a nombre de {nombre_completo}, " +
                        "con fecha {fecha}, para el siguiente motivo: {motivo}.",
                ),
                PlantillaSolicitudEntity(
                    titulo = "Justificación de inasistencia",
                    descripcion = "Para justificar una falta ante CECAPI.",
                    cuerpoPlantilla = "Yo, {nombre_completo}, justifico mi inasistencia del día {fecha} " +
                        "por el siguiente motivo: {motivo}.",
                ),
                PlantillaSolicitudEntity(
                    titulo = "Solicitud de apoyo",
                    descripcion = "Para pedir apoyo o material a CECAPI.",
                    cuerpoPlantilla = "Yo, {nombre_completo}, solicito apoyo con: {motivo}. Fecha: {fecha}.",
                ),
            ),
        )

        database.ejercicioDao().insertAll(
            listOf(
                EjercicioEntity(
                    titulo = "Saludo",
                    instruccion = "Repite después de mí: hola, buenos días.",
                    respuestaCorrecta = "hola buenos dias",
                    nivel = 1,
                ),
                EjercicioEntity(
                    titulo = "Comando básico",
                    instruccion = "¿Qué palabra usarías para abrir el asistente de voz? Di: asistente de voz.",
                    respuestaCorrecta = "asistente de voz",
                    nivel = 1,
                ),
                EjercicioEntity(
                    titulo = "Pedir ayuda",
                    instruccion = "Di la palabra que usarías para pedir ayuda a la aplicación.",
                    respuestaCorrecta = "ayuda",
                    nivel = 1,
                ),
                EjercicioEntity(
                    titulo = "Cámara inteligente",
                    instruccion = "Di el nombre del módulo que lee documentos con la cámara.",
                    respuestaCorrecta = "camara inteligente",
                    nivel = 2,
                ),
                EjercicioEntity(
                    titulo = "Solicitudes",
                    instruccion = "Di el nombre del módulo donde generas cartas y solicitudes.",
                    respuestaCorrecta = "centro de solicitudes",
                    nivel = 2,
                ),
                EjercicioEntity(
                    titulo = "Repaso final",
                    instruccion = "Di: gracias por tu ayuda.",
                    respuestaCorrecta = "gracias por tu ayuda",
                    nivel = 3,
                ),
            ),
        )
    }

    @Provides
    fun provideUsuarioDao(db: AppDatabase): UsuarioDao = db.usuarioDao()

    @Provides
    fun provideConfiguracionUsuarioDao(db: AppDatabase): ConfiguracionUsuarioDao = db.configuracionUsuarioDao()

    @Provides
    fun providePermisosModuloDao(db: AppDatabase): PermisosModuloDao = db.permisosModuloDao()

    @Provides
    fun provideComandoVozDao(db: AppDatabase): ComandoVozDao = db.comandoVozDao()

    @Provides
    fun provideHistorialComandoDao(db: AppDatabase): HistorialComandoDao = db.historialComandoDao()

    @Provides
    fun provideRespuestaAuditivaDao(db: AppDatabase): RespuestaAuditivaDao = db.respuestaAuditivaDao()

    @Provides
    fun provideConsultaIaDao(db: AppDatabase): ConsultaIaDao = db.consultaIaDao()

    @Provides
    fun provideRespuestaIaDao(db: AppDatabase): RespuestaIaDao = db.respuestaIaDao()

    @Provides
    fun provideContextoConversacionDao(db: AppDatabase): ContextoConversacionDao = db.contextoConversacionDao()

    @Provides
    fun provideDocumentoEscaneadoDao(db: AppDatabase): DocumentoEscaneadoDao = db.documentoEscaneadoDao()

    @Provides
    fun provideTextoExtraidoDao(db: AppDatabase): TextoExtraidoDao = db.textoExtraidoDao()

    @Provides
    fun provideHistorialLecturaDao(db: AppDatabase): HistorialLecturaDao = db.historialLecturaDao()

    @Provides
    fun providePlantillaSolicitudDao(db: AppDatabase): PlantillaSolicitudDao = db.plantillaSolicitudDao()

    @Provides
    fun provideSolicitudGeneradaDao(db: AppDatabase): SolicitudGeneradaDao = db.solicitudGeneradaDao()

    @Provides
    fun provideDatoSolicitudDao(db: AppDatabase): DatoSolicitudDao = db.datoSolicitudDao()

    @Provides
    fun provideEjercicioDao(db: AppDatabase): EjercicioDao = db.ejercicioDao()

    @Provides
    fun provideResultadoEjercicioDao(db: AppDatabase): ResultadoEjercicioDao = db.resultadoEjercicioDao()

    @Provides
    fun provideNivelAprendizajeDao(db: AppDatabase): NivelAprendizajeDao = db.nivelAprendizajeDao()

    @Provides
    fun provideEscaneoEntornoDao(db: AppDatabase): EscaneoEntornoDao = db.escaneoEntornoDao()

    @Provides
    fun provideObjetoDetectadoDao(db: AppDatabase): ObjetoDetectadoDao = db.objetoDetectadoDao()

    @Provides
    fun provideDescripcionEntornoDao(db: AppDatabase): DescripcionEntornoDao = db.descripcionEntornoDao()
}
