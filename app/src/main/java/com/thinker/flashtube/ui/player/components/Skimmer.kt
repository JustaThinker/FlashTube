package com.thinker.flashtube.ui.player.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thinker.flashtube.Net
import com.thinker.flashtube.StreamCache
import com.thinker.flashtube.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.schabi.newpipe.extractor.stream.Frameset
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Storyboard / Skimmer frame specification parsed from YouTube InnerTube or NewPipe extractor.
 */
data class StoryboardSpec(
    val frameWidth: Int,
    val frameHeight: Int,
    val framesPerPageX: Int,
    val framesPerPageY: Int,
    val totalFrames: Int,
    val durationPerFrameMs: Long,
    val urls: List<String>,
)

/**
 * Parses YouTube InnerTube storyboard spec string into structured [StoryboardSpec].
 * Example InnerTube spec format:
 * "https://i.ytimg.com/sb/VIDEO_ID/storyboard_3_L$L/$N.jpg?...#48#27#100#10#10#0#default#rs$..."
 */
object InnerTubeStoryboardParser {
    fun parseSpec(specString: String): StoryboardSpec? {
        if (specString.isBlank()) return null
        return runCatching {
            val parts = specString.split("#")
            if (parts.size < 6) return null
            val baseUrl = parts[0]
            val width = parts[1].toIntOrNull() ?: 80
            val height = parts[2].toIntOrNull() ?: 45
            val totalFrames = parts[3].toIntOrNull() ?: 100
            val cols = parts[4].toIntOrNull() ?: 10
            val rows = parts[5].toIntOrNull() ?: 10
            val framesPerPage = cols * rows
            val pageCount = if (framesPerPage > 0) (totalFrames + framesPerPage - 1) / framesPerPage else 1

            val urls = (0 until pageCount).map { pageIndex ->
                baseUrl.replace("\$L", "2").replace("\$N", "M$pageIndex")
            }

            StoryboardSpec(
                frameWidth = width,
                frameHeight = height,
                framesPerPageX = cols,
                framesPerPageY = rows,
                totalFrames = totalFrames,
                durationPerFrameMs = 2000L,
                urls = urls,
            )
        }.getOrNull()
    }
}

/**
 * High-performance Video Skimmer engine managing sprite caching, frame extraction,
 * and memory management for video timeline scrub previewing.
 */
object VideoSkimmer {
    private const val TAG = "VideoSkimmer"
    private const val MAX_SPRITE_CACHE_BYTES = 20 * 1024 * 1024 // 20 MB memory cache
    private const val MAX_FRAME_CACHE_ENTRIES = 50

    private val framesetMap = ConcurrentHashMap<String, Frameset>()
    private val specMap = ConcurrentHashMap<String, StoryboardSpec>()

