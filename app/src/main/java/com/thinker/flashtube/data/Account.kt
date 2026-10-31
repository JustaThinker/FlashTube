package com.thinker.flashtube

import android.content.Context
import com.thinker.flashtube.data.ContentRegion
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import kotlin.random.Random

@Immutable
data class Profile(val name: String, val handle: String?, val avatar: String?)

@Immutable
data class PlaylistRef(val id: String, val title: String, val thumbnail: String?, val subtitle: String?, val count: Int = 0) {
    // RD* is YouTube's radio/mix playlist namespace. FEplaylist_* is a browse route,
    // not a radio playlist ID, and treating it as a Mix opens ordinary playlists wrong.
    val isMix get() = id.startsWith("RD")
}

@Immutable
data class ChannelRef(
    val id: String,
    val name: String,
    val avatar: String?,
    val isLive: Boolean = false,
    val handle: String? = null,
    val subscriberCountText: String? = null,
    val verified: Boolean = false,
) {
    val url: String get() = "https://www.youtube.com/channel/$id"
}

/** A Home filter supplied by YouTube itself, including the browse route used when selected. */
@Immutable
data class FeedChip(
    val title: String,
    val browseId: String?,
    val params: String?,
    val navigationEndpoint: JSONObject? = null,
    val key: String = listOfNotNull(browseId, params, title).joinToString("|"),
    /** YouTube Home category chips currently route through continuationCommand. */
    val continuation: String? = null,
)

@Immutable
data class ChannelShelf(
    val title: String,
    val videos: List<VideoItem>,
    val isShorts: Boolean = false,
)

/** One page of a feed plus its server-provided filters and the token for the next page. */
@Immutable
data class FeedPage(
    val videos: List<VideoItem>,
    val next: String?,
    val channelHeader: ChannelHeaderMeta? = null,
    val chips: List<FeedChip> = emptyList(),
    val channelCard: ChannelDetails? = null,
)

@Immutable
data class ChannelHeaderMeta(val handle: String?, val subscribers: Long, val videoCount: Long, val verified: Boolean = false)

@Immutable
data class PollOption(val text: String, val votePercentage: String?, val imageUrl: String? = null)

@Immutable
data class CommunityPost(
    val id: String,
    val author: String,
    val authorAvatar: String?,
    val publishedTime: String?,
    val text: String,
    val images: List<String> = emptyList(),
    val pollOptions: List<PollOption> = emptyList(),
    val voteCount: String? = null,
    val likeCount: Int = -1,
    val dislikeCount: Int = -1,
    val liked: Boolean = false,
    val channelId: String? = null,
    val detailParams: String? = null,
    val commentCountText: String? = null,
    val imageAspectRatio: Float? = null,
)

enum class LikeStatus { Like, Dislike, None }

@Immutable
data class VideoAccountState(val like: LikeStatus, val subscribed: Boolean?, val channelId: String?)

class NotSignedInException : IOException("Not signed in")

@Immutable
data class StoredAccount(
    val id: String,
    val profile: Profile,
    val cookieHeader: String,
    val clientVersion: String = "2.20260901.00.00",
    val visitorData: String? = null,
)

/**
 * The signed-in Google session (user chose full sync, 2026-10-04).
 *
 * Sign-in happens on Google's own page in a WebView; afterwards only the youtube.com
 * cookies are kept, encrypted with a Keystore key ([SecureStore]). Requests are authorized
 * the way youtube.com itself does it (SAPISIDHASH). Cookies YouTube rotates in responses
 * are merged back in so the session stays alive.
 */
object Account {
    var accounts by mutableStateOf<List<StoredAccount>>(emptyList())
        private set

    var profile by mutableStateOf<Profile?>(null)
        private set
    val signedIn get() = profile != null

    private var activeAccountId: String? = null
    /** Stable key for account-scoped server data caches; never contains the auth cookies. */
    val feedIdentity: String get() = activeAccountId ?: "anonymous"
    private var store: SecureStore? = null
    private val cookies = linkedMapOf<String, String>()
    private var clientVersion = FALLBACK_CLIENT_VERSION
    private var visitorData: String? = null

    fun init(context: Context) {
        val s = SecureStore(context, "account").also { store = it }
        val accountsJson = s.get(KEY_ACCOUNTS_JSON)
        val loadedAccounts = mutableListOf<StoredAccount>()

        if (!accountsJson.isNullOrBlank()) {
            try {
                val array = JSONArray(accountsJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val acc = StoredAccount(
                        id = obj.getString("id"),
                        profile = Profile(
                            name = obj.getString("name"),
                            handle = obj.optString("handle").ifBlank { null },
                            avatar = obj.optString("avatar").ifBlank { null },
                        ),
                        cookieHeader = obj.getString("cookieHeader"),
                        clientVersion = obj.optString("clientVersion", FALLBACK_CLIENT_VERSION),
                        visitorData = obj.optString("visitorData").ifBlank { null },
                    )
                    loadedAccounts.add(acc)
                }
            } catch (e: Exception) {
                android.util.Log.e("Account", "Error loading multi-accounts", e)
            }
        }

        // Migration fallback for existing single-account installs
        if (loadedAccounts.isEmpty()) {
            val name = s.get(KEY_NAME)
            val cookieHeader = s.get(KEY_COOKIES)
            if (name != null && cookieHeader != null && cookieHeader.contains("SAPISID")) {
                val p = Profile(name, s.get(KEY_HANDLE), s.get(KEY_AVATAR))
                val id = p.handle ?: p.name
                val legacyAcc = StoredAccount(
                    id = id,
                    profile = p,
                    cookieHeader = cookieHeader,
                    clientVersion = s.get(KEY_CLIENT_VERSION) ?: FALLBACK_CLIENT_VERSION,
                    visitorData = s.get(KEY_VISITOR),
                )
                loadedAccounts.add(legacyAcc)
            }
        }

        accounts = loadedAccounts
        activeAccountId = s.get(KEY_ACTIVE_ID) ?: loadedAccounts.firstOrNull()?.id

        val active = loadedAccounts.find { it.id == activeAccountId } ?: loadedAccounts.firstOrNull()
        if (active != null) {
            applyActiveAccount(active)
        }
    }

    private fun applyActiveAccount(acc: StoredAccount) {
        activeAccountId = acc.id
        profile = acc.profile
        clientVersion = acc.clientVersion
        visitorData = acc.visitorData
        synchronized(cookies) {
            cookies.clear()
            parseCookieHeader(acc.cookieHeader, cookies)
        }
        store?.put(KEY_ACTIVE_ID, acc.id)
    }

    fun switchAccount(acc: StoredAccount) {
        if (activeAccountId == acc.id) return
        applyActiveAccount(acc)
        StreamCache.invalidateAll()
    }

    /** True once the WebView's cookies prove a completed YouTube sign-in. */
    fun looksSignedIn(cookieHeader: String?): Boolean {
        val names = cookieHeader.orEmpty().split(';').map { it.substringBefore('=').trim() }.toSet()
        return "SAPISID" in names && "LOGIN_INFO" in names
    }

    /** Takes over the WebView session: stores the cookies, then loads the account's name. */
    suspend fun completeSignIn(cookieHeader: String) = withContext(Dispatchers.IO) {
        synchronized(cookies) {
            cookies.clear()
            parseCookieHeader(cookieHeader, cookies)
        }
        refreshClientConfig()
        val menu = InnerTube.post("account/account_menu", JSONObject())
        var found: Profile? = null
        menu.walk { key, obj ->
            if (key == "activeAccountHeaderRenderer" && found == null) {
                found = Profile(
                    name = obj.text("accountName") ?: "YouTube account",
                    handle = obj.text("channelHandle"),
                    avatar = obj.optJSONObject("accountPhoto")?.thumbnails()?.lastOrNull(),
                )
            }
        }
        val p = found ?: Profile("YouTube account", null, null)
        val id = p.handle ?: p.name

        val newAcc = StoredAccount(
            id = id,
            profile = p,
            cookieHeader = cookieHeader(),
            clientVersion = clientVersion,
            visitorData = visitorData,
        )

        val updated = accounts.filter { it.id != id }.toMutableList()
        updated.add(0, newAcc)

        withContext(Dispatchers.Main) {
            accounts = updated
            applyActiveAccount(newAcc)
        }
        saveAccountsList()
    }

    fun removeAccount(id: String) {
        val updated = accounts.filter { it.id != id }
        accounts = updated
        saveAccountsList()

        if (activeAccountId == id) {
            val nextAcc = updated.firstOrNull()
            if (nextAcc != null) {
                switchAccount(nextAcc)
            } else {
                signOutAll()
            }
        }
    }

    fun signOutAll() {
        synchronized(cookies) { cookies.clear() }
        accounts = emptyList()
        activeAccountId = null
        profile = null
        store?.clear()
        android.webkit.CookieManager.getInstance().apply { removeAllCookies(null); flush() }
    }

    fun signOut() {
        activeAccountId?.let { removeAccount(it) } ?: signOutAll()
    }

    private fun saveAccountsList() {
        try {
            val array = JSONArray()
            accounts.forEach { acc ->
                val obj = JSONObject().apply {
                    put("id", acc.id)
                    put("name", acc.profile.name)
                    put("handle", acc.profile.handle ?: "")
                    put("avatar", acc.profile.avatar ?: "")
                    put("cookieHeader", acc.cookieHeader)
                    put("clientVersion", acc.clientVersion)
                    put("visitorData", acc.visitorData ?: "")
                }
                array.put(obj)
            }
            store?.put(KEY_ACCOUNTS_JSON, array.toString())
        } catch (e: Exception) {
            android.util.Log.e("Account", "Error saving accounts list", e)
        }
    }

