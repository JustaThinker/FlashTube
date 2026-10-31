package com.thinker.flashtube

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val FALLBACK_SUBS_CHIPS = listOf(
    FeedChip("All", browseId = "FEsubscriptions", key = "all"),
    FeedChip("Today", key = "today"),
    FeedChip("Videos", key = "videos"),
    FeedChip("Shorts", key = "shorts"),
    FeedChip("Live", key = "live"),
    FeedChip("Unwatched", key = "unwatched"),
    FeedChip("Continue watching", key = "continue_watching"),
)

enum class BellLevel(val label: String, val subtitle: String) {
    ALL("All", "Get all notifications for this channel"),
    PERSONALIZED("Personalized", "Get personalized notifications based on your activity"),
    NONE("None", "You won't get any notifications from this channel"),
}

enum class ChannelSortOrder(val title: String) {
    MOST_RELEVANT("Most relevant"),
    A_TO_Z("A-Z"),
    NEWEST_ACTIVITY("Newest activity"),
}

@Composable
fun SubscriptionsScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onSearch: () -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
    onOpenAllSubscriptions: () -> Unit = {},
) {
    val signedIn = Account.signedIn
    var selectedChannelId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(signedIn) {
        if (signedIn) {
            lib.subscriptions.ensureLoaded()
            if (lib.channels.isEmpty()) lib.loadChannels()
        } else {
            selectedChannelId = null
        }
    }
    LaunchedEffect(scrollToTop) {
        selectedChannelId = null
    }

    val selectedChannel = remember(selectedChannelId, lib.channels) {
        selectedChannelId?.let { id -> lib.channels.find { it.id == id } }
    }

    val liveChannelNamesOrIds = remember(lib.subscriptions.videos) {
        lib.subscriptions.videos
            .filter { it.isLive || it.uploaded?.contains("LIVE", ignoreCase = true) == true || it.uploaded?.contains("streamed", true) == true }
            .flatMap { listOfNotNull(it.channelId()?.lowercase(), it.channel.lowercase()) }
            .toSet()
    }

    val filteredVideos = remember(lib.subscriptions.videos, selectedChannel, lib.subscriptions.selectedChip) {
        val base = if (selectedChannel != null) {
            lib.subscriptions.videos.filter { it.fromChannel(selectedChannel) }
        } else {
            lib.subscriptions.videos
        }

        val chip = lib.subscriptions.selectedChip
        if (chip == null || chip.title.equals("All", true)) {
            base
        } else {
            val title = chip.title
            base.filter { video ->
                when {
                    title.equals("Today", true) -> {
                        val up = video.uploaded?.lowercase().orEmpty()
                        up.contains("second") || up.contains("minute") || up.contains("hour") ||
                            up.contains("now") || up.contains("today") || up.contains("h ago") ||
                            up.contains("m ago") || up.contains("s ago")
                    }
                    title.equals("Videos", true) -> video.durationSec > 60 || (video.durationSec == 0L && video.feedPost == null && !video.isLive)
                    title.equals("Shorts", true) -> (video.durationSec in 1..60 || video.title.contains("short", ignoreCase = true)) && video.feedPost == null
                    title.equals("Live", true) -> video.isLive || video.uploaded?.lowercase()?.contains("live") == true || video.uploaded?.lowercase()?.contains("streamed") == true
                    title.equals("Podcasts", true) -> video.title.contains("podcast", ignoreCase = true) || video.title.contains("ep.", ignoreCase = true)
                    title.equals("Unwatched", true) -> ResumeStore.progress(video.id) == null
                    title.equals("Continue watching", true) -> ResumeStore.progress(video.id) != null
                    else -> true
                }
            }
        }
    }

    val serverChips = lib.subscriptions.chips.ifEmpty { FALLBACK_SUBS_CHIPS }

    CollapsingHeaderLayout(
        resetKey = scrollToTop to listOf(lib.subscriptions.generation, selectedChannelId, lib.subscriptions.selectedChip?.key, lib.channels.size),
        header = {
            Column(Modifier.fillMaxWidth()) {
                ScreenHeader(title = "Subscriptions") { HeaderActions(onSearch, onAccount) }
                if (signedIn && lib.channels.isNotEmpty()) {
                    SubscriptionsChannelStrip(
                        channels = lib.channels,
                        selectedId = selectedChannelId,
                        liveChannelNamesOrIds = liveChannelNamesOrIds,
                        feedVideos = lib.subscriptions.videos,
                        onSelect = { id -> selectedChannelId = id },
                        onAll = onOpenAllSubscriptions,
                    )
                    CategoryChipsBar(
                        categories = serverChips,
                        selectedKey = lib.subscriptions.selectedChip?.key,
                        onCategorySelected = { chip ->
                            lib.subscriptions.selectChip(if (chip.title.equals("All", true)) null else chip)
                        },
                    )
                }
            }
        },
    ) { top ->
        if (signedIn) {
            when {
                filteredVideos.isEmpty() && !lib.subscriptions.loading -> {
                    Box(Modifier.fillMaxSize().padding(contentPadding.withTop(top))) {
                        EmptyState(
                            icon = Icons.Rounded.Subscriptions,
                            title = if (selectedChannel != null) "Nothing recent from ${selectedChannel.name}" else "No matching videos",
                            body = if (selectedChannel != null) "No recent uploads found for this selection." else "Try selecting a different filter chip.",
                        )
                    }
                }
                else -> FeedList(
                    lib.subscriptions,
                    contentPadding.withTop(top),
                    onOpen,
                    scrollToTop = scrollToTop,
                    indicatorTop = top,
                    videos = filteredVideos,
                    filterScrollKey = selectedChannelId to lib.subscriptions.selectedChip?.key,
                )
            }
        } else {
            LazyColumn(contentPadding = contentPadding.withTop(top)) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Subscriptions,
                        title = "Your subscriptions",
                        body = "Sign in with Google to see new videos from the channels you follow.",
                        action = { PillButton("Sign in with Google", onSignIn) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionsChannelStrip(
    channels: List<ChannelRef>,
    selectedId: String?,
    liveChannelNamesOrIds: Set<String>,
    feedVideos: List<VideoItem>,
    onSelect: (String?) -> Unit,
    onAll: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val sortedChannels = remember(channels, liveChannelNamesOrIds, feedVideos) {
        channels.sortedByDescending { ch ->
            ch.isLive ||
                ch.id.lowercase() in liveChannelNamesOrIds ||
                ch.name.lowercase() in liveChannelNamesOrIds ||
                feedVideos.any { v -> v.fromChannel(ch) && (v.isLive || v.uploaded?.contains("LIVE", ignoreCase = true) == true) }
        }
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(sortedChannels, key = { it.id }) { ch ->
            val selected = ch.id == selectedId
            val isLiveNow = ch.isLive ||
                ch.id.lowercase() in liveChannelNamesOrIds ||
                ch.name.lowercase() in liveChannelNamesOrIds ||
                feedVideos.any { v -> v.fromChannel(ch) && (v.isLive || v.uploaded?.contains("LIVE", ignoreCase = true) == true) }

            Column(
                Modifier
                    .width(68.dp)
                    .pressable { onSelect(if (selected) null else ch.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ChannelAvatar(ch.avatar, ch.name, size = 56, selected = selected, ringSlot = true, isLive = isLiveNow)
                    if (isLiveNow) {
                        // Prominent Red Live Dot Indicator
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 2.dp, bottom = 2.dp)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935))
                                .border(2.dp, c.surface, CircleShape),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    ch.name,
                    fontSize = 12.sp,
                    color = if (isLiveNow) Color(0xFFE53935) else if (selected) c.onSurface else c.onSurfaceVariant,
                    fontWeight = if (isLiveNow || selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item(key = "all_channels") {
            Column(
                Modifier
                    .width(68.dp)
                    .pressable(onClick = onAll),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(c.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("All", color = Color(0xFF3EA6FF), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "View all",
                    fontSize = 12.sp,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllSubscriptionsScreen(
    channels: List<ChannelRef>,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val openChannel = LocalChannelOpener.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(ChannelSortOrder.MOST_RELEVANT) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    val bellStates = remember { mutableStateMapOf<String, BellLevel>() }
    var selectedSheetChannel by remember { mutableStateOf<ChannelRef?>(null) }
    val localChannels = remember(channels) { channels.toMutableList() }

    val filteredSortedChannels = remember(localChannels, searchQuery, sortOrder) {
        val searched = if (searchQuery.isBlank()) {
            localChannels
        } else {
            localChannels.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.handle?.contains(searchQuery, ignoreCase = true) == true
            }
        }
        when (sortOrder) {
            ChannelSortOrder.MOST_RELEVANT -> searched.sortedByDescending { it.isLive }
            ChannelSortOrder.A_TO_Z -> searched.sortedBy { it.name.lowercase() }
            ChannelSortOrder.NEWEST_ACTIVITY -> searched.sortedWith(
                compareByDescending<ChannelRef> { it.isLive }.thenBy { it.name.lowercase() }
            )
        }
    }

    Box(Modifier.fillMaxSize().background(c.surface)) {
        Column(Modifier.fillMaxSize()) {
            if (isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(c.surface)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        isSearchActive = false
                        searchQuery = ""
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close search", tint = c.onSurface)
                    }
                    Spacer(Modifier.width(4.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface, fontSize = 16.sp),
                        cursorBrush = SolidColor(c.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { }),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text("Search channels…", color = c.onSurfaceVariant, fontSize = 16.sp)
                            }
                            innerTextField()
                        },
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = c.onSurfaceVariant)
                        }
                    }
                }
            } else {
                BackScreenHeader(onBack = onBack) {
                    Text(
                        "All subscriptions",
                        modifier = Modifier.weight(1f),
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search channels", tint = c.onSurface)
                    }
                    Box {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = c.onSurface)
                        }
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sort order") },
                                onClick = {
                                    showOptionsMenu = false
                                    showSortMenu = true
                                },
                            )
                        }
                    }
                }
            }

            // Sort order chip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.surfaceContainerHigh)
                            .pressable(onClick = { showSortMenu = true })
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            sortOrder.title,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = c.onSurface,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.ArrowDropDown,
                            contentDescription = "Select sort order",
                            tint = c.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                    ) {
                        ChannelSortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        order.title,
                                        fontWeight = if (order == sortOrder) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                leadingIcon = if (order == sortOrder) {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, tint = c.primary) }
                                } else null,
                                onClick = {
                                    sortOrder = order
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                }
            }

            LazyColumn(contentPadding = contentPadding) {
                items(filteredSortedChannels, key = { it.id }) { ch ->
                    val bell = bellStates[ch.id] ?: BellLevel.PERSONALIZED
                    SubscriptionChannelRow(
                        channel = ch,
                        bellLevel = bell,
                        onOpenChannel = { openChannel(ch.url) },
                        onBellClick = { selectedSheetChannel = ch },
                    )
                }
            }
        }

        selectedSheetChannel?.let { ch ->
            val bell = bellStates[ch.id] ?: BellLevel.PERSONALIZED
            ChannelNotificationSheet(
                channel = ch,
                currentBell = bell,
                onDismiss = { selectedSheetChannel = null },
                onSelectBell = { newLevel ->
                    bellStates[ch.id] = newLevel
                    selectedSheetChannel = null
                },
                onUnsubscribe = {
                    scope.launch {
                        runCatching { InnerTube.setSubscribed(ch.id, false) }
                        localChannels.removeIf { it.id == ch.id }
                        selectedSheetChannel = null
                    }
                },
            )
        }
    }
}

