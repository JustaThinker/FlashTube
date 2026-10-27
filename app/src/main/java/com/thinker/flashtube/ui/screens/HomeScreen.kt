package com.thinker.flashtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    // Always load home — works anonymously (shows popular/trending) and signed-in (personalised).
    LaunchedEffect(signedIn) {
        if (lib.home.videos.isEmpty()) lib.home.ensureLoaded()
        else lib.home.refresh()
    }
    CollapsingHeaderLayout(
        resetKey = scrollToTop to lib.home.generation,
        header = { ScreenHeader(title = "FlashTube", brandMark = true) { HeaderActions(onSearch, onAccount) } },
    ) { top ->
        FeedList(
            feed = lib.home,
            contentPadding = contentPadding.withTop(top),
            onOpen = onOpen,
            scrollToTop = scrollToTop,
            indicatorTop = top,
            header = {
                // Logged-out banner: shown above the feed, dismisses on sign-in.
                if (!signedIn) {
                    item(key = "anon_banner") {
                        AnonHomeBanner(onSignIn = onSignIn, onSearch = onSearch)
                    }
                }
            },
        )
    }
}
