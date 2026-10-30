package com.thinker.flashtube.data

import android.content.Context
import android.util.Log
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import com.thinker.flashtube.Net
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** A bounded, transient cache for ordinary progressive YouTube video and audio bytes. */
object VideoPlaybackCache {
    private const val TAG = "VideoPlaybackCache"
    private const val MAX_CACHE_BYTES = 512L * 1024L * 1024L
    private const val CACHE_DIR = "video-playback-cache"
    private val cacheRef = AtomicReference<SimpleCache?>()
    private val factoryRef = AtomicReference<DataSource.Factory?>()

    /** Cache playback bytes on disk; if cache setup fails, keep playback working directly. */
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

    /** Stable across refreshed signed URLs and unique to each selected rendition. */
    fun cacheKey(videoId: String, streamRole: String, url: String, fallbackVariant: String): String {
        val itag = Regex("(?:[?&])itag=([^&#]+)", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.getOrNull(1)
        // Keep the rendition in the key even when YouTube provides an itag.
        val identity = listOfNotNull(itag, fallbackVariant.takeIf { it.isNotBlank() })
            .joinToString(":")
        return "flashtube-video:$videoId:$streamRole:$identity"
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
