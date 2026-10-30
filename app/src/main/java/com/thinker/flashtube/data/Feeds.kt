package com.thinker.flashtube

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Persistent store for seen video IDs so recommendations don't repeat across launches. */
object SeenStore {
    private const val PREFS_NAME = "seen_videos_prefs"
    private const val KEY_SEEN = "seen_ids"
    private const val MAX_SEEN = 500

    private val prefs by lazy {
        FlashTubeApplication.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val lock = Any()
    private var loaded = false
    private val set = LinkedHashSet<String>()

    fun load(): HashSet<String> = synchronized(lock) {
        if (!loaded) {
            val raw = runCatching { prefs.getString(KEY_SEEN, null) }.getOrNull()
            if (!raw.isNullOrBlank()) {
                raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { set.add(it) }
            }
            loaded = true
        }
        HashSet(set)
    }

    fun addAll(ids: Collection<String>) = synchronized(lock) {
        load()
        var changed = false
        for (id in ids) {
            if (id.isNotBlank()) {
                set.remove(id)
                set.add(id)
                changed = true
            }
        }
        while (set.size > MAX_SEEN) {
            val first = set.iterator().next()
            set.remove(first)
            changed = true
        }
        if (changed) {
            runCatching {
                prefs.edit().putString(KEY_SEEN, set.joinToString(",")).apply()
            }
        }
    }

    fun clear() = synchronized(lock) {
        set.clear()
        loaded = true
        runCatching {
            prefs.edit().remove(KEY_SEEN).apply()
        }
    }
}

/** Instant local cache for home recommendations so startup renders instantly. */
object HomeFeedCache {
    private const val PREFS_NAME = "home_feed_cache_prefs"
    private const val KEY_CACHE = "cached_home_items"
    private const val KEY_TIMESTAMP = "cached_home_time"
    private const val MAX_CACHE = 30
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    private val prefs by lazy {
        FlashTubeApplication.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun save(videos: List<VideoItem>) {
        runCatching {
            val arr = org.json.JSONArray()
            videos.take(MAX_CACHE).forEach { v ->
                val obj = org.json.JSONObject()
                    .put("id", v.id)
                    .put("title", v.title)
                    .put("channel", v.channel)
                    .put("channelUrl", v.channelUrl ?: "")
                    .put("thumbnail", v.thumbnail ?: "")
                    .put("avatar", v.avatar ?: "")
                    .put("durationSec", v.durationSec)
                    .put("views", v.views)
                    .put("uploaded", v.uploaded ?: "")
                    .put("mixPlaylistId", v.mixPlaylistId ?: "")
                arr.put(obj)
            }
            prefs.edit()
                .putString(KEY_CACHE, arr.toString())
                .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                .apply()
        }
    }

    fun load(nowMs: Long = System.currentTimeMillis()): List<VideoItem> {
        val savedAt = prefs.getLong(KEY_TIMESTAMP, 0L)
        if (savedAt > 0 && nowMs - savedAt > MAX_AGE_MS) return emptyList()
        val raw = prefs.getString(KEY_CACHE, null) ?: return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            val list = mutableListOf<VideoItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    VideoItem(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        channel = o.getString("channel"),
                        channelUrl = o.optString("channelUrl").ifBlank { null },
                        thumbnail = o.optString("thumbnail").ifBlank { null },
                        avatar = o.optString("avatar").ifBlank { null },
                        durationSec = o.optLong("durationSec", 0L),
                        views = o.optLong("views", -1L),
                        uploaded = o.optString("uploaded").ifBlank { null },
                        mixPlaylistId = o.optString("mixPlaylistId").ifBlank { null },
                    )
                )
            }
            list
        }.getOrElse { emptyList() }
    }
}

/** Last YouTube-returned Home filters, scoped to the active account and selected region. */
private object HomeChipCache {
    private val prefs by lazy {
        FlashTubeApplication.appContext.getSharedPreferences("home_chip_cache_prefs", Context.MODE_PRIVATE)
    }