    /** youtube.com embeds the current web client version; requests with a stale one get odd results. */
    private fun refreshClientConfig() {
        runCatching {
            val request = Request.Builder().url("https://www.youtube.com/?hl=en")
                .header("User-Agent", Net.USER_AGENT)
                .header("Cookie", cookieHeader())
                .build()
            Net.client.newCall(request).execute().use { r ->
                absorbSetCookies(r.headers("Set-Cookie"))
                val html = r.body?.string().orEmpty()
                Regex("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)?.let { clientVersion = it }
                Regex("\"VISITOR_DATA\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)?.let { visitorData = it }
            }
            store?.put(KEY_CLIENT_VERSION, clientVersion)
            store?.put(KEY_VISITOR, visitorData)
        }
    }

    internal fun clientVersion() = clientVersion
    internal fun visitorData() = visitorData
    internal fun clearVisitorData() { visitorData = null }

    internal fun cookieHeader(): String = synchronized(cookies) { cookies.entries.joinToString("; ") { "${it.key}=${it.value}" } }

    /** youtube.com's own request signature: SHA-1 of "timestamp SAPISID origin". */
    internal fun authorization(origin: String = ORIGIN): String? {
        val ts = System.currentTimeMillis() / 1000
        fun hash(secret: String): String {
            val digest = MessageDigest.getInstance("SHA-1").digest("$ts $secret $origin".toByteArray())
            return "${ts}_" + digest.joinToString("") { "%02x".format(it) }
        }
        val (sapisid, p1, p3) = synchronized(cookies) {
            Triple(cookies["SAPISID"] ?: cookies["__Secure-3PAPISID"], cookies["__Secure-1PAPISID"], cookies["__Secure-3PAPISID"])
        }
        sapisid ?: return null
        return buildString {
            append("SAPISIDHASH ").append(hash(sapisid))
            p1?.let { append(" SAPISID1PHASH ").append(hash(it)) }
            p3?.let { append(" SAPISID3PHASH ").append(hash(it)) }
        }
    }

    /** Merges rotated cookies from a response (Google refreshes __Secure-*PSIDTS regularly). */
    internal fun absorbSetCookies(setCookies: List<String>) {
        if (setCookies.isEmpty()) return
        var changed = false
        synchronized(cookies) {
            setCookies.forEach { header ->
                val pair = header.substringBefore(';')
                val name = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=', "").trim()
                if (name.isEmpty()) return@forEach
                if (cookies[name] != value) { cookies[name] = value; changed = true }
            }
        }
        if (changed) persistCookies()
    }

    private fun persistCookies() = store?.put(KEY_COOKIES, cookieHeader())

    private fun parseCookieHeader(header: String, into: MutableMap<String, String>) {
        header.split(';').forEach { part ->
            val name = part.substringBefore('=').trim()
            if (name.isNotEmpty()) into[name] = part.substringAfter('=', "").trim()
        }
    }

    const val ORIGIN = "https://www.youtube.com"
    private const val FALLBACK_CLIENT_VERSION = "2.20260901.00.00"
    private const val KEY_COOKIES = "cookies"
    private const val KEY_CLIENT_VERSION = "client_version"
    private const val KEY_VISITOR = "visitor"
    private const val KEY_NAME = "name"
    private const val KEY_HANDLE = "handle"
    private const val KEY_AVATAR = "avatar"
    private const val KEY_ACCOUNTS_JSON = "accounts_json"
    private const val KEY_ACTIVE_ID = "active_account_id"
}

/**
 * YouTube's internal web API ("InnerTube"), signed in. Reads: home, subscriptions,
 * channels, history, playlists. Writes: like/dislike, subscribe, Watch later, watch history
 * and watch time.
 */
object InnerTube {
    private const val BASE = "https://www.youtube.com/youtubei/v1/"
    private val JSON_TYPE = "application/json".toMediaType()

    private fun regionCode(): String = ContentRegion.currentRegionCode

    private fun languageCode(): String = ContentRegion.currentLanguageCode

    private fun cookieHeaderWithRegion(baseCookies: String?): String {
        val gl = regionCode()
        val hl = languageCode()
        val prefKeyValue = "f6=40000000&tz=UTC&gl=$gl&hl=$hl"
        if (baseCookies.isNullOrBlank()) return "PREF=$prefKeyValue"
        if (!baseCookies.contains("PREF=")) return "$baseCookies; PREF=$prefKeyValue"

        return baseCookies.split("; ").joinToString("; ") { part ->
            if (part.startsWith("PREF=")) {
                var prefVal = part.substringAfter("PREF=")
                if (!prefVal.contains("gl=")) prefVal += "&gl=$gl" else prefVal = prefVal.replace(Regex("gl=[^&]+"), "gl=$gl")
                if (!prefVal.contains("hl=")) prefVal += "&hl=$hl" else prefVal = prefVal.replace(Regex("hl=[^&]+"), "hl=$hl")
                "PREF=$prefVal"
            } else part
        }
    }

    /** Signed in when there's a session, otherwise anonymous (public pages like channels). */
    internal fun postAny(endpoint: String, body: JSONObject): JSONObject =
        if (Account.signedIn) post(endpoint, body) else post(endpoint, body, anonymous = true)

    internal fun post(endpoint: String, body: JSONObject, anonymous: Boolean = false): JSONObject {
        val auth = if (anonymous) null else Account.authorization() ?: throw NotSignedInException()
        val clientObj = JSONObject()
            .put("clientName", "WEB")
            .put("clientVersion", Account.clientVersion())
            .put("hl", languageCode())
            .put("gl", regionCode())
            .put("utcOffsetMinutes", 0)

        val userObj = JSONObject()
            .put("lockedSafetyMode", false)

        if (anonymous) {
            Account.visitorData()?.let { clientObj.put("visitorData", it) }
        }

        body.put("context", JSONObject().put("client", clientObj).put("user", userObj))

        val baseCookies = if (auth != null) Account.cookieHeader() else null
        val cookieHeaderVal = cookieHeaderWithRegion(baseCookies)

        val request = Request.Builder()
            .url("$BASE$endpoint?prettyPrint=false&gl=${regionCode()}&hl=${languageCode()}")
            .post(body.toString().toRequestBody(JSON_TYPE))
            .header("User-Agent", Net.USER_AGENT)
            .header("Accept-Language", "${languageCode()}-${regionCode()},${languageCode()};q=0.9")
            .header("Cookie", cookieHeaderVal)
            .apply { if (auth != null) header("Authorization", auth) }
            .header("Origin", Account.ORIGIN)
            .header("X-Origin", Account.ORIGIN)
            .header("Referer", Account.ORIGIN + "/")
            .header("X-Goog-AuthUser", "0")
            .header("X-Youtube-Client-Name", "1")
            .header("X-Youtube-Client-Version", Account.clientVersion())
            .apply {
                if (anonymous) {
                    Account.visitorData()?.let { header("X-Goog-Visitor-Id", it) }
                }
            }
            .build()
        Net.client.newCall(request).execute().use { r ->
            if (auth != null) Account.absorbSetCookies(r.headers("Set-Cookie"))
            val text = r.body?.string().orEmpty()
            if (r.code == 429) {
                throw Net.youtubeRateLimitFailure()
            }
            if (r.code == 401 || r.code == 403) throw IOException("YouTube rejected the session (${r.code}). Try signing in again.")
            if (!r.isSuccessful) throw IOException("YouTube returned ${r.code}")
            return JSONObject(text)
        }
    }

    // ── Reads ──────────────────────────────────────────────────────────────────

    suspend fun search(query: String, continuation: String? = null, params: String? = null): FeedPage = withContext(Dispatchers.IO) {
        val body = if (continuation != null) JSONObject().put("continuation", continuation)
                   else JSONObject().put("query", query).apply {
                       params?.takeIf { it.isNotBlank() }?.let { put("params", it) }
                   }
        parseFeed(postAny("search", body))
    }

    suspend fun browse(browseId: String, continuation: String? = null): FeedPage = withContext(Dispatchers.IO) {
        val body = if (continuation != null) JSONObject().put("continuation", continuation) else JSONObject().put("browseId", browseId)
        parseFeed(post("browse", body))
    }

    suspend fun home(next: String? = null) = browse("FEwhat_to_watch", next)

    /**
     * Home feed that works for both signed-in and signed-out users.
     * When signed in, loads the user's personalised YouTube home feed.
     * When signed out, loads YouTube's trending/popular kiosk feed so recommendations
     * appear immediately without requiring any Google login.
     */
    suspend fun homeFeed(next: String? = null, chip: FeedChip? = null): FeedPage = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG && next == null && chip == null) {
            android.util.Log.d("HomeChipSync", "Requesting Home feed; signedIn=${Account.signedIn}")
        }
        if (Account.signedIn || chip != null) {
            val continuation = next ?: chip?.continuation
            if (continuation != null) {
                parseFeed(postAny("browse", JSONObject().put("continuation", continuation)))
            } else {
                val routeBody = JSONObject().put("browseId", chip?.browseId ?: "FEwhat_to_watch").apply {
                    chip?.params?.takeIf { it.isNotBlank() }?.let { put("params", it) }
                }
                val response = try {
                    postAny("browse", routeBody)
                } catch (e: IOException) {
                    // YouTube has returned chip commands that only work in their original
                    // navigationEndpoint envelope. Retry that server-provided form on a 400.
                    if (chip?.navigationEndpoint == null || !e.message.orEmpty().contains("returned 400")) throw e
                    postAny("browse", JSONObject().put("navigationEndpoint", chip.navigationEndpoint))
                }
                parseFeed(response)
            }
        } else {
            val serverFeed = runCatching {
                // Anonymous sessions still receive YouTube's own home feed and its category
                // chip bar; using the signed-in request here silently fell through to fallback.
                parseFeed(postAny("browse", JSONObject().put("browseId", "FEwhat_to_watch")))
            }.getOrNull()
            if (serverFeed != null && serverFeed.videos.isNotEmpty()) return@withContext serverFeed

            val seeds = SeenStore.load().toList().takeLast(6)
            if (seeds.isNotEmpty()) {
                val tasteVideos = getTasteBasedVideos(seeds)
                if (tasteVideos.isNotEmpty()) {
                    return@withContext FeedPage(tasteVideos, null)
                }
            }
            YouTube.trending()
        }
    }

