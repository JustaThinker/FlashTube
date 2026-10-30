package com.thinker.flashtube

import com.thinker.flashtube.ui.player.components.SkimmerPreviewCard
import com.thinker.flashtube.ui.player.components.PlainIconButton
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RepeatOne
import com.thinker.flashtube.data.ContentRegion
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.luminance
import com.thinker.flashtube.ui.player.components.PlayerPlayPauseGlyph
import com.thinker.flashtube.ui.player.components.PlayerDrawableIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The video with its overlay controls — shared by the portrait player and fullscreen.
 * Tap toggles the controls; they auto-hide while playing. [controlsAlpha] lets the morph
 * hold them back until the video has landed.
 */
@Composable
fun VideoContent(
    state: PlayerState,
    fullscreen: Boolean,
    onCollapse: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    controlsAlpha: () -> Float = { 1f },
    /** False while the player collapses: no controls, and a tap calls [onInactiveTap]. */
    interactive: Boolean = true,
    onInactiveTap: () -> Unit = {},
    /** Portrait: seam scrubber on the morphing stage (not a screen-fixed overlay). */
    portraitEdgeScrub: Boolean = false,
) {
    // Hidden until the video is tapped: opening the player (from the mini-player too) and
    // rotating in or out of fullscreen never pop the controls up.
    var controlsVisible by remember { mutableStateOf(false) }
    var touchedAt by remember { mutableLongStateOf(0L) }
    fun touched() { touchedAt = System.nanoTime() }
    SideEffect { state.videoControlsVisible = controlsVisible }
    LaunchedEffect(controlsVisible, state.isPlaying, touchedAt) {
        if (controlsVisible && state.isPlaying) {
            delay(Motion.ControlsHideMs)
            controlsVisible = false
        }
    }
    val reduced = rememberReducedMotion()
    val visibleAlpha by animateFloatAsState(
        targetValue = if (controlsVisible) 1f else 0f,
        animationSpec = if (reduced) tween(0) else tween(160, easing = Motion.Emphasized),
        label = "controlsAlpha",
    )

    // Double-tap seek: a double tap on the left/right 40% starts a burst of -/+10 s; each
    // further tap on that side within SeekBurstWindowMs adds 10 s more (SeekBurstPill).
    var burstSide by remember { mutableIntStateOf(1) }
    var burstSeconds by remember { mutableIntStateOf(0) }
    var burstTaps by remember { mutableIntStateOf(0) }
    var burstActive by remember { mutableStateOf(false) }
    LaunchedEffect(burstTaps) {
        if (burstTaps == 0) return@LaunchedEffect
        delay(SeekBurstWindowMs)
        burstActive = false
    }
    // Double-tap seek burst feedback
    LaunchedEffect(interactive) {
        if (!interactive) {
            controlsVisible = false
            burstActive = false
            state.endScrubDragging()
            state.endHoldSpeed()
        }
    }
    val currentInteractive by androidx.compose.runtime.rememberUpdatedState(interactive)
    val currentInactiveTap by androidx.compose.runtime.rememberUpdatedState(onInactiveTap)
    fun toggleControls() { controlsVisible = !controlsVisible; touched() }
    fun seekStep(side: Int) {
        val moved = state.seekBy(side * SeekStepMs)
        burstSeconds += ((abs(moved) + 500) / 1000).toInt()
        burstTaps++
    }
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val activity = context as? Activity
    var gestureHud by remember { mutableStateOf<Pair<ImageVector, Pair<String, Float>>?>(null) }

    LaunchedEffect(gestureHud) {
        if (gestureHud != null) {
            delay(1200)
            gestureHud = null
        }
    }

    val pip = LocalPictureInPicture.current
    val view = LocalView.current
    val currentView by rememberUpdatedState(view)
    val fullscreenControlsScrub = fullscreen && !portraitEdgeScrub
    val scrubbing = state.scrubDragging
    val scrubPreviewMs = state.scrubPreviewMs
    val zoomState = rememberTransformableState { zoomChange, _, _ ->
        if (interactive) {
            state.videoZoom = (state.videoZoom * zoomChange).coerceIn(1f, 4f)
        }
    }
    LaunchedEffect(state.current?.id) {
        state.current?.id?.let { ScrubStoryboards.preload(it) }
    }
    Box(modifier.background(Color.Black).onGloballyPositioned { pip?.videoBounds = it.boundsInWindow() }.pointerInput(state, portraitEdgeScrub) {
        val doubleTapMs = viewConfiguration.doubleTapTimeoutMillis
        val holdSpeedMs = Motion.HoldSpeedMs
        coroutineScope {
            // Side taps show/hide controls at once; a second tap on that side inside the double-tap window seeks.
            var seekWindow: Job? = null
            var pendingSide = 0
            var pendingAt = 0L
            awaitEachGesture {
                val down = awaitFirstDown()
                if (portraitEdgeScrub && down.position.y >= size.height - EdgeScrubTouchPadAbove.roundToPx()) {
                    return@awaitEachGesture
                }
                down.consume()
                if (!currentInteractive) {
                    waitForUpOrCancellation()
                    currentInactiveTap()
                    return@awaitEachGesture
                }
                if (state.error != null) {
                    waitForUpOrCancellation()
                    return@awaitEachGesture
                }

                val startTime = System.currentTimeMillis()
                val downX = down.position.x / size.width
                val downY = down.position.y
                var isDraggingGesture = false
                var initialBrightness = -1f
                var initialVolume = -1f
                var lastY = downY

                // Check for vertical drag gesture:
                // If fullscreen: left = brightness, right = volume, center drag down = exit fullscreen.
                // If portrait: drag down = minimize, drag up = fullscreen.
                var verticalSwipeHandled = false
                val upEarly = withTimeoutOrNull(holdSpeedMs) {
                    var event = awaitPointerEvent()
                    while (event.changes.any { it.pressed }) {
                        val change = event.changes.firstOrNull { it.pressed }
                        if (change != null) {
                            val dy = lastY - change.position.y
                            val totalDy = downY - change.position.y
                            val totalDx = down.position.x - change.position.x
                            // Require a predominantly vertical gesture (slight horizontal wiggle ignored)
                            val isVerticalIntent = kotlin.math.abs(totalDy) > kotlin.math.abs(totalDx) * 1.5f

                            if (fullscreen) {
                                // Narrow edge zones: left 25% for brightness, right 25% for volume
                                val isLeftEdge = downX < 0.25f
                                val isRightEdge = downX > 0.75f

                                if (!isDraggingGesture && isVerticalIntent && kotlin.math.abs(totalDy) > 30f) {
                                    if (isLeftEdge || isRightEdge) {
                                        isDraggingGesture = true
                                        if (isLeftEdge && activity != null) {
                                            val attrs = activity.window.attributes
                                            initialBrightness = if (attrs.screenBrightness < 0f) {
                                                runCatching {
                                                    android.provider.Settings.System.getInt(
                                                        context.contentResolver,
                                                        android.provider.Settings.System.SCREEN_BRIGHTNESS,
                                                    ) / 255f
                                                }.getOrDefault(0.5f)
                                            } else {
                                                attrs.screenBrightness
                                            }
                                        } else if (isRightEdge && audioManager != null) {
                                            initialVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()
                                        }
                                    } else if (totalDy < -90f) {
                                        // Intentional center swipe down to exit fullscreen
                                        verticalSwipeHandled = true
                                        onToggleFullscreen()
                                        return@withTimeoutOrNull null
                                    }
                                }
                                if (isDraggingGesture) {
                                    change.consume()
                                    val dragFraction = totalDy / (size.height * 0.85f)
                                    if (isLeftEdge && activity != null && initialBrightness >= 0f) {
                                        val window = activity.window
                                        val attrs = window.attributes
                                        val targetB = initialBrightness + dragFraction
                                        if (targetB <= -0.05f) {
                                            attrs.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
                                            window.attributes = attrs
                                            gestureHud = Pair(Icons.Rounded.BrightnessAuto, Pair("Brightness", -1f))
                                        } else {
                                            val setB = targetB.coerceIn(0.01f, 1f)
                                            attrs.screenBrightness = setB
                                            window.attributes = attrs
                                            gestureHud = Pair(Icons.Rounded.BrightnessMedium, Pair("Brightness", setB))
                                        }
                                    } else if (isRightEdge && audioManager != null && initialVolume >= 0f) {
                                        val maxV = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()
                                        val targetV = (initialVolume + dragFraction * maxV).coerceIn(0f, maxV)
                                        val newVInt = targetV.roundToInt()
                                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVInt, 0)
                                        val icon = if (newVInt == 0) Icons.AutoMirrored.Rounded.VolumeMute else Icons.AutoMirrored.Rounded.VolumeUp
                                        gestureHud = Pair(icon, Pair("Volume", targetV / maxV))
                                    }
                                }
                            } else {
                                // In portrait: handled by VideoStage's draggable (.draggable) to prevent double-triggering
                                // Only consume if a deliberate swipe (> 90px) occurs
                                if (!verticalSwipeHandled && isVerticalIntent && kotlin.math.abs(totalDy) > 90f) {
                                    verticalSwipeHandled = true
                                    change.consume()
                                    if (totalDy < -90f) {
                                        onCollapse()
                                    } else {
                                        onToggleFullscreen()
                                    }
                                    return@withTimeoutOrNull null
                                }
                            }
                        }
                        event = awaitPointerEvent()
                    }
                    event.changes.firstOrNull()
                }

                if (isDraggingGesture || verticalSwipeHandled) {
                    return@awaitEachGesture
                }

                if (upEarly == null) {
                    val elapsed = System.currentTimeMillis() - startTime
                    // ONLY trigger 2x playback speed if the user held down for >= 500ms continuously!
                    if (elapsed >= holdSpeedMs - 80L) {
                        seekWindow?.cancel()
                        if (!state.scrubDragging && state.beginHoldSpeed()) {
                            currentView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            controlsVisible = false
                            burstActive = false
                            waitForUpOrCancellation()?.consume()
                            state.endHoldSpeed()
                        } else {
                            waitForUpOrCancellation()?.consume()
                        }
                    }
                    return@awaitEachGesture
                }
                val up = upEarly
                up.consume()
                val x = up.position.x / size.width
                val side = if (x < 0.4f) -1 else if (x > 0.6f) 1 else 0
                if (burstActive && side == burstSide) { seekStep(side); return@awaitEachGesture }
                burstActive = false
                val inSeekWindow = seekWindow?.isActive == true
                seekWindow?.cancel()
                if (side == 0) {
                    if (state.videoZoom > 1.05f) {
                        state.resetZoom()
                    } else {
                        toggleControls()
                    }
                } else if (inSeekWindow && side == pendingSide && up.uptimeMillis - pendingAt < doubleTapMs) {
                    controlsVisible = false
                    burstSide = side
                    burstSeconds = 0
                    burstActive = true
                    seekStep(side)
                } else {
                    toggleControls()
                    pendingSide = side
                    pendingAt = up.uptimeMillis
                    seekWindow = launch { delay(doubleTapMs) }
                }
            }
        }
    }) {
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .transformable(state = zoomState),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .aspectFit(state.videoAspect, cover = state.zoomToFill)
                    .graphicsLayer {
                        val z = state.videoZoom
                        scaleX = z
                        scaleY = z
                    },
            ) {
                // Under the (transparent until it has a frame) TextureView, so opening or
                // re-attaching never shows a black void.
                Thumbnail(
                    state.current?.thumbnail,
                    Modifier.fillMaxSize().graphicsLayer { alpha = if (scrubbing) 0f else 1f },
                    radius = 0,
                )
                LocalVideoSurface.current(Modifier.fillMaxSize())
                if (scrubbing && scrubPreviewMs != null) {
                    ScrubVideoPreview(
                        state.current?.id,
                        scrubPreviewMs,
                        Modifier.fillMaxSize(),
                    )
                }
            }
        }
        var showZoomPill by remember { mutableStateOf(false) }
        LaunchedEffect(state.videoZoom) {
            if (state.videoZoom > 1.05f) {
                showZoomPill = true
                delay(2_000)
                showZoomPill = false
            } else {
                showZoomPill = false
            }
        }
        val zoomAlpha by animateFloatAsState(
            targetValue = if (showZoomPill || controlsVisible) 1f else 0f,
            animationSpec = tween(300),
            label = "zoomPillAlpha"
        )
        if (state.videoZoom > 1.05f && zoomAlpha > 0f) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
                    .graphicsLayer { alpha = zoomAlpha }
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable { state.resetZoom() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${(state.videoZoom * 10).toInt() / 10f}× · Tap to reset",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (!scrubbing) {
            SeekBurstPill(burstSide, burstSeconds, burstTaps, burstActive, fullscreen)
        }
        if (state.holdSpeedActive) {
            HoldSpeedPill(Modifier.align(Alignment.TopCenter).padding(top = 12.dp).graphicsLayer { alpha = controlsAlpha() })
        }
        gestureHud?.let { (icon, info) ->
            GestureHUD(icon = icon, label = info.first, percent = info.second, modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp))
        }

        val error = state.error
        if (error != null) {
            ErrorPanel(error, onRetry = state::retry, onCollapse = onCollapse)
            return@Box
        }

        val enabled = controlsVisible && interactive
        // With the controls hidden, loading still shows: a small spinner in the middle.
        if (!scrubbing && !controlsVisible && (state.isBuffering || (state.loading && state.details == null))) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(36.dp))
        }
        if (!scrubbing) {
            SkippedNote(state, Modifier.align(Alignment.TopCenter).padding(top = 12.dp).graphicsLayer { alpha = controlsAlpha() })
        }
        if (state.statsForNerdsOpen) {
            StatsForNerdsOverlay(
                state = state,
                onClose = { state.toggleStatsForNerds() },
                modifier = Modifier.align(Alignment.Center),
            )
        }
        // The Ask pill stays reachable while the controls are hidden.
        if (!scrubbing && !controlsVisible) {
            state.askSegment?.let { segment ->
                SkipSegmentPill(segment, enabled = true, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, top = 12.dp, bottom = 40.dp)) {
                    touched(); state.skipSegment(segment)
                }
            }
        }
        val chromeAlpha = if (scrubbing) 0f else visibleAlpha * controlsAlpha()
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = chromeAlpha }) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.5f),
                        0.32f to Color.Black.copy(alpha = 0.08f),
                        0.58f to Color.Black.copy(alpha = 0.08f),
                        1f to Color.Black.copy(alpha = 0.62f),
                    ),
                ),
            )
            val edge = if (fullscreen) {
                Modifier.fullscreenControlsHorizontalInsets().padding(vertical = FullscreenControlsVerticalPad)
            } else {
                Modifier
            }
            Box(Modifier.fillMaxSize().then(edge)) {
                // Top row: minimize / exit fullscreen, title (fullscreen), options (Loop, Speed, Quality, PiP).
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (fullscreen) {
                        GlassButton(Icons.AutoMirrored.Rounded.ArrowBack, "Exit fullscreen", enabled, size = 32.dp, iconSize = 20.dp) {
                            touched()
                            onToggleFullscreen()
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            state.current?.title.orEmpty(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        GlassButton(Icons.Rounded.KeyboardArrowDown, "Minimize", enabled, size = 32.dp, iconSize = 22.dp) {
                            touched()
                            onCollapse()
                        }
                        Spacer(Modifier.weight(1f))
                    }

                    // Picture-in-picture
                    if (pip?.supported == true) {
                        GlassButton(null, "Picture-in-picture", enabled, drawableRes = R.drawable.ic_player_floating, size = 40.dp, iconSize = 30.dp) {
                            controlsVisible = false
                            if (!pip.enter(state.videoAspect)) state.actionMessage = "Picture-in-picture is turned off for FlashTube in Settings."
                        }
                        Spacer(Modifier.width(6.dp))
                    }

                    // Settings (Gear) icon - opens playback settings list (Morphe/YouTube style)
                    GlassButton(null, "Playback settings", enabled, drawableRes = R.drawable.ic_player_settings, size = 40.dp, iconSize = 30.dp) {
                        touched()
                        state.sheet = PlayerSheet.Settings
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 44.dp else 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val prevNextSize = if (fullscreen) 56.dp else 48.dp
                    val iconSize = if (fullscreen) 36.dp else 30.dp

                    // Previous Video (Clean unboxed icon)
                    PlainIconButton(
                        icon = Icons.Rounded.SkipPrevious,
                        description = "Previous video",
                        enabled = enabled,
                        size = prevNextSize,
                        iconSize = iconSize,
                    ) {
                        touched()
                        val c = state.controller
                        if (c != null && c.hasPreviousMediaItem()) {
                            c.seekToPreviousMediaItem()
                        } else {
                            state.seekBy(-10_000)
                        }
                    }

                    // Center Play / Pause (Clean unboxed icon)
                    MorphPlayButton(
                        playing = state.isPlaying,
                        buffering = !state.isPlaying && (state.isBuffering || (state.loading && state.details == null)),
                        size = if (fullscreen) 72.dp else 60.dp,
                        container = Color.Transparent,
                        content = Color.White,
                        enabled = enabled,
                    ) {
                        touched()
                        state.togglePlay()
                    }

                    // Next Video (Clean unboxed icon)
                    PlainIconButton(
                        icon = Icons.Rounded.SkipNext,
                        description = "Next video",
                        enabled = enabled,
                        size = prevNextSize,
                        iconSize = iconSize,
                    ) {
                        touched()
                        val c = state.controller
                        if (c != null && c.hasNextMediaItem()) {
                            c.seekToNextMediaItem()
                        } else {
                            state.seekBy(SeekConfig.stepMs)
                        }
                    }
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(
                            start = if (fullscreenControlsScrub) 0.dp else 14.dp,
                            end = if (fullscreenControlsScrub) 0.dp else 8.dp,
                            bottom = EdgeScrubTimeRowBottomPad +
                                if (fullscreenControlsScrub) FullscreenControlsLift else 0.dp,
                        ),
                ) {
                    Row(
                        Modifier.height(EdgeScrubTimeRowHeight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(formatDuration(state.scrubPreviewMs ?: state.positionMs), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            " / " + formatDuration(state.durationMs),
                            color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        state.askSegment?.let { segment ->
                            SkipSegmentPill(segment, enabled = true) { touched(); state.skipSegment(segment) }
                            Spacer(Modifier.width(6.dp))
                        }
                        GlassButton(
                            null,
                            if (fullscreen) "Exit fullscreen" else "Fullscreen",
                            enabled,
                            drawableRes = if (fullscreen) R.drawable.ic_player_fullscreen_exit else R.drawable.ic_player_fullscreen,
                            size = 34.dp,
                            iconSize = 28.dp,
                        ) { onToggleFullscreen() }
                    }
                }
            }
        }
        // Portrait keeps the thin edge bar when controls are hidden; fullscreen hides the scrubber until controls (or a drag) show.
        if (state.error == null && (!fullscreenControlsScrub || controlsVisible || scrubbing)) {
            val scrubActive = controlsVisible && interactive
            val morphFade = controlsAlpha()
            val scrubFade = if (scrubbing) 1f else morphFade
            val segments = if (SponsorBlock.enabled) state.segments.filter { it.action != SponsorBlock.Action.Off } else emptyList()
            val scrubModifier = when {
                portraitEdgeScrub -> Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // Fixed on the stage/page seam; the morph layer moves it with the video.
                    .offset(y = EdgeScrubTouchPadBelow)
                    .height(EdgeScrubTouchHeight)
                    .graphicsLayer { alpha = scrubFade }
                fullscreenControlsScrub -> Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fullscreenControlsHorizontalInsets()
                    .padding(bottom = FullscreenScrubBottomPad + FullscreenControlsLift)
                    .height(FullscreenScrubTouchHeight)
                    .graphicsLayer { alpha = scrubFade }
                else -> Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(EdgeScrubTouchPadAbove * 2)
                    .graphicsLayer { alpha = scrubFade }
            }
            Scrubber(
                segments = segments,
                positionMs = state.positionMs,
                bufferedMs = state.bufferedMs,
                durationMs = state.durationMs,
                enabled = interactive,
                active = scrubActive,
                relativeDrag = true,
                edgeToEdge = portraitEdgeScrub,
                tapToSeek = false,
                edgeTouchPadAbove = EdgeScrubTouchPadAbove,
                trackHeight = if (fullscreenControlsScrub) FullscreenScrubTrackHeight else 4.dp,
                touchHeight = when {
                    portraitEdgeScrub -> EdgeScrubTouchHeight
                    fullscreenControlsScrub -> FullscreenScrubTouchHeight
                    else -> EdgeScrubTouchPadAbove * 2
                },
                knob = if (fullscreenControlsScrub) FullscreenScrubKnob else 7.dp,
                knobDragging = if (fullscreenControlsScrub) FullscreenScrubKnobDragging else 9.dp,
                onPreview = { state.scrubPreviewMs = it; touched() },
                onSeek = { state.endScrubDragging(it); touched() },
                onDraggingChanged = { dragging ->
                    if (dragging) state.beginScrubDragging()
                    else state.endScrubDragging()
                },
                modifier = scrubModifier.zIndex(10f),
            )
        }
        if (scrubbing && scrubPreviewMs != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .then(
                        if (fullscreenControlsScrub) Modifier.fullscreenControlsHorizontalInsets() else Modifier,
                    )
                    .padding(
                        bottom = if (fullscreenControlsScrub) {
                            FullscreenTimeRowBottomFromEdge
                        } else {
                            EdgeScrubTimeRowBottomPad
                        },
                    )
                    .zIndex(11f),
            ) {
                SkimmerPreviewCard(
                    videoId = state.current?.id,
                    positionMs = scrubPreviewMs,
                    visible = scrubbing,
                )
                Box(
                    modifier = Modifier.height(EdgeScrubTimeRowHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        formatDuration(scrubPreviewMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Action popup overlay for likes, quality changes, account actions, etc.
        ActionMessage(
            state = state,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (fullscreen) 64.dp else 40.dp)
                .zIndex(100f),
        )

        // Bottom sheets triggered from video controls / playback settings
        when (state.sheet) {
            PlayerSheet.Quality -> QualitySheet(state, onDismiss = { state.sheet = null })
            PlayerSheet.Settings -> PlayerSettingsSheet(state, onDismiss = { state.sheet = null })
            PlayerSheet.Queue -> QueueSheet(player = state, onDismiss = { state.sheet = null })
            PlayerSheet.Subtitles -> SubtitlesSheet(player = state, onDismiss = { state.sheet = null })
            else -> {}
        }
    }
}

/**
 * Lays the content out at [aspect], centred: as large as fits inside the incoming box, or
 * with [cover] as small as covers it (the overflow is for the caller to clip).
 */
fun Modifier.aspectFit(aspect: Float, cover: Boolean = false) = layout { measurable, constraints ->
    val boxW = constraints.maxWidth.toFloat()
    val boxH = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else boxW / aspect
    // Height decides when the box is wider than the content (fit) or narrower (cover).
    val byHeight = (boxW / boxH > aspect) != cover
    val w = (if (byHeight) boxH * aspect else boxW).roundToInt()
    val h = (if (byHeight) boxH else boxW / aspect).roundToInt()
    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
    layout(boxW.roundToInt(), boxH.roundToInt()) {
        placeable.place(((boxW - w) / 2f).roundToInt(), ((boxH - h) / 2f).roundToInt())
    }
}

/**
 * Landscape fullscreen. Swipe down (or Back, or the exit button) returns to portrait.
 * System bars are hidden by the caller while this is shown.
 */
@Composable
fun FullscreenPlayer(state: PlayerState, onExit: () -> Unit) {
    val reduced = rememberReducedMotion()
    var drag by remember { mutableStateOf(0f) }
    VideoContent(
        state = state,
        fullscreen = true,
        onCollapse = onExit,
        onToggleFullscreen = onExit,
        modifier = Modifier
            .fillMaxSize()
            .draggable(
                state = rememberDraggableState { delta -> drag = (drag + delta).coerceAtLeast(0f) },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (drag > 120f || velocity > 1400f) onExit()
                    drag = 0f
                },
            )
            .graphicsLayer {
                // A little give while dragging, so the gesture is visibly being heard.
                translationY = if (reduced) 0f else drag * 0.35f
            },
    )
}

@Composable
private fun HoldSpeedPill(modifier: Modifier = Modifier) {
    Text(
        "2×",
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        lineHeight = 13.sp,
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** "Skip sponsor ›" — shown while playback is inside a segment set to Ask. */
@Composable
private fun SkipSegmentPill(segment: SponsorBlock.Segment, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(32.dp)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(segment.category.color.copy(alpha = 0.92f))
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (segment.category.color.luminance() > 0.45f) Color(0xFF151515) else Color.White
        Text("Skip ${segment.category.short}", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Icon(Icons.Rounded.SkipNext, null, tint = ink, modifier = Modifier.size(18.dp))
    }
}

/** "Skipped 1:12 sponsor · Undo" after an automatic skip; fades after a few seconds. */
@Composable
private fun SkippedNote(state: PlayerState, modifier: Modifier) {
    val skip = SponsorBlock.lastSkip?.takeIf { it.videoId == state.current?.id } ?: return
    LaunchedEffect(skip) {
        delay(4_000)
        if (SponsorBlock.lastSkip == skip) SponsorBlock.lastSkip = null
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Skipped ${formatDuration(skip.segment.lengthMs)} ${skip.segment.category.short}",
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        )
        Text(
            "Undo",
            color = skip.segment.category.color, fontSize = 12.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.pressable { state.undoSkip(skip) }.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySheet(state: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val details = state.details ?: return
    val chosen = QualityPreference.height
    val context = LocalContext.current
    val isMetered = remember(context) { QualityPreference.isNetworkMetered(context) }
    val cachedInfo = remember(details.item.id) { StreamCache.get(details.item.id) }
    val qualities = remember(details.qualities, cachedInfo) {
        val fromInfo = cachedInfo?.let { StreamSelection.heights(it) }.orEmpty()
        (fromInfo + details.qualities).filter { it > 0 }.distinct().sortedDescending()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Quality",
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f)
                )
                state.playingFormat?.let {
                    Text(it, color = c.onSurfaceVariant, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(6.dp))

            val networkType = if (isMetered) "Mobile Data · max 720p" else "Wi-Fi · up to 1080p/2160p"
            val activeLabel = (state.decodedHeight?.let { "${it}p" } ?: details.quality)
            val autoDesc = "Auto ($networkType)" + (activeLabel?.let { " · $it" } ?: "")
            QualityRow("Auto", autoDesc, chosen == null) {
                state.setQuality(null)
                onDismiss()
            }

            qualities.forEach { h ->
                QualityRow("${h}p", qualityTag(h), chosen == h) {
                    state.setQuality(h)
                    onDismiss()
                }
            }
        }
    }
}

private fun qualityTag(height: Int): String? = when {
    height >= 4320 -> "8K"
    height >= 2160 -> "4K"
    height >= 1440 -> "2K"
    height >= 1080 -> "Full HD"
    height >= 720 -> "HD"
    height <= 240 -> "Data saver"
    else -> null
}

@Composable
private fun QualityRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.primaryContainer else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 14.5.sp,
            color = if (selected) c.onPrimaryContainer else c.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (subtitle != null) {
            Text(
                subtitle,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) c.onPrimaryContainer.copy(alpha = 0.8f) else c.onSurfaceVariant
            )
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Check, "Selected", tint = c.onPrimaryContainer, modifier = Modifier.size(17.dp))
        }
    }
}

/**
 * Playback Settings Menu — sleek, compact, and minimal panel matching modern YouTube.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(state: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var currentSubMenu by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.onSurfaceVariant.copy(alpha = 0.35f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            when (currentSubMenu) {
                "speed" -> {
                    // Header with back arrow
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .pressable { currentSubMenu = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = c.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Playback speed",
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = c.onSurface,
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    val speedPresets = listOf(
                        0.25f to "0.25×",
                        0.5f to "0.5×",
                        0.75f to "0.75×",
                        1.0f to "Normal",
                        1.25f to "1.25×",
                        1.5f to "1.5×",
                        1.75f to "1.75×",
                        2.0f to "2×",
                    )

                    speedPresets.forEach { (speedValue, speedLabel) ->
                        val selected = kotlin.math.abs(state.speed - speedValue) < 0.02f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressable(pressedScale = 0.98f) {
                                    state.commitSpeed(speedValue)
                                    currentSubMenu = null
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = speedLabel,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 15.sp,
                                color = if (selected) c.primary else c.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = c.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
                "audio" -> {
                    // Header with back arrow
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .pressable { currentSubMenu = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = c.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Audio track",
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = c.onSurface,
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    val videoId = state.current?.id
                    val cachedInfo = remember(videoId) { videoId?.let { StreamCache.get(it) } }
                    val audioStreams = remember(cachedInfo) {
                        cachedInfo?.audioStreams.orEmpty().filter { it.isUrl }
                    }

                    if (audioStreams.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Default (Original)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = c.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = c.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else {
                        val tracks = audioStreams.distinctBy { it.audioTrackName ?: it.audioTrackType?.name ?: "Default" }
                        tracks.forEach { audio ->
                            val trackName = audio.audioTrackName ?: audio.audioTrackType?.name ?: "Default"
                            val isSelected = state.selectedAudioLanguage == trackName || (state.selectedAudioLanguage == null && tracks.indexOf(audio) == 0)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressable(pressedScale = 0.98f) {
                                        state.setAudioLanguage(trackName)
                                        currentSubMenu = null
                                    }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = trackName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 15.sp,
                                    color = if (isSelected) c.primary else c.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = c.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                "more" -> {
                    // Header with back arrow
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .pressable { currentSubMenu = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = c.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "More options",
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = c.onSurface,
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // Loop Video
                    CompactPlayerSwitchRow(
                        icon = null,
                        title = "Loop video",
                        checked = state.loopEnabled,
                        drawableRes = R.drawable.ic_player_loop,
                        onCheckedChange = { state.toggleLoop() },
                    )

                    // Autoplay
                    CompactPlayerSwitchRow(
                        icon = Icons.Rounded.PlayCircle,
                        title = "Autoplay next",
                        checked = state.autoPlayEnabled,
                        onCheckedChange = { state.toggleAutoPlay() },
                    )

                    // Video aspect (Zoom to fill)
                    CompactPlayerSwitchRow(
                        icon = if (state.zoomToFill) Icons.Rounded.AspectRatio else Icons.Rounded.CropFree,
                        title = "Zoom to fill screen",
                        checked = state.zoomToFill,
                        onCheckedChange = { state.toggleZoomToFill() },
                    )

                    // Manual Zoom Reset
                    if (state.videoZoom > 1.05f) {
                        CompactPlayerNavRow(
                            icon = Icons.Rounded.ZoomIn,
                            title = "Reset manual zoom",
                            value = "${(state.videoZoom * 10).toInt() / 10f}×",
                            onClick = { state.resetZoom() },
                        )
                    }
                }
                "region" -> {
                    // Header with back arrow
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .pressable { currentSubMenu = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = c.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Content region",
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = c.onSurface,
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    ContentRegion.supportedRegions.forEach { item ->
                        val selected = ContentRegion.code == item.code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressable(pressedScale = 0.98f) {
                                    ContentRegion.setRegion(item.code)
                                    currentSubMenu = null
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 15.sp,
                                    color = if (selected) c.primary else c.onSurface,
                                )
                                if (item.code != ContentRegion.AUTO_REGION) {
                                    Text(
                                        text = "Region code: ${item.code}",
                                        fontSize = 12.sp,
                                        color = c.onSurfaceVariant,
                                    )
                                }
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = c.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
                else -> {
                    Text(
                        "Playback settings",
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = c.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )

                    Spacer(Modifier.height(4.dp))

                    // 1. Quality
                    val qualityText = (state.decodedHeight?.let { "${it}p" } ?: state.details?.quality) ?: "Auto"
                    CompactPlayerNavRow(
                        icon = Icons.Rounded.HighQuality,
                        title = "Quality",
                        value = if (QualityPreference.height == null) "Auto • $qualityText" else qualityText,
                        onClick = {
                            onDismiss()
                            state.sheet = PlayerSheet.Quality
                        },
                    )

                    // 2. Playback Speed
                    val speedDisplay = if (state.speed == 1f) "Normal" else "${state.speed}×"
                    CompactPlayerNavRow(
                        icon = null,
                        title = "Playback speed",
                        value = speedDisplay,
                        drawableRes = R.drawable.ic_player_speed,
                        onClick = { currentSubMenu = "speed" },
                    )

                    // 3. Captions
                    val captionsText = if (state.selectedSubtitleLanguage != null) "On" else "Off"
                    CompactPlayerNavRow(
                        icon = Icons.Rounded.ClosedCaption,
                        title = "Captions",
                        value = captionsText,
                        onClick = {
                            onDismiss()
                            state.sheet = PlayerSheet.Subtitles
                        },
                    )

                    // 4. Audio track (YouTube Multi-language Dubs)
                    val audioTrackVal = state.selectedAudioLanguage?.uppercase() ?: "Default"
                    CompactPlayerNavRow(
                        icon = Icons.Rounded.Audiotrack,
                        title = "Audio track",
                        value = audioTrackVal,
                        onClick = { currentSubMenu = "audio" },
                    )

                    // 5. Content region (Country/location for feed and search)
                    CompactPlayerNavRow(
                        icon = Icons.Rounded.Public,
                        title = "Content region",
                        value = ContentRegion.displaySummary,
                        onClick = { currentSubMenu = "region" },
                    )

                    // 6. More options (loop, autoplay, aspect, zoom)
                    CompactPlayerNavRow(
                        icon = Icons.Rounded.Tune,
                        title = "More options",
                        value = "",
                        onClick = { currentSubMenu = "more" },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactPlayerNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    value: String,
    onClick: () -> Unit,
    drawableRes: Int? = null,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (drawableRes != null) PlayerDrawableIcon(drawableRes, null, c.onSurface, Modifier.size(32.dp))
        else icon?.let { Icon(it, null, tint = c.onSurface, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = c.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontSize = 13.5.sp,
            color = c.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = c.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CompactPlayerSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    drawableRes: Int? = null,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f) { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (drawableRes != null) PlayerDrawableIcon(drawableRes, null, c.onSurface, Modifier.size(22.dp))
        else icon?.let { Icon(it, null, tint = c.onSurface, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = c.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
            modifier = Modifier.scale(0.85f),
        )
    }
}

@Composable
private fun BoxScope.ErrorPanel(message: String, onRetry: () -> Unit, onCollapse: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)))
    Column(
        Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(message, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.pressable(onClick = onRetry).clip(CircleShape).background(Color.White).padding(horizontal = 18.dp, vertical = 9.dp),
        ) { Text("Try again", color = Color(0xFF141414), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
    }
}

@Composable
private fun GlassButton(
    icon: ImageVector?,
    description: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    drawableRes: Int? = null,
    size: Dp = 38.dp,
    iconSize: Dp = 22.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (drawableRes != null) PlayerDrawableIcon(drawableRes, description, Color.White, Modifier.size(iconSize))
        else icon?.let { Icon(it, description, tint = Color.White, modifier = Modifier.size(iconSize)) }
    }
}

private val SeekStepMs: Long get() = SeekConfig.stepMs
/** A burst stays open this long after its last tap; further taps on that side add to it. */
private const val SeekBurstWindowMs = 600L

/**
 * Double-tap seek feedback: a glass pill at 22% / 78% across the video with the rewind /
 * fast-forward glyph and the burst's running total. Springs in; every tap ([taps] changes)
 * squishes it, dashes the glyph the way it seeks and throws sparks (the like Pop's burst).
 * Reduced motion: fade only.
 */
@Composable
private fun SeekBurstPill(side: Int, seconds: Int, taps: Int, active: Boolean, fullscreen: Boolean) {
    val reduced = rememberReducedMotion()
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(if (reduced) 0 else if (active) 140 else 200, easing = Motion.Emphasized),
        label = "seekPillAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (active || reduced) 1f else 0.4f,
        animationSpec = if (active) spring(dampingRatio = 0.5f, stiffness = 500f) else tween(200, easing = Motion.Emphasized),
        label = "seekPillScale",
    )
    val squishX = remember { Animatable(1f) }
    val squishY = remember { Animatable(1f) }
    val dash = remember { Animatable(0f) }
    val sparks = remember { Animatable(1f) } // 0→1 = sparks flying; 1 = none
    LaunchedEffect(taps) {
        if (taps == 0 || reduced) return@LaunchedEffect
        launch { squishX.animateTo(1f, keyframes { durationMillis = 440; 1.14f at 120; 0.96f at 270 }) }
        launch { squishY.animateTo(1f, keyframes { durationMillis = 440; 0.88f at 120; 1.04f at 270 }) }
        launch { dash.animateTo(0f, keyframes { durationMillis = 380; 1f at 115 }) }
        launch { sparks.snapTo(0f); sparks.animateTo(1f, tween(520, easing = Motion.Emphasized)) }
    }
    val sparkColor = MaterialTheme.colorScheme.primary
    val font = if (fullscreen) 20.sp else 16.sp
    Row(
        Modifier
            .layout { measurable, constraints ->
                val pill = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val centerX = constraints.maxWidth * (if (side < 0) 0.22f else 0.78f)
                    pill.place((centerX - pill.width / 2f).roundToInt(), (constraints.maxHeight - pill.height) / 2)
                }
            }
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale * squishX.value
                scaleY = scale * squishY.value
            }
            .drawBehind {
                val t = sparks.value
                if (t >= 1f) return@drawBehind
                // Eight sparks, from just inside the pill out past its edge, fading.
                val reach = size.width / 2f + 16.dp.toPx()
                for (i in 0 until 8) {
                    val a = (i / 8f + 1f / 16f) * 2f * PI.toFloat()
                    val from = Offset(cos(a) * size.width * 0.36f, sin(a) * 12.dp.toPx())
                    val to = Offset(cos(a) * reach, sin(a) * reach * 0.62f)
                    drawCircle(
                        (if (i % 2 == 0) sparkColor else Color.White).copy(alpha = 1f - t),
                        radius = 3.5.dp.toPx() * (1f - 0.65f * t),
                        center = center + from + (to - from) * t,
                    )
                }
            }
            .height(if (fullscreen) 48.dp else 40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.42f))
            .padding(start = if (fullscreen) 14.dp else 11.dp, end = if (fullscreen) 18.dp else 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (side < 0) Icons.Rounded.FastRewind else Icons.Rounded.FastForward,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(if (fullscreen) 28.dp else 22.dp)
                .graphicsLayer { translationX = side * 6.dp.toPx() * dash.value },
        )
        Spacer(Modifier.width(8.dp))
        Text(if (side < 0) "−" else "+", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = font)
        RollingText("$seconds", Color.White, up = true, fontSize = font)
    }
}

