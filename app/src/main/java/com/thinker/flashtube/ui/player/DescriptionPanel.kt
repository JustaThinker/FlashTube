package com.thinker.flashtube

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private fun extractHashtags(title: String, desc: String): List<String> {
    val regex = Regex("""#[a-zA-Z0-9_\u0900-\u097F]+""")
    val set = mutableSetOf<String>()
    regex.findAll(title).forEach { set.add(it.value) }
    regex.findAll(desc).forEach { set.add(it.value) }
    return set.toList()
}

private fun buildAnnotatedDescription(
    text: String,
    primaryColor: Color,
): AnnotatedString {
    return buildAnnotatedString {
        val pattern = Regex("""\b(?:(\d{1,2}):)?(\d{1,2}):(\d{2})\b|https?://[^\s<]+|#[a-zA-Z0-9_\u0900-\u097F]+|@[a-zA-Z0-9_.-]+""")
        var lastIndex = 0

        for (match in pattern.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start > lastIndex) {
                append(text.substring(lastIndex, start))
            }

            val value = match.value
            pushStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold))

            when {
                value.startsWith("http://") || value.startsWith("https://") -> {
                    pushStringAnnotation(tag = "URL", annotation = value)
                    append(value)
                    pop()
                }
                value.startsWith("#") -> {
                    pushStringAnnotation(tag = "HASHTAG", annotation = value)
                    append(value)
                    pop()
                }
                value.startsWith("@") -> {
                    pushStringAnnotation(tag = "HANDLE", annotation = value)
                    append(value)
                    pop()
                }
                else -> { // Timestamp
                    val parts = value.split(":").mapNotNull { it.toLongOrNull() }
                    val sec = when (parts.size) {
                        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                        2 -> parts[0] * 60 + parts[1]
                        else -> 0L
                    }
                    pushStringAnnotation(tag = "TIMESTAMP", annotation = sec.toString())
                    append(value)
                    pop()
                }
            }
            pop()
            lastIndex = end
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

