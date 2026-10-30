package com.thinker.flashtube

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/** Comments for the open video: first page, paging, per-comment replies, likes and posting. */
@Stable
class CommentsState(private val scope: CoroutineScope) {
    private var videoId: String? = null
    var comments by mutableStateOf<List<Comment>>(emptyList())
        private set
    var total by mutableStateOf(-1)
        private set
    var disabled by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var isLive by mutableStateOf(false)
        private set
    var liveViewerCount by mutableStateOf<String?>(null)
        private set
    var liveMaxMessageLength by mutableStateOf(200)
        private set
    var liveRestriction by mutableStateOf<String?>(null)
        private set
    private var liveSendParams: String? = null
    var error by mutableStateOf<String?>(null)
        private set
    private var next: String? = null
    val hasMore get() = next != null

    /** Replies already fetched, by comment id. */
    val replies = mutableStateMapOf<String, List<Comment>>()
    /** Comments the account has liked: from YouTube, plus taps this session. */
    val liked = mutableStateMapOf<String, Boolean>()

    /** Optimistic like toggle; reverts with a note if YouTube refuses. */
    fun toggleLike(comment: Comment, onError: (String) -> Unit) {
        val id = videoId ?: return
        val now = liked[comment.id] != true
        liked[comment.id] = now
        scope.launch {
            runCatching { CommentPosting.setLiked(id, comment.id, now) }
                .onFailure { liked[comment.id] = !now; onError(it.message ?: "Couldn't like that comment.") }
        }
    }
    val repliesLoading = mutableStateMapOf<String, Boolean>()

    private var job: Job? = null
    private var liveJob: Job? = null

    /** A new video opened: drop the old comments now; [load] follows once its data is in. */
    fun clearFor(id: String) {
        job?.cancel()
        liveJob?.cancel()
        videoId = id
        isLive = false; liveViewerCount = null; liveSendParams = null; liveRestriction = null
        comments = emptyList(); total = -1; disabled = false; error = null; next = null
        replies.clear(); repliesLoading.clear(); replyingTo = null; draft = ""; postError = null
        loading = true
    }

    fun load(id: String, live: Boolean = false) {
        videoId = id
        isLive = live
        liveViewerCount = null; liveSendParams = null; liveRestriction = null
        replyingTo = null
        draft = ""
        postError = null
        comments = emptyList()
        total = -1
        disabled = false
        error = null
        next = null
        replies.clear()
        repliesLoading.clear()
        liked.clear()
        loading = true
        job?.cancel()
        liveJob?.cancel()
        job = scope.launch {
            runCatching { if (live) CommentFeed.firstLive(id) else CommentFeed.first(id) }
                .onSuccess { page ->
                    if (videoId != id) return@onSuccess
                    comments = page.comments
                    total = page.total
                    disabled = page.disabled
                    next = page.next
                    liveSendParams = page.liveSendParams
                    liveMaxMessageLength = page.liveMaxMessageLength
                    liveRestriction = page.liveRestriction
                    arrived(page.comments)
                    if (live) startLivePolling(id, page.next, page.liveTimeoutMs)
                }
                .onFailure { if (videoId == id) error = YouTube.friendlyError(it) }
            loading = false
        }
    }

