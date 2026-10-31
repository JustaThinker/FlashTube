package com.thinker.flashtube

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

import androidx.compose.runtime.mutableFloatStateOf

/**
 * A screen header that gets out of the way: it slides up exactly as far as the list scrolls
 * down, comes straight back on any upward scroll, and on release settles fully shown or
 * fully hidden. The page runs to the top edge; as the header leaves, a soft top vignette
 * (the bottom one's [vignetteBrush], mirrored, half its depth) fades in behind the status bar.
 *
 * [content] gets the header's height to use as its top content padding. [resetKey] changing
 * (refresh, scroll-to-top, new search) brings the header back.
 */
@Composable
fun CollapsingHeaderLayout(
    resetKey: Any? = null,
    header: @Composable () -> Unit,
    content: @Composable (top: Dp) -> Unit,
) {
    val density = LocalDensity.current
    val c = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    var headerPx by remember { mutableIntStateOf(0) }
    var offsetPx by remember { mutableFloatStateOf(0f) }
    val animatable = remember { Animatable(0f) }
    var isAnimating by remember { mutableStateOf(false) }

    val settleSpec = spring<Float>(dampingRatio = 0.9f, stiffness = 420f)

    LaunchedEffect(resetKey) {
        isAnimating = true
        if (reduced) animatable.snapTo(0f) else animatable.animateTo(0f, settleSpec)
        offsetPx = 0f
        isAnimating = false
    }

    val connection = remember(reduced) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isAnimating) {
                    isAnimating = false
                    offsetPx = animatable.value
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val h = headerPx.toFloat()
                if (h <= 0f) return Offset.Zero
                val delta = if (consumed.y != 0f) consumed.y else if (available.y > 0f) available.y else 0f
                if (delta == 0f) return Offset.Zero
                val target = (offsetPx + delta).coerceIn(-h, 0f)
                if (target != offsetPx) {
                    offsetPx = target
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val h = headerPx.toFloat()
                val v = offsetPx
                if (h > 0f && v < 0f && v > -h) {
                    val target = if (v < -h / 2f) -h else 0f
                    animatable.snapTo(offsetPx)
                    isAnimating = true
                    if (reduced) animatable.snapTo(target) else animatable.animateTo(target, settleSpec)
                    offsetPx = target
                    isAnimating = false
                }
                return Velocity.Zero
            }
        }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize().nestedScroll(connection)) {
        content(with(density) { headerPx.toDp() })
        // Top vignette, shown in proportion to how hidden the header is.
        val page = c.surface
        val vignette = remember(page) { vignetteBrush(page, opaqueAtTop = true) }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height((statusTop + 72.dp) / 2)
                .graphicsLayer {
                    val h = headerPx.toFloat()
                    val curr = if (isAnimating) animatable.value else offsetPx
                    alpha = if (h > 0) (-curr / h).coerceIn(0f, 1f) else 0f
                }
                .drawBehind { drawRect(vignette) },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { headerPx = it.height }
                .graphicsLayer {
                    val h = headerPx.toFloat()
                    val curr = if (isAnimating) animatable.value else offsetPx
                    translationY = curr
                    alpha = if (h > 0) 1f - 0.5f * (-curr / h).coerceIn(0f, 1f) else 1f
                }
                .background(c.surface),
        ) { header() }
    }
}

/** Strongest a page vignette gets (Derek: 80% of Ledgii's 95%). */
const val VignetteAlpha = 0.76f

/**
 * Page color fading from [VignetteAlpha] to 0 on a smoothstep ramp: flat at both ends, so
 * there's no visible line where the vignette begins. [opaqueAtTop] puts the strong end on top.
 */
fun vignetteBrush(color: Color, opaqueAtTop: Boolean): Brush {
    val stops = 12
    return Brush.verticalGradient(
        *Array(stops + 1) { i ->
            val t = i / stops.toFloat()
            val strength = if (opaqueAtTop) 1f - t else t
            t to color.copy(alpha = VignetteAlpha * strength * strength * (3f - 2f * strength))
        },
    )
}

/** Bottom padding as given, plus the collapsing header's height on top. */
fun PaddingValues.withTop(top: Dp) = PaddingValues(top = top, bottom = calculateBottomPadding())