    fun load(identity: String): List<FeedChip> = runCatching {
        val raw = prefs.getString(identity, null) ?: return emptyList()
        val array = org.json.JSONArray(raw)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            FeedChip(
                title = item.getString("title"),
                browseId = item.optString("browseId").ifBlank { null },
                params = item.optString("params").ifBlank { null },
                navigationEndpoint = item.optJSONObject("navigationEndpoint"),
                key = item.optString("key").ifBlank { item.getString("title") },
            )
        }
    }.getOrDefault(emptyList())

    fun save(identity: String, chips: List<FeedChip>) {
        if (chips.isEmpty()) return
        runCatching {
            val array = org.json.JSONArray()
            chips.forEach { chip ->
                array.put(org.json.JSONObject()
                    .put("title", chip.title)
                    .put("browseId", chip.browseId ?: "")
                    .put("params", chip.params ?: "")
                    .put("navigationEndpoint", chip.navigationEndpoint)
                    .put("key", chip.key))
            }
            prefs.edit().putString(identity, array.toString()).apply()
        }
    }
}

/** A pageable list of videos from the signed-in account (home, subscriptions, history, a playlist). */
@Stable
class FeedState(
    private val scope: CoroutineScope,
    /**
     * Home: every refresh should bring different suggestions, as in the YouTube app. The
     * signed-in web feed returns much the same first page each time, so a refresh reads
     * on into the recommendation stream and keeps only videos not yet shown this session.
     */
    private val freshOnRefresh: Boolean = false,
    private val loader: suspend (next: String?) -> FeedPage,
) {
    var videos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Bumped when a refresh lands; lists scroll back to the top on it. */
    var generation by mutableStateOf(0)
        private set
    /** A pull/button refresh is running (the first load shows its own spinner). */
    var refreshing by mutableStateOf(false)
        private set
    var chips by mutableStateOf<List<FeedChip>>(emptyList())
        private set
    var chipsLoading by mutableStateOf(false)
        private set
    var selectedChip by mutableStateOf<FeedChip?>(null)
        private set
    private var next: String? = null
    private var loaded = false
    private var lastLoadIdentity: Any? = null
    /** Ids already put in front of the user across sessions (fresh-refresh feeds only). */
    private val shown = if (freshOnRefresh) SeenStore.load() else HashSet()
    private var job: Job? = null
    private var chipJob: Job? = null
    private var chipRequestAttempted = false
    private var chipCacheIdentity: String? = null
    val hasMore get() = next != null

    fun selectChip(chip: FeedChip?) {
        if (selectedChip?.key == chip?.key) return
        selectedChip = chip
        videos = emptyList()
        next = null
        error = null
        refresh()
    }

    fun hideVideo(id: String) {
        val updated = videos.filterNot { it.feedKey == id || it.id == id }
        if (updated.size == videos.size) return
        videos = updated
        generation++
        if (freshOnRefresh) HomeFeedCache.save(updated)
    }

    fun ensureLoaded(identity: Any? = null) {
        val identityChanged = identity != null && lastLoadIdentity != null && lastLoadIdentity != identity
        if (identity != null) lastLoadIdentity = identity
        if (identityChanged && loaded && !loading) {
            selectedChip = null
            chips = emptyList()
            chipRequestAttempted = false
            refresh()
            return
        }
        if (!loaded && !loading) {
            if (videos.isNotEmpty()) {
                loaded = true
                return
            }
            if (freshOnRefresh) {
                val cached = HomeFeedCache.load()
                if (cached.isNotEmpty()) {
                    videos = cached
                    loaded = true
                    return
                }
            }
            refresh()
        }
    }

    /** Fetch YouTube's server-provided Home filters when videos came from the instant cache. */
    fun ensureChipsLoaded(cacheIdentity: String) {
        if (chipCacheIdentity != cacheIdentity) {
            chipCacheIdentity = cacheIdentity
            chips = HomeChipCache.load(cacheIdentity)
            chipRequestAttempted = false
        }
        if (!freshOnRefresh || loading || chipsLoading || chipRequestAttempted) return
        chipRequestAttempted = true
        chipsLoading = true
        chipJob = scope.launch {
            runCatching { loader(null) }
                .onSuccess { page ->
                    if (page.chips.isNotEmpty()) {
                        chips = page.chips
                        HomeChipCache.save(cacheIdentity, page.chips)
                    }
                }
            chipsLoading = false
        }
    }

    fun refresh() {
        loaded = true
        loading = true
        refreshing = videos.isNotEmpty()
        error = null
        chipJob?.cancel()
        chipJob = null
        chipsLoading = false
        job?.cancel()
        job = scope.launch {
            // A selected server chip must show that exact topic feed; don't filter it through
            // the Home recommendation de-duplication window.
            runCatching {
                if (freshOnRefresh && selectedChip == null && shown.isNotEmpty()) loadFresh()
                else loader(null)
            }
                .onSuccess {
                    videos = it.videos; next = it.next; generation++
                    if (it.chips.isNotEmpty()) {
                        chips = it.chips
                        chipCacheIdentity?.let { identity -> HomeChipCache.save(identity, it.chips) }
                    }
                    chipRequestAttempted = true
                    if (freshOnRefresh) {
                        val ids = it.videos.mapNotNull { v ->
                            when {
                                v.feedPost != null -> v.feedKey
                                v.mixPlaylistId != null -> v.feedKey
                                else -> v.id
                            }
                        }
                        shown += ids
                        SeenStore.addAll(ids)
                        HomeFeedCache.save(it.videos)
                    }
                }
                .onFailure {
                    error = friendly(it)
                    chipRequestAttempted = true
                }
            loading = false
            refreshing = false
        }
    }

    fun loadMore() {
        val token = next ?: return
        if (loading) return
        loading = true
        job = scope.launch {
            runCatching { loader(token) }
                .onSuccess { page ->
                    val seen = videos.mapTo(HashSet()) { it.feedKey }
                    val added = page.videos.filter { seen.add(it.feedKey) }
                    videos = videos + added
                    if (page.chips.isNotEmpty()) {
                        chips = page.chips
                        chipCacheIdentity?.let { identity -> HomeChipCache.save(identity, page.chips) }
                    }
                    chipRequestAttempted = true
                    if (freshOnRefresh) {
                        val ids = added.mapNotNull { v ->
                            when {
                                v.feedPost != null -> v.feedKey
                                v.mixPlaylistId != null -> v.feedKey
                                else -> v.id
                            }
                        }
                        shown += ids
                        SeenStore.addAll(ids)
                    }
                    next = page.next
                }
                .onFailure {
                    error = friendly(it)
                }
            loading = false
        }
    }

    /**
     * Unseen videos only: reads up to [FRESH_PAGES] pages into the feed until there are
     * [FRESH_TARGET]. If YouTube has nothing new at all, falls back to the plain first page.
     */
    private suspend fun loadFresh(): FeedPage {
        val first = loader(null)
        val fresh = LinkedHashMap<String, VideoItem>()
        var token = first.next
        first.videos.filter { it.feedKey !in shown && it.id !in shown }.forEach { fresh[it.feedKey] = it }
        var pages = 1
        while (fresh.size < FRESH_TARGET && token != null && pages < FRESH_PAGES) {
            val page = loader(token)
            page.videos.filter { it.feedKey !in shown && it.id !in shown }.forEach { fresh.putIfAbsent(it.feedKey, it) }
            token = page.next
            pages++
        }
        if (fresh.isEmpty()) return first
        return FeedPage(fresh.values.toList(), token, chips = first.chips)
    }



    fun reset() {
        job?.cancel()
        if (freshOnRefresh) {
            shown.clear()
            shown += SeenStore.load()
        }
        videos = emptyList(); next = null; loaded = false; loading = false; error = null
        chips = emptyList(); chipsLoading = false; chipRequestAttempted = false; chipCacheIdentity = null; selectedChip = null
        chipJob?.cancel(); chipJob = null
        lastLoadIdentity = null
    }

    private companion object {
        const val FRESH_TARGET = 30
        const val FRESH_PAGES = 10
    }

    private fun friendly(t: Throwable) = when (t) {
        is NotSignedInException -> "You're signed out."
        else -> t.message?.takeIf { it.startsWith("YouTube") } ?: YouTube.friendlyError(t)
    }
}