@Composable
private fun SubscriptionChannelRow(
    channel: ChannelRef,
    bellLevel: BellLevel,
    onOpenChannel: () -> Unit,
    onBellClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onOpenChannel)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelAvatar(channel.avatar, channel.name, size = 52, isLive = channel.isLive)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                channel.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitleText = buildString {
                if (!channel.handle.isNullOrBlank()) append(channel.handle)
                if (!channel.subscriberCountText.isNullOrBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(channel.subscriberCountText)
                }
            }
            if (subtitleText.isNotEmpty()) {
                Text(
                    subtitleText,
                    fontSize = 13.sp,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))

        // Bell Notification Button [ 🔔 ∨ ]
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(c.surfaceContainerHigh)
                .pressable(onClick = onBellClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val bellIcon = when (bellLevel) {
                BellLevel.ALL -> Icons.Rounded.NotificationsActive
                BellLevel.PERSONALIZED -> Icons.Rounded.Notifications
                BellLevel.NONE -> Icons.Rounded.NotificationsOff
            }
            Icon(
                imageVector = bellIcon,
                contentDescription = bellLevel.label,
                tint = c.onSurface,
                modifier = Modifier.size(20.dp),
            )
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = "Change notification settings",
                tint = c.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelNotificationSheet(
    channel: ChannelRef,
    currentBell: BellLevel,
    onDismiss: () -> Unit,
    onSelectBell: (BellLevel) -> Unit,
    onUnsubscribe: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChannelAvatar(channel.avatar, channel.name, size = 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(channel.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.onSurface)
                    channel.handle?.let { Text(it, fontSize = 13.sp, color = c.onSurfaceVariant) }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = c.outlineVariant.copy(alpha = 0.5f))

            BellLevel.entries.forEach { level ->
                val isSelected = level == currentBell
                val icon = when (level) {
                    BellLevel.ALL -> Icons.Rounded.NotificationsActive
                    BellLevel.PERSONALIZED -> Icons.Rounded.Notifications
                    BellLevel.NONE -> Icons.Rounded.NotificationsOff
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable(onClick = { onSelectBell(level) })
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, tint = if (isSelected) c.primary else c.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            level.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 16.sp,
                            color = if (isSelected) c.primary else c.onSurface,
                        )
                        Text(level.subtitle, fontSize = 12.sp, color = c.onSurfaceVariant)
                    }
                    if (isSelected) {
                        Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = c.primary, modifier = Modifier.size(22.dp))
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = c.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable(onClick = onUnsubscribe)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PersonRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    "Unsubscribe",
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
