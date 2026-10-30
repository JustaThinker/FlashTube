package com.thinker.flashtube.data

import android.util.Log
import com.thinker.flashtube.Account
import com.thinker.flashtube.Net
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages YouTube's visitor identity (visitorData / X-Goog-Visitor-Id token),
 * bot-check circuit breaker, and automatic token rotation upon 403 HTTP errors.
 */
object VisitorIdentity {
    private const val TAG = "VisitorIdentity"
    private val cachedVisitorData = AtomicReference<String?>(null)
    private val circuitBreakerUntilMs = AtomicLong(0L)

    fun cachedOrNull(): String? {
        return cachedVisitorData.get() ?: Account.visitorData()
    }

    fun updateCached(newToken: String?) {
        if (!newToken.isNullOrBlank()) {
            cachedVisitorData.set(newToken)
        }
    }

    fun isCircuitBreakerActive(): Boolean {
        return System.currentTimeMillis() < circuitBreakerUntilMs.get()
    }

    /**
     * Called when a .googlevideo.com media request returns HTTP 403.
     * Extracts the 'ei' parameter from the failed media URL and requests a fresh visitor token.
     */
    fun refreshVisitorDataAfterPlaybackFailure(failedUrl: String) {
        if (isCircuitBreakerActive()) {
            Log.w(TAG, "Circuit breaker active. Skipping visitorData refresh attempt.")
            return
        }

        val eiParam = extractQueryParam(failedUrl, "ei")
        Log.i(TAG, "Refreshing visitorData token after 403 error (ei parameter: $eiParam)")

        val newVisitorToken = fetchFreshVisitorToken(eiParam)
        if (!newVisitorToken.isNullOrBlank()) {
            cachedVisitorData.set(newVisitorToken)
            Log.i(TAG, "Successfully acquired fresh visitorData: $newVisitorToken")
            VideoStreamResolutionCache.clear()
        } else {
            Log.w(TAG, "Failed to refresh visitorData. Triggering 3-minute circuit breaker.")
            circuitBreakerUntilMs.set(System.currentTimeMillis() + 3 * 60 * 1000L)
        }
    }

    private fun fetchFreshVisitorToken(eiParam: String?): String? {
        return try {
            val clientContext = JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "VISIONOS")
                    put("clientVersion", "1.0.3")
                    put("platform", "OSX")
                    put("osName", "Macintosh")
                    put("osVersion", "14.3.0")
                })
            }
            val payload = JSONObject().apply {
                put("context", clientContext)
                if (!eiParam.isNullOrBlank()) {
                    put("ei", eiParam)
                }
            }

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/visitor_id")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3 Safari/605.1.15")
                .build()

            Net.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val json = JSONObject(bodyStr)
                json.optJSONObject("responseContext")?.optString("visitorData")
                    ?: json.optString("visitorData").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching fresh visitor token", e)
            null
        }
    }

    private fun extractQueryParam(url: String, name: String): String? {
        return try {
            val query = url.substringAfter('?', "")
            query.split('&').mapNotNull {
                val parts = it.split('=')
                if (parts.size == 2 && parts[0] == name) URLDecoder.decode(parts[1], "UTF-8") else null
            }.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