/** Description opens as a draggable panel whose top sits just under the video player. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DescriptionPanel(
    state: PlayerState,
    partialTop: Dp,
    onClose: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val currentItem = state.current ?: return
    val details = state.details
    val item = details?.item ?: currentItem
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    val openChannel = LocalChannelOpener.current
    val openCollabSheet = LocalCollaboratorsOpener.current

    var expandedDesc by remember { mutableStateOf(false) }
    var showChannelAbout by remember { mutableStateOf(false) }

    val desc = details?.description.orEmpty()
    val hashtags = remember(item.title, desc) { extractHashtags(item.title, desc) }
    val accentBlue = Color(0xFF3EA6FF)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val fullPx = with(density) { (this@BoxWithConstraints.maxHeight - statusTop - 8.dp).toPx() }
        val partialPx = with(density) { (this@BoxWithConstraints.maxHeight - partialTop).toPx() }.coerceIn(fullPx * 0.35f, fullPx)
        val height = remember { Animatable(partialPx) }
        val slide = remember { Animatable(1f) }

        LaunchedEffect(Unit) {
            if (reduced) slide.snapTo(0f) else slide.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 550f))
        }

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
                h < partialPx * 0.7f || (velocity > 1200f && h < partialPx - 1f) || (velocity > 1800f && h <= partialPx + 1f) -> close()
                velocity < -1200f -> scope.launch { height.animateTo(fullPx, spring(dampingRatio = 0.86f, stiffness = 550f)) }
                velocity > 1200f -> scope.launch { height.animateTo(partialPx, spring(dampingRatio = 0.86f, stiffness = 550f)) }
                else -> {
                    val target = if (h > (partialPx + fullPx) / 2f) fullPx else partialPx
                    scope.launch { height.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 550f)) }
                }
            }
        }

        val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(with(density) { height.value.toDp() })
                .graphicsLayer { translationY = slide.value * size.height }
                .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
                .clip(shape)
                .background(c.surface)
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                .imePadding(),
        ) {
            // Drag handle and title header
            Column(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = rememberDraggableState { dragBy(it) },
                        orientation = Orientation.Vertical,
                        onDragStopped = { v -> settle(v) },
                    ),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(c.onSurfaceVariant.copy(alpha = 0.45f)))
                }
                Row(
                    Modifier.padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Description", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = c.onSurface)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .pressable { close() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, "Close description", tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
            ) {
                // Video Title
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = c.onSurface,
                )

                Spacer(Modifier.height(14.dp))

                // Stats Cards Row: Likes | Views | Date (Compact 3-card design)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Likes Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.surfaceContainerHigh)
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = details?.likes?.takeIf { it > 0 }?.let { formatCount(it) } ?: "--",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = c.onSurface,
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = "Likes",
                                fontSize = 11.sp,
                                color = c.onSurfaceVariant,
                            )
                        }
                    }

                    // Views Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.surfaceContainerHigh)
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (item.views >= 0) formatCount(item.views) else "--",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = c.onSurface,
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = "Views",
                                fontSize = 11.sp,
                                color = c.onSurfaceVariant,
                            )
                        }
                    }

                    // Upload Date Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.surfaceContainerHigh)
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = item.uploaded ?: "Recent",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = c.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = "Upload date",
                                fontSize = 11.sp,
                                color = c.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Hashtags Chips Row
                if (hashtags.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        hashtags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(c.surfaceContainerHigh)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = accentBlue,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Description Box (Highlight Card with interactive text & expand button)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surfaceContainerHigh.copy(alpha = 0.5f))
                        .padding(14.dp),
                ) {
                    Column {
                        if (details == null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Loading description...", fontSize = 13.sp, color = c.onSurfaceVariant)
                            }
                        } else if (desc.isBlank()) {
                            Text("No description provided.", fontSize = 14.sp, color = c.onSurfaceVariant)
                        } else {
                            val annotatedDesc = remember(desc, accentBlue) { buildAnnotatedDescription(desc, accentBlue) }
                            val maxLines = if (expandedDesc) Int.MAX_VALUE else 4

                            ClickableText(
                                text = annotatedDesc,
                                style = TextStyle(color = c.onSurface, fontSize = 14.sp, lineHeight = 20.sp),
                                maxLines = maxLines,
                                overflow = TextOverflow.Ellipsis,
                                onClick = { offset ->
                                    annotatedDesc.getStringAnnotations(offset, offset).firstOrNull()?.let { ann ->
                                        when (ann.tag) {
                                            "TIMESTAMP" -> ann.item.toLongOrNull()?.let { sec -> state.seekTo(sec * 1000L) }
                                            "URL" -> try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ann.item)))
                                            } catch (_: Exception) {}
                                        }
                                    }
                                },
                            )

                            if (desc.lines().size > 4 || desc.length > 180) {
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { expandedDesc = !expandedDesc },
                                    modifier = Modifier.fillMaxWidth().height(36.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, c.outline.copy(alpha = 0.35f)),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(
                                        text = if (expandedDesc) "Show less" else "See more",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = c.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(14.dp))

                // Channel Info Row
                val collabChannels = details?.item?.collabChannels?.takeIf { it.size >= 2 }
                    ?: item.collabChannels.takeIf { it.size >= 2 }
                    ?: emptyList()
                val displayChannelName = if (collabChannels.size >= 2) {
                    formatCollabNames(collabChannels)
                } else {
                    item.channel
                }
                val displayChannelUrl = if (collabChannels.size >= 2) {
                    collabChannels.firstOrNull()?.url ?: item.channelUrl
                } else {
                    item.channelUrl
                }

                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .pressable(enabled = displayChannelUrl != null || collabChannels.size >= 2) {
                                if (collabChannels.size >= 2) {
                                    close()
                                    openCollabSheet(collabChannels)
                                } else {
                                    displayChannelUrl?.let { url ->
                                        close()
                                        openChannel(url)
                                    }
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChannelAvatar(item.avatar, displayChannelName, size = 40, collabChannels = collabChannels)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = displayChannelName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = c.onSurface,
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = c.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            if (details != null && details.subscribers > 0) {
                                Text(
                                    text = "${formatCount(details.subscribers)} subscribers",
                                    fontSize = 12.sp,
                                    color = c.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Channel Quick Action Buttons: [ Videos ] [ About ]
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(c.surfaceContainerHigh)
                                .pressable(enabled = item.channelUrl != null) {
                                    item.channelUrl?.let { url ->
                                        close()
                                        openChannel(url)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(R.drawable.ic_player_play), contentDescription = null, tint = c.onSurface, modifier = Modifier.size(21.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Videos", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(c.surfaceContainerHigh)
                                .pressable(enabled = item.channelUrl != null) {
                                    showChannelAbout = true
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Info, contentDescription = null, tint = c.onSurface, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("About", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.onSurface)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(14.dp))

                // Video Details Section
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = "Video details",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = c.onSurface,
                    )

                    Spacer(Modifier.height(12.dp))

                    // Detail Row 1: Date
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Date", fontSize = 14.sp, color = c.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(item.uploaded ?: "Recent", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.onSurface)
                    }

                    // Detail Row 2: Views
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Visibility, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Views", fontSize = 14.sp, color = c.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (item.views >= 0) String.format(java.util.Locale.US, "%,d", item.views) else "--",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.onSurface,
                        )
                    }

                    // Detail Row 3: Likes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.ThumbUp, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Likes", fontSize = 14.sp, color = c.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = details?.likes?.takeIf { it > 0 }?.let { String.format(java.util.Locale.US, "%,d", it) } ?: "--",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.onSurface,
                        )
                    }
                }

                if (showChannelAbout) {
                    BackHandler { showChannelAbout = false }
                    ChannelAboutScreen(
                        channelDetails = null,
                        channelName = item.channel,
                        channelAvatar = item.avatar,
                        channelUrl = item.channelUrl,
                        subscribersCount = details?.subscribers ?: 0L,
                        onBack = { showChannelAbout = false },
                    )
                }
            }
        }
    }
}
