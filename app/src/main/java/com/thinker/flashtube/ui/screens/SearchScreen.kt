package com.thinker.flashtube

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.thinker.flashtube.data.SearchDuration
import com.thinker.flashtube.data.SearchFeature
import com.thinker.flashtube.data.SearchFilterState
import com.thinker.flashtube.data.SearchSortBy
import com.thinker.flashtube.data.SearchType
import com.thinker.flashtube.data.SearchUploadDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** State manager for search queries, suggestions, filters, and channel results. */
@Stable
class SearchState(private val scope: CoroutineScope) {
    var query by mutableStateOf("")
        private set
    var submitted by mutableStateOf("")
        private set
    var results by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var channelCard by mutableStateOf<ChannelDetails?>(null)
        private set
    var filterState by mutableStateOf(SearchFilterState())
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var suggestionsLoading by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var generation by mutableStateOf(0)
        private set
    private var next: String? = null
    private var job: Job? = null
    private var suggestJob: Job? = null

    val hasMore get() = next != null

    fun reset() {
        job?.cancel()
        suggestJob?.cancel()
        query = ""
        submitted = ""
        results = emptyList()
        channelCard = null
        suggestions = emptyList()
        suggestionsLoading = false
        loading = false
        loadingMore = false
        error = null
        next = null
        filterState = SearchFilterState()
    }

    fun updateQuery(text: String) {
        query = text
        suggestJob?.cancel()
        if (text.isBlank()) {
            suggestions = emptyList()
            suggestionsLoading = false
            if (submitted.isNotEmpty() || results.isNotEmpty() || error != null || loading) {
                submitted = ""
                results = emptyList()
                channelCard = null
                error = null
                loading = false
                loadingMore = false
                next = null
                job?.cancel()
            }
            return
        }
        suggestJob = scope.launch {
            delay(200)
            suggestionsLoading = true
            runCatching { YouTube.searchSuggestions(text) }
                .onSuccess { suggestions = it }
                .onFailure { suggestions = emptyList() }
            suggestionsLoading = false
        }
    }

    fun updateFilter(filter: SearchFilterState) {
        filterState = filter
        if (query.isNotBlank()) {
            submit()
        }
    }

    fun submit() {
        val q = query.trim()
        if (q.isEmpty()) return
        SearchHistory.add(q)
        suggestJob?.cancel()
        suggestions = emptyList()
        suggestionsLoading = false
        submitted = q
        loading = true
        loadingMore = false
        error = null
        next = null
        channelCard = null
        job?.cancel()
        job = scope.launch {
            runCatching { YouTube.search(q, params = filterState.toInnerTubeParams()) }
                .onSuccess { page ->
                    results = page.videos
                    channelCard = page.channelCard
                    next = page.next
                    generation++
                }
                .onFailure { error = YouTube.friendlyError(it) }
            loading = false
        }
    }

    fun loadMore() {
        val token = next ?: return
        if (loading || loadingMore) return
        loadingMore = true
        job = scope.launch {
            runCatching { YouTube.search(submitted, token, params = filterState.toInnerTubeParams()) }
                .onSuccess { page ->
                    val seen = results.mapTo(HashSet()) { it.id }
                    val added = page.videos.filter { seen.add(it.id) }
                    results = results + added
                    next = page.next
                }
                .onFailure { /* loadMore fails silently */ }
            loadingMore = false
        }
    }
}

