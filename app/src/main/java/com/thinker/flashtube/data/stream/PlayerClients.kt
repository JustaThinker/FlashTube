package com.thinker.flashtube.data.stream

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val STREAM_TAG = "YouTubeStreams"

/**
 * One InnerTube client identity a `/player` call can be made as.
 */
class PlayerClient(
    val name: String,
    val version: String,
    val id: Int,
    val userAgent: String,
    val fields: Map<String, Any>,
    val sendsPlaybackNonce: Boolean = false,
)

object PlayerClients {
    const val VISION_OS_USER_AGENT =
        "com.google.visionos.youtube/1.02(RealityDevice14,1; U; CPU visionOS 25_6_0 like Mac OS X; GB)"
    val VISION_OS = PlayerClient(
        name = "VISIONOS",
        version = "1.02",
        id = 101,
        userAgent = VISION_OS_USER_AGENT,
        fields = mapOf(
            "clientScreen" to "WATCH",
            "platform" to "MOBILE",
            "deviceMake" to "Apple",
            "deviceModel" to "RealityDevice14,1",
            "osName" to "visionOS",
            "osVersion" to "25.6.0.23O471",
        ),
        sendsPlaybackNonce = true,
    )

    const val ANDROID_VR_USER_AGENT =
        "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    val ANDROID_VR = PlayerClient(
        name = "ANDROID_VR",
        version = "1.65.10",
        id = 28,
        userAgent = ANDROID_VR_USER_AGENT,
        fields = mapOf(
            "androidSdkVersion" to 32,
            "deviceMake" to "Oculus",
            "deviceModel" to "Quest 3",
            "osName" to "Android",
            "osVersion" to "12L",
        ),
    )

    const val IOS_USER_AGENT =
        "com.google.ios.youtube/21.02.3 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X)"
    val IOS = PlayerClient(
        name = "IOS",
        version = "21.02.3",
        id = 5,
        userAgent = IOS_USER_AGENT,
        fields = mapOf(
            "deviceMake" to "Apple",
            "deviceModel" to "iPhone16,2",
            "osName" to "iPhone",
            "osVersion" to "18.1.0.22B83",
        ),
    )

    const val NEWPIPE_ANDROID_USER_AGENT =
        "com.google.android.youtube/21.03.36 (Linux; U; Android 15; GB) gzip"

    const val TV_EMBED_USER_AGENT =
        "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Safari/605.1.15"
}

internal fun userAgentForStreamUrl(url: String, browserUserAgent: String): String =
    userAgentForStreamClient(url.toHttpUrlOrNull()?.queryParameter("c"), browserUserAgent)

internal fun userAgentForStreamClient(client: String?, browserUserAgent: String): String =
    when (client?.uppercase()) {
        "IOS" -> PlayerClients.IOS_USER_AGENT
        "ANDROID_VR" -> PlayerClients.ANDROID_VR_USER_AGENT
        "ANDROID", "ANDROID_TESTSUITE", "ANDROID_MUSIC" -> PlayerClients.NEWPIPE_ANDROID_USER_AGENT
        "VISIONOS" -> PlayerClients.VISION_OS_USER_AGENT
        "TVHTML5_SIMPLY_EMBEDDED_PLAYER", "TVHTML5_SIMPLY_EMBEDDED", "TVHTML5" ->
            PlayerClients.TV_EMBED_USER_AGENT
        else -> browserUserAgent
    }
