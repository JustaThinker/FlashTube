package com.thinker.flashtube

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import android.content.Intent
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.ui.res.painterResource
import com.thinker.flashtube.ui.player.components.PlayerDrawableIcon
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.exp
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW

// ── Geometry shared with the morph (MainActivity builds MorphGeometry from these) ──

// The video runs edge to edge as a plain rectangle, right under the status bar (Derek).
internal val StageTopGap = 0.dp
internal val StageSideGap = 0.dp
internal val StageRadius = 0.dp
internal val MiniHeight = 112.dp
internal val MiniRadius = 12.dp
internal val MiniThumbStart = 0.dp
internal val MiniThumbWidth = 224.dp
internal val MiniThumbHeight = 126.dp
internal val MiniThumbRadius = 12.dp

val LocalAmbientColor = staticCompositionLocalOf { Color.Unspecified }
/**
 * Portrait stage shape: tall videos can use up to 4:5; wide ones letterbox inside a 16:9 frame
 * (2.35:1 and wider no longer squash the whole player flat).
 */
internal const val StageMinAspect = 0.8f
internal const val StageMaxAspect = 2.4f
/** Wider than this: stage stays 16:9 and the picture letterboxes top/bottom. */
internal const val StageFrameAspect = 16f / 9f
/** Morph progress after which the details and tint start fading in (tuning dial). */
private const val DetailsFadeStart = 0.55f

// ── Full player (portrait) ─────────────────────────────────────────────────────