/** Everything the signed-in tabs show, kept above the tab switcher so it survives tab changes. */
@Stable
class LibraryData(scope: CoroutineScope) {
    /** Home feed: uses anonymous InnerTube when signed out so logged-out users see popular videos. */
    val home: FeedState by lazy {
        FeedState(scope, freshOnRefresh = true) { next -> InnerTube.homeFeed(next, home.selectedChip) }
    }
    val subscriptions = FeedState(scope) { InnerTube.subscriptionsFeed(it) }
    val history = FeedState(scope) { InnerTube.history(it) }
    var homePosts by mutableStateOf<List<CommunityPost>>(emptyList())
        private set
    var homePostsLoading by mutableStateOf(false)
        private set
    private var homePostChannelOrder: List<ChannelRef> = emptyList()
    private var homePostNextChannel = 0
    private var homePostChannelsStarted = false
    private var homePostJob: Job? = null
    private var homePostGeneration = 0L
    private var homeIdentity: Any? = null
    var channels by mutableStateOf<List<ChannelRef>>(emptyList())
        private set
    var playlists by mutableStateOf<List<PlaylistRef>>(emptyList())
        private set
    private val scope = scope

    fun loadChannels() = scope.launch { runCatching { InnerTube.channels() }.onSuccess { channels = it } }
    fun loadPlaylists() = scope.launch { runCatching { InnerTube.playlists() }.onSuccess { playlists = it } }

