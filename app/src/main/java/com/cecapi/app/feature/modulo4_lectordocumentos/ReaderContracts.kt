package com.cecapi.app.feature.modulo4_lectordocumentos

/**
 * Resultado de procesar una foto en el Lector de Documentos.
 * El Repository lo produce y el ViewModel decide qué decir en cada caso.
 */
sealed class OcrOutcome {

    /** Se reconoció texto. [documentoId] es null cuando nadie inició sesión: se lee, pero no se guarda. */
    data class Exito(
        val documentoId: Long?,
        val parrafos: List<String>,
    ) : OcrOutcome() {
        val textoCompleto: String get() = parrafos.joinToString("\n\n")
    }

    /** La foto está demasiado oscura para leerla. No se guarda nada. */
    object PocaLuz : OcrOutcome()

    /** La foto salió movida o desenfocada. No se guarda nada. */
    object Borrosa : OcrOutcome()

    /** La foto se ve bien, pero no contiene texto suficiente. No se guarda nada. */
    object SinTexto : OcrOutcome()

    /** Falla inesperada (archivo dañado, error de ML Kit, etc.). */
    data class Error(val causa: Throwable) : OcrOutcome()
}
