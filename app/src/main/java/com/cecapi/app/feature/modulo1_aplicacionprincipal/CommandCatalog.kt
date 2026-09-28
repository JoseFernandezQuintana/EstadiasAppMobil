package com.cecapi.app.feature.modulo1_aplicacionprincipal

import com.cecapi.app.core.voice.VoiceText

/** The spoken "lista de comandos" for each screen, kept in one place so it never drifts from what works. */
object CommandCatalog {

    private val requestPhrases = listOf(
        "lista de comandos", "lista comandos", "que comandos", "comandos disponibles", "que puedo decir",
        "que puedo hacer", "que puedes hacer", "que sabes hacer",
    )

    fun isRequest(spoken: String): Boolean = VoiceText.normalize(spoken).let { text -> requestPhrases.any { it in text } }

    /**
     * Said right after a screen introduces itself, so the person knows every screen has its own list.
     * [module] is how the screen is called aloud: "cámara", "personalización"...
     */
    fun hint(module: String): String = "Di lista de comandos de $module para escuchar lo que puedes decir."

    private const val EVERYWHERE =
        "Estado del teléfono, batería, wifi, hora, fecha, año o mes. Sube el volumen o baja el volumen. " +
            "Pantalla negra, para ver todo en negro, y pantalla normal, para volver. Brillo mínimo, y brillo normal. " +
            "Qué notificaciones tengo. Dime más, para profundizar en lo último que respondí. Silencio, para que me calle un momento. Para, para detenerme del todo."

    const val HOME =
        "Puedes decir: menú, o el nombre de una opción, como cámara, documentos, actividades, chats, personalización o configuración. " +
            "Qué hay enfrente, o leer texto. Iniciar sesión. " + EVERYWHERE +
            " Llámate, y un nombre, para ponerme nombre. Repite la solicitud anterior. Cerrar la aplicación."

    const val DASHBOARD =
        "Puedes decir: menú, o el nombre de una opción, como cámara, documentos, actividades, chats, personalización o configuración. " +
            "Qué hay enfrente, o leer texto. " + EVERYWHERE +
            " Repite la solicitud anterior. Cerrar sesión. Cerrar la aplicación."

    const val LOGIN =
        "En el inicio de sesión puedes decir: usuario, y luego tu usuario. Contraseña, y luego tu contraseña. " +
            "También todo junto: usuario pepe, contraseña 1234. Ingresar. Crear cuenta. Cancelar, para corregir."

    const val CAMERA =
        "En la cámara puedes decir: leer texto, para leer un papel, un cartel o una etiqueta. " +
            "Qué hay enfrente, para que te describa lo que ve la cámara. Atrás, para volver al menú. " +
            "Lista de comandos, para repetir esta lista. " + EVERYWHERE

    const val READER =
        "En el lector de texto puedes decir: toma la foto, para leer lo que tienes enfrente. " +
            "Repite, o lee otra vez, para volver a leer desde el principio. " +
            "Siguiente párrafo, o párrafo anterior, para moverte por el texto. " +
            "Pausa, para detener la lectura, y continúa, para seguir. " +
            "Otra foto, para empezar con otro papel. " +
            "Qué hay enfrente, para cambiar a describir lo que ve la cámara. " +
            "Atrás, para volver a la cámara. Lista de comandos, para repetir esta lista. " + EVERYWHERE

    const val ENVIRONMENT =
        "En la descripción del entorno puedes decir: qué hay enfrente, o toma la foto, para que te describa lo que ve. " +
            "Repite, o dilo otra vez, para escuchar la última descripción. " +
            "Leer texto, para cambiar al lector de texto. " +
            "Atrás, para volver a la cámara. Lista de comandos, para repetir esta lista. " + EVERYWHERE

    const val DOCUMENTS =
        "En documentos puedes decir: el nombre de una plantilla, para elegirla. " +
            "Mientras respondes las preguntas, di tu respuesta con normalidad. " +
            "Repite la pregunta, para escucharla otra vez. Cancelar, para elegir otra plantilla. " +
            "Atrás, para volver al menú. Lista de comandos, para repetir esta lista. " + EVERYWHERE

    const val ACTIVITIES =
        "En actividades escuchas un sonido y dices de dónde viene: izquierda, derecha, centro, cerca, lejos, " +
            "de izquierda a derecha o de derecha a izquierda, y en el nivel tres también enfrente o atrás. " +
            "En vibración dices si fue corto, largo o mixto. " +
            "Repite, para escuchar otra vez. Siguiente, para el siguiente ejercicio. " +
            "Nivel uno, nivel dos o nivel tres, para elegir el nivel. Vibración, o sonidos, para cambiar de actividad. " +
            "Volver, para regresar al menú. Lista de comandos, para repetir esta lista. " + EVERYWHERE

    const val CHATS =
        "En chats puedes decir: lee el último, para escuchar la conversación más reciente. " +
            "Siguiente, o anterior, para moverte entre conversaciones. Repite, para escucharla otra vez. " +
            "Cuántos chats tengo. Borra este chat, o borra todos los chats; te pido que confirmes con sí o no. " +
            "Atrás, para volver al menú. Lista de comandos, para repetir esta lista. " + EVERYWHERE

    const val PERSONALIZATION =
        "En personalización puedes decir: háblame de tú, o háblame de usted. " +
            "Llámame, y un nombre, para que te salude así. Llámate, y un nombre, para ponerme nombre. " +
            "Otra voz, para probar la siguiente voz, o voz anterior. " +
            "Más rápido, más lento, más grave o más agudo, para cambiar cómo hablo. " +
            "Activa o desactiva los sonidos y las vibraciones. Vibración suave, normal o fuerte. " +
            "Prueba de voz, para escucharme. Atrás, para volver al menú. " +
            "Lista de comandos, para repetir esta lista. " + EVERYWHERE

    const val SETTINGS =
        "En configuración puedes decir: sube el volumen o baja el volumen. " +
            "Activa los avisos, o desactiva los avisos, para que te diga cuando llega una notificación. " +
            "Da acceso a notificaciones, para abrir los ajustes de Android y que pueda leerlas. " +
            "Activa o desactiva escuchar fuera de la aplicación. " +
            "Cuánto espacio tengo, para el reporte de almacenamiento. Libera espacio, para borrar las fotos de más de un mes; te pido que confirmes con sí o no. " +
            "Activa o desactiva el modo simple. " +
            "Cerrar sesión. Atrás, para volver al menú. Lista de comandos, para repetir esta lista. " + EVERYWHERE
}