    /** Track account/region changes in the persistent feed state, not the disposable tab UI. */
    fun ensureHomeLoaded(identity: Any) {
        if (homeIdentity != null && homeIdentity != identity) refreshHomePosts()
        homeIdentity = identity
        home.ensureLoaded(identity)
    }

    /** Interleave server-fetched channel posts among normal videos, never anchoring them to mixes. */
    fun homeRows(): List<VideoItem> {
        val base = home.videos
        val supplementalPosts = if (home.selectedChip == null) {
            val present = base.mapNotNullTo(HashSet()) { it.feedPost?.id }
            homePosts.filterNot { it.id in present }
        } else emptyList()
        if (supplementalPosts.isEmpty()) return base

        val rows = ArrayList<VideoItem>(base.size + supplementalPosts.size)
        var postIndex = 0
        var videosSincePost = 0
        for (item in base) {
            rows += item
            // Keep posts independent from mix placement: count ordinary videos only.
            if (item.feedPost == null && item.mixPlaylistId == null) videosSincePost++
            if (videosSincePost >= 5 && postIndex < supplementalPosts.size) {
                rows += supplementalPosts[postIndex++].asHomePostRow()
                videosSincePost = 0
            }
        }
        while (postIndex < supplementalPosts.size) rows += supplementalPosts[postIndex++].asHomePostRow()
        return rows
    }

    private fun CommunityPost.asHomePostRow() = VideoItem(
        id = "post:$id",
        title = text.ifBlank { author },
        channel = author,
        channelUrl = channelId?.let { "https://www.youtube.com/channel/$it" },
        thumbnail = images.firstOrNull(),
        avatar = authorAvatar,
        durationSec = 0L,
        views = -1L,
        uploaded = publishedTime,
        feedPost = this,
    )

    /** Load community posts from subscribed channels and recommended/similar channels from Home feed. */
    fun ensureHomePosts(videoCount: Int) {
        if (videoCount <= 0 || homePostJob?.isActive == true || homePosts.size >= (videoCount + 4) / 5) return
        if (homePostChannelsStarted && homePostNextChannel >= homePostChannelOrder.size) return
        homePostsLoading = true
        val generation = homePostGeneration
        homePostJob = scope.launch {
            try {
                if (!homePostChannelsStarted) {
                    val followed = if (Account.signedIn) runCatching { InnerTube.channels() }.getOrDefault(emptyList()) else emptyList()
                    val videoChannels = home.videos.mapNotNull { v ->
                        val chId = v.channelUrl?.substringAfterLast("/")?.takeIf { it.startsWith("UC") }
                        if (chId != null) ChannelRef(id = chId, name = v.channel, avatar = v.feedAvatar()) else null
                    }
                    val combined = (followed + videoChannels).distinctBy { it.id }.shuffled()
                    if (generation != homePostGeneration) return@launch
                    homePostChannelOrder = combined
                    homePostNextChannel = 0
                    homePostChannelsStarted = true
                }
                var batchesTried = 0
                var added = false
                while (!added && homePostNextChannel < homePostChannelOrder.size && batchesTried < 5) {
                    batchesTried++
                    val batch = homePostChannelOrder.subList(
                        homePostNextChannel,
                        minOf(homePostChannelOrder.size, homePostNextChannel + 3),
                    )
                    homePostNextChannel += batch.size
                    val fetched = batch.map { channel ->
                        async {
                            runCatching { InnerTube.channelCommunityPosts(channel.id).take(2) }
                                .getOrDefault(emptyList())
                        }
                    }.awaitAll()
                    if (generation != homePostGeneration) return@launch
                    val known = homePosts.mapTo(HashSet()) { it.id }
                    val fresh = fetched.flatten().filter { known.add(it.id) }.shuffled()
                    if (fresh.isNotEmpty()) {
                        homePosts = homePosts + fresh
                        added = true
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            } finally {
                if (generation == homePostGeneration) homePostsLoading = false
            }
        }
    }

    fun refreshHomePosts() {
        homePostGeneration++
        homePostJob?.cancel()
        homePostJob = null
        homePostsLoading = false
        homePosts = emptyList()
        homePostChannelOrder = emptyList()
        homePostNextChannel = 0
        homePostChannelsStarted = false
    }

    fun playlistFeed(id: String) = FeedState(scope) {
        if (id == "DL") {
            val downloadedItems = Downloads.tasks.filter { it.status == DownloadStatus.Complete }.map { it.toVideoItem() }
            FeedPage(downloadedItems, null)
        } else {
            InnerTube.playlist(id, it)
        }
    }

    fun reset() {
        home.reset(); subscriptions.reset(); history.reset()
        refreshHomePosts()
        homeIdentity = null
        channels = emptyList(); playlists = emptyList()
    }
}

/**
 * A list state that animates back to the top whenever [signal] changes (tapping the
 * already-selected tab). The value it was created with is ignored, so merely showing a
 * tab never scrolls it.
 */
@Composable
fun rememberScrollToTop(signal: Int): LazyListState {
    val state = rememberLazyListState()
    val initial = remember { signal }
    LaunchedEffect(signal) { if (signal != initial) state.animateScrollToItem(0) }
    return state
}

/** A feed as a column of video cards with infinite scroll and designed loading/error states. */
@Composable
fun FeedList(
    feed: FeedState,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    header: LazyListScope.() -> Unit = {},
    scrollToTop: Int = 0,
    pullToRefresh: Boolean = true,
    /** Collapsing header height: the refresh spinner appears just below it. */
    indicatorTop: Dp = 0.dp,
    /** When set, shown instead of [FeedState.videos] (e.g. subscriptions channel filter). */
    videos: List<VideoItem>? = null,
    /** Scrolls the list to the top when this changes (e.g. subscriptions channel pick). */
    filterScrollKey: Any? = null,
    /** Subscription channel avatars to avoid extra lookups on playlist rows. */
    channelAvatarSeed: Map<String, String?> = emptyMap(),
    useCompactRows: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    isLoadingMore: Boolean = false,
    hasMore: Boolean = true,
    onRefresh: (() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    val listVideos = videos ?: feed.videos
    val scope = rememberCoroutineScope()
    LaunchedEffect(listVideos, channelAvatarSeed) {
        ChannelAvatars.resolve(scope, listVideos, channelAvatarSeed)
    }
    val list = rememberScrollToTop(scrollToTop)
    // Fresh content always starts at the top (first load and every refresh).
    LaunchedEffect(feed.generation, filterScrollKey) { list.scrollToItem(0) }
    val nearEnd by remember {
        derivedStateOf {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= list.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, listVideos.size) {
        if (nearEnd && listVideos.isNotEmpty()) {
            if (onLoadMore != null) onLoadMore() else feed.loadMore()
        }
    }
    PullToRefresh(enabled = pullToRefresh, refreshing = feed.refreshing, onRefresh = onRefresh ?: feed::refresh, topPadding = indicatorTop) {
    LazyColumn(state = list, contentPadding = contentPadding) {
        header()
        items(listVideos, key = { it.feedKey }) { video ->
            if (video.feedPost != null) {
                CommunityPostCard(video.feedPost)
            } else if (video.mixPlaylistId != null) {
                MixCard(video, onClick = { onOpen(video) })
            } else if (useCompactRows) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                    VideoRow(video, onClick = { onOpen(video) })
                }
            } else {
                VideoCard(video, onClick = { onOpen(video) })
            }
        }
        when {
            // No keys on these trailing items: a keyed loader at index 0 makes LazyColumn
            // hold it in view when videos arrive above it, landing at the bottom of the page.
            feed.error != null && listVideos.isEmpty() -> item {
                EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = "Couldn't load this",
                    body = feed.error.orEmpty(),
                    action = { PillButton("Try again", { feed.refresh() }) },
                )
            }
            isLoadingMore || feed.loading || (onLoadMore != null && hasMore) || (onLoadMore == null && feed.hasMore) -> item {
                Box(Modifier.fillMaxWidth().padding(28.dp), Alignment.Center) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
    }
}

/** YouTube-style loading placeholder for the first page, before any feed rows exist. */
@Composable
private fun FeedSkeletonCard() {
    val c = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "feed-skeleton")
    val sweep by transition.animateFloat(
        initialValue = -0.7f,
        targetValue = 1.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "feed-skeleton-sweep",
    )
    val shimmer = Brush.linearGradient(
        colors = listOf(c.surfaceContainerLow, c.surfaceContainerHighest, c.surfaceContainerLow),
        start = androidx.compose.ui.geometry.Offset(sweep * 900f, 0f),
        end = androidx.compose.ui.geometry.Offset((sweep + 0.8f) * 900f, 700f),
    )
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Box(Modifier.fillMaxWidth().height(210.dp).background(shimmer))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(shimmer))
            Column(Modifier.weight(1f).padding(start = 12.dp, top = 2.dp)) {
                Box(Modifier.fillMaxWidth(0.88f).height(13.dp).clip(RoundedCornerShape(5.dp)).background(shimmer))
                Box(Modifier.padding(top = 8.dp).width(156.dp).height(11.dp).clip(RoundedCornerShape(5.dp)).background(shimmer))
                Box(Modifier.padding(top = 7.dp).width(112.dp).height(11.dp).clip(RoundedCornerShape(5.dp)).background(shimmer))
            }
        }
    }
}

/**
 * Swipe down at the top to reload (replaces the old refresh button). The indicator uses
 * the theme's colors and sits just under the screen header.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PullToRefresh(enabled: Boolean, refreshing: Boolean, onRefresh: () -> Unit, topPadding: Dp = 0.dp, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val c = MaterialTheme.colorScheme
    val pull = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = pull,
        indicator = {
            androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                state = pull, isRefreshing = refreshing,
                containerColor = c.primaryContainer, color = c.onPrimaryContainer,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topPadding),
            )
        },
    ) { content() }
}
