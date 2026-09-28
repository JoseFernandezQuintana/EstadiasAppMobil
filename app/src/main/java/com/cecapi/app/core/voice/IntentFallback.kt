package com.cecapi.app.core.voice

import javax.inject.Inject
import javax.inject.Singleton

/** What the AI backend sent back for a phrase the app did not understand. */
data class AiReply(
    /** A command the app already knows ("sube el volumen"): it is run as if the person had said it. */
    val command: String? = null,
    /** A short answer to read aloud. Kept brief on purpose: the person hears it and cannot skim. */
    val answer: String? = null,
    /** True when there is much more to say; the app offers "dime más". */
    val hasMore: Boolean = false,
)

/**
 * Where the AI team plugs in as the "cerebro temporal". When the assistant does not understand a phrase
 * and there is internet, [resolver] gets the phrase (and whether the person asked to go deeper) and returns
 * either a known command or a short answer. Returning null means "I could not help either".
 * No resolver is registered yet.
 */
@Singleton
class IntentFallback @Inject constructor() {
    @Volatile
    var resolver: (suspend (text: String, deep: Boolean) -> AiReply?)? = null

    /** The last question that received an answer, so "dime más" can ask for the deep version of it. */
    @Volatile
    var lastQuestion: String? = null

    /** The question to go deeper on if [spoken] is "dime más" / "explícame a fondo" and there is one; else null. */
    fun deepQuestionFor(spoken: String): String? {
        if (resolver == null) return null
        val text = VoiceText.normalize(spoken)
        val wantsMore = DEEP_PHRASES.let { phrases -> VoiceText.hasAny(text, phrases) }
        return if (wantsMore) lastQuestion else null
    }

    private companion object {
        val DEEP_PHRASES = listOf(
            "dime mas", "cuentame mas", "explicame mas", "quiero saber mas", "a fondo", "en detalle",
            "profundiza", "amplia", "investiga",
        )
    }
}
