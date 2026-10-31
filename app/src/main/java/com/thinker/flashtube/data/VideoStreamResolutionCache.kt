package com.thinker.flashtube.data

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object VideoStreamResolutionCache {
    private const val TAG = "VideoStreamResCache"
    private const val MAX_ENTRIES = 16
    private const val FALLBACK_TTL_MS = 15 * 60 * 1000L
    private const val EXPIRY_SAFETY_MS = 5 * 60 * 1000L

    private data class Entry(
        val result: VideoStreamResult,
        val expiresAtMs: Long,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val epoch = AtomicLong(0L)
    private val lock = Any()
    private val active = mutableMapOf<String, Deferred<VideoStreamResult>>()
    private val cached = object : LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Entry>,
        ): Boolean = size > MAX_ENTRIES
    }

    suspend fun getOrResolve(
        videoId: String,
        includeHdr: Boolean = false,
        resolver: suspend () -> VideoStreamResult,
    ): VideoStreamResult {
        val cacheKey = if (includeHdr) "$videoId:hdr" else videoId
        var shouldStart = false
        val deferred = synchronized(lock) {
            val now = System.currentTimeMillis()
            cached[cacheKey]?.let { entry ->
                if (entry.expiresAtMs > now) {
                    return entry.result
                }
                cached.remove(cacheKey)
            }
            active[cacheKey] ?: run {
                val startEpoch = epoch.get()
                scope.async(start = CoroutineStart.LAZY) {
                    val result = resolver()
                    currentCoroutineContext().ensureActive()
                    val expiresAtMs = streamResultExpiryMs(
                        result = result,
                        nowMs = System.currentTimeMillis(),
                        fallbackTtlMs = FALLBACK_TTL_MS,
                        expirySafetyMs = EXPIRY_SAFETY_MS,
                    )
                    if (result.qualities.isNotEmpty() &&
                        expiresAtMs > System.currentTimeMillis() &&
                        epoch.get() == startEpoch
                    ) {
                        synchronized(lock) {
                            if (epoch.get() == startEpoch) {
                                cached[cacheKey] = Entry(result, expiresAtMs)
                            }
                        }
                    }
                    result
                }.also { created ->
                    active[cacheKey] = created
                    created.invokeOnCompletion {
                        synchronized(lock) {
                            if (active[cacheKey] === created) active.remove(cacheKey)
                        }
                    }
                    shouldStart = true
                }
            }
        }
        if (shouldStart) deferred.start()
        return deferred.await()
    }

    fun invalidate(videoId: String) {
        Log.i(TAG, "Invalidating resolved stream ladder for $videoId")
        val activeResolutions = synchronized(lock) {
            cached.remove(videoId)
            cached.remove("$videoId:hdr")
            listOfNotNull(
                active.remove(videoId),
                active.remove("$videoId:hdr")
            )
        }
        activeResolutions.forEach { it.cancel() }
    }

    fun dropResolved() {
        synchronized(lock) {
            epoch.incrementAndGet()
            cached.clear()
        }
    }

    fun clear() {
        Log.i(TAG, "Clearing all resolved stream ladders from cache")
        val activeResolutions = synchronized(lock) {
            epoch.incrementAndGet()
            cached.clear()
            active.values.toList().also { active.clear() }
        }
        activeResolutions.forEach { it.cancel() }
    }
}

internal fun streamResultExpiryMs(
    result: VideoStreamResult,
    nowMs: Long,
    fallbackTtlMs: Long,
    expirySafetyMs: Long,
): Long {
    val urls = result.qualities.flatMap { quality ->
        listOfNotNull(quality.url.takeIf(String::isNotBlank), quality.audioUrl)
    }
    if (urls.isEmpty()) return nowMs
    return urls.minOf { url ->
        val expireSeconds = url.toHttpUrlOrNull()
            ?.queryParameter("expire")
            ?.toLongOrNull()
        if (expireSeconds != null && expireSeconds > 0L) {
            expireSeconds * 1000L - expirySafetyMs
        } else {
            nowMs + fallbackTtlMs
        }
    }
}
