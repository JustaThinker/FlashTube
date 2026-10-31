package com.thinker.flashtube.data

import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioTrackType

enum class VideoDynamicRange {
    SDR,
    HLG,
    HDR10,
}

enum class YouTubeAudioTrackKind {
    ORIGINAL,
    DUBBED,
    AUTO_DUBBED,
    DESCRIPTIVE,
    SECONDARY,
    UNKNOWN,
}

data class YouTubeAudioTrack(
    val id: String,
    val displayName: String,
    val languageTag: String?,
    val kind: YouTubeAudioTrackKind,
    val url: String,
) {
    val isOriginal: Boolean get() = kind == YouTubeAudioTrackKind.ORIGINAL
}

data class VideoQuality(
    val resolution: String,
    val url: String,
    val format: String = "mp4",
    val isDASH: Boolean = false,
    val audioUrl: String? = null,
    val isLive: Boolean = false,
    val sourceAspectRatio: Float? = null,
    val codec: String? = null,
    val dynamicRange: VideoDynamicRange = VideoDynamicRange.SDR,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Int = 30,
) {
    val isHdr: Boolean get() = dynamicRange != VideoDynamicRange.SDR
}

data class VideoSeekPreview(
    val pageUrls: List<String>,
    val frameWidthPx: Int,
    val frameHeightPx: Int,
    val framesPerPageX: Int,
    val framesPerPageY: Int,
    val totalFrameCount: Int,
    val durationPerFrameMs: Long,
)

data class VideoStreamResult(
    val qualities: List<VideoQuality>,
    val seekPreview: VideoSeekPreview? = null,
    val audioTracks: List<YouTubeAudioTrack> = emptyList(),
)

fun parseDirectVideoQualities(
    streamingData: JSONObject,
    includeHdr: Boolean = false,
): List<VideoQuality> {
    fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    val adaptive = streamingData.optJSONArray("adaptiveFormats")?.objects() ?: emptyList()
    val muxed = streamingData.optJSONArray("formats")?.objects() ?: emptyList()

    val sourceAspect = (adaptive + muxed)
        .filter { it.optInt("width") > 0 && it.optInt("height") > 0 }
        .maxByOrNull { it.optInt("height") }
        ?.let { it.optInt("width").toFloat() / it.optInt("height").toFloat() }

    fun dynamicRange(format: JSONObject): VideoDynamicRange =
        youtubeVideoDynamicRange(
            qualityLabel = format.optString("qualityLabel"),
            transferCharacteristics = format.optJSONObject("colorInfo")
                ?.optString("transferCharacteristics"),
        )

    fun isHdrFormat(format: JSONObject): Boolean =
        dynamicRange(format) != VideoDynamicRange.SDR

    fun labelHeight(label: String): Int =
        label.takeWhile(Char::isDigit).toIntOrNull() ?: 0

    fun labelFps(label: String): Int =
        label.substringAfter("p", "").takeWhile(Char::isDigit).toIntOrNull() ?: 30

    fun isVodFormat(format: JSONObject): Boolean {
        val url = format.optString("url")
        return url.isNotEmpty() &&
            format.optString("contentLength").isNotBlank() &&
            !url.contains("live=1")
    }

    val hlsManifestUrl = streamingData.optString("hlsManifestUrl").takeIf(String::isNotBlank)
    if (hlsManifestUrl != null && (adaptive + muxed).none(::isVodFormat)) {
        fun liveEntry(label: String) = VideoQuality(
            resolution = label,
            url = hlsManifestUrl,
            format = "HLS",
            isDASH = true,
            isLive = true,
            sourceAspectRatio = sourceAspect,
        )

        val ladder = adaptive
            .filter {
                it.optString("mimeType").startsWith("video/") && !isHdrFormat(it)
            }
            .mapNotNull { it.optString("qualityLabel").takeIf(String::isNotEmpty) }
            .distinct()
            .sortedWith(
                compareByDescending<String>(::labelHeight)
                    .thenByDescending(::labelFps)
            )

        return listOf(liveEntry("Auto")) + ladder.map(::liveEntry)
    }

    val directAudioFormats = adaptive.filter {
        it.optString("mimeType").startsWith("audio/") &&
            it.optString("url").isNotEmpty()
    }
    val typedAudio = directAudioFormats.map { it to directAudioTrackType(it) }
    val hasAlternateAudioTracks = typedAudio.any {
        it.second != null && it.second != AudioTrackType.ORIGINAL
    }
    val originalAudio = typedAudio.filter { it.second == AudioTrackType.ORIGINAL }
        .map { it.first }
        .ifEmpty { typedAudio.filter { it.second == null }.map { it.first } }
    val bestAudioUrl = originalAudio
        .maxWithOrNull(
            compareBy(
                { if (it.optString("mimeType").contains("mp4a")) 1 else 0 },
                { it.optInt("bitrate") },
            )
        )
        ?.optString("url")
        ?.takeIf(String::isNotEmpty)

    fun codecRank(mimeType: String): Int = when {
        mimeType.contains("avc1") -> 3
        mimeType.contains("vp9") || mimeType.contains("vp09") -> 2
        else -> 1
    }

    fun container(mimeType: String): String =
        mimeType.substringAfter("video/").substringBefore(';').ifEmpty { "mp4" }

    fun codec(mimeType: String): String? =
        mimeType.substringAfter("codecs=\"", "")
            .substringBefore('"')
            .takeIf(String::isNotBlank)

    val qualities = mutableListOf<VideoQuality>()

    if (bestAudioUrl != null) {
        adaptive
            .filter {
                it.optString("mimeType").startsWith("video/") &&
                    it.optString("url").isNotEmpty() &&
                    it.optString("qualityLabel").isNotEmpty() &&
                    (includeHdr || !isHdrFormat(it))
            }
            .groupBy {
                normalizedVideoQualityLabel(it.optString("qualityLabel")) to dynamicRange(it)
            }
            .forEach { (identity, formats) ->
                val (label, range) = identity
                val best = formats.maxWithOrNull(
                    compareBy({ codecRank(it.optString("mimeType")) }, { it.optInt("bitrate") })
                ) ?: return@forEach
                qualities += VideoQuality(
                    resolution = label,
                    url = best.optString("url"),
                    format = container(best.optString("mimeType")),
                    isDASH = false,
                    audioUrl = bestAudioUrl,
                    sourceAspectRatio = sourceAspect,
                    codec = codec(best.optString("mimeType")),
                    dynamicRange = range,
                    width = best.optInt("width"),
                    height = best.optInt("height"),
                    frameRate = best.optInt("fps"),
                )
            }
    }

    if (!hasAlternateAudioTracks || bestAudioUrl == null) {
        muxed.forEach { format ->
            val range = dynamicRange(format)
            if (!includeHdr && range != VideoDynamicRange.SDR) return@forEach
            val label = normalizedVideoQualityLabel(format.optString("qualityLabel"))
            val url = format.optString("url")
            if (label.isNotEmpty() && url.isNotEmpty()) {
                qualities += VideoQuality(
                    resolution = label,
                    url = url,
                    format = container(format.optString("mimeType")),
                    isDASH = false,
                    sourceAspectRatio = sourceAspect,
                    codec = codec(format.optString("mimeType")),
                    dynamicRange = range,
                    width = format.optInt("width"),
                    height = format.optInt("height"),
                    frameRate = format.optInt("fps"),
                )
            }
        }
    }

    return deduplicateVideoQualityVariants(qualities)
}

