package com.thinker.flashtube

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Reading comments straight from YouTube's web `next` pages (signed in when there's a session,
 * so liked state comes along). NewPipeExtractor 0.26.5 dropped every commenter's picture; the
 * raw page has it (`author.avatarThumbnailUrl`, YouTube's own 88 px size, a few KB).
 *
 * Page shape (2026-10): the thread list is in `continuationItems` of
 * `reloadContinuationItemsCommand` / `appendContinuationItemsAction`; each `commentViewModel`
 * names its text, author and counts by `commentKey` → `commentEntityPayload`, and its like /
 * heart state by `toolbarStateKey`. Both live in `frameworkUpdates` entity mutations.
 */
object CommentFeed {
    /** Locate and load a live chat using the continuation in the watch-next response. */
    suspend fun firstLive(videoId: String): CommentsPage = withContext(Dispatchers.IO) {
        val root = InnerTube.postAny("next", JSONObject().put("videoId", videoId))
        var continuation: String? = null
        root.walk { key, obj ->
            if (key == "liveChatRenderer" && continuation == null) {
                continuation = obj.optJSONArray("continuations")?.optJSONObject(0)
                    ?.let { it.optJSONObject("reloadContinuationData") ?: it.optJSONObject("timedContinuationData") }
                    ?.optString("continuation")?.takeIf { it.isNotBlank() }
            }
        }
        val token = continuation ?: return@withContext CommentsPage(emptyList(), -1, true, null)
        livePage(InnerTube.postAny("live_chat/get_live_chat", JSONObject().put("continuation", token)))
    }

    suspend fun moreLive(token: String): CommentsPage = withContext(Dispatchers.IO) {
        livePage(InnerTube.postAny("live_chat/get_live_chat", JSONObject().put("continuation", token)))
    }

    suspend fun liveViewerCount(videoId: String): String? = withContext(Dispatchers.IO) {
        val root = InnerTube.postAny("updated_metadata", JSONObject().put("videoId", videoId))
        var count: String? = null
        root.walk { key, obj ->
            if (key == "videoViewCountRenderer" && obj.optBoolean("isLive")) {
                count = obj.optJSONObject("viewCount")?.text("simpleText")
                    ?: obj.optJSONObject("extraShortViewCount")?.text("simpleText")
            }
        }
        count
    }

    internal fun livePage(root: JSONObject): CommentsPage {
        val chat = root.optJSONObject("continuationContents")?.optJSONObject("liveChatContinuation")
            ?: return CommentsPage(emptyList(), -1, true, null)
        val messages = ArrayList<Comment>()
        val actions = chat.optJSONArray("actions")
        val removed = linkedSetOf<String>()
        for (i in 0 until (actions?.length() ?: 0)) {
            val action = actions?.optJSONObject(i) ?: continue
            action.optJSONObject("removeChatItemAction")?.optString("targetItemId")
                ?.takeIf { it.isNotBlank() }?.let(removed::add)
            action.optJSONObject("markChatItemAsDeletedAction")?.optString("targetItemId")
                ?.takeIf { it.isNotBlank() }?.let(removed::add)
            val item = action.optJSONObject("addChatItemAction")?.optJSONObject("item") ?: continue
            val rendererKey = listOf("liveChatTextMessageRenderer", "liveChatPaidMessageRenderer", "liveChatMembershipItemRenderer")
                .firstOrNull { item.has(it) } ?: continue
            val renderer = item.optJSONObject(rendererKey) ?: continue
            val authorName = renderer.optJSONObject("authorName")?.let { it.text("simpleText") ?: it.text("runs") }.orEmpty()
            val channel = renderer.optString("authorExternalChannelId").takeIf { it.startsWith("UC") }
            val avatar = renderer.optJSONObject("authorPhoto")?.optJSONArray("thumbnails")?.optJSONObject(0)
                ?.optString("url")?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("//")) "https:$it" else it }
            val text = renderer.optJSONObject("message")?.let { message ->
                message.optString("simpleText").takeIf { it.isNotBlank() }
                    ?: message.optJSONArray("runs")?.let { runs -> (0 until runs.length()).mapNotNull { runs.optJSONObject(it)?.optString("text")?.takeIf(String::isNotBlank) }.joinToString("") }
            }.orEmpty().ifBlank {
                renderer.optJSONObject("headerSubtext")?.text("simpleText").orEmpty()
            }
            if (text.isBlank() || authorName.isBlank()) continue
            val id = renderer.optString("id").ifBlank { "live-${channel.orEmpty()}-$i-${text.hashCode()}" }
            messages += Comment(id, authorName, channel, avatar = avatar, text = text, likes = 0,
                ago = "live", pinned = false, hearted = false, replyCount = 0, repliesToken = null)
        }
        val next = chat.optJSONArray("continuations")?.optJSONObject(0)?.let { c ->
            c.optJSONObject("invalidationContinuationData") ?: c.optJSONObject("timedContinuationData") ?: c.optJSONObject("reloadContinuationData")
        }
        val input = chat.optJSONObject("actionPanel")?.optJSONObject("liveChatMessageInputRenderer")
        val restriction = chat.optJSONObject("actionPanel")?.optJSONObject("liveChatRestrictedParticipationRenderer")
            ?.optJSONObject("message")?.text("simpleText")
        return CommentsPage(
            comments = messages.distinctBy { it.id }, total = -1, disabled = false,
            next = next?.optString("continuation")?.takeIf { it.isNotBlank() },
            liveSendParams = input?.optJSONObject("sendButton")?.optJSONObject("buttonRenderer")
                ?.optJSONObject("serviceEndpoint")?.optJSONObject("sendLiveChatMessageEndpoint")?.optString("params")?.takeIf { it.isNotBlank() },
            liveTimeoutMs = next?.optLong("timeoutMs")?.takeIf { it > 0 } ?: 10_000L,
            liveMaxMessageLength = input?.optJSONObject("inputField")?.optJSONObject("liveChatTextInputFieldRenderer")
                ?.optInt("maxCharacterLimit")?.takeIf { it > 0 } ?: 200,
            liveRestriction = restriction?.takeIf { it.isNotBlank() },
            removedIds = removed,
        )
    }

    suspend fun first(videoId: String): CommentsPage = withContext(Dispatchers.IO) {
        var token: String? = null
        InnerTube.postAny("next", JSONObject().put("videoId", videoId)).forEachObject { obj ->
            if (token == null && obj.optString("sectionIdentifier") == "comment-item-section") {
                token = obj.collectStrings("token").firstOrNull()
            }
        }
        // No comments section at all: they're turned off for this video.
        val start = token ?: return@withContext CommentsPage(emptyList(), total = -1, disabled = true, next = null)
        parsePage(InnerTube.postAny("next", JSONObject().put("continuation", start)))
    }

    /** The next page of comments, or one thread's replies (same call, different token). */
    suspend fun more(token: String): CommentsPage = withContext(Dispatchers.IO) {
        parsePage(InnerTube.postAny("next", JSONObject().put("continuation", token)))
    }

    suspend fun firstPost(params: String): CommentsPage = withContext(Dispatchers.IO) {
        parsePage(InnerTube.postAny("browse", JSONObject().put("params", params)))
    }

    suspend fun morePost(token: String): CommentsPage = withContext(Dispatchers.IO) {
        parsePage(InnerTube.postAny("browse", JSONObject().put("continuation", token)))
    }

    internal fun parsePage(page: JSONObject): CommentsPage {
        val entities = HashMap<String, JSONObject>()
        val mutations = page.optJSONObject("frameworkUpdates")
            ?.optJSONObject("entityBatchUpdate")
            ?.optJSONArray("mutations")
        if (mutations != null) {
            for (i in 0 until mutations.length()) {
                val obj = mutations.optJSONObject(i) ?: continue
                val key = obj.optString("entityKey")
                val payload = obj.optJSONObject("payload")
                if (key.isNotEmpty() && payload != null) entities[key] = payload
            }
        } else {
            page.forEachObject { obj ->
                val key = obj.optString("entityKey")
                val payload = obj.optJSONObject("payload")
                if (key.isNotEmpty() && payload != null) entities[key] = payload
            }
        }
        val comments = ArrayList<Comment>()
        var total = -1
        var next: String? = null
        page.walk { key, obj ->
            if (key != "reloadContinuationItemsCommand" && key != "appendContinuationItemsAction") return@walk
            val items = obj.optJSONArray("continuationItems") ?: return@walk
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                when {
                    item.has("commentsHeaderRenderer") -> item.getJSONObject("commentsHeaderRenderer").text("countText")
                        ?.let { InnerTube.parseViews(it) }?.takeIf { it >= 0 }?.let { total = it.toInt() }
                    item.has("commentThreadRenderer") -> {
                        val thread = item.getJSONObject("commentThreadRenderer")
                        val repliesToken = thread.optJSONObject("replies")?.collectStrings("token")?.firstOrNull()
                        thread.viewModel()?.let { vm -> parseComment(vm, entities, repliesToken)?.let(comments::add) }
                    }
                    item.has("commentViewModel") -> item.viewModel()?.let { vm -> parseComment(vm, entities, null)?.let(comments::add) }
                    item.has("continuationItemRenderer") -> item.collectStrings("token").firstOrNull()?.let { next = it }
                }
            }
        }
        val unique = comments.distinctBy { it.id }
        return CommentsPage(unique, total, disabled = false, next = next)
    }

    private fun JSONObject.viewModel(): JSONObject? {
        var found: JSONObject? = null
        walk { key, obj -> if (found == null && key == "commentViewModel" && obj.has("commentKey")) found = obj }
        return found
    }

    private fun parseComment(vm: JSONObject, entities: Map<String, JSONObject>, repliesToken: String?): Comment? {
        val entity = entities[vm.optString("commentKey")]?.optJSONObject("commentEntityPayload") ?: return null
        val props = entity.optJSONObject("properties") ?: return null
        val author = entity.optJSONObject("author")
        val toolbar = entity.optJSONObject("toolbar")
        val state = entities[vm.optString("toolbarStateKey")]?.optJSONObject("engagementToolbarStateEntityPayload")
        val replyCount = toolbar?.optString("replyCount")?.let { InnerTube.parseViews(it) }?.takeIf { it > 0 }?.toInt() ?: 0
        val displayName = author?.optString("displayName")?.takeIf { it.isNotBlank() }
            ?: author?.optString("channelTitle")?.takeIf { it.isNotBlank() }
            ?: author?.optString("commentId")
        return Comment(
            id = props.optString("commentId").ifBlank { vm.optString("commentId") }.ifBlank { return null },
            author = displayName ?: "Anonymous",
            channelId = author?.optString("channelId")?.takeIf { it.startsWith("UC") },
            avatar = author?.optString("avatarThumbnailUrl")?.ifBlank { null }?.let { if (it.startsWith("//")) "https:$it" else it },
            text = props.optJSONObject("content")?.optString("content").orEmpty().trim(),
            likes = toolbar?.optString("likeCountNotliked")?.let { InnerTube.parseViews(it) }?.takeIf { it > 0 }?.toInt() ?: 0,
            ago = props.optString("publishedTime").ifBlank { null },
            pinned = vm.has("pinnedText") || props.has("pinnedText"),
            hearted = state?.optString("heartState") == "TOOLBAR_HEART_STATE_HEARTED",
            replyCount = replyCount,
            repliesToken = repliesToken?.takeIf { replyCount > 0 },
            liked = state?.optString("likeState") == "TOOLBAR_LIKE_STATE_LIKED",
        )
    }
}

