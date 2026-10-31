package com.thinker.flashtube

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

internal class YouTubeRateLimitException(val retryAtMs: Long) : IOException(
    YouTubeRequestLimiter.message(retryAtMs),
)

private object YouTubeRequestLimiter {
    private const val MIN_REQUEST_INTERVAL_MS = 350L
    private const val DEFAULT_COOLDOWN_MS = 60_000L
    private const val MAX_COOLDOWN_MS = 15 * 60_000L
    private val lock = Object()
    private var nextRequestAtMs = 0L
    private var cooldownUntilMs = 0L

    fun awaitRequestSlot() {
        while (true) {
            val waitMs = synchronized(lock) {
                val now = System.currentTimeMillis()
                if (cooldownUntilMs > now) throw YouTubeRateLimitException(cooldownUntilMs)
                val delay = nextRequestAtMs - now
                if (delay <= 0L) {
                    nextRequestAtMs = now + MIN_REQUEST_INTERVAL_MS
                    return
                }
                delay
            }
            try {
                Thread.sleep(waitMs)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted while waiting to contact YouTube.", e)
            }
        }
    }

    fun recordRateLimit(retryAfter: String?) {
        val now = System.currentTimeMillis()
        val requestedDelay = retryAfter?.let(::retryAfterMs) ?: DEFAULT_COOLDOWN_MS
        val delay = requestedDelay.coerceIn(1_000L, MAX_COOLDOWN_MS)
        synchronized(lock) {
            cooldownUntilMs = maxOf(cooldownUntilMs, now + delay)
            nextRequestAtMs = maxOf(nextRequestAtMs, cooldownUntilMs)
            lock.notifyAll()
        }
    }

    fun currentFailure(): YouTubeRateLimitException = synchronized(lock) {
        YouTubeRateLimitException(cooldownUntilMs.takeIf { it > System.currentTimeMillis() } ?: System.currentTimeMillis() + DEFAULT_COOLDOWN_MS)
    }

    fun reset() = synchronized(lock) {
        cooldownUntilMs = 0L
        nextRequestAtMs = 0L
        lock.notifyAll()
    }

    fun message(retryAtMs: Long): String {
        val seconds = ((retryAtMs - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1000L
        val wait = if (seconds >= 60L) "about ${(seconds + 59L) / 60L} minutes" else "$seconds seconds"
        return "YouTube is temporarily rate-limiting requests. Please wait $wait before retrying."
    }

    private fun retryAfterMs(value: String): Long? {
        value.trim().toLongOrNull()?.let { return it.coerceIn(0L, MAX_COOLDOWN_MS / 1_000L) * 1_000L }
        return runCatching {
            (java.time.ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() -
                System.currentTimeMillis()).coerceAtLeast(0L)
        }.getOrNull()
    }
}

/** One HTTP client for the whole app: extractor requests and media playback share it. */
object Net {
    // NewPipe uses a desktop Firefox UA; YouTube serves the web client pages it parses.
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    val streamDataSourceFactory by lazy {
        com.thinker.flashtube.data.ChunkedStreamDataSource.Factory()
    }

    internal fun youtubeRateLimitFailure(): IOException = YouTubeRequestLimiter.currentFailure()

    fun onNetworkChanged() {
        YouTubeRequestLimiter.reset()
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val host = chain.request().url.host
                val isYouTubeRequest = host == "youtube.com" || host.endsWith(".youtube.com") ||
                    host == "youtu.be" || host.endsWith(".youtu.be")
                if (!isYouTubeRequest) return@addInterceptor chain.proceed(chain.request())
                YouTubeRequestLimiter.awaitRequestSlot()
                chain.proceed(chain.request()).also { response ->
                    if (response.code == 429) {
                        YouTubeRequestLimiter.recordRateLimit(response.header("Retry-After"))
                    }
                }
            }
            // Idle connections to the video servers stay warm between videos.
            .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
            .build()
    }

    /** The video-server host YouTube last handed out; usually the same one next time. */
    @Volatile private var mediaHost: String? = null

    fun noteMediaHost(url: String?) {
        url?.toHttpUrlOrNull()?.host?.let { mediaHost = it }
    }

    /**
     * Opens (DNS + TLS) a connection to the likely video server while the stream data is
     * still being extracted, so the player's first requests skip ~0.7 s of setup. A cheap
     * request to /generate_204; if YouTube picks another host this time, nothing is lost.
     */
    fun prewarmMediaConnection() {
        val host = mediaHost ?: return
        val request = okhttp3.Request.Builder().url("https://$host/generate_204").head().header("User-Agent", USER_AGENT).build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) = response.close()
        })
    }
}

/** Bridges NewPipeExtractor's HTTP abstraction onto OkHttp. */
class NewPipeDownloader private constructor(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), body)
            .url(request.url())
            .addHeader("User-Agent", Net.USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        if (request.url().contains("youtube.com") && Account.signedIn) {
            val cookies = Account.cookieHeader()
            if (cookies.isNotEmpty() && !request.headers().containsKey("Cookie")) {
                builder.addHeader("Cookie", cookies)
            }
            val auth = Account.authorization()
            if (auth != null && !request.headers().containsKey("Authorization")) {
                builder.addHeader("Authorization", auth)
            }
        }
        client.newCall(builder.build()).execute().use { response ->
            // YouTube answers rate-limited clients with 429 + a captcha page.
            if (response.code == 429) throw ReCaptchaException("reCaptcha challenge requested", request.url())
            val text = response.body?.string()
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                if (PlayerFallback.handles(request.url())) PlayerFallback.substitute(request.url(), request.dataToSend(), text) else text,
                response.request.url.toString(),
            )
        }
    }

    companion object {
        val instance: NewPipeDownloader by lazy { NewPipeDownloader(Net.client) }
    }
}
