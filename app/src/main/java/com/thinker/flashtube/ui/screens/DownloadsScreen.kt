package com.thinker.flashtube

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQualitySheet(
    video: VideoItem,
    onDownloadStarted: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    var options by remember(video.id) { mutableStateOf<List<DownloadOption>?>(null) }
    var error by remember(video.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(video.id) {
        runCatching { Downloads.options(video.id) }
            .onSuccess { list -> options = list }
            .onFailure { error = it.message ?: "Could not load available qualities" }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumbnail(video.thumbnail, modifier = Modifier.size(width = 60.dp, height = 34.dp), radius = 6)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Download Quality", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.onSurfaceVariant, fontSize = 11.5.sp)
                }
            }
            Spacer(Modifier.height(12.dp))

            when {
                error != null -> Text(
                    error!!,
                    color = c.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                options == null -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp, color = c.primary)
                }
                options!!.isEmpty() -> Text(
                    "This video has no downloadable qualities.",
                    color = c.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        options!!.forEach { option ->
                            val isAudio = option.height == 0
                            val sizeStr = if (option.estimatedBytes > 0) formatBytes(option.estimatedBytes) else ""

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressable {
                                        val added = if (isAudio) Downloads.enqueueAudio(video) else Downloads.enqueue(video, option.height)
                                        onDownloadStarted?.invoke(if (added) "Downloading ${option.label} • ${video.title}" else "Already in downloads")
                                        onDismiss()
                                    }
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.surfaceContainerHigh)
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (isAudio) Icons.Rounded.Headphones else Icons.Rounded.HighQuality,
                                    contentDescription = null,
                                    tint = c.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (isAudio) "Audio Only" else option.label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = c.onSurface,
                                    )
                                    if (sizeStr.isNotBlank()) {
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(c.primaryContainer)
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = sizeStr,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = c.onPrimaryContainer,
                                            )
                                        }
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = "Download",
                                    tint = c.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen(
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onOpenMusic: (VideoItem) -> Unit = {},
    onBrowse: () -> Unit = {},
    onBrowseMusic: () -> Unit = {},
    onSearch: () -> Unit = {},
    onAccount: () -> Unit = {},
    scrollToTop: Int = 0,
) {
    val tasks = Downloads.tasks

    var section by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    val videos = tasks.filter { it.height > 0 }
    val music = tasks.filter { it.height == 0 }
    val shown = if (section == 0) videos else music
    CollapsingHeaderLayout(
        resetKey = scrollToTop to listOf(tasks.size, tasks.count { it.status == DownloadStatus.Complete }),
        header = { ScreenHeader(title = "Downloads") { HeaderActions(onSearch, onAccount) } },
    ) { top ->
        LazyColumn(contentPadding = contentPadding.withTop(top)) {
            item(key = "sections") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DownloadSectionPill("Videos", videos.size, section == 0) { section = 0 }
                    DownloadSectionPill("Music", music.size, section == 1) { section = 1 }
                }
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(top = 28.dp)) {
                        EmptyState(
                            icon = if (section == 0) Icons.Rounded.Download else Icons.Rounded.MusicNote,
                            title = if (section == 0) "No videos downloaded yet" else "No music downloaded yet",
                            body = if (section == 0) {
                                "Videos you download are saved here and play without a connection."
                            } else {
                                "Songs you download from Music are saved here for offline listening."
                            },
                            action = {
                                PillButton(
                                    if (section == 0) "Browse videos" else "Browse music",
                                    if (section == 0) onBrowse else onBrowseMusic,
                                )
                            },
                        )
                    }
                }
            } else {
                val active = shown.filter { it.status != DownloadStatus.Complete }
                val finished = shown.filter { it.status == DownloadStatus.Complete }
                if (active.isNotEmpty()) {
                    item(key = "activeTitle") { SectionTitle("Downloading") }
                    items(active, key = { "a-" + it.key }) { task ->
                        DownloadRow(task, onOpen = null)
                    }
                }
                if (finished.isNotEmpty()) {
                    item(key = "doneTitle") { SectionTitle(if (section == 0) "Videos" else "Music") }
                    items(finished, key = { "d-" + it.key }) { task ->
                        DownloadRow(
                            task,
                            onOpen = {
                                if (section == 0) onOpen(task.toVideoItem())
                                else onOpenMusic(task.toVideoItem())
                            },
                        )
                    }
                    item(key = "footer") { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DownloadSectionPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (selected) c.primary else c.surfaceContainerHigh)
            .pressable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (selected) c.onPrimary else c.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                count.toString(),
                color = if (selected) c.onPrimary.copy(alpha = 0.8f) else c.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DownloadRow(task: DownloadTask, onOpen: (() -> Unit)?) {
    val c = MaterialTheme.colorScheme
    var menu by remember(task.key) { mutableStateOf(false) }
    val running = task.status == DownloadStatus.Downloading || task.status == DownloadStatus.Queued

    Column(
        Modifier
            .fillMaxWidth()
            .pressable(enabled = onOpen != null, pressedScale = 0.985f) { onOpen?.invoke() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box {
                Thumbnail(
                    task.thumbnail,
                    Modifier.size(width = 132.dp, height = 74.dp),
                    radius = 12,
                )
                DurationPill(task.durationSec, Modifier.align(Alignment.BottomEnd).padding(5.dp), small = true)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (task.height > 0) "${task.channel} · ${task.height}p" else "${task.channel} · Audio",
                    color = c.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    statusText(task),
                    color = if (task.status == DownloadStatus.Failed) c.error else c.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { menu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MoreVert, "More options", tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    when (task.status) {
                        DownloadStatus.Downloading, DownloadStatus.Queued -> DropdownMenuItem(
                            text = { Text("Pause") },
                            onClick = { menu = false; Downloads.pause(task) },
                        )
                        DownloadStatus.Paused, DownloadStatus.Failed -> DropdownMenuItem(
                            text = { Text("Resume") },
                            onClick = { menu = false; Downloads.resume(task) },
                        )
                        DownloadStatus.Complete -> DropdownMenuItem(
                            text = { Text("Play offline") },
                            onClick = { menu = false; onOpen?.invoke() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete", color = c.error) },
                        onClick = { menu = false; Downloads.remove(task) },
                    )
                }
            }
        }

        if (running || task.status == DownloadStatus.Paused || task.status == DownloadStatus.Failed) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                color = if (task.status == DownloadStatus.Failed) c.error else c.primary,
                trackColor = c.onSurfaceVariant.copy(alpha = 0.18f),
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    progressText(task),
                    color = c.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    when (task.status) {
                        DownloadStatus.Downloading -> "Downloading"
                        DownloadStatus.Queued -> "Waiting"
                        DownloadStatus.Paused -> "Paused"
                        DownloadStatus.Failed -> "Failed"
                        DownloadStatus.Complete -> "Downloaded"
                    },
                    color = c.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun statusText(task: DownloadTask): String = when (task.status) {
    DownloadStatus.Complete -> "Downloaded · ${formatBytes(task.file.length())}"
    DownloadStatus.Downloading -> "Downloading ${(task.progress * 100).toInt()}%"
    DownloadStatus.Queued -> "Waiting to download"
    DownloadStatus.Paused -> "Paused · ${formatBytes(task.downloaded)} downloaded"
    DownloadStatus.Failed -> task.error ?: "Download failed"
}

private fun progressText(task: DownloadTask): String {
    val total = task.total
    return if (total > 0) {
        "${formatBytes(task.downloaded)} of ${formatBytes(total)}"
    } else {
        formatBytes(task.downloaded)
    }
}

private fun qualityTag(height: Int): String? = when {
    height >= 2160 -> "4K"
    height >= 1440 -> "2K"
    height >= 1080 -> "Full HD"
    height >= 720 -> "HD"
    height <= 240 -> "Data saver"
    else -> null
}

private fun sizeSuffix(bytes: Long): String =
    if (bytes > 0) " · ${formatBytes(bytes)}" else ""

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
        mb >= 10 -> String.format(Locale.US, "%.0f MB", mb)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }
}
