package com.cecapi.app.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/** A short summary of a topic and, if the person asks for more, the rest of it. */
data class WikipediaResult(val short: String, val rest: String?, val title: String)

/**
 * A second source of answers when there is internet but the AI is off (its providers do not allow minors,
 * so it stays off until Pp decides otherwise): Wikipedia in Spanish, which needs no key and no account.
 * Two calls, both documented, free endpoints: `action=opensearch` finds the closest article title for what
 * the person said, then the REST summary endpoint returns its first paragraph.
 */
@Singleton
class WikipediaLookup @Inject constructor() {

    suspend fun search(query: String): WikipediaResult? = withContext(Dispatchers.IO) {
        try {
            val title = findTitle(query) ?: return@withContext null
            val extract = fetchSummary(title) ?: return@withContext null
            val cut = cutAtSentence(extract, SHORT_ANSWER_CHARS)
            WikipediaResult(
                short = cut,
                rest = extract.substring(cut.length).trim().ifBlank { null },
                title = title,
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun findTitle(query: String): String? {
        val url = "https://es.wikipedia.org/w/api.php?action=opensearch&format=json&limit=1&namespace=0&search=" +
            URLEncoder.encode(query, "UTF-8")
        val body = get(url) ?: return null
        // ["query", ["Título encontrado"], [...], [...]]
        val titles = JSONArray(body).optJSONArray(1) ?: return null
        return titles.optString(0).takeIf { it.isNotBlank() }
    }

    private fun fetchSummary(title: String): String? {
        val url = "https://es.wikipedia.org/api/rest_v1/page/summary/" + URLEncoder.encode(title, "UTF-8")
        val body = get(url) ?: return null
        val json = JSONObject(body)
        if (json.optString("type") == "disambiguation") return null // "¿cuál de estos?" needs a screen, not voice
        return json.optString("extract").trim().ifBlank { null }
    }

    private fun get(urlString: String): String? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            // Wikipedia's own etiquette (meta.wikimedia.org/wiki/User-Agent_policy) asks for a real identifier.
            setRequestProperty("User-Agent", "CECAPI-App/1.0 (accesibilidad; proyecto de estadía)")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Cuts at the sentence end at or after [minChars], so the short answer never stops mid-thought. */
    private fun cutAtSentence(text: String, minChars: Int): String {
        if (text.length <= minChars) return text
        val end = text.indexOf(". ", minChars).let { if (it == -1) text.indexOf('.', minChars) else it }
        return if (end == -1) text else text.substring(0, end + 1)
    }

    private companion object {
        const val SHORT_ANSWER_CHARS = 220
    }
}
