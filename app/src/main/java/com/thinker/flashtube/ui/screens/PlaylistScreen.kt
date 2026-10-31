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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** A playlist's videos (Watch later, Liked, or the user's own) with Koda-inspired hero header. */
@Composable
fun PlaylistScreen(
    playlist: PlaylistRef,
    feed: FeedState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (VideoItem) -> Unit,
    onOpenPlaylist: ((List<VideoItem>, Int) -> Unit)? = null,
    channelAvatarSeed: Map<String, String?> = emptyMap(),
) {
    val c = MaterialTheme.colorScheme
    LaunchedEffect(playlist.id) { feed.ensureLoaded() }
    val videos = feed.videos

    Box(Modifier.fillMaxSize().background(c.surface)) {
        CollapsingHeaderLayout(
            resetKey = feed.generation,
            header = {
                BackScreenHeader(onBack = onBack) {
                    Text(
                        playlist.title,
                        modifier = Modifier.weight(1f),
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        letterSpacing = (-0.8).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        ) { top ->
            if (videos.isEmpty() && !feed.loading && feed.error == null && !feed.hasMore) {
                val icon = if (playlist.id == "DL") Icons.Rounded.Download else Icons.Rounded.VideoLibrary
                val title = if (playlist.id == "DL") "No downloads" else "Nothing here yet"
                val body = if (playlist.id == "DL") "" else "Videos you add to this playlist will show up here."
                Box(Modifier.padding(top = top)) { EmptyState(icon, title, body) }
            } else {
                LazyColumn(
                    state = rememberScrollToTop(0),
                    contentPadding = contentPadding.withTop(top),
                ) {
                    // Playlist Hero Header Card
                    item(key = "playlist_hero") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val heroThumb = playlist.thumbnail ?: videos.firstOrNull()?.thumbnail
                                Box(Modifier.width(130.dp)) {
                                    StackedPlaylistThumbnail(
                                        thumbnailUrl = heroThumb,
                                        radius = 10,
                                        badgeText = "${videos.size} videos",
                                        badgeIcon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                    )
                                }

                                Spacer(Modifier.width(16.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.title,
                                        fontFamily = RoundedHeavy,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = c.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "${videos.size} videos",
                                        color = c.onSurfaceVariant,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Action Buttons Row: Play All | Shuffle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // Play All
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(c.onSurface)
                                        .pressable {
                                            if (onOpenPlaylist != null) {
                                                onOpenPlaylist(videos, 0)
                                            } else {
                                                videos.firstOrNull()?.let(onOpen)
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Play all",
                                            tint = c.surface,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Play all",
                                            color = c.surface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }

                                // Shuffle
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(c.surfaceContainerHigh)
                                        .pressable {
                                            val shuffled = videos.shuffled()
                                            if (onOpenPlaylist != null) {
                                                onOpenPlaylist(shuffled, 0)
                                            } else {
                                                shuffled.firstOrNull()?.let(onOpen)
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Shuffle,
                                            contentDescription = "Shuffle",
                                            tint = c.onSurface,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Shuffle",
                                            color = c.onSurface,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Numbered Playlist Video Items
                    itemsIndexed(videos, key = { index, item -> "${index}_${item.id}" }) { index, video ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = c.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(28.dp),
                            )
                            Box(Modifier.weight(1f)) {
                                VideoRow(
                                    video,
                                    onClick = {
                                        if (onOpenPlaylist != null) {
                                            onOpenPlaylist(videos, index)
                                        } else {
                                            onOpen(video)
                                        }
                                    },
                                )
                            }
                        }
                    }

                    if (feed.loading) {
                        item(key = "playlist_loading") {
                            Box(Modifier.fillMaxWidth().padding(28.dp), Alignment.Center) {
                                CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
