package com.thinker.flashtube

import androidx.compose.animation.Crossfade
import com.thinker.flashtube.data.ContentRegion
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Slim sign-in nudge shown above the anonymous home feed. */
@Composable
private fun AnonHomeBanner(onSignIn: () -> Unit, onSearch: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surfaceContainerLow)
            .clickable(onClick = onSignIn)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(32.dp))
        Column(Modifier.weight(1f)) {
            Text("Sign in for recommendations", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text("Showing popular videos · Tap to personalise", color = c.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

/** YouTube-style horizontal topic category chips bar matching official 32dp height spec. */
@Composable
fun CategoryChipsBar(
    categories: List<FeedChip>,
    selectedKey: String?,
    onCategorySelected: (FeedChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it.key }) { cat ->
            val isSelected = cat.key == selectedKey || (selectedKey == null && cat.title.equals("All", true))
            val isTrending = cat.title.equals("Trending", ignoreCase = true) || cat.title.equals("Hot", ignoreCase = true)
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) c.onSurface else c.surfaceContainerHigh,
                animationSpec = if (reduced) tween(0) else spring(dampingRatio = 0.75f, stiffness = 600f),
                label = "chipBg",
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) c.surface else c.onSurface,
                animationSpec = if (reduced) tween(0) else spring(dampingRatio = 0.75f, stiffness = 600f),
                label = "chipText",
            )
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .pressable(pressedScale = 0.95f, onClick = { onCategorySelected(cat) })
                    .padding(horizontal = if (isTrending) 10.dp else 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isTrending) {
                    Icon(
                        imageVector = Icons.Rounded.Whatshot,
                        contentDescription = "Trending",
                        tint = textColor,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(
                        text = cat.title,
                        color = textColor,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    lib: LibraryData,
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onSearch: () -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
) {
    val signedIn = Account.signedIn
    // FeedState remembers this identity across tab disposal: refresh once when account/region
    // changes, but don't refetch just because the user returns to Home.
    LaunchedEffect(Account.feedIdentity, ContentRegion.regionGeneration) {
        val feedIdentity = "${Account.feedIdentity}|${ContentRegion.currentRegionCode}"
        lib.ensureHomeLoaded(feedIdentity)
        lib.home.ensureChipsLoaded(feedIdentity)
    }
    LaunchedEffect(lib.home.generation, lib.home.videos.size, lib.home.selectedChip?.key, lib.homePosts.size) {
        if (lib.home.selectedChip == null && lib.home.videos.isNotEmpty()) {
            lib.ensureHomePosts(lib.home.videos.count { it.feedPost == null })
        }
    }

    CollapsingHeaderLayout(
        resetKey = scrollToTop to (lib.home.generation to lib.home.selectedChip?.key),
        header = { ScreenHeader(title = "FlashTube", brandMark = true) { HeaderActions(onSearch, onAccount) } },
    ) { top ->
        FeedList(
            feed = lib.home,
            contentPadding = contentPadding.withTop(top),
            onOpen = onOpen,
            videos = lib.homeRows(),
            scrollToTop = scrollToTop,
            indicatorTop = top,
            onRefresh = {
                lib.refreshHomePosts()
                lib.home.refresh()
            },
            filterScrollKey = lib.home.selectedChip?.key,
            header = {
                if (!signedIn) {
                    item(key = "anon_banner") { AnonHomeBanner(onSignIn = onSignIn, onSearch = onSearch) }
                }
                if (lib.home.chips.isNotEmpty()) {
                    item(key = "server_category_chips") {
                        CategoryChipsBar(
                            categories = lib.home.chips,
                            selectedKey = lib.home.selectedChip?.key,
                            onCategorySelected = { chip ->
                                lib.home.selectChip(if (chip.title.equals("All", true)) null else chip)
                            },
                        )
                    }
                } else if (lib.home.loading || lib.home.chipsLoading || lib.home.chips.isEmpty()) {
                    item(key = "loading_category_chips") {
                        CategoryChipSkeleton()
                    }
                }
            },
        )
    }
}

@Composable
private fun CategoryChipSkeleton(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "chip-skeleton")
    val sweep by transition.animateFloat(
        initialValue = -0.7f,
        targetValue = 1.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "chip-skeleton-sweep",
    )
    val shimmer = Brush.linearGradient(
        colors = listOf(c.surfaceContainerLow, c.surfaceContainerHighest, c.surfaceContainerLow),
        start = Offset(sweep * 900f, 0f),
        end = Offset((sweep + 0.8f) * 900f, 80f),
    )
    LazyRow(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .background(c.surface)
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        val skeletonWidths = listOf(40.dp, 52.dp, 72.dp, 84.dp, 64.dp, 90.dp, 78.dp, 68.dp)
        items(skeletonWidths.size) { index ->
            Box(
                Modifier
                    .width(skeletonWidths[index])
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(shimmer),
            )
        }
    }
}
