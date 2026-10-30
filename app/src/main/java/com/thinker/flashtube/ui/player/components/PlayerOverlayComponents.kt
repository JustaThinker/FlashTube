package com.thinker.flashtube.ui.player.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thinker.flashtube.R
import com.thinker.flashtube.pressable

@Composable
fun PlayerDrawableIcon(
    resourceId: Int,
    description: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(resourceId),
        contentDescription = description,
        tint = tint,
        modifier = modifier,
    )
}

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
    Icon(
        painter = painterResource(if (playing) R.drawable.ic_player_pause else R.drawable.ic_player_play),
        contentDescription = null,
        tint = color,
        modifier = modifier.size(size),
    )
}
