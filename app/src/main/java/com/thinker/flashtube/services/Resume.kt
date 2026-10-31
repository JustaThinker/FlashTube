package com.thinker.flashtube

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateMapOf
import androidx.media3.common.C
import androidx.media3.common.Player

/**
 * Where each video was left off. Written by the playback service (so it keeps saving in
 * the background and survives the app being killed), read when a video opens and by the
 * thumbnails' progress bars. Finished videos are forgotten, so they restart from 0.
 */
object ResumeStore {
    private const val MAX_ENTRIES = 500
    /** Closer than this to either end isn't worth resuming. */
    private const val EDGE_MS = 10_000L

    private var prefs: SharedPreferences? = null
    private val musicVideoIds = mutableSetOf<String>()
    /** id → (positionMs, durationMs), also observable by the UI. */
    val positions = mutableStateMapOf<String, Pair<Long, Long>>()

    fun init(context: Context) {
        val p = context.getSharedPreferences("resume", Context.MODE_PRIVATE).also { prefs = it }
        musicVideoIds.clear()
        musicVideoIds.addAll(p.getStringSet("_music_video_ids", emptySet()).orEmpty())
        p.all.forEach { (id, value) ->
            val parts = (value as? String)?.split(',') ?: return@forEach
            val pos = parts.getOrNull(0)?.toLongOrNull() ?: return@forEach
            val dur = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            positions[id] = pos to dur
        }
    }

    /** Where to start [id], or 0. */
    fun startPosition(id: String): Long = positions[id]?.first ?: 0L

    /** Music videos start from the beginning and are excluded from resume progress. */
    fun markMusicVideo(id: String, isMusicVideo: Boolean) {
        if (id.isBlank()) return
        if (isMusicVideo) {
            musicVideoIds.add(id)
            positions.remove(id)
            prefs?.edit()?.remove(id)?.putStringSet("_music_video_ids", musicVideoIds.toSet())?.apply()
        } else if (musicVideoIds.remove(id)) {
            prefs?.edit()?.putStringSet("_music_video_ids", musicVideoIds.toSet())?.apply()
        }
    }

    fun isMusicVideo(id: String): Boolean = id in musicVideoIds

    /** 0..1 watched, or null if never started / finished. For thumbnail bars. */
    fun progress(id: String): Float? {
        val (pos, dur) = positions[id] ?: return null
        return if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else null
    }

    fun save(id: String, positionMs: Long, durationMs: Long) {
        if (id in musicVideoIds) return
        if (id.isBlank() || durationMs <= 0 || durationMs == C.TIME_UNSET) return
        if (positionMs < EDGE_MS || positionMs > durationMs - EDGE_MS) {
            // Barely started or essentially finished: nothing to resume.
            if (positions.remove(id) != null) prefs?.edit()?.remove(id)?.apply()
            return
        }
        positions[id] = positionMs to durationMs
        prefs?.edit()?.putString(id, "$positionMs,$durationMs")?.apply()
        if (positions.size > MAX_ENTRIES) trim()
    }

    private fun trim() {
        // Oldest-first isn't tracked; dropping arbitrary extras is fine at this size.
        val extra = positions.keys.take(positions.size - MAX_ENTRIES)
        val edit = prefs?.edit()
        extra.forEach { positions.remove(it); edit?.remove(it) }
        edit?.apply()
    }
}

/**
 * Service-side saver: every 5 s while playing, and on pause, seek, item change and stop.
 * Runs on the player's (main) looper.
 */
class ResumeSaver(private val player: Player) : Player.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private var lastId: String? = null
    private val tick = object : Runnable {
        override fun run() {
            saveNow()
            if (player.isPlaying) handler.postDelayed(this, 5_000)
        }
    }

    fun saveNow() {
        val id = player.currentMediaItem?.mediaId ?: return
        lastId = id
        ResumeStore.save(id, player.currentPosition, player.duration)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        handler.removeCallbacks(tick)
        if (isPlaying) handler.postDelayed(tick, 5_000) else saveNow()
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        // Leaving a video (auto-advance, skip): record where the old one ended.
        val oldId = oldPosition.mediaItem?.mediaId
        if (oldId != null && oldId != newPosition.mediaItem?.mediaId) {
            val duration = player.duration.takeIf { lastId == oldId } ?: 0L
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) ResumeStore.save(oldId, Long.MAX_VALUE, Long.MAX_VALUE)
            else if (duration > 0) ResumeStore.save(oldId, oldPosition.positionMs, duration)
        } else saveNow()
    }

    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_ENDED) player.currentMediaItem?.mediaId?.let { ResumeStore.save(it, Long.MAX_VALUE, Long.MAX_VALUE) }
    }
}