    // Memory LRU Cache for full sprite sheet Bitmaps
    private val spriteCache = object : LruCache<String, Bitmap>(MAX_SPRITE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // Memory LRU Cache for extracted preview frames
    private val frameCache = LruCache<String, Bitmap>(MAX_FRAME_CACHE_ENTRIES)

    /** Preload storyboards for a video in background */
    suspend fun preload(videoId: String) {
        withContext(Dispatchers.IO) {
            resolveFrameset(videoId)
        }
    }

    /** Register an InnerTube storyboard spec */
    fun registerSpec(videoId: String, specString: String) {
        InnerTubeStoryboardParser.parseSpec(specString)?.let {
            specMap[videoId] = it
        }
    }

    /** Clear cached data for a specific video */
    fun clear(videoId: String) {
        framesetMap.remove(videoId)
        specMap.remove(videoId)
    }

    /** Clear all cached memory */
    fun clearAll() {
        framesetMap.clear()
        specMap.clear()
        spriteCache.evictAll()
        frameCache.evictAll()
    }

    /** Get cropped video preview thumbnail for given position in milliseconds */
    suspend fun getFrameBitmap(
        videoId: String,
        positionMs: Long,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "$videoId:${positionMs / 1000}"
        frameCache.get(cacheKey)?.let { return@withContext it }

        // Try resolving via NewPipe Frameset first
        val set = resolveFrameset(videoId)
        if (set != null) {
            val bitmap = extractFrameFromFrameset(set, positionMs)
            if (bitmap != null) {
                frameCache.put(cacheKey, bitmap)
                return@withContext bitmap
            }
        }

        // Try InnerTube parsed StoryboardSpec fallback
        val spec = specMap[videoId]
        if (spec != null) {
            val bitmap = extractFrameFromSpec(spec, positionMs)
            if (bitmap != null) {
                frameCache.put(cacheKey, bitmap)
                return@withContext bitmap
            }
        }

        null
    }

    private fun resolveFrameset(videoId: String): Frameset? {
        framesetMap[videoId]?.let { return it }
        val info = runCatching {
            StreamCache.get(videoId) ?: StreamCache.getOrLoad(videoId)
        }.getOrNull()
        val sets = info?.previewFrames ?: emptyList()
        if (sets.isEmpty()) {
            Log.i(TAG, "No storyboard frames available for $videoId")
            return null
        }
        val sortedByArea = sets.sortedBy { it.frameWidth * it.frameHeight }
        val pick = sortedByArea.getOrNull(1) ?: sortedByArea.firstOrNull() ?: return null
        framesetMap[videoId] = pick
        return pick
    }

    private fun extractFrameFromFrameset(set: Frameset, positionMs: Long): Bitmap? {
        val bounds = set.getFrameBoundsAt(positionMs) ?: return null
        val pageUrl = set.urls.getOrNull(bounds[0]) ?: return null
        val sprite = getOrFetchSprite(pageUrl) ?: return null

        val left = bounds[1].coerceIn(0, max(0, sprite.width - 1))
        val top = bounds[2].coerceIn(0, max(0, sprite.height - 1))
        val right = bounds[3].coerceIn(left + 1, sprite.width)
        val bottom = bounds[4].coerceIn(top + 1, sprite.height)

        val width = min(right - left, set.frameWidth)
        val height = min(bottom - top, set.frameHeight)

        if (width <= 0 || height <= 0) return null
        return runCatching { Bitmap.createBitmap(sprite, left, top, width, height) }.getOrNull()
    }

    private fun extractFrameFromSpec(spec: StoryboardSpec, positionMs: Long): Bitmap? {
        val frameIndex = (positionMs / spec.durationPerFrameMs).toInt().coerceIn(0, max(0, spec.totalFrames - 1))
        val framesPerPage = spec.framesPerPageX * spec.framesPerPageY
        if (framesPerPage <= 0) return null

        val pageIndex = frameIndex / framesPerPage
        val indexOnPage = frameIndex % framesPerPage
        val col = indexOnPage % spec.framesPerPageX
        val row = indexOnPage / spec.framesPerPageX

        val pageUrl = spec.urls.getOrNull(pageIndex) ?: return null
        val sprite = getOrFetchSprite(pageUrl) ?: return null

        val left = col * spec.frameWidth
        val top = row * spec.frameHeight
        val right = min(left + spec.frameWidth, sprite.width)
        val bottom = min(top + spec.frameHeight, sprite.height)

        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) return null
        return runCatching { Bitmap.createBitmap(sprite, left, top, width, height) }.getOrNull()
    }

    private fun getOrFetchSprite(url: String): Bitmap? {
        synchronized(spriteCache) {
            spriteCache.get(url)?.let { return it }
        }
        val bitmap = fetchBitmap(url) ?: return null
        synchronized(spriteCache) {
            spriteCache.put(url, bitmap)
        }
        return bitmap
    }

    private fun fetchBitmap(url: String): Bitmap? {
        val bytes = runCatching {
            Net.client.newCall(
                Request.Builder().url(url).header("User-Agent", Net.USER_AGENT).build(),
            ).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        }.getOrNull() ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}

/**
 * Floating skimmer preview card UI component.
 * Renders a stylized thumbnail popup with a timestamp chip above the seek bar.
 */
@Composable
fun SkimmerPreviewCard(
    videoId: String?,
    positionMs: Long,
    visible: Boolean,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 160.dp,
    cardAspectRatio: Float = 16f / 9f,
) {
    var frameBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(videoId, positionMs, visible) {
        if (visible && videoId != null) {
            frameBitmap = VideoSkimmer.getFrameBitmap(videoId, positionMs)
        } else if (!visible) {
            frameBitmap = null
        }
    }

    AnimatedVisibility(
        visible = visible && frameBitmap != null,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier,
    ) {
        val bitmap = frameBitmap
        if (bitmap != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .shadow(12.dp, shape = RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.85f), shape = RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp))
                    .padding(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(cardWidth)
                        .aspectRatio(cardAspectRatio)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Skimmer frame preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDuration(positionMs),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * Full video view skimmer overlay. Replaces full video surface during seek bar dragging.
 */
@Composable
fun SkimmerFullOverlay(
    videoId: String?,
    positionMs: Long,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    var frameBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(videoId, positionMs, visible) {
        if (visible && videoId != null) {
            frameBitmap = VideoSkimmer.getFrameBitmap(videoId, positionMs)
        } else if (!visible) {
            frameBitmap = null
        }
    }

    if (visible) {
        val bitmap = frameBitmap
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
