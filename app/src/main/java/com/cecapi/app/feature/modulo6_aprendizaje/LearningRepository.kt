package com.cecapi.app.feature.modulo6_aprendizaje

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val PUNTOS_POR_ACIERTO = 10
private const val PUNTOS_PARA_SUBIR_NIVEL = 50
private const val NIVEL_MAXIMO = 3

@Singleton
class LearningRepository @Inject constructor(
    private val ejercicioDao: EjercicioDao,
    private val resultadoDao: ResultadoEjercicioDao,
    private val nivelDao: NivelAprendizajeDao,
) {
    fun observeNivel(usuarioId: Long): Flow<NivelAprendizajeEntity> =
        nivelDao.observeByUser(usuarioId).map { it ?: NivelAprendizajeEntity(usuarioId = usuarioId) }

    fun observeEjerciciosDelNivel(usuarioId: Long): Flow<List<EjercicioEntity>> =
        observeNivel(usuarioId).flatMapLatest { nivel -> ejercicioDao.observeByNivel(nivel.nivelActual) }

    suspend fun registrarResultado(usuarioId: Long, ejercicioId: Long, correcto: Boolean) {
        resultadoDao.insert(ResultadoEjercicioEntity(usuarioId = usuarioId, ejercicioId = ejercicioId, fueCorrecto = correcto))
        if (!correcto) return

        val nivelActual = nivelDao.findByUser(usuarioId) ?: NivelAprendizajeEntity(usuarioId = usuarioId)
        val nuevosPuntos = nivelActual.puntosTotales + PUNTOS_POR_ACIERTO
        val subeNivel = nuevosPuntos >= PUNTOS_PARA_SUBIR_NIVEL && nivelActual.nivelActual < NIVEL_MAXIMO
        val actualizado = nivelActual.copy(
            puntosTotales = if (subeNivel) 0 else nuevosPuntos,
            nivelActual = if (subeNivel) nivelActual.nivelActual + 1 else nivelActual.nivelActual,
            fechaActualizacion = System.currentTimeMillis(),
        )
        nivelDao.upsert(actualizado)
    }
}
