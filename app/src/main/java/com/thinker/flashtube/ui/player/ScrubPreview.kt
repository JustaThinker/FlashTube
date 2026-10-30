package com.thinker.flashtube

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.thinker.flashtube.ui.player.components.SkimmerFullOverlay
import com.thinker.flashtube.ui.player.components.VideoSkimmer

/**
 * Storyboard sprites for scrub previews, delegating to unified high-performance [VideoSkimmer].
 */
object ScrubStoryboards {
    suspend fun preload(videoId: String) {
        VideoSkimmer.preload(videoId)
    }

    suspend fun frameBitmap(
        @Suppress("UNUSED_PARAMETER") context: Context,
        videoId: String,
        positionMs: Long,
    ): Bitmap? {
        return VideoSkimmer.getFrameBitmap(videoId, positionMs)
    }
}

@Composable
fun ScrubVideoPreview(
    videoId: String?,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    SkimmerFullOverlay(
        videoId = videoId,
        positionMs = positionMs,
        visible = videoId != null,
        modifier = modifier,
    )
}

