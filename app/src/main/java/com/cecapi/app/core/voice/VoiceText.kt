package com.cecapi.app.core.voice

import java.text.Normalizer

/** Helpers for comparing what the user said, ignoring case, accents and punctuation. */
object VoiceText {

    /**
     * Lowercases and strips accents/punctuation one character at a time, so every index in the
     * result still lines up with the same index in [text].
     */
    fun fold(text: String): String = buildString(text.length) {
        for (ch in text) {
            val base = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD).firstOrNull { it.isLetterOrDigit() }
            append(base?.lowercaseChar() ?: ' ')
        }
    }

    /** [fold] with repeated spaces collapsed: "¡Ingresa, el usuario!" -> "ingresa el usuario". */
    fun normalize(text: String): String = fold(text).trim().replace(Regex("\\s+"), " ")
}
