package com.thinker.flashtube.data

import android.content.Context
import android.util.Log
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.thinker.flashtube.Net
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

enum class VideoPlaybackCacheStream(val keyPart: String) {
    MUXED("muxed"),
    VIDEO("video"),
    AUDIO("audio"),
}

private const val VIDEO_PLAYBACK_CACHE_PREFIX = "video-playback:"
private const val OPAQUE_PLAYBACK_CACHE_PREFIX = "playback-uri:"
private val ITAG_QUERY_PARAMETER = Regex("(?:[?&])itag=([^&#]+)", RegexOption.IGNORE_CASE)
private val LAST_MODIFIED_QUERY_PARAMETER = Regex("(?:[?&])lmt=([^&#]+)", RegexOption.IGNORE_CASE)
private val CONTENT_LENGTH_QUERY_PARAMETER = Regex("(?:[?&])clen=([^&#]+)", RegexOption.IGNORE_CASE)
private val AUDIO_TRACK_QUERY_PARAMETER = Regex("(?:[?&])xtags=([^&#]+)", RegexOption.IGNORE_CASE)
private val NON_KEY_CHARACTER = Regex("[^a-z0-9._-]+")

fun videoPlaybackCacheKey(
    videoId: String,
    stream: VideoPlaybackCacheStream,
    sourceUrl: String,
    fallbackVariant: String,
): String {
    val itag = ITAG_QUERY_PARAMETER.valueIn(sourceUrl)
    val rendition = itag
        ?.let {
            buildString {
                append("itag-").append(it)
                LAST_MODIFIED_QUERY_PARAMETER.valueIn(sourceUrl)?.let { value ->
                    append("-lmt-").append(value)
                }
                CONTENT_LENGTH_QUERY_PARAMETER.valueIn(sourceUrl)?.let { value ->
                    append("-clen-").append(value)
                }
                AUDIO_TRACK_QUERY_PARAMETER.valueIn(sourceUrl)?.let { value ->
                    append("-track-").append(value.sha256Prefix())
                }
            }
        }
        ?: run {
            val variant = fallbackVariant
                .lowercase()
                .replace(NON_KEY_CHARACTER, "-")
                .trim('-')
                .takeIf { it.isNotBlank() }
                ?: "default"
            "$variant-url-${sourceUrl.sha256Prefix()}"
        }
    return "$VIDEO_PLAYBACK_CACHE_PREFIX$videoId:${stream.keyPart}:$rendition"
}

private fun Regex.valueIn(url: String): String? =
    find(url)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }

private fun String.sha256Prefix(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .take(8)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

fun isVideoPlaybackCacheKey(key: String): Boolean =
    key.startsWith(VIDEO_PLAYBACK_CACHE_PREFIX)

fun opaquePlaybackCacheKey(uri: String): String =
    "$OPAQUE_PLAYBACK_CACHE_PREFIX$uri"

fun isUncacheablePlaybackUrl(url: String): Boolean {
    val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
    if (afterScheme.isEmpty()) return false
    val authorityEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
    val authority = if (authorityEnd < 0) afterScheme else afterScheme.take(authorityEnd)
    val rest = if (authorityEnd < 0) "" else afterScheme.substring(authorityEnd)
    val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
    val path = rest.substringBefore('?').substringBefore('#')
    if (path.endsWith(".m3u8", ignoreCase = true) || path.endsWith(".mpd", ignoreCase = true)) {
        return true
    }
    val googleVideo = host == "googlevideo.com" || host.endsWith(".googlevideo.com")
    return googleVideo && path.isNotEmpty() && !rest.contains('?')
}

/** A bounded, transient cache for ordinary progressive YouTube video and audio bytes. */
object VideoPlaybackCache {
    private const val TAG = "VideoPlaybackCache"
    private const val MAX_CACHE_BYTES = 512L * 1024L * 1024L
    private const val CACHE_DIR = "video-playback-cache"
    private val cacheRef = AtomicReference<SimpleCache?>()
    private val factoryRef = AtomicReference<DataSource.Factory?>()

    fun dataSourceFactory(context: Context): DataSource.Factory {
        factoryRef.get()?.let { return it }
        val created = runCatching {
            val cache = getOrCreateCache(context.applicationContext) ?: return Net.streamDataSourceFactory
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(Net.streamDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }.getOrElse {
            Log.w(TAG, "Video cache unavailable; using direct playback", it)
            return Net.streamDataSourceFactory
        }
        factoryRef.compareAndSet(null, created)
        return factoryRef.get() ?: created
    }

    fun cacheKey(videoId: String, streamRole: String, url: String, fallbackVariant: String): String {
        val stream = if (streamRole == "audio") VideoPlaybackCacheStream.AUDIO else VideoPlaybackCacheStream.VIDEO
        return videoPlaybackCacheKey(videoId, stream, url, fallbackVariant)
    }

    @Synchronized
    private fun getOrCreateCache(context: Context): SimpleCache? {
        cacheRef.get()?.let { return it }
        return runCatching {
            val cache = SimpleCache(
                File(context.cacheDir, CACHE_DIR),
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                StandaloneDatabaseProvider(context),
            )
            if (cacheRef.compareAndSet(null, cache)) cache else {
                cache.release()
                cacheRef.get()
            }
        }.onFailure { Log.w(TAG, "Could not initialize video cache", it) }.getOrNull()
    }
}
