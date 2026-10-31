package com.thinker.flashtube

import android.content.Context
import com.thinker.flashtube.data.ContentRegion
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
    val localUri: String? = null,
    val collabChannels: List<ChannelRef> = emptyList(),
    /** Non-null for a YouTube-generated radio/mix card; [id] remains its playable first video. */
    val mixPlaylistId: String? = null,
    /** Recommendation feedback tokens included in the server-rendered overflow menu. */
    val feedbackTokens: Map<String, String> = emptyMap(),
    /** A YouTube community post surfaced inline in Home, separate from video/mix cards. */
    val feedPost: CommunityPost? = null,
    /** Live status provided by the YouTube feed or player response. */
    val isLive: Boolean = false,
)

/** Stable identity for mixed Home feed rows (a mix can share its opening video with another mix). */
val VideoItem.feedKey: String
    get() = feedPost?.let { "post:${it.id}" }
        ?: mixPlaylistId?.let { "mix:$it" }
        ?: id

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
    val relatedToken: String? = null,
    val playable: Boolean,
    /** Height of the chosen stream, e.g. "1080p". */
    val quality: String?,
    /** Every video height YouTube offers for this video, highest first. */
    val qualities: List<Int>,
    /** Width / height of the chosen stream, known before the first frame; null if unknown. */
    val aspect: Float? = null,
    /** YouTube's Music category; songs should not keep a resume position. */
    val isMusicVideo: Boolean = false,
)

data class RelatedVideoDetails(
    val videos: List<VideoItem>,
    val continuation: String?,
    val collaborators: List<ChannelRef>,
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
    val isLive: Boolean = false,
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
    val imageUrl: String? = null,
)

@Immutable
data class CommentsPage(
    val comments: List<Comment>,
    val total: Int,
    val disabled: Boolean,
    val next: String?,
    val liveSendParams: String? = null,
    val liveTimeoutMs: Long = 10_000L,
    val liveMaxMessageLength: Int = 200,
    val liveRestriction: String? = null,
    val removedIds: Set<String> = emptySet(),
    val viewerCount: String? = null,
)

/** Everything the app asks of YouTube goes through here. All calls are blocking-safe (IO). */
object YouTube {
    private val service get() = ServiceList.YouTube

    fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    /** Public trending/popular feed for logged-out users via NewPipe's YouTube Kiosk extractor. */
    suspend fun trending(): FeedPage = withContext(Dispatchers.IO) {
        val loc = org.schabi.newpipe.extractor.localization.Localization(ContentRegion.currentLanguageCode, ContentRegion.currentRegionCode)
        val extractor = service.kioskList.getDefaultKioskExtractor(null, loc)
        val info = KioskInfo.getInfo(extractor)
        val videos = info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideoItem() }
        val next = if (info.hasNextPage()) info.nextPage.url else null
        FeedPage(videos, next)
    }

    suspend fun search(query: String, continuation: String? = null): FeedPage = withContext(Dispatchers.IO) {
        InnerTube.search(query, continuation)
    }

    suspend fun nextRelated(continuation: String): FeedPage = withContext(Dispatchers.IO) {
        InnerTube.parseFeed(InnerTube.postAny("next", JSONObject().put("continuation", continuation)))
    }

    suspend fun searchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) emptyList()
        else service.suggestionExtractor.suggestionList(q)
    }

    suspend fun details(id: String, includeRelated: Boolean = true): VideoDetails = withContext(Dispatchers.IO) {
        val info = StreamCache.getOrLoad(id)
        val picked = StreamSelection.pick(info)
        val related = if (includeRelated) runCatching { related(id) }.getOrNull() else null
        val relatedVideos = info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideoItem() }

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
                collabChannels = related?.collaborators.orEmpty(),
                isLive = info.streamType == StreamType.LIVE_STREAM,
            ),
            description = info.description?.let { d ->
                if (d.type == Description.HTML) HtmlCompat.fromHtml(d.content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                else d.content.trim()
            }.orEmpty(),
            likes = info.likeCount,
            subscribers = info.uploaderSubscriberCount,
            related = related?.videos?.takeIf { it.isNotEmpty() } ?: relatedVideos,
            relatedToken = related?.continuation,
            playable = picked.isPlayable,
            quality = picked.playedHeight?.let { "${it}p" },
            qualities = StreamSelection.heights(info),
            aspect = (if (picked.usesSeparate) picked.video else picked.muxed)
                ?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() / it.height },
            isMusicVideo = info.category.equals("Music", ignoreCase = true),
        )
    }

    /** Loads recommendation/collaboration metadata separately from the critical stream lookup. */
    suspend fun related(id: String): RelatedVideoDetails = withContext(Dispatchers.IO) {
        val json = InnerTube.postAny("next", JSONObject().put("videoId", id))
        val feed = runCatching { InnerTube.parseFeed(json) }.getOrNull()
        val collaborators = runCatching { InnerTube.parseCollabChannelsFromNext(json) }.getOrDefault(emptyList())
        RelatedVideoDetails(feed?.videos.orEmpty(), feed?.next, collaborators)
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
    suspend fun channelVideos(channel: ChannelDetails, params: String? = InnerTube.CHANNEL_VIDEOS_TAB, token: String? = null): FeedPage {
        val page = InnerTube.channelVideos(channel.id, params, token)
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
        is YouTubeRateLimitException -> t.message ?: "YouTube is temporarily rate-limiting requests. Please wait before retrying."
        is AgeRestrictedContentException -> "This video is age-restricted and needs a YouTube account to watch."
        is GeographicRestrictionException -> "This video isn't available in your country."
        is org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException ->
            "YouTube is temporarily rate-limiting requests. Please wait a few minutes before retrying."
        is org.schabi.newpipe.extractor.exceptions.PrivateContentException -> "This video is private."
        // Members-only videos (e.g. "Insider Edition") need a channel membership; FlashTube's
        // stream extraction is anonymous, so it can't play them even when you're a member.
        is ContentNotAvailableException ->
            if (t.message.orEmpty().contains("member", ignoreCase = true) || t is org.schabi.newpipe.extractor.exceptions.PaidContentException)
                "This is a members-only video. FlashTube can't play members-only videos yet."
            else "This video isn't available."
        is ReCaptchaException -> "YouTube is temporarily rate-limiting requests. Please wait a few minutes before retrying."
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
    fun invalidate(id: String) {
        entries.remove(id)
        com.thinker.flashtube.data.VideoStreamResolutionCache.invalidate(id)
    }

    /** The phone switched networks: every cached URL is now bound to the old address. */
    fun invalidateAll() {
        entries.clear()
        com.thinker.flashtube.data.VideoStreamResolutionCache.clear()
    }

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
        com.thinker.flashtube.data.VideoStreamResolver.prefetch(id)
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
    private const val KEY_WIFI_DEFAULT = "quality_wifi_default"
    private const val KEY_MOBILE_DEFAULT = "quality_mobile_default"
    private const val KEY_PREFER_HDR = "quality_prefer_hdr"

    private var prefs: SharedPreferences? = null

    @Volatile
    var height: Int? = null
        private set

    @Volatile
    var wifiDefaultHeight: Int? = null
        private set

    @Volatile
    var mobileDefaultHeight: Int? = null
        private set

    @Volatile
    var preferHdr: Boolean = false
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        height = prefs?.getInt(KEY, 0)?.takeIf { it in QUALITY_LADDER }
        wifiDefaultHeight = prefs?.getInt(KEY_WIFI_DEFAULT, 0)?.takeIf { it in QUALITY_LADDER }
        mobileDefaultHeight = prefs?.getInt(KEY_MOBILE_DEFAULT, 0)?.takeIf { it in QUALITY_LADDER }
        preferHdr = prefs?.getBoolean(KEY_PREFER_HDR, false) ?: false
    }

    fun set(value: Int?) {
        val supported = value?.takeIf { it in QUALITY_LADDER }
        height = supported
        prefs?.edit()?.putInt(KEY, supported ?: 0)?.apply()
    }

    fun setWifiDefault(value: Int?) {
        val supported = value?.takeIf { it in QUALITY_LADDER }
        wifiDefaultHeight = supported
        prefs?.edit()?.putInt(KEY_WIFI_DEFAULT, supported ?: 0)?.apply()
    }

    fun setMobileDefault(value: Int?) {
        val supported = value?.takeIf { it in QUALITY_LADDER }
        mobileDefaultHeight = supported
        prefs?.edit()?.putInt(KEY_MOBILE_DEFAULT, supported ?: 0)?.apply()
    }

    fun setPreferHdr(enabled: Boolean) {
        preferHdr = enabled
        prefs?.edit()?.putBoolean(KEY_PREFER_HDR, enabled)?.apply()
    }

    fun isNetworkMetered(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            cm?.isActiveNetworkMetered ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun getEffectiveTargetHeight(context: Context): Int? {
        if (height != null) return height
        val metered = isNetworkMetered(context)
        return if (metered) mobileDefaultHeight else wifiDefaultHeight
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
    /** Highest available tier supported (up to 8K 4320p). */
    private const val MAX_TIER = 4320

    data class Picked(val video: VideoStream?, val audio: AudioStream?, val muxed: VideoStream?, val isLive: Boolean = false) {
        val isPlayable get() = (video != null && audio != null) || muxed != null || isLive
        /** What will actually play: the separate streams, or the combined fallback. */
        val usesSeparate get() = video != null && audio != null
        /** Quality label tier (1080p, not the ultrawide pixel height). */
        val playedHeight: Int? get() = (if (usesSeparate) video?.tier() else muxed?.tier())?.takeIf { it > 0 }
    }

    fun pick(info: StreamInfo): Picked {
        if (!info.hlsUrl.isNullOrBlank() || info.streamType == StreamType.LIVE_STREAM) {
            return Picked(video = null, audio = null, muxed = null, isLive = true)
        }
        val candidates = info.videoOnlyStreams
            .filter { it.isUrl && (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || it.deliveryMethod == DeliveryMethod.DASH) && it.height > 0 }
        val chosen = QualityPreference.height
        val video = if (chosen != null) {
            candidates.filter { it.tier() == chosen || it.height == chosen }
                .maxByOrNull { it.fps * 100_000L + it.bitrate }
                ?: candidates.filter { it.tier() <= chosen || it.height <= chosen }
                    .maxByOrNull { it.height }
                ?: candidates.maxByOrNull { it.height }
        } else {
            candidates.sortedWith(
                compareByDescending<VideoStream> { it.tier() }
                    .thenByDescending { it.fps * 100_000L + it.bitrate },
            ).firstOrNull()
        }
        val audio = info.audioStreams
            .filter { it.isUrl && (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || it.deliveryMethod == DeliveryMethod.DASH) }
            .sortedWith(
                compareBy<AudioStream> { if (it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL) 0 else 1 }
                    .thenBy { if (it.format == MediaFormat.M4A) 0 else 1 }
                    .thenByDescending { it.averageBitrate },
            )
            .firstOrNull()
        val muxed = info.videoStreams
            .filter { it.isUrl && (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || it.deliveryMethod == DeliveryMethod.DASH) }
            .maxByOrNull { it.height }
        return Picked(video, audio, muxed)
    }

    /**
     * The Auto ladder: prefer WEBM / VP9 / AV1 (which support up to 4K / 8K, unlike AVC
     * which caps at 1080p), one stream per height tier (highest frame rate wins).
     * Played as a single adaptive DASH set, so playback starts at whatever the connection
     * can sustain immediately and climbs, like YouTube.
     */
    fun autoLadder(info: StreamInfo): List<VideoStream> {
        val usable = info.videoOnlyStreams.filter {
            it.isUrl && (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || it.deliveryMethod == DeliveryMethod.DASH) && it.tier() in 1..MAX_TIER && it.itagItem != null
        }
        val family = usable.filter { it.format == MediaFormat.WEBM || it.codec?.contains("vp") == true || it.codec?.contains("av01") == true }
            .ifEmpty { usable.filter { it.codec?.startsWith("avc") == true } }
            .ifEmpty { usable }
        return family.groupBy { it.tier() }
            .map { (_, sameTier) -> sameTier.maxBy { it.fps * 100_000L + it.bitrate } }
            .sortedBy { it.tier() }
    }

    fun heights(info: StreamInfo): List<Int> = (info.videoOnlyStreams + info.videoStreams)
        .filter { it.isUrl && (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || it.deliveryMethod == DeliveryMethod.DASH) && it.height > 0 }
        .map { it.tier() }
        .filter { it in 1..MAX_TIER }
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
    val isYtc = "/ytc/" in url
    val rawEdge = Regex("=s(\\d+)").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    val normalized = normalizeChannelAvatarUrl(url)
    val m = Regex("=s(\\d+)").find(normalized) ?: return normalized
    val current = m.groupValues[1].toIntOrNull() ?: return normalized
    if (isYtc && (rawEdge > 96 || current > 96)) {
        return normalized.replace(Regex("=s\\d+"), "=s96")
    }
    if (isYtc) {
        return normalized.replace(Regex("=s\\d+"), "=s96")
    }
    if (current >= minEdgePx) return normalized
    val edge = minOf(minEdgePx, 800).coerceAtLeast(current)
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
 * Fetches the real dislike count from returnyoutubedislikeapi.com (RYD).
 * Returns -1 on failure so the caller can handle gracefully.
 */
fun fetchDislikeCount(videoId: String): Long {
    return runCatching {
        val req = okhttp3.Request.Builder()
            .url("https://returnyoutubedislikeapi.com/votes?videoId=$videoId")
            .header("User-Agent", Net.USER_AGENT)
            .build()
        Net.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@runCatching 0L
            val json = org.json.JSONObject(r.body?.string().orEmpty())
            json.optLong("dislikes", 0L)
        }
    }.getOrElse { 0L }
}