/**
 * Commenters' display names are now parsed directly from author.displayName in the comment payload.
 */
object ChannelNames {
    val known = mutableStateMapOf<String, String>()

    fun resolve(scope: CoroutineScope, channelIds: Collection<String>) {
        // No-op: author.displayName is extracted directly from the JSON payload.
    }
}

/** What the comments show as the author: the real name once known, else the @handle. */
fun Comment.displayName(): String = name ?: author

/**
 * Channel pictures for feed rows that only name the uploader (Watch later / playlistVideoRenderer).
 * Filled from subscription avatars when available, else one channel browse per id (cached).
 */
object ChannelAvatars {
    val known = mutableStateMapOf<String, String>()
    private val asked = ConcurrentHashMap.newKeySet<String>()
    private val gate = Semaphore(4)

    fun resolve(scope: CoroutineScope, videos: List<VideoItem>, seed: Map<String, String?> = emptyMap()) {
        seed.forEach { (id, url) ->
            if (url != null) {
                val normalized = normalizeChannelAvatarUrl(url)
                if (known[id] != normalized) known[id] = normalized
            }
        }
        val need = mutableSetOf<String>()
        for (v in videos) {
            if (v.avatar == null) {
                v.channelId()?.let { need.add(it) }
            }
            for (collab in v.collabChannels) {
                if (collab.avatar == null && collab.id.startsWith("UC")) {
                    need.add(collab.id)
                }
            }
        }
        need.filter { it !in known && asked.add(it) }.forEach { id ->
            scope.launch {
                gate.withPermit {
                    withContext(Dispatchers.IO) {
                        runCatching { InnerTube.channelAvatarUrl(id) }
                            .onSuccess { url -> if (url != null && known[id] != url) known[id] = url }
                            .onFailure { asked.remove(id) }
                    }
                }
            }
        }
    }
}
