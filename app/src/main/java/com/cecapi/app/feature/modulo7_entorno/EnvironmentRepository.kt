package com.cecapi.app.feature.modulo7_entorno

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class EnvironmentResult(val escaneoId: Long, val descripcion: String, val etiquetas: List<String>)

@Singleton
class EnvironmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val escaneoDao: EscaneoEntornoDao,
    private val objetoDao: ObjetoDetectadoDao,
    private val descripcionDao: DescripcionEntornoDao,
) {
    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build(),
    )

    fun observeRecent(usuarioId: Long): Flow<List<EscaneoEntornoEntity>> = escaneoDao.observeRecent(usuarioId)

    suspend fun processCapturedPhoto(usuarioId: Long, imageUri: Uri, rutaImagen: String): Result<EnvironmentResult> {
        return try {
            val inputImage = InputImage.fromFilePath(context, imageUri)
            val objetosDetectados = detector.process(inputImage).await()

            val etiquetas = objetosDetectados
                .flatMap { it.labels }
                .sortedByDescending { it.confidence }
                .map { it.text }
                .distinct()

            val escaneoId = escaneoDao.insert(EscaneoEntornoEntity(usuarioId = usuarioId, rutaImagen = rutaImagen))

            if (etiquetas.isNotEmpty()) {
                objetoDao.insertAll(
                    objetosDetectados.flatMap { detected ->
                        detected.labels.map { label ->
                            ObjetoDetectadoEntity(escaneoId = escaneoId, etiqueta = label.text, confianza = label.confidence)
                        }
                    },
                )
            }

            val descripcion = if (etiquetas.isEmpty()) {
                "No logré identificar objetos claros enfrente. Intenta acercarte o mejorar la iluminación."
            } else {
                "Enfrente de ti veo: ${etiquetas.joinToString(", ")}."
            }
            descripcionDao.insert(DescripcionEntornoEntity(escaneoId = escaneoId, textoDescripcion = descripcion))

            Result.success(EnvironmentResult(escaneoId, descripcion, etiquetas))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