/**
 * Play/pause that changes shape with state: a squircle while playing, a circle when
 * paused (Material 3 Expressive shape morph). Shows a spinner while buffering.
 */
@Composable
fun MorphPlayButton(
    playing: Boolean,
    buffering: Boolean,
    size: Dp,
    container: Color,
    content: Color,
    enabled: Boolean = true,
    backdrop: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val reduced = rememberReducedMotion()
    val radius by animateDpAsState(
        targetValue = if (playing) size * 0.33f else size / 2,
        animationSpec = if (reduced) tween(0) else spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "playShape",
    )
    Box(
        modifier = Modifier
            .size(size)
            .pressable(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(radius))
            .then(backdrop)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        if (buffering) {
            CircularProgressIndicator(color = content, strokeWidth = 3.dp, modifier = Modifier.size(size * 0.42f))
        } else {
            PlayerPlayPauseGlyph(playing = playing, color = content, size = 63.dp)
        }
    }
}

private val ScrubberActiveRed = Color(0xFFFF0033)

/** Touch area tight over the progress line so scrolling chips below never accidentally scrubs. */
internal val EdgeScrubTouchPadAbove = 8.dp
internal val EdgeScrubTouchPadBelow = 2.dp
internal val EdgeScrubTouchHeight = EdgeScrubTouchPadAbove + EdgeScrubTouchPadBelow
/** Current / total row above the bar (fullscreen chip sets the row height). */
private val EdgeScrubTimeRowBottomPad = EdgeScrubTouchPadAbove + 6.dp
private val EdgeScrubTimeRowHeight = 34.dp
/** Landscape fullscreen: shared horizontal inset for the time row and scrubber. */
private val FullscreenControlsHorizontalPad = 20.dp

