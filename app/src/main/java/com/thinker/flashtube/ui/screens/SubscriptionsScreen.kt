package com.thinker.flashtube

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    LaunchedEffect(scrollToTop) { selectedChannelId = null }
    val selectedChannel = remember(selectedChannelId, lib.channels) {
        selectedChannelId?.let { id -> lib.channels.find { it.id == id } }
    }
    val filteredVideos = remember(lib.subscriptions.videos, selectedChannel) {
        val ch = selectedChannel ?: return@remember null
        lib.subscriptions.videos.filter { it.fromChannel(ch) }
    }
    CollapsingHeaderLayout(
        resetKey = scrollToTop to listOf(lib.subscriptions.generation, selectedChannelId, lib.channels.size),
        header = {
            Column(Modifier.fillMaxWidth()) {
                ScreenHeader(title = "Subscriptions") { HeaderActions(onSearch, onAccount) }
                if (signedIn && lib.channels.isNotEmpty()) {
                    SubscriptionsChannelStrip(
                        channels = lib.channels,
                        selectedId = selectedChannelId,
                        onSelect = { id -> selectedChannelId = id },
                        onAll = onOpenAllSubscriptions,
                    )
                }
            }
        },
    ) { top ->
        if (signedIn) {
            when {
                selectedChannel != null && filteredVideos.isNullOrEmpty() && !lib.subscriptions.loading -> {
                    Box(Modifier.fillMaxSize().padding(contentPadding.withTop(top))) {
                        EmptyState(
                            icon = Icons.Rounded.Subscriptions,
                            title = "Nothing recent",
                            body = "No new uploads from ${selectedChannel.name} in this feed yet.",
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
                    filterScrollKey = selectedChannelId,
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
    onSelect: (String?) -> Unit,
    onAll: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val sortedChannels = remember(channels) {
        channels.sortedByDescending { it.isLive }
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(sortedChannels, key = { it.id }) { ch ->
            val selected = ch.id == selectedId
            Column(
                Modifier
                    .width(68.dp)
                    .pressable { onSelect(if (selected) null else ch.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ChannelAvatar(ch.avatar, ch.name, size = 56, selected = selected, ringSlot = true, isLive = ch.isLive)
                Spacer(Modifier.height(6.dp))
                Text(
                    ch.name,
                    fontSize = 12.sp,
                    color = if (ch.isLive) Color(0xFFE53935) else c.onSurfaceVariant,
                    fontWeight = if (ch.isLive) FontWeight.Bold else FontWeight.Normal,
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
                    Text("All", color = c.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

@Composable
fun AllSubscriptionsScreen(
    channels: List<ChannelRef>,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val openChannel = LocalChannelOpener.current
    Box(Modifier.fillMaxSize().background(c.surface)) {
        Column(Modifier.fillMaxSize()) {
            BackScreenHeader(onBack = onBack) {
                Text(
                    "All subscriptions",
                    modifier = Modifier.weight(1f),
                    fontFamily = RoundedHeavy,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    letterSpacing = (-0.8).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LazyColumn(contentPadding = contentPadding) {
                items(channels, key = { it.id }) { ch ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressable(onClick = { openChannel(ch.url) })
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChannelAvatar(ch.avatar, ch.name, size = 48)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            ch.name,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
