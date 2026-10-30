package com.thinker.flashtube.ui.player.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thinker.flashtube.pressable

@Composable
fun PlainIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 30.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .pressable(enabled = enabled, pressedScale = 0.85f, onClick = onClick)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Shared 24-unit player transport glyph. The play triangle is inset and optically centered;
 * pause bars use the same visual weight and spacing so the toggle doesn't feel cramped.
 */
@Composable
fun PlayerPlayPauseGlyph(
    playing: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val factor = this.size.minDimension / 24f
        scale(factor) {
            if (playing) {
                val barWidth = 4.2f
                val barHeight = 13f
                val top = (24f - barHeight) / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(5.4f, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(1.2f),
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(14.4f, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(1.2f),
                )
            } else {
                val triangle = Path().apply {
                    moveTo(7.2f, 4.6f)
                    lineTo(18.2f, 12f)
                    lineTo(7.2f, 19.4f)
                    close()
                }
                drawPath(triangle, color)
            }
        }
    }
}