/** Equal left/right margin: notch cutout often hits one side only — balance to the larger inset. */
private fun Modifier.fullscreenControlsHorizontalInsets(): Modifier = composed {
    val layoutDirection = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout.asPaddingValues()
    val balanced = maxOf(
        cutout.calculateStartPadding(layoutDirection),
        cutout.calculateEndPadding(layoutDirection),
    )
    padding(
        start = balanced + FullscreenControlsHorizontalPad,
        end = balanced + FullscreenControlsHorizontalPad,
    )
}
private val FullscreenControlsVerticalPad = 8.dp
private val FullscreenScrubTrackHeight = 4.dp
private val FullscreenScrubTouchHeight = 30.dp
private val FullscreenScrubBottomPad = 6.dp
private val FullscreenControlsLift = 10.dp
/** Bottom of the time row from the video edge (controls overlay includes [FullscreenControlsVerticalPad]). */
private val FullscreenTimeRowBottomFromEdge: Dp
    get() = FullscreenControlsVerticalPad + EdgeScrubTimeRowBottomPad + FullscreenControlsLift
/** Knob radii (not diameters). */
private val FullscreenScrubKnob = 5.dp
private val FullscreenScrubKnobDragging = 6.dp

/**
 * Scrubber: buffered and played tracks, optional knob. On the video stage the bar is pinned to
 * the bottom edge (full width); [active] turns the played track red and shows the knob. Dragging
 * can follow the finger ([relativeDrag] false) or slide from where you started (true).
 */
