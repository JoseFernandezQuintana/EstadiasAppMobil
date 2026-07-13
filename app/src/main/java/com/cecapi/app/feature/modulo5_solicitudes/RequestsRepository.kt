package com.cecapi.app.feature.modulo5_solicitudes

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Matches `{placeholder_name}` tokens inside a plantilla body. */
private val PLACEHOLDER_REGEX = Regex("\\{(\\w+)}")

fun extractPlaceholders(cuerpoPlantilla: String): List<String> =
    PLACEHOLDER_REGEX.findAll(cuerpoPlantilla).map { it.groupValues[1] }.distinct().toList()

@Singleton
class RequestsRepository @Inject constructor(
    private val plantillaDao: PlantillaSolicitudDao,
    private val solicitudDao: SolicitudGeneradaDao,
    private val datoDao: DatoSolicitudDao,
) {
    fun observePlantillas(): Flow<List<PlantillaSolicitudEntity>> = plantillaDao.observeAll()

    fun observeSolicitudes(usuarioId: Long): Flow<List<SolicitudGeneradaEntity>> = solicitudDao.observeByUser(usuarioId)

    suspend fun generarSolicitud(
        usuarioId: Long,
        plantilla: PlantillaSolicitudEntity,
        datos: Map<String, String>,
    ): SolicitudGeneradaEntity {
        var textoFinal = plantilla.cuerpoPlantilla
        datos.forEach { (clave, valor) -> textoFinal = textoFinal.replace("{$clave}", valor) }

        val solicitudId = solicitudDao.insert(
            SolicitudGeneradaEntity(usuarioId = usuarioId, plantillaId = plantilla.id, textoFinal = textoFinal),
        )
        datoDao.insertAll(
            datos.map { (clave, valor) -> DatoSolicitudEntity(solicitudId = solicitudId, clave = clave, valor = valor) },
        )
        return SolicitudGeneradaEntity(
            id = solicitudId,
            usuarioId = usuarioId,
            plantillaId = plantilla.id,
            textoFinal = textoFinal,
        )
    }
}