    private fun startLivePolling(id: String, initialToken: String?, initialDelay: Long) {
        liveJob?.cancel()
        liveJob = scope.launch {
            runCatching { CommentFeed.liveViewerCount(id) }
                .onSuccess { if (videoId == id) liveViewerCount = it }
            var token = initialToken
            var waitMs = initialDelay.coerceIn(1_000L, 30_000L)
            var polls = 0
            while (videoId == id && isLive && token != null) {
                delay(waitMs)
                if (videoId != id || !isLive) break
                val result = runCatching { CommentFeed.moreLive(token!!) }
                result.onSuccess { page ->
                    if (videoId != id) return@onSuccess
                    val removed = page.removedIds
                    val seen = comments.mapTo(HashSet()) { it.id }
                    comments = comments.filterNot { it.id in removed } + page.comments.filter { seen.add(it.id) }
                    liveSendParams = page.liveSendParams ?: liveSendParams
                    liveRestriction = page.liveRestriction
                    liveMaxMessageLength = page.liveMaxMessageLength
                    token = page.next
                    waitMs = page.liveTimeoutMs.coerceIn(1_000L, 30_000L)
                    arrived(page.comments)
                    if (page.viewerCount != null) liveViewerCount = page.viewerCount
                    polls++
                    if (polls % 3 == 0) runCatching { CommentFeed.liveViewerCount(id) }
                        .onSuccess { count -> if (videoId == id) liveViewerCount = count }
                }.onFailure {
                    waitMs = (waitMs * 2).coerceAtMost(30_000L)
                }
            }
        }
    }

    fun loadMore() {
        if (isLive) return
        val id = videoId ?: return
        val page = next ?: return
        if (loading) return
        loading = true
        job = scope.launch {
            runCatching { CommentFeed.more(page) }
                .onSuccess { more ->
                    if (videoId != id) return@onSuccess
                    // Pages can overlap at the boundary; keep ids unique for the list keys.
                    val seen = comments.mapTo(HashSet()) { it.id }
                    comments = comments + more.comments.filter { seen.add(it.id) }
                    next = more.next
                    arrived(more.comments)
                }
                .onFailure { if (videoId == id) error = YouTube.friendlyError(it) }
            loading = false
        }
    }

    fun loadReplies(comment: Comment) {
        val id = videoId ?: return
        val page = comment.repliesToken ?: return
        if (repliesLoading[comment.id] == true || replies.containsKey(comment.id)) return
        repliesLoading[comment.id] = true
        scope.launch {
            runCatching { CommentFeed.more(page) }
                .onSuccess { if (videoId == id) { replies[comment.id] = it.comments; arrived(it.comments) } }
            repliesLoading.remove(comment.id)
        }
    }

    /** New comments in: their liked state, and their authors' real names (looked up once each). */
    private fun arrived(page: List<Comment>) {
        page.forEach { if (it.liked) liked[it.id] = true }
        ChannelNames.resolve(scope, page.mapNotNullTo(LinkedHashSet()) { it.channelId })
    }

    fun hideReplies(comment: Comment) {
        replies.remove(comment.id)
    }

    // ── Posting (signed in) ──
    /** The comment being replied to, or null for a new top-level comment. */
    var replyingTo by mutableStateOf<Comment?>(null)
    var draft by mutableStateOf("")
    var posting by mutableStateOf(false)
        private set
    var postError by mutableStateOf<String?>(null)

    fun startReply(comment: Comment) {
        replyingTo = comment
        // Replies to a reply go to the same thread, addressed with an @mention (as on YouTube).
        if (comment.id.contains('.') && draft.isBlank()) draft = "${comment.author.trim()} "
    }

    fun post() {
        val id = videoId ?: return
        val text = draft.trim()
        if (text.isEmpty() || posting) return
        val target = replyingTo
        val threadId = target?.id?.substringBefore('.')
        posting = true
        postError = null
        scope.launch {
            runCatching {
                if (isLive) CommentPosting.liveMessage(liveSendParams ?: throw IOException("Live chat is not ready yet."), text)
                else {
                    if (threadId == null) CommentPosting.comment(id, text) else CommentPosting.reply(id, threadId, text)
                    null
                }
            }.onSuccess { echoed ->
                val profile = Account.profile
                val mine = Comment(
                    id = echoed?.id ?: "local-${System.nanoTime()}",
                    author = profile?.handle ?: profile?.name ?: "You",
                    channelId = null,
                    name = profile?.name,
                    avatar = profile?.avatar,
                    text = echoed?.text ?: text, likes = 0, ago = if (isLive) "live" else "just now",
                    pinned = false, hearted = false, replyCount = 0, repliesToken = null,
                )
                if (isLive) {
                    val seen = comments.any { it.id == mine.id }
                    if (!seen) comments = (comments + mine).takeLast(250)
                    draft = ""
                } else if (threadId == null) {
                    comments = listOf(mine) + comments
                    if (total >= 0) total += 1
                } else {
                    replies[threadId] = replies[threadId].orEmpty() + mine
                }
                draft = ""
                replyingTo = null
            }.onFailure { postError = it.message ?: "Couldn't post your comment." }
            posting = false
        }
    }
}