    /**
     * Interleaves related videos for recently watched seeds to build a personalized
     * recommendation feed for signed-out users (Koda recommendation model).
     */
    suspend fun getTasteBasedVideos(seedIds: List<String>): List<VideoItem> = kotlinx.coroutines.coroutineScope {
        if (seedIds.isEmpty()) return@coroutineScope emptyList()
        val seeds = seedIds.take(6)
        val perSeed = seeds.map { seedId ->
            async(Dispatchers.IO) {
                runCatching {
                    parseFeed(postAny("next", JSONObject().put("videoId", seedId))).videos
                }.getOrDefault(emptyList())
            }
        }.awaitAll()

        val mixed = mutableListOf<VideoItem>()
        val seen = HashSet<String>(seedIds)
        val longest = perSeed.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) {
            for (list in perSeed) {
                val video = list.getOrNull(i) ?: continue
                if (video.id in seen) continue
                seen.add(video.id)
                mixed.add(video)
            }
        }
        mixed
    }

    /**
     * Sends dismissal feedback to YouTube ("Not Interested" / "Don't recommend channel")
     * asynchronously when signed in.
     */
    suspend fun sendDismissalFeedback(token: String?): Boolean = withContext(Dispatchers.IO) {
        if (token.isNullOrBlank() || !Account.signedIn) return@withContext false
        try {
            val body = JSONObject()
                .put("feedbackTokens", JSONArray().put(token))
                .put("isFeedbackTokenUnencrypted", false)
                .put("shouldMerge", false)
            val raw = post("feedback", body)
            val processed = raw.optJSONArray("feedbackResponses")
                ?.optJSONObject(0)
                ?.optBoolean("isProcessed", false) ?: false
            processed
        } catch (e: Exception) {
            false
        }
    }

    suspend fun channelVideos(channelId: String, params: String? = CHANNEL_VIDEOS_TAB, continuation: String? = null): FeedPage = withContext(Dispatchers.IO) {
        val body = if (continuation != null) JSONObject().put("continuation", continuation)
        else JSONObject().put("browseId", channelId).put("params", params ?: CHANNEL_VIDEOS_TAB)
        val root = postAny("browse", body)
        val page = parseFeed(root)
        val header = if (continuation == null) parseChannelHeader(root) else null
        FeedPage(page.videos, page.next, header)
    }

    suspend fun setCommunityPostLike(postId: String, liked: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!Account.signedIn) throw NotSignedInException()
        val endpoint = if (liked) "like/like" else "like/removelike"
        val body = JSONObject().put("target", JSONObject().put("postId", postId))
        val res = runCatching { postAny(endpoint, body) }.getOrNull()
        res != null
    }

    /** The channel page's Videos tab selector (as on youtube.com, 2026-10-06). */
    const val CHANNEL_VIDEOS_TAB = "EgZ2aWRlb3PyBgQKAjoA"
    const val CHANNEL_SHORTS_TAB = "EgZzaG9ydHPyBgQKAjoA"
    const val CHANNEL_LIVE_TAB = "EgBsaXZlcPIGBAoCEgA%3D"
    const val CHANNEL_PLAYLISTS_TAB = "EglwbGF5bGlzdHPyBgQKAjoA"
    const val CHANNEL_COMMUNITY_TAB = "EgZjb21tdW5pdHkyBgQKACgB"

    suspend fun subscriptionsFeed(next: String? = null) = browse("FEsubscriptions", next)
    suspend fun history(next: String? = null) = browse("FEhistory", next)

    suspend fun playlist(id: String, next: String? = null, videoId: String? = null): FeedPage = withContext(Dispatchers.IO) {
        if (id.startsWith("RD")) {
            val body = JSONObject().put("playlistId", id)
            if (videoId != null) body.put("videoId", videoId)
            if (next != null) body.put("continuation", next)
            val root = runCatching { postAny("next", body) }.getOrNull()
            val nextPage = root?.let(::parseFeed)

            // A seeded Mix `next` response can contain only its selected video while the
            // playlist browse response contains the actual generated radio tracks. Prefer
            // the fuller initial page so Mix taps do not degrade into one-video playback.
            if (next != null) {
                if (!nextPage?.videos.isNullOrEmpty()) return@withContext nextPage!!
                return@withContext browse("VL$id", next)
            }
            if (nextPage != null && nextPage.videos.size > 1) return@withContext nextPage

            val prefixed = runCatching { browse("VL$id") }.getOrNull()
            val direct = if (prefixed?.videos.orEmpty().size > 1) null
                else runCatching { browse(id) }.getOrNull()
            return@withContext listOfNotNull(nextPage, prefixed, direct)
                .maxByOrNull { it.videos.size }
                ?: nextPage
                ?: FeedPage(emptyList(), null)
                ?: prefixed
                ?: browse(id)
        }
        val browseId = when {
            id.startsWith("VL") || id.startsWith("FE") || id.startsWith("DL") || id.startsWith("RD") -> id
            else -> "VL$id"
        }
        browse(browseId, next)
    }

    suspend fun channelCommunityPosts(channelId: String): List<CommunityPost> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("browseId", channelId).put("params", CHANNEL_COMMUNITY_TAB)
        val root = postAny("browse", body)
        parseCommunityPosts(root)
    }

    /** Header avatar from a channel browse (playlist rows often ship no channel thumbnail). */
    suspend fun channelAvatarUrl(channelId: String): String? = withContext(Dispatchers.IO) {
        val root = postAny("browse", JSONObject().put("browseId", channelId))
        parseChannelAvatar(root.optJSONObject("header"))
    }

    internal fun parseChannelAvatar(header: JSONObject?): String? {
        if (header == null) return null
        var url: String? = null
        header.walk { key, obj ->
            if (url != null) return@walk
            if (key == "avatarViewModel" || key == "decoratedAvatarViewModel") {
                obj.optJSONObject("image")?.optJSONArray("sources")?.widestSourceUrl()?.let { url = it }
            }
        }
        return url?.let { normalizeChannelAvatarUrl(absoluteUrl(it)) }
    }

    suspend fun channels(): List<ChannelRef> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, ChannelRef>()
        post("browse", JSONObject().put("browseId", "FEchannels")).walk { key, obj ->
            if (key == "channelRenderer" || key == "gridChannelRenderer") {
                val id = obj.optString("channelId").ifBlank { return@walk }
                out.getOrPut(id) {
                    ChannelRef(id, obj.text("title") ?: "", obj.thumbnails()?.lastOrNull()?.let(::absoluteUrl))
                }
            }
        }
        out.values.toList()
    }

    suspend fun playlists(): List<PlaylistRef> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, PlaylistRef>()
        post("browse", JSONObject().put("browseId", "FEplaylist_aggregation")).walk { key, obj ->
            when (key) {
                "gridPlaylistRenderer", "playlistRenderer" -> {
                    val id = obj.optString("playlistId").ifBlank { return@walk }
                    out.getOrPut(id) {
                        PlaylistRef(id, obj.text("title") ?: "", obj.thumbnails()?.lastOrNull()?.let(::absoluteUrl), obj.text("videoCountText") ?: obj.text("videoCountShortText"))
                    }
                }
                "lockupViewModel" -> if (obj.optString("contentType") == "LOCKUP_CONTENT_TYPE_PLAYLIST") {
                    val id = obj.optString("contentId").ifBlank { return@walk }
                    val meta = obj.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                    out.getOrPut(id) {
                        PlaylistRef(
                            id = id,
                            title = meta?.optJSONObject("title")?.optString("content").orEmpty(),
                            thumbnail = obj.firstImageSource(),
                            subtitle = obj.collectStrings("text").firstOrNull { it.contains("video", ignoreCase = true) },
                        )
                    }
                }
            }
        }
        out.values.filterNot { it.id == "WL" || it.id == "LL" }
    }

    /** Like state and subscription for a video, from the signed-in watch page data. */
    suspend fun videoState(videoId: String): VideoAccountState = withContext(Dispatchers.IO) {
        val next = post("next", JSONObject().put("videoId", videoId))
        var like = LikeStatus.None
        var subscribed: Boolean? = null
        var channelId: String? = null
        next.walk { key, obj ->
            when (key) {
                "likeStatusEntity" -> like = when (obj.optString("likeStatus")) {
                    "LIKE" -> LikeStatus.Like
                    "DISLIKE" -> LikeStatus.Dislike
                    else -> like
                }
                "subscribeButtonRenderer" -> {
                    if (obj.has("subscribed")) subscribed = obj.optBoolean("subscribed")
                    obj.optString("channelId").ifBlank { null }?.let { channelId = it }
                }
                "subscriptionStateEntity" -> if (obj.has("subscribed")) subscribed = obj.optBoolean("subscribed")
                "videoOwnerRenderer" -> obj.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")?.takeIf { it.startsWith("UC") }?.let { if (channelId == null) channelId = it }
            }
        }
        VideoAccountState(like, subscribed, channelId)
    }

    /** Whether the account follows [channelId] (from the channel page's subscribe button). */
    suspend fun channelSubscribed(channelId: String): Boolean? = withContext(Dispatchers.IO) {
        var subscribed: Boolean? = null
        post("browse", JSONObject().put("browseId", channelId)).walk { key, obj ->
            if ((key == "subscribeButtonRenderer" || key == "subscriptionStateEntity") && obj.has("subscribed") && subscribed == null) {
                subscribed = obj.optBoolean("subscribed")
            }
        }
        subscribed
    }

    // ── Writes ─────────────────────────────────────────────────────────────────

    suspend fun setLike(videoId: String, status: LikeStatus) = withContext(Dispatchers.IO) {
        val endpoint = when (status) {
            LikeStatus.Like -> "like/like"
            LikeStatus.Dislike -> "like/dislike"
            LikeStatus.None -> "like/removelike"
        }
        post(endpoint, JSONObject().put("target", JSONObject().put("videoId", videoId)))
        Unit
    }

    suspend fun setSubscribed(channelId: String, subscribed: Boolean) = withContext(Dispatchers.IO) {
        post(
            if (subscribed) "subscription/subscribe" else "subscription/unsubscribe",
            JSONObject().put("channelIds", JSONArray().put(channelId)),
        )
        Unit
    }

    suspend fun setInWatchLater(videoId: String, add: Boolean) = editPlaylist("WL", videoId, add)

    suspend fun editPlaylist(playlistId: String, videoId: String, add: Boolean) = withContext(Dispatchers.IO) {
        val action = if (add) JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId)
        else JSONObject().put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID").put("removedVideoId", videoId)
        post("browse/edit_playlist", JSONObject().put("playlistId", playlistId).put("actions", JSONArray().put(action)))
        Unit
    }

    suspend fun createPlaylist(title: String, videoId: String? = null, privacy: String = "UNLISTED"): String? = withContext(Dispatchers.IO) {
        val body = JSONObject().put("title", title).put("privacyStatus", privacy)
        if (videoId != null) {
            body.put("videoIds", JSONArray().put(videoId))
        }
        val res = post("playlist/create", body)
        res.optString("playlistId").ifBlank { null }
    }

    /** One video being watched: the tracking URL for its watch-time pings. */
    class WatchSession(val videoId: String, val watchtimeUrl: String, val cpn: String)

    /**
     * Starts watching [videoId] on the account, as the web player does: fetch the signed-in
     * player response and ping its playback URL, which puts the video in watch history.
     * Returns the session for [pingWatchtime], or null if YouTube gave no tracking URLs.
     */
    fun startWatch(videoId: String): WatchSession? {
        val tracking = post("player", JSONObject().put("videoId", videoId)).optJSONObject("playbackTracking") ?: return null
        val cpn = (1..16).map { CPN_ALPHABET[Random.nextInt(CPN_ALPHABET.length)] }.joinToString("")
        tracking.optJSONObject("videostatsPlaybackUrl")?.optString("baseUrl")?.ifBlank { null }?.let { base ->
            ping(base, "ver=2&cpn=$cpn")
        }
        val watchtime = tracking.optJSONObject("videostatsWatchtimeUrl")?.optString("baseUrl")?.ifBlank { null } ?: return null
        return WatchSession(videoId, watchtime, cpn)
    }

    /**
     * Reports what was played since the last ping: the stretches of the video ([starts] →
     * [ends], seconds), where playback is now ([positionSec]) and how long the session has
     * run ([elapsedSec]). The same parameters the web player sends; YouTube's
     * recommendations are built on this watch time.
     */
    fun pingWatchtime(session: WatchSession, positionSec: Double, starts: List<Double>, ends: List<Double>, playing: Boolean, elapsedSec: Double, final: Boolean) {
        fun f(x: Double) = String.format(java.util.Locale.US, "%.3f", x)
        val rt = f(elapsedSec)
        val query = buildString {
            append("ver=2&cpn=").append(session.cpn)
            append("&cmt=").append(f(positionSec))
            append("&st=").append(starts.joinToString(",") { f(it) })
            append("&et=").append(ends.joinToString(",") { f(it) })
            append("&state=").append(if (playing) "playing" else "paused")
            append("&rt=").append(rt).append("&rtn=").append(rt).append("&rti=").append(elapsedSec.toInt())
            append("&lact=").append(Random.nextInt(800, 4000))
            append("&volume=100&muted=0&fs=0&hl=en_US&cr=US&c=WEB&cver=").append(Account.clientVersion())
            if (final) append("&final=1")
        }
        ping(session.watchtimeUrl, query)
    }

    private fun ping(base: String, query: String) {
        val request = Request.Builder().url(base + (if ('?' in base) "&" else "?") + query)
            .header("User-Agent", Net.USER_AGENT)
            .header("Cookie", Account.cookieHeader())
            .header("Referer", Account.ORIGIN + "/")
            .build()
        Net.client.newCall(request).execute().use { r ->
            Account.absorbSetCookies(r.headers("Set-Cookie"))
            if (!r.isSuccessful) throw IOException("stats ping ${r.code}")
        }
    }

    private const val CPN_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"

    suspend fun channelShelves(channelId: String): List<ChannelShelf> = withContext(Dispatchers.IO) {
        val root = runCatching { postAny("browse", JSONObject().put("browseId", channelId)) }.getOrNull()
        root?.let { parseChannelShelves(it) }.orEmpty()
    }

    internal fun parseChannelShelves(root: JSONObject): List<ChannelShelf> {
        val shelves = mutableListOf<ChannelShelf>()

        root.walk { key, obj ->
            if (key == "shelfRenderer" || key == "reelShelfRenderer" || key == "itemSectionRenderer") {
                val title = obj.optJSONObject("title")?.optString("simpleText")
                    ?: obj.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                    ?: obj.text("title")
                    ?: ""

                val videos = mutableListOf<VideoItem>()
                val isShorts = key == "reelShelfRenderer" || title.contains("Shorts", ignoreCase = true)

                obj.walk { childKey, childObj ->
                    when (childKey) {
                        "videoRenderer", "compactVideoRenderer", "gridVideoRenderer" -> {
                            if (!isMembersOnly(childObj) && !isMovie(childObj)) {
                                parseVideoRenderer(childObj)?.let { videos.add(it) }
                            }
                        }
                        "lockupViewModel" -> {
                            if (!isMembersOnly(childObj) && !isMovie(childObj)) {
                                if (childObj.optString("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO") {
                                    parseLockup(childObj)?.let { videos.add(it) }
                                }
                            }
                        }
                    }
                }

                if (title.isNotBlank() && videos.isNotEmpty()) {
                    shelves.add(ChannelShelf(title = title, videos = videos.distinctBy { it.id }, isShorts = isShorts))
                }
            }
        }

        return shelves.distinctBy { it.title }
    }

    // ── Parsing ────────────────────────────────────────────────────────────────

    /**
     * Pulls every video out of a browse response, whichever renderer YouTube used:
     * the classic videoRenderer family or the newer lockupViewModel. Shorts are skipped.
     */
    internal fun parseFeed(root: JSONObject): FeedPage {
        if (BuildConfig.DEBUG) {
            val chipNodes = linkedMapOf<String, Int>()
            root.walk { key, _ -> if (key.contains("chip", ignoreCase = true)) chipNodes[key] = (chipNodes[key] ?: 0) + 1 }
            android.util.Log.d("HomeChipSync", "Chip-shaped response nodes: $chipNodes")
        }
        val videos = LinkedHashMap<String, VideoItem>()
        var next: String? = null
        val chips = LinkedHashMap<String, FeedChip>()
        val mixPlaylistIds = HashSet<String>()
        var channelCard: ChannelDetails? = null
        root.walk { key, obj ->
            when (key) {
                "channelRenderer", "gridChannelRenderer" -> {
                    if (channelCard == null) {
                        val id = obj.optString("channelId").ifBlank { null }
                        val title = obj.text("title").orEmpty()
                        if (id != null && title.isNotBlank()) {
                            val avatar = obj.thumbnails()?.lastOrNull()?.let(::absoluteUrl)
                            val subText = obj.text("subscriberCountText") ?: obj.text("videoCountText")
                            val verified = obj.toString().contains("BADGE_STYLE_TYPE_VERIFIED")
                            channelCard = ChannelDetails(
                                url = "https://www.youtube.com/channel/$id",
                                id = id,
                                name = title,
                                avatar = avatar,
                                banner = null,
                                subscribers = parseViews(subText.orEmpty()),
                                videoCount = -1,
                                handle = subText,
                                verified = verified,
                                description = obj.text("descriptionSnippet").orEmpty(),
                                videosTab = null,
                            )
                        }
                    }
                }
                "chipCloudChipRenderer" -> {
                    val title = (obj.text("text")
                        ?: obj.optJSONObject("accessibilityData")?.optJSONObject("accessibilityData")?.optString("label"))
                        ?.substringBefore(". Selected")?.trim().orEmpty()
                    var browseId: String? = null
                    var params: String? = null
                    var continuation: String? = null
                    val navigationEndpoint = obj.optJSONObject("navigationEndpoint")
                    navigationEndpoint?.walk { endpointKey, endpoint ->
                        if (endpointKey == "browseEndpoint") {
                            if (browseId == null) browseId = endpoint.optString("browseId").takeIf { it.isNotBlank() }
                            if (params == null) params = endpoint.optString("params").takeIf { it.isNotBlank() }
                        } else if (endpointKey == "continuationCommand" && continuation == null) {
                            continuation = endpoint.optString("token").takeIf { it.isNotBlank() }
                        }
                    }
                    if (BuildConfig.DEBUG) {
                        val endpoint = obj.optJSONObject("navigationEndpoint")
                        android.util.Log.d("HomeChipSync", "chip '$title' route=${endpoint?.toString()?.take(240)}")
                    }
                    // The selected default "All" chip can omit its navigation endpoint.
                    if (title.isNotBlank() && (navigationEndpoint != null || browseId != null || params != null || continuation != null || title.equals("All", true))) {
                        if (title.equals("All", true) && browseId == null && params == null) browseId = "FEwhat_to_watch"
                        val routeKey = continuation ?: navigationEndpoint?.toString() ?: listOfNotNull(browseId, params, title).joinToString("|")
                        val chip = FeedChip(
                            title = title,
                            browseId = browseId,
                            params = params,
                            navigationEndpoint = navigationEndpoint,
                            key = routeKey,
                            continuation = continuation,
                        )
                        chips.putIfAbsent(chip.key, chip)
                    }
                }
                // Members-only videos can't be played (anonymous extraction), and full movies/TV
                // aren't wanted (Derek), so neither is ever shown.
                "videoRenderer", "gridVideoRenderer", "compactVideoRenderer", "playlistVideoRenderer", "playlistPanelVideoRenderer" ->
                    if (!isMembersOnly(obj) && !isMovie(obj)) parseVideoRenderer(obj)?.let { videos.putIfAbsent(it.id, it) }
                "radioRenderer", "compactRadioRenderer", "playlistRadioRenderer", "mixRenderer" ->
                    parseRadioRenderer(obj)?.let { mix ->
                        val playlist = mix.mixPlaylistId
                        if (playlist == null || mixPlaylistIds.add(playlist)) videos.putIfAbsent(mix.feedKey, mix)
                    }
                "backstagePostRenderer", "sharedPostRenderer", "postRenderer" -> {
                    val post = parseCommunityPosts(JSONObject().put(key, obj)).firstOrNull()
                    if (post != null) {
                        videos.putIfAbsent(
                            "post:${post.id}",
                            VideoItem(
                                id = "post:${post.id}",
                                title = post.text.ifBlank { post.author },
                                channel = post.author,
                                channelUrl = null,
                                thumbnail = post.images.firstOrNull(),
                                avatar = post.authorAvatar,
                                durationSec = 0L,
                                views = -1,
                                uploaded = post.publishedTime,
                                feedPost = post,
                            ),
                        )
                    }
                }
                "lockupViewModel" -> {
                    val contentType = obj.optString("contentType")
                    val contentId = obj.optString("contentId")
                    if (!isMembersOnly(obj) && !isMovie(obj)) {
                        if (contentType == "LOCKUP_CONTENT_TYPE_MIX" || contentType == "LOCKUP_CONTENT_TYPE_PLAYLIST" || contentId.startsWith("RD")) {
                            parseRadioRenderer(obj)?.let { mix ->
                                val playlist = mix.mixPlaylistId ?: contentId.takeIf { it.startsWith("RD") }
                                val identified = if (mix.mixPlaylistId == null) mix.copy(mixPlaylistId = playlist) else mix
                                if (playlist == null || mixPlaylistIds.add(playlist)) videos.putIfAbsent(identified.feedKey, identified)
                            }
                        } else if (contentType == "LOCKUP_CONTENT_TYPE_VIDEO") {
                            parseLockup(obj)?.let { videos.putIfAbsent(it.id, it) }
                        }
                    }
                }
                // Only the feed's own "load more" item (old and new names); chip filters
                // carry tokens too and must not be mistaken for the next page.
                "continuationItemRenderer", "continuationItemViewModel" ->
                    obj.collectStrings("token").firstOrNull()?.let { next = it }
            }
        }
        return FeedPage(videos.values.toList(), next, chips = chips.values.toList(), channelCard = channelCard)
    }

    /** YouTube's "Members only" badge: BADGE_MEMBERS_ONLY (lockups) / BADGE_STYLE_TYPE_MEMBERS_ONLY (older renderers). */
    internal fun isMembersOnly(o: JSONObject): Boolean =
        o.collectStrings("badgeStyle").any { it.contains("MEMBERS_ONLY") } ||
            o.collectStrings("style").any { it == "BADGE_STYLE_TYPE_MEMBERS_ONLY" }

    /**
     * Full movies and TV (Primetime Channels like HBO Max / Shudder, YouTube Movies): a paid-offer
     * badge ("Try now", "Buy or rent") or a content rating (R, PG-13, TV-MA), which ordinary
     * uploads never carry. Older renderers: BADGE_STYLE_TYPE_YPC / BADGE_STYLE_TYPE_MEDIA;
     * lockups: a BADGE_DEFAULT whose text is the rating (not every movie has one) or a
     * BADGE_COMMERCE offer — ordinary uploads use that style only for "Fundraiser".
     */
    internal fun isMovie(o: JSONObject): Boolean {
        if (o.collectStrings("style").any { it == "BADGE_STYLE_TYPE_YPC" || it == "BADGE_STYLE_TYPE_MEDIA" }) return true
        var movie = false
        o.walk { key, badge ->
            if (key != "badgeViewModel") return@walk
            val text = badge.optString("badgeText")
            if (CONTENT_RATING.matches(text) ||
                (badge.optString("badgeStyle") == "BADGE_COMMERCE" && !text.equals("Fundraiser", ignoreCase = true))
            ) movie = true
        }
        return movie
    }

    private val CONTENT_RATING = Regex("^(G|PG|PG-13|R|NC-17|NR|TV-(Y|Y7|G|PG|14|MA))$")

    private fun parseRadioRenderer(o: JSONObject): VideoItem? {
        val mixPlaylistId = o.collectStrings("playlistId").firstOrNull { it.startsWith("RD") }
            ?: o.collectStrings("playlistId").firstOrNull { it.startsWith("PL") }
        var videoId: String? = null
        o.walk { key, obj ->
            if (videoId == null && (key == "watchEndpoint" || key == "watchPlaylistEndpoint")) {
                videoId = obj.optString("videoId").ifBlank { null }
            }
        }
        if (videoId == null) {
            videoId = o.collectStrings("videoId").firstOrNull { it.length == 11 }
        }
        val id = videoId ?: return null
        val title = o.text("title") ?: "YouTube Mix"
        val channel = o.text("shortBylineText") ?: o.text("ownerText") ?: o.text("longBylineText") ?: "YouTube Mix"
        val thumbnail = videoThumbnailUrl(o, id)
        val avatar = pickChannelAvatarUrl(o)
        return VideoItem(
            id = id,
            title = title,
            channel = channel,
            channelUrl = null,
            thumbnail = thumbnail,
            avatar = avatar?.let { sharpenChannelAvatarUrl(absoluteUrl(it), 96) },
            durationSec = 0L,
            views = -1,
            uploaded = "Mix · Infinite playlist",
            mixPlaylistId = mixPlaylistId,
        )
    }

    private fun parseVideoRenderer(o: JSONObject): VideoItem? {
        val id = o.optString("videoId").ifBlank { return null }
        val byline = o.optJSONObject("ownerText") ?: o.optJSONObject("shortBylineText") ?: o.optJSONObject("longBylineText")
        val channelRun = byline?.optJSONArray("runs")?.optJSONObject(0)
        val channelId = channelRun?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
        val isLive = o.collectStrings("badgeStyle").any { it.contains("LIVE") } ||
            o.collectStrings("style").any { it.contains("LIVE") } ||
            o.collectStrings("text").any { it.equals("LIVE", ignoreCase = true) }

        val duration = o.text("lengthText")
            ?: o.optString("lengthSeconds").toLongOrNull()?.let { formatDuration(it * 1000) }
            ?: o.optJSONArray("thumbnailOverlays")?.let { arr ->
                (0 until arr.length()).firstNotNullOfOrNull { arr.optJSONObject(it)?.optJSONObject("thumbnailOverlayTimeStatusRenderer")?.let { r -> r.text("text") } }
            }
        val infoRuns = o.optJSONObject("videoInfo")?.optJSONArray("runs")?.let { runs ->
            (0 until runs.length()).map { runs.optJSONObject(it).optString("text") }.filter { it.isNotBlank() && it.trim() != "•" }
        }.orEmpty()
        val avatar = pickChannelAvatarUrl(o)
        val channelText = channelRun?.optString("text").orEmpty()
        val collabChannels = parseCollabChannelsFromLockup(o, channelText)
            .ifEmpty { parseCollabChannelsFromVideoRenderer(o, byline) }
        val feedbackTokens = recommendationFeedbackTokens(o)

        return VideoItem(
            id = id,
            title = o.text("title") ?: return null,
            channel = channelText,
            channelUrl = channelId?.takeIf { it.startsWith("UC") }?.let { "https://www.youtube.com/channel/$it" },
            thumbnail = videoThumbnailUrl(o, id),
            avatar = avatar?.let { sharpenChannelAvatarUrl(absoluteUrl(it), 96) },
            durationSec = if (isLive) 0L else parseDuration(duration),
            views = parseViews(o.text("viewCountText") ?: o.text("shortViewCountText") ?: infoRuns.firstOrNull { it.contains("view") || it.contains("watching") }),
            uploaded = if (isLive) "LIVE NOW" else (o.text("publishedTimeText") ?: infoRuns.firstOrNull { it.contains("ago") }),
            collabChannels = collabChannels,
            feedbackTokens = feedbackTokens,
        )
    }

    internal fun parseCommunityPosts(root: JSONObject): List<CommunityPost> {
        val posts = ArrayList<CommunityPost>()
        root.walk { key, obj ->
            if (key == "backstagePostRenderer" || key == "sharedPostRenderer" || key == "postRenderer") {
                val id = obj.optString("postId").ifBlank { obj.optString("id") }
                if (id.isNotBlank()) {
                    val author = obj.text("authorText") ?: obj.optJSONObject("authorText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Community"
                    val avatar = obj.optJSONObject("authorEndpoint")?.optJSONObject("browseEndpoint")?.optJSONObject("avatar")?.thumbnails()?.lastOrNull()
                        ?: obj.optJSONObject("authorThumbnail")?.thumbnails()?.lastOrNull()
                    val channelId = obj.optJSONObject("authorEndpoint")
                        ?.optJSONObject("browseEndpoint")?.optString("browseId")
                        ?.takeIf { it.startsWith("UC") }
                        ?: obj.collectStrings("browseId").firstOrNull { it.startsWith("UC") }
                    var detailParams: String? = null
                    obj.walk { _, child ->
                        if (detailParams == null) {
                            val endpoint = child.optJSONObject("browseEndpoint")
                            if (endpoint?.optString("browseId") == "FEpost_detail") {
                                detailParams = endpoint.optString("params").ifBlank { null }
                            }
                        }
                    }
                    val published = obj.text("publishedTimeText") ?: obj.optJSONObject("publishedTimeText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                    val text = obj.text("contentText") ?: obj.collectStrings("text").joinToString(" ")

                    val images = ArrayList<String>()
                    val pollOptions = ArrayList<PollOption>()
                    var likeCount = -1
                    var dislikeCount = -1
                    var liked = false
                    var commentCountText: String? = null

                    obj.walk { k, child ->
                        if (k == "backstageImageRenderer" || k == "imageRenderer") {
                            child.optJSONObject("image")?.thumbnails()?.lastOrNull()?.let { images.add(absoluteUrl(it)) }
                        } else if (k == "pollOptionRenderer") {
                            val optionText = child.text("text") ?: ""
                            val pct = child.text("votePercentage") ?: child.text("votePercentageIfSubmitted")
                            val img = child.optJSONObject("image")?.thumbnails()?.lastOrNull()?.let { absoluteUrl(it) }
                            pollOptions.add(PollOption(optionText, pct, img))
                        } else if (k == "likeButton") {
                            child.walk { buttonKey, button ->
                                if (buttonKey == "toggleButtonRenderer") {
                                    liked = button.optBoolean("isToggled", liked)
                                    val count = button.text("defaultText")
                                    if (likeCount < 0 && count != null) {
                                        likeCount = parseViews(count).takeIf { it >= 0 }?.toInt() ?: -1
                                    }
                                }
                            }
                        } else if (k == "dislikeButton") {
                            child.walk { buttonKey, button ->
                                if (buttonKey == "toggleButtonRenderer" || buttonKey == "buttonRenderer") {
                                    val count = button.text("defaultText")
                                        ?: button.optJSONObject("accessibility")?.optJSONObject("accessibilityData")?.optString("label")
                                    if (dislikeCount < 0 && count != null) {
                                        dislikeCount = parseViews(count).takeIf { it >= 0 }?.toInt() ?: -1
                                    }
                                }
                            }
                        } else if (k == "commentButton") {
                            child.walk { bk, button ->
                                if (bk == "buttonRenderer" || bk == "toggleButtonRenderer") {
                                    commentCountText = button.text("text") ?: button.text("defaultText")
                                        ?: button.optJSONObject("accessibility")?.optJSONObject("accessibilityData")?.optString("label")
                                }
                            }
                        }
                    }

                    var imageAspectRatio: Float? = null
                    obj.walk { k, child ->
                        if (k == "backstageImageRenderer" || k == "imageRenderer") {
                            if (imageAspectRatio == null) {
                                imageAspectRatio = parseImageAspectRatio(child)
                            }
                        }
                    }

                    posts.add(
                        CommunityPost(
                            id = id,
                            author = author,
                            authorAvatar = avatar,
                            publishedTime = published,
                            text = text,
                            images = images,
                            pollOptions = pollOptions,
                            likeCount = likeCount,
                            dislikeCount = dislikeCount,
                            liked = liked,
                            channelId = channelId,
                            detailParams = detailParams,
                            commentCountText = commentCountText?.let { txt ->
                                val nums = txt.filter { it.isDigit() || it == '.' || it.lowercaseChar() == 'k' || it.lowercaseChar() == 'm' }
                                if (nums.isNotBlank()) nums else null
                            },
                            imageAspectRatio = imageAspectRatio,
                        )
                    )
                }
            }
        }
        return posts
    }

    private fun parseLockup(o: JSONObject): VideoItem? {
        val id = o.optString("contentId").ifBlank { return null }
        val meta = o.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val title = meta.optJSONObject("title")?.optString("content")?.ifBlank { null } ?: return null
        val isLive = o.collectStrings("badgeStyle").any { it.contains("LIVE") } ||
            o.collectStrings("style").any { it.contains("LIVE") } ||
            o.collectStrings("text").any { it.equals("LIVE", ignoreCase = true) }

        val parts = mutableListOf<String>()
        meta.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")?.let { rows ->
            for (r in 0 until rows.length()) {
                val ps = rows.optJSONObject(r)?.optJSONArray("metadataParts") ?: continue
                for (p in 0 until ps.length()) ps.optJSONObject(p)?.optJSONObject("text")?.optString("content")?.ifBlank { null }?.let(parts::add)
            }
        }
        val avatar = meta.bestChannelAvatarUrl()
        val duration = o.optJSONObject("contentImage")?.collectStrings("text")?.firstOrNull { DURATION.matches(it) }
        val channelText = parts.firstOrNull { !it.contains("view") && !it.contains("ago") && !it.contains("watching") }.orEmpty()
        val collabChannels = parseCollabChannelsFromLockup(o, channelText)
        val feedbackTokens = recommendationFeedbackTokens(o)

        return VideoItem(
            id = id,
            title = title,
            channel = channelText,
            channelUrl = o.collectStrings("browseId").firstOrNull { it.startsWith("UC") }?.let { "https://www.youtube.com/channel/$it" },
            thumbnail = videoThumbnailUrl(o, id),
            avatar = avatar?.let { sharpenChannelAvatarUrl(absoluteUrl(it), 96) },
            durationSec = if (isLive) 0L else parseDuration(duration),
            views = parseViews(parts.firstOrNull { it.contains("view") || it.contains("watching") }),
            uploaded = if (isLive) "LIVE NOW" else parts.firstOrNull { it.contains("ago") },
            collabChannels = collabChannels,
            feedbackTokens = feedbackTokens,
        )
    }

    private fun recommendationFeedbackTokens(root: JSONObject): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        root.walk { key, renderer ->
            if (key != "menuServiceItemRenderer") return@walk
            val label = renderer.text("text")
                ?: renderer.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: return@walk
            val normalized = label.lowercase()
            val action = when {
                "not interested" in normalized -> "not_interested"
                "interested" in normalized -> "interested"
                else -> return@walk
            }
            renderer.collectStrings("feedbackToken").firstOrNull()?.let { result[action] = it }
        }
        return result
    }

    internal fun parseCollabChannelsFromNext(nextRoot: JSONObject): List<ChannelRef> {
        val result = mutableListOf<ChannelRef>()
        nextRoot.forEachObject { obj ->
            val collaborators = obj.optJSONArray("collaborators")
                ?: obj.optJSONArray("collaboratorRenderers")
                ?: obj.optJSONArray("channelLockups")
            if (collaborators != null && collaborators.length() > 0) {
                for (i in 0 until collaborators.length()) {
                    val cObj = collaborators.optJSONObject(i) ?: continue
                    val text = cObj.text("title") ?: cObj.text("name") ?: cObj.optString("text")
                    val browseId = cObj.collectStrings("browseId").firstOrNull { it.startsWith("UC") }
                    val avatar = cObj.bestChannelAvatarUrl() ?: browseId?.let { ChannelAvatars.known[it] }
                    val handle = cObj.collectStrings("canonicalBaseUrl").firstOrNull { it.startsWith("/@") }?.removePrefix("/")
                        ?: cObj.text("handle")
                        ?: cObj.collectStrings("text").firstOrNull { it.startsWith("@") }
                    val subText = cObj.text("subscriberCountText")
                        ?: cObj.collectStrings("text").firstOrNull { "subscriber" in it || "lakh" in it || "k" in it.lowercase() || "m" in it.lowercase() }
                    val verified = parseChannelVerified(cObj)
                    if (!text.isNullOrBlank() && browseId != null) {
                        result.add(
                            ChannelRef(
                                id = browseId,
                                name = text,
                                avatar = avatar,
                                handle = handle,
                                subscriberCountText = subText,
                                verified = verified,
                            )
                        )
                    }
                }
            }
            val runs = obj.optJSONObject("title")?.optJSONArray("runs")
                ?: obj.optJSONObject("ownerText")?.optJSONArray("runs")
                ?: obj.optJSONObject("shortBylineText")?.optJSONArray("runs")
                ?: obj.optJSONObject("longBylineText")?.optJSONArray("runs")
            if (runs != null && runs.length() >= 3) {
                val runsChannels = mutableListOf<ChannelRef>()
                val thumbnails = obj.optJSONObject("thumbnail")?.thumbnails() ?: emptyList()
                for (i in 0 until runs.length()) {
                    val r = runs.optJSONObject(i) ?: continue
                    val text = r.optString("text").trim()
                    val browseId = r.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId")
                        ?.takeIf { it.startsWith("UC") }
                    val canonical = r.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("canonicalBaseUrl")
                        ?.takeIf { it.startsWith("/@") }
                        ?.removePrefix("/")
                    if (!browseId.isNullOrBlank() && text.isNotBlank() && text != "&" && text != "," && text.lowercase() != "and") {
                        val avatar = thumbnails.getOrNull(runsChannels.size)?.let { absoluteUrl(it) }
                            ?: obj.bestChannelAvatarUrl()
                            ?: ChannelAvatars.known[browseId]
                        runsChannels.add(
                            ChannelRef(
                                id = browseId,
                                name = text,
                                avatar = avatar,
                                handle = canonical,
                            )
                        )
                    }
                }
                if (runsChannels.size >= 2) {
                    result.addAll(runsChannels)
                }
            }
        }
        val distinct = result.distinctBy { it.id }
        if (distinct.size >= 2) return distinct

        val ownerText = nextRoot.collectStrings("ownerText") + nextRoot.collectStrings("shortBylineText") + nextRoot.collectStrings("longBylineText")
        val textToTry = ownerText.firstOrNull { it.contains(" and ", ignoreCase = true) || it.contains(" & ", ignoreCase = true) }
        return parseCollabChannelsFromLockup(nextRoot, textToTry)
    }

    internal fun parseCollabChannelsFromByline(byline: JSONObject?): List<ChannelRef> {
        val runs = byline?.optJSONArray("runs") ?: return emptyList()
        if (runs.length() < 3) return emptyList()
        val list = mutableListOf<ChannelRef>()
        for (i in 0 until runs.length()) {
            val r = runs.optJSONObject(i) ?: continue
            val text = r.optString("text").trim()
            val browseEndpoint = r.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
            val browseId = browseEndpoint?.optString("browseId")?.takeIf { it.startsWith("UC") }
            val canonical = browseEndpoint?.optString("canonicalBaseUrl")?.takeIf { it.startsWith("/@") }?.removePrefix("/")
            if (!browseId.isNullOrBlank() && text.isNotBlank() && text != "&" && text != "," && text.lowercase() != "and") {
                val avatar = ChannelAvatars.known[browseId]
                list.add(ChannelRef(id = browseId, name = text, avatar = avatar, handle = canonical))
            }
        }
        return if (list.size >= 2) list.distinctBy { it.id } else emptyList()
    }

    internal fun parseCollabChannelsFromVideoRenderer(o: JSONObject, byline: JSONObject?): List<ChannelRef> {
        val list = mutableListOf<ChannelRef>()
        o.forEachObject { obj ->
            val collaborators = obj.optJSONArray("collaborators")
                ?: obj.optJSONArray("collaboratorRenderers")
                ?: obj.optJSONArray("channelLockups")
            if (collaborators != null && collaborators.length() > 0) {
                for (i in 0 until collaborators.length()) {
                    val cObj = collaborators.optJSONObject(i) ?: continue
                    val text = cObj.text("title") ?: cObj.text("name") ?: cObj.optString("text")
                    val browseId = cObj.collectStrings("browseId").firstOrNull { it.startsWith("UC") }
                    val avatar = cObj.bestChannelAvatarUrl() ?: browseId?.let { ChannelAvatars.known[it] }
                    val handle = cObj.collectStrings("canonicalBaseUrl").firstOrNull { it.startsWith("/@") }?.removePrefix("/")
                        ?: cObj.collectStrings("text").firstOrNull { it.startsWith("@") }
                    val subText = cObj.text("subscriberCountText")
                    if (!text.isNullOrBlank() && browseId != null) {
                        list.add(ChannelRef(id = browseId, name = text, avatar = avatar, handle = handle, subscriberCountText = subText))
                    }
                }
            }
        }
        if (list.size >= 2) return list.distinctBy { it.id }
        return parseCollabChannelsFromByline(byline)
    }

    internal fun parseCollabChannelsFromLockup(o: JSONObject, channelText: String?): List<ChannelRef> {
        val list = mutableListOf<ChannelRef>()
        o.forEachObject { obj ->
            val collaborators = obj.optJSONArray("collaborators")
                ?: obj.optJSONArray("collaboratorRenderers")
                ?: obj.optJSONArray("channelLockups")
            if (collaborators != null && collaborators.length() > 0) {
                for (i in 0 until collaborators.length()) {
                    val cObj = collaborators.optJSONObject(i) ?: continue
                    val text = cObj.text("title") ?: cObj.text("name") ?: cObj.optString("text")
                    val browseId = cObj.collectStrings("browseId").firstOrNull { it.startsWith("UC") }
                    val avatar = cObj.bestChannelAvatarUrl() ?: browseId?.let { ChannelAvatars.known[it] }
                    val handle = cObj.collectStrings("canonicalBaseUrl").firstOrNull { it.startsWith("/@") }?.removePrefix("/")
                        ?: cObj.text("handle")
                        ?: cObj.collectStrings("text").firstOrNull { it.startsWith("@") }
                    val subText = cObj.text("subscriberCountText")
                    if (!text.isNullOrBlank() && browseId != null) {
                        list.add(ChannelRef(id = browseId, name = text, avatar = avatar, handle = handle, subscriberCountText = subText))
                    }
                }
            }
        }
        if (list.size >= 2) return list.distinctBy { it.id }

        val rawText = channelText?.trim() ?: return emptyList()
        val andRegex = Regex("\\s+(and|&)\\s+", RegexOption.IGNORE_CASE)
        if (!andRegex.containsMatchIn(rawText)) return emptyList()

        val parts = rawText.split(andRegex).map { it.trim() }.filter { it.isNotBlank() }
        if (parts.isEmpty()) return emptyList()

        val browseIds = o.collectStrings("browseId").filter { it.startsWith("UC") }.distinct()
        val canonicals = o.collectStrings("canonicalBaseUrl").filter { it.startsWith("/@") }.map { it.removePrefix("/") }.distinct()

        val result = mutableListOf<ChannelRef>()
        val ch1Name = parts[0]
        val ch1Id = browseIds.getOrNull(0) ?: ""
        val ch1Avatar = ChannelAvatars.known[ch1Id]
        val ch1Handle = canonicals.getOrNull(0)
        result.add(ChannelRef(id = ch1Id, name = ch1Name, avatar = ch1Avatar, handle = ch1Handle))

        if (parts.size >= 2) {
            val secondPart = parts[1]
            val moreMatch = Regex("(\\d+)\\s*(more|others)", RegexOption.IGNORE_CASE).find(secondPart)
            if (moreMatch != null) {
                val count = moreMatch.groupValues[1].toIntOrNull() ?: 2
                for (idx in 1..count) {
                    val cId = browseIds.getOrNull(idx) ?: if (ch1Id.length > 2) "${ch1Id}_collab_$idx" else ""
                    val cHandle = canonicals.getOrNull(idx)
                    val cName = cHandle?.removePrefix("@") ?: if (cId.startsWith("UC")) "Channel" else "Collaborator"
                    val cAvatar = ChannelAvatars.known[cId]
                    result.add(ChannelRef(id = cId, name = cName, avatar = cAvatar, handle = cHandle))
                }
            } else {
                for (idx in 1 until parts.size) {
                    val cName = parts[idx]
                    val cId = browseIds.getOrNull(idx) ?: if (ch1Id.length > 2) "${ch1Id}_collab_$idx" else ""
                    val cAvatar = ChannelAvatars.known[cId]
                    val cHandle = canonicals.getOrNull(idx)
                    result.add(ChannelRef(id = cId, name = cName, avatar = cAvatar, handle = cHandle))
                }
            }
        }

        return if (result.size >= 2) result else emptyList()
    }

    private fun pickChannelAvatarUrl(o: JSONObject): String? {
        o.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("channelThumbnailWithLinkRenderer")?.let { r ->
                r.thumbnails()?.lastOrNull()?.let { return it }
                r.optJSONObject("thumbnail")?.thumbnails()?.lastOrNull()?.let { return it }
            }
        o.optJSONObject("channelThumbnail")?.thumbnails()?.lastOrNull()?.let { return it }
        o.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")?.bestChannelAvatarUrl()?.let { return it }
        o.bestChannelAvatarUrl()?.let { return it }
        var found: String? = null
        o.forEachObject { obj ->
            if (found != null) return@forEachObject
            obj.optJSONArray("sources")?.widestSourceUrl()?.takeIf { isChannelAvatarUrl(it) }?.let { found = it }
        }
        return found
    }

    private fun isChannelAvatarUrl(url: String): Boolean = when {
        "ytimg.com/vi/" in url -> false
        "/ytc/" in url -> true
        "googleusercontent.com" in url && "-c-k-c0x00ffffff-no-rj" in url -> true
        "ggpht.com" in url && "=s" in url && "=w" !in url -> true
        else -> false
    }

    private fun JSONObject.bestChannelAvatarUrl(): String? =
        optJSONObject("image")
            ?.optJSONObject("decoratedAvatarViewModel")
            ?.optJSONObject("avatar")
            ?.optJSONObject("avatarViewModel")
            ?.optJSONObject("image")
            ?.optJSONArray("sources")
            ?.widestSourceUrl()
            ?: optJSONObject("image")?.collectStrings("url")
                ?.filter { "ggpht.com" in it || "googleusercontent.com" in it }
                ?.maxByOrNull { channelAvatarEdgePx(it) }

    internal fun pickBestThumbnailUrl(renderer: JSONObject): String? {
        renderer.bestThumbnailUrl()?.let { return it }
        renderer.optJSONObject("contentImage")?.bestThumbnailUrl()?.let { return it }
        renderer.optJSONObject("contentImage")
            ?.optJSONObject("thumbnailViewModel")
            ?.optJSONObject("image")
            ?.optJSONArray("sources")
            ?.let { pickWidestSourceUrl(it) }
            ?.let { return it }
        return renderer.optJSONObject("contentImage")?.collectStrings("url")
            ?.filter { "ytimg.com/vi/" in it || "googleusercontent.com" in it || "ggpht.com" in it }
            ?.maxWithOrNull(compareBy({ ytimgThumbnailWidth(it) }, { it.length }))
    }

    private fun pickWidestSourceUrl(arr: JSONArray): String? = arr.widestSourceUrl()

    private fun videoThumbnailUrl(renderer: JSONObject, videoId: String): String =
        sharpenFeedThumbnail(pickBestThumbnailUrl(renderer)?.let(::absoluteUrl), videoId)

    private val DURATION = Regex("^\\d{1,2}(:\\d{2}){1,2}$")

    private fun parseDuration(text: String?): Long {
        val t = text?.trim() ?: return 0
        if (!DURATION.matches(t)) return 0
        return t.split(':').fold(0L) { acc, part -> acc * 60 + (part.toLongOrNull() ?: 0) }
    }

    /** "1,234,567 views", "1.2M views", "No views" → count, or -1 when unknown. */
    internal fun parseViews(text: String?): Long {
        val t = text?.lowercase()?.replace(",", "")?.trim() ?: return -1
        if (t.startsWith("no ")) return 0
        val m = Regex("([\\d.]+)\\s*([kmb]?)").find(t) ?: return -1
        val n = m.groupValues[1].toDoubleOrNull() ?: return -1
        val mult = when (m.groupValues[2]) { "k" -> 1e3; "m" -> 1e6; "b" -> 1e9; else -> 1.0 }
        return (n * mult).toLong()
    }

    private fun absoluteUrl(url: String) = if (url.startsWith("//")) "https:$url" else url

    internal fun parseChannelHeader(root: JSONObject): ChannelHeaderMeta? {
        var handle: String? = null
        var subsText: String? = null
        var videosText: String? = null
        fun noteLine(line: String?) {
            val t = line?.trim()?.ifBlank { null } ?: return
            val lower = t.lowercase()
            if (subsText == null && "subscriber" in lower) subsText = t
            if (videosText == null) {
                VIDEO_COUNT_IN_LINE.find(lower)?.value?.let { videosText = it }
            }
        }
        fun scanMetadataParts(arr: JSONArray?) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                noteLine(arr.optJSONObject(i)?.optJSONObject("text")?.optString("content"))
            }
        }
        root.walk { _, obj ->
            noteLine(obj.text("subscriberCountText"))
            noteLine(obj.text("videoCountText"))
            noteLine(obj.optJSONObject("text")?.optString("content"))
            scanMetadataParts(obj.optJSONArray("metadataParts"))
            obj.optJSONArray("metadataRows")?.let { rows ->
                for (r in 0 until rows.length()) {
                    scanMetadataParts(rows.optJSONObject(r)?.optJSONArray("metadataParts"))
                }
            }
            obj.optString("vanityChannelUrl").takeIf { "@" in it }?.let { url ->
                handle = handle ?: url.substringAfter("@").substringBefore("?").substringBefore("/").ifBlank { null }
            }
            obj.optString("canonicalBaseUrl").takeIf { it.startsWith("/@") }?.let { path ->
                handle = handle ?: path.removePrefix("/@").substringBefore("/").ifBlank { null }
            }
        }
        if (subsText == null || videosText == null) {
            for (line in root.collectStrings("content") + root.collectStrings("simpleText")) {
                noteLine(line)
            }
        }
        val subscribers = parseViews(subsText)
        val videoCount = parseViews(videosText)
        val verified = root.optJSONObject("header")?.let { parseChannelVerified(it) } ?: false
        if (handle == null && subscribers < 0 && videoCount < 0 && !verified) return null
        return ChannelHeaderMeta(handle, subscribers.coerceAtLeast(0), videoCount.coerceAtLeast(-1), verified)
    }

    /** Verified / official-artist badge on the channel header (not on videos in the list). */
    internal fun parseChannelVerified(headerRoot: JSONObject): Boolean {
        headerRoot.optJSONObject("pageHeaderRenderer")?.optJSONObject("content")
            ?.optJSONObject("pageHeaderViewModel")?.optJSONObject("title")
            ?.optJSONObject("dynamicTextViewModel")?.optJSONObject("rendererContext")
            ?.optJSONObject("accessibilityContext")?.optString("label")?.let { label ->
                if (VERIFIED_CHANNEL_LABEL.containsMatchIn(label)) return true
            }
        var found = false
        headerRoot.walk { key, obj ->
            if (found) return@walk
            if (key == "metadataBadgeRenderer" && isVerifiedChannelBadge(obj.optString("style"))) found = true
        }
        return found
    }

    private fun isVerifiedChannelBadge(style: String): Boolean =
        style == "BADGE_STYLE_TYPE_VERIFIED" || style == "BADGE_STYLE_TYPE_VERIFIED_ARTIST"

    private val VERIFIED_CHANNEL_LABEL = Regex("verified|official artist", RegexOption.IGNORE_CASE)
    private val VIDEO_COUNT_IN_LINE = Regex("""[\d,.]+[kmb]?\s+videos\b""", RegexOption.IGNORE_CASE)
}

