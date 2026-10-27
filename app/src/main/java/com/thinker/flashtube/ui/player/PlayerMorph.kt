package com.thinker.flashtube

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Where the morph starts and ends, in root pixels. Both ends are computed from the same
 * layout constants the mini-player and the player use, so they never need measuring
 * (the player may open before the mini-player has ever been laid out).
 */
data class MorphGeometry(
    val screen: Size,
    val miniCard: Rect,
    val miniThumb: Rect,
    val stage: Rect,
    val miniRadius: Float,
    val thumbRadius: Float,
    val stageRadius: Float,
)

/**
 * The mini-player <-> player container transform. A single [progress] (0 = mini,
 * 1 = full) drives every visual through graphicsLayer reads, so nothing relayouts or
 * recomposes per frame.
 *
 * Gestures move [progress] directly: the video's top edge travels exactly with the
 * finger between the mini-player slot and its full position. Tuning dials:
 *  - [FLING_PX_PER_S] how fast a flick must be to decide on its own.
 *  - [DISMISS_FROM_FULL] / [EXPAND_FROM_MINI] how far a slow drag must travel.
 *  - [settleSpec] the release spring (Castii's player spring).
 */
@Stable
class PlayerMorph(
    private val scope: CoroutineScope,
    private val onSettled: (expanded: Boolean) -> Unit,
) {
    /**
     * Default Float threshold is 0.01, where a spring stops and snaps the rest: 1% of the
     * travel is ~15–20 px, the visible "jump into place" at the end of a swipe-minimize
     * (Back uses a tween and eased all the way). 0.0005 ≈ under a pixel.
     */
    val progress = Animatable(0f, visibilityThreshold = 0.0005f)

    var geometry by mutableStateOf<MorphGeometry?>(null)
    var dragging by mutableStateOf(false)
        private set
    private var startedExpanded = false

    /** Vertical distance the video's top edge travels between mini and full. */
    private val travel: Float
        get() = geometry?.let { (it.miniThumb.top - it.stage.top).coerceAtLeast(1f) } ?: 1f

    /** Applies a vertical finger delta (down = positive). Returns the pixels used. */
    fun dragBy(deltaY: Float): Float {
        if (!dragging) {
            dragging = true
            startedExpanded = progress.value >= 0.5f
        }
        val before = progress.value.coerceIn(0f, 1f)
        val after = (before - deltaY / travel).coerceIn(0f, 1f)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { progress.snapTo(after) }
        return (before - after) * travel
    }

    /** Finger lifted: finish toward mini or full depending on speed and distance. */
    fun settle(velocityY: Float, reducedMotion: Boolean) {
        if (!dragging) return
        dragging = false
        val p = progress.value
        val expand = when {
            velocityY > FLING_PX_PER_S -> false
            velocityY < -FLING_PX_PER_S -> true
            startedExpanded -> p > 1f - DISMISS_FROM_FULL
            else -> p > EXPAND_FROM_MINI
        }
        val target = if (expand) 1f else 0f
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (reducedMotion) progress.snapTo(target)
            // Closing never overshoots: an underdamped spring dipped below 0 and came back,
            // flipping the video between player and mini-player at the very end (flicker).
            else progress.animateTo(target, if (expand) settleSpec else closeSpec, initialVelocity = -velocityY / travel)
        }
        onSettled(expand)
    }

    /** Non-gesture changes (tap, chevron, back, opening a video). */
    suspend fun animateTo(expanded: Boolean, reducedMotion: Boolean) {
        if (dragging) return
        val target = if (expanded) 1f else 0f
        // Already heading there (e.g. a gesture's settle spring): don't restart it.
        if (progress.targetValue == target && (progress.isRunning || progress.value == target)) return
        when {
            reducedMotion -> progress.snapTo(target)
            expanded -> progress.animateTo(target, tween(Motion.MorphMs, easing = Motion.Morph))
            else -> progress.animateTo(target, tween(Motion.CollapseMs, easing = Motion.Emphasized))
        }
    }

    /**
     * Lets the details list hand its leftover downward drag to the morph once it's
     * scrolled to the top, so the whole player can be swiped down, not just the video.
     */
    val nestedScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Mid-dismiss and the finger reverses: restore the player before scrolling.
            if (dragging && source == NestedScrollSource.UserInput && available.y < 0f) {
                return Offset(0f, dragBy(available.y))
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && available.y > 0f) {
                return Offset(0f, dragBy(available.y))
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!dragging) return Velocity.Zero
            settle(available.y, reducedMotion = false)
            return available
        }
    }

    companion object {
        const val FLING_PX_PER_S = 2200f
        const val DISMISS_FROM_FULL = 0.32f
        const val EXPAND_FROM_MINI = 0.32f
        private val settleSpec = spring(dampingRatio = 0.86f, stiffness = 550f, visibilityThreshold = 0.0005f)
        private val closeSpec = spring(dampingRatio = 1f, stiffness = 550f, visibilityThreshold = 0.0005f)
    }
}

/**
 * How the portrait video is drawn at morph progress p (see [MorphGeometry.videoFrame]):
 * the part of the stage that shows ([window], in the stage's own pixels), the uniform
 * [scale] it's drawn at, and how far its top-left has moved from the stage's ([offset]).
 */
class VideoFrame(val window: Rect, val scale: Float, val offset: Offset, val startScale: Float)

/**
 * The video's container transform for a stage laid out at [stageSize]. The stage takes the
 * video's shape, but the mini-player's slot is always 16:9, so at 0 only the stage's centred
 * 16:9 part shows (the mini-player crops the same way) and sits exactly on the thumbnail;
 * by 1 the window has opened to the whole stage.
 */
fun MorphGeometry.videoFrame(stageSize: Size, p: Float): VideoFrame {
    val slotAspect = miniThumb.width / miniThumb.height
    val crop = if (stageSize.width / stageSize.height > slotAspect) {
        val w = stageSize.height * slotAspect
        Rect((stageSize.width - w) / 2f, 0f, (stageSize.width + w) / 2f, stageSize.height)
    } else {
        val h = stageSize.width / slotAspect
        Rect(0f, (stageSize.height - h) / 2f, stageSize.width, (stageSize.height + h) / 2f)
    }
    val s0 = miniThumb.width / crop.width
    return VideoFrame(
        window = lerpRect(crop, Rect(Offset.Zero, stageSize), p),
        scale = lerpF(s0, 1f, p),
        offset = Offset(
            lerpF(miniThumb.left - stage.left - s0 * crop.left, 0f, p),
            lerpF(miniThumb.top - stage.top - s0 * crop.top, 0f, p),
        ),
        startScale = s0,
    )
}

/** Clips to an arbitrary rounded rect inside the layer — the morph's moving frame. */
class RoundRectClip(private val rect: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius.coerceAtLeast(0f))))
}

fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

fun lerpRect(a: Rect, b: Rect, t: Float) = Rect(lerp(a.topLeft, b.topLeft, t), lerp(a.bottomRight, b.bottomRight, t))

/** 0 until [start], then 0→1 by the end of the range. */
fun ramp(p: Float, start: Float, end: Float = 1f) = ((p - start) / (end - start)).coerceIn(0f, 1f)
