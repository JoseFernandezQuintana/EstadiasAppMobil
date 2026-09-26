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

    /**
     * Cleans text that came from outside (an AI answer, a scanned document) before it is read aloud.
     * A speech engine reads formatting literally ("asterisco asterisco importante asterisco asterisco"),
     * and emojis as their names, so both are dropped and bullets become plain sentences.
     */
    fun forSpeech(text: String): String = text
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1") // [texto](enlace) -> texto
        .replace(Regex("^\\s*[-•*]\\s+", RegexOption.MULTILINE), "") // list bullets
        .replace(Regex("[*_`#>~]+"), " ") // markdown marks
        .replace(Regex("[\\p{So}\\p{Cs}]"), "") // emojis and pictographs
        .replace(Regex("\\s+"), " ")
        .trim()

    /** [fold] with repeated spaces collapsed: "¡Ingresa, el usuario!" -> "ingresa el usuario". */
    fun normalize(text: String): String = fold(text).trim().replace(Regex("\\s+"), " ")
}