/** The card under the video: comment count and a compact top-comment preview. */
@Composable
fun CommentsCard(state: CommentsState, onOpen: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val ambient = LocalAmbientColor.current
    val isDark = c.surface.luminance() < 0.4f
    val hasAmbient = AmbientMode.enabled && isDark && ambient != Color.Unspecified && ambient != c.surface

    val shape = RoundedCornerShape(12.dp)
    val cardBg = if (hasAmbient) lerpColor(c.surfaceContainerLow, ambient.copy(alpha = 0.30f), 0.20f) else c.surfaceContainerLow
    val shadowColor = if (hasAmbient) ambient else Color.Black

    val first = state.comments.firstOrNull()
    Column(
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .shadow(
                elevation = if (hasAmbient) 3.dp else 0.dp,
                shape = shape,
                ambientColor = shadowColor.copy(alpha = 0.45f),
                spotColor = shadowColor.copy(alpha = 0.30f),
            )
            .pressable(enabled = state.isLive || state.comments.isNotEmpty() || (Account.signedIn && !state.disabled && !state.loading), pressedScale = 0.98f, onClick = onOpen)
            .clip(shape)
            .background(cardBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_player_comments),
                contentDescription = null,
                tint = c.onSurface,
                modifier = Modifier.size(21.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(if (state.isLive) "Live chat" else "Comments", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (state.isLive && state.liveViewerCount != null) {
                Spacer(Modifier.width(6.dp))
                Text("${state.liveViewerCount} watching", color = c.onSurfaceVariant, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            } else if (state.total > 0) {
                Spacer(Modifier.width(6.dp))
                Text(formatCount(state.total.toLong()), color = c.onSurfaceVariant, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        if (state.isLive) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (hasAmbient) ambient.copy(alpha = 0.16f) else c.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Text("Leave a comment", color = c.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
        } else when {
            state.disabled -> Text("Comments are turned off for this video.", color = c.onSurfaceVariant, fontSize = 12.sp)
            first != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelAvatar(first.avatar, first.author, size = 22)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = first.text,
                    color = c.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            state.loading -> CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            state.error != null -> Text(state.error.orEmpty(), color = c.onSurfaceVariant, fontSize = 12.sp)
            else -> Text(if (Account.signedIn) "No comments yet. Add the first one." else "No comments yet.", color = c.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

fun formatHandle(name: String): String {
    val trimmed = name.trim()
    if (trimmed.startsWith("@")) return trimmed
    return "@$trimmed"
}

/**
 * Comments as a panel over the player's details, opening with its top just under the video
 * (the video keeps playing above it). The comment box is pinned to the panel's bottom at
 * every size. Drag the top handle up to cover the screen, down to shrink or close; a fling
 * decides on its own. The list scrolls first: only once it's at the top does a further pull
 * down move the panel the same way (Derek, 2026-10-10).
 */
@Composable
fun CommentsPanel(
    state: CommentsState,
    partialTop: Dp,
    onClose: () -> Unit,
    creatorAvatar: String? = null,
    creatorName: String = "",
    onSeekToTimestamp: ((Long) -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val fullPx = with(density) { (maxHeight - statusTop - 8.dp).toPx() }
        val partialPx = with(density) { (maxHeight - partialTop).toPx() }.coerceIn(fullPx * 0.35f, fullPx)
        val height = remember { Animatable(partialPx) }
        // Slides up into place on open; slides back down to close.
        val slide = remember { Animatable(1f) }
        LaunchedEffect(Unit) { if (reduced) slide.snapTo(0f) else slide.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 550f)) }
        fun close() = scope.launch {
            if (!reduced) slide.animateTo(1f, tween(240, easing = Motion.Emphasized))
            onClose()
        }
        BackHandler { close() }
        fun dragBy(dy: Float): Float {
            val before = height.value
            val after = (before - dy).coerceIn(partialPx * 0.25f, fullPx)
            scope.launch { height.snapTo(after) }
            return before - after
        }
        fun settle(velocity: Float) {
            val h = height.value
            when {
                // Dragged down past 30%, flicked down once below the resting size, or flicked
                // hard from it: close.
                h < partialPx * 0.7f || velocity > 1200f && h < partialPx - 1f || velocity > 1800f && h <= partialPx + 1f -> close()
                velocity < -1200f -> scope.launch { height.animateTo(fullPx, spring(dampingRatio = 0.86f, stiffness = 550f)) }
                velocity > 1200f -> scope.launch { height.animateTo(partialPx, spring(dampingRatio = 0.86f, stiffness = 550f)) }
                else -> {
                    val target = if (h > (partialPx + fullPx) / 2f) fullPx else partialPx
                    scope.launch { height.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 550f)) }
                }
            }
        }
        // The list's leftover downward drag (it's already at the top) pulls the panel down;
        // reversing gives the height back, up to where the pull began, before the list scrolls.
        // Flings never move the panel, so a fast scroll up stops at the top.
        val listPull = remember(partialPx, fullPx) {
            object : NestedScrollConnection {
                var pulling = false
                var pullFrom = 0f

                fun pullBy(dy: Float): Float {
                    val before = height.value
                    val after = (before - dy).coerceIn(partialPx * 0.25f, pullFrom)
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { height.snapTo(after) }
                    return before - after
                }

                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (pulling && source == NestedScrollSource.UserInput && available.y < 0f) return Offset(0f, pullBy(available.y))
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                    if (!pulling) { pulling = true; pullFrom = height.value }
                    return Offset(0f, pullBy(available.y))
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (!pulling) return Velocity.Zero
                    pulling = false
                    settle(available.y)
                    return available
                }
            }
        }
        val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        var selectedSort by remember { mutableStateOf("Top") }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(with(density) { height.value.toDp() })
                .graphicsLayer { translationY = slide.value * size.height }
                .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
                .clip(shape)
                .background(c.surface)
                // Swallow taps so nothing underneath reacts through the panel.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                .imePadding(),
        ) {
            // Handle + title: the drag area.
            Column(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = rememberDraggableState { dragBy(it) },
                        orientation = Orientation.Vertical,
                        onDragStopped = { v -> settle(v) },
                    ),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), Alignment.Center) {
                    Box(Modifier.size(width = 38.dp, height = 4.dp).clip(CircleShape).background(c.onSurfaceVariant.copy(alpha = 0.4f)))
                }
                Row(
                    Modifier.padding(start = 16.dp, end = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_player_comments),
                        contentDescription = null,
                        tint = c.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.isLive) "Live chat" else "Comments", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (state.isLive && state.liveViewerCount != null) {
                        Spacer(Modifier.width(8.dp))
                        Text("${state.liveViewerCount} watching", color = c.onSurfaceVariant, fontWeight = FontWeight.Normal, fontSize = 14.sp)
                    } else if (state.total > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(formatCount(state.total.toLong()), color = c.onSurfaceVariant, fontWeight = FontWeight.Normal, fontSize = 14.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .pressable { /* Info popup */ },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Info, "Info", tint = c.onSurface, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .pressable { close() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, "Close comments", tint = c.onSurface, modifier = Modifier.size(22.dp))
                    }
                }

                // Sort Chips Bar (Top | Timed | Newest)
                if (!state.isLive) Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val sortOptions = listOf("Top", "Timed", "Newest")
                    sortOptions.forEach { option ->
                        val isSelected = selectedSort == option
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) c.onSurface else c.surfaceContainerHigh)
                                .pressable { selectedSort = option }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = option,
                                color = if (isSelected) c.surface else c.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.onSurface.copy(alpha = 0.08f)))
            }
            val list = rememberLazyListState()
            val nearEnd by remember {
                derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 4 }
            }
            LaunchedEffect(state.isLive, state.comments.size) {
                if (state.isLive && state.comments.isNotEmpty()) list.scrollToItem(state.comments.lastIndex)
            }
            LaunchedEffect(nearEnd, state.isLive) { if (nearEnd && !state.isLive) state.loadMore() }

            val displayedComments = remember(state.comments, selectedSort) {
                when {
                    state.isLive -> state.comments
                    selectedSort == "Timed" -> state.comments.filter { TIMESTAMP_REGEX.containsMatchIn(it.text) }.ifEmpty { state.comments }
                    else -> state.comments
                }
            }

            LazyColumn(state = list, modifier = Modifier.weight(1f).nestedScroll(listPull)) {
                if (!state.isLive && state.comments.isNotEmpty() && onSeekToTimestamp != null && selectedSort != "Timed") {
                    item(key = "chapters_carousel") {
                        TimelineChaptersCarousel(state.comments, onSeekToTimestamp)
                    }
                }
                items(displayedComments, key = { it.id }) {
                    CommentRow(it, state, creatorAvatar, creatorName, onSeekToTimestamp = onSeekToTimestamp)
                }
                if (state.loading || (!state.isLive && state.hasMore)) item {
                    Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) {
                        CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                    }
                }
                if (!state.loading && displayedComments.isEmpty()) item {
                    Text(
                        if (state.isLive && state.disabled) "Live chat is unavailable for this stream."
                        else if (state.disabled) "Comments are turned off for this video." else if (state.isLive) "Waiting for messages…" else "No comments yet.",
                        color = c.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(20.dp),
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
            if (Account.signedIn && (!state.isLive || state.liveRestriction == null)) Composer(state) else Text(
                state.liveRestriction ?: if (state.isLive) "Sign in to chat." else "Sign in to comment.",
                color = c.onSurfaceVariant, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().background(c.surfaceContainer).padding(horizontal = 20.dp, vertical = 14.dp),
            )
            // Clear of the gesture bar when the keyboard isn't up.
            Spacer(Modifier.fillMaxWidth().background(if (Account.signedIn) c.surfaceContainer else c.surfaceContainer).windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/** Text field pinned to the bottom of the sheet; Send (or Enter) posts and dismisses the keyboard. */
@Composable
private fun Composer(state: CommentsState) {
    val c = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val view = LocalView.current
    val requester = remember { FocusRequester() }
    fun send() {
        focus.clearFocus(force = true)
        keyboard?.hide()
        view.postDelayed({
            view.context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(view.windowToken, 0)
        }, 120)
        state.post()
    }
    LaunchedEffect(state.replyingTo) { if (state.replyingTo != null) runCatching { requester.requestFocus() } }
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        state.replyingTo?.let { target ->
            Row(Modifier.padding(start = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Replying to ${formatHandle(target.displayName())}", color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(Modifier.size(28.dp).pressable { state.replyingTo = null }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, "Cancel reply", tint = c.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
        state.postError?.let { Text(it, color = c.error, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)) }

        val canSend = state.draft.isNotBlank() && !state.posting
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(CircleShape)
                .background(c.surfaceContainerHigh)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelAvatar(
                url = Account.profile?.avatar,
                name = Account.profile?.name ?: "Me",
                size = 26,
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (state.draft.isEmpty()) {
                    Text(
                        text = if (state.replyingTo != null) "Add a reply…" else "Share your thoughts...",
                        color = c.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
                BasicTextField(
                    value = state.draft,
                    onValueChange = { state.draft = if (state.isLive) it.take(state.liveMaxMessageLength) else it },
                    textStyle = TextStyle(color = c.onSurface, fontSize = 14.sp),
                    cursorBrush = SolidColor(c.primary),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, capitalization = KeyboardCapitalization.Sentences),
                    keyboardActions = KeyboardActions(onSend = { send() }, onDone = { send() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(requester)
                        .onPreviewKeyEvent { e ->
                            if (e.key == Key.Enter || e.key == Key.NumPadEnter) {
                                if (e.type == KeyEventType.KeyDown) send()
                                true
                            } else false
                        },
                )
            }
            if (canSend || state.posting) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .pressable(enabled = canSend) { send() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.posting) {
                        CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Post",
                            tint = c.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatorHeartBadge(avatarUrl: String?, name: String) {
    val c = MaterialTheme.colorScheme
    val avatarPx = 22
    Box(Modifier.size(26.dp), contentAlignment = Alignment.TopStart) {
        ChannelAvatar(avatarUrl, name, size = avatarPx)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = 2.dp)
                .size(13.dp)
                .clip(CircleShape)
                .background(c.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Favorite, "Hearted by creator", tint = Color(0xFFE53935), modifier = Modifier.size(9.dp))
        }
    }
}

private val TIMESTAMP_REGEX = Regex("\\b(?:(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2}))\\b")

fun parseTimestampToSeconds(text: String): Long? {
    val parts = text.split(':').mapNotNull { it.toLongOrNull() }
    return when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommentTextWithTimestamps(
    text: String,
    onSeekToTimestamp: ((Long) -> Unit)?,
) {
    val c = MaterialTheme.colorScheme
    val matches = TIMESTAMP_REGEX.findAll(text).toList()

    if (matches.isEmpty() || onSeekToTimestamp == null) {
        Text(text, fontSize = 13.5.sp, lineHeight = 18.5.sp, color = c.onSurface)
        return
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        var lastIdx = 0
        matches.forEach { match ->
            if (match.range.first > lastIdx) {
                val segment = text.substring(lastIdx, match.range.first)
                Text(segment, fontSize = 13.5.sp, lineHeight = 18.5.sp, color = c.onSurface)
            }

            val tsStr = match.value
            val sec = parseTimestampToSeconds(tsStr)
            if (sec != null) {
                val isDark = c.surface.luminance() < 0.4f
                val blueColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(blueColor.copy(alpha = 0.16f))
                        .pressable { onSeekToTimestamp(sec) }
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = tsStr,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = blueColor,
                    )
                }
            } else {
                Text(tsStr, fontSize = 13.5.sp, lineHeight = 18.5.sp, color = c.onSurface)
            }
            lastIdx = match.range.last + 1
        }

        if (lastIdx < text.length) {
            val remaining = text.substring(lastIdx)
            Text(remaining, fontSize = 13.5.sp, lineHeight = 18.5.sp, color = c.onSurface)
        }
    }
}

@Composable
private fun TimelineChaptersCarousel(
    comments: List<Comment>,
    onSeekToTimestamp: (Long) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val chapters = remember(comments) {
        val list = mutableListOf<Pair<String, Long>>()
        comments.take(15).forEach { comment ->
            val matches = TIMESTAMP_REGEX.findAll(comment.text)
            matches.forEach { match ->
                val sec = parseTimestampToSeconds(match.value)
                if (sec != null && sec > 0L) {
                    val label = comment.text.replace(match.value, "").trim().trim('-', ':', ' ', '•')
                    val title = if (label.isNotBlank()) label.take(25) else match.value
                    list.add(Pair("${match.value} $title", sec))
                }
            }
        }
        list.distinctBy { it.second }.sortedBy { it.second }
    }

    if (chapters.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = "CHAPTERS & TIMELINE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = c.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chapters, key = { "ch-" + it.second }) { (title, sec) ->
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.secondaryContainer)
                        .pressable { onSeekToTimestamp(sec) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = c.onSecondaryContainer,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.onSecondaryContainer,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    state: CommentsState,
    creatorAvatar: String?,
    creatorName: String,
    isReply: Boolean = false,
    onSeekToTimestamp: ((Long) -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme

    // Compact YouTube-style row for Live Stream Chat Comments
    if (comment.ago == "live") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelAvatar(comment.avatar, comment.author, size = 22)
            Spacer(Modifier.width(8.dp))
            Text(
                formatHandle(comment.displayName()),
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                color = c.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                comment.text,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    val avatarSize = if (isReply) 26 else 32
    val hasReplies = !isReply && comment.replyCount > 0 && comment.repliesToken != null
    val open = state.replies.containsKey(comment.id)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 52.dp else 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(avatarSize.dp)
        ) {
            ChannelAvatar(comment.avatar, comment.author, size = avatarSize)
            if (hasReplies) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .height(if (open) 40.dp else 24.dp)
                        .width(1.5.dp)
                        .background(c.onSurface.copy(alpha = 0.18f))
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (comment.pinned) {
                    Icon(Icons.Rounded.PushPin, "Pinned", tint = c.onSurfaceVariant, modifier = Modifier.size(13.dp))
                }
                Text(
                    text = formatHandle(comment.displayName()),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val isVerified = comment.author.contains("YouTube", ignoreCase = true) || (comment.name?.contains("YouTube", ignoreCase = true) == true)
                if (isVerified) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Verified",
                        tint = c.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                }
                comment.ago?.let { Text("• $it", color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 1) }
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Options",
                    tint = c.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.height(3.dp))
            CommentTextWithTimestamps(comment.text, onSeekToTimestamp)

            var isTranslated by remember { mutableStateOf(false) }
            Text(
                text = if (isTranslated) "See original" else "Translate to Hindi",
                color = c.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .pressable { isTranslated = !isTranslated },
            )

            if (!comment.imageUrl.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Thumbnail(
                    url = comment.imageUrl,
                    modifier = Modifier
                        .width(180.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp)),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                val isLiked = state.liked[comment.id] == true
                val canLike = Account.signedIn && !comment.id.startsWith("local-")
                val likeTint = if (isLiked) c.primary else c.onSurfaceVariant
                Row(
                    Modifier
                        .pressable(enabled = canLike) { state.toggleLike(comment) { state.postError = it } }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FlourishIcon(
                        if (isLiked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                        if (isLiked) "Unlike" else "Like",
                        active = isLiked, kind = Flourish.Pop, tint = likeTint, size = 17.dp,
                    )
                    val count = comment.likes + if (isLiked) 1 else 0
                    if (count > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(formatCount(count.toLong()), color = likeTint, fontSize = 12.sp, fontWeight = FontWeight.Normal)
                    }
                }

                // Dislike button
                Icon(
                    imageVector = Icons.Outlined.ThumbDown,
                    contentDescription = "Dislike",
                    tint = c.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )

                // Comment / Reply bubble
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Comment,
                    contentDescription = "Reply",
                    tint = c.onSurfaceVariant,
                    modifier = Modifier
                        .size(17.dp)
                        .pressable { state.startReply(comment) },
                )

                if (comment.hearted) {
                    if (creatorName.isNotBlank()) {
                        CreatorHeartBadge(creatorAvatar, creatorName)
                    } else {
                        Icon(Icons.Rounded.Favorite, "Hearted by creator", tint = Color(0xFFE53935), modifier = Modifier.size(14.dp))
                    }
                }
            }
            if (hasReplies) {
                val busy = state.repliesLoading[comment.id] == true
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .pressable { if (open) state.hideReplies(comment) else state.loadReplies(comment) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (open) "Hide replies" else "${comment.replyCount} ${if (comment.replyCount == 1) "reply" else "replies"}",
                        color = c.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = c.onSurface,
                        modifier = Modifier.size(16.dp),
                    )
                    if (busy) {
                        Spacer(Modifier.width(6.dp))
                        CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
    if (!isReply) {
        state.replies[comment.id]?.forEach { reply ->
            CommentRow(reply, state, creatorAvatar, creatorName, isReply = true)
        }
    }
}