@Composable
private fun Scrubber(
    segments: List<SponsorBlock.Segment>,
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onPreview: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onDraggingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    active: Boolean = true,
    relativeDrag: Boolean = false,
    edgeToEdge: Boolean = false,
    edgeTouchPadAbove: Dp = EdgeScrubTouchPadAbove,
    tapToSeek: Boolean = true,
    track: Color = Color.White.copy(alpha = 0.3f),
    buffered: Color = Color.White.copy(alpha = 0.5f),
    played: Color = Color.White,
    trackHeight: Dp = 4.dp,
    touchHeight: Dp = 24.dp,
    knob: Dp = 7.dp,
    knobDragging: Dp = 9.dp,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val currentPositionMs by rememberUpdatedState(positionMs)
    val duration = durationMs.coerceAtLeast(1L)
    val playedFraction = dragFraction ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedMs.toFloat() / duration).coerceIn(0f, 1f)
    val dragging = dragFraction != null
    // Controls visible, or a lone drag while they're hidden: red + thumb, no overlay.
    val chromeActive = active || dragging
    val playedColor by animateColorAsState(
        if (chromeActive) ScrubberActiveRed else played,
        tween(160, easing = Motion.Emphasized),
        label = "scrubPlayed",
    )
    val knobRadius by animateDpAsState(if (dragging) knobDragging else knob, label = "knob")
    val h = if (edgeToEdge) 2.dp else trackHeight
    val insetEnds = !edgeToEdge
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(touchHeight)
            .pointerInput(enabled, durationMs, relativeDrag, tapToSeek, edgeToEdge, trackHeight) {
                if (!enabled || durationMs <= 0) return@pointerInput
                val slop = viewConfiguration.touchSlop * 0.35f
                val endInset = if (edgeToEdge) 0f else (trackHeight / 2).toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val width = (size.width - endInset * 2f).coerceAtLeast(1f)
                    var fraction = (currentPositionMs.toFloat() / duration).coerceIn(0f, 1f)
                    var dragged = false
                    var slopAccum = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: run {
                            if (dragged) onDraggingChanged(false)
                            break
                        }
                        if (!change.pressed) {
                            if (dragged) onSeek((fraction * durationMs).toLong())
                            else if (tapToSeek) {
                                val x = (change.position.x - endInset).coerceIn(0f, width)
                                onSeek((x / width * durationMs).toLong())
                            }
                            dragFraction = null
                            if (dragged) onDraggingChanged(false)
                            break
                        }
                        val delta = change.positionChange()
                        if (!dragged) {
                            slopAccum += delta
                            if (slopAccum.getDistance() < slop) continue
                            dragged = true
                            onDraggingChanged(true)
                            fraction = (currentPositionMs.toFloat() / duration).coerceIn(0f, 1f)
                            dragFraction = fraction
                            onPreview((fraction * durationMs).toLong())
                        } else {
                            fraction = if (relativeDrag) {
                                (fraction + delta.x / width).coerceIn(0f, 1f)
                            } else {
                                ((change.position.x - endInset) / width).coerceIn(0f, 1f)
                            }
                            dragFraction = fraction
                            onPreview((fraction * durationMs).toLong())
                        }
                        change.consume()
                    }
                }
            },
    ) {
        val trackH = h.toPx()
        val centerY = if (edgeToEdge) edgeTouchPadAbove.toPx() else size.height / 2f
        val y = centerY - trackH / 2f
        val r = CornerRadius(trackH / 2f)
        val endInset = if (insetEnds) trackH / 2f else 0f
        val trackW = size.width - endInset * 2f
        val left = endInset
        drawRoundRect(track, Offset(left, y), Size(trackW, trackH), r)
        drawRoundRect(buffered, Offset(left, y), Size(trackW * bufferedFraction, trackH), r)
        drawRoundRect(playedColor, Offset(left, y), Size(trackW * playedFraction.coerceAtLeast(0f), trackH), r)
        segments.forEach { seg ->
            val x0 = left + trackW * (seg.startMs.toFloat() / duration).coerceIn(0f, 1f)
            val x1 = left + trackW * (seg.endMs.toFloat() / duration).coerceIn(0f, 1f)
            if (x1 > x0) drawRoundRect(seg.category.color, Offset(x0, y), Size(x1 - x0, trackH), r)
        }
        if (chromeActive) {
            val knobR = knobRadius.toPx()
            val knobX = (left + trackW * playedFraction).coerceIn(left + knobR, left + trackW - knobR)
            drawCircle(playedColor, radius = knobR, center = Offset(knobX, centerY))
        }
    }
}

@Composable
private fun GestureHUD(
    icon: ImageVector,
    label: String,
    percent: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(
                text = if (percent < 0f) "$label: Auto" else "$label: ${(percent * 100).roundToInt().coerceIn(0, 100)}%",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
