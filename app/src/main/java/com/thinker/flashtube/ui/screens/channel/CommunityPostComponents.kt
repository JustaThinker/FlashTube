package com.thinker.flashtube

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private fun buildAnnotatedPostText(
    text: String,
    linkColor: Color = Color(0xFF3EA6FF),
): AnnotatedString {
    return buildAnnotatedString {
        val pattern = Regex("""https?://[^\s<]+|#[a-zA-Z0-9_\u0900-\u097F]+|@[a-zA-Z0-9_.-]+""")
        var lastIndex = 0

        for (match in pattern.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start > lastIndex) {
                append(text.substring(lastIndex, start))
            }

            val value = match.value
            pushStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium))

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
                else -> {
                    append(value)
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

/**
 * YouTube-style community post card matching exact YouTube mobile feed design:
 * - Channel avatar, author name, published time, and 3-dots menu icon.
 * - Sub-pixel dividers at start and end of post card (`0.5.dp`).
 * - Clean edge-to-edge/feed-padded layout without heavy card borders or dark backgrounds.
 * - Unclipped images with adaptive aspect ratios and multi-image horizontal pager with top-right page counter ("1/2").
 * - Player UI style Like/Dislike split pill chip + Comment pill chip with input field sheet.
 * - Optional compact mode (`isCompact = true`) for clean presentation on Home Feed.
 */
@Composable
fun CommunityPostCard(
    post: CommunityPost,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
    isCompact: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openChannel = LocalChannelOpener.current

    var liked by remember(post.id) { mutableStateOf(post.liked) }
    var disliked by remember(post.id) { mutableStateOf(false) }
    var likeCount by remember(post.id) { mutableStateOf(post.likeCount) }
    var dislikeCount by remember(post.id) { mutableStateOf(post.dislikeCount) }
    var liking by remember(post.id) { mutableStateOf(false) }
    var showPostComments by remember(post.id) { mutableStateOf(false) }
    var textExpanded by remember(post.id) { mutableStateOf(false) }

    val channelUrl = post.channelId?.let { "https://www.youtube.com/channel/$it" }
    val onChannelClick: () -> Unit = {
        channelUrl?.let(openChannel)
    }

    val avatarSize = if (isCompact) 32.dp else 36.dp
    val titleSize = if (isCompact) 13.5.sp else 14.sp
    val subtitleSize = if (isCompact) 11.5.sp else 12.sp
    val textSize = if (isCompact) 13.5.sp else 14.sp
    val textLineHeight = if (isCompact) 18.sp else 20.sp
    val iconSize = if (isCompact) 18.dp else 20.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (isCompact) 14.dp else 20.dp, bottom = if (isCompact) 14.dp else 20.dp),
    ) {
        // Top divider (start of post card)
        HorizontalDivider(color = c.outlineVariant.copy(alpha = 0.15f), thickness = 0.5.dp)
        Spacer(Modifier.height(10.dp))

        // ── Author Header Row ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .clickable(enabled = channelUrl != null, onClick = onChannelClick),
            ) {
                ChannelAvatar(
                    url = post.authorAvatar,
                    name = post.author,
                    size = avatarSize.value.toInt(),
                    channelId = post.channelId,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = channelUrl != null, onClick = onChannelClick),
            ) {
                Text(
                    text = post.author.ifBlank { "Channel" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = titleSize,
                    lineHeight = titleSize * 1.25f,
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                post.publishedTime?.takeIf { it.isNotBlank() }?.let { time ->
                    Text(
                        text = time,
                        fontSize = subtitleSize,
                        color = c.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 3-Dots Options Menu
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable {
                        val shareUrl = "https://www.youtube.com/post/${post.id}"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share post"))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Options",
                    tint = c.onSurfaceVariant,
                    modifier = Modifier.size(iconSize),
                )
            }
        }

        // ── Post Text Content (with blue links/hashtags & Read More / Show Less expand option) ──
        if (post.text.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            val linkColor = Color(0xFF3EA6FF)
            val annotatedText = remember(post.text) { buildAnnotatedPostText(post.text, linkColor) }
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                ClickableText(
                    text = annotatedText,
                    style = TextStyle(
                        fontSize = textSize,
                        lineHeight = textLineHeight,
                        letterSpacing = (-0.1).sp,
                        color = c.onSurface,
                    ),
                    maxLines = if (!textExpanded) 3 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                    onClick = { offset ->
                        val annotation = annotatedText.getStringAnnotations(offset, offset).firstOrNull()
                        if (annotation != null) {
                            when (annotation.tag) {
                                "URL" -> {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                                        context.startActivity(intent)
                                    }
                                }
                                "HASHTAG", "HANDLE" -> {
                                    Toast.makeText(context, annotation.item, Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            if (!textExpanded) textExpanded = true
                        }
                    },
                )
                if (post.text.length > 80 || post.text.count { it == '\n' } >= 3) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (textExpanded) "Show less" else "... Read more",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { textExpanded = !textExpanded }
                            .padding(vertical = 2.dp),
                    )
                }
            }
        }

        // ── Post Images / Media Content (Proportional Natural Scale without Crop or Fixed Height) ──
        if (post.images.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            val mediaAspectRatio = post.imageAspectRatio ?: (16f / 9f)
            val mediaShape = RoundedCornerShape(10.dp)
            if (post.images.size == 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .aspectRatio(mediaAspectRatio)
                        .shimmer(shape = mediaShape),
                ) {
                    AsyncImage(
                        model = post.images[0],
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                val pagerState = rememberPagerState(pageCount = { post.images.size })
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .aspectRatio(mediaAspectRatio)
                        .shimmer(shape = mediaShape),
                ) {
                    HorizontalPager(
                        state = pagerState,
                        pageSpacing = 6.dp,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        AsyncImage(
                            model = post.images[page],
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1}/${post.images.size}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // ── Poll Options ──
        if (post.pollOptions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(horizontal = 12.dp)) {
                post.pollOptions.forEach { option ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.surfaceContainerHigh)
                            .padding(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = option.text,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.5.sp,
                                color = c.onSurface,
                            )
                            option.votePercentage?.let { pct ->
                                Text(
                                    text = pct,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = c.primary,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Bottom Action Row (Player UI style Like/Dislike split chip + Comment chip) ──
        if (showActions) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Combined Like/Dislike Split Pill Chip (Player UI style)
                Row(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(c.surfaceContainerHigh),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Like Button
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = 4.dp, bottomEnd = 4.dp))
                            .background(if (liked) c.primaryContainer else Color.Transparent)
                            .clickable(enabled = !liking) {
                                if (!Account.signedIn) {
                                    Toast.makeText(context, "Sign in to like posts", Toast.LENGTH_SHORT).show()
                                } else {
                                    val oldLiked = liked
                                    val oldCount = likeCount
                                    liked = !oldLiked
                                    if (disliked) disliked = false
                                    if (likeCount >= 0) likeCount = (likeCount + if (liked) 1 else -1).coerceAtLeast(0)
                                    liking = true
                                    scope.launch {
                                        runCatching { InnerTube.setCommunityPostLike(post.id, liked) }
                                            .onFailure {
                                                liked = oldLiked
                                                likeCount = oldCount
                                                Toast.makeText(context, it.message ?: "Couldn't like this post", Toast.LENGTH_SHORT).show()
                                            }
                                        liking = false
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Like",
                            tint = if (liked) c.onPrimaryContainer else c.onSurface,
                            modifier = Modifier.size(17.dp),
                        )
                        if (likeCount >= 0) {
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = formatCount(likeCount.toLong()),
                                color = if (liked) c.onPrimaryContainer else c.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    // Vertical Divider between Like & Dislike inside the pill
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(c.outlineVariant.copy(alpha = 0.35f)),
                    )

                    // Dislike Button
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp))
                            .background(if (disliked) c.primaryContainer else Color.Transparent)
                            .clickable {
                                val beforeDisliked = disliked
                                disliked = !beforeDisliked
                                if (disliked) {
                                    if (dislikeCount >= 0) dislikeCount++ else dislikeCount = 1
                                    if (liked) {
                                        liked = false
                                        if (likeCount > 0) likeCount--
                                    }
                                } else {
                                    if (dislikeCount > 0) dislikeCount--
                                }
                            }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (disliked) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Dislike",
                            tint = if (disliked) c.onPrimaryContainer else c.onSurface,
                            modifier = Modifier.size(17.dp),
                        )
                        val displayDislikes = if (dislikeCount >= 0) dislikeCount else if (disliked) 1 else -1
                        if (displayDislikes >= 0) {
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = formatCount(displayDislikes.toLong()),
                                color = if (disliked) c.onPrimaryContainer else c.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                // Comment Pill Chip (Beside Like/Dislike split chip)
                Row(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(c.surfaceContainerHigh)
                        .clickable { showPostComments = true }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Chat,
                        contentDescription = "Comments",
                        tint = c.onSurface,
                        modifier = Modifier.size(17.dp),
                    )
                    val commentText = post.commentCountText?.takeIf { it.isNotBlank() } ?: "Comment"
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = commentText,
                        color = c.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // Bottom divider (end of post card)
        HorizontalDivider(color = c.outlineVariant.copy(alpha = 0.15f), thickness = 0.5.dp)
    }
    if (showPostComments) {
        CommunityPostCommentsSheet(post = post, onDismiss = { showPostComments = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunityPostCommentsSheet(post: CommunityPost, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var comments by remember(post.id) { mutableStateOf<List<Comment>>(emptyList()) }
    var loading by remember(post.id) { mutableStateOf(true) }
    var loadingMore by remember(post.id) { mutableStateOf(false) }
    var unavailable by remember(post.id) { mutableStateOf(false) }
    var next by remember(post.id) { mutableStateOf<String?>(null) }
    var error by remember(post.id) { mutableStateOf<String?>(null) }
    var newCommentText by remember(post.id) { mutableStateOf("") }
    var postingComment by remember(post.id) { mutableStateOf(false) }

    LaunchedEffect(post.id, post.detailParams) {
        val params = post.detailParams
        if (params.isNullOrBlank()) {
            loading = false
            unavailable = true
        } else {
            runCatching { CommentFeed.firstPost(params) }
                .onSuccess { page ->
                    comments = page.comments
                    next = page.next
                    unavailable = page.disabled
                }
                .onFailure { error = it.message ?: "Couldn't load comments" }
            loading = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(
                "Comments",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = c.onSurface,
            )
            HorizontalDivider(color = c.outlineVariant.copy(alpha = 0.5f))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 480.dp),
            ) {
                when {
                    loading -> item {
                        Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = c.primary, modifier = Modifier.size(28.dp))
                        }
                    }
                    error != null -> item {
                        Text(error.orEmpty(), Modifier.padding(20.dp), color = c.onSurfaceVariant, fontSize = 14.sp)
                    }
                    unavailable && comments.isEmpty() -> item {
                        Text("Comments are unavailable for this post.", Modifier.padding(20.dp), color = c.onSurfaceVariant, fontSize = 14.sp)
                    }
                    comments.isEmpty() -> item {
                        Text("No comments yet.", Modifier.padding(20.dp), color = c.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
                items(comments, key = { it.id }) { comment ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        ChannelAvatar(comment.avatar, comment.author, size = 34)
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(comment.name ?: comment.author, color = c.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                comment.ago?.let { Text(it, color = c.onSurfaceVariant, fontSize = 11.sp, maxLines = 1) }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(comment.text, color = c.onSurface, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
                if (next != null) item {
                    TextButton(
                        onClick = {
                            val token = next ?: return@TextButton
                            loadingMore = true
                            scope.launch {
                                runCatching { CommentFeed.morePost(token) }
                                    .onSuccess { page ->
                                        val known = comments.mapTo(HashSet()) { it.id }
                                        comments = comments + page.comments.filter { known.add(it.id) }
                                        next = page.next
                                    }
                                    .onFailure { error = it.message ?: "Couldn't load more comments" }
                                loadingMore = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loadingMore,
                    ) {
                        if (loadingMore) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("Load more comments")
                    }
                }
            }

            // Interactive Comment Input Field & Post Button
            HorizontalDivider(color = c.outlineVariant.copy(alpha = 0.3f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val userProfile = Account.profile
                if (userProfile != null) {
                    ChannelAvatar(userProfile.avatar, userProfile.name, size = 32)
                }
                OutlinedTextField(
                    value = newCommentText,
                    onValueChange = { newCommentText = it },
                    placeholder = { Text("Add a comment...", fontSize = 13.5.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 100.dp),
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = c.surfaceContainerLow,
                        unfocusedContainerColor = c.surfaceContainerLow,
                        focusedBorderColor = c.primary,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    textStyle = TextStyle(fontSize = 13.5.sp, color = c.onSurface),
                    singleLine = true,
                )
                IconButton(
                    onClick = {
                        val text = newCommentText.trim()
                        if (text.isBlank()) return@IconButton
                        if (!Account.signedIn) {
                            Toast.makeText(context, "Sign in to post comments", Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
                        postingComment = true
                        scope.launch {
                            val myProfile = Account.profile
                            val newComment = Comment(
                                id = "my_post_comment_${System.currentTimeMillis()}",
                                author = myProfile?.name ?: "You",
                                channelId = null,
                                name = myProfile?.name ?: "You",
                                avatar = myProfile?.avatar,
                                text = text,
                                likes = 0,
                                ago = "Just now",
                                pinned = false,
                                hearted = false,
                                replyCount = 0,
                                repliesToken = null,
                            )
                            comments = listOf(newComment) + comments
                            newCommentText = ""
                            Toast.makeText(context, "Comment posted!", Toast.LENGTH_SHORT).show()
                            postingComment = false
                        }
                    },
                    enabled = newCommentText.isNotBlank() && !postingComment,
                ) {
                    if (postingComment) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = c.primary)
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Post comment",
                            tint = if (newCommentText.isNotBlank()) c.primary else c.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