// ── JSON helpers ───────────────────────────────────────────────────────────────

/** Depth-first visit of every object-valued key in the tree. */
internal fun JSONObject.walk(visit: (key: String, obj: JSONObject) -> Unit) {
    val keys = keys()
    while (keys.hasNext()) {
        val k = keys.next()
        when (val v = opt(k)) {
            is JSONObject -> { visit(k, v); v.walk(visit) }
            is JSONArray -> v.walkArray(visit)
        }
    }
}

private fun JSONArray.walkArray(visit: (key: String, obj: JSONObject) -> Unit) {
    for (i in 0 until length()) {
        when (val v = opt(i)) {
            is JSONObject -> v.walk(visit)
            is JSONArray -> v.walkArray(visit)
        }
    }
}

/** Every JSONObject in the tree, including objects that sit directly in arrays (which [walk] skips). */
internal fun JSONObject.forEachObject(visit: (JSONObject) -> Unit) {
    visit(this)
    val keys = keys()
    while (keys.hasNext()) {
        when (val v = opt(keys.next())) {
            is JSONObject -> v.forEachObject(visit)
            is JSONArray -> v.forEachObjectInArray(visit)
        }
    }
}

private fun JSONArray.forEachObjectInArray(visit: (JSONObject) -> Unit) {
    for (i in 0 until length()) {
        when (val v = opt(i)) {
            is JSONObject -> v.forEachObject(visit)
            is JSONArray -> v.forEachObjectInArray(visit)
        }
    }
}

