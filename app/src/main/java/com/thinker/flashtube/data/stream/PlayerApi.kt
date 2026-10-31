package com.thinker.flashtube.data.stream

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom

sealed interface PlayerAnswer {
    class Playable(val root: JSONObject, val streamingData: JSONObject) : PlayerAnswer
    class IdentityRefused(val reason: String?, val root: JSONObject? = null) : PlayerAnswer
    class Unplayable(val status: String, val reason: String?) : PlayerAnswer
    class Failed(val httpCode: Int? = null) : PlayerAnswer
}

fun readPlayerAnswer(httpCode: Int, body: String): PlayerAnswer {
    if (httpCode !in 200..299 || body.isEmpty()) return PlayerAnswer.Failed(httpCode)
    val root = try {
        JSONObject(body)
    } catch (_: org.json.JSONException) {
        return PlayerAnswer.Failed(httpCode)
    }
    val playability = root.optJSONObject("playabilityStatus")
    val status = playability?.optString("status").orEmpty()
    if (status.isNotEmpty() && status != "OK") {
        val reason = playability?.optString("reason")?.takeIf { it.isNotBlank() }
        return if (status == "LOGIN_REQUIRED") {
            PlayerAnswer.IdentityRefused(reason)
        } else {
            PlayerAnswer.Unplayable(status, reason)
        }
    }
    val streamingData = root.optJSONObject("streamingData")
        ?: return PlayerAnswer.IdentityRefused(reason = null, root = root)
    return PlayerAnswer.Playable(root, streamingData)
}

class PlayerApi(
    private val http: OkHttpClient,
    private val apiKey: String = "AIzaSyAO_FJ2SlqU8Q4UYfeAyzqW72_V_2wL8Z4",
) {
    suspend fun player(
        videoId: String,
        client: PlayerClient,
        visitorData: String,
    ): PlayerAnswer = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(videoId, client, visitorData)
            val (code, body) = http.newCall(request).execute().use { response ->
                response.code to response.body?.string().orEmpty()
            }
            readPlayerAnswer(code, body).also { log(it, videoId, client) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(STREAM_TAG, "Player[${client.name}] exception videoId=$videoId", e)
            PlayerAnswer.Failed()
        }
    }

    private fun buildRequest(videoId: String, client: PlayerClient, visitorData: String): Request {
        val clientContext = JSONObject().apply {
            put("clientName", client.name)
            put("clientVersion", client.version)
            put("hl", "en")
            put("gl", "US")
            put("utcOffsetMinutes", 0)
            if (visitorData.isNotBlank()) put("visitorData", visitorData)
            client.fields.forEach { (key, value) -> put(key, value) }
        }
        val body = JSONObject().apply {
            put("videoId", videoId)
            put("context", JSONObject().put("client", clientContext))
            if (client.sendsPlaybackNonce) {
                put("contentPlaybackNonce", nonce(16))
                put("t", nonce(12))
            }
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }.toString()

        return Request.Builder()
            .url("https://youtubei.googleapis.com/youtubei/v1/player?key=$apiKey&prettyPrint=false")
            .post(body.toRequestBody(JSON))
            .addHeader("User-Agent", client.userAgent)
            .addHeader("X-Goog-Api-Format-Version", "2")
            .addHeader("X-YouTube-Client-Name", client.id.toString())
            .addHeader("X-YouTube-Client-Version", client.version)
            .addHeader("Origin", "https://www.youtube.com")
            .addHeader("Accept", "application/json")
            .apply { if (visitorData.isNotBlank()) addHeader("X-Goog-Visitor-Id", visitorData) }
            .build()
    }

    private fun log(answer: PlayerAnswer, videoId: String, client: PlayerClient) {
        val what = when (answer) {
            is PlayerAnswer.Playable -> return
            is PlayerAnswer.IdentityRefused ->
                if (answer.root == null) "bot check: ${answer.reason}" else "OK without streamingData"
            is PlayerAnswer.Unplayable -> "playability=${answer.status} reason=${answer.reason}"
            is PlayerAnswer.Failed -> "HTTP ${answer.httpCode ?: "none"} or unreadable body"
        }
        Log.w(STREAM_TAG, "Player[${client.name}] $what videoId=$videoId")
    }

    private fun nonce(length: Int): String =
        buildString(length) { repeat(length) { append(NONCE_ALPHABET[random.nextInt(NONCE_ALPHABET.length)]) } }

    private val random = SecureRandom()

    private companion object {
        val JSON = "application/json".toMediaType()
        const val NONCE_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    }
}
