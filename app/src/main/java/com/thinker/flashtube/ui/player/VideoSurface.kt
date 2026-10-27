package com.thinker.flashtube

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import java.lang.ref.WeakReference

/** The app's single shared video surface (see FlashTubeApp). */
val LocalVideoSurface = androidx.compose.runtime.staticCompositionLocalOf<@Composable (Modifier) -> Unit> { { m -> VideoSurface(null, m) } }

/** Draws nothing: where the video must not show (picture-in-picture shows it elsewhere). */
val NoVideoSurface: @Composable (Modifier) -> Unit = {}

/**
 * The one surface the decoder draws into, kept alive for the whole process.
 *
 * Why: a TextureView destroys its SurfaceTexture whenever it leaves the window (moving it
 * from the full player to the mini-player does exactly that). The decoder then loses its
 * output, is rebuilt, and waits for the next keyframe — seconds of frozen picture, the
 * "flash" when minimizing. Here the SurfaceTexture is never released (the destroy callback
 * returns false) and is handed back to whichever TextureView shows the video next, so the
 * player's surface never changes and playback never notices a move.
 */
object SharedVideoTexture {
    var texture: SurfaceTexture? = null
        private set
    var surface: Surface? = null
        private set
    /** Bumped when the surface first exists, so players can be pointed at it. */
    var version by mutableIntStateOf(0)
        private set

    private var activeViewRef: WeakReference<TextureView>? = null
    private val sampleBitmap by lazy { Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888) }
    private val samplePixels = IntArray(16 * 12)

    fun registerView(view: TextureView) {
        activeViewRef = WeakReference(view)
    }

    fun unregisterView(view: TextureView) {
        if (activeViewRef?.get() === view) {
            activeViewRef = null
        }
    }

    /**
     * Extracts a vibrant ambient color from the currently rendered frame on the TextureView.
     * Must be called on the main thread.
     */
    fun sampleAmbientColor(): Color? {
        val view = activeViewRef?.get() ?: return null
        if (!view.isAvailable || !view.isAttachedToWindow) return null
        if (view.width <= 0 || view.height <= 0) return null
        return try {
            val bm = view.getBitmap(sampleBitmap) ?: return null
            bm.getPixels(samplePixels, 0, 16, 0, 0, 16, 12)
            extractVibrantColor(samplePixels)
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractVibrantColor(pixels: IntArray): Color {
        var rSum = 0f
        var gSum = 0f
        var bSum = 0f
        var totalWeight = 0f

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            val max = maxOf(r, maxOf(g, b))
            val min = minOf(r, minOf(g, b))
            val v = max / 255f
            val s = if (max == 0) 0f else (max - min).toFloat() / max

            // Discard letterbox black bars or nearly pitch-black pixels (< 6% brightness)
            if (v < 0.06f) continue

            // Prioritize saturated and vivid colors for rich ambient lighting
            val weight = (s * 2.2f + 0.3f) * (v * 1.2f)
            rSum += r * weight
            gSum += g * weight
            bSum += b * weight
            totalWeight += weight
        }

        if (totalWeight < 0.05f) {
            // Scene is dark: dim down to deep subtle dark
            return Color(0xFF0F0F0F)
        }

        val avgR = (rSum / totalWeight).toInt().coerceIn(0, 255)
        val avgG = (gSum / totalWeight).toInt().coerceIn(0, 255)
        val avgB = (bSum / totalWeight).toInt().coerceIn(0, 255)

        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(avgR, avgG, avgB, hsv)

        // Boost saturation by 25% for a richer, more luminous ambient effect (capped at 1.0)
        hsv[1] = (hsv[1] * 1.25f).coerceIn(0f, 1f)
        // Keep value within comfortable ambient lighting bounds
        hsv[2] = hsv[2].coerceIn(0.20f, 0.95f)

        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    fun adopt(view: TextureView, fresh: SurfaceTexture) {
        registerView(view)
        val kept = texture
        if (kept == null) {
            texture = fresh
            surface = Surface(fresh)
            version++
        } else if (kept !== fresh) {
            // The view made a new one on re-attach: swap ours back in (releases the new one).
            runCatching { view.setSurfaceTexture(kept) }
                .onFailure { Log.w("FlashTube", "couldn't re-attach the video texture", it) }
        }
    }
}

/**
 * The video, in a TextureView this app owns. All TextureViews show [SharedVideoTexture];
 * the player's output surface is set once and never cleared while the app runs.
 */
@Composable
fun VideoSurface(player: Player?, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                // A view created after the texture exists shows it straight away.
                SharedVideoTexture.texture?.let { runCatching { setSurfaceTexture(it) } }
                SharedVideoTexture.registerView(this)
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        SharedVideoTexture.registerView(this@apply)
                        SharedVideoTexture.adopt(this@apply, st)
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
                    // Never let the view release it: the next view (or this one, re-attached) reuses it.
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = st !== SharedVideoTexture.texture
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = AvSync.onFrameDrawn()
                }
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        SharedVideoTexture.registerView(this@apply)
                        // Before the hardware layer is built, so it adopts the kept texture.
                        val kept = SharedVideoTexture.texture ?: return
                        if (surfaceTexture !== kept) runCatching { setSurfaceTexture(kept) }
                    }
                    override fun onViewDetachedFromWindow(v: View) {
                        SharedVideoTexture.unregisterView(this@apply)
                    }
                })
            }
        },
    )
    val version = SharedVideoTexture.version
    LaunchedEffect(player, version) {
        val surface = SharedVideoTexture.surface ?: return@LaunchedEffect
        player?.setVideoSurface(surface)
    }
}
