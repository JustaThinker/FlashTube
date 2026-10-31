package com.thinker.flashtube.data.stream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Asks googlevideo whether it will serve a resolved stream to the end, before
 * the player is given it.
 */
class StreamProbe(
    private val http: OkHttpClient,
    private val browserUserAgent: String,
) {
    /**
     * True or false when googlevideo said so (206 or 403); null when it could
     * not be asked or the answer means nothing.
     */
    suspend fun servesLastByte(url: String): Boolean? = withContext(Dispatchers.IO) {
        val offset = lastByteOffset(url) ?: return@withContext null
        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", userAgentForStreamUrl(url, browserUserAgent))
                .addHeader("Range", "bytes=$offset-$offset")
                .build()
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    206 -> true
                    403 -> false
                    else -> null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}

private const val MIN_PROBED_DURATION_S = 120.0

/**
 * The offset of [url]'s last byte, or null when probing it would prove nothing.
 */
internal fun lastByteOffset(url: String): Long? {
    val parsed = url.toHttpUrlOrNull() ?: return null
    val length = parsed.queryParameter("clen")?.toLongOrNull()?.takeIf { it > 0L } ?: return null
    val durationS = parsed.queryParameter("dur")?.toDoubleOrNull() ?: return null
    if (durationS < MIN_PROBED_DURATION_S) return null
    return length - 1
}