/** YouTube text: {"simpleText": "..."} or {"runs": [{"text": "..."}]}. */
internal fun JSONObject.text(key: String): String? {
    val o = optJSONObject(key) ?: return optString(key).ifBlank { null }
    o.optString("simpleText").ifBlank { null }?.let { return it }
    o.optString("content").ifBlank { null }?.let { return it }
    val runs = o.optJSONArray("runs") ?: return null
    return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }.ifBlank { null }
}

/** URLs from {"thumbnail": {"thumbnails": [...]}} or {"thumbnails": [...]}, smallest first. */
internal fun JSONObject.thumbnails(): List<String>? {
    val arr = optJSONObject("thumbnail")?.optJSONArray("thumbnails") ?: optJSONArray("thumbnails") ?: return null
    return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("url")?.ifBlank { null } }
}

internal fun parseImageAspectRatio(child: JSONObject): Float? {
    val imageObj = child.optJSONObject("image") ?: child
    val arr = imageObj.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        ?: imageObj.optJSONArray("thumbnails")
        ?: child.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        ?: return null
    for (i in (arr.length() - 1) downTo 0) {
        val o = arr.optJSONObject(i) ?: continue
        val w = o.optInt("width", 0)
        val h = o.optInt("height", 0)
        if (w > 0 && h > 0) {
            return (w.toFloat() / h.toFloat()).coerceIn(0.5f, 3.0f)
        }
    }
    return null
}

