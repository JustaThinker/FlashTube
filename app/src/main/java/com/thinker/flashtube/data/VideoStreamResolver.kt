package com.thinker.flashtube.data

import android.content.Context
import android.util.Log
import com.thinker.flashtube.Net
import com.thinker.flashtube.StreamCache
import com.thinker.flashtube.data.stream.BotCheckVerdict
import com.thinker.flashtube.data.stream.PlayerApi
import com.thinker.flashtube.data.stream.PlayerSession
import com.thinker.flashtube.data.stream.STREAM_TAG
import com.thinker.flashtube.data.stream.StreamProbe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamType
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
    val loudnessDb: Float? = null,
    val isOriginalAudio: Boolean = false,
    val audioTrackName: String? = null,
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

object VideoStreamResolver {
    private const val TAG = "VideoStreamResolver"
    private val prefetcher = java.util.concurrent.Executors.newFixedThreadPool(4)
    private val prefetching = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var sessionInstance: PlayerSession? = null

    fun getPlayerSession(context: Context): PlayerSession {
        return sessionInstance ?: synchronized(this) {
            sessionInstance ?: run {
                val identity = VisitorIdentity.init(context)
                val probe = StreamProbe(Net.client, Net.USER_AGENT)
                val api = PlayerApi(Net.client)
                PlayerSession(identity, api, probe).also { sessionInstance = it }
            }
        }
    }

    fun prefetch(videoId: String) = prefetch(com.thinker.flashtube.FlashTubeApplication.appContext, videoId)

    fun prefetch(context: Context, videoId: String) {
        if (!prefetching.add(videoId)) return
        prefetcher.execute {
            try {
                runCatching { runBlocking { resolve(context, videoId) } }
            } finally {
                prefetching.remove(videoId)
            }
        }
    }

    suspend fun getVideoStreamResult(
        videoId: String,
        includeHdr: Boolean = false
    ): VideoStreamResult = getVideoStreamResult(com.thinker.flashtube.FlashTubeApplication.appContext, videoId, includeHdr)

    suspend fun getVideoStreamResult(
        context: Context,
        videoId: String,
        includeHdr: Boolean = false
    ): VideoStreamResult = VideoStreamResolutionCache.getOrResolve(videoId, includeHdr) {
        resolveVideoStreamResult(context, videoId, includeHdr)
    }

    suspend fun resolve(videoId: String): ResolvedStreamLadder = resolve(com.thinker.flashtube.FlashTubeApplication.appContext, videoId)

    suspend fun resolve(context: Context, videoId: String): ResolvedStreamLadder = withContext(Dispatchers.IO) {
        val result = getVideoStreamResult(context, videoId)
        val videoVariants = result.qualities.map {
            ResolvedStreamVariant(
                url = it.url,
                mimeType = if (it.format.equals("HLS", ignoreCase = true)) "application/x-mpegURL" else "video/${it.format}",
                qualityLabel = it.resolution,
                height = it.height,
                width = it.width,
                fps = it.frameRate,
                bitrate = 0L,
                isHls = it.format.equals("HLS", ignoreCase = true),
                isHdr = it.isHdr,
            )
        }
        val audioVariants = result.qualities.filter { it.audioUrl != null }.map {
            ResolvedStreamVariant(
                url = it.audioUrl!!,
                mimeType = "audio/mp4",
                qualityLabel = "Audio",
                height = 0,
                width = 0,
                fps = 0,
                bitrate = 128000L,
                isOriginalAudio = true
            )
        }.distinctBy { it.url }

        val expiresAtMs = System.currentTimeMillis() + 4 * 3600 * 1000L
        ResolvedStreamLadder(
            videoId = videoId,
            title = "",
            durationSec = 0L,
            videoStreams = videoVariants.sortedByDescending { it.height },
            audioStreams = audioVariants,
            expiresAtMs = expiresAtMs,
            isLive = result.qualities.any { it.isLive }
        )
    }

    private suspend fun resolveVideoStreamResult(
        context: Context,
        videoId: String,
        includeHdr: Boolean = false,
    ): VideoStreamResult = withContext(Dispatchers.IO) {
        val playerSession = getPlayerSession(context)
        val direct = runCatching {
            kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                playerSession.visionOsVetted(videoId) { streamingData ->
                    val adaptive = streamingData.optJSONArray("adaptiveFormats")
                    if (adaptive != null) {
                        for (i in 0 until adaptive.length()) {
                            val format = adaptive.optJSONObject(i) ?: continue
                            if (format.optString("mimeType").startsWith("audio/")) {
                                val url = format.optString("url")
                                if (url.isNotEmpty()) return@visionOsVetted url
                            }
                        }
                    }
                    null
                }
            }
        }.getOrNull()

