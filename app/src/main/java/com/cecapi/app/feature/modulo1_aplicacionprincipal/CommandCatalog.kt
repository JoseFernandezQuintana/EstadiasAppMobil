package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.voice.VoiceText

/** The spoken "lista de comandos" for each screen, kept in one place so it never drifts from what works. */
object CommandCatalog {

    private val requestPhrases = listOf(
        "lista de comandos", "lista comandos", "que comandos", "comandos disponibles", "que puedo decir",
        "que puedo hacer", "que puedes hacer", "que sabes hacer",
    )

    fun isRequest(spoken: String): Boolean = VoiceText.normalize(spoken).let { text -> requestPhrases.any { it in text } }

    private const val EVERYWHERE =
        "Estado del teléfono, batería, wifi, hora, fecha, año o mes. Sube el volumen o baja el volumen. " +
            "Qué notificaciones tengo. Dime más, para profundizar en lo último que respondí. Silencio, para que me calle un momento. Para, para detenerme del todo."

    const val HOME =
        "Puedes decir: menú, o el nombre de una opción, como cámara, documentos, personalización o configuración. " +
            "Qué hay enfrente, o leer texto. Iniciar sesión. " + EVERYWHERE +
            " Llámate, y un nombre, para ponerme nombre. Repite la solicitud anterior. Cerrar la aplicación."

    const val DASHBOARD =
        "Puedes decir: menú, o el nombre de una opción, como cámara, documentos, personalización o configuración. " +
            "Qué hay enfrente, o leer texto. " + EVERYWHERE +
            " Repite la solicitud anterior. Cerrar sesión. Cerrar la aplicación."

    const val LOGIN =
        "En el inicio de sesión puedes decir: usuario, y luego tu usuario. Contraseña, y luego tu contraseña. " +
            "También todo junto: usuario pepe, contraseña 1234. Ingresar. Crear cuenta. Cancelar, para corregir."
}