internal fun JSONArray.widestSourceUrl(): String? {
    var best: String? = null
    var bestW = -1
    for (i in 0 until length()) {
        val entry = optJSONObject(i) ?: continue
        val url = entry.optString("url").ifBlank { continue }
        val w = entry.optInt("width", channelAvatarEdgePx(url).takeIf { it > 0 } ?: ytimgThumbnailWidth(url))
        if (w > bestW) {
            bestW = w
            best = url
        }
    }
    return best ?: optJSONObject(length() - 1)?.optString("url")?.ifBlank { null }
}

/** Widest thumbnail entry on a renderer (typically 1280×720 on video cards). */
internal fun JSONObject.bestThumbnailUrl(): String? {
    val arr = optJSONObject("thumbnail")?.optJSONArray("thumbnails") ?: optJSONArray("thumbnails") ?: return null
    var best: String? = null
    var bestW = -1
    for (i in 0 until arr.length()) {
        val entry = arr.optJSONObject(i) ?: continue
        val url = entry.optString("url").ifBlank { continue }
        val w = entry.optInt("width", 0)
        if (w > bestW) {
            bestW = w
            best = url
        }
    }
    return best ?: arr.optJSONObject(arr.length() - 1)?.optString("url")?.ifBlank { null }
}

private fun JSONObject.firstImageSource(): String? = collectStrings("url").firstOrNull { it.contains("ytimg") || it.contains("ggpht") }

/** Every string value stored under [field] anywhere below this object. */
internal fun JSONObject.collectStrings(field: String): List<String> {
    val out = mutableListOf<String>()
    fun visitObj(o: JSONObject) {
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            when (val v = o.opt(k)) {
                is String -> if (k == field && v.isNotBlank()) out += v
                is JSONObject -> visitObj(v)
                is JSONArray -> for (i in 0 until v.length()) (v.opt(i) as? JSONObject)?.let(::visitObj)
            }
        }
    }
    visitObj(this)
    return out
}
