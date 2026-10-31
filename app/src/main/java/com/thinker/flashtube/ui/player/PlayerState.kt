package com.thinker.flashtube

import android.content.ComponentName
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The UI's view of playback. Holds the MediaController plus everything the player
 * screens render, as snapshot state so only readers recompose.
 */
@Stable
class PlayerState(private val scope: CoroutineScope) {
    var controller by mutableStateOf<MediaController?>(null)
        internal set

    /** What's loaded (or loading). Null means nothing — no mini-player. */
    var current by mutableStateOf<VideoItem?>(null)
        private set
    var details by mutableStateOf<VideoDetails?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var bufferedMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    /** The player's playback speed (see [PlaybackSpeed]). */
    var speed by mutableFloatStateOf(1f)
        private set
    var loopEnabled by mutableStateOf(false)
        private set
    var autoPlayEnabled by mutableStateOf(true)
        private set
    var videoZoom by mutableFloatStateOf(1f)
    var zoomToFill by mutableStateOf(false)

    fun resetZoom() { videoZoom = 1f; zoomToFill = false }

    fun toggleZoomToFill() {
        zoomToFill = !zoomToFill
        actionMessage = if (zoomToFill) "Zoomed to fill screen" else "Fit to screen"
    }

    fun toggleAutoPlay() {
        autoPlayEnabled = !autoPlayEnabled
        actionMessage = if (autoPlayEnabled) "Autoplay on" else "Autoplay off"
    }

    fun toggleLoop() {
        val player = controller ?: return
        val next = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
        player.repeatMode = next
        loopEnabled = next == Player.REPEAT_MODE_ONE
        actionMessage = if (loopEnabled) "Loop on" else "Loop off"
    }

    // ── A-B Segment Loop ──
    var pointA by mutableStateOf<Long?>(null)
        private set
    var pointB by mutableStateOf<Long?>(null)
        private set
    var abLoopEnabled by mutableStateOf(false)
        private set

    fun markPointA(ms: Long? = positionMs) {
        pointA = ms
        if (ms != null && pointB != null && ms >= pointB!!) {
            pointB = null
        }
        actionMessage = if (ms != null) "Point A set to ${formatDuration(ms)}" else "Point A cleared"
    }

    fun markPointB(ms: Long? = positionMs) {
        if (pointA != null && ms != null && ms <= pointA!!) {
            actionMessage = "Point B must be after Point A"
            return
        }
        pointB = ms
        abLoopEnabled = pointA != null && pointB != null
        actionMessage = if (ms != null) "Point B set to ${formatDuration(ms)}" else "Point B cleared"
    }

    fun toggleAbLoop() {
        if (pointA == null || pointB == null) {
            actionMessage = "Set Point A and B to loop"
            return
        }
        abLoopEnabled = !abLoopEnabled
        actionMessage = if (abLoopEnabled) "A-B Loop ON" else "A-B Loop OFF"
    }

    fun clearAbLoop() {
        pointA = null
        pointB = null
        abLoopEnabled = false
        actionMessage = "A-B Loop Cleared"
    }

    // ── Stats for Nerds ──
    var statsForNerdsOpen by mutableStateOf(false)

    fun toggleStatsForNerds() {
        statsForNerdsOpen = !statsForNerdsOpen
    }

    var selectedSubtitleLanguage by mutableStateOf<String?>(null)
        private set

    fun setSubtitleLanguage(code: String?) {
        selectedSubtitleLanguage = code
        val p = controller ?: return
        try {
            if (code == null) {
                p.trackSelectionParameters = p.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                actionMessage = "Captions off"
            } else {
                p.trackSelectionParameters = p.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setPreferredTextLanguage(code)
                    .build()
                actionMessage = "Captions on"
            }
        } catch (e: Exception) {
            Log.e("PlayerState", "Error setting subtitle language", e)
        }
    }

    var selectedAudioLanguage by mutableStateOf<String?>(null)
        private set

    fun setAudioLanguage(code: String?) {
        selectedAudioLanguage = code
        val p = controller ?: return
        try {
            if (code != null) {
                p.trackSelectionParameters = p.trackSelectionParameters
                    .buildUpon()
                    .setPreferredAudioLanguage(code)
                    .build()
                actionMessage = "Audio track updated"
            }
        } catch (e: Exception) {
            Log.e("PlayerState", "Error setting audio language", e)
        }
    }
    var speedPanelOpen by mutableStateOf(false)
    /** Finger is held for temporary 2× playback. */
    var holdSpeedActive by mutableStateOf(false)
        internal set
    /** While dragging the portrait edge scrubber; also mirrors overlay controls visibility. */
    var scrubPreviewMs by mutableStateOf<Long?>(null)
        internal set
    var scrubDragging by mutableStateOf(false)
        internal set
    private var resumePlayAfterScrub = false
    private var speedBeforeHold: Float? = null
    var videoControlsVisible by mutableStateOf(false)
        internal set

    init {
        // The service's stream recovery has no player event for its outcome: follow its state.
        scope.launch {
            snapshotFlow { StreamRecovery.recovering to StreamRecovery.failed }.collect { (_, failed) ->
                syncFromPlayer()
                if (failed != null && failed.first == current?.id) {
                    error = failed.second
                    isBuffering = false
                }
            }
        }
    }
    var videoAspect by mutableFloatStateOf(16f / 9f)
        private set
    /** Set once real frames have reported their size; the stream's own numbers no longer apply. */
    private var aspectFromFrames = false

    /**
     * Portrait player frame: the video's aspect within [StageMinAspect]..[StageMaxAspect], but
     * not wider than [StageFrameAspect] (ultrawide letterboxes inside 16:9).
     */
    val stageAspect: Float get() = min(videoAspect.coerceIn(StageMinAspect, StageMaxAspect), StageFrameAspect)
    var firstFrameRendered by mutableStateOf(false)
        private set
    /** What the decoder is really producing, e.g. "1920×1080 · H.264 · itag 299". */
    var playingFormat by mutableStateOf<String?>(null)
        private set
    /** Quality tier label (1080p, not ultrawide pixel height) from decoded frames when known. */
    var decodedHeight by mutableStateOf<Int?>(null)
        private set

    var expanded by mutableStateOf(false)
    /** Landscape fullscreen requested (button) or entered (rotation). */
    var fullscreen by mutableStateOf(false)
    /** Which bottom sheet is open over the player, if any. */
    var sheet by mutableStateOf<PlayerSheet?>(null)

    val comments = CommentsState(scope)

    // ── Signed-in account state for the open video (full sync) ──
    /** Like/subscribe state from the account; null while loading or signed out. */
    var accountState by mutableStateOf<VideoAccountState?>(null)
        private set
    private var originalLike = LikeStatus.None
    var inWatchLater by mutableStateOf(false)
        private set
    /** A short message after an account action fails (shown briefly over the player). */
    var actionMessage by mutableStateOf<String?>(null)

    private var loadJob: Job? = null
    /** A video picked before the service connection finished; started on connect. */
    private var pendingStart: VideoItem? = null

    // ── Queue ──
    // The player's playlist is always [current, ...queue]: opening a video swaps the
    // current item, auto-advancing drops the finished one. Items carry only an id across
    // the controller boundary, so the full VideoItems are remembered here.
    private val known = HashMap<String, VideoItem>()
    /** Videos after the current one, in play order. */
    var queue by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    /** True only while the current media came from a server-generated YouTube Mix. */
    var mixQueueActive by mutableStateOf(false)
        private set
    private var mixLoadJob: Job? = null
    private var mixLoadGeneration = 0
    private var previousItems by mutableStateOf<List<VideoItem>>(emptyList())
    val canGoPreviousMedia: Boolean get() = previousItems.isNotEmpty()

    fun open(item: VideoItem, expand: Boolean = true) {
        val mixId = item.mixPlaylistId
            ?: if (item.id.startsWith("RD")) item.id
            else if (item.uploaded?.contains("Infinite playlist", ignoreCase = true) == true || item.title.contains("YouTube Mix", ignoreCase = true)) "RDMM${item.id}"
            else null

        if (mixId != null) {
            openMix(item.copy(mixPlaylistId = mixId), expand)
            return
        }
        mixLoadJob?.cancel()
        mixLoadGeneration++
        if (mixQueueActive) {
            clearQueue()
            mixQueueActive = false
        }
        openVideo(item, expand)
    }

    private fun openVideo(item: VideoItem, expand: Boolean) {
        if (expand) expanded = true
        if (current?.id == item.id && error == null) return
        known[item.id] = item
        show(item, startPlayback = true)
    }

    /** Loads the actual YouTube radio playlist and replaces, rather than appends to, the old mix queue. */
    fun openMix(mix: VideoItem, expand: Boolean = true) {
        val playlistId = mix.mixPlaylistId
            ?: if (mix.id.startsWith("RD")) mix.id else "RDMM${mix.id}"
        loadMix(playlistId, mix, expand)
    }

    /** Starts a Mix card directly, using its first server-provided track as the current item. */
    fun openMix(playlistId: String, expand: Boolean = true) {
        if (!playlistId.startsWith("RD")) return
        loadMix(playlistId, seed = null, expand = expand)
    }

    private fun loadMix(playlistId: String, seed: VideoItem?, expand: Boolean) {
        mixLoadJob?.cancel()
        val generation = ++mixLoadGeneration
        if (expand) expanded = true
        clearQueue()
        mixQueueActive = false
        queue = emptyList()
        // A Mix card already identifies the track the user selected. Start it now;
        // loading the rest of the Mix is background work and must not delay playback.
        val immediateStart = seed?.copy(mixPlaylistId = null)
        if (immediateStart != null) {
            known[immediateStart.id] = immediateStart
            openVideo(immediateStart, expand)
        }
        mixLoadJob = scope.launch {
            runCatching {
                InnerTube.playlist(playlistId, videoId = seed?.id)
            }.onSuccess { firstPage ->
                if (generation != mixLoadGeneration) return@onSuccess
                val items = firstPage.videos.filter { it.mixPlaylistId == null }.distinctBy { it.id }
                if (items.isEmpty()) {
                    actionMessage = "Couldn't load this Mix queue."
                    return@onSuccess
                }
                val startIndex = items.indexOfFirst { it.id == seed?.id }.takeIf { it >= 0 } ?: 0
                val ordered = items.drop(startIndex)
                val start = ordered.first().copy(mixPlaylistId = null)
                if (current?.id != start.id) openVideo(start, expand)
                known[start.id] = start

                val remaining = ordered.drop(1)
                remaining.forEach { known[it.id] = it }
                queue = remaining
                if (queue.isNotEmpty()) mixQueueActive = true

                // The playlist is fetched from YouTube, not generated from related videos.
                // Add the remaining server order as one replacement queue for this mix.
                var activeController: MediaController? = null
                repeat(50) {
                    val connected = controller
                    if (connected != null && current?.id == start.id && connected.mediaItemCount > 0) {
                        activeController = connected
                    } else {
                        delay(100)
                    }
                }
                val controller = activeController
                if (generation != mixLoadGeneration || controller == null) return@onSuccess
                val queuedIds = HashSet<String>().apply { add(start.id) }
                fun appendPage(videos: List<VideoItem>) {
                    videos.forEach { video ->
                        if (queuedIds.add(video.id)) {
                            known[video.id] = video
                            controller.addMediaItem(mediaItemFor(video))
                        }
                    }
                    rebuildQueue()
                    if (queue.isNotEmpty()) mixQueueActive = true
                }
                appendPage(remaining)

                // Start with YouTube's first batch, then keep the radio queue ahead of playback.
                var continuation = firstPage.next
                while (continuation != null && generation == mixLoadGeneration) {
                    while (queue.size > 5 && generation == mixLoadGeneration) delay(500)
                    if (generation != mixLoadGeneration) break
                    val page = InnerTube.playlist(playlistId, continuation)
                    if (generation != mixLoadGeneration) break
                    appendPage(page.videos.filter { it.mixPlaylistId == null })
                    continuation = page.next
                }
            }.onFailure {
                if (generation == mixLoadGeneration) {
                    mixQueueActive = false
                    seed?.let { openVideo(it.copy(mixPlaylistId = null), expand) }
                    actionMessage = "Couldn't load this Mix."
                }
            }
        }
    }

    /** Opens an entire playlist of videos starting at [startIndex]. */
    fun openPlaylist(items: List<VideoItem>, startIndex: Int = 0, expand: Boolean = true) {
        if (items.isEmpty()) return
        val index = startIndex.coerceIn(0, items.size - 1)
        val start = items[index]
        val remaining = items.drop(index + 1)
        open(start, expand = expand)
        scope.launch {
            delay(150)
            remaining.forEach { item ->
                addToQueue(item)
            }
        }
    }

    /** Opens a playlist in shuffled order. */
    fun shufflePlaylist(items: List<VideoItem>) {
        if (items.isEmpty()) return
        openPlaylist(items.shuffled(), startIndex = 0)
    }

    /** Shuffles the remaining upcoming queued items. */
    fun shuffleQueue() {
        if (queue.isEmpty()) return
        if (controller == null) return
        val reordered = queue.shuffled()
        moveQueueTo(reordered)
    }