@Composable
fun SearchScreen(
    state: SearchState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (VideoItem) -> Unit,
    onOpenChannelUrl: (String) -> Unit = {},
    scrollToTop: Int = 0,
) {
    val c = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val view = LocalView.current
    val searchFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showMenu by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    // Speech Recognizer Launcher for Voice Search
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                state.updateQuery(spokenText)
                focus.clearFocus(force = true)
                keyboard?.hide()
                state.submit()
            }
        }
    }

    LaunchedEffect(Unit) { SearchHistory.init(context) }

    fun showSearchKeyboard() {
        scope.launch {
            delay(80)
            runCatching { searchFocus.requestFocus() }
            keyboard?.show()
        }
    }

    LaunchedEffect(Unit) { showSearchKeyboard() }

    fun submit() {
        focus.clearFocus(force = true)
        keyboard?.hide()
        view.postDelayed({
            view.context.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(view.windowToken, 0)
        }, 120)
        state.submit()
    }

    fun startVoiceSearch() {
        runCatching {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Search YouTube")
            }
            speechLauncher.launch(intent)
        }
    }

    if (showFilterDialog) {
        SearchFiltersDialog(
            initialFilter = state.filterState,
            onDismiss = { showFilterDialog = false },
            onApply = { newFilter ->
                showFilterDialog = false
                state.updateFilter(newFilter)
            }
        )
    }

    Box(Modifier.fillMaxSize().background(c.surface).overlayTouchBlock()) {
        CollapsingHeaderLayout(
            resetKey = scrollToTop to state.generation,
            header = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(c.surface)
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Navigate up", tint = c.onSurface, modifier = Modifier.size(22.dp))
                        }

                        // Search box pill matching Morph style
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(c.surfaceContainerHigh)
                                .padding(start = 14.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f)) {
                                if (state.query.isEmpty()) {
                                    Text(
                                        "Search YouTube",
                                        color = c.onSurfaceVariant,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                BasicTextField(
                                    value = state.query,
                                    onValueChange = state::updateQuery,
                                    singleLine = true,
                                    textStyle = TextStyle(color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                                    cursorBrush = SolidColor(c.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { submit() }, onDone = { submit() }),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(searchFocus)
                                        .onPreviewKeyEvent { e ->
                                            if (e.key == Key.Enter || e.key == Key.NumPadEnter) {
                                                if (e.type == KeyEventType.KeyDown) submit()
                                                true
                                            } else false
                                        },
                                )
                            }

                            if (state.query.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            state.updateQuery("")
                                            showSearchKeyboard()
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.Close, "Clear", tint = c.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Spacer(Modifier.width(2.dp))

                        // Voice Search Button
                        IconButton(onClick = ::startVoiceSearch, modifier = Modifier.size(44.dp)) {
                            Icon(painterResource(R.drawable.ic_mic), contentDescription = "Voice search", tint = c.onSurface, modifier = Modifier.size(22.dp))
                        }

                        // Overflow menu for Search Filters & Help
                        Box {
                            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = c.onSurface, modifier = Modifier.size(22.dp))
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Search filters", fontSize = 14.sp) },
                                    leadingIcon = { Icon(Icons.Rounded.FilterList, null, modifier = Modifier.size(20.dp)) },
                                    onClick = {
                                        showMenu = false
                                        showFilterDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Help and feedback", fontSize = 14.sp) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, modifier = Modifier.size(20.dp)) },
                                    onClick = {
                                        showMenu = false
                                        Toast.makeText(context, "FlashTube v5.2 • Morph Search Engine", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }

                    // Active Filters Chips Bar
                    if (!state.filterState.isDefault && state.submitted.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (state.filterState.type != SearchType.ALL) {
                                item {
                                    ActiveFilterChip(state.filterState.type.label) {
                                        state.updateFilter(state.filterState.copy(type = SearchType.ALL))
                                    }
                                }
                            }
                            if (state.filterState.duration != SearchDuration.ANY) {
                                item {
                                    ActiveFilterChip(state.filterState.duration.label) {
                                        state.updateFilter(state.filterState.copy(duration = SearchDuration.ANY))
                                    }
                                }
                            }
                            if (state.filterState.uploadDate != SearchUploadDate.ANY) {
                                item {
                                    ActiveFilterChip(state.filterState.uploadDate.label) {
                                        state.updateFilter(state.filterState.copy(uploadDate = SearchUploadDate.ANY))
                                    }
                                }
                            }
                            if (state.filterState.sortBy != SearchSortBy.RELEVANCE) {
                                item {
                                    ActiveFilterChip(state.filterState.sortBy.label) {
                                        state.updateFilter(state.filterState.copy(sortBy = SearchSortBy.RELEVANCE))
                                    }
                                }
                            }
                            state.filterState.selectedFeatures.forEach { feature ->
                                item {
                                    ActiveFilterChip(feature.label) {
                                        state.updateFilter(state.filterState.copy(selectedFeatures = state.filterState.selectedFeatures - feature))
                                    }
                                }
                            }
                            item {
                                TextButton(
                                    onClick = { state.updateFilter(SearchFilterState()) },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("Clear all", fontSize = 12.sp, color = c.primary)
                                }
                            }
                        }
                    }
                }
            },
        ) { top ->
            val isEditing = state.submitted.isEmpty() || state.query.trim() != state.submitted.trim()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.padding(top = if (!isEditing && state.results.isNotEmpty() && state.error == null) 0.dp else top)) {
                    when {
                        isEditing -> {
                            val matchingHistory = if (state.query.isBlank()) {
                                SearchHistory.queries
                            } else {
                                SearchHistory.queries.filter {
                                    it.contains(state.query.trim(), ignoreCase = true)
                                }
                            }
                            val nonHistorySuggestions = if (state.query.isBlank()) {
                                emptyList()
                            } else {
                                state.suggestions.filterNot { s ->
                                    matchingHistory.any { it.equals(s, ignoreCase = true) }
                                }
                            }

                            if (state.suggestionsLoading && state.suggestions.isEmpty() && matchingHistory.isEmpty() && state.query.isNotBlank()) {
                                Box(Modifier.fillMaxWidth().padding(top = 32.dp), Alignment.Center) {
                                    CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                                }
                            } else if (state.suggestions.isEmpty() && matchingHistory.isEmpty()) {
                                EmptyState(
                                    icon = null,
                                    title = if (state.query.isBlank()) "Recent searches" else "Keep typing",
                                    body = if (state.query.isBlank()) "Your recent search history will appear here." else "Suggestions appear as you enter a search.",
                                    iconRes = R.drawable.ic_player_search,
                                )
                            } else {
                                LazyColumn(contentPadding = contentPadding) {
                                    // Local History suggestions (Morph height: 48dp)
                                    items(matchingHistory, key = { "hist-$it" }, contentType = { "history_suggestion" }) { query ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .clickable {
                                                    state.updateQuery(query)
                                                    submit()
                                                }
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Rounded.History,
                                                contentDescription = null,
                                                tint = c.onSurfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(Modifier.width(14.dp))
                                            Text(
                                                text = query,
                                                modifier = Modifier.weight(1f),
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 15.sp,
                                                color = c.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            // Morph's Edit / Insert Arrow (copies suggestion into search bar)
                                            IconButton(
                                                onClick = {
                                                    state.updateQuery(query)
                                                    showSearchKeyboard()
                                                },
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    painterResource(R.drawable.ic_edit_suggestion),
                                                    contentDescription = "Edit suggestion $query",
                                                    tint = c.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            // Delete button for history
                                            IconButton(
                                                onClick = {
                                                    SearchHistory.remove(query)
                                                    state.updateQuery(state.query)
                                                },
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Delete,
                                                    contentDescription = "Remove from history",
                                                    tint = c.onSurfaceVariant.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Live API Suggestions (Morph height: 48dp)
                                    items(nonHistorySuggestions, key = { "sug-$it" }, contentType = { "live_suggestion" }) { suggestion ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .clickable {
                                                    state.updateQuery(suggestion)
                                                    submit()
                                                }
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                painterResource(R.drawable.ic_player_search),
                                                contentDescription = null,
                                                tint = c.onSurfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(Modifier.width(14.dp))
                                            Text(
                                                text = suggestion,
                                                modifier = Modifier.weight(1f),
                                                fontSize = 15.sp,
                                                color = c.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            // Morph's Edit / Insert Arrow (copies suggestion into search bar)
                                            IconButton(
                                                onClick = {
                                                    state.updateQuery(suggestion)
                                                    showSearchKeyboard()
                                                },
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    painterResource(R.drawable.ic_edit_suggestion),
                                                    contentDescription = "Edit suggestion $suggestion",
                                                    tint = c.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        state.loading -> {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                            }
                        }
                        state.error != null -> {
                            EmptyState(
                                icon = Icons.Rounded.CloudOff,
                                title = "Search failed",
                                body = state.error.orEmpty(),
                                action = { PillButton("Try again", state::submit) },
                            )
                        }
                        state.results.isEmpty() && state.channelCard == null -> {
                            EmptyState(
                                icon = null,
                                title = "No results found",
                                body = "No content matched \"${state.submitted}\". Try another search.",
                                iconRes = R.drawable.ic_player_search,
                            )
                        }
                        else -> {
                            Column(Modifier.fillMaxSize()) {
                                // Render Channel Hero Card if present
                                state.channelCard?.let { channel ->
                                    ChannelResultCard(
                                        channel = channel,
                                        onOpenChannel = { onOpenChannelUrl(channel.url) }
                                    )
                                }

                                FeedList(
                                    feed = FeedState(scope) { FeedPage(state.results, if (state.hasMore) "more" else null) },
                                    contentPadding = contentPadding.withTop(top),
                                    onOpen = onOpen,
                                    scrollToTop = scrollToTop,
                                    indicatorTop = top,
                                    videos = state.results,
                                    onLoadMore = state::loadMore,
                                    isLoadingMore = state.loadingMore,
                                    hasMore = state.hasMore,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Channel Card Component displayed in search results matching Morph app layout. */
@Composable
fun ChannelResultCard(
    channel: ChannelDetails,
    onOpenChannel: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    var isSubscribed by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable { onOpenChannel() },
        shape = RoundedCornerShape(16.dp),
        color = c.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = channel.avatar,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(c.surfaceContainerHigh)
                )

                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = channel.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = c.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (channel.verified) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Verified channel",
                                tint = c.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    val subText = buildString {
                        if (!channel.handle.isNull_or_blank()) {
                            append(channel.handle)
                        }
                        if (channel.subscribers > 0) {
                            if (isNotEmpty()) append(" • ")
                            append("${formatCount(channel.subscribers)} subscribers")
                        }
                    }

                    if (subText.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subText,
                            fontSize = 13.sp,
                            color = c.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Subscribe Pill Button
                Button(
                    onClick = { isSubscribed = !isSubscribed },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSubscribed) c.surfaceContainerHighest else c.primary,
                        contentColor = if (isSubscribed) c.onSurfaceVariant else c.onPrimary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Text(
                        text = if (isSubscribed) "Subscribed" else "Subscribe",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // View Channel Outlined Button
                OutlinedButton(
                    onClick = onOpenChannel,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Text("View Channel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                // YouTube Music Option Button
                OutlinedButton(
                    onClick = onOpenChannel,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_youtube_music),
                        contentDescription = "YouTube Music",
                        tint = c.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun String?.isNull_or_blank(): Boolean = this.isNullOrBlank()

@Composable
private fun ActiveFilterChip(label: String, onRemove: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = c.primaryContainer,
        modifier = Modifier.height(30.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 12.sp, color = c.onPrimaryContainer, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Remove filter",
                tint = c.onPrimaryContainer,
                modifier = Modifier.size(14.dp).clickable { onRemove() }
            )
        }
    }
}

/** Morph Search Filters Modal Dialog */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchFiltersDialog(
    initialFilter: SearchFilterState,
    onDismiss: () -> Unit,
    onApply: (SearchFilterState) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    var currentFilter by remember { mutableStateOf(initialFilter) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = c.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = "Search filters",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 1. Type Dropdown
                FilterDropdownSection(
                    label = "Type",
                    options = SearchType.entries.map { it.label },
                    selected = currentFilter.type.label,
                    onSelect = { selectedLabel ->
                        val selected = SearchType.entries.first { it.label == selectedLabel }
                        currentFilter = currentFilter.copy(type = selected)
                    }
                )

                Spacer(Modifier.height(12.dp))

                // 2. Duration Dropdown
                FilterDropdownSection(
                    label = "Duration",
                    options = SearchDuration.entries.map { it.label },
                    selected = currentFilter.duration.label,
                    onSelect = { selectedLabel ->
                        val selected = SearchDuration.entries.first { it.label == selectedLabel }
                        currentFilter = currentFilter.copy(duration = selected)
                    }
                )

                Spacer(Modifier.height(12.dp))

                // 3. Upload date Dropdown
                FilterDropdownSection(
                    label = "Upload date",
                    options = SearchUploadDate.entries.map { it.label },
                    selected = currentFilter.uploadDate.label,
                    onSelect = { selectedLabel ->
                        val selected = SearchUploadDate.entries.first { it.label == selectedLabel }
                        currentFilter = currentFilter.copy(uploadDate = selected)
                    }
                )

                Spacer(Modifier.height(12.dp))

                // 4. Prioritise (Sort by) Dropdown
                FilterDropdownSection(
                    label = "Prioritise",
                    options = SearchSortBy.entries.map { it.label },
                    selected = currentFilter.sortBy.label,
                    onSelect = { selectedLabel ->
                        val selected = SearchSortBy.entries.first { it.label == selectedLabel }
                        currentFilter = currentFilter.copy(sortBy = selected)
                    }
                )

                Spacer(Modifier.height(20.dp))

                // 5. Features Tag Cloud / Chips
                Text(
                    text = "Features",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SearchFeature.entries.forEach { feature ->
                        val isSelected = feature in currentFilter.selectedFeatures
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val newFeatures = if (isSelected) {
                                    currentFilter.selectedFeatures - feature
                                } else {
                                    currentFilter.selectedFeatures + feature
                                }
                                currentFilter = currentFilter.copy(selectedFeatures = newFeatures)
                            },
                            label = { Text(feature.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = c.primary,
                                selectedLabelColor = c.onPrimary,
                                containerColor = c.surfaceContainer,
                                labelColor = c.onSurface
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = c.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onApply(currentFilter) },
                        colors = ButtonDefaults.buttonColors(containerColor = c.primary),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Apply", color = c.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdownSection(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = c.onSurface,
            modifier = Modifier.width(100.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.primary,
                    unfocusedBorderColor = c.outline.copy(alpha = 0.4f),
                    focusedContainerColor = c.surfaceContainer,
                    unfocusedContainerColor = c.surfaceContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                textStyle = TextStyle(fontSize = 13.sp)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, fontSize = 13.sp) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
