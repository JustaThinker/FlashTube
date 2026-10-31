package com.thinker.flashtube.data.stream

import android.util.Log
import com.thinker.flashtube.data.VisitorIdentity
import com.thinker.flashtube.data.VideoStreamResolutionCache
import org.json.JSONObject

class VisionOsOutcome(
    val answer: PlayerAnswer.Playable?,
    val botChecked: Boolean,
    val visitorData: String = "",
)

class PlayerSession(
    private val identity: VisitorIdentity,
    private val api: PlayerApi,
    private val probe: StreamProbe,
) {
    suspend fun visionOsVetted(
        videoId: String,
        probeUrl: (streamingData: JSONObject) -> String?,
    ): VisionOsOutcome {
        for (round in 1..PROBE_ROUNDS) {
            val direct = visionOs(videoId)
            val answer = direct.answer ?: return direct
            val token = direct.visitorData
            if (token.isBlank() || identity.isVetted(token)) return direct
            val url = probeUrl(answer.streamingData) ?: return direct
            when (probe.servesLastByte(url)) {
                true -> {
                    identity.markVetted(token)
                    return direct
                }
                null -> return direct
                false -> {
                    Log.w(
                        STREAM_TAG,
                        "Resolve[visionOS] googlevideo refuses this visitorData past the opening " +
                            "(round $round/$PROBE_ROUNDS) videoId=$videoId",
                    )
                    if (round == PROBE_ROUNDS) return direct
                    VideoStreamResolutionCache.dropResolved()
                    identity.replace(token) ?: return direct
                }
            }
        }
        error("unreachable")
    }

    suspend fun visionOs(videoId: String): VisionOsOutcome {
        val token = currentOrMinted()
        when (val first = player(videoId, PlayerClients.VISION_OS, token)) {
            is PlayerAnswer.Playable -> return VisionOsOutcome(first, botChecked = false, token)
            is PlayerAnswer.IdentityRefused -> Unit
            else -> return VisionOsOutcome(null, botChecked = false)
        }
        if (BotCheckVerdict.isActive()) return VisionOsOutcome(null, botChecked = true)

        Log.w(STREAM_TAG, "Player[VISIONOS] visitorData refused, replacing it videoId=$videoId")
        val fresh = identity.replace(token)
        if (fresh == null || fresh == token) return VisionOsOutcome(null, botChecked = false)
        return when (val retry = player(videoId, PlayerClients.VISION_OS, fresh)) {
            is PlayerAnswer.Playable -> VisionOsOutcome(retry, botChecked = false, fresh)
            is PlayerAnswer.IdentityRefused -> {
                BotCheckVerdict.note(videoId)
                VisionOsOutcome(null, botChecked = true)
            }
            else -> VisionOsOutcome(null, botChecked = false)
        }
    }

    suspend fun nativeFallback(
        videoId: String,
        botCheckedAlready: Boolean = false,
        onResponse: (JSONObject) -> Unit = {},
    ): PlayerAnswer.Playable? {
        val token = currentOrMinted()
        if (token.isBlank()) {
            Log.w(STREAM_TAG, "No visitorData available for videoId=$videoId, bot check will refuse")
        }
        val first = androidVrThenIos(videoId, token, onResponse)
        first.playable?.let {
            BotCheckVerdict.clearOnSuccess()
            return it
        }
        if (!first.identityRefused) return null

        if (botCheckedAlready || BotCheckVerdict.isActive()) {
            Log.w(STREAM_TAG, "Bot check refused a fresh identity already, not replacing videoId=$videoId")
            BotCheckVerdict.note(videoId)
            return null
        }

        Log.w(STREAM_TAG, "visitorData refused by the bot check, replacing and retrying videoId=$videoId")
        val fresh = identity.replace(token) ?: return null
        if (fresh == token) return null
        val retry = androidVrThenIos(videoId, fresh, onResponse)
        retry.playable?.let {
            BotCheckVerdict.clearOnSuccess()
            return it
        }
        if (retry.identityRefused) BotCheckVerdict.note(videoId)
        return null
    }

    suspend fun single(videoId: String, client: PlayerClient): PlayerAnswer =
        player(videoId, client, identity.current())

    private suspend fun currentOrMinted(): String =
        identity.current().ifBlank { identity.replace(refused = "").orEmpty() }

    private suspend fun player(videoId: String, client: PlayerClient, token: String): PlayerAnswer =
        api.player(videoId, client, token).also {
            if (it is PlayerAnswer.Playable) identity.noteSigned(it.streamingData, token)
        }

    private companion object {
        const val PROBE_ROUNDS = 3
    }

    private class PairOutcome(val playable: PlayerAnswer.Playable?, val identityRefused: Boolean)

    private suspend fun androidVrThenIos(
        videoId: String,
        token: String,
        onResponse: (JSONObject) -> Unit,
    ): PairOutcome {
        val vr = player(videoId, PlayerClients.ANDROID_VR, token)
        vr.okRoot()?.let(onResponse)
        if (vr is PlayerAnswer.Playable) return PairOutcome(vr, identityRefused = false)

        val ios = player(videoId, PlayerClients.IOS, token)
        ios.okRoot()?.let(onResponse)
        return PairOutcome(
            playable = ios as? PlayerAnswer.Playable,
            identityRefused = vr is PlayerAnswer.IdentityRefused || ios is PlayerAnswer.IdentityRefused,
        )
    }
}

fun PlayerAnswer.okRoot(): JSONObject? = when (this) {
    is PlayerAnswer.Playable -> root
    is PlayerAnswer.IdentityRefused -> root
    else -> null
}
