package com.thinker.flashtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Playback speed (Derek, 2026-10-13): 0.5–3× in 0.05 steps, from the speed panel under the
 * video. Normally a speed belongs to one video and the next one starts at 1×. With "Remember
 * for every video" it is saved and every video starts at it, until it is set back to 1×,
 * which ends remembering. Applied in the service (`SpeedKeeper`), so a queue advancing in
 * the background follows it too. Pitch is never changed.
 */
object PlaybackSpeed {
    const val MIN = 0.25f
    const val MAX = 3f
    const val STEP = 0.05f
    /** Steps from [MIN] to [MAX]. */
    val LAST_STEP = ((MAX - MIN) / STEP).roundToInt()

    private const val KEY_REMEMBER = "speed_remember"
    private const val KEY_SPEED = "speed_saved"
    private var prefs: SharedPreferences? = null

    var remember by mutableStateOf(false)
        private set
    /** The remembered speed (meaningful while [remember]). */
    var saved by mutableFloatStateOf(1f)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        remember = prefs?.getBoolean(KEY_REMEMBER, false) == true
        saved = snap(prefs?.getFloat(KEY_SPEED, 1f) ?: 1f)
    }

    /** What a video that has just become current starts at. */
    val forNewVideo: Float get() = if (remember) saved else 1f

    fun setRemember(on: Boolean, current: Float) {
        remember = on
        saved = snap(current)
        persist()
    }

    fun save(speed: Float) {
        saved = snap(speed)
        persist()
    }

    private fun persist() {
        prefs?.edit()?.putBoolean(KEY_REMEMBER, remember)?.putFloat(KEY_SPEED, saved)?.apply()
    }

    fun step(speed: Float) = ((speed - MIN) / STEP).roundToInt().coerceIn(0, LAST_STEP)
    /** Exact hundredths (1.4, not 1.4000001), so speeds compare and store cleanly. */
    fun atStep(step: Int) = ((MIN + step * STEP) * 100f).roundToInt() / 100f
    fun snap(speed: Float) = atStep(step(speed))
    fun isNormal(speed: Float) = abs(speed - 1f) < STEP / 2

    /** "1", "1.5", "1.75" (the × is drawn separately where it's styled). */
    fun label(speed: Float): String =
        String.format(Locale.US, "%.2f", snap(speed)).trimEnd('0').trimEnd('.')
}

/**
 * Ambient mode: dynamic lighting around the player matching the live video content.
 */
object AmbientMode {
    private const val KEY = "ambient_lighting"
    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        enabled = prefs?.getBoolean(KEY, true) ?: true
    }

    fun set(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean(KEY, on)?.apply()
    }

    fun toggle() = set(!enabled)
}

/**
 * Double-tap seek step configuration (5s, 10s, 15s, 20s, 30s, 60s).
 */
object SeekConfig {
    private const val KEY = "double_tap_seek_step"
    private var prefs: SharedPreferences? = null

    val options = listOf(5, 10, 15, 20, 30, 60)

    var stepSeconds by mutableStateOf(10)
        private set

    val stepMs: Long get() = stepSeconds * 1000L

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        stepSeconds = prefs?.getInt(KEY, 10) ?: 10
    }

    fun setStep(seconds: Int) {
        if (seconds in options) {
            stepSeconds = seconds
            prefs?.edit()?.putInt(KEY, seconds)?.apply()
        }
    }
}

/** Keeps audio playback alive when the app is backgrounded or swiped from Recents. */
object BackgroundPlayback {
    private const val KEY_ENABLED = "background_playback_enabled"
    private const val KEY_KEEP_ON_TASK_REMOVED = "background_keep_on_task_removed"
    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(true)
        private set
    var keepOnTaskRemoved by mutableStateOf(true)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        enabled = prefs?.getBoolean(KEY_ENABLED, true) ?: true
        keepOnTaskRemoved = prefs?.getBoolean(KEY_KEEP_ON_TASK_REMOVED, true) ?: true
    }

    fun updateEnabled(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean(KEY_ENABLED, on)?.apply()
    }

    fun updateKeepOnTaskRemoved(on: Boolean) {
        keepOnTaskRemoved = on
        prefs?.edit()?.putBoolean(KEY_KEEP_ON_TASK_REMOVED, on)?.apply()
    }
}