        direct?.answer?.let { answer ->
            val qualities = parseDirectVideoQualities(answer.streamingData, includeHdr)
            if (qualities.isNotEmpty()) {
                BotCheckVerdict.clearOnSuccess()
                Log.i(
                    STREAM_TAG,
                    "Video qualities via visionOS: ${qualities.size} for $videoId" +
                        qualities.count(VideoQuality::isHdr).let { if (it > 0) " (HDR=$it)" else "" },
                )
                val audioTracks = if (qualities.any(VideoQuality::isLive)) {
                    emptyList()
                } else {
                    parseDirectAudioTracks(answer.streamingData)
                }
                val seekPreview = runCatching { parseStoryboardSeekPreview(answer.root) }.getOrNull()
                return@withContext VideoStreamResult(qualities, seekPreview, audioTracks)
            }
            Log.w(STREAM_TAG, "visionOS answered with no usable formats for $videoId")
        }

        var newPipeBotChecked = false
        try {
            val extracted = kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                getVideoStreamsFromNewPipe(videoId)
            }
            if (extracted != null && extracted.qualities.isNotEmpty()) {
                BotCheckVerdict.clearOnSuccess()
                Log.i(
                    STREAM_TAG,
                    "Video qualities via NewPipe fallback: ${extracted.qualities.size} for $videoId",
                )
                return@withContext extracted
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(
                STREAM_TAG,
                "NewPipe quality resolution failed, falling back to direct InnerTube",
                e,
            )
        }

        try {
            val streamingData = kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                playerSession.nativeFallback(videoId, newPipeBotChecked)?.streamingData
            }
            if (streamingData != null) {
                val qualities = parseDirectVideoQualities(streamingData, includeHdr)
                return@withContext VideoStreamResult(qualities)
            }
            VideoStreamResult(emptyList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(STREAM_TAG, "Error getting video stream qualities", e)
            VideoStreamResult(emptyList())
        }
    }

    private fun getVideoStreamsFromNewPipe(videoId: String): VideoStreamResult {
        val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        extractor.fetchPage()

        val videoOnlyStreams = extractor.videoOnlyStreams
        val muxedStreams = extractor.videoStreams
        val isLiveStream = extractor.streamType == StreamType.LIVE_STREAM ||
            extractor.streamType == StreamType.AUDIO_LIVE_STREAM
        val sourceAspect = (videoOnlyStreams + muxedStreams)
            .filter { it.width > 0 && it.height > 0 }
            .maxByOrNull { it.height }
            ?.let { it.width.toFloat() / it.height.toFloat() }

        val extractedAudioStreams = extractor.audioStreams
        val qualities = mutableListOf<VideoQuality>()
        val manifest = if (isLiveStream) {
            extractor.hlsUrl?.takeIf { it.isNotBlank() }?.let { "HLS" to it }
        } else {
            extractor.dashMpdUrl?.takeIf { it.isNotBlank() }?.let { "DASH" to it }
                ?: extractor.hlsUrl?.takeIf { it.isNotBlank() }?.let { "HLS" to it }
        }
        manifest?.let { (format, url) ->
            qualities.add(
                VideoQuality(
                    resolution = if (format == "HLS") "Auto (HLS)" else "Auto (Best)",
                    url = url,
                    format = format,
                    isDASH = true,
                    isLive = isLiveStream,
                    sourceAspectRatio = sourceAspect,
                )
            )
        }

        if (isLiveStream) return VideoStreamResult(qualities)

        val bestAudio = extractedAudioStreams
            .asSequence()
            .filter { it.isUrl }
            .maxWithOrNull(
                compareBy<AudioStream>(
                    {
                        if (it.format?.suffix.equals("m4a", ignoreCase = true) ||
                            it.codec?.contains("mp4a", ignoreCase = true) == true
                        ) 1 else 0
                    },
                    { it.averageBitrate },
                )
            )

        if (bestAudio != null) {
            videoOnlyStreams.asSequence()
                .filter { it.isUrl }
                .mapNotNull { stream ->
                    stream.resolution?.takeIf { it.isNotBlank() }?.let { resolution ->
                        VideoQuality(
                            resolution = resolution,
                            url = stream.content,
                            format = stream.format?.suffix ?: "mp4",
                            isDASH = false,
                            audioUrl = bestAudio.content,
                            sourceAspectRatio = sourceAspect,
                            codec = stream.codec,
                        )
                    }
                }
                .forEach(qualities::add)
        }

        muxedStreams.asSequence()
            .filter { it.isUrl }
            .mapNotNull { stream ->
                stream.resolution?.takeIf { it.isNotBlank() }?.let { resolution ->
                    VideoQuality(
                        resolution = resolution,
                        url = stream.content,
                        format = stream.format?.suffix ?: "mp4",
                        isDASH = false,
                        sourceAspectRatio = sourceAspect,
                        codec = stream.codec,
                    )
                }
            }
            .forEach(qualities::add)

        return VideoStreamResult(deduplicateVideoQualityVariants(qualities))
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
            val autoCeiling = if (isMetered) 720 else 1080
            videoStreams.filter { it.height <= autoCeiling }.maxByOrNull { it.height }
                ?: videoStreams.minByOrNull { it.height }
                ?: videoStreams.first()
        }

        val bestAudio = audioStreams
            .filter { it.isOriginalAudio }
            .maxByOrNull { it.bitrate }
            ?: audioStreams.maxByOrNull { it.bitrate }

        return PickedStreams(
            video = bestVideo,
            audio = bestAudio,
            isLive = false
        )
    }
}
