package com.thinker.flashtube

import android.Manifest
import android.app.Activity
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The media notification (and its lock-screen controls) needs this on Android 13+.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { FlashTubeTheme { FlashTubeApp() } }
    }
}

enum class Tab(val label: String) {
    Home("Home"),
    Subscriptions("Subscriptions"),
    Library("Library"),
    Downloads("Downloads"),
}

// Flat four-destination bar with a compact floating video popup above it.
private val NavHeight = 60.dp
private val MiniWidth = 200.dp
private val MiniGap = 8.dp

@Composable
fun FlashTubeApp() {
    val c = MaterialTheme.colorScheme
    val player = rememberPlayerState()
    val scope = rememberCoroutineScope()
    val search = remember { SearchState(scope) }
    val lib = remember { LibraryData(scope) }
    var signingIn by remember { mutableStateOf(false) }
    var openSettings by remember { mutableStateOf(false) }
    var openPlaylist by remember { mutableStateOf<PlaylistRef?>(null) }
    var openChannel by remember { mutableStateOf<String?>(null) }
    var openAllSubscriptions by remember { mutableStateOf(false) }
    var openSearch by remember { mutableStateOf(false) }
    LaunchedEffect(openSearch) { if (openSearch) search.reset() }
    // Kept after closing so the page can slide out with its content.
    var channelState by remember { mutableStateOf<ChannelState?>(null) }
    val playlistFeed = remember(openPlaylist?.id) { openPlaylist?.let { lib.playlistFeed(it.id) } }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    // Bumped when the already-selected tab is tapped; that tab's list scrolls to the top.
    val scrollToTop = remember { mutableStateMapOf<Tab, Int>() }
    val reduced = rememberReducedMotion()

    var miniOffsetX by remember { mutableFloatStateOf(0f) }
    var miniOffsetY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player.current?.id) {
        miniOffsetX = 0f
        miniOffsetY = 0f
    }
    val morph = remember { PlayerMorph(scope) { expanded -> player.expanded = expanded } }

    // The screen stays on while a video plays (full player, mini-player or fullscreen);
    // paused, the normal timeout applies again. Only affects the visible window.
    val rootView = LocalView.current
    DisposableEffect(player.isPlaying) {
        rootView.keepScreenOn = player.isPlaying
        onDispose { rootView.keepScreenOn = false }
    }

    // Opening the player drops any text focus (the search box behind it), so the keyboard
    // can't come back over the video.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(player.expanded) {
        if (player.expanded) {
            openSearch = false
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    // Off screen (app in background, screen locked): audio only.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> player.setBackground(true)
                androidx.lifecycle.Lifecycle.Event.ON_START -> player.setBackground(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // As in Castii: at least 8 dp off the bottom, and extra clearance above a three-button bar.
    val stackBottom = navBottom.coerceAtLeast(8.dp) +
        if (WindowInsets.tappableElement.asPaddingValues().calculateBottomPadding() > 0.dp) 10.dp else 0.dp
    var optionsFor by remember { mutableStateOf<VideoItem?>(null) }
    val hasMini = player.current != null
    // Lists scroll fully clear of the floating nav (and mini-player when present).
    val contentPadding = PaddingValues(
        bottom = stackBottom + NavHeight + (if (hasMini) MiniHeight + MiniGap else 0.dp) + 16.dp,
    )

    // Fullscreen is whenever the open player is shown in landscape (rotation or button).
    val activity = LocalContext.current as ComponentActivity
    val orientation = rememberOrientationController(activity)
    val playerOpen = player.current != null && player.expanded
    // Audio only has no picture to go fullscreen with: the open player stays portrait.
    orientation.Bind(playerOpen && !AudioOnly.enabled)
    val pip = remember(activity) { PipController(activity) }
    // Closing the picture-in-picture window pauses (expanding it just comes back).
    pip.Bind(onClosed = { player.controller?.pause() })
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // The PiP window can be any shape: hold whatever was showing when it opened.
    val fullscreenBeforePip = remember { mutableStateOf(false) }
    val showFullscreen = if (pip.active) fullscreenBeforePip.value else playerOpen && landscape
    SideEffect { if (!pip.active) fullscreenBeforePip.value = showFullscreen }
    LaunchedEffect(showFullscreen) { player.fullscreen = showFullscreen }
    SystemBarsHidden(showFullscreen)
    fun toggleFullscreen() = if (showFullscreen) orientation.exitFullscreen() else orientation.enterFullscreen()

    BackHandler(enabled = player.expanded) { player.expanded = false }
    BackHandler(enabled = !player.expanded && openSettings) { openSettings = false }
    BackHandler(enabled = !player.expanded && openAllSubscriptions && openChannel == null) { openAllSubscriptions = false }
    BackHandler(enabled = !player.expanded && tab != Tab.Home && openPlaylist == null && openChannel == null && !openAllSubscriptions) {
        tab = Tab.Home
    }
    BackHandler(enabled = !player.expanded && openSearch) { openSearch = false }
    BackHandler(enabled = !player.expanded && openPlaylist != null) { openPlaylist = null }
    BackHandler(enabled = signingIn) { signingIn = false }
    BackHandler(enabled = !player.expanded && openChannel != null) { openChannel = null }
    // Registered last so it wins: in fullscreen, Back returns to the portrait player first.
    BackHandler(enabled = showFullscreen) { orientation.exitFullscreen() }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current

    // Taps, back and opening a video set `expanded`; this turns that into the morph.
    // Gesture releases settle themselves and are left alone (see PlayerMorph.animateTo).
    LaunchedEffect(player.expanded, player.current?.id) {
        if (player.current == null) morph.progress.snapTo(0f) else morph.animateTo(player.expanded, reduced)
    }
    val showPlayer by remember {
        // Held until the morph has completely finished, so the video changes hands once.
        derivedStateOf { player.current != null && (player.expanded || morph.dragging || morph.progress.isRunning || morph.progress.value > 0f) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.surface)) {
        // Both ends of the morph, from the same constants the mini-player and player use.
        val stageAspect = player.stageAspect
        val geometry = remember(constraints.maxWidth, constraints.maxHeight, stackBottom, statusTop, density, stageAspect, miniOffsetX, miniOffsetY) {
            with(density) {
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                val miniBottom = h - (stackBottom + NavHeight + MiniGap).toPx()
                val miniW = minOf(w - 32.dp.toPx(), MiniWidth.toPx())
                val miniH = miniW * 9f / 16f
                val miniRight = w - 16.dp.toPx()
                val baseCard = Rect(miniRight - miniW, miniBottom - miniH, miniRight, miniBottom)
                val miniCard = baseCard.translate(miniOffsetX, miniOffsetY)
                val side = StageSideGap.toPx()
                val stageTop = (statusTop + StageTopGap).toPx()
                val stage = Rect(side, stageTop, w - side, stageTop + (w - 2 * side) / stageAspect)
                MorphGeometry(
                    screen = Size(w, h),
                    miniCard = miniCard,
                    miniThumb = miniCard,
                    stage = stage,
                    miniRadius = MiniRadius.toPx(),
                    thumbRadius = MiniRadius.toPx(),
                    stageRadius = StageRadius.toPx(),
                )
            }
        }
        SideEffect { morph.geometry = geometry }

        // One video surface that moves between player, fullscreen and mini-player (never two
        // at once), so the picture is never rebuilt and never blinks back to the thumbnail.
        val sharedSurface = remember(player) {
            androidx.compose.runtime.movableContentOf { m: Modifier -> VideoSurface(player.controller, m) }
        }
        CompositionLocalProvider(
            // In picture-in-picture only the PiP view below shows the video.
            LocalVideoSurface provides if (pip.active) NoVideoSurface else sharedSurface,
            LocalPictureInPicture provides pip,
            LocalVideoMenu provides { optionsFor = it },
            LocalChannelOpener provides { url ->
                // From the player: minimize it so the channel page is visible.
                player.expanded = false
                if (channelState?.url != url) channelState = ChannelState(url, scope)
                openChannel = url
            },
        ) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                if (reduced) fadeIn(snap()) togetherWith fadeOut(snap())
                else (fadeIn(tween(Motion.PageEnterMs, easing = Motion.Emphasized)) +
                    slideInVertically(tween(Motion.PageRiseMs, easing = Motion.Emphasized)) { (it / 35).coerceIn(8, 24) }) togetherWith
                    fadeOut(tween(Motion.PageExitMs))
            },
            label = "tabs",
        ) { t ->
            when (t) {
                Tab.Home -> HomeScreen(
                    lib, contentPadding, onOpen = player::open, onSearch = { openSearch = true },
                    onSignIn = { signingIn = true }, onAccount = { openSettings = true },
                    scrollToTop = scrollToTop[Tab.Home] ?: 0,
                )
                Tab.Subscriptions -> SubscriptionsScreen(
                    lib, contentPadding, onOpen = player::open, onSearch = { openSearch = true },
                    onSignIn = { signingIn = true }, onAccount = { openSettings = true },
                    scrollToTop = scrollToTop[Tab.Subscriptions] ?: 0,
                    onOpenAllSubscriptions = { openAllSubscriptions = true },
                )
                Tab.Library -> LibraryScreen(
                    lib, contentPadding, onOpen = player::open, onOpenPlaylist = { openPlaylist = it },
                    onSearch = { openSearch = true },
                    onSignIn = { signingIn = true }, onAccount = { openSettings = true },
                    scrollToTop = scrollToTop[Tab.Library] ?: 0,
                )
                Tab.Downloads -> DownloadsScreen(
                    contentPadding = contentPadding,
                    onOpen = player::open,
                    onSearch = { openSearch = true },
                    onAccount = { openSettings = true },
                    scrollToTop = scrollToTop[Tab.Downloads] ?: 0,
                )
            }
        }

        AnimatedVisibility(
            visible = openSearch,
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            SearchScreen(
                search,
                contentPadding,
                onBack = { openSearch = false },
                onOpen = player::open,
            )
        }

        // A playlist opens as a page over the tabs (slides in from the right).
        AnimatedVisibility(
            visible = openPlaylist != null,
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            val pl = openPlaylist
            val feed = playlistFeed
            if (pl != null && feed != null) {
                PlaylistScreen(
                    pl, feed, contentPadding,
                    onBack = { openPlaylist = null },
                    onOpen = player::open,
                    channelAvatarSeed = lib.channels.associate { it.id to it.avatar },
                )
            }
        }

        // All subscriptions sits under an open channel so Back from the channel returns here.
        AnimatedVisibility(
            visible = openAllSubscriptions,
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            AllSubscriptionsScreen(
                channels = lib.channels,
                contentPadding = contentPadding,
                onBack = { openAllSubscriptions = false },
            )
        }

        // A channel opens the same way, over everything below the mini-player.
        AnimatedVisibility(
            visible = openChannel != null,
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            channelState?.let { ch ->
                ChannelScreen(ch, contentPadding, onBack = { openChannel = null }, onOpen = player::open, onMessage = { player.actionMessage = it })
            }
        }

        // Dedicated Settings page (slides in from the right over tabs)
        AnimatedVisibility(
            visible = openSettings,
            enter = if (reduced) fadeIn(snap()) else slideInHorizontally(tween(340, easing = Motion.Emphasized)) { it } + fadeIn(tween(120)),
            exit = if (reduced) fadeOut(snap()) else slideOutHorizontally(tween(300, easing = Motion.Emphasized)) { it } + fadeOut(tween(220)),
        ) {
            SettingsScreen(
                onBack = { openSettings = false },
                onSignIn = { signingIn = true },
                onSignedOut = { lib.reset(); player.refreshAccountState() },
                contentPadding = contentPadding,
            )
        }

        // Ledgii's bottom vignette: the page color fades in over the last 130 dp (plus the
        // system bar), so content dissolves behind the mini-player and tab bar instead of
        // running hard into them. Drawn only — it never intercepts touches.
        val pageColor = c.surface
        val vignette = remember(pageColor) { vignetteBrush(pageColor, opaqueAtTop = false) }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(navBottom + 130.dp)
                .drawBehind { drawRect(vignette) },
        )
        // Swiping home from here never scrolls the page (the tab bar above still wins its taps).
        BottomGestureGuard(Modifier.align(Alignment.BottomCenter))

        if (hasMini) {
            val dismiss = remember { androidx.compose.animation.core.Animatable(0f) }
            val dismissDistance = with(density) { 56.dp.toPx() }
            MiniPlayer(
                player,
                onExpand = { player.expanded = true },
                showVideo = !showPlayer && !showFullscreen,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset { IntOffset(miniOffsetX.roundToInt(), miniOffsetY.roundToInt()) }
                    .padding(start = 16.dp, end = 16.dp, bottom = stackBottom + NavHeight + MiniGap)
                    .widthIn(max = MiniWidth)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            miniOffsetX += dragAmount.x
                            miniOffsetY += dragAmount.y
                        }
                    }
                    // Fades as the video grows out of it; slides and fades as it's dismissed.
                    .graphicsLayer {
                        translationY = dismiss.value
                        // Hidden for the whole morph: the shrinking page draws the mini-player
                        // itself (it stays touchable underneath, so taps still work mid-collapse).
                        alpha = (if (showPlayer) 0f else 1f) * (1f - (dismiss.value / (dismissDistance * 1.6f)).coerceIn(0f, 1f))
                    },
            )
        }

        MinimalMonochromeNavBar(
            selected = tab,
            onSelect = { selected ->
                when {
                    // Re-tapping the current tab: back out of a playlist, else scroll to top.
                    selected == tab && openSearch -> openSearch = false
                    selected == tab && openChannel != null -> openChannel = null
                    selected == tab && openPlaylist != null -> openPlaylist = null
                    selected == tab && openAllSubscriptions -> openAllSubscriptions = false
                    selected == tab -> scrollToTop[selected] = (scrollToTop[selected] ?: 0) + 1
                    else -> {
                        openPlaylist = null
                        openChannel = null
                        openAllSubscriptions = false
                        openSearch = false
                        tab = selected
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // The player morphs out of (and back into) the mini-player; see PlayerMorph.
        if (showFullscreen) {
            FullscreenPlayer(player, onExit = { orientation.exitFullscreen() })
        } else if (showPlayer) {
            PlayerScreen(
                player, morph,
                onOpen = player::open,
                onCollapse = { player.expanded = false },
                onFullscreen = { toggleFullscreen() },
            )
        }

        if (signingIn) {
            SignInScreen(
                onDone = { signingIn = false; lib.reset(); player.refreshAccountState() },
                onCancel = { signingIn = false },
            )
        }

        when (player.sheet) {
            PlayerSheet.Quality -> QualitySheet(player, onDismiss = { player.sheet = null })
            PlayerSheet.Subtitles -> SubtitlesSheet(player, onDismiss = { player.sheet = null })
            PlayerSheet.Settings -> PlayerSettingsSheet(player, onDismiss = { player.sheet = null })
            PlayerSheet.Comments -> Unit // a panel inside the player (PlayerScreen)
            PlayerSheet.Queue -> QueueSheet(player, onDismiss = { player.sheet = null })
            null -> Unit
        }
        optionsFor?.let { video -> VideoOptionsSheet(video, player, onDismiss = { optionsFor = null }) }
        // Confirmations ("Added to queue") when the full player isn't showing its own.
        if (!showPlayer) {
            QueueMessage(player, Modifier.align(Alignment.BottomCenter).padding(bottom = stackBottom + NavHeight + (if (hasMini) MiniHeight + MiniGap else 0.dp) + 12.dp))
        }
        // Picture-in-picture: the window is just the video, over everything (kept composed
        // underneath, so expanding returns to exactly where things were).
        if (pip.active) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                sharedSurface(Modifier.fillMaxSize().aspectFit(player.videoAspect))
            }
        }
        }
    }
}

