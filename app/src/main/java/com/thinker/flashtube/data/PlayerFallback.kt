package com.thinker.flashtube

import android.util.Log
import com.thinker.flashtube.data.ContentRegion
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * "Sign in to confirm you're not a bot" fallback mechanism:
 * When YouTube's watch page or player response is blocked by bot detection, rate limits, or login prompts,
 * this intercepts the HTTP response body and transparently substitutes it with an unblocked
 * client response (TVHTML5 / ANDROID_VR / WEB_EMBEDDED_PLAYER).
 */
object PlayerFallback {
    private const val TAG = "PlayerFallback"

    /**
     * Handles any player or watch endpoint called by the extractor:
     * - standard `/youtubei/v1/player`
     * - shorts reel player `/youtubei/v1/reel/reel_item_watch`
     * - watch page `/watch?v=`
     */
    fun handles(url: String) = "/youtubei/v1/reel/reel_item_watch" in url ||
                               "/youtubei/v1/player" in url ||
                               "/watch" in url

    /**
     * [responseText] is YouTube's answer to the request at [url]. Returns it as is
     * unless it's a bot check; then queries fallback clients so that NewPipeExtractor
     * receives valid stream metadata without erroring out.
     */
    fun substitute(url: String, requestBody: ByteArray?, responseText: String?): String? {
        if (responseText == null || !isBotCheck(responseText)) return responseText

        val videoIdFromReq = if (requestBody != null) {
            val reqObj = runCatching { JSONObject(String(requestBody)) }.getOrNull()
            val playerObj = reqObj?.optJSONObject("playerRequest") ?: reqObj
            playerObj?.optString("videoId")?.ifEmpty { null }
        } else null

        val videoId = videoIdFromReq ?: extractVideoIdFromUrl(url) ?: return responseText

        val origClient = if (requestBody != null) {
            runCatching { JSONObject(String(requestBody)) }.getOrNull()?.optJSONObject("context")?.optJSONObject("client")
        } else null

        // Battle-tested fallback client configs
        val clients = listOf(
            // 1. TVHTML5 (Smart TV / Tizen) - no bot challenge mechanism, unthrottled streams
            ClientConfig(
                name = "TVHTML5",
                id = "7",
                version = "7.20260213.00.00",
                userAgent = "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 (KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15",
                osName = "Tizen",
                osVersion = "4.0.0.2",
                deviceMake = "Samsung",
                deviceModel = "SmartTV",
                platform = "TV"
            ),
            // 2. Android VR 1.65.10 (Meta Quest 3) - rarely challenged
            ClientConfig(
                name = "ANDROID_VR",
                id = "28",
                version = "1.65.10",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                osName = "Android",
                osVersion = "12L",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                platform = "MOBILE"
            ),
            // 3. Web Embedded Player - public embed endpoint
            ClientConfig(
                name = "WEB_EMBEDDED_PLAYER",
                id = "56",
                version = "1.20260213.00.00",
                userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                osName = "Windows",
                osVersion = "10.0",
                deviceMake = "",
                deviceModel = "",
                platform = "DESKTOP"
            ),
            // 4. VisionOS 1.02
            ClientConfig(
                name = "VISIONOS",
                id = "101",
                version = "1.02",
                userAgent = "com.google.ios.youtube/1.02 (RealityDevice14,1; U; CPU visionOS 25_6_0 like Mac OS X)",
                osName = "visionOS",
                osVersion = "25.6.0.23O471",
                deviceMake = "Apple",
                deviceModel = "RealityDevice14,1",
                platform = "MOBILE"
            )
        )

        for (cfg in clients) {
            val answer = runCatching { ask(videoId, origClient, cfg) }
                .onFailure { Log.w(TAG, "Fallback ${cfg.name} request failed for $videoId", it) }
                .getOrNull() ?: continue
            val status = answer.optJSONObject("playabilityStatus")?.optString("status")
            if (status == "OK") {
                Log.i(TAG, "Bypassed bot check for $videoId on $url using ${cfg.name}")
                return when {
                    "/watch" in url -> {
                        if ("ytInitialPlayerResponse" in responseText) {
                            responseText.replace(
                                Regex("""(var\s+ytInitialPlayerResponse\s*=\s*|\bwindow\["ytInitialPlayerResponse"]\s*=\s*|\bytInitialPlayerResponse\s*=\s*)(\{.+?\});""", RegexOption.DOT_MATCHES_ALL),
                                "var ytInitialPlayerResponse = ${answer};"
                            )
                        } else {
                            "<!DOCTYPE html><html><head><script>var ytInitialPlayerResponse = ${answer};</script></head><body></body></html>"
                        }
                    }
                    "/reel" in url -> {
                        JSONObject().put("playerResponse", answer).toString()
                    }
                    else -> answer.toString()
                }
            } else {
                Log.w(TAG, "Fallback ${cfg.name} returned status $status for $videoId")
            }
        }
        return responseText
    }

    private fun extractVideoIdFromUrl(url: String): String? {
        val vParam = url.substringAfter("v=", "").substringBefore("&").substringBefore("#").trim()
        return if (vParam.length >= 11) vParam.take(11) else null
    }

    private data class ClientConfig(
        val name: String,
        val id: String,
        val version: String,
        val userAgent: String,
        val osName: String,
        val osVersion: String,
        val deviceMake: String,
        val deviceModel: String,
        val platform: String
    )

    private fun isBotCheck(body: String): Boolean {
        if ("a bot" in body || "confirm you're not a bot" in body || "robot" in body) return true
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return false
        val status = (root.optJSONObject("playerResponse") ?: root).optJSONObject("playabilityStatus") ?: return false
        val code = status.optString("status")
        val reason = status.optString("reason")
        return (code.equals("LOGIN_REQUIRED", ignoreCase = true) && ("a bot" in reason || "confirm" in reason || reason.isBlank())) ||
               code.equals("UNPLAYABLE", ignoreCase = true) ||
               code.equals("ERROR", ignoreCase = true)
    }

    private fun ask(videoId: String, original: JSONObject?, cfg: ClientConfig): JSONObject {
        val client = JSONObject()
            .put("clientName", cfg.name)
            .put("clientVersion", cfg.version)
            .put("clientScreen", if (cfg.name == "WEB_EMBEDDED_PLAYER") "EMBED" else "WATCH")
            .put("platform", cfg.platform)
            .put("deviceMake", cfg.deviceMake)
            .put("deviceModel", cfg.deviceModel)
            .put("osName", cfg.osName)
            .put("osVersion", cfg.osVersion)
            .put("hl", original?.optString("hl")?.ifEmpty { null } ?: ContentRegion.currentLanguageCode)
            .put("gl", original?.optString("gl")?.ifEmpty { null } ?: ContentRegion.currentRegionCode)

        val body = JSONObject()
            .put("context", JSONObject().put("client", client))
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .apply {
                if (cfg.name == "WEB_EMBEDDED_PLAYER") {
                    put("thirdPartyEmbedUrl", "https://www.youtube.com/embed/$videoId")
                }
            }

        val request = okhttp3.Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", cfg.userAgent)
            .header("X-Goog-Api-Format-Version", "2")
            .header("X-YouTube-Client-Name", cfg.id)
            .header("X-YouTube-Client-Version", cfg.version)
            .apply {
                if (Account.signedIn) {
                    val cookies = Account.cookieHeader()
                    if (cookies.isNotEmpty()) header("Cookie", cookies)
                    Account.authorization()?.let { header("Authorization", it) }
                }
            }
            .build()

        Net.client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            return JSONObject(response.body!!.string())
        }
    }
}