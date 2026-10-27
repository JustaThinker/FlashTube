package com.thinker.flashtube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A playlist's videos (Watch later, Liked, or the user's own). */
@Composable
fun PlaylistScreen(
    playlist: PlaylistRef,
    feed: FeedState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (VideoItem) -> Unit,
    channelAvatarSeed: Map<String, String?> = emptyMap(),
) {
    LaunchedEffect(playlist.id) { feed.ensureLoaded() }
    val avatarSeed = remember(channelAvatarSeed) { channelAvatarSeed }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        CollapsingHeaderLayout(
            resetKey = feed.generation,
            header = {
                BackScreenHeader(onBack = onBack) {
                    Text(
                        playlist.title,
                        modifier = Modifier.weight(1f),
                        fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = (-0.8).sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        ) { top ->
            if (feed.videos.isEmpty() && !feed.loading && feed.error == null && !feed.hasMore) {
                val icon = if (playlist.id == "DL") Icons.Rounded.Download else Icons.Rounded.VideoLibrary
                val title = if (playlist.id == "DL") "No downloads yet" else "Nothing here yet"
                val body = if (playlist.id == "DL") "Downloaded videos will appear here." else "Videos you add to this playlist will show up here."
                Box(Modifier.padding(top = top)) { EmptyState(icon, title, body) }
            } else {
                FeedList(feed, contentPadding.withTop(top), onOpen, indicatorTop = top, channelAvatarSeed = avatarSeed)
            }
        }
    }
}
