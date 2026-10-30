package com.thinker.flashtube.data

import android.util.Log
import com.thinker.flashtube.Account
import com.thinker.flashtube.Net
import com.thinker.flashtube.YouTube
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

data class ResolvedStreamVariant(
    val url: String,
    val mimeType: String,
    val qualityLabel: String?,
    val height: Int,
    val width: Int,
    val fps: Int,
    val bitrate: Long,
    val isHls: Boolean = false,
    val isHdr: Boolean = false,
    val loudnessDb: Float? = null
)

data class ResolvedStreamLadder(
    val videoId: String,
    val title: String,
    val durationSec: Long,
    val videoStreams: List<ResolvedStreamVariant>,
    val audioStreams: List<ResolvedStreamVariant>,
    val expiresAtMs: Long,
    val isLive: Boolean = false
)

data class PickedStreams(
    val video: ResolvedStreamVariant?,
    val audio: ResolvedStreamVariant?,
    val isLive: Boolean = false,
    val hlsUrl: String? = null
)

object VideoStreamResolutionCache {
    private const val TAG = "VideoStreamResCache"
    private val cache = ConcurrentHashMap<String, ResolvedStreamLadder>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<ResolvedStreamLadder>>()

    /** Return only a still-valid ladder; media-source construction must never fetch it. */
    fun getIfFresh(videoId: String): ResolvedStreamLadder? {
        val cached = cache[videoId] ?: return null
        if (cached.expiresAtMs > System.currentTimeMillis()) return cached
        cache.remove(videoId, cached)
        return null
    }

    fun getOrLoad(videoId: String, loader: suspend (String) -> ResolvedStreamLadder): suspend () -> ResolvedStreamLadder = {
        val now = System.currentTimeMillis()
        val cached = cache[videoId]
        if (cached != null && cached.expiresAtMs > now) {
            cached
        } else {
            var deferred = inFlight[videoId]
            var isOwner = false
            if (deferred == null) {
                val newDeferred = CompletableDeferred<ResolvedStreamLadder>()
                val existing = inFlight.putIfAbsent(videoId, newDeferred)
                if (existing == null) {
                    deferred = newDeferred
                    isOwner = true
                } else {
                    deferred = existing
                }
            }

            if (isOwner) {
                try {
                    val result = loader(videoId)
                    cache[videoId] = result
                    deferred.complete(result)
                    result
                } catch (e: Exception) {
                    deferred.completeExceptionally(e)
                    throw e
                } finally {
                    inFlight.remove(videoId, deferred)
                }
            } else {
                deferred.await()
            }
        }
    }

    fun clear() {
        Log.i(TAG, "Clearing all resolved stream ladders from cache")
        cache.clear()
    }

    fun invalidate(videoId: String) {
        Log.i(TAG, "Invalidating resolved stream ladder for $videoId")
        cache.remove(videoId)
    }
}

object VideoStreamResolver {
    private const val TAG = "VideoStreamResolver"
    private val prefetcher = java.util.concurrent.Executors.newFixedThreadPool(4)

    fun prefetch(videoId: String) {
        prefetcher.execute {
            runCatching {
                kotlinx.coroutines.runBlocking { resolve(videoId) }
            }
        }
    }

    suspend fun resolve(videoId: String): ResolvedStreamLadder = withContext(Dispatchers.IO) {
        VideoStreamResolutionCache.getOrLoad(videoId) { id ->
            val visionOs = resolveVisionOS(id)
            val fallback = visionOs ?: resolveFallback(id)
            if (fallback.isLive && fallback.videoStreams.none { it.isHls }) {
                resolveLiveHls(id) ?: fallback
            } else {
                fallback
            }
        }()
    }

    fun pickBestStreams(
        ladder: ResolvedStreamLadder,
        targetQualityHeight: Int?,
        isMetered: Boolean = false,
        preferHdr: Boolean = false
    ): PickedStreams {
        if (ladder.isLive || ladder.videoStreams.any { it.isHls }) {
            val hlsUrl = ladder.videoStreams.firstOrNull { it.isHls }?.url ?: ladder.videoStreams.firstOrNull()?.url
            return PickedStreams(video = null, audio = null, isLive = true, hlsUrl = hlsUrl)
        }

        var videoStreams = ladder.videoStreams
        if (preferHdr && videoStreams.any { it.isHdr }) {
            val hdrVariants = videoStreams.filter { it.isHdr }
            if (hdrVariants.isNotEmpty()) {
                videoStreams = hdrVariants
            }
        }

        val audioStreams = ladder.audioStreams

        val bestVideo = if (videoStreams.isEmpty()) {
            null
        } else if (targetQualityHeight != null) {
            videoStreams.minByOrNull { kotlin.math.abs(it.height - targetQualityHeight) } ?: videoStreams.first()
        } else {
            // Auto quality ceiling: 720p on metered (cellular) networks to save data, 1080p on unmetered (Wi-Fi)
            val autoCeiling = if (isMetered) 720 else 1080
            videoStreams.filter { it.height <= autoCeiling }.maxByOrNull { it.height }
                ?: videoStreams.minByOrNull { it.height }
                ?: videoStreams.first()
        }

        val bestAudio = audioStreams.maxByOrNull { it.bitrate } ?: audioStreams.firstOrNull()

        return PickedStreams(
            video = bestVideo,
            audio = bestAudio,
            isLive = false
        )
    }

