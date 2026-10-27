package com.thinker.flashtube

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ClosedCaptionDisabled
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Long-press options ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoOptionsSheet(video: VideoItem, player: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(video.thumbnail, Modifier.width(112.dp).aspectRatio(16f / 9f), radius = 14)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(video.channel, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
            }
        }
        OptionRow(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next") { player.playNext(video); onDismiss() }
        OptionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to queue") { player.addToQueue(video); onDismiss() }
        if (Account.signedIn) {
            OptionRow(Icons.Rounded.WatchLater, "Save to Watch later") { player.saveToWatchLater(video); onDismiss() }
        }
        OptionRow(Icons.Rounded.Share, "Share") {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${video.id}")
                .putExtra(Intent.EXTRA_SUBJECT, video.title)
            context.startActivity(Intent.createChooser(send, null))
            onDismiss()
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OptionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().pressable(pressedScale = 0.98f, onClick = onClick).padding(horizontal = 24.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.onSurface)
        Spacer(Modifier.width(18.dp))
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

// ── Queue ──────────────────────────────────────────────────────────────────────

/** Card under the player when something is queued; opens the queue sheet. */
@Composable
fun QueueCard(player: PlayerState, onOpen: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val ambient = LocalAmbientColor.current
    val isDark = c.surface.luminance() < 0.4f
    val hasAmbient = AmbientMode.enabled && isDark && ambient != Color.Unspecified && ambient != c.surface

    val shape = RoundedCornerShape(24.dp)
    val cardBg = if (hasAmbient) lerpColor(c.secondaryContainer, ambient.copy(alpha = 0.35f), 0.20f) else c.secondaryContainer
    val shadowColor = if (hasAmbient) ambient else Color.Black

    val next = player.queue.firstOrNull() ?: return
    Row(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .shadow(
                elevation = if (hasAmbient) 3.dp else 0.dp,
                shape = shape,
                ambientColor = shadowColor.copy(alpha = 0.45f),
                spotColor = shadowColor.copy(alpha = 0.30f),
            )
            .pressable(pressedScale = 0.98f, onClick = onOpen)
            .clip(shape)
            .background(cardBg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(next.thumbnail, Modifier.width(88.dp).aspectRatio(16f / 9f), radius = 12)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Queue · ${player.queue.size}",
                color = c.onSecondaryContainer, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
            )
            Text(next.title, color = c.onSecondaryContainer, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = c.onSecondaryContainer)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(player: PlayerState, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainerLow) {
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Queue", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, modifier = Modifier.weight(1f))
            if (player.queue.isNotEmpty()) {
                Text(
                    "Clear", color = c.primary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                    modifier = Modifier.pressable { player.clearQueue() }.padding(8.dp),
                )
            }
        }
        if (player.queue.isEmpty()) {
            Text(
                "Nothing queued. Press and hold a video to add it.",
                color = c.onSurfaceVariant, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxHeight(0.7f)) {
                itemsIndexed(player.queue, key = { i, v -> "$i-${v.id}" }) { index, video ->
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        VideoRow(video, onClick = { player.playFromQueue(index); onDismiss() }) {
                            Box(Modifier.size(40.dp).pressable { player.removeFromQueue(index) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Close, "Remove from queue", tint = c.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ── SponsorBlock settings (inside the account sheet) ───────────────────────────

@Composable
fun SponsorBlockSettings() {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SponsorBlock", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text("Skip sponsor reads and other segments", color = c.onSurfaceVariant, fontSize = 13.sp)
            }
            Switch(
                checked = SponsorBlock.enabled,
                onCheckedChange = SponsorBlock::updateEnabled,
                colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
            )
        }
        if (SponsorBlock.enabled) {
            Spacer(Modifier.height(10.dp))
            SponsorBlock.Category.entries.forEach { category -> CategoryRow(category) }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Segments from SponsorBlock (sponsor.ajay.app), a community project. Data licensed CC BY-NC-SA 4.0.",
            color = c.onSurfaceVariant, fontSize = 11.5.sp, lineHeight = 16.sp,
        )
    }
}

@Composable
private fun CategoryRow(category: SponsorBlock.Category) {
    val c = MaterialTheme.colorScheme
    val current = SponsorBlock.actionFor(category)
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(category.color))
            Spacer(Modifier.width(8.dp))
            Text(category.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SponsorBlock.Action.entries.forEach { action ->
                val on = action == current
                Box(
                    Modifier
                        .pressable { SponsorBlock.setAction(category, action) }
                        .clip(RoundedCornerShape(if (on) 18.dp else 10.dp))
                        .background(if (on) c.secondaryContainer else c.surfaceContainerHighest)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(action.label, color = if (on) c.onSecondaryContainer else c.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

// ── Appearance (inside the account sheet) ──────────────────────────────────────

/** Theme-mode selector: System / Light / Dark. No palette picker. */
@Composable
fun AppearanceSettings() {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Text("Appearance", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                val on = ThemeSettings.mode == key
                Box(
                    Modifier
                        .weight(1f)
                        .pressable { ThemeSettings.chooseMode(key) }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) c.secondaryContainer else c.surfaceContainerHighest)
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (on) c.onSecondaryContainer else c.onSurfaceVariant,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}



fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1000) {
        "%.1f GB".format(mb / 1024)
    } else if (mb >= 1) {
        "%.1f MB".format(mb)
    } else {
        "%d KB".format((bytes / 1024).toInt())
    }
}

/** Options sheet for a downloaded video in the Downloads tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedVideoOptionsSheet(
    video: VideoItem,
    player: PlayerState,
    onOpen: (VideoItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(video.thumbnail, Modifier.width(100.dp).aspectRatio(16f / 9f), radius = 12)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(video.channel, color = c.onSurfaceVariant, fontSize = 12.5.sp, maxLines = 1)
            }
        }
        OptionRow(Icons.Rounded.PlayArrow, "Play offline") {
            onOpen(video)
            onDismiss()
        }
        OptionRow(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next") {
            player.playNext(video)
            onDismiss()
        }
        OptionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to queue") {
            player.addToQueue(video)
            onDismiss()
        }
        OptionRow(Icons.Rounded.Share, "Share link") {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${video.id}")
                .putExtra(Intent.EXTRA_SUBJECT, video.title)
            context.startActivity(Intent.createChooser(send, null))
            onDismiss()
        }
        OptionRow(Icons.Rounded.Delete, "Delete download") {
            val task = Downloads.tasks.find { it.videoId == video.id }
            if (task != null) Downloads.remove(task)
            onDismiss()
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Sheet asking which playlist to save a video to, with a + New playlist option. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToPlaylistSheet(
    video: VideoItem,
    player: PlayerState,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var showCreateDialog by remember { mutableStateOf(false) }
    var userPlaylists by remember { mutableStateOf<List<PlaylistRef>>(emptyList()) }
    var loadingPlaylists by remember { mutableStateOf(true) }
    val savedInPlaylists = remember { mutableStateMapOf<String, Boolean>() }

    val defaultWatchLater = remember { PlaylistRef("WL", "Watch later", null, null) }

    LaunchedEffect(video.id) {
        savedInPlaylists["WL"] = player.inWatchLater
        runCatching {
            InnerTube.playlists()
        }.onSuccess { fetched ->
            userPlaylists = fetched
            loadingPlaylists = false
        }.onFailure {
            loadingPlaylists = false
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title ->
                scope.launch {
                    val newId = InnerTube.createPlaylist(title, video.id)
                    if (newId != null) {
                        player.actionMessage = "Created playlist and saved video"
                        onDismiss()
                    } else {
                        player.actionMessage = "Couldn't create playlist"
                    }
                }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumbnail(video.thumbnail, modifier = Modifier.size(width = 72.dp, height = 40.dp), radius = 8)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Save video to...", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.onSurfaceVariant, fontSize = 12.5.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Create new playlist button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable { showCreateDialog = true }
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.primaryContainer)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = c.onPrimaryContainer, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("New playlist", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.onPrimaryContainer)
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
            Spacer(Modifier.height(8.dp))

            if (loadingPlaylists) {
                Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                }
            } else {
                val allPls = listOf(defaultWatchLater) + userPlaylists.filter { it.id != "WL" }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    allPls.forEach { pl ->
                        val isSaved = savedInPlaylists[pl.id] == true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressable {
                                    val newSaved = !isSaved
                                    savedInPlaylists[pl.id] = newSaved
                                    if (pl.id == "WL") {
                                        player.toggleWatchLater()
                                    } else {
                                        scope.launch {
                                            runCatching {
                                                InnerTube.editPlaylist(pl.id, video.id, add = newSaved)
                                            }.onSuccess {
                                                player.actionMessage = if (newSaved) "Saved to ${pl.title}" else "Removed from ${pl.title}"
                                            }
                                        }
                                    }
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (pl.id == "WL") Icons.Rounded.WatchLater else Icons.AutoMirrored.Rounded.PlaylistPlay,
                                contentDescription = null,
                                tint = c.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(pl.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.onSurface, modifier = Modifier.weight(1f))
                            Checkbox(
                                checked = isSaved,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = c.primary),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist", fontFamily = RoundedHeavy, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column {
                Text("Enter a title for your playlist:", color = c.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(c.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .pressable(enabled = title.isNotBlank()) {
                        if (title.isNotBlank()) onCreate(title.trim())
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (title.isNotBlank()) c.primary else c.surfaceContainerHighest)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text("Create", color = if (title.isNotBlank()) c.onPrimary else c.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            }
        },
        dismissButton = {
            Text(
                "Cancel",
                modifier = Modifier
                    .pressable(onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                color = c.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp,
            )
        },
        containerColor = c.surfaceContainerLow,
    )
}

/** Subtitles / Closed Captions selection bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitlesSheet(
    player: PlayerState,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val videoId = player.current?.id
    val info = remember(videoId) { videoId?.let { StreamCache.get(it) } }
    val subtitleTracks = remember(info) { info?.subtitles.orEmpty() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("Captions / Subtitles", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Spacer(Modifier.height(14.dp))

            // Option 1: Off (Turn off captions)
            val isOff = player.selectedSubtitleLanguage == null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable {
                        player.setSubtitleLanguage(null)
                        onDismiss()
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isOff) c.surfaceContainerHigh else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.ClosedCaptionDisabled, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Off", fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = c.onSurface, modifier = Modifier.weight(1f))
                if (isOff) {
                    Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = c.primary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
            Spacer(Modifier.height(4.dp))

            if (subtitleTracks.isEmpty()) {
                Text(
                    "No captions available for this video.",
                    color = c.onSurfaceVariant,
                    fontSize = 13.5.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    subtitleTracks.forEach { sub ->
                        val code = sub.languageTag ?: "en"
                        val name = sub.displayLanguageName ?: code
                        val label = if (sub.isAutoGenerated) "$name (auto-generated)" else name
                        val selected = player.selectedSubtitleLanguage == code

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressable {
                                    player.setSubtitleLanguage(code)
                                    onDismiss()
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) c.surfaceContainerHigh else Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.ClosedCaption,
                                contentDescription = null,
                                tint = if (selected) c.primary else c.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.onSurface, modifier = Modifier.weight(1f))
                            if (selected) {
                                Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = c.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}