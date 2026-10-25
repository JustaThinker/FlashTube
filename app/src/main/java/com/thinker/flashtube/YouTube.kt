package com.thinker.flashtube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.localization.DateWrapper
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import kotlin.math.abs

@Immutable
data class VideoItem(
    val id: String,
    val title: String,
    val channel: String,
    val channelUrl: String?,
    val thumbnail: String?,
    val avatar: String?,
    val durationSec: Long,
    val views: Long,
    val uploaded: String?,
)

/** `UC…` from a `…/channel/UC…` URL, when InnerTube set [channelUrl]. */
fun VideoItem.channelId(): String? =
    channelUrl?.substringAfter("/channel/", "")?.substringBefore('?')?.takeIf { it.startsWith("UC") && it.length > 2 }

fun VideoItem.fromChannel(ref: ChannelRef): Boolean =
    channelId() == ref.id || channel.equals(ref.name, ignoreCase = true)

/** Channel row avatar: parsed URL, then [ChannelAvatars] lookup for playlist rows with no picture. */
fun VideoItem.feedAvatar(): String? =
    avatar?.let(::normalizeChannelAvatarUrl)
        ?: channelId()?.let { ChannelAvatars.known[it] }

@Immutable
data class VideoDetails(
    val item: VideoItem,
    val description: String,
    val likes: Long,
    val dislikes: Long = -1L,   // from returnyoutubedislike.com; -1 = not loaded yet
    val subscribers: Long,
    val related: List<VideoItem>,
    val playable: Boolean,
    /** Height of the chosen stream, e.g. "1080p". */
    val quality: String?,
    /** Every video height YouTube offers for this video, highest first. */
    val qualities: List<Int>,
    /** Width / height of the chosen stream, known before the first frame; null if unknown. */
    val aspect: Float? = null,
)

@Immutable
data class ChannelDetails(
    val url: String,
    val id: String,
    val name: String,
    val avatar: String?,
    val banner: String?,
    val subscribers: Long,
    /** Upload count when known from InnerTube; otherwise -1. */
    val videoCount: Long = -1,
    /** @handle without the @; from the channel URL or InnerTube. */
    val handle: String? = null,
    val verified: Boolean = false,
    val description: String,
    val videosTab: ListLinkHandler?,
)

fun ChannelDetails.withHeader(header: ChannelHeaderMeta): ChannelDetails = copy(
    handle = header.handle ?: handle,
    subscribers = if (header.subscribers > 0) header.subscribers else subscribers,
    videoCount = if (header.videoCount > 0) header.videoCount else videoCount,
    verified = header.verified || verified,
)

@Immutable
data class Comment(
    val id: String,
    /** The @handle (YouTube's comment data carries nothing else); also the reply @mention. */
    val author: String,
    /** The author's channel, for their display name ([ChannelNames]). */
    val channelId: String?,
    /** Display name when already known (the account's own comments). */
    val name: String? = null,
    val avatar: String?,
    val text: String,
    val likes: Int,
    val ago: String?,
    val pinned: Boolean,
    val hearted: Boolean,
    val replyCount: Int,
    /** Continuation for this thread's replies. */
    val repliesToken: String?,
    /** Liked by the signed-in account (from YouTube's toolbar state). */
    val liked: Boolean = false,
)

@Immutable
data class CommentsPage(val comments: List<Comment>, val total: Int, val disabled: Boolean, val next: String?)

/** Everything the app asks of YouTube goes through here. All calls are blocking-safe (IO). */
object YouTube {
    private val service get() = ServiceList.YouTube

    fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    /** Public trending/popular feed for logged-out users via NewPipe's YouTube Kiosk extractor. */
    suspend fun trending(): FeedPage = withContext(Dispatchers.IO) {
        val extractor = service.kioskList.defaultKioskExtractor
        val info = KioskInfo.getInfo(service, extractor.url)
        val videos = info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideoItem() }
        val next = if (info.hasNextPage()) info.nextPage.url else null
        FeedPage(videos, next)
    }

    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val handler = service.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        SearchInfo.getInfo(service, handler).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toVideoItem() }
    }

    suspend fun searchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) emptyList()
        else service.suggestionExtractor.suggestionList(q)
    }

    suspend fun details(id: String): VideoDetails = withContext(Dispatchers.IO) {
        val info = StreamCache.getOrLoad(id)
        val picked = StreamSelection.pick(info)
        VideoDetails(
            item = VideoItem(
                id = id,
                title = info.name,
                channel = info.uploaderName.orEmpty(),
                channelUrl = info.uploaderUrl,
                thumbnail = sharpenFeedThumbnail(info.thumbnails.bestUrl(), id),
                avatar = info.uploaderAvatars.bestUrl(targetWidth = 96),
                durationSec = info.duration,
                views = info.viewCount,
                uploaded = uploadedText(info.textualUploadDate, info.uploadDate),
            ),
            description = info.description?.let { d ->
                if (d.type == Description.HTML) HtmlCompat.fromHtml(d.content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                else d.content.trim()
            }.orEmpty(),
            likes = info.likeCount,
            subscribers = info.uploaderSubscriberCount,
            related = info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideoItem() },
            playable = picked.isPlayable,
            quality = picked.playedHeight?.let { "${it}p" },
            qualities = StreamSelection.heights(info),
            aspect = (if (picked.usesSeparate) picked.video else picked.muxed)
                ?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() / it.height },
        )
    }

    // ── Channels ──

    suspend fun channel(url: String): ChannelDetails = withContext(Dispatchers.IO) {
        val info = ChannelInfo.getInfo(service, url)
        val videosTab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) }
        ChannelDetails(
            url = url,
            id = info.id,
            name = info.name,
            avatar = info.avatars.bestUrl(targetWidth = 176),
            banner = info.banners.bestUrl(targetWidth = 1080),
            subscribers = info.subscriberCount,
            handle = handleFromChannelUrl(url),
            description = info.description.orEmpty(),
            videosTab = videosTab,
        )
    }

    private fun handleFromChannelUrl(url: String): String? {
        val path = url.substringAfter("youtube.com", url).trimStart('/')
        if (!path.startsWith("@")) return null
        return path.removePrefix("@").substringBefore('/').substringBefore('?').ifBlank { null }
    }

    /**
     * One page of a channel's uploads; [token] null for the first. Read through InnerTube
     * (not NewPipe) because only the raw page carries the "Members only" badge.
     */
    suspend fun channelVideos(channel: ChannelDetails, token: String?): FeedPage {
        val page = InnerTube.channelVideos(channel.id, token)
        // The uploads list doesn't repeat the channel on every item; fill it in.
        return page.copy(videos = page.videos.map {
            it.copy(channel = it.channel.ifBlank { channel.name }, avatar = it.avatar ?: channel.avatar, channelUrl = it.channelUrl ?: channel.url)
        })
    }

    private fun StreamInfoItem.toVideoItem(): VideoItem? {
        // Live and upcoming entries need HLS handling that isn't built yet.
        if (streamType != StreamType.VIDEO_STREAM) return null
        val id = runCatching { service.streamLHFactory.getId(url) }.getOrNull() ?: return null
        return VideoItem(
            id = id,
            title = name,
            channel = uploaderName.orEmpty(),
            channelUrl = uploaderUrl,
            thumbnail = sharpenFeedThumbnail(thumbnails.bestUrl(), id),
            avatar = uploaderAvatars.bestUrl(targetWidth = 96),
            durationSec = duration,
            views = viewCount,
            uploaded = uploadedText(textualUploadDate, uploadDate),
        )
    }

    /** The image closest to [targetWidth]: sharp enough for a full-width card, no 4K downloads. */
    private fun List<Image>.bestUrl(targetWidth: Int = 1080): String? =
        filter { it.width > 0 }.minByOrNull { abs(it.width - targetWidth) }?.url ?: firstOrNull()?.url

    /**
     * For brand-new uploads the extractor's "textual" date is a raw ISO timestamp
     * ("2026-10-04T04:00:22-07:00"); show it relative, like everywhere else.
     */
    private fun uploadedText(textual: String?, date: DateWrapper?): String? {
        if (textual != null && !ISO_DATE.containsMatchIn(textual)) return textual
        val then = date?.offsetDateTime()?.toInstant() ?: return textual?.substringBefore('T')
        return relativeTime(then)
    }

    private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}T")

    fun relativeTime(then: Instant, now: Instant = Instant.now()): String {
        val s = Duration.between(then, now).seconds.coerceAtLeast(0)
        fun ago(n: Long, unit: String) = "$n $unit${if (n == 1L) "" else "s"} ago"
        return when {
            s < 60 -> "just now"
            s < 3_600 -> ago(s / 60, "minute")
            s < 86_400 -> ago(s / 3_600, "hour")
            s < 7 * 86_400 -> ago(s / 86_400, "day")
            s < 30 * 86_400 -> ago(s / (7 * 86_400), "week")
            s < 365 * 86_400 -> ago(s / (30 * 86_400), "month")
            else -> ago(s / (365 * 86_400), "year")
        }
    }

    /** Turns extractor failures into a sentence a person can act on. */
    fun friendlyError(t: Throwable): String = when (t) {
        is AgeRestrictedContentException -> "This video is age-restricted and needs a YouTube account to watch."
        is GeographicRestrictionException -> "This video isn't available in your country."
        is org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException ->
            "YouTube is temporarily rate-limiting this request. Note: Logging into your account won't fix this — try again in a few minutes or switch networks."
        is org.schabi.newpipe.extractor.exceptions.PrivateContentException -> "This video is private."
        // Members-only videos (e.g. "Insider Edition") need a channel membership; FlashTube's
        // stream extraction is anonymous, so it can't play them even when you're a member.
        is ContentNotAvailableException ->
            if (t.message.orEmpty().contains("member", ignoreCase = true) || t is org.schabi.newpipe.extractor.exceptions.PaidContentException)
                "This is a members-only video. FlashTube can't play members-only videos yet."
            else "This video isn't available."
        is ReCaptchaException -> "YouTube is rate-limiting requests right now. Try again in a minute."
        is IOException -> "Couldn't reach YouTube. Check your connection."
        else -> "Something went wrong loading this. Try again."
    }
}

/**
 * Stream info is fetched once per video and shared by the UI (details) and the playback
 * service (media URLs). Both live in one process, so a map is enough. Stream URLs expire
 * after roughly six hours; entries older than [MAX_AGE_MS] are refetched.
 */
object StreamCache {
    private const val MAX_AGE_MS = 4 * 60 * 60 * 1000L
    private class Entry(val info: StreamInfo, val loadedAt: Long)
    private val entries = ConcurrentHashMap<String, Entry>()

    /**
     * Stream URLs are signed for the network they were fetched on and expire; a stale one
     * gets HTTP 403. Dropping it forces a fresh extraction on the next load.
     */
    fun invalidate(id: String) { entries.remove(id) }

    /** The phone switched networks: every cached URL is now bound to the old address. */
    fun invalidateAll() = entries.clear()

    fun get(id: String): StreamInfo? =
        entries[id]?.takeIf { System.currentTimeMillis() - it.loadedAt < MAX_AGE_MS }?.info

    /**
     * One extraction per video at a time: a prefetch (touch-down, top of Home) and the real
     * open share the same in-flight load instead of racing two full extractions.
     */
    private val inflight = ConcurrentHashMap<String, FutureTask<StreamInfo>>()

    fun getOrLoad(id: String): StreamInfo {
        get(id)?.let { return it }
        val mine = FutureTask {
            try {
                StreamInfo.getInfo(ServiceList.YouTube, YouTube.watchUrl(id)).also {
                    entries[id] = Entry(it, System.currentTimeMillis())
                    Net.noteMediaHost(StreamSelection.pick(it).let { p -> (p.video ?: p.muxed)?.content })
                }
            } catch (t: Throwable) {
                if (t is org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException) {
                    // Transparent retry: clear cache and retry extraction once
                    entries.remove(id)
                    StreamInfo.getInfo(ServiceList.YouTube, YouTube.watchUrl(id)).also {
                        entries[id] = Entry(it, System.currentTimeMillis())
                        Net.noteMediaHost(StreamSelection.pick(it).let { p -> (p.video ?: p.muxed)?.content })
                    }
                } else throw t
            }
        }
        val running = inflight.putIfAbsent(id, mine)
        val task = running ?: mine.also { it.run() }
        try {
            return task.get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        } finally {
            if (running == null) inflight.remove(id, mine)
        }
    }

    /** Background warm-up; one at a time so prefetches never crowd a real open. */
    private val prefetcher = Executors.newSingleThreadExecutor()

    fun prefetch(id: String) {
        if (get(id) != null || inflight.containsKey(id)) return
        prefetcher.execute { runCatching { getOrLoad(id) } }
    }
}

/**
 * The user's chosen video height, or null for Auto. Read by the playback service when it
 * builds a media source, so it lives outside Compose; persisted across launches.
 */
object QualityPreference {
    private const val KEY = "quality_height"
    private var prefs: SharedPreferences? = null

    @Volatile
    var height: Int? = null
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        height = prefs?.getInt(KEY, 0)?.takeIf { it > 0 }?.takeIf { it in QUALITY_LADDER }
    }

    fun set(value: Int?) {
        height = value
        prefs?.edit()?.putInt(KEY, value ?: 0)?.apply()
    }
}

/** Standard rungs shown in the quality sheet (matches YouTube's labels). */
val QUALITY_LADDER = intArrayOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 4320)

/**
 * Label tier for a stream: 16:9 and taller uses pixel height; ultrawide uses the equivalent
 * 16:9 height from width (3840×1634 → 2160p, 1920×818 → 1080p).
 */
fun qualityTier(width: Int, height: Int): Int {
    if (height <= 0) return 0
    val basis = if (width > 0 && width.toFloat() / height > 16f / 9f + 0.02f) {
        width * 9 / 16
    } else {
        height
    }
    return QUALITY_LADDER.minByOrNull { kotlin.math.abs(it - basis) } ?: basis
}

private fun VideoStream.tier() = qualityTier(width, height)

/** Chooses which of YouTube's many streams to play. */
object StreamSelection {
    /** Above this tier, phones gain little and pay a lot in data. */
    private const val MAX_TIER = 1080

    data class Picked(val video: VideoStream?, val audio: AudioStream?, val muxed: VideoStream?) {
        val isPlayable get() = (video != null && audio != null) || muxed != null
        /** What will actually play: the separate streams, or the combined fallback. */
        val usesSeparate get() = video != null && audio != null
        /** Quality label tier (1080p, not the ultrawide pixel height). */
        val playedHeight: Int? get() = (if (usesSeparate) video?.tier() else muxed?.tier())?.takeIf { it > 0 }
    }

    fun pick(info: StreamInfo): Picked {
        // H.264 first: every device decodes it in hardware. VP9 next, anything else last.
        fun formatRank(format: MediaFormat?) = when (format) {
            MediaFormat.MPEG_4 -> 0
            MediaFormat.WEBM -> 1
            else -> 2
        }
        val candidates = info.videoOnlyStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.height > 0 }
        val chosen = QualityPreference.height
        // A chosen tier plays exactly if offered, else the nearest below. Auto caps at 1080p.
        val ceiling = chosen ?: MAX_TIER
        val video = (candidates.filter { it.tier() <= ceiling }.ifEmpty { candidates })
            .sortedWith(
                compareBy<VideoStream> { if (it.tier() <= ceiling) ceiling - it.tier() else it.tier() }
                    .thenBy { formatRank(it.format) },
            )
            .firstOrNull()
        // The original language is preferred, but ANY audio track beats none: with no
        // accepted audio the player fell back to the 360p combined stream while the label
        // still said 1080p (the blurry-video bug, 2026-10-04).
        val audio = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .sortedWith(
                compareBy<AudioStream> { if (it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL) 0 else 1 }
                    .thenBy { if (it.format == MediaFormat.M4A) 0 else 1 }
                    .thenByDescending { it.averageBitrate },
            )
            .firstOrNull()
        val muxed = info.videoStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { it.height }
        return Picked(video, audio, muxed)
    }

    /**
     * The Auto ladder: one codec family (H.264 if offered, else VP9 — the player won't
     * switch between codecs), one stream per height (highest frame rate wins), up to 1080p.
     * Played as a single adaptive DASH set, so playback starts at whatever the connection
     * can sustain immediately and climbs, like YouTube.
     */
    fun autoLadder(info: StreamInfo): List<VideoStream> {
        val usable = info.videoOnlyStreams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.tier() in 1..MAX_TIER && it.itagItem != null
        }
        val family = usable.filter { it.codec?.startsWith("avc") == true }.ifEmpty { usable.filter { it.format == MediaFormat.WEBM } }
        return family.groupBy { it.tier() }
            .map { (_, sameTier) -> sameTier.maxBy { it.fps * 100_000L + it.bitrate } }
            .sortedBy { it.tier() }
    }

    fun heights(info: StreamInfo): List<Int> = info.videoOnlyStreams
        .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.height > 0 }
        .map { it.tier() }
        .distinct()
        .sortedDescending()
}

// ── Feed thumbnails ────────────────────────────────────────────────────────────
// InnerTube lockups only list hqdefault variants up to ~336 px (all named hqdefault.jpg).
// Full-bleed rows need the direct ytimg ladder instead.

