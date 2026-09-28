package com.cecapi.app.feature.modulo7_entorno

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import androidx.core.graphics.get
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File

data class EnvironmentResult(val escaneoId: Long, val descripcion: String, val etiquetas: List<String>)

private data class ObjetoIdentificado(val etiqueta: String, val confianza: Float)

@Singleton
class EnvironmentRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val escaneoDao: EscaneoEntornoDao,
    private val objetoDao: ObjetoDetectadoDao,
    private val descripcionDao: DescripcionEntornoDao,
) {
    // El Object Detector se usa SOLO para localizar dónde está cada objeto (bounding box).
    // Su clasificador integrado ("enableClassification") solo distingue 5 categorías muy
    // amplias (ropa, comida, objeto del hogar, lugar, planta) — nunca da nombres específicos
    // como "cama" o "mesa", así que aquí NO lo activamos.
    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .build(),
    )

    // El Image Labeler sí reconoce más de 400 entidades específicas (cama, mesa, silla, sofá,
    // ventana, lámpara, etc.). Lo corremos sobre el recorte de cada objeto localizado.
    private val imageLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    fun observeRecent(usuarioId: Long): Flow<List<EscaneoEntornoEntity>> = escaneoDao.observeRecent(usuarioId)

    suspend fun processCapturedPhoto(usuarioId: Long, imageUri: Uri, rutaImagen: String): Result<EnvironmentResult> {
        return try {
            val file = File(rutaImagen)
            if (!file.exists()) {
                return Result.failure(Exception("La imagen no existe en el disco."))
            }

            val inputImage = InputImage.fromFilePath(context, imageUri)
            val objetosDetectados = detector.process(inputImage).await()

            val objetosIdentificados = if (objetosDetectados.isNotEmpty()) {
                objetosDetectados.mapNotNull { detected ->
                    val recorte = recortarRegion(rutaImagen, detected.boundingBox) ?: return@mapNotNull null
                    try {
                        val nombreEspecifico = etiquetarRecorte(recorte)
                        val color = colorDominante(recorte)
                        if (nombreEspecifico == null) {
                            null
                        } else {
                            val label = nombreEspecifico.confianza
                            val texto = if (color != null) "${nombreEspecifico.etiqueta} de color $color" else nombreEspecifico.etiqueta
                            ObjetoIdentificado(texto, label)
                        }
                    } finally {
                        recorte.recycle()
                    }
                }
            } else {
                // El Object Detector no localizó nada (objeto pegado a la cámara, fondo liso, etc.).
                // Como último recurso, etiquetamos la foto completa.
                val bitmapCompleto = BitmapFactory.decodeFile(rutaImagen)
                val etiqueta = bitmapCompleto?.let {
                    try {
                        etiquetarRecorte(it)
                    } finally {
                        it.recycle()
                    }
                }
                etiqueta?.let { listOf(it) } ?: emptyList()
            }

            val etiquetas = objetosIdentificados.map { it.etiqueta }.distinct()

            val escaneoId = escaneoDao.insert(EscaneoEntornoEntity(usuarioId = usuarioId, rutaImagen = rutaImagen))

            if (objetosIdentificados.isNotEmpty()) {
                objetoDao.insertAll(
                    objetosIdentificados.map { objeto ->
                        ObjetoDetectadoEntity(
                            escaneoId = escaneoId,
                            etiqueta = objeto.etiqueta,
                            confianza = objeto.confianza,
                        )
                    },
                )
            }

            val descripcion = if (etiquetas.isEmpty()) {
                "No logré identificar objetos claros enfrente. Intenta centrar el objeto, acercarte un poco más o mejorar la iluminación."
            } else {
                "Enfrente de ti veo: ${etiquetas.joinToString(", ")}."
            }
            descripcionDao.insert(DescripcionEntornoEntity(escaneoId = escaneoId, textoDescripcion = descripcion))

            Result.success(EnvironmentResult(escaneoId, descripcion, etiquetas))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Identifica qué es un recorte específico (cama, mesa, silla...) usando Image Labeling. */
    private suspend fun etiquetarRecorte(recorte: Bitmap): ObjetoIdentificado? {
        return try {
            val etiquetaDetectada = imageLabeler.process(InputImage.fromBitmap(recorte, 0))
                .await()
                .maxByOrNull { it.confidence }
                ?: return null
            ObjetoIdentificado(traducir(etiquetaDetectada.text), etiquetaDetectada.confidence)
        } catch (_: Exception) {
            null
        }
    }

    /** Traduce las etiquetas de ML Kit (en inglés) a nombres comunes en español. */
    private fun traducir(texto: String): String {
        return when (texto.lowercase()) {
            "bed" -> "cama"
            "table" -> "mesa"
            "desk" -> "escritorio"
            "chair" -> "silla"
            "couch", "sofa" -> "sofá"
            "furniture" -> "mueble"
            "wardrobe", "cabinetry" -> "armario"
            "shelf", "shelving" -> "estante"
            "pillow" -> "almohada"
            "curtain" -> "cortina"
            "lamp" -> "lámpara"
            "mirror" -> "espejo"
            "stairs" -> "escaleras"
            "door" -> "puerta"
            "window" -> "ventana"
            "wall" -> "pared"
            "floor" -> "piso"
            "ceiling" -> "techo"
            "sink" -> "lavabo"
            "refrigerator" -> "refrigerador"
            "stove", "oven" -> "estufa"
            "television" -> "televisión"
            "laptop" -> "computadora portátil"
            "computer keyboard" -> "teclado de computadora"
            "mobile phone" -> "teléfono celular"
            "bottle" -> "botella"
            "cup" -> "taza"
            "book" -> "libro"
            "pen" -> "bolígrafo"
            "plant" -> "planta"
            "car" -> "automóvil"
            "bicycle" -> "bicicleta"
            "person" -> "persona"
            "dog" -> "perro"
            "cat" -> "gato"
            "food" -> "comida"
            "clothing" -> "ropa"
            "shoe" -> "zapato"
            "room" -> "habitación"
            "bathroom" -> "baño"
            "kitchen" -> "cocina"
            "home good" -> "objeto del hogar"
            "fashion good" -> "prenda o accesorio"
            else -> texto // mejor mostrar el nombre en inglés que perder la etiqueta
        }
    }

    /** Recorta solo la región del objeto detectado, para procesarla de forma eficiente. */
    private fun recortarRegion(rutaImagen: String, box: Rect): Bitmap? {
        return try {
            val regionDecoder = if (android.os.Build.VERSION.SDK_INT >= 31) {
                BitmapRegionDecoder.newInstance(rutaImagen)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(rutaImagen, false)
            }

            val bounds = Rect(
                box.left.coerceIn(0, regionDecoder.width - 1),
                box.top.coerceIn(0, regionDecoder.height - 1),
                box.right.coerceIn(1, regionDecoder.width),
                box.bottom.coerceIn(1, regionDecoder.height),
            )

            if (bounds.width() <= 0 || bounds.height() <= 0) {
                regionDecoder.recycle()
                return null
            }

            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            val recorte = regionDecoder.decodeRegion(bounds, opts)
            regionDecoder.recycle()
            recorte
        } catch (_: Exception) {
            null
        }
    }

    private fun colorDominante(recorte: Bitmap): String? {
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var muestras = 0
        val pasoX = maxOf(1, recorte.width / 10)
        val pasoY = maxOf(1, recorte.height / 10)

        for (y in 0 until recorte.height step pasoY) {
            for (x in 0 until recorte.width step pasoX) {
                val pixel = recorte[x, y]
                sumR += Color.red(pixel)
                sumG += Color.green(pixel)
                sumB += Color.blue(pixel)
                muestras++
            }
        }

        if (muestras == 0) return null

        val hsv = FloatArray(3)
        Color.RGBToHSV((sumR / muestras).toInt(), (sumG / muestras).toInt(), (sumB / muestras).toInt(), hsv)
        return nombreDeColor(hsv)
    }

    private fun nombreDeColor(hsv: FloatArray): String {
        val hue = hsv[0]
        val sat = hsv[1]
        val value = hsv[2]
        return when {
            value < 0.15f -> "negro"
            sat < 0.15f && value > 0.8f -> "blanco"
            sat < 0.15f -> "gris"
            hue !in 30f..<330f && sat > 0.2f && value < 0.5f -> "café"
            hue !in 15f..<345f -> "rojo"
            hue < 45f -> "naranja"
            hue < 75f -> "amarillo"
            hue < 160f -> "verde"
            hue < 260f -> "azul"
            hue < 320f -> "morado"
            else -> "rosa"
        }
    }
}
