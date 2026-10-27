package com.thinker.flashtube

import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Lives above the tab switcher so results survive switching tabs. */
@Stable
class SearchState(private val scope: CoroutineScope) {
    var query by mutableStateOf("")
        private set
    var submitted by mutableStateOf("")
        private set
    var results by mutableStateOf<List<VideoItem>>(emptyList())
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
        suggestions = emptyList()
        suggestionsLoading = false
        loading = false
        loadingMore = false
        error = null
        next = null
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
                error = null
                loading = false
                loadingMore = false
                next = null
                job?.cancel()
            }
            return
        }
        suggestJob = scope.launch {
            delay(220)
            suggestionsLoading = true
            runCatching { YouTube.searchSuggestions(text) }
                .onSuccess { suggestions = it }
                .onFailure { suggestions = emptyList() }
            suggestionsLoading = false
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
        job?.cancel()
        job = scope.launch {
            runCatching { YouTube.search(q) }
                .onSuccess { page ->
                    results = page.videos
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
            runCatching { YouTube.search(submitted, token) }
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
    scrollToTop: Int = 0,
) {
    val c = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val view = LocalView.current
    val searchFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Ensure search history is loaded (defensive; already initialized in Application).
    LaunchedEffect(Unit) { SearchHistory.init(context) }
    fun showSearchKeyboard() {
        scope.launch {
            delay(80)
            runCatching { searchFocus.requestFocus() }
            keyboard?.show()
        }
    }
    LaunchedEffect(Unit) { showSearchKeyboard() }
    // Search and Enter both dismiss the keyboard — every field, every time. A hide issued
    // while the key event is still being dispatched can be undone by the IME, so the
    // window-level hide is posted to run after it.
    fun submit() {
        focus.clearFocus(force = true)
        keyboard?.hide()
        view.postDelayed({
            view.context.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(view.windowToken, 0)
        }, 120)
        state.submit()
    }
    Box(Modifier.fillMaxSize().background(c.surface).overlayTouchBlock()) {
        // The search bar rides with the header: both slide away together while scrolling results.
        CollapsingHeaderLayout(
            resetKey = scrollToTop to state.generation,
            header = {
                BackScreenHeader(onBack = onBack) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(CircleShape)
                            .background(c.surfaceContainerHigh)
                            .padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (state.query.isEmpty()) {
                                Text(
                                    "Search YouTube",
                                    color = c.onSurfaceVariant,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
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
                                modifier = Modifier.size(32.dp).pressable {
                                    state.updateQuery("")
                                    showSearchKeyboard()
                                },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Close, "Clear", tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            },
        ) { top ->
            val draft = state.query.isNotBlank() &&
                (state.submitted.isEmpty() || state.query.trim() != state.submitted.trim())
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.padding(top = if (!draft && state.results.isNotEmpty() && state.error == null) 0.dp else top)) {
                    when {
                        draft -> {
                            val matchingHistory = SearchHistory.queries.filter {
                                it.contains(state.query.trim(), ignoreCase = true)
                            }
                            val nonHistorySuggestions = state.suggestions.filterNot { s ->
                                matchingHistory.any { it.equals(s, ignoreCase = true) }
                            }

                            if (state.suggestionsLoading && state.suggestions.isEmpty() && matchingHistory.isEmpty()) {
                                Box(Modifier.fillMaxWidth().padding(top = 32.dp), Alignment.Center) {
                                    CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(32.dp))
                                }
                            } else if (state.suggestions.isEmpty() && matchingHistory.isEmpty()) {
                                EmptyState(
                                    icon = Icons.Rounded.Search,
                                    title = "Keep typing",
                                    body = "Suggestions appear as you enter a search.",
                                )
                            } else {
                                LazyColumn(contentPadding = contentPadding) {
                                    items(matchingHistory, key = { "hist-$it" }) { query ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .pressable {
                                                    state.updateQuery(query)
                                                    submit()
                                                }
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Rounded.History, null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(16.dp))
                                            Text(
                                                query,
                                                modifier = Modifier.weight(1f),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 15.sp,
                                            )
                                            Box(
                                                modifier = Modifier.size(32.dp).pressable {
                                                    SearchHistory.remove(query)
                                                    state.updateQuery(state.query)
                                                },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Delete,
                                                    "Remove from search history",
                                                    tint = c.onSurfaceVariant.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                    }
                                    items(nonHistorySuggestions, key = { "sug-$it" }) { suggestion ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .pressable {
                                                    state.updateQuery(suggestion)
                                                    submit()
                                                }
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Rounded.Search, null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(16.dp))
                                            Text(
                                                suggestion,
                                                modifier = Modifier.weight(1f),
                                                fontSize = 15.sp,
                                            )
                                            Icon(
                                                Icons.Rounded.TravelExplore,
                                                null,
                                                tint = c.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        state.loading -> {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(36.dp))
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
                        state.results.isEmpty() -> {
                            EmptyState(
                                icon = Icons.Rounded.Search,
                                title = "No results found",
                                body = "No videos matched \"${state.submitted}\". Try another search.",
                            )
                        }
                        else -> {
                            FeedList(
                                feed = FeedState(scope) { FeedPage(state.results, if (state.hasMore) "more" else null) },
                                contentPadding = contentPadding.withTop(top),
                                onOpen = onOpen,
                                scrollToTop = scrollToTop,
                                indicatorTop = top,
                                videos = state.results,
                            )
                        }
                    }
                }
            }
        }
    }
}
