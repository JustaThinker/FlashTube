package com.thinker.flashtube.data

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.thinker.flashtube.Net
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

/**
 * Custom Media3 DataSource for YouTube progressive streams.
 * Bypasses YouTube's ~32 KB/s rate-limiting on open-ended GET requests by breaking fetches into
 * 10 MB bounded byte-range chunks (`Range: bytes=start-(start + 10MB - 1)`).
 */
@OptIn(UnstableApi::class)
class ChunkedStreamDataSource(
    private val okHttpClient: OkHttpClient = Net.client
) : BaseDataSource(true) {

    companion object {
        private const val TAG = "ChunkedStreamDS"
        private const val CHUNK_SIZE_BYTES = 10 * 1024 * 1024L // 10 MB

        fun uaForPlaybackUri(uriString: String): String {
            val c = extractQueryParam(uriString, "c")?.uppercase()
            return when (c) {
                "VISIONOS" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3 Safari/605.1.15"
                "ANDROID", "ANDROID_VR" -> "com.google.android.youtube/19.09.37 (Linux; U; Android 11; US)"
                "IOS" -> "com.google.ios.youtube/19.09.3 (iPhone; CPU iPhone OS 17_3_1 like Mac OS X)"
                else -> Net.USER_AGENT
            }
        }

        private fun extractQueryParam(url: String, name: String): String? {
            return try {
                val query = url.substringAfter('?', "")
                query.split('&').mapNotNull {
                    val parts = it.split('=')
                    if (parts.size == 2 && parts[0] == name) java.net.URLDecoder.decode(parts[1], "UTF-8") else null
                }.firstOrNull()
            } catch (e: Exception) {
                null
            }
        }
    }

    class Factory(
        private val okHttpClient: OkHttpClient = Net.client
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return ChunkedStreamDataSource(okHttpClient)
        }
    }

    private var dataSpec: DataSpec? = null
    private var uri: Uri? = null
    private var opened = false

    private var bytesToReadTotal: Long = C.LENGTH_UNSET.toLong()
    private var totalBytesRead: Long = 0L

    private var currentChunkStart: Long = 0L
    private var currentChunkEnd: Long = 0L // inclusive
    private var currentResponse: Response? = null
    private var currentInputStream: InputStream? = null
    private var currentChunkBytesRead: Long = 0L
    private var currentChunkLength: Long = 0L

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        this.uri = dataSpec.uri
        transferInitializing(dataSpec)

        val urlString = dataSpec.uri.toString()
        val isGoogleVideo = dataSpec.uri.host?.contains("googlevideo.com") == true
        val isHls = urlString.contains(".m3u8")

        totalBytesRead = 0L
        currentChunkStart = dataSpec.position
        bytesToReadTotal = dataSpec.length

        if (!isGoogleVideo || isHls) {
            // Unchunked request
            openDirectChunk(dataSpec.position, if (bytesToReadTotal != C.LENGTH_UNSET.toLong()) dataSpec.position + bytesToReadTotal - 1 else null)
        } else {
            // 10 MB chunked progressive request
            val targetEnd = if (bytesToReadTotal != C.LENGTH_UNSET.toLong()) {
                min(dataSpec.position + CHUNK_SIZE_BYTES - 1, dataSpec.position + bytesToReadTotal - 1)
            } else {
                dataSpec.position + CHUNK_SIZE_BYTES - 1
            }
            openDirectChunk(dataSpec.position, targetEnd)
        }

        opened = true
        transferStarted(dataSpec)
        return bytesToReadTotal
    }

    private fun openDirectChunk(start: Long, endInclusive: Long?) {
        closeCurrentChunk()
        val spec = dataSpec ?: throw IOException("DataSpec is null")
        val urlString = spec.uri.toString()
        val ua = uaForPlaybackUri(urlString)

        val requestBuilder = Request.Builder()
            .url(urlString)
            .addHeader("User-Agent", ua)

        val rangeHeader = if (endInclusive != null) {
            "bytes=$start-$endInclusive"
        } else {
            "bytes=$start-"
        }
        requestBuilder.addHeader("Range", rangeHeader)

        VisitorIdentity.cachedOrNull()?.let { visitorToken ->
            requestBuilder.addHeader("X-Goog-Visitor-Id", visitorToken)
        }

        val request = requestBuilder.build()
        val response = okHttpClient.newCall(request).execute()

        if (response.code == 403) {
            Log.w(TAG, "HTTP 403 received for stream URL. Triggering VisitorIdentity token refresh.")
            VisitorIdentity.refreshVisitorDataAfterPlaybackFailure(urlString)
            throw IOException("HTTP 403 Forbidden on stream chunk $rangeHeader")
        }

        if (!response.isSuccessful && response.code != 206 && response.code != 200) {
            response.close()
            throw IOException("HTTP ${response.code} error on range $rangeHeader: ${response.message}")
        }

        val body = response.body ?: throw IOException("Null response body for range $rangeHeader")
        currentResponse = response
        currentInputStream = body.byteStream()
        currentChunkStart = start
        currentChunkEnd = endInclusive ?: (start + body.contentLength() - 1)
        currentChunkLength = body.contentLength()
        currentChunkBytesRead = 0L

        if (bytesToReadTotal == C.LENGTH_UNSET.toLong() && response.code == 206) {
            val contentRange = response.header("Content-Range")
            if (contentRange != null && contentRange.contains("/")) {
                val totalStr = contentRange.substringAfter('/')
                totalStr.toLongOrNull()?.let { totalLen ->
                    bytesToReadTotal = totalLen - start
                }
            }
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (!opened) return C.RESULT_END_OF_INPUT

        if (bytesToReadTotal != C.LENGTH_UNSET.toLong() && totalBytesRead >= bytesToReadTotal) {
            return C.RESULT_END_OF_INPUT
        }

        var stream = currentInputStream
        if (stream == null || (currentChunkLength > 0 && currentChunkBytesRead >= currentChunkLength)) {
            val isGoogleVideo = uri?.host?.contains("googlevideo.com") == true
            val isHls = uri.toString().contains(".m3u8")
            if (!isGoogleVideo || isHls) {
                return C.RESULT_END_OF_INPUT
            }

            // Fetch next 10 MB chunk
            val nextStart = currentChunkEnd + 1
            if (bytesToReadTotal != C.LENGTH_UNSET.toLong() && (nextStart - (dataSpec?.position ?: 0L)) >= bytesToReadTotal) {
                return C.RESULT_END_OF_INPUT
            }

            val nextEnd = if (bytesToReadTotal != C.LENGTH_UNSET.toLong()) {
                min(nextStart + CHUNK_SIZE_BYTES - 1, (dataSpec?.position ?: 0L) + bytesToReadTotal - 1)
            } else {
                nextStart + CHUNK_SIZE_BYTES - 1
            }

            try {
                openDirectChunk(nextStart, nextEnd)
                stream = currentInputStream ?: return C.RESULT_END_OF_INPUT
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open next chunk at $nextStart", e)
                throw e
            }
        }

        val bytesToRead = if (bytesToReadTotal != C.LENGTH_UNSET.toLong()) {
            min(length.toLong(), bytesToReadTotal - totalBytesRead).toInt()
        } else {
            length
        }

        val readCount = try {
            stream.read(buffer, offset, bytesToRead)
        } catch (e: IOException) {
            throw e
        }

        if (readCount < 0) {
            if (currentChunkLength > 0 && currentChunkBytesRead < currentChunkLength) {
                throw EOFException("Unexpected EOF inside 10 MB chunk (read $currentChunkBytesRead of $currentChunkLength)")
            }
            return C.RESULT_END_OF_INPUT
        }

        currentChunkBytesRead += readCount
        totalBytesRead += readCount
        bytesTransferred(readCount)
        return readCount
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (opened) {
            opened = false
            closeCurrentChunk()
            transferEnded()
        }
    }

    private fun closeCurrentChunk() {
        try {
            currentInputStream?.close()
        } catch (e: Exception) {
            // Ignore stream close exceptions
        }
        currentInputStream = null
        currentResponse?.close()
        currentResponse = null
    }
}
