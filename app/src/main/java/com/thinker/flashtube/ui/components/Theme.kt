package com.thinker.flashtube

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Heavy rounded face for titles (header, section titles, video titles). Pixel ships a
 * rounded sans; other devices fall back to the regular sans at the same weight.
 */
val RoundedHeavy = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-rounded"), weight = FontWeight.ExtraBold),
    Font(DeviceFontFamilyName("sans-serif-rounded"), weight = FontWeight.Bold),
    Font(DeviceFontFamilyName("sans-serif"), weight = FontWeight.ExtraBold),
    Font(DeviceFontFamilyName("sans-serif"), weight = FontWeight.Bold),
)

// ── Fixed default color schemes ────────────────────────────────────────────────
// Palette customization has been removed. The app supports exactly three appearance
// modes: Light, Dark, and System Default.

private val DefaultLight = lightColorScheme(
    primary = Color(0xFF121212),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E5E5),
    onPrimaryContainer = Color(0xFF121212),
    secondary = Color(0xFF606060),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E0E0),
    onSecondaryContainer = Color(0xFF0F0F0F),
    tertiary = Color(0xFF121212),
    tertiaryContainer = Color(0xFFF2F2F2),
    onTertiaryContainer = Color(0xFF0F0F0F),
    background = Color.White,
    onBackground = Color(0xFF0F0F0F),
    surface = Color.White,
    onSurface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFFE5E5E5),
    onSurfaceVariant = Color(0xFF606060),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9F9F9),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = Color(0xFFEAEAEA),
    surfaceContainerHighest = Color(0xFFE0E0E0),
    outline = Color(0xFF909090),
    outlineVariant = Color(0xFFE5E5E5),
)

private val DefaultDark = darkColorScheme(
    primary = Color.White,
    onPrimary = Color(0xFF0F0F0F),
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFAAAAAA),
    onSecondary = Color(0xFF0F0F0F),
    secondaryContainer = Color(0xFF3F3F3F),
    onSecondaryContainer = Color(0xFFF1F1F1),
    tertiary = Color.White,
    tertiaryContainer = Color(0xFF272727),
    onTertiaryContainer = Color(0xFFF1F1F1),
    background = Color(0xFF0F0F0F),
    onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF0F0F0F),
    onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF272727),
    onSurfaceVariant = Color(0xFFAAAAAA),
    surfaceContainerLowest = Color(0xFF0A0A0A),
    surfaceContainerLow = Color(0xFF181818),
    surfaceContainer = Color(0xFF212121),
    surfaceContainerHigh = Color(0xFF2C2C2C),
    surfaceContainerHighest = Color(0xFF3A3A3A),
    outline = Color(0xFF717171),
    outlineVariant = Color(0xFF303030),
)

// ── Theme settings ─────────────────────────────────────────────────────────────

/**
 * The selected theme mode, persisted across restarts.
 * Supported modes: "system" (default), "light", "dark".
 */
object ThemeSettings {
    private var prefs: SharedPreferences? = null

    /** "system", "light", or "dark". */
    var mode by mutableStateOf("system")
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).also { prefs = it }
        mode = p.getString("mode", "system") ?: "system"
        // Clean up any legacy palette seed preference to prevent corrupted state
        if (p.contains("seed")) {
            p.edit().remove("seed").apply()
        }
    }

    fun chooseMode(value: String) {
        mode = value
        prefs?.edit()?.putString("mode", value)?.apply()
    }
}

@Composable
fun isDarkTheme(): Boolean = when (ThemeSettings.mode) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashTubeTheme(content: @Composable () -> Unit) {
    val dark = isDarkTheme()
    val scheme = if (dark) DefaultDark else DefaultLight
    SystemBars(dark, scheme.surface)
    MaterialTheme(colorScheme = scheme, shapes = ExpressiveShapes, typography = Typography()) {
        CompositionLocalProvider(
            // No tap ripples anywhere: press feedback is the scale-down from Motion.kt.
            LocalRippleConfiguration provides null,
            // Text and icons without an explicit color follow the theme.
            LocalContentColor provides scheme.onSurface,
            content = content,
        )
    }
}

/**
 * Status/navigation bar icon colors and the window background follow the app's mode.
 */
@Composable
private fun SystemBars(dark: Boolean, windowBg: Color) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
    DisposableEffect(windowBg) {
        (view.context as? Activity)?.window?.setBackgroundDrawable(ColorDrawable(windowBg.toArgb()))
        onDispose { }
    }
}