fun parseDirectAudioTracks(streamingData: JSONObject): List<YouTubeAudioTrack> {
    val adaptive = streamingData.optJSONArray("adaptiveFormats") ?: return emptyList()
    val byTrack = linkedMapOf<String, MutableList<JSONObject>>()
    for (index in 0 until adaptive.length()) {
        val format = adaptive.optJSONObject(index) ?: continue
        if (!format.optString("mimeType").startsWith("audio/")) continue
        if (format.optString("url").isEmpty() || format.optBoolean("isDrc")) continue
        val id = format.optJSONObject("audioTrack")?.optString("id")
            ?.takeIf(String::isNotBlank) ?: continue
        byTrack.getOrPut(id) { mutableListOf() } += format
    }
    if (byTrack.size < 2) return emptyList()

    return byTrack.mapNotNull { (id, formats) ->
        val best = formats.maxWithOrNull(
            compareBy(
                { if (it.optString("mimeType").contains("mp4a")) 1 else 0 },
                { it.optInt("bitrate") },
            )
        ) ?: return@mapNotNull null
        val track = best.optJSONObject("audioTrack")
        val tags = decodeYouTubeXtags(best.optString("xtags"))
        YouTubeAudioTrack(
            id = id,
            displayName = track?.optString("displayName")?.takeIf(String::isNotBlank) ?: id,
            languageTag = tags["lang"] ?: id.substringBefore('.').takeIf(String::isNotBlank),
            kind = youTubeAudioTrackKind(
                acont = tags["acont"],
                isDefault = track?.optBoolean("audioIsDefault") == true,
            ),
            url = best.optString("url"),
        )
    }.sortedForMenu()
}

fun List<YouTubeAudioTrack>.sortedForMenu(): List<YouTubeAudioTrack> =
    sortedWith(compareBy({ !it.isOriginal }, { it.displayName.lowercase() }))

fun youTubeAudioTrackKind(acont: String?, isDefault: Boolean): YouTubeAudioTrackKind =
    when (acont) {
        "original" -> YouTubeAudioTrackKind.ORIGINAL
        "dubbed" -> YouTubeAudioTrackKind.DUBBED
        "dubbed-auto" -> YouTubeAudioTrackKind.AUTO_DUBBED
        "descriptive" -> YouTubeAudioTrackKind.DESCRIPTIVE
        "secondary" -> YouTubeAudioTrackKind.SECONDARY
        else -> if (isDefault) YouTubeAudioTrackKind.ORIGINAL else YouTubeAudioTrackKind.UNKNOWN
    }