private val YT_VIDEO_ID_IN_THUMB = Regex("""i\.ytimg\.com/vi/([\w-]{11})/""")

fun ytVideoIdFromThumbUrl(url: String): String? = YT_VIDEO_ID_IN_THUMB.find(url)?.groupValues?.getOrNull(1)

fun isLowRungYtimgUrl(url: String): Boolean {
    val path = url.substringBefore('?')
    return "/hqdefault" in path || "/mqdefault" in path || "/default.jpg" in path
}

fun ytimgThumbnailWidth(url: String): Int {
    Regex("=w(\\d+)").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
    return when {
        "maxresdefault" in url -> 1280
        "sddefault" in url -> 640
        "hqdefault" in url -> 480
        "mqdefault" in url -> 320
        else -> 0
    }
}

/** 640×480; always present for VOD (unlike maxresdefault, which can 200 as a tiny stub). */
fun feedThumbnailUrl(videoId: String): String = "https://i.ytimg.com/vi/$videoId/sddefault.jpg"

/** Picks a feed thumbnail URL sharp enough for edge-to-edge rows. */
fun sharpenFeedThumbnail(apiUrl: String?, videoId: String): String {
    val api = apiUrl?.trim()?.ifBlank { null }
    // InnerTube lockups only embed hqdefault/mqdefault (often ≤336 px despite the filename).
    if (api != null && isLowRungYtimgUrl(api)) return feedThumbnailUrl(videoId)
    if (api != null && ytimgThumbnailWidth(api) >= 720) return api
    if (api != null && ("googleusercontent.com" in api || "ggpht.com" in api)) {
        return upscaleGgphtThumbnail(api, 1280)
    }
    return feedThumbnailUrl(videoId)
}

/** Edge length from `yt3.ggpht.com/...=s88-...` channel avatars. */
fun channelAvatarEdgePx(url: String): Int =
    Regex("=s(\\d+)").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

/** `ytc` icons on ggpht load more reliably from googleusercontent with the channel cookie suffix. */
fun normalizeChannelAvatarUrl(url: String): String {
    if ("/ytc/" !in url || "googleusercontent.com" in url) return url
    val id = url.substringAfter("/ytc/").substringBefore("=").substringBefore("?").ifBlank { return url }
    return "https://yt3.googleusercontent.com/ytc/$id=s88-c-k-c0x00ffffff-no-rj"
}

/** InnerTube feed rows often ship =s68 avatars; the subscriptions rail uses much larger art. */
fun sharpenChannelAvatarUrl(url: String, minEdgePx: Int): String {
    val normalized = normalizeChannelAvatarUrl(url)
    val m = Regex("=s(\\d+)").find(normalized) ?: return normalized
    val current = m.groupValues[1].toIntOrNull() ?: return normalized
    if ("/ytc/" in normalized && current > 96 && "googleusercontent.com" !in normalized) {
        return normalized.replace(Regex("=s\\d+"), "=s96")
    }
    if (current >= minEdgePx) return normalized
    // Legacy `ytc/` icons 404 above ~96; googleusercontent channel art can go much higher.
    val ceiling = if ("/ytc/" in normalized && "googleusercontent.com" !in normalized) 96 else 800
    val edge = minOf(minEdgePx, ceiling).coerceAtLeast(current)
    return normalized.replace(Regex("=s\\d+"), "=s$edge")
}

internal fun upscaleGgphtThumbnail(url: String, targetWidth: Int): String {
    val current = ytimgThumbnailWidth(url)
    if (current >= targetWidth) return url
    val height = targetWidth * 9 / 16
    return if ("=w" in url) {
        url.replace(Regex("=w\\d+-h\\d+"), "=w$targetWidth-h$height")
    } else {
        "$url=w$targetWidth-h$height"
    }
}

// ── Return YouTube Dislike ────────────────────────────────────────────────────

/**
 * Fetches the real dislike count from returnyoutubedislike.com (RYD).
 * Returns -1 on failure so the caller can hide the field gracefully.
 */
fun fetchDislikeCount(videoId: String): Long {
    return runCatching {
        val req = okhttp3.Request.Builder()
            .url("https://returnyoutubedislike.com/votes?videoId=$videoId")
            .header("User-Agent", Net.USER_AGENT)
            .build()
        Net.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@runCatching -1L
            org.json.JSONObject(r.body?.string().orEmpty()).optLong("dislikes", -1L)
        }
    }.getOrElse { -1L }
}