    /** Moves one upcoming item and updates both the visible list and Media3's play order. */
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) return
        val reordered = queue.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        moveQueueTo(reordered)
    }

    private fun moveQueueTo(reordered: List<VideoItem>) {
        val player = controller ?: return
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex < 0 || currentIndex >= player.mediaItemCount) return

        // Move items in place so playback position and the current item are preserved.
        val working = queue.toMutableList()
        reordered.forEachIndexed { target, item ->
            val from = working.indexOfFirst { it.id == item.id }
            if (from >= 0 && from != target) {
                player.moveMediaItem(currentIndex + 1 + from, currentIndex + 1 + target)
                working.add(target, working.removeAt(from))
            }
        }
        queue = reordered
    }

    /**
     * Shows [item] as the current video: details, comments and account state load; with
     * [startPlayback] it also becomes the playing item (otherwise the player already moved
     * to it, e.g. the queue advanced).
     */
    private var openedAt = 0L

    var loadingMoreRelated by mutableStateOf(false)
        private set

    fun loadMoreRelated() {
        val curr = details ?: return
        val item = curr.item
        if (loadingMoreRelated) return
        loadingMoreRelated = true
        scope.launch {
            val token = curr.relatedToken
            val result = if (token != null) {
                runCatching { YouTube.nextRelated(token) }.getOrNull()
            } else null

            if (result != null && result.videos.isNotEmpty()) {
                val seen = curr.related.mapTo(HashSet()) { it.id }
                val added = result.videos.filter { seen.add(it.id) && it.id != item.id }
                details = curr.copy(related = curr.related + added, relatedToken = result.next)
                added.forEach { known[it.id] = it }
            } else {
                // Continuation token missing or exhausted: query search for additional related videos
                val query = "${item.channel} ${item.title.split(" ").take(3).joinToString(" ")}"
                runCatching { YouTube.search(query) }
                    .onSuccess { page ->
                        val seen = curr.related.mapTo(HashSet()) { it.id }
                        val added = page.videos.filter { seen.add(it.id) && it.id != item.id }
                        details = curr.copy(related = curr.related + added)
                        added.forEach { known[it.id] = it }
                    }
            }
            loadingMoreRelated = false
        }
    }

    private fun show(item: VideoItem, startPlayback: Boolean) {
        endHoldSpeed()
        if (startPlayback && current?.id != item.id) rememberPrevious(current)
        openedAt = android.os.SystemClock.elapsedRealtime()
        Log.i("FlashTubeTime", "open ${item.id}")
        if (startPlayback) Net.prewarmMediaConnection()
        current = item
        details = null
        error = null
        firstFrameRendered = false
        videoAspect = 16f / 9f
        aspectFromFrames = false
        decodedHeight = null
        playingFormat = null
        loading = true
        sheet = null
        comments.clearFor(item.id)
        accountState = null
        inWatchLater = false
        speedPanelOpen = false
        videoZoom = 1f
        loadingMoreRelated = false
        // Start the service's stream resolution as soon as the user taps.
        // YouTube.details() also reads StreamCache, so running both now lets
        // the two requests share its in-flight extraction instead of making
        // the player wait for details before it even asks for a media item.
        if (startPlayback) startPlayback(item)
        if (Account.signedIn) {
            scope.launch {
                runCatching { InnerTube.videoState(item.id) }
                    .onSuccess { if (current?.id == item.id) { accountState = it; originalLike = it.like } }
                    .onFailure { Log.w(TAG, "account state for ${item.id}", it) }
            }
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            runCatching { YouTube.details(item.id, includeRelated = false) }
                .onSuccess { d ->
                    Log.i("FlashTubeTime", "details +${android.os.SystemClock.elapsedRealtime() - openedAt}ms")
                    details = d
                    ResumeStore.markMusicVideo(item.id, d.isMusicVideo)
                    if (!d.isMusicVideo && startPlayback && current?.id == item.id) {
                        val resumeAt = ResumeStore.startPosition(item.id)
                        if (resumeAt > 0L && (controller?.currentPosition ?: Long.MAX_VALUE) < 2_000L) {
                            controller?.seekTo(resumeAt)
                        }
                    }
                    // The player takes the video's shape before its first frame arrives.
                    if (!aspectFromFrames) d.aspect?.let { videoAspect = it }
                    known[item.id] = d.item
                    d.related.forEach { known[it.id] = it }
                    if (!d.playable) error = "No playable stream was found for this video."

                    // PRIORITY 2: More Videos / Related Videos
                    scope.launch {
                        runCatching { YouTube.related(item.id) }
                            .onSuccess { rel ->
                                if (current?.id == item.id) {
                                    val latest = details ?: return@onSuccess
                                    details = latest.copy(
                                        item = latest.item.copy(collabChannels = rel.collaborators),
                                        related = rel.videos.ifEmpty { latest.related },
                                        relatedToken = rel.continuation,
                                    )
                                    rel.videos.forEach { known[it.id] = it }
                                }
                            }

                        // PRIORITY 3: Comments THIRD (after details & related videos arrive)
                        comments.load(item.id, live = d.item.isLive)
                    }
                    // Fetch real dislike count from Return YouTube Dislike in the background.
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val dislikes = fetchDislikeCount(item.id)
                        if (current?.id == item.id && dislikes >= 0) {
                            details = details?.copy(dislikes = dislikes)
                        }
                    }
                }
                .onFailure {
                    Log.w(TAG, "details failed for ${item.id}", it)
                    error = YouTube.friendlyError(it)
                    // Comments wait for details; without this their spinner never stopped.
                    comments.load(item.id, live = item.isLive)
                }
            loading = false
        }
    }

    private fun mediaItemFor(item: VideoItem) = MediaItem.Builder()
        .setMediaId(item.id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.channel)
                .setArtworkUri(item.thumbnail?.toUri())
                .build(),
        )
        .build()

    private fun startPlayback(item: VideoItem) {
        val player = controller ?: run { pendingStart = item; return }
        // Pick up where this video was left off (ResumeStore; written by the service).
        // Apply resume after category metadata arrives so songs always start at zero.
        replaceCurrent(player, item, positionMs = 0L)
        player.play()
    }

    /**
     * Puts [item] in the current slot, keeping the queue behind it: insert after, jump to
     * it, drop the old one. (setMediaItem would wipe the queue; replaceMediaItem with the
     * same id would keep the old media source, so a quality change wouldn't apply.)
     */
    private fun replaceCurrent(player: MediaController, item: VideoItem, positionMs: Long) {
        if (player.mediaItemCount == 0) {
            player.setMediaItem(mediaItemFor(item), positionMs)
        } else {
            swapping = true
            val index = player.currentMediaItemIndex
            player.replaceMediaItem(index, mediaItemFor(item))
            player.seekTo(index, positionMs)
        }
        player.prepare()
    }

    /** Plays right after the current video. Nothing playing: starts it in the mini-player. */
    fun playNext(item: VideoItem) {
        val player = controller ?: return
        known[item.id] = item
        if (current == null || player.mediaItemCount == 0) { open(item, expand = false); return }
        player.addMediaItem(player.currentMediaItemIndex + 1, mediaItemFor(item))
        actionMessage = "Playing next"
        continueIfEnded(player)
    }

    /** Queued after the current video already finished: carry on into it, as YouTube does. */
    private fun continueIfEnded(player: MediaController) {
        if (player.playbackState == Player.STATE_ENDED && player.currentMediaItemIndex + 1 < player.mediaItemCount) {
            player.seekTo(player.currentMediaItemIndex + 1, 0L)
            player.play()
        }
    }

    fun addToQueue(item: VideoItem) {
        val player = controller ?: return
        known[item.id] = item
        if (current == null || player.mediaItemCount == 0) { open(item, expand = false); return }
        player.addMediaItem(mediaItemFor(item))
        actionMessage = "Added to queue"
        continueIfEnded(player)
    }

    /** [index] into [queue]. */
    fun playFromQueue(index: Int) {
        val player = controller ?: return
        player.seekTo(player.currentMediaItemIndex + 1 + index, 0L)
        player.play()
    }

    /** Return to the last item played, including the previous track in a Mix queue. */
    fun playPreviousMedia() {
        val player = controller ?: return
        val previous = previousItems.lastOrNull() ?: return
        previousItems = previousItems.dropLast(1)
        known[previous.id] = previous
        replaceCurrent(player, previous, 0L)
        show(previous, startPlayback = false)
        player.play()
    }

    /** Previous/next buttons navigate between videos; 10-second seeking has dedicated controls. */
    fun skipToPreviousVideo() {
        val player = controller ?: return
        when {
            player.hasPreviousMediaItem() -> player.seekToPreviousMediaItem()
            previousItems.isNotEmpty() -> playPreviousMedia()
            else -> player.seekTo(0L)
        }
    }

    fun skipToNextVideo() {
        val player = controller ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else {
            autoPlayNext()
        }
    }

    private fun rememberPrevious(item: VideoItem?) {
        item ?: return
        if (previousItems.lastOrNull()?.id == item.id) return
        previousItems = (previousItems + item).takeLast(100)
    }

    fun removeFromQueue(index: Int) {
        val player = controller ?: return
        player.removeMediaItem(player.currentMediaItemIndex + 1 + index)
    }

    fun clearQueue() {
        controller?.let { player ->
            val from = player.currentMediaItemIndex + 1
            if (from < player.mediaItemCount) player.removeMediaItems(from, player.mediaItemCount)
        }
        queue = emptyList()
        mixQueueActive = false
    }

    private fun rebuildQueue() {
        val player = controller ?: return
        val from = player.currentMediaItemIndex + 1
        queue = (from until player.mediaItemCount).map { i -> itemFor(player.getMediaItemAt(i)) }
    }

    private fun itemFor(media: MediaItem): VideoItem = known[media.mediaId] ?: VideoItem(
        id = media.mediaId,
        title = media.mediaMetadata.title?.toString().orEmpty(),
        channel = media.mediaMetadata.artist?.toString().orEmpty(),
        channelUrl = null,
        thumbnail = media.mediaMetadata.artworkUri?.toString(),
        avatar = null,
        durationSec = 0,
        views = -1,
        uploaded = null,
    ).also { known[media.mediaId] = it }

    private fun autoPlayNext() {
        val curr = current ?: return
        val currId = curr.id
        // Prefer related videos from the same channel/creator first to match same-kind media, else next related item
        val relList = details?.related.orEmpty().filter { it.id != currId }
        val next = relList.firstOrNull { it.channel.equals(curr.channel, ignoreCase = true) }
            ?: relList.firstOrNull()
        if (next != null) {
            open(next, expand = expanded)
        } else {
            scope.launch {
                runCatching { YouTube.details(currId) }
                    .onSuccess { d ->
                        val list = d.related.filter { it.id != currId }
                        val rel = list.firstOrNull { it.channel.equals(curr.channel, ignoreCase = true) }
                            ?: list.firstOrNull()
                        if (rel != null) {
                            open(rel, expand = expanded)
                        }
                    }
            }
        }
    }

    /** The player moved on by itself (queue advanced, notification "next"): follow it. */
    private var swapping = false

    private fun onPlayerMovedTo(media: MediaItem) {
        if (controller == null) return
        val wasSwapping = swapping
        swapping = false
        if (wasSwapping || media.mediaId.isBlank()) return
        if (media.mediaId != current?.id) rememberPrevious(current)
        if (media.mediaId != current?.id) show(itemFor(media), startPlayback = false)
    }

    // ── SponsorBlock (segments fetched by the service; read here for the UI) ──
    val segments: List<SponsorBlock.Segment>
        get() = current?.id?.let { SponsorBlock.forUi[it] }.orEmpty()

    /** The "Ask" segment playback is inside right now, if any — drives the Skip pill. */
    val askSegment: SponsorBlock.Segment?
        get() = if (!SponsorBlock.enabled) null else segments.firstOrNull {
            it.action == SponsorBlock.Action.Button && positionMs in it.startMs until it.endMs - 500
        }

    fun skipSegment(segment: SponsorBlock.Segment) = seekTo(segment.endMs)

    fun undoSkip(skip: SponsorBlock.Skip) {
        if (skip.videoId == current?.id) seekTo(skip.segment.startMs)
        SponsorBlock.lastSkip = null
    }

    /** After sign-in or sign-out: reload (or drop) the open video's account state. */
    fun refreshAccountState() {
        val item = current ?: return
        accountState = null
        inWatchLater = false
        if (!Account.signedIn) return
        scope.launch {
            runCatching { InnerTube.videoState(item.id) }
                .onSuccess { if (current?.id == item.id) { accountState = it; originalLike = it.like } }
                .onFailure { Log.w(TAG, "account state for ${item.id}", it) }
        }
    }

    /** Like count adjusted for the user's own change since the video opened. */
    val displayLikes: Long
        get() {
            val base = details?.likes ?: return -1
            val now = accountState?.like ?: return base
            return base + when {
                now == LikeStatus.Like && originalLike != LikeStatus.Like -> 1
                now != LikeStatus.Like && originalLike == LikeStatus.Like -> -1
                else -> 0
            }
        }

    fun setLike(target: LikeStatus) {
        val id = current?.id ?: return
        val before = accountState ?: return
        val next = if (before.like == target) LikeStatus.None else target
        accountState = before.copy(like = next)
        scope.launch {
            runCatching { InnerTube.setLike(id, next) }
                .onFailure { accountState = before; actionMessage = "Couldn't update your like." }
        }
    }

    fun toggleSubscribe() {
        val before = accountState ?: return
        val channelId = before.channelId
            ?: details?.item?.channelUrl?.substringAfter("/channel/", "")?.takeIf { it.startsWith("UC") }
            ?: return
        val subscribe = before.subscribed != true
        accountState = before.copy(subscribed = subscribe, channelId = channelId)
        scope.launch {
            runCatching { InnerTube.setSubscribed(channelId, subscribe) }
                .onFailure { accountState = before; actionMessage = "Couldn't update your subscription." }
        }
    }

    fun toggleWatchLater() {
        val id = current?.id ?: return
        val add = !inWatchLater
        inWatchLater = add
        scope.launch {
            runCatching { InnerTube.setInWatchLater(id, add) }
                .onSuccess { actionMessage = if (add) "Saved to Watch later" else "Removed from Watch later" }
                .onFailure { inWatchLater = !add; actionMessage = "Couldn't update Watch later."; Log.w(TAG, "watch later $id", it) }
        }
    }

    /** Long-press Save. Runs here, not in the sheet: closing the sheet would cancel its scope mid-request. */
    fun saveToWatchLater(video: VideoItem) {
        scope.launch {
            runCatching { InnerTube.setInWatchLater(video.id, true) }
                .onSuccess {
                    if (current?.id == video.id) inWatchLater = true
                    actionMessage = "Saved to Watch later"
                }
                .onFailure { actionMessage = "Couldn't save to Watch later."; Log.w(TAG, "watch later ${video.id}", it) }
        }
    }

    var selectedQuality by mutableStateOf<Int?>(null)
        private set

    /**
     * Switches quality (null = Auto). Updates ExoPlayer track selection parameters and
     * rebuilds the media source from the cached stream info at the exact position.
     */
    fun setQuality(height: Int?) {
        QualityPreference.set(height)
        selectedQuality = height
        val player = controller ?: return
        val item = details?.item ?: current ?: return
        val position = player.currentPosition
        val playWhenReady = player.playWhenReady

        // 1. Clear video size constraints so ExoPlayer accepts whatever single-resolution or adaptive stream is requested
        try {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearVideoSizeConstraints()
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting trackSelectionParameters", e)
        }

        // 2. Rebuild current media source for immediate stream resolution match
        replaceCurrent(player, item, position)
        player.playWhenReady = playWhenReady
        val info = StreamCache.get(item.id)
        if (info != null) {
            val picked = StreamSelection.pick(info)
            details = details?.copy(quality = picked.playedHeight?.let { "${it}p" })
        }
        actionMessage = if (height == null) "Quality: Auto" else "Quality: ${height}p"
    }

    /** Fresh links too: the cached ones may be exactly what YouTube refused. */
    fun retry() = current?.let { item -> StreamCache.invalidate(item.id); current = null; open(item) }

    fun togglePlay() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    /** Seeks by [deltaMs], clamped to the video; returns how far it actually moved. */
    fun seekBy(deltaMs: Long): Long {
        val player = controller ?: return 0L
        val from = player.currentPosition
        seekTo(from + deltaMs)
        return positionMs - from
    }

    fun seekTo(ms: Long) {
        val player = controller ?: return
        val max = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo(ms.coerceIn(0L, max))
        positionMs = player.currentPosition
    }

    fun beginScrubDragging() {
        if (scrubDragging) return
        val player = controller
        resumePlayAfterScrub = player?.isPlaying == true
        if (resumePlayAfterScrub) player?.pause()
        scrubDragging = true
    }

    /** Ends a scrub gesture; [seekMs] is set on release after a drag. */
    fun endScrubDragging(seekMs: Long? = null) {
        if (!scrubDragging) return
        val player = controller
        val resume = resumePlayAfterScrub
        resumePlayAfterScrub = false
        scrubDragging = false
        scrubPreviewMs = null
        if (seekMs != null) seekTo(seekMs)
        if (resume) player?.play()
    }

    /** Hold anywhere on the video while playing → 2× until release (does not change remembered speed). */
    fun beginHoldSpeed(): Boolean {
        val player = controller ?: return false
        if (scrubDragging || speedBeforeHold != null) return false
        if (!player.isPlaying) return false
        speedBeforeHold = player.playbackParameters.speed
        holdSpeedActive = true
        previewSpeed(2f)
        return true
    }

    fun endHoldSpeed() {
        val restore = speedBeforeHold ?: return
        speedBeforeHold = null
        holdSpeedActive = false
        previewSpeed(restore)
    }

    /** The ruler is moving: play at [value] now (remembering waits for [commitSpeed]). */
    fun previewSpeed(value: Float) {
        val player = controller ?: return
        val s = PlaybackSpeed.snap(value)
        if (player.playbackParameters.speed != s) player.setPlaybackSpeed(s)
        speed = s
    }

    /**
     * The ruler has landed on [value]. While remembering, it becomes the remembered speed —
     * unless it is 1× coming from another speed, which ends remembering (Derek: "until the
     * user resets it back to 1×").
     */
    fun commitSpeed(value: Float) {
        previewSpeed(value)
        val s = PlaybackSpeed.snap(value)
        if (!PlaybackSpeed.remember) return
        if (PlaybackSpeed.isNormal(s) && !PlaybackSpeed.isNormal(PlaybackSpeed.saved)) PlaybackSpeed.setRemember(false, 1f)
        else PlaybackSpeed.save(s)
    }

    fun toggleRememberSpeed() = PlaybackSpeed.setRemember(!PlaybackSpeed.remember, speed)

    /**
     * App left the screen: stop fetching video and keep only audio (saves data and battery);
     * back on screen, video resumes. Done through the player's track selection, so the
     * video stream simply stops loading.
     */
    fun setBackground(background: Boolean) {
        if (!com.thinker.flashtube.BackgroundPlayback.enabled && background) {
            controller?.pause()
        }
        wantBackground = background
        applyVideoTrack()
    }

    /**
     * What the screen wants, remembered even before the player connection exists. The
     * service keeps its track settings across screens, so a rebuilt screen (dark-mode switch,
     * process trim) that came back while disconnected left video off for good; the state is
     * now applied on every connect too.
     */
    private var wantBackground = false

    /** Pause video decoding while the app is backgrounded; restore it when foregrounded. */
    private fun applyVideoTrack() {
        val off = wantBackground
        val player = controller ?: return
        if (player.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_VIDEO) == off) return
        Log.i(TAG, if (off) "video off (background)" else "video on")
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, off)
            .build()
    }

    fun close() {
        loadJob?.cancel()
        mixLoadJob?.cancel()
        mixLoadGeneration++
        controller?.run { stop(); clearMediaItems() }
        queue = emptyList()
        mixQueueActive = false
        expanded = false
        fullscreen = false
        sheet = null
        current = null
        details = null
        error = null
        loading = false
    }

    /**
     * Called once the MediaController connects. Starts anything picked while connecting,
     * and if the service was already playing (app reopened mid-video), rebuilds the
     * mini-player from the session's current item.
     */
    internal fun onConnected() {
        syncFromPlayer()
        applyVideoTrack()
        controller?.videoSize?.let(::takeVideoSize)
        pendingStart?.let { pendingStart = null; startPlayback(it); return }
        val player = controller ?: return
        rebuildQueue()
        val playing = player.currentMediaItem ?: return
        if (current != null || playing.mediaId.isBlank()) return
        val meta = playing.mediaMetadata
        val item = VideoItem(
            id = playing.mediaId,
            title = meta.title?.toString().orEmpty(),
            channel = meta.artist?.toString().orEmpty(),
            channelUrl = null,
            thumbnail = meta.artworkUri?.toString(),
            avatar = null,
            durationSec = player.duration.takeIf { it > 0 }?.div(1000) ?: 0L,
            views = -1,
            uploaded = null,
        )
        current = item
        loadJob = scope.launch {
            runCatching { YouTube.details(item.id) }.onSuccess { details = it; current = it.item }
        }
    }

    internal fun syncFromPlayer() {
        val player = controller ?: return
        isPlaying = player.isPlaying
        // While the service refetches refused links the player sits idle; show the spinner.
        isBuffering = (player.playbackState == Player.STATE_BUFFERING && !player.isPlaying) || StreamRecovery.recovering == current?.id
        positionMs = player.currentPosition.coerceAtLeast(0L)
        bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
        durationMs = player.duration.takeIf { it > 0 } ?: ((current?.durationSec ?: 0L) * 1000L)
        speed = player.playbackParameters.speed
        loopEnabled = player.repeatMode == Player.REPEAT_MODE_ONE

        if (abLoopEnabled) {
            val a = pointA ?: 0L
            val b = pointB
            if (b != null && positionMs >= b) {
                seekTo(a)
            }
        }
    }

    internal val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncFromPlayer()
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) rebuildQueue()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                val p = controller ?: return
                if (p.repeatMode == Player.REPEAT_MODE_ONE) return

                val currentPos = p.currentPosition
                val duration = p.duration
                val nearEnd = duration > 0L && (duration - currentPos) <= 3_000L

                if (!nearEnd) {
                    retry()
                    return
                }

                if (p.currentMediaItemIndex + 1 < p.mediaItemCount) {
                    p.seekTo(p.currentMediaItemIndex + 1, 0L)
                    p.play()
                } else if (autoPlayEnabled) {
                    autoPlayNext()
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null) onPlayerMovedTo(mediaItem)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) = takeVideoSize(videoSize)

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            val vs = controller?.videoSize ?: return
            if (vs.width <= 0 || vs.height <= 0) return
            val w = (vs.width * vs.pixelWidthHeightRatio).roundToInt()
            describeFormat(w, vs.height)
        }

        override fun onRenderedFirstFrame() {
            Log.i("FlashTubeTime", "first frame +${android.os.SystemClock.elapsedRealtime() - openedAt}ms")
            firstFrameRendered = true
        }

        override fun onPlayerError(e: PlaybackException) {
            val status = (e.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
            Log.w(TAG, "playback error ${e.errorCodeName}${status?.let { " (HTTP $it)" } ?: ""} for ${current?.id}", e)
            // A refused or expired stream URL: the service is fetching fresh links (StreamRecoverer).
            if (e.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && StreamRecovery.recovering == current?.id) return
            error = when (e.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Lost the connection. Check your network and try again."
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "YouTube refused the stream. Try again."
                else -> "This video couldn't be played."
            }
        }
    }

    private fun takeVideoSize(videoSize: VideoSize) {
        if (videoSize.width <= 0 || videoSize.height <= 0) return
        videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
        aspectFromFrames = true
        // Auto switches resolution mid-play; the label follows the real frames.
        val w = (videoSize.width * videoSize.pixelWidthHeightRatio).roundToInt()
        describeFormat(w, videoSize.height)
    }

    /** Fills the quality label and the "Playing …" line from the frames actually decoded. */
    private fun describeFormat(width: Int, height: Int) {
        val tier = qualityTier(width, height)
        if (decodedHeight != tier) {
            Log.i("FlashTubeTime", "now ${tier}p (${width}×${height}) at +${android.os.SystemClock.elapsedRealtime() - openedAt}ms")
        }
        decodedHeight = tier
        val formats = controller?.currentTracks?.groups.orEmpty()
            .filter { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }
            .flatMap { g -> (0 until g.length).map { g.getTrackFormat(it) } }
        val format = formats.firstOrNull { it.height == height }
        if (format == null) {
            playingFormat = "${tier}p"
            return
        }
        run {
            val codec = when {
                format.codecs?.startsWith("avc") == true -> "H.264"
                format.codecs?.startsWith("vp9") == true || format.codecs?.startsWith("vp09") == true -> "VP9"
                format.codecs?.startsWith("av01") == true -> "AV1"
                else -> format.codecs ?: format.sampleMimeType ?: "?"
            }
            val auto = if (formats.size > 1) "Auto" else null
            val labelTier = qualityTier(format.width, format.height)
            playingFormat = listOfNotNull(auto, "${labelTier}p", codec, format.id?.substringAfterLast(":")?.let { "itag $it" }).joinToString(" · ")
        }
    }
}

@Composable
fun rememberPlayerState(): PlayerState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { PlayerState(scope) }
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.getOrNull()?.let { controller ->
                state.controller = controller
                controller.addListener(state.listener)
                state.onConnected()
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            state.controller?.removeListener(state.listener)
            MediaController.releaseFuture(future)
            state.controller = null
        }
    }
    // Position isn't evented. Keep the controller state fresh even before the first
    // isPlaying event, otherwise a delayed playback-start event can leave progress stale.
    LaunchedEffect(state.controller) {
        while (isActive && state.controller != null) {
            state.syncFromPlayer()
            delay(250)
        }
    }
    return state
}

enum class PlayerSheet { Quality, Comments, Queue, Settings, Subtitles, Description }

private const val TAG = "FlashTube"
