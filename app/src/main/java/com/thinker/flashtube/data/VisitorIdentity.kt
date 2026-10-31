package com.thinker.flashtube.data

import android.content.Context
import android.util.Log
import com.thinker.flashtube.Net
import com.thinker.flashtube.data.stream.STREAM_TAG
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * FlashTube's anonymous identity towards YouTube: the `visitorData` token.
 */
class VisitorIdentity(
    context: Context,
    private val quickHttp: OkHttpClient = Net.client,
    private val pageHttp: OkHttpClient = Net.client,
    private val webClientVersion: String = "2.20241028.01.00",
    private val browserUserAgent: String = Net.USER_AGENT,
    private val region: () -> String = { "US" },
) {
    private val prefs by lazy {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    suspend fun prefetch() {
        try {
            current()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(STREAM_TAG, "visitorData prefetch failed: ${e.message}")
        }
    }

    suspend fun current(): String {
        freshCached(System.currentTimeMillis())?.let { return it }
        return mutex.withLock {
            val now = System.currentTimeMillis()
            freshCached(now)?.let { return@withLock it }
            val persistedAt = prefs.getLong(KEY_TOKEN_AT, 0L)
            if (now - persistedAt in 0 until TTL_MS) {
                persisted()?.let {
                    cached = it
                    cachedAt = persistedAt
                    return@withLock it
                }
            }
            val fresh = mint()
            if (!fresh.isNullOrEmpty()) {
                adopt(fresh, now)
                Log.i(STREAM_TAG, "visitorData refreshed (len=${fresh.length})")
                fresh
            } else {
                cached ?: persisted() ?: ""
            }
        }
    }

    fun cachedOrNull(): String? = cached ?: persisted()

    suspend fun replace(refused: String): String? = mutex.withLock {
        cached?.takeIf { it != refused }?.let { return@withLock it }
        cached = null
        cachedAt = 0L
        if (prefs.getString(KEY_TOKEN, null) == refused) {
            prefs.edit().remove(KEY_TOKEN).remove(KEY_TOKEN_AT).apply()
        }
        val fresh = mint()
        if (!fresh.isNullOrEmpty()) {
            adopt(fresh, System.currentTimeMillis())
            Log.i(STREAM_TAG, "visitorData replaced after a refusal")
            fresh
        } else {
            Log.w(STREAM_TAG, "visitorData replacement failed (mint)")
            null
        }
    }

    fun signedBySupersededToken(failedUrl: String?): Boolean {
        val signer = failedUrl?.let(::signerOf) ?: return false
        if (signer == cachedOrNull()) return false
        Log.i(STREAM_TAG, "Playback refused a URL signed under a replaced visitorData; keeping the current one")
        return true
    }

    suspend fun replaceAfterPlaybackFailure(failedUrl: String?) {
        val refused = failedUrl?.let(::signerOf) ?: cachedOrNull() ?: return
        try {
            replace(refused)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(STREAM_TAG, "visitorData replacement after a playback failure failed: ${e.message}")
        }
    }

    fun noteSigned(streamingData: JSONObject, token: String) {
        if (token.isBlank()) return
        val key = streamUrlsOf(streamingData).firstNotNullOfOrNull(::responseKeyOf) ?: return
        synchronized(signers) { signers[key] = token }
    }

    private fun signerOf(url: String): String? {
        val key = responseKeyOf(url) ?: return null
        return synchronized(signers) { signers[key] }
    }

    fun isVetted(token: String): Boolean {
        if (vetted == token) return true
        if (prefs.getString(KEY_VETTED, null) != token) return false
        vetted = token
        return true
    }

    fun markVetted(token: String) {
        vetted = token
        prefs.edit().putString(KEY_VETTED, token).apply()
    }

    private fun freshCached(now: Long): String? = cached?.takeIf { now - cachedAt < TTL_MS }

    private fun persisted(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    private fun adopt(token: String, now: Long) {
        cached = token
        cachedAt = now
        prefs.edit().putString(KEY_TOKEN, token).putLong(KEY_TOKEN_AT, now).apply()
    }

    private suspend fun mint(): String? = withContext(Dispatchers.IO) {
        mintFromApi() ?: mintFromBootstrapPage()
    }

    private fun mintFromApi(): String? = try {
        val body = JSONObject().put(
            "context",
            JSONObject().put(
                "client",
                JSONObject()
                    .put("clientName", "WEB")
                    .put("clientVersion", webClientVersion)
                    .put("hl", "en")
                    .put("gl", region()),
            ),
        ).toString()
        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false")
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("User-Agent", browserUserAgent)
            .build()
        val json = quickHttp.newCall(request).execute().use { it.body?.string().orEmpty() }
        JSONObject(json)
            .optJSONObject("responseContext")
            ?.optString("visitorData")
            ?.replace("%3D", "=")
            ?.replace("%3d", "=")
            ?.takeIf { it.isNotEmpty() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(STREAM_TAG, "visitor_id mint failed: ${e.message}")
        null
    }

    private fun mintFromBootstrapPage(): String? = try {
        val request = Request.Builder()
            .url("https://www.youtube.com/")
            .addHeader("User-Agent", browserUserAgent)
            .addHeader("Accept-Language", "en-US,en;q=0.9")
            .build()
        val html = pageHttp.newCall(request).execute().use { it.body?.string().orEmpty() }
        BOOTSTRAP_TOKEN.find(html)
            ?.groupValues?.get(1)
            ?.replace("\\u003d", "=")
            ?.replace("\\u0026", "&")
            ?.takeIf { it.isNotEmpty() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(STREAM_TAG, "bootstrap visitorData scrape failed: ${e.message}")
        null
    }

    companion object {
        private const val PREFS = "flashtube_visitor_data"
        private const val KEY_TOKEN = "visitor_data"
        private const val KEY_TOKEN_AT = "visitor_data_at"
        private const val KEY_VETTED = "visitor_data_vetted"
        private const val TTL_MS = 6 * 60 * 60 * 1000L
        private val BOOTSTRAP_TOKEN = Regex("\"visitorData\":\"(.*?)\"")

        @Volatile private var cached: String? = null
        @Volatile private var cachedAt: Long = 0L
        @Volatile private var vetted: String? = null
        private val mutex = Mutex()

        private val signers = object : LinkedHashMap<String, String>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) =
                size > MAX_SIGNERS
        }
        private const val MAX_SIGNERS = 512

        @Volatile private var globalInstance: VisitorIdentity? = null

        fun init(context: Context): VisitorIdentity {
            return globalInstance ?: synchronized(this) {
                globalInstance ?: VisitorIdentity(context.applicationContext).also { globalInstance = it }
            }
        }

        fun getInstance(): VisitorIdentity? = globalInstance

        fun cachedOrNull(): String? = globalInstance?.cachedOrNull()

        fun refreshVisitorDataAfterPlaybackFailure(failedUrl: String?) {
            val instance = globalInstance ?: return
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                runCatching { instance.replaceAfterPlaybackFailure(failedUrl) }
            }
        }
    }
}

internal fun responseKeyOf(url: String): String? {
    val parsed = url.toHttpUrlOrNull() ?: return null
    parsed.queryParameter("ei")?.takeIf { it.isNotBlank() }?.let { return it }
    val segments = parsed.pathSegments
    val at = segments.indexOf("ei")
    if (at < 0) return null
    return segments.getOrNull(at + 1)?.takeIf { it.isNotBlank() }
}

private fun streamUrlsOf(streamingData: JSONObject): Sequence<String> = sequence {
    for (key in listOf("adaptiveFormats", "formats")) {
        val formats = streamingData.optJSONArray(key) ?: continue
        for (i in 0 until formats.length()) {
            formats.optJSONObject(i)?.optString("url")?.takeIf { it.isNotBlank() }?.let { yield(it) }
        }
    }
    for (key in listOf("hlsManifestUrl", "dashManifestUrl")) {
        streamingData.optString(key).takeIf { it.isNotBlank() }?.let { yield(it) }
    }
}
