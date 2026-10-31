package com.thinker.flashtube.data.stream

import android.util.Log

/**
 * Whether YouTube's bot check recently refused a stream in a way no fresh
 * identity could fix: a just-minted visitorData was refused as well, or
 * NewPipe was refused alongside the direct chain. That is a verdict on the address,
 * not on a token.
 */
object BotCheckVerdict {
    private const val STREAM_TAG = "YouTubeStreams"
    private const val HOLD_MS = 3 * 60 * 1000L

    @Volatile private var refusedAtMs: Long = 0L

    /** Never a reason to refuse a request the user made. */
    fun isActive(): Boolean {
        val at = refusedAtMs
        if (at == 0L) return false
        return System.currentTimeMillis() - at in 0 until HOLD_MS
    }

    fun note(videoId: String) {
        if (!isActive()) {
            Log.w(
                STREAM_TAG,
                "Bot check refused videoId=$videoId with a fresh identity; " +
                    "holding automatic retries and prefetch for ${HOLD_MS / 1000}s",
            )
        }
        refusedAtMs = System.currentTimeMillis()
    }

    /** A stream resolved, so the address is being served again. */
    fun clearOnSuccess() {
        if (refusedAtMs != 0L) {
            refusedAtMs = 0L
            Log.i(STREAM_TAG, "Stream resolved; bot-check verdict cleared")
        }
    }

    /** The identity or the network changed, so the verdict describes neither. */
    fun reset() {
        refusedAtMs = 0L
    }
}