    private fun resolveVisionOS(videoId: String): ResolvedStreamLadder? {
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
                put("videoId", videoId)
            }

            VisitorIdentity.cachedOrNull()?.let { visitor ->
                clientContext.getJSONObject("client").put("visitorData", visitor)
            }

            val requestBuilder = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3 Safari/605.1.15")

            VisitorIdentity.cachedOrNull()?.let { visitor ->
                requestBuilder.addHeader("X-Goog-Visitor-Id", visitor)
            }

            Net.client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val root = JSONObject(bodyStr)
                parseVisionOsPayload(videoId, root)
            }
        } catch (e: Exception) {
            Log.w(TAG, "VISIONOS stream resolution failed for $videoId", e)
            null
        }
    }

    private fun parseVisionOsPayload(videoId: String, root: JSONObject): ResolvedStreamLadder? {
        val videoDetails = root.optJSONObject("videoDetails") ?: return null
        val title = videoDetails.optString("title", "")
        val durationSec = videoDetails.optString("lengthSeconds", "0").toLongOrNull() ?: 0L
        val isLive = videoDetails.optBoolean("isLive", false) ||
            videoDetails.optBoolean("isLiveContent", false)

        val streamingData = root.optJSONObject("streamingData") ?: return null
        val hlsManifestUrl = streamingData.optString("hlsManifestUrl").takeIf { it.isNotBlank() }

        val videoStreams = mutableListOf<ResolvedStreamVariant>()
        val audioStreams = mutableListOf<ResolvedStreamVariant>()

        var earliestExpireMs = System.currentTimeMillis() + 4 * 3600 * 1000L // default 4h

        if (!hlsManifestUrl.isNullOrEmpty()) {
            videoStreams.add(
                ResolvedStreamVariant(
                    url = hlsManifestUrl,
                    mimeType = "application/x-mpegURL",
                    qualityLabel = "Live HLS",
                    height = 1080,
                    width = 1920,
                    fps = 30,
                    bitrate = 0L,
                    isHls = true
                )
            )
        } else {
            val formats = (streamingData.optJSONArray("formats") ?: JSONArray())
            val adaptiveFormats = (streamingData.optJSONArray("adaptiveFormats") ?: JSONArray())

            for (i in 0 until formats.length()) {
                parseFormatItem(formats.optJSONObject(i), videoStreams, audioStreams)?.let { exp ->
                    if (exp < earliestExpireMs) earliestExpireMs = exp
                }
            }
            for (i in 0 until adaptiveFormats.length()) {
                parseFormatItem(adaptiveFormats.optJSONObject(i), videoStreams, audioStreams)?.let { exp ->
                    if (exp < earliestExpireMs) earliestExpireMs = exp
                }
            }
        }

        if (videoStreams.isEmpty() && audioStreams.isEmpty()) return null

        val cacheExpiresAtMs = (earliestExpireMs - 5 * 60 * 1000L).coerceAtLeast(System.currentTimeMillis() + 60 * 1000L)

        return ResolvedStreamLadder(
            videoId = videoId,
            title = title,
            durationSec = durationSec,
            videoStreams = videoStreams.sortedByDescending { it.height },
            audioStreams = audioStreams.sortedByDescending { it.bitrate },
            expiresAtMs = cacheExpiresAtMs,
            isLive = isLive
        )
    }

    private fun parseFormatItem(
        format: JSONObject?,
        videoStreams: MutableList<ResolvedStreamVariant>,
        audioStreams: MutableList<ResolvedStreamVariant>
    ): Long? {
        if (format == null) return null
        val url = format.optString("url").takeIf { it.isNotBlank() }
            ?: parseCipher(format.optString("signatureCipher").ifBlank { format.optString("cipher") })
            ?: return null

        val mimeType = format.optString("mimeType", "")
        val qualityLabel = format.optString("qualityLabel", "")
        val height = format.optInt("height", 0)
        val width = format.optInt("width", 0)
        val fps = format.optInt("fps", 30)
        val bitrate = format.optLong("bitrate", 0L)
        val itag = format.optInt("itag", 0)
        val isHdr = itag in 330..337 || mimeType.contains("vp9.2", ignoreCase = true)

        val loudnessDb = format.optDouble("loudnessDb", Double.NaN).let {
            if (it.isNaN()) null else it.toFloat()
        }

        val variant = ResolvedStreamVariant(
            url = url,
            mimeType = mimeType,
            qualityLabel = qualityLabel,
            height = height,
            width = width,
            fps = fps,
            bitrate = bitrate,
            isHls = false,
            isHdr = isHdr,
            loudnessDb = loudnessDb
        )

        if (mimeType.startsWith("video/")) {
            videoStreams.add(variant)
        } else if (mimeType.startsWith("audio/")) {
            audioStreams.add(variant)
        }

        return extractExpireMs(url)
    }

    private fun parseCipher(cipher: String): String? {
        if (cipher.isBlank()) return null
        return try {
            val params = cipher.split('&').associate {
                val parts = it.split('=')
                if (parts.size == 2) URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8") else "" to ""
            }
            params["url"]
        } catch (e: Exception) {
            null
        }
    }

    private fun extractExpireMs(url: String): Long? {
        return try {
            val expireSec = extractQueryParam(url, "expire")?.toLongOrNull() ?: return null
            expireSec * 1000L
        } catch (e: Exception) {
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

    private fun resolveFallback(videoId: String): ResolvedStreamLadder {
        Log.i(TAG, "Resolving stream via NewPipe fallback for $videoId")
        val info = StreamInfo.getInfo(ServiceList.YouTube, YouTube.watchUrl(videoId))
        val videoStreams = (info.videoOnlyStreams + info.videoStreams).map {
            ResolvedStreamVariant(
                url = it.content ?: it.url.orEmpty(),
                mimeType = it.format?.mimeType ?: "video/mp4",
                qualityLabel = it.quality,
                height = it.height,
                width = it.width,
                fps = it.fps,
                bitrate = it.bitrate.toLong(),
                isHls = false
            )
        }
        val audioStreams = info.audioStreams.map {
            ResolvedStreamVariant(
                url = it.content ?: it.url.orEmpty(),
                mimeType = it.format?.mimeType ?: "audio/mp4",
                qualityLabel = "Audio",
                height = 0,
                width = 0,
                fps = 0,
                bitrate = it.bitrate.toLong(),
                isHls = false
            )
        }

        val hlsUrl = info.hlsUrl?.takeIf { it.isNotBlank() }
        val streamsWithHls = if (hlsUrl != null) {
            videoStreams + ResolvedStreamVariant(
                url = hlsUrl,
                mimeType = "application/x-mpegURL",
                qualityLabel = "Live HLS",
                height = 1080,
                width = 1920,
                fps = 30,
                bitrate = 0L,
                isHls = true,
            )
        } else videoStreams

        val isLive = info.streamType == StreamType.LIVE_STREAM
        val expiresAtMs = System.currentTimeMillis() + 2 * 3600 * 1000L

        return ResolvedStreamLadder(
            videoId = videoId,
            title = info.name.orEmpty(),
            durationSec = info.duration,
            videoStreams = streamsWithHls.sortedByDescending { it.height },
            audioStreams = audioStreams.sortedByDescending { it.bitrate },
            expiresAtMs = expiresAtMs,
            isLive = isLive || hlsUrl != null
        )
    }

    /** The iOS player client exposes HLS for active live streams; request it only when needed. */
    private fun resolveLiveHls(videoId: String): ResolvedStreamLadder? {
        return try {
            val clientContext = JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "IOS")
                    put("clientVersion", "19.09.3")
                    put("deviceModel", "iPhone16,2")
                    put("platform", "MOBILE")
                    put("osName", "iPhone")
                    put("osVersion", "17.3.1")
                })
            }
            val payload = JSONObject().apply {
                put("context", clientContext)
                put("videoId", videoId)
            }
            VisitorIdentity.cachedOrNull()?.let { visitor ->
                clientContext.getJSONObject("client").put("visitorData", visitor)
            }
            val requestBuilder = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", "com.google.ios.youtube/19.09.3 (iPhone; CPU iPhone OS 17_3_1 like Mac OS X)")
            VisitorIdentity.cachedOrNull()?.let { visitor ->
                requestBuilder.addHeader("X-Goog-Visitor-Id", visitor)
            }
            Net.client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val ladder = parseVisionOsPayload(videoId, JSONObject(body)) ?: return null
                if (ladder.videoStreams.none { it.isHls }) return null
                ladder.copy(isLive = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "iOS HLS resolution failed for live video $videoId", e)
            null
        }
    }
}