@Composable
fun PlayerScreen(
    state: PlayerState,
    morph: PlayerMorph,
    onOpen: (VideoItem) -> Unit,
    onCollapse: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val item = state.current ?: return
    // Collapsing (or collapsed): the player gives up the screen immediately, so the
    // mini-player underneath answers taps and swipes even while the animation finishes.
    val interactive = state.expanded || morph.dragging
    val showDetails by remember { derivedStateOf { state.expanded || morph.dragging || morph.progress.value > DetailsFadeStart } }
    val details = state.details
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var descriptionOpen by remember(item.id) { mutableStateOf(false) }

    val context = LocalContext.current
    var dominantColor by remember(item.id) { mutableStateOf(c.primaryContainer) }

    // Seed from thumbnail immediately on load
    LaunchedEffect(item.thumbnail) {
        val thumb = item.thumbnail ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context).data(thumb).size(32).allowHardware(false).build()
                val bm = (context.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap(1, 1)
                bm?.getPixel(0, 0)?.let { pixel ->
                    withContext(Dispatchers.Main) { dominantColor = Color(pixel) }
                }
            }
        }
    }

    // Live frame sampling: sample the TextureView ~1s per second while playing
    LaunchedEffect(state.isPlaying, state.expanded) {
        if (!state.isPlaying || !state.expanded || !AmbientMode.enabled) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1000)
            if (!state.isPlaying || !state.expanded || !AmbientMode.enabled) break
            val sampled = SharedVideoTexture.sampleAmbientColor()
            if (sampled != null && sampled != dominantColor) dominantColor = sampled
        }
    }

    val isDark = c.surface.luminance() < 0.4f
    val ambientColor by animateColorAsState(
        targetValue = if (AmbientMode.enabled && isDark) dominantColor else c.surface,
        animationSpec = tween(500, easing = LinearEasing),
        label = "ambient",
    )

    val pageColor = c.surface
    val miniColor = c.surfaceContainerHigh
    Box(
        Modifier
            .fillMaxSize()
            // The morph's moving frame: the mini-player card growing to the full screen.
            .graphicsLayer {
                val g = morph.geometry ?: return@graphicsLayer
                val p = morph.progress.value
                // The page itself becomes the mini-player card. Its bottom edge holds at the
                // card's bottom until well into the open (and from there on the way down), so
                // it never sweeps over the tab bar.
                val full = Rect(Offset.Zero, size)
                val frame = Rect(
                    lerpF(g.miniCard.left, full.left, p), lerpF(g.miniCard.top, full.top, p),
                    lerpF(g.miniCard.right, full.right, p), lerpF(g.miniCard.bottom, full.bottom, ramp(p, 0.3f)),
                )
                shape = RoundRectClip(frame, lerpF(g.miniRadius, 0f, p.coerceIn(0f, 1f)))
                clip = true
            }
            .drawBehind {
                val p = morph.progress.value
                // Opaque throughout: at the small end it IS the mini-player card (same color).
                drawRect(lerpColor(miniColor, pageColor, ramp(p, 0f, 0.6f)))
            }
            // Open: a hit target over the whole screen, so taps never reach the tabs beneath.
            // Collapsing: no hit target at all, so touches fall through to the mini-player.
            .then(if (interactive) Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } } else Modifier),
    ) {
        val relatedListState = rememberLazyListState()
        val scrollAlpha by remember {
            derivedStateOf {
                val firstIndex = relatedListState.firstVisibleItemIndex
                val firstOffset = relatedListState.firstVisibleItemScrollOffset
                if (firstIndex > 0) 0f else (1f - (firstOffset / 300f)).coerceIn(0f, 1f)
            }
        }

        // Ambient glow behind the video: thumbnail's dominant color fading into the page surface (Dark Mode only).
        // Fades away smoothly as the user scrolls down the details page.
        if (isDark && AmbientMode.enabled) {
            Box(
                Modifier.fillMaxWidth().height(520.dp)
                    .graphicsLayer { alpha = ramp(morph.progress.value, DetailsFadeStart) * scrollAlpha }
                    .background(Brush.verticalGradient(listOf(
                        ambientColor.copy(alpha = 0.6f),
                        c.surface.copy(alpha = 0f),
                    ))),
            )
        }
        CompositionLocalProvider(LocalAmbientColor provides ambientColor) {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(top + StageTopGap))
                // Pinned: the video stays put while the details scroll beneath it. Sit above the
                // details list so the bottom scrubber/thumb aren't covered at the seam.
                Box(Modifier.zIndex(2f)) {
                    VideoStage(state, morph, onCollapse, onFullscreen, interactive)
                }
                // While collapsing the details are invisible anyway; leaving them out keeps them
                // from catching touches meant for the mini-player.
                if (!showDetails) Spacer(Modifier.weight(1f)) else {
                    val related = details?.related.orEmpty()
                    val nearEndRelated by remember {
                        derivedStateOf {
                            val last = relatedListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            last >= relatedListState.layoutInfo.totalItemsCount - 3
                        }
                    }
                    LaunchedEffect(nearEndRelated, related.size) {
                        if (nearEndRelated && related.isNotEmpty()) {
                            state.loadMoreRelated()
                        }
                    }

                    LazyColumn(
                        state = relatedListState,
                        modifier = Modifier
                            .weight(1f)
                            .zIndex(0f)
                            .nestedScroll(morph.nestedScroll)
                            .graphicsLayer {
                            // The details ride along under the video's bottom edge (so they never
                            // overlap it mid-morph) and only fade in for the last stretch.
                            val p = morph.progress.value
                            alpha = ramp(p, DetailsFadeStart)
                            val g = morph.geometry
                            translationY = if (g != null && p < 0.98f) {
                                lerpF(g.miniThumb.bottom - g.stage.bottom, 0f, p)
                            } else {
                                0f
                            }
                        },
                    contentPadding = PaddingValues(bottom = bottom + 32.dp),
                ) {
                    item(key = "info") {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Spacer(Modifier.height(18.dp))
                            Text(
                                item.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                lineHeight = 24.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(6.dp))
                            val stats = listOfNotNull(
                                item.views.takeIf { it >= 0 }?.let { "${formatCount(it)} views" },
                                item.uploaded?.ifBlank { null },
                                details?.likes?.takeIf { it > 0 }?.let { "${formatCount(it)} likes" },
                            ).joinToString(" · ")
                            val hasDescription = !details?.description.isNullOrBlank()
                            Row(
                                modifier = if (hasDescription) Modifier.pressable(pressedScale = 0.98f) { state.sheet = PlayerSheet.Description } else Modifier,
                            ) {
                                Text(stats, color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Normal)
                                if (hasDescription) {
                                    Text(
                                        "  …more",
                                        color = c.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val openChannel = LocalChannelOpener.current
                                val openCollabSheet = LocalCollaboratorsOpener.current
                                val collabChannels = details?.item?.collabChannels?.takeIf { it.size >= 2 }
                                    ?: item.collabChannels.takeIf { it.size >= 2 }
                                    ?: emptyList()
                                val channelName = if (collabChannels.size >= 2) {
                                    formatCollabNames(collabChannels)
                                } else {
                                    details?.item?.channel?.ifBlank { null } ?: item.channel
                                }
                                val channelUrl = if (collabChannels.size >= 2) {
                                    collabChannels.firstOrNull()?.url ?: details?.item?.channelUrl ?: item.channelUrl
                                } else {
                                    details?.item?.channelUrl ?: item.channelUrl
                                }

                                // Avatar + name open the channel (or collaborators sheet if multiple collaborators).
                                Row(
                                    Modifier.weight(1f).pressable(enabled = channelUrl != null || collabChannels.size >= 2, pressedScale = 0.97f) {
                                        if (collabChannels.size >= 2) {
                                            openCollabSheet(collabChannels)
                                        } else {
                                            channelUrl?.let(openChannel)
                                        }
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ChannelAvatar(
                                        url = details?.item?.avatar ?: item.avatar,
                                        name = channelName,
                                        size = 40,
                                        collabChannels = collabChannels,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(channelName, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (collabChannels.size >= 2) {
                                            Text("Collaborators", color = c.onSurfaceVariant, fontSize = 12.sp)
                                        } else {
                                            details?.subscribers?.takeIf { it > 0 }?.let {
                                                Text("${formatCount(it)} subscribers", color = c.onSurfaceVariant, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                                state.accountState?.subscribed?.let { subscribed ->
                                    SubscribeButton(subscribed, onClick = state::toggleSubscribe)
                                }
                            }
                            PlayerActions(state)
                            if (state.mixQueueActive || state.queue.isNotEmpty()) {
                                QueueCard(state, onOpen = { state.sheet = PlayerSheet.Queue })
                            }
                            CommentsCard(state.comments, onOpen = { state.sheet = PlayerSheet.Comments })
                        }
                    }
                    if (related.isNotEmpty()) {
                        item(key = "moreTitle") { SectionTitle("More videos") }
                        items(related, key = { "rel-" + it.id }) { video ->
                            Box(Modifier.padding(bottom = 12.dp)) {
                                VideoCard(video, onClick = { onOpen(video) })
                            }
                        }
                        if (state.loadingMoreRelated) {
                            item(key = "rel_loading") {
                                Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) {
                                    CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                                }
                            }
                        }
                    } else if (state.loading) {
                        item(key = "loading") {
                            Box(Modifier.fillMaxWidth().padding(top = 40.dp), Alignment.Center) {
                                CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
            }
        }
        if (interactive && (state.mixQueueActive || state.queue.isNotEmpty()) && state.sheet == null) {
            QueuePill(
                state,
                onOpen = { state.sheet = PlayerSheet.Queue },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 24.dp),
            )
        }
        // Swiping home from the bottom edge never scrolls the details (open player only: while
        // collapsing, touches must still fall through to the mini-player).
        if (interactive) BottomGestureGuard(Modifier.align(Alignment.BottomCenter))
        // Comments open as a panel whose top sits just under the video.
        if (state.sheet == PlayerSheet.Comments) {
            val screenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
            val videoBottom = top + StageTopGap + (screenW - StageSideGap * 2) / state.stageAspect
            val item = state.details?.item
            CommentsPanel(
                state.comments,
                partialTop = videoBottom + 10.dp,
                onClose = { state.sheet = null },
                creatorAvatar = item?.avatar,
                creatorName = item?.channel.orEmpty(),
                onSeekToTimestamp = { sec -> state.seekTo(sec * 1000L) },
            )
        }
        // Description opens as a panel whose top sits just under the video.
        if (state.sheet == PlayerSheet.Description) {
            val screenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
            val videoBottom = top + StageTopGap + (screenW - StageSideGap * 2) / state.stageAspect
            DescriptionPanel(
                state = state,
                partialTop = videoBottom + 10.dp,
                onClose = { state.sheet = null },
            )
        }

        // Action popup pill at bottom center of the device screen
        ActionMessage(
            state = state,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottom + 28.dp)
                .zIndex(100f),
        )
    }
}

/**
 * The pinned video in portrait: the morph's container-transform layer around [VideoContent].
 * Swiping it down minimizes (the morph). Swiping it up while open goes fullscreen, as in the
 * YouTube app (Derek, 2026-10-10): the video swells under the finger, and past
 * [FullscreenPull] or with a flick it turns to landscape; short of that it eases back.
 */
@Composable
private fun VideoStage(state: PlayerState, morph: PlayerMorph, onCollapse: () -> Unit, onFullscreen: () -> Unit, interactive: Boolean) {
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    // The height follows the video; when its real shape arrives the change eases in.
    val aspect by animateFloatAsState(
        state.stageAspect,
        if (reduced) snap() else spring(dampingRatio = 1f, stiffness = 380f),
        label = "stageAspect",
    )
    val pullUp = remember { Animatable(0f) }
    val pullDistance = with(LocalDensity.current) { FullscreenPull.toPx() }
    // Each gesture's first move decides: up on the open player pulls toward fullscreen,
    // anything else drives the morph.
    var decided by remember { mutableStateOf(false) }
    var pulling by remember { mutableStateOf(false) }
    val stage = Modifier
        .padding(horizontal = StageSideGap)
        .fillMaxWidth()
        .aspectRatio(aspect)
        .graphicsLayer {
            // Rubber band: up to 6% bigger, ever more slowly, growing down from the top edge.
            val k = 1f + 0.06f * (1f - exp(-pullUp.value / (pullDistance * 1.5f)))
            scaleX = k
            scaleY = k
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
        // Container transform: at progress 0 the video's centred 16:9 part sits exactly
        // on the mini-player's thumbnail (scale, position and corner radius); at 1 the
        // whole stage is home.
        .graphicsLayer {
            val g = morph.geometry
            val p = morph.progress.value
            if (g != null) {
                val f = g.videoFrame(size, p)
                scaleX = f.scale
                scaleY = f.scale
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = f.offset.x
                translationY = f.offset.y
                // Radius in layer space: divided by the scale so the small version
                // shows the thumbnail's 17 dp corners, not a shrunken 28 dp.
                shape = RoundRectClip(f.window, lerpF(g.thumbRadius / f.startScale, g.stageRadius, p.coerceIn(0f, 1f)))
            } else {
                shape = RoundedCornerShape(StageRadius)
            }
            // Morph shape clips the stage; keep clip off so the seam scrub/thumb aren't cropped on collapse.
            clip = false
            // Only while it floats mid-morph; landed, it is a flat edge-to-edge rectangle.
            shadowElevation = 14.dp.toPx() * (1f - p).coerceIn(0f, 1f)
            ambientShadowColor = Color.Black.copy(alpha = 0.25f)
            spotShadowColor = Color.Black.copy(alpha = 0.35f)
        }
        .draggable(
            state = rememberDraggableState { delta ->
                if (!decided) {
                    decided = true
                    pulling = delta < 0f && state.expanded && !state.fullscreen && morph.progress.value >= 0.999f
                }
                if (pulling) scope.launch(start = CoroutineStart.UNDISPATCHED) { pullUp.snapTo((pullUp.value - delta).coerceAtLeast(0f)) }
                else morph.dragBy(delta)
            },
            orientation = Orientation.Vertical,
            onDragStopped = { velocity ->
                val wasPulling = pulling
                decided = false
                pulling = false
                if (wasPulling) {
                    if (pullUp.value > pullDistance || velocity < -PlayerMorph.FLING_PX_PER_S) onFullscreen()
                    if (reduced) pullUp.snapTo(0f) else pullUp.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
                } else {
                    morph.settle(velocity, reduced)
                }
            },
        )
    // Controls appear only once the video has nearly landed, so they never ride the morph.
    val controlsAlpha = { ramp(morph.progress.value, 0.7f) }
    VideoContent(
            state = state,
            fullscreen = false,
            interactive = interactive,
            // A tap on the shrinking video brings the player straight back.
            onInactiveTap = { state.expanded = true },
            onCollapse = onCollapse,
            onToggleFullscreen = onFullscreen,
            controlsAlpha = controlsAlpha,
            portraitEdgeScrub = true,
            modifier = stage,
        )
}

/** How far a swipe up on the open video travels before it goes fullscreen on release. */
private val FullscreenPull = 96.dp

// ── Account actions (signed in) ────────────────────────────────────────────────

@Composable
private fun SubscribeButton(subscribed: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    val ambient = LocalAmbientColor.current
    val isDark = c.surface.luminance() < 0.4f
    val hasAmbient = AmbientMode.enabled && isDark && ambient != Color.Unspecified && ambient != c.surface

    val radius by animateDpAsState(if (subscribed) 14.dp else 18.dp, if (reduced) tween(0) else spring(dampingRatio = 0.6f, stiffness = 500f), label = "subShape")
    val shape = RoundedCornerShape(radius)

    val baseSubscribed = if (hasAmbient) lerpColor(c.secondaryContainer, ambient.copy(alpha = 0.35f), 0.22f) else c.secondaryContainer
    val baseUnsubscribed = if (hasAmbient) lerpColor(c.onSurface, ambient.copy(alpha = 0.88f), 0.18f) else c.onSurface
    val shadowColor = if (hasAmbient) ambient else Color.Black

    Row(
        modifier = Modifier
            .squishOn(subscribed)
            .shadow(
                elevation = if (hasAmbient) 3.dp else 0.dp,
                shape = shape,
                ambientColor = shadowColor.copy(alpha = if (hasAmbient) 0.50f else 0.15f),
                spotColor = shadowColor.copy(alpha = if (hasAmbient) 0.35f else 0.20f),
            )
            .height(36.dp)
            .pressable(onClick = onClick)
            .background(if (subscribed) baseSubscribed else baseUnsubscribed, shape)
            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
            .padding(horizontal = if (subscribed) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (subscribed) {
            FlourishIcon(Icons.Rounded.NotificationsActive, null, active = subscribed, kind = Flourish.Ring, tint = c.onSecondaryContainer, size = 16.dp)
            Spacer(Modifier.width(5.dp))
        }
        Text(
            if (subscribed) "Subscribed" else "Subscribe",
            color = if (subscribed) c.onSecondaryContainer else c.surface,
            fontWeight = FontWeight.Bold, fontSize = 13.sp,
        )
    }
}

@Composable
private fun DownloadIconAnimated(
    downloading: Boolean,
    downloaded: Boolean,
    tint: Color,
) {
    if (downloaded) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    } else if (downloading) {
        val infiniteTransition = rememberInfiniteTransition(label = "dlAnim")
        val arrowOffsetY by infiniteTransition.animateFloat(
            initialValue = -3f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "arrowOffsetY",
        )
        Box(
            modifier = Modifier.size(width = 18.dp, height = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.ArrowDownward,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(15.dp)
                    .offset(y = arrowOffsetY.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(14.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
        }
    } else {
        Icon(Icons.Rounded.Download, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/**
 * The action row: like | dislike, save, share, loop and speed.
 * Compact pills matching the reverse-engineered modded player styling.
 */
@Composable
private fun PlayerActions(state: PlayerState) {
    val context = LocalContext.current
    val item = state.current
    var showDownloadSheet by remember { mutableStateOf(false) }

    if (showDownloadSheet && item != null) {
        DownloadQualitySheet(
            video = item,
            onDownloadStarted = { msg -> state.actionMessage = msg },
            onDismiss = { showDownloadSheet = false },
        )
    }

    Row(
        Modifier
            .padding(top = 10.dp)
            .bleed(20.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountActions(state)
        if (item != null) {
            val task = Downloads.tasks.find { it.videoId == item.id }
            val downloaded = task?.status == DownloadStatus.Complete
            val downloading = task?.status == DownloadStatus.Downloading || task?.status == DownloadStatus.Queued

            val targetPercent = ((task?.progress ?: 0f) * 100).toInt().coerceIn(0, 100)
            val animatedPercent by animateIntAsState(
                targetValue = targetPercent,
                animationSpec = tween(300),
                label = "dlPercent",
            )

            ActionPill(
                icon = null,
                label = if (downloading) "$animatedPercent%" else (if (downloaded) "Downloaded" else "Download"),
                active = downloaded || downloading,
                flourish = Flourish.Pop,
                shape = RoundedCornerShape(18.dp),
                description = if (downloaded) "Remove download" else "Download video",
                labelRollsUp = !downloading,
                customIcon = {
                    val tint = if (downloaded || downloading) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    DownloadIconAnimated(downloading = downloading, downloaded = downloaded, tint = tint)
                },
            ) {
                if (downloaded && task != null) {
                    Downloads.remove(task)
                    state.actionMessage = "Removed from Downloads"
                } else if (downloading && task != null) {
                    state.actionMessage = "Downloading video… $animatedPercent%"
                } else {
                    showDownloadSheet = true
                }
            }
            ActionPill(
                icon = Icons.Rounded.Share,
                label = "Share",
                active = false,
                flourish = Flourish.Pop,
                shape = RoundedCornerShape(18.dp),
                description = "Share video",
            ) {
                val url = "https://youtu.be/${item.id}"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
                context.startActivity(Intent.createChooser(intent, item.title))
            }

            // A-B Segment Loop Pill
            ActionPill(
                icon = Icons.Rounded.Sync,
                label = if (state.abLoopEnabled) "A-B On" else (if (state.pointA != null) "A-B Set" else "A-B Loop"),
                active = state.abLoopEnabled || state.pointA != null,
                flourish = Flourish.Pop,
                shape = RoundedCornerShape(18.dp),
                description = "A-B Segment Loop",
            ) {
                if (state.pointA == null) {
                    state.markPointA()
                } else if (state.pointB == null) {
                    state.markPointB()
                } else {
                    state.toggleAbLoop()
                }
            }

            // Stats for Nerds Pill
            ActionPill(
                icon = Icons.Rounded.Info,
                label = "Stats",
                active = state.statsForNerdsOpen,
                flourish = Flourish.Pop,
                shape = RoundedCornerShape(18.dp),
                description = "Stats for Nerds",
            ) {
                state.toggleStatsForNerds()
            }
        }
    }
}

/** Lays the content out [x] wider on each side than the parent's padding allows. */
private fun Modifier.bleed(x: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
    val extra = (x * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** Connected like | dislike group and Save (Watch later), always visible and compact. */
@Composable
private fun AccountActions(state: PlayerState) {
    val signedIn = state.accountState != null
    val like = state.accountState?.like ?: LikeStatus.None
    val likes = if (signedIn) state.displayLikes else (state.details?.likes ?: 0L)
    var lastLikes by remember { mutableStateOf(likes) }
    val rising = likes >= lastLikes
    SideEffect { lastLikes = likes }

    val baseDislikes = state.details?.dislikes ?: -1L
    val isDisliked = like == LikeStatus.Dislike
    val displayDislikes = if (baseDislikes >= 0) {
        if (isDisliked) baseDislikes + 1 else baseDislikes
    } else if (isDisliked) 1L else -1L

    // Dislike count label: invisible on loading (just like Likes), shows formatted count once > 0
    val dislikeLabel = displayDislikes.takeIf { it > 0 }?.let { formatCount(it) }

    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        // Like chip on the LEFT: [ ThumbUp Icon | 12K Count ]
        ActionPill(
            icon = if (like == LikeStatus.Like) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
            label = likes.takeIf { it > 0 }?.let { formatCount(it) },
            active = like == LikeStatus.Like,
            flourish = Flourish.Pop,
            labelRollsUp = rising,
            shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = 6.dp, bottomEnd = 6.dp),
            description = "Like",
        ) {
            if (signedIn) state.setLike(LikeStatus.Like)
            else state.actionMessage = "Sign in to like videos"
        }
        // Dislike chip on the RIGHT: [ Count Number | ThumbDown Icon ]
        ActionPill(
            icon = if (isDisliked) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
            label = dislikeLabel,
            active = isDisliked,
            flourish = Flourish.Droop,
            shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp),
            description = "Dislike",
            iconOnRight = true,
        ) {
            if (signedIn) state.setLike(LikeStatus.Dislike)
            else state.actionMessage = "Sign in to dislike"
        }
    }
    ActionPill(
        icon = null,
        label = if (state.inWatchLater) "Saved" else "Save",
        active = state.inWatchLater,
        flourish = Flourish.Drop,
        shape = RoundedCornerShape(18.dp),
        description = "Save to Watch later",
        customIcon = {
            PlayerDrawableIcon(
                R.drawable.ic_player_save,
                "Save to Watch later",
                if (state.inWatchLater) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                Modifier.size(23.dp),
            )
        },
    ) {
        if (signedIn) state.toggleWatchLater()
        else state.actionMessage = "Sign in to save videos"
    }
}

@Composable
private fun ActionPill(
    icon: ImageVector?,
    label: String?,
    active: Boolean,
    flourish: Flourish,
    shape: androidx.compose.ui.graphics.Shape,
    description: String,
    labelRollsUp: Boolean = true,
    iconOnRight: Boolean = false,
    customIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val ambient = LocalAmbientColor.current
    val isDark = c.surface.luminance() < 0.4f
    val hasAmbient = AmbientMode.enabled && isDark && ambient != Color.Unspecified && ambient != c.surface

    val baseInactive = if (hasAmbient) lerpColor(c.surfaceContainerHigh, ambient.copy(alpha = 0.35f), 0.22f) else c.surfaceContainerHigh
    val baseActive = if (hasAmbient) lerpColor(c.primary, ambient, 0.28f) else c.primary

    val bg by androidx.compose.animation.animateColorAsState(if (active) baseActive else baseInactive, tween(220), label = "pillBg")
    val tint by androidx.compose.animation.animateColorAsState(if (active) c.onPrimary else c.onSurface, tween(220), label = "pillInk")
    val shadowColor = if (hasAmbient) ambient else Color.Black

    val renderIcon = @Composable {
        if (customIcon != null) {
            customIcon()
        } else if (icon != null) {
            FlourishIcon(icon, description, active = active, kind = flourish, tint = tint, size = 18.dp, sparkColor = if (hasAmbient) ambient else c.primary)
        }
    }

    val renderLabel = @Composable {
        if (label != null) {
            if (labelRollsUp) {
                RollingText(label, tint, up = true, fontSize = 12.5.sp)
            } else {
                Text(label, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
            }
        }
    }

    Row(
        modifier = Modifier
            .squishOn(active)
            .shadow(
                elevation = if (active) 4.dp else (if (hasAmbient) 2.dp else 0.dp),
                shape = shape,
                ambientColor = shadowColor.copy(alpha = if (hasAmbient) 0.55f else 0.15f),
                spotColor = shadowColor.copy(alpha = if (hasAmbient) 0.40f else 0.20f),
            )
            .height(36.dp)
            .pressable(onClick = onClick)
            .background(bg, shape)
            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconOnRight) {
            if (label != null) {
                renderLabel()
                Spacer(Modifier.width(5.dp))
            }
            renderIcon()
        } else {
            renderIcon()
            if (label != null) {
                Spacer(Modifier.width(5.dp))
                renderLabel()
            }
        }
    }
}

/** Brief confirmation or failure note for account actions; clears itself. */
@Composable
fun ActionMessage(state: PlayerState, modifier: Modifier = Modifier) {
    val message = state.actionMessage ?: return
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2_200)
        state.actionMessage = null
    }
    val c = MaterialTheme.colorScheme
    Box(
        modifier
            .zIndex(100f)
            .clip(CircleShape)
            .background(c.inverseSurface.copy(alpha = 0.92f))
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) { Text(message, color = c.inverseOnSurface, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
}

// ── Mini player ────────────────────────────────────────────────────────────────

@Composable
fun MiniPlayer(state: PlayerState, onExpand: () -> Unit, modifier: Modifier = Modifier, showVideo: Boolean = true) {
    val c = MaterialTheme.colorScheme
    val item = state.current ?: return
    val shape = RoundedCornerShape(MiniRadius)

    var miniControlsVisible by remember { mutableStateOf(true) }
    var touchedAt by remember { mutableLongStateOf(0L) }
    fun touched() { touchedAt = System.currentTimeMillis() }

    LaunchedEffect(miniControlsVisible, state.isPlaying, touchedAt) {
        if (miniControlsVisible && state.isPlaying) {
            delay(3000)
            miniControlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .pressable(
                pressedScale = 0.99f,
                onClick = {
                    if (!miniControlsVisible) {
                        miniControlsVisible = true
                        touched()
                    } else {
                        onExpand()
                    }
                },
            )
            .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.20f), spotColor = Color.Black.copy(alpha = 0.28f))
            .border(1.dp, c.outlineVariant.copy(alpha = 0.25f), shape)
            .clip(shape)
            .background(Color.Black),
    ) {
        if (showVideo) {
            Thumbnail(item.thumbnail, Modifier.fillMaxSize(), radius = 0)
            Box(Modifier.fillMaxSize().aspectFit(state.stageAspect, cover = true)) {
                LocalVideoSurface.current(Modifier.fillMaxSize().aspectFit(state.videoAspect))
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(c.surfaceContainerHigh, c.surface))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = c.onSurfaceVariant.copy(alpha = 0.82f),
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        // Overlay controls layer (auto-hides after 3 seconds)
        AnimatedVisibility(
            visible = miniControlsVisible || state.isBuffering || !state.isPlaying,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent, Color.Black.copy(alpha = 0.40f)),
                            ),
                        ),
                )
                // Play / Pause button at TOP-LEFT
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 6.dp, top = 6.dp)
                        .size(40.dp)
                        .pressable(onClick = {
                            touched()
                            state.togglePlay()
                        }),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                        )
                    } else {
                        PlayerDrawableIcon(
                            if (state.isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play,
                            if (state.isPlaying) "Pause" else "Play",
                            Color.White,
                            Modifier.size(35.dp),
                        )
                    }
                }

                // Exit / Close (X) button at TOP-RIGHT
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 6.dp, top = 6.dp)
                        .size(32.dp)
                        .pressable(onClick = {
                            touched()
                            state.close()
                        }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close player",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Always visible progress bar
        val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.38f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(Color(0xFFFF0000)),
            )
        }
    }
}

@Composable
private fun MiniPlayerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        Modifier
            .then(modifier)
            .clip(CircleShape)
            .background(if (selected) Color(0xFFFF0000).copy(alpha = 0.88f) else Color.Black.copy(alpha = 0.48f))
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}



/** Stats for Nerds Overlay card displaying live ExoPlayer telemetry. */
@Composable
fun StatsForNerdsOverlay(
    state: PlayerState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Card(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.88f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, tint = Color.Green, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Stats for Nerds", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .pressable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            val current = state.current
            val details = state.details
            val format = state.playingFormat ?: "Auto"
            val textStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.5.sp, color = Color.Green)

            Text("Video ID: ${current?.id ?: "-"}", style = textStyle)
            Text("Resolution / Format: $format", style = textStyle)
            Text("Aspect Ratio: ${String.format("%.2f", state.videoAspect)}", style = textStyle)
            Text("Playback Speed: ${state.speed}x", style = textStyle)
            Text("Position: ${formatDuration(state.positionMs)} / ${formatDuration(state.durationMs)}", style = textStyle)
            Text("Buffer Health: ${formatDuration((state.bufferedMs - state.positionMs).coerceAtLeast(0L))}", style = textStyle)
            Text("Audio Language: ${state.selectedAudioLanguage ?: "Default"}", style = textStyle)
            Text("Captions Language: ${state.selectedSubtitleLanguage ?: "None"}", style = textStyle)
        }
    }
}
