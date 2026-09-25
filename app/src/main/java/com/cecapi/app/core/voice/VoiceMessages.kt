package com.cecapi.app.core.voice

/** Phrases the assistant says in more than one place, so they stay identical everywhere. */
object VoiceMessages {
    const val WAKE_PROMPT = "¿En qué te puedo ayudar?"
    const val WAKE_PROMPT_USTED = "¿En qué le puedo ayudar?"

    /** Said when a phrase is heard outside the app and only the app itself could act on it. */
    const val OPEN_APP = "Eso lo hago dentro de la aplicación. Ábrela para continuar."

    const val MIC_DENIED =
        "Sin el permiso del micrófono no puedo escucharte. Actívalo en los ajustes de la aplicación."

    /** For modules that store their history per user, when nobody has signed in. */
    const val NEEDS_LOGIN =
        "Para usar este módulo necesitas iniciar sesión. Vuelve al inicio y di iniciar sesión."
}
