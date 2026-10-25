package com.thinker.flashtube

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
internal val MiniHeight = 60.dp
internal val MiniRadius = 16.dp
internal val MiniThumbStart = 6.dp
internal val MiniThumbWidth = 96.dp
internal val MiniThumbHeight = 54.dp
internal val MiniThumbRadius = 10.dp
/**
 * Portrait stage shape: tall videos can use up to 4:5; wide ones letterbox inside a 16:9 frame
 * (2.35:1 and wider no longer squash the whole player flat).
 */
internal const val StageMinAspect = 0.8f
internal const val StageMaxAspect = 2.4f
/** Wider than this: stage stays 16:9 and the picture letterboxes top/bottom. */
internal const val StageFrameAspect = 16f / 9f
/** Audio only: the stage is a short strip holding just the controls (~164 dp on a Pixel). */
internal const val AudioStageAspect = 2.5f
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
    LaunchedEffect(item.thumbnail) {
        val thumb = item.thumbnail ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context).data(thumb).size(32).allowHardware(false).build()
                val bm = (context.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap(1, 1)
                bm?.getPixel(0, 0)?.let { pixel ->
                    withContext(Dispatchers.Main) {
                        dominantColor = Color(pixel)
                    }
                }
            }
        }
    }
    val ambientColor by animateColorAsState(dominantColor, tween(600), label = "ambient")

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
        // Ambient glow behind the video: thumbnail's dominant color fading into the page surface.
        Box(
            Modifier.fillMaxWidth().height(520.dp)
                .graphicsLayer { alpha = ramp(morph.progress.value, DetailsFadeStart) }
                .background(Brush.verticalGradient(listOf(
                    ambientColor.copy(alpha = 0.6f),
                    c.surface.copy(alpha = 0f),
                ))),
        )
        ActionMessage(state, Modifier.align(Alignment.BottomCenter).zIndex(1f).padding(bottom = bottom + 24.dp))
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(top + StageTopGap))
            // Pinned: the video stays put while the details scroll beneath it. Sit above the
            // details list so the bottom scrubber/thumb aren't covered at the seam.
            Box(Modifier.zIndex(2f)) {
                VideoStage(state, morph, onCollapse, onFullscreen, interactive)
            }
            // While collapsing the details are invisible anyway; leaving them out keeps them
            // from catching touches meant for the mini-player.
            if (!showDetails) Spacer(Modifier.weight(1f)) else
            LazyColumn(
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
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                            letterSpacing = (-0.4).sp,
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
                            modifier = if (hasDescription) Modifier.pressable(pressedScale = 0.98f) { descriptionOpen = !descriptionOpen } else Modifier,
                        ) {
                            Text(stats, color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            if (hasDescription) {
                                Text(
                                    if (descriptionOpen) "  less" else "  …more",
                                    color = c.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                        if (descriptionOpen && details != null) {
                            Text(
                                details.description,
                                modifier = Modifier.padding(top = 10.dp),
                                color = c.onSurfaceVariant,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val openChannel = LocalChannelOpener.current
                            val channelUrl = details?.item?.channelUrl ?: item.channelUrl
                            // Avatar + name open the channel (the Subscribe button beside them doesn't).
                            Row(
                                Modifier.weight(1f).pressable(enabled = channelUrl != null, pressedScale = 0.97f) { channelUrl?.let(openChannel) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                            ChannelAvatar(details?.item?.avatar ?: item.avatar, item.channel, size = 46)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.channel, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                details?.subscribers?.takeIf { it > 0 }?.let {
                                    Text("${formatCount(it)} subscribers", color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            }
                            state.accountState?.subscribed?.let { subscribed ->
                                SubscribeButton(subscribed, onClick = state::toggleSubscribe)
                            }
                        }
                        PlayerActions(state)
                        QueueCard(state, onOpen = { state.sheet = PlayerSheet.Queue })
                        CommentsCard(state.comments, onOpen = { state.sheet = PlayerSheet.Comments })
                    }
                }
                val related = details?.related.orEmpty()
                if (related.isNotEmpty()) {
                    item(key = "moreTitle") { SectionTitle("More videos") }
                    items(related, key = { "rel-" + it.id }) { video ->
                        Box(Modifier.padding(horizontal = 20.dp)) { VideoRow(video, onClick = { onOpen(video) }) }
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
        // Swiping home from the bottom edge never scrolls the details (open player only: while
        // collapsing, touches must still fall through to the mini-player).
        if (interactive) BottomGestureGuard(Modifier.align(Alignment.BottomCenter))
        // The mini-player's title and buttons travel with the shrinking video and fade in as
        // it lands, so the page turns into the mini-player rather than flying to one.
        MorphingMiniDetails(state, morph)

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
            )
        }
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
                    pulling = delta < 0f && state.expanded && !state.fullscreen && !AudioOnly.enabled && morph.progress.value >= 0.999f
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
    if (AudioOnly.enabled) {
        AudioOnlyStage(
            state = state,
            interactive = interactive,
            onInactiveTap = { state.expanded = true },
            progress = { morph.progress.value },
            controlsAlpha = controlsAlpha,
            modifier = stage,
        )
    } else {
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
}

/** How far a swipe up on the open video travels before it goes fullscreen on release. */
private val FullscreenPull = 96.dp

// ── Account actions (signed in) ────────────────────────────────────────────────

@Composable
private fun SubscribeButton(subscribed: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    // Expressive state change: the pill rounds into a softer tonal shape once subscribed,
    // squishes as it does, and the bell rings.
    val radius by animateDpAsState(if (subscribed) 16.dp else 22.dp, if (reduced) tween(0) else spring(dampingRatio = 0.6f, stiffness = 500f), label = "subShape")
    Row(
        modifier = Modifier
            .squishOn(subscribed)
            .height(44.dp)
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(radius))
            .background(if (subscribed) c.secondaryContainer else c.onSurface)
            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
            .padding(horizontal = if (subscribed) 14.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (subscribed) {
            FlourishIcon(Icons.Rounded.NotificationsActive, null, active = subscribed, kind = Flourish.Ring, tint = c.onSecondaryContainer, size = 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            if (subscribed) "Subscribed" else "Subscribe",
            color = if (subscribed) c.onSecondaryContainer else c.surface,
            fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
        )
    }
}

/**
 * The action row: like | dislike, save, share, loop, speed and audio only.
 * Compact pills matching the reverse-engineered modded player styling.
 */
@Composable
private fun PlayerActions(state: PlayerState) {
    val context = LocalContext.current
    val item = state.current
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
            ActionPill(
                icon = Icons.Rounded.Download,
                label = "Download",
                active = false,
                flourish = Flourish.Pop,
                shape = RoundedCornerShape(18.dp),
                description = "Download video",
            ) {
                state.actionMessage = "Download feature coming soon"
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
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
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
        ActionPill(
            icon = if (like == LikeStatus.Dislike) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
            label = state.details?.dislikes?.takeIf { it >= 0 }?.let { formatCount(it) },
            active = like == LikeStatus.Dislike,
            flourish = Flourish.Droop,
            shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp),
            description = "Dislike",
        ) {
            if (signedIn) state.setLike(LikeStatus.Dislike)
            else state.actionMessage = "Sign in to dislike"
        }
    }
    ActionPill(
        icon = if (state.inWatchLater) Icons.Rounded.WatchLater else Icons.Outlined.WatchLater,
        label = if (state.inWatchLater) "Saved" else "Save",
        active = state.inWatchLater,
        flourish = Flourish.Drop,
        shape = RoundedCornerShape(18.dp),
        description = "Save to Watch later",
    ) {
        if (signedIn) state.toggleWatchLater()
        else state.actionMessage = "Sign in to save videos"
    }
}

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    active: Boolean,
    flourish: Flourish,
    shape: androidx.compose.ui.graphics.Shape,
    description: String,
    labelRollsUp: Boolean = true,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val bg by androidx.compose.animation.animateColorAsState(if (active) c.primary else c.surfaceContainerHigh, tween(220), label = "pillBg")
    val tint by androidx.compose.animation.animateColorAsState(if (active) c.onPrimary else c.onSurface, tween(220), label = "pillInk")
    Row(
        modifier = Modifier
            .squishOn(active)
            .height(36.dp)
            .pressable(onClick = onClick)
            // background(shape), not clip: the like sparks may fly past the pill's edge.
            .background(bg, shape)
            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlourishIcon(icon, description, active = active, kind = flourish, tint = tint, size = 18.dp, sparkColor = c.primary)
        if (label != null) {
            Spacer(Modifier.width(5.dp))
            RollingText(label, tint, up = labelRollsUp, fontSize = 12.5.sp)
        }
    }
}

/** Brief confirmation or failure note for account actions; clears itself. */
@Composable
private fun ActionMessage(state: PlayerState, modifier: Modifier) {
    val message = state.actionMessage ?: return
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2_500)
        state.actionMessage = null
    }
    val c = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(CircleShape)
            .background(c.inverseSurface)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) { Text(message, color = c.inverseOnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

// ── Mini player ────────────────────────────────────────────────────────────────

@Composable
fun MiniPlayer(state: PlayerState, onExpand: () -> Unit, modifier: Modifier = Modifier, showVideo: Boolean = true) {
    val c = MaterialTheme.colorScheme
    val item = state.current ?: return
    val shape = RoundedCornerShape(MiniRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniHeight)
            .pressable(pressedScale = 0.99f, onClick = onExpand)
            .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.20f), spotColor = Color.Black.copy(alpha = 0.28f))
            .border(1.dp, c.outlineVariant.copy(alpha = 0.25f), shape)
            .clip(shape)
            .background(c.surfaceContainerHigh)
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = MiniThumbStart, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(width = MiniThumbWidth, height = MiniThumbHeight).clip(RoundedCornerShape(MiniThumbRadius))) {
                if (showVideo) {
                    Box(Modifier.fillMaxSize().background(Color.Black))
                    Thumbnail(item.thumbnail, Modifier.fillMaxSize(), radius = 0)
                    if (!AudioOnly.enabled) {
                        Box(Modifier.fillMaxSize().aspectFit(state.stageAspect, cover = true)) {
                            LocalVideoSurface.current(Modifier.fillMaxSize().aspectFit(state.videoAspect))
                        }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            MiniDetails(state, interactive = true)
        }
    }
}

/**
 * YouTube original popup miniplayer control layout:
 * - Left: Thumbnail with live video
 * - Middle: 2-line title and channel
 * - Right: Transparent touch-target Play/Pause and Dismiss (X) icons
 * - Bottom: Full-width YouTube red progress bar
 */
@Composable
private fun RowScope.MiniDetails(state: PlayerState, interactive: Boolean) {
    val c = MaterialTheme.colorScheme
    val item = state.current ?: return
    Box(Modifier.weight(1f).fillMaxHeight()) {
        Row(
            Modifier.fillMaxSize().padding(bottom = 3.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.channel,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Normal,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(4.dp))
            // YouTube style flat action icon for play/pause
            Box(
                Modifier
                    .size(40.dp)
                    .pressable(enabled = interactive, onClick = state::togglePlay),
                contentAlignment = Alignment.Center
            ) {
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        color = c.onSurface,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = c.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            // YouTube style flat close icon
            Box(
                Modifier
                    .size(40.dp)
                    .pressable(enabled = interactive, onClick = state::close),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Close,
                    "Close",
                    tint = c.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Full-width YouTube original progress bar anchored along bottom
        val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(2.5.dp)
                .background(c.onSurface.copy(alpha = 0.12f))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(Color(0xFFFF0000)) // YouTube red
            )
        }
    }
}

/**
 * The mini-player's details, drawn inside the morphing page: always just right of the
 * shrinking video and centred on it, fading in over the last 30% of the collapse (out over
 * the first 30% of an open). At progress 0 they sit exactly where the real mini-player's
 * are, so the handoff is invisible.
 */
@Composable
private fun MorphingMiniDetails(state: PlayerState, morph: PlayerMorph) {
    val g = morph.geometry ?: return
    Row(
        Modifier
            // Laid out per frame (this small row only, no recomposition): it spans from the
            // video's right edge to the page's right edge, so the buttons hug the page while
            // it narrows, and it sits vertically centred on the video.
            .layout { measurable, _ ->
                val p = morph.progress.value
                val gap = 12.dp.toPx()
                val f = g.videoFrame(g.stage.size, p)
                val start = g.stage.left + f.offset.x + f.scale * f.window.right + gap
                val videoCenterY = g.stage.top + f.offset.y + f.scale * f.window.center.y
                val frameRight = lerpF(g.miniCard.right, g.screen.width, p)
                val width = (frameRight - 8.dp.toPx() - start).coerceAtLeast(0f).toInt()
                val height = MiniHeight.roundToPx()
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(width, height))
                layout(width, height) {
                    placeable.placeRelative(start.toInt(), (videoCenterY - height / 2f).toInt())
                }
            }
            .graphicsLayer { alpha = 1f - ramp(morph.progress.value, 0f, 0.3f) },
        verticalAlignment = Alignment.CenterVertically,
    ) { MiniDetails(state, interactive = false) }
}
