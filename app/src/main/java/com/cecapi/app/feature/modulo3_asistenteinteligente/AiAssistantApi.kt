package com.cecapi.app.feature.modulo3_asistenteinteligente

import com.cecapi.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

interface AiAssistantApi {
    suspend fun ask(pregunta: String, contexto: List<String>): Result<String>
}

/**
 * Talks to a small backend YOU control — never directly to a third-party LLM
 * provider's API from the phone. Shipping any provider's API key inside an
 * Android APK means anyone who decompiles it gets the key — there is no
 * client-side secret storage that fixes this, so the request must be
 * relayed through a server this team (Módulo 3) owns and deploys.
 *
 * Recommended provider for that backend: **Gemini API** (Google AI Studio) —
 * it has a genuinely free tier with no time limit, which matters for a
 * student project with no budget. This is a backend-only choice: swapping to
 * another provider later never touches this file or any other Android code,
 * since the app only ever talks to your own endpoint below.
 *
 * Expected backend contract (build this endpoint — e.g. Python + FastAPI —
 * calling the Gemini API server-side):
 *   POST {AI_PROXY_BASE_URL}
 *   body:     {"pregunta": "...", "contexto": ["turno 1", "turno 2", ...]}
 *   response: {"respuesta": "..."}
 */
@Singleton
class ProxyAiAssistantApi @Inject constructor() : AiAssistantApi {

    override suspend fun ask(pregunta: String, contexto: List<String>): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(BuildConfig.AI_PROXY_BASE_URL)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }

                val body = JSONObject().apply {
                    put("pregunta", pregunta)
                    put("contexto", JSONArray(contexto))
                }

                connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }

                if (connection.responseCode !in 200..299) {
                    return@withContext Result.failure(
                        IllegalStateException("El asistente no respondió (código ${connection.responseCode})."),
                    )
                }

                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val respuesta = JSONObject(responseText).getString("respuesta")
                Result.success(respuesta)
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
}
