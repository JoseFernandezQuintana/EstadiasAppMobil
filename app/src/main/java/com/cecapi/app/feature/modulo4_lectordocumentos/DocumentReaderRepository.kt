package com.cecapi.app.feature.modulo4_lectordocumentos

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class OcrResult(val documentoId: Long, val texto: String)

@Singleton
class DocumentReaderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentoDao: DocumentoEscaneadoDao,
    private val textoDao: TextoExtraidoDao,
    private val historialDao: HistorialLecturaDao,
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun observeRecent(usuarioId: Long): Flow<List<DocumentoEscaneadoEntity>> = documentoDao.observeRecent(usuarioId)

    /** Runs on-device OCR on the captured photo and persists the document + extracted text. */
    suspend fun processCapturedPhoto(usuarioId: Long, imageUri: Uri, rutaImagen: String): Result<OcrResult> {
        return try {
            val inputImage = InputImage.fromFilePath(context, imageUri)
            val visionText = recognizer.process(inputImage).await()
            val texto = visionText.text.ifBlank { "No se detectó texto en la imagen." }

            val documentoId = documentoDao.insert(
                DocumentoEscaneadoEntity(usuarioId = usuarioId, rutaImagen = rutaImagen),
            )
            textoDao.insert(TextoExtraidoEntity(documentoId = documentoId, textoCompleto = texto))
            Result.success(OcrResult(documentoId, texto))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logLectura(documentoId: Long) {
        historialDao.insert(HistorialLecturaEntity(documentoId = documentoId))
    }
}
