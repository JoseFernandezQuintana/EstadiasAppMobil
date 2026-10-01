package com.cecapi.app.feature.modulo7_entorno

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class EnvironmentResult(val escaneoId: Long?, val descripcion: String, val etiquetas: List<String>)

/** Un objeto ya nombrado en español, con su color y dónde está en la foto. */
private data class ObjetoDescrito(
    val nombre: NombreEs,
    val color: String?,
    val posicion: String?,
    val confianza: Float,
    val area: Int,
) {
    /** "silla negra" (sin artículo ni posición), para guardar en la base. */
    val etiqueta: String get() = if (color != null) "${nombre.texto} $color" else nombre.texto

    /** "una silla negra a la izquierda", para decirlo en voz alta. */
    val frase: String
        get() = buildString {
            append(nombre.conArticulo)
            if (color != null) append(" ").append(color)
            if (posicion != null) append(" ").append(posicion)
        }
}

/**
 * Describe lo que hay enfrente SIN internet: los dos modelos de ML Kit (detección de objetos y
 * etiquetado de imágenes) viajan dentro de la app y corren en el teléfono.
 */
@Singleton
class EnvironmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val escaneoDao: EscaneoEntornoDao,
    private val objetoDao: ObjetoDetectadoDao,
    private val descripcionDao: DescripcionEntornoDao,
) {
    // Detector: encuentra DÓNDE están los objetos (cajas). Su clasificación propia solo tiene
    // 5 categorías gruesas (hogar, moda, comida, lugar, planta), por eso se usa como respaldo.
    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build(),
    )

    // Etiquetador: dice QUÉ es cada objeto (silla, laptop, botella...). Modelo incluido en la app.
    private val etiquetador = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.5f)
            .build(),
    )

    fun observeRecent(usuarioId: Long): Flow<List<EscaneoEntornoEntity>> = escaneoDao.observeRecent(usuarioId)

    /**
     * The camera is free for anyone: with no [usuarioId] (nobody signed in) the description is still said
     * out loud, it just is not saved to a history that would have nowhere to belong.
     */
    suspend fun processCapturedPhoto(usuarioId: Long?, imageUri: Uri, rutaImagen: String): Result<EnvironmentResult> {
        return try {
            val inicio = SystemClock.elapsedRealtime()

            val file = File(rutaImagen)
            if (!file.exists()) {
                return Result.failure(Exception("La imagen no existe en el disco."))
            }

            // Una sola imagen, ya girada y reducida: detección, recortes y color usan las mismas coordenadas.
            val bitmap = cargarBitmapOrientado(rutaImagen)
                ?: return Result.failure(Exception("No pude abrir la imagen."))

            val objetosDetectados = detector.process(InputImage.fromBitmap(bitmap, 0)).await()

            // Los MAX_OBJETOS más grandes se consideran los más importantes.
            val importantes = objetosDetectados
                .sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }
                .take(MAX_OBJETOS)

            val descritos = mutableListOf<ObjetoDescrito>()
            for (objeto in importantes) {
                describirObjeto(bitmap, objeto)?.let { descritos.add(it) }
            }

            // Si el detector no encontró cajas (por ejemplo, un objeto muy de cerca),
            // se etiqueta la foto completa y se dice lo principal sin posición.
            val principal: ObjetoDescrito? =
                if (descritos.isEmpty()) describirFotoCompleta(bitmap) else null

            val finales = when {
                descritos.isNotEmpty() -> descritos
                    .distinctBy { it.etiqueta to it.posicion }
                    .sortedBy { ordenHorizontal(it.posicion) }
                principal != null -> listOf(principal)
                else -> emptyList()
            }

            val descripcion = when {
                finales.isEmpty() ->
                    "No logré identificar objetos claros enfrente. Intenta centrar el objeto, acercarte un poco más o mejorar la iluminación."
                descritos.isEmpty() ->
                    "Veo principalmente ${finales.first().frase}."
                else ->
                    "Veo ${unir(finales.map { it.frase })}."
            }

            bitmap.recycle()

            val escaneoId = usuarioId?.let { id ->
                val nuevoId = escaneoDao.insert(EscaneoEntornoEntity(usuarioId = id, rutaImagen = rutaImagen))
                if (finales.isNotEmpty()) {
                    objetoDao.insertAll(
                        finales.map {
                            ObjetoDetectadoEntity(
                                escaneoId = nuevoId,
                                etiqueta = it.etiqueta,
                                confianza = it.confianza,
                            )
                        },
                    )
                }
                descripcionDao.insert(DescripcionEntornoEntity(escaneoId = nuevoId, textoDescripcion = descripcion))
                nuevoId
            }

            Log.d(TAG, "Descripción lista en ${SystemClock.elapsedRealtime() - inicio} ms: $descripcion")

            Result.success(EnvironmentResult(escaneoId, descripcion, finales.map { it.etiqueta }))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Recorta el objeto, lo etiqueta en el teléfono y arma nombre + color + posición. */
    private suspend fun describirObjeto(bitmap: Bitmap, objeto: DetectedObject): ObjetoDescrito? {
        val caja = objeto.boundingBox
        val recorte = recortar(bitmap, caja) ?: return null
        try {
            var nombre: NombreEs? = null
            var confianza = 0f

            val etiquetas = etiquetador.process(InputImage.fromBitmap(recorte, 0)).await()
                .sortedByDescending { it.confidence }
            for (etiqueta in etiquetas) {
                val traducido = EtiquetasEntorno.traducir(etiqueta.text)
                if (traducido != null) {
                    nombre = traducido
                    confianza = etiqueta.confidence
                    break
                } else if (!EtiquetasEntorno.esIgnorada(etiqueta.text)) {
                    // Etiqueta que aún no tenemos en español: anótala para completar el diccionario.
                    Log.d(TAG_ETIQUETAS, "Sin traducción: ${etiqueta.text} (${etiqueta.confidence})")
                }
            }

            // Respaldo: la categoría gruesa del detector ("objeto del hogar", "planta"...).
            if (nombre == null) {
                val gruesa = objeto.labels.maxByOrNull { it.confidence }
                nombre = EtiquetasEntorno.categoriaGruesa(gruesa?.text)
                confianza = gruesa?.confidence ?: 0.5f
            }
            if (nombre == null) return null

            val color = colorConcordado(recorte, nombre)
            return ObjetoDescrito(
                nombre = nombre,
                color = color,
                posicion = posicionHorizontal(caja, bitmap.width),
                confianza = confianza,
                area = caja.width() * caja.height(),
            )
        } finally {
            recorte.recycle()
        }
    }

    /** Sin cajas del detector: etiqueta la foto completa y devuelve lo más seguro, sin posición. */
    private suspend fun describirFotoCompleta(bitmap: Bitmap): ObjetoDescrito? {
        val etiquetas = etiquetador.process(InputImage.fromBitmap(bitmap, 0)).await()
            .sortedByDescending { it.confidence }
        for (etiqueta in etiquetas) {
            val traducido = EtiquetasEntorno.traducir(etiqueta.text)
            if (traducido != null) {
                return ObjetoDescrito(
                    nombre = traducido,
                    color = null,
                    posicion = null,
                    confianza = etiqueta.confidence,
                    area = bitmap.width * bitmap.height,
                )
            } else if (!EtiquetasEntorno.esIgnorada(etiqueta.text)) {
                Log.d(TAG_ETIQUETAS, "Sin traducción: ${etiqueta.text} (${etiqueta.confidence})")
            }
        }
        return null
    }

    private fun posicionHorizontal(caja: Rect, anchoImagen: Int): String {
        val centro = caja.exactCenterX() / anchoImagen.toFloat()
        return when {
            centro < 0.33f -> "a la izquierda"
            centro > 0.66f -> "a la derecha"
            else -> "al centro"
        }
    }

    private fun ordenHorizontal(posicion: String?): Int = when (posicion) {
        "a la izquierda" -> 0
        "al centro" -> 1
        "a la derecha" -> 2
        else -> 3
    }

    private fun unir(frases: List<String>): String = when (frases.size) {
        0 -> ""
        1 -> frases[0]
        else -> frases.dropLast(1).joinToString(", ") + " y " + frases.last()
    }

    /** Carga la foto, la reduce si es enorme y la gira según el EXIF para que quede derecha. */
    private fun cargarBitmapOrientado(ruta: String): Bitmap? {
        return try {
            val bordes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(ruta, bordes)
            if (bordes.outWidth <= 0 || bordes.outHeight <= 0) return null

            var muestreo = 1
            while (maxOf(bordes.outWidth, bordes.outHeight) / muestreo > LADO_MAXIMO) muestreo *= 2

            val opciones = BitmapFactory.Options().apply { inSampleSize = muestreo }
            val original = BitmapFactory.decodeFile(ruta, opciones) ?: return null

            val grados = when (
                ExifInterface(ruta).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (grados == 0f) return original

            val matriz = Matrix().apply { postRotate(grados) }
            val girado = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matriz, true)
            if (girado !== original) original.recycle()
            girado
        } catch (e: Exception) {
            null
        }
    }

    /** Recorta la caja del objeto (con un poco de margen) dentro de los límites de la foto. */
    private fun recortar(bitmap: Bitmap, caja: Rect): Bitmap? {
        val margenX = (caja.width() * 0.05f).toInt()
        val margenY = (caja.height() * 0.05f).toInt()
        val izquierda = (caja.left - margenX).coerceIn(0, bitmap.width - 1)
        val arriba = (caja.top - margenY).coerceIn(0, bitmap.height - 1)
        val derecha = (caja.right + margenX).coerceIn(izquierda + 1, bitmap.width)
        val abajo = (caja.bottom + margenY).coerceIn(arriba + 1, bitmap.height)
        val ancho = derecha - izquierda
        val alto = abajo - arriba
        if (ancho < MIN_LADO_RECORTE || alto < MIN_LADO_RECORTE) return null
        return try {
            Bitmap.createBitmap(bitmap, izquierda, arriba, ancho, alto)
        } catch (e: Exception) {
            null
        }
    }

    private fun colorConcordado(recorte: Bitmap, nombre: NombreEs): String? {
        val color = colorPromedio(recorte)
        return EtiquetasEntorno.colorConcordado(color, nombre)
    }

    /** Promedia el color de la parte central del recorte (así pesa menos el fondo). */
    private fun colorPromedio(bitmap: Bitmap): String {
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var muestras = 0
        val desdeX = (bitmap.width * 0.2f).toInt()
        val hastaX = (bitmap.width * 0.8f).toInt().coerceAtLeast(desdeX + 1)
        val desdeY = (bitmap.height * 0.2f).toInt()
        val hastaY = (bitmap.height * 0.8f).toInt().coerceAtLeast(desdeY + 1)
        val pasoX = maxOf(1, (hastaX - desdeX) / 10)
        val pasoY = maxOf(1, (hastaY - desdeY) / 10)

        var y = desdeY
        while (y < hastaY && y < bitmap.height) {
            var x = desdeX
            while (x < hastaX && x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                sumR += Color.red(pixel)
                sumG += Color.green(pixel)
                sumB += Color.blue(pixel)
                muestras++
                x += pasoX
            }
            y += pasoY
        }

        if (muestras == 0) return "desconocido"

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
            (hue < 30f || hue >= 330f) && sat > 0.2f && value < 0.5f -> "café"
            hue < 15f || hue >= 345f -> "rojo"
            hue < 45f -> "naranja"
            hue < 75f -> "amarillo"
            hue < 160f -> "verde"
            hue < 260f -> "azul"
            hue < 320f -> "morado"
            else -> "rosa"
        }
    }

    private companion object {
        const val TAG = "EntornoTiempo"
        const val TAG_ETIQUETAS = "EntornoEtiquetas"
        const val MAX_OBJETOS = 3
        const val LADO_MAXIMO = 1280
        const val MIN_LADO_RECORTE = 32
    }
}