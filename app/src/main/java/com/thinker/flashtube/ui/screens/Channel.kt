package com.thinker.flashtube

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import android.content.Intent
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.AsyncImagePainter
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class ChannelTab(val label: String, val params: String) {
    Videos("Videos", InnerTube.CHANNEL_VIDEOS_TAB),
    Shorts("Shorts", InnerTube.CHANNEL_SHORTS_TAB),
    Live("Live", InnerTube.CHANNEL_LIVE_TAB),
    Playlists("Playlists", InnerTube.CHANNEL_PLAYLISTS_TAB),
    Community("Community", InnerTube.CHANNEL_COMMUNITY_TAB),
}

/** Opens a channel page from anywhere (player channel row, card avatars). Provided by the shell. */
val LocalChannelOpener = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Opens the Collaborators bottom sheet when a video has multiple collaborators. Provided by the shell. */
val LocalCollaboratorsOpener = staticCompositionLocalOf<(List<ChannelRef>) -> Unit> { {} }

@Stable
class ChannelState(val url: String, private val scope: CoroutineScope) {
    var details by mutableStateOf<ChannelDetails?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var subscribed by mutableStateOf<Boolean?>(null)
        private set

    var selectedTab by mutableStateOf(ChannelTab.Videos)
        private set

    private val tabItems = mutableStateMapOf<ChannelTab, List<VideoItem>>()
    private val tabNext = mutableStateMapOf<ChannelTab, String?>()
    private val tabLoading = mutableStateMapOf<ChannelTab, Boolean>()

    val currentItems: List<VideoItem> get() = tabItems[selectedTab].orEmpty()
    val currentLoading: Boolean get() = tabLoading[selectedTab] == true
    val currentHasMore: Boolean get() = tabNext[selectedTab] != null

    val videos: List<VideoItem> get() = currentItems
    val hasMore: Boolean get() = currentHasMore

    init { load() }

    fun load() {
        loading = true
        error = null
        scope.launch {
            runCatching { YouTube.channel(url) }
                .onSuccess { base ->
                    var d = base
                    details = d
                    if (Account.signedIn) launch { subscribed = runCatching { InnerTube.channelSubscribed(d.id) }.getOrNull() }
                    runCatching { YouTube.channelVideos(d, ChannelTab.Videos.params, null) }
                        .onSuccess { p ->
                            tabItems[ChannelTab.Videos] = p.videos
                            tabNext[ChannelTab.Videos] = p.next
                            p.channelHeader?.let { d = d.withHeader(it) }
                            details = d
                        }
                        .onFailure {
                            details = d
                            error = YouTube.friendlyError(it)
                        }
                }
                .onFailure { error = YouTube.friendlyError(it) }
            loading = false
        }
    }

    var communityPosts by mutableStateOf<List<CommunityPost>>(emptyList())
        private set
    var communityLoading by mutableStateOf(false)
        private set

    var videoSort by mutableStateOf("Latest")
        private set

    fun selectVideoSort(sort: String) {
        if (videoSort == sort) return
        videoSort = sort
        val params = when (sort) {
            "Popular" -> "EgZ2aWRlb3PyBgQKAjoAQgYIgAQQABgA"
            "Oldest" -> "EgZ2aWRlb3PyBgQKAjoAQgYIgAQQAxgA"
            else -> InnerTube.CHANNEL_VIDEOS_TAB
        }
        val d = details ?: return
        tabLoading[ChannelTab.Videos] = true
        scope.launch {
            runCatching { YouTube.channelVideos(d, params, null) }
                .onSuccess { p ->
                    tabItems[ChannelTab.Videos] = p.videos
                    tabNext[ChannelTab.Videos] = p.next
                }
            tabLoading[ChannelTab.Videos] = false
        }
    }

    fun selectTab(tab: ChannelTab) {
        selectedTab = tab
        if (tab == ChannelTab.Community) {
            loadCommunity()
        } else if (!tabItems.containsKey(tab) && tabLoading[tab] != true) {
            loadTab(tab)
        }
    }

    private fun loadCommunity() {
        val d = details ?: return
        if (communityPosts.isNotEmpty() || communityLoading) return
        communityLoading = true
        scope.launch {
            runCatching { InnerTube.channelCommunityPosts(d.id) }
                .onSuccess { communityPosts = it }
            communityLoading = false
        }
    }

    private fun loadTab(tab: ChannelTab) {
        val d = details ?: return
        tabLoading[tab] = true
        scope.launch {
            runCatching { YouTube.channelVideos(d, tab.params, null) }
                .onSuccess { p ->
                    tabItems[tab] = p.videos
                    tabNext[tab] = p.next
                }
            tabLoading[tab] = false
        }
    }

    fun loadMore() {
        val d = details ?: return
        val page = tabNext[selectedTab] ?: return
        if (tabLoading[selectedTab] == true) return
        tabLoading[selectedTab] = true
        scope.launch {
            runCatching { YouTube.channelVideos(d, selectedTab.params, page) }
                .onSuccess { p ->
                    val curr = tabItems[selectedTab].orEmpty()
                    val seen = curr.mapTo(HashSet()) { it.id }
                    tabItems[selectedTab] = curr + p.videos.filter { seen.add(it.id) }
                    tabNext[selectedTab] = p.next
                }
            tabLoading[selectedTab] = false
        }
    }

    fun toggleSubscribe(onError: (String) -> Unit) {
        val id = details?.id ?: return
        val before = subscribed ?: return
        subscribed = !before
        scope.launch {
            runCatching { InnerTube.setSubscribed(id, !before) }
                .onFailure { subscribed = before; onError("Couldn't update your subscription.") }
        }
    }
}

@Composable
fun ChannelSubscribeButton(subscribed: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .squishOn(subscribed)
            .fillMaxWidth()
            .height(38.dp)
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(if (subscribed) c.secondaryContainer else c.onSurface),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (subscribed) {
            FlourishIcon(Icons.Rounded.NotificationsActive, null, active = true, kind = Flourish.Ring, tint = c.onSecondaryContainer, size = 16.dp)
            Spacer(Modifier.width(5.dp))
        }
        Text(
            if (subscribed) "Subscribed" else "Subscribe",
            color = if (subscribed) c.onSecondaryContainer else c.surface,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
        )
    }
}

@Composable
fun CreatorHeader(
    details: ChannelDetails,
    subscribed: Boolean?,
    onSubscribeToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Column(modifier.padding(horizontal = 16.dp)) {
        if (details.banner != null) {
            Thumbnail(details.banner, Modifier.fillMaxWidth().aspectRatio(5.6f), radius = 12)
            Spacer(Modifier.height(16.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelAvatar(details.avatar, details.name, size = 76, isLive = details.isLive)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                ChannelNameWithBadge(details.name, verified = details.verified, maxLines = 2)
                details.handle?.let { handle ->
                    Spacer(Modifier.height(2.dp))
                    Text("@$handle", color = c.onSurfaceVariant, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                channelStatLine(details.subscribers, details.videoCount).takeIf { it.isNotBlank() }?.let { stats ->
                    Text(stats, color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        subscribed?.let { sub ->
            Spacer(Modifier.height(14.dp))
            ChannelSubscribeButton(sub, onClick = onSubscribeToggle)
        }
        if (details.description.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                details.description,
                color = c.onSurfaceVariant,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ChannelTabs(
    selectedTab: ChannelTab,
    onTabSelected: (ChannelTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ChannelTab.entries.toTypedArray(), key = { it.name }) { tab ->
            val active = tab == selectedTab
            val bgColor by animateColorAsState(
                targetValue = if (active) c.onSurface else c.surfaceContainerHigh,
                animationSpec = if (reduced) tween(0) else spring(dampingRatio = 0.75f, stiffness = 600f),
                label = "tabBg",
            )
            val textColor by animateColorAsState(
                targetValue = if (active) c.surface else c.onSurface,
                animationSpec = if (reduced) tween(0) else spring(dampingRatio = 0.75f, stiffness = 600f),
                label = "tabText",
            )
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .pressable(pressedScale = 0.95f) { onTabSelected(tab) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    color = textColor,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.5.sp,
                )
            }
        }
    }
}

@Composable
fun ChannelPlaylistCard(
    item: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .pressable(onClick = onClick),
    ) {
        StackedPlaylistThumbnail(
            thumbnailUrl = item.thumbnail,
            radius = 12,
            badgeText = item.uploaded ?: "Playlist",
            badgeIcon = Icons.AutoMirrored.Rounded.PlaylistPlay,
            videoId = item.id,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            fontWeight = FontWeight.Bold,
            fontSize = 14.5.sp,
            color = c.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.channel.isNotBlank()) {
            Text(
                text = item.channel,
                color = c.onSurfaceVariant,
                fontSize = 12.5.sp,
            )
        }
    }
}

@Composable
fun ChannelScreen(
    state: ChannelState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (VideoItem) -> Unit,
    onOpenPlaylist: (PlaylistRef) -> Unit = {},
    onMessage: (String) -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    val list = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 4 }
    }
    LaunchedEffect(nearEnd, state.currentItems.size) { if (nearEnd && state.currentItems.isNotEmpty()) state.loadMore() }
    Box(Modifier.fillMaxSize().background(c.surface)) {
        CollapsingHeaderLayout(
            header = {
                BackScreenHeader(onBack = onBack) {
                    ChannelNameWithBadge(
                        state.details?.name.orEmpty(),
                        verified = state.details?.verified == true,
                        modifier = Modifier.weight(1f),
                        fontSize = 22.sp,
                        maxLines = 1,
                        letterSpacing = (-0.6).sp,
                    )
                }
            },
        ) { top ->
            val d = state.details
            when {
                d == null && state.error != null -> Box(Modifier.padding(top = top)) {
                    EmptyState(
                        Icons.Rounded.CloudOff, "Couldn't load this channel", state.error.orEmpty(),
                        action = { PillButton("Try again", state::load) },
                    )
                }
                d == null -> Box(Modifier.fillMaxWidth().padding(top = top + 60.dp), Alignment.Center) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(36.dp))
                }
                else -> LazyColumn(state = list, contentPadding = contentPadding.withTop(top)) {
                    item(key = "header") {
                        CreatorHeader(
                            details = d,
                            subscribed = state.subscribed,
                            onSubscribeToggle = { state.toggleSubscribe(onMessage) },
                        )
                    }
                    item(key = "tabRow") {
                        ChannelTabs(
                            selectedTab = state.selectedTab,
                            onTabSelected = { state.selectTab(it) },
                        )
                    }

                    if (state.selectedTab == ChannelTab.Videos) {
                        item(key = "video_sort_chips") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf("Latest", "Popular", "Oldest").forEach { sort ->
                                    val active = sort == state.videoSort
                                    Box(
                                        modifier = Modifier
                                            .height(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (active) c.surfaceContainerHighest else c.surfaceContainerLow)
                                            .pressable { state.selectVideoSort(sort) }
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = sort,
                                            color = if (active) c.primary else c.onSurfaceVariant,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.5.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    when (state.selectedTab) {
                        ChannelTab.Community -> {
                            items(state.communityPosts, key = { it.id }) { post ->
                                CommunityPostCard(post)
                            }
                        }
                        ChannelTab.Playlists -> {
                            items(state.currentItems, key = { it.id }) { pl ->
                                ChannelPlaylistCard(
                                    item = pl,
                                    onClick = { onOpenPlaylist(PlaylistRef(pl.id, pl.title, pl.thumbnail, pl.uploaded)) },
                                )
                            }
                        }
                        else -> {
                            items(state.currentItems, key = { it.id }) { video ->
                                VideoCard(video, onClick = { onOpen(video) }, radius = 12)
                            }
                        }
                    }

                    if (state.currentLoading || state.communityLoading || state.currentHasMore) item {
                        Box(Modifier.fillMaxWidth().padding(28.dp), Alignment.Center) {
                            CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelAboutScreen(
    channelDetails: ChannelDetails? = null,
    channelName: String,
    channelAvatar: String?,
    channelUrl: String?,
    subscribersCount: Long,
    onBack: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val openChannel = LocalChannelOpener.current
    val url = channelUrl ?: channelDetails?.url

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surface)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(c.surfaceContainerHigh)
                    .pressable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = c.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = "About",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = c.onSurface,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .pressable(enabled = url != null) { url?.let { onBack(); openChannel(it) } }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelAvatar(channelAvatar ?: channelDetails?.avatar, channelName, size = 56)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = channelName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = c.onSurface,
                )
                if (subscribersCount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${formatCount(subscribersCount)} subscribers",
                        color = c.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