@Composable
private fun QueueMessage(player: PlayerState, modifier: Modifier) {
    val message = player.actionMessage ?: return
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2_000)
        player.actionMessage = null
    }
    val c = MaterialTheme.colorScheme
    Box(modifier.clip(RoundedCornerShape(50)).background(c.inverseSurface).padding(horizontal = 18.dp, vertical = 11.dp)) {
        Text(message, color = c.inverseOnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** Flat four-tab navigation styled after docs/design/navbar-concept.svg. */
@Composable
private fun MinimalMonochromeNavBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = c.surface,
        tonalElevation = 0.dp,
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(NavHeight)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tab.entries.forEach { t ->
                    val active = t == selected
                    val activeContentColor by animateColorAsState(
                        targetValue = if (active) c.onSurface else c.onSurfaceVariant,
                        animationSpec = if (reduced) snap() else tween(180, easing = Motion.Emphasized),
                        label = "activeContentColor",
                    )
                    val interaction = remember(t) { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(interactionSource = interaction, indication = null) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                onSelect(t)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            NavDestinationIcon(t, active, activeContentColor, Modifier.size(24.dp))
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = t.label,
                                color = activeContentColor,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(c.outlineVariant.copy(alpha = 0.12f)),
            )
        }
    }
}

@Composable
private fun NavDestinationIcon(tab: Tab, active: Boolean, color: Color, modifier: Modifier = Modifier) {
    val icon = when (tab) {
        Tab.Home -> if (active) R.drawable.ic_nav_home_filled else R.drawable.ic_nav_home_outline
        Tab.Subscriptions -> R.drawable.ic_nav_subscriptions
        Tab.Library -> R.drawable.ic_nav_library
        Tab.Downloads -> R.drawable.ic_nav_downloads
    }
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier.size(if (tab == Tab.Home) 21.dp else 23.dp),
    )
}