fun decodeYouTubeXtags(xtags: String?): Map<String, String> {
    if (xtags.isNullOrBlank()) return emptyMap()
    val bytes = runCatching {
        java.util.Base64.getUrlDecoder().decode(xtags.trimEnd('='))
    }.getOrNull() ?: return emptyMap()

    fun fields(buffer: ByteArray): List<Pair<Int, ByteArray>> {
        val out = mutableListOf<Pair<Int, ByteArray>>()
        var position = 0
        fun varint(): Long? {
            var shift = 0
            var value = 0L
            while (position < buffer.size && shift < 64) {
                val byte = buffer[position++].toInt() and 0xff
                value = value or ((byte and 0x7f).toLong() shl shift)
                if (byte and 0x80 == 0) return value
                shift += 7
            }
            return null
        }
        while (position < buffer.size) {
            val key = varint() ?: break
            when ((key and 7).toInt()) {
                0 -> varint() ?: break
                2 -> {
                    val length = varint()?.toInt() ?: break
                    if (length < 0 || length > buffer.size - position) break
                    out += (key ushr 3).toInt() to buffer.copyOfRange(position, position + length)
                    position += length
                }
                else -> break
            }
        }
        return out
    }

    return runCatching {
        fields(bytes).filter { it.first == 1 }.mapNotNull { (_, pair) ->
            val entry = fields(pair).toMap()
            val key = entry[1]?.toString(Charsets.UTF_8)?.takeIf(String::isNotBlank)
            val value = entry[2]?.toString(Charsets.UTF_8)
            if (key != null && value != null) key to value else null
        }.toMap()
    }.getOrDefault(emptyMap())
}

private fun directAudioTrackType(format: JSONObject): AudioTrackType? {
    val xtags = format.optString("xtags").takeIf(String::isNotBlank) ?: return null
    return runCatching { YoutubeParsingHelper.extractAudioTrackType(xtags) }.getOrNull()
}

fun youtubeVideoDynamicRange(
    qualityLabel: String,
    transferCharacteristics: String?,
): VideoDynamicRange {
    val transfer = transferCharacteristics.orEmpty().uppercase()
    return when {
        transfer.contains("ARIB_STD_B67") || transfer.contains("HLG") ->
            VideoDynamicRange.HLG
        transfer.contains("2084") || qualityLabel.contains("HDR", ignoreCase = true) ->
            VideoDynamicRange.HDR10
        else -> VideoDynamicRange.SDR
    }
}

fun normalizedVideoQualityLabel(label: String): String = label
    .replace(Regex("""\s+HDR(?:10\+?)?\b.*$""", RegexOption.IGNORE_CASE), "")
    .trim()

val VideoQuality.resolutionHeight: Int
    get() = resolution.takeWhile(Char::isDigit).toIntOrNull() ?: 0

val VideoQuality.resolutionFrameRate: Int
    get() = resolution.substringAfter('p', "").takeWhile(Char::isDigit).toIntOrNull() ?: 30

fun deduplicateVideoQualityVariants(qualities: List<VideoQuality>): List<VideoQuality> {
    val seen = HashSet<String>()
    val result = mutableListOf<VideoQuality>()
    for (q in qualities) {
        val key = "${q.resolution}:${q.format}:${q.isDASH}:${q.dynamicRange}"
        if (seen.add(key)) {
            result.add(q)
        }
    }
    return result
}

fun parseStoryboardSeekPreview(root: JSONObject): VideoSeekPreview? {
    val storyboards = root.optJSONObject("storyboards")
        ?: root.optJSONObject("playerConfig")?.optJSONObject("storyboards")
        ?: return null
    val spec = storyboards.optJSONObject("playerStoryboardSpecRenderer")?.optString("spec")
        ?: return null

    val parts = spec.split("|")
    if (parts.size < 2) return null
    val baseUrl = parts[0]
    val frameSpec = parts.last()
    val specParts = frameSpec.split("#")
    if (specParts.size < 8) return null

    val frameWidth = specParts[0].toIntOrNull() ?: return null
    val frameHeight = specParts[1].toIntOrNull() ?: return null
    val count = specParts[2].toIntOrNull() ?: return null
    val cols = specParts[3].toIntOrNull() ?: return null
    val rows = specParts[4].toIntOrNull() ?: return null
    val durationMs = specParts[5].toLongOrNull() ?: return null
    val sigh = specParts[7]

    val framesPerPage = cols * rows
    val numPages = if (framesPerPage > 0) (count + framesPerPage - 1) / framesPerPage else 1
    val urls = (0 until numPages).map { page ->
        baseUrl.replace("\$L", "2")
            .replace("\$N", "M$page")
            .replace("\$M", "M$page") + "?sigh=$sigh"
    }

    return VideoSeekPreview(
        pageUrls = urls,
        frameWidthPx = frameWidth,
        frameHeightPx = frameHeight,
        framesPerPageX = cols,
        framesPerPageY = rows,
        totalFrameCount = count,
        durationPerFrameMs = durationMs,
    )
}
