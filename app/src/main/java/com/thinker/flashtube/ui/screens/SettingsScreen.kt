package com.thinker.flashtube

import androidx.activity.compose.BackHandler
import com.thinker.flashtube.data.ContentRegion
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Settings categories for modern modular navigation. */
enum class SettingsSection(
    val title: String,
    val icon: ImageVector,
) {
    Appearance("Appearance", Icons.Rounded.DarkMode),
    ContentRegion("Content Region", Icons.Rounded.Public),
    Playback("Playback", Icons.Rounded.PlayCircle),
    SponsorBlock("SponsorBlock", Icons.Rounded.FastForward),
    DataStorage("Data & Storage", Icons.Rounded.Storage),
    About("About", Icons.Rounded.Info),
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSignedOut: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val c = MaterialTheme.colorScheme
    var currentSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    var showAccountsPage by rememberSaveable { mutableStateOf(false) }

    // Intercept back key when inside a sub-category page to return to main settings list
    BackHandler(enabled = currentSection != null || showAccountsPage) {
        if (showAccountsPage) showAccountsPage = false else currentSection = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surface),
    ) {
        AnimatedContent(
            targetState = currentSection to showAccountsPage,
            transitionSpec = {
                if (targetState.second || targetState.first != null) {
                    // Navigate deeper: slide in from right
                    (slideInHorizontally(tween(260, easing = Motion.Emphasized)) { it / 3 } + fadeIn(tween(200)))
                        .togetherWith(slideOutHorizontally(tween(200)) { -it / 3 } + fadeOut(tween(160)))
                } else {
                    // Navigate back to main: slide in from left
                    (slideInHorizontally(tween(260, easing = Motion.Emphasized)) { -it / 3 } + fadeIn(tween(200)))
                        .togetherWith(slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(160)))
                }
            },
            label = "settings_navigation",
        ) { (section, accountsPage) ->
            when {
                accountsPage -> AccountsPage(
                    onBack = { showAccountsPage = false },
                    onSignIn = onSignIn,
                    onSignedOut = {
                        showAccountsPage = false
                        onSignedOut()
                    },
                )
                section == null -> SettingsMainList(
                    onBack = onBack,
                    onOpenSection = { currentSection = it },
                    onOpenAccounts = { showAccountsPage = true },
                    onSignIn = onSignIn,
                    contentPadding = contentPadding,
                )
                section == SettingsSection.Appearance -> AppearancePage(
                    onBack = { currentSection = null },
                    contentPadding = contentPadding,
                )
                section == SettingsSection.ContentRegion -> ContentRegionPage(
                    onBack = { currentSection = null },
                    contentPadding = contentPadding,
                )
                section == SettingsSection.Playback -> PlaybackPage(
                    onBack = { currentSection = null },
                    onOpenContentRegion = { currentSection = SettingsSection.ContentRegion },
                    contentPadding = contentPadding,
                )
                section == SettingsSection.SponsorBlock -> SponsorBlockPage(
                    onBack = { currentSection = null },
                    contentPadding = contentPadding,
                )
                section == SettingsSection.DataStorage -> DataStoragePage(
                    onBack = { currentSection = null },
                    contentPadding = contentPadding,
                )
                section == SettingsSection.About -> AboutPage(
                    onBack = { currentSection = null },
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

// ── Main Category Index ────────────────────────────────────────────────────────

@Composable
private fun SettingsMainList(
    onBack: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
    onOpenAccounts: () -> Unit,
    onSignIn: () -> Unit,
    contentPadding: PaddingValues,
) {
    val c = MaterialTheme.colorScheme
    val profile = Account.profile
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        BackScreenHeader(onBack = onBack) {
            Text(
                text = "Settings",
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = (-0.5).sp,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = contentPadding.calculateBottomPadding() + 28.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            SonicSpaceSectionTitle("Account")
            SettingsProfileCard(
                profile = profile,
                onOpenAccounts = onOpenAccounts,
                onSignIn = onSignIn,
            )

            Spacer(Modifier.height(16.dp))

            SonicSpaceSectionTitle("Preferences")

            SonicSpaceGroupCard {
                SettingsSection.entries.forEachIndexed { index, section ->
                    CategoryRow(
                        section = section,
                        onClick = { onOpenSection(section) },
                    )
                    if (index < SettingsSection.entries.size - 1) {
                        HorizontalDivider(
                            color = c.onSurface.copy(alpha = 0.06f),
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SonicSpaceSectionTitle(title: String) {
    val c = MaterialTheme.colorScheme
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = c.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun SonicSpaceGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 4.dp),
            content = content,
        )
    }
}

@Composable
private fun CategoryRow(
    section: SettingsSection,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = null,
                tint = c.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = section.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.5.sp,
                color = c.onSurface,
            )
            if (section == SettingsSection.ContentRegion) {
                Text(
                    text = ContentRegion.displaySummary,
                    fontSize = 12.5.sp,
                    color = c.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = c.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(20.dp),
        )
    }
}

// ── 1. Appearance Page ─────────────────────────────────────────────────────────

@Composable
private fun AppearancePage(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val c = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        BackScreenHeader(onBack = onBack) {
            Text(
                text = "Appearance",
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = (-0.5).sp,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = contentPadding.calculateBottomPadding() + 28.dp),
        ) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Theme mode",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.5.sp,
                color = c.onSurface,
            )
            Text(
                text = "Choose whether FlashTube follows your system settings or stays in light or dark mode.",
                fontSize = 12.5.sp,
                color = c.onSurfaceVariant,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )

            // Four-option theme selector including OLED AMOLED Pitch Black
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("system" to "System", "light" to "Light").forEach { (key, label) ->
                        val on = ThemeSettings.mode == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .pressable { ThemeSettings.chooseMode(key) }
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) c.primary else c.surfaceContainerHigh)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = if (on) c.onPrimary else c.onSurfaceVariant,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("dark" to "Dark", "amoled" to "OLED Black").forEach { (key, label) ->
                        val on = ThemeSettings.mode == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .pressable { ThemeSettings.chooseMode(key) }
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) c.primary else c.surfaceContainerHigh)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = if (on) c.onPrimary else c.onSurfaceVariant,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Display details & Effects",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = c.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable { AmbientMode.set(!AmbientMode.enabled) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(c.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = c.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ambient lighting effect", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.onSurface)
                    Text(
                        "Casts a soft ambient glow of video colors behind the player",
                        fontSize = 12.sp,
                        color = c.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = AmbientMode.enabled,
                    onCheckedChange = AmbientMode::set,
                    colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
                )
            }

        }
    }
}

// ── 1b. Content Region Page ───────────────────────────────────────────────────

@Composable
private fun ContentRegionPage(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val c = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        BackScreenHeader(onBack = onBack) {
            Text(
                text = "Content Region",
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = (-0.5).sp,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = contentPadding.calculateBottomPadding() + 28.dp),
        ) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Select feed & search region",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.5.sp,
                color = c.onSurface,
            )
            Text(
                text = "Controls the country used for YouTube search results, home feed recommendations, and trending videos.",
                fontSize = 12.5.sp,
                color = c.onSurfaceVariant,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ContentRegion.supportedRegions.forEach { item ->
                    val isSelected = ContentRegion.code == item.code
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) c.primaryContainer.copy(alpha = 0.4f) else c.surfaceContainerLow)
                            .pressable { ContentRegion.setRegion(item.code) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 15.sp,
                                color = if (isSelected) c.primary else c.onSurface,
                            )
                            if (item.code != ContentRegion.AUTO_REGION) {
                                Text(
                                    text = "Region code: ${item.code}",
                                    fontSize = 12.sp,
                                    color = c.onSurfaceVariant,
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = c.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionSelectorDialog(
    onDismiss: () -> Unit,
    onSelectRegion: (String) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Content Region", fontFamily = RoundedHeavy, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ContentRegion.supportedRegions.forEach { region ->
                    val code = region.code
                    val name = region.name
                    val isSelected = ContentRegion.code.equals(code, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressable { onSelectRegion(code) }
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) c.primaryContainer else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "$name ($code)",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.5.sp,
                            color = if (isSelected) c.onPrimaryContainer else c.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = c.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Text(
                "Close",
                modifier = Modifier.pressable(onClick = onDismiss).padding(horizontal = 16.dp, vertical = 10.dp),
                color = c.primary,
                fontWeight = FontWeight.Bold,
            )
        },
        containerColor = c.surfaceContainerLow,
    )
}

// ── 2. Playback Page ──────────────────────────────────────────────────────────

@Composable
private fun PlaybackPage(
    onBack: () -> Unit,
    onOpenContentRegion: () -> Unit,
    contentPadding: PaddingValues,
) {
    val c = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        BackScreenHeader(onBack = onBack) {
            Text(
                text = "Playback",
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = (-0.5).sp,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = contentPadding.calculateBottomPadding() + 28.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Content region setting row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable { onOpenContentRegion() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(c.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Public, null, tint = c.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Content region", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.onSurface)
                    Text("Location for feeds & search recommendations", fontSize = 12.sp, color = c.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = ContentRegion.displaySummary,
                    fontSize = 13.sp,
                    color = c.primary,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = c.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            }

            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

            // Double tap seek step
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(c.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.FastForward, null, tint = c.onSurfaceVariant, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Double-tap to seek", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.onSurface)
                        Text("Choose seek step interval on double-tap", fontSize = 12.sp, color = c.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SeekConfig.options.forEach { sec ->
                        val active = SeekConfig.stepSeconds == sec
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) c.primary else c.surfaceContainerHigh)
                                .clickable { SeekConfig.setStep(sec) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${sec}s",
                                color = if (active) c.onPrimary else c.onSurface,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

            // Ambient lighting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable { AmbientMode.toggle() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(c.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = c.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ambient lighting", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.onSurface)
                    Text("Glow behind the player updates live with the video content", fontSize = 12.sp, color = c.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = AmbientMode.enabled,
                    onCheckedChange = { AmbientMode.set(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
                )
            }

            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

            // Remember speed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable { PlaybackSpeed.setRemember(!PlaybackSpeed.remember, PlaybackSpeed.saved) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(c.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Speed, null, tint = c.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Remember playback speed", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.onSurface)
                    Text(
                        if (PlaybackSpeed.remember) "Every video starts at ${PlaybackSpeed.label(PlaybackSpeed.saved)}×"
                        else "New videos start at normal (1×) speed",
                        fontSize = 12.sp,
                        color = c.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = PlaybackSpeed.remember,
                    onCheckedChange = { PlaybackSpeed.setRemember(it, PlaybackSpeed.saved) },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
                )
            }

            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

            // Double-tap seek step
            var showSeekDialog by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable { showSeekDialog = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(c.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.FastForward, null, tint = c.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Double-tap seek step", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.onSurface)
                    Text(
                        "${SeekConfig.stepSeconds} seconds",
                        fontSize = 12.sp,
                        color = c.onSurfaceVariant,
                    )
                }
            }

            if (showSeekDialog) {
                AlertDialog(
                    onDismissRequest = { showSeekDialog = false },
                    title = { Text("Double-tap seek step", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            SeekConfig.options.forEach { step ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pressable {
                                            SeekConfig.setStep(step)
                                            showSeekDialog = false
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = SeekConfig.stepSeconds == step,
                                        onClick = {
                                            SeekConfig.setStep(step)
                                            showSeekDialog = false
                                        },
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text("$step seconds", fontSize = 15.sp)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSeekDialog = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }

            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

            // Wi-Fi Default Video Quality
            var showWifiQualityDialog by remember { mutableStateOf(false) }
            val wifiQualityOptions = listOf(null to "Auto", 720 to "720p (HD)", 1080 to "1080p (Full HD)", 1440 to "1440p (QHD)", 2160 to "2160p (4K)")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable { showWifiQualityDialog = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(c.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Wifi, null, tint = c.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Wi-Fi default quality", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.onSurface)
                    val wifiLabel = QualityPreference.wifiDefaultHeight?.let { "${it}p" } ?: "Auto"
                    Text("Quality on unmetered networks ($wifiLabel)", fontSize = 12.sp, color = c.onSurfaceVariant)
                }
            }

            if (showWifiQualityDialog) {
                AlertDialog(
                    onDismissRequest = { showWifiQualityDialog = false },
                    title = { Text("Wi-Fi default quality", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            wifiQualityOptions.forEach { (h, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pressable {
                                            QualityPreference.setWifiDefault(h)
                                            showWifiQualityDialog = false
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = h == QualityPreference.wifiDefaultHeight,
                                        onClick = {
                                            QualityPreference.setWifiDefault(h)
                                            showWifiQualityDialog = false
                                        },
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(label, fontSize = 15.sp)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showWifiQualityDialog = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }

            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

            // Mobile Data Default Video Quality
            var showMobileQualityDialog by remember { mutableStateOf(false) }
            val mobileQualityOptions = listOf(null to "Auto (Max 720p)", 360 to "360p (Data saver)", 480 to "480p", 720 to "720p (HD)", 1080 to "1080p (Full HD)")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable { showMobileQualityDialog = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(c.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.SignalCellularAlt, null, tint = c.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Mobile data default quality", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.onSurface)
                    val mobileLabel = QualityPreference.mobileDefaultHeight?.let { "${it}p" } ?: "Auto"
                    Text("Quality on metered networks ($mobileLabel)", fontSize = 12.sp, color = c.onSurfaceVariant)
                }
            }

            if (showMobileQualityDialog) {
                AlertDialog(
                    onDismissRequest = { showMobileQualityDialog = false },
                    title = { Text("Mobile data default quality", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            mobileQualityOptions.forEach { (h, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pressable {
                                            QualityPreference.setMobileDefault(h)
                                            showMobileQualityDialog = false
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = h == QualityPreference.mobileDefaultHeight,
                                        onClick = {
                                            QualityPreference.setMobileDefault(h)
                                            showMobileQualityDialog = false
                                        },
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(label, fontSize = 15.sp)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showMobileQualityDialog = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }

            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

            // Prefer HDR Video Streams
            var preferHdrState by remember { mutableStateOf(QualityPreference.preferHdr) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable {
                        preferHdrState = !preferHdrState
                        QualityPreference.setPreferHdr(preferHdrState)
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(c.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.HighQuality, null, tint = c.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Prefer HDR video", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.onSurface)
                    Text("Prioritize HDR dynamic range streams when available", fontSize = 12.sp, color = c.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = preferHdrState,
                    onCheckedChange = {
                        preferHdrState = it
                        QualityPreference.setPreferHdr(it)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
                )
            }
        }
    }
}

// ── 3. SponsorBlock Page ──────────────────────────────────────────────────────

@Composable
private fun SponsorBlockPage(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val c = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        BackScreenHeader(onBack = onBack) {
            Text(
                text = "SponsorBlock",
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = (-0.5).sp,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = contentPadding.calculateBottomPadding() + 28.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Master toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable { SponsorBlock.updateEnabled(!SponsorBlock.enabled) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Enable SponsorBlock", fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp, color = c.onSurface)
                    Text(
                        "Automatically skip sponsors, intros, and other segments",
                        fontSize = 12.5.sp,
                        color = c.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = SponsorBlock.enabled,
                    onCheckedChange = SponsorBlock::updateEnabled,
                    colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
                )
            }

            if (SponsorBlock.enabled) {
                HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 10.dp))

                Text(
                    text = "Auto-skip categories",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = c.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                listOf(
                    Triple(SponsorBlock.Category.Sponsor, "Sponsor segments", "Paid promotions, product ads & sponsored deals"),
                    Triple(SponsorBlock.Category.Intro, "Intros & intermissions", "Channel intros, title animations & pause screens"),
                    Triple(SponsorBlock.Category.Outro, "Endcards & credits", "End screens, credits & post-video cards"),
                    Triple(SponsorBlock.Category.Interaction, "Subscribe & like reminders", "Requests to like, subscribe or follow"),
                    Triple(SponsorBlock.Category.SelfPromo, "Self-promotion", "Merch plugs, creator products & social links"),
                    Triple(SponsorBlock.Category.MusicOffTopic, "Non-music parts in music videos", "Dialogue or narrative in music videos"),
                    Triple(SponsorBlock.Category.Preview, "Previews & recaps", "Upcoming clip teasers or previous episode recaps"),
                ).forEach { (category, title, description) ->
                    val isAuto = SponsorBlock.actionFor(category) == SponsorBlock.Action.Auto
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressable {
                                SponsorBlock.setAction(category, if (isAuto) SponsorBlock.Action.Off else SponsorBlock.Action.Auto)
                            }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(category.color),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.onSurface)
                            Spacer(Modifier.height(2.dp))
                            Text(description, fontSize = 12.sp, color = c.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = isAuto,
                            onCheckedChange = { on ->
                                SponsorBlock.setAction(category, if (on) SponsorBlock.Action.Auto else SponsorBlock.Action.Off)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "Segments provided by SponsorBlock (sponsor.ajay.app), a community crowdsourced database. Data licensed under CC BY-NC-SA 4.0.",
                color = c.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

// ── 4. Data & Storage Page ────────────────────────────────────────────────────

@Composable
private fun DataStoragePage(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val c = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    var cacheMessage by remember { mutableStateOf<String?>(null) }
    var historyMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        BackScreenHeader(onBack = onBack) {
            Text(
                text = "Data & Storage",
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = (-0.5).sp,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = contentPadding.calculateBottomPadding() + 28.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Stream Cache
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(c.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.CleaningServices, null, tint = c.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Clear stream cache", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.onSurface)
                    Text(
                        cacheMessage ?: "Refresh cached stream URLs",
                        fontSize = 12.sp,
                        color = c.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .pressable {
                            StreamCache.invalidateAll()
                            cacheMessage = "Cleared!"
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Clear", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.primary)
                }
            }

            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

            // Search history
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(c.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.History, null, tint = c.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Clear search history", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.onSurface)
                    Text(
                        historyMessage ?: "${SearchHistory.queries.size} saved queries",
                        fontSize = 12.sp,
                        color = c.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .pressable {
                            SearchHistory.clear()
                            historyMessage = "Cleared!"
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Clear", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.primary)
                }
            }
        }
    }
}

// ── 5. About Page ─────────────────────────────────────────────────────────────

@Composable
private fun AboutPage(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val c = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        BackScreenHeader(onBack = onBack) {
            Text(
                text = "About",
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = (-0.5).sp,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = contentPadding.calculateBottomPadding() + 28.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            // Brand banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark()
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "FlashTube",
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = c.onSurface,
                    )
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        fontSize = 12.5.sp,
                        color = c.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
            Spacer(Modifier.height(8.dp))

            SettingsRow(
                icon = Icons.Rounded.Security,
                title = "Privacy & Open Source",
                subtitle = "Zero tracking SDKs, ad-free, completely open source.",
            )

            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

            SettingsRow(
                icon = Icons.Rounded.AccountCircle,
                title = "Google Session Sync",
                subtitle = "Encrypted session tokens stored using Android Keystore AES-GCM.",
            )

            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

            SettingsRow(
                icon = Icons.Rounded.Info,
                title = "PoToken & Player Fallback",
                subtitle = "Automated Proof-of-Origin token minting to bypass YouTube bot checks.",
            )
        }
    }
}

// ── Shared UI Components ───────────────────────────────────────────────────────

@Composable
private fun AccountsPage(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val accounts = Account.accounts
    val activeProfile = Account.profile

    Column(modifier = Modifier.fillMaxSize()) {
        BackScreenHeader(onBack = onBack) {
            Text(
                text = "Accounts",
                fontFamily = RoundedHeavy,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = (-0.5).sp,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Select an account to switch profiles.",
                fontSize = 13.sp,
                color = c.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            SonicSpaceSectionTitle("Saved Accounts")
            SonicSpaceGroupCard {
                accounts.forEachIndexed { index, account ->
                    val isActive = account.profile == activeProfile
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressable { Account.switchAccount(account) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChannelAvatar(account.profile.avatar, account.profile.name, size = 44)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = account.profile.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp,
                                color = c.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = account.profile.handle ?: if (isActive) "Current account" else "YouTube account",
                                fontSize = 12.sp,
                                color = c.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isActive) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Current account",
                                tint = c.primary,
                                modifier = Modifier.padding(horizontal = 8.dp).size(20.dp),
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Remove ${account.profile.name}",
                            tint = c.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(36.dp)
                                .pressable {
                                    Account.removeAccount(account.id)
                                    if (Account.profile == null) onSignedOut()
                                }
                                .padding(9.dp),
                        )
                    }
                    if (index < accounts.lastIndex) {
                        HorizontalDivider(
                            color = c.onSurface.copy(alpha = 0.06f),
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }

                HorizontalDivider(
                    color = c.onSurface.copy(alpha = 0.06f),
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable(onClick = onSignIn)
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.padding(start = 8.dp, end = 12.dp).size(22.dp),
                    )
                    Text(
                        text = "Add another account",
                        color = c.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
            }

            if (activeProfile != null) {
                Spacer(Modifier.height(18.dp))
                SonicSpaceGroupCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressable {
                                Account.signOut()
                                if (Account.profile == null) onSignedOut()
                            }
                            .padding(vertical = 16.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Sign out of current account",
                            color = c.error,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = c.onSurfaceVariant.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsProfileCard(
    profile: Profile?,
    onOpenAccounts: () -> Unit,
    onSignIn: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val isLoggedIn = profile != null
    val accounts = Account.accounts
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoggedIn) c.surfaceContainerLow else c.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isLoggedIn) {
                        Brush.linearGradient(
                            colors = listOf(
                                c.primaryContainer.copy(alpha = 0.25f),
                                c.surfaceContainerLow,
                            ),
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                c.surfaceContainerHigh,
                                c.surfaceContainerLow,
                            ),
                        )
                    },
                )
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Profile Avatar / Placeholder
                Box(contentAlignment = Alignment.Center) {
                    if (isLoggedIn && profile?.avatar != null) {
                        ChannelAvatar(profile.avatar, profile.name, size = 52)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(c.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                tint = c.onPrimaryContainer,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.width(14.dp))

                // Account Name & Status
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isLoggedIn) profile!!.name else "YouTube Account",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.5.sp,
                            color = c.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isLoggedIn) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(c.primary.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "SYNCED",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = c.primary,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(3.dp))

                    Text(
                        text = if (isLoggedIn) (profile?.handle ?: "Active account") else "Sign in to sync your library",
                        fontSize = 12.5.sp,
                        color = c.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Action Button / Switcher Toggle
                if (isLoggedIn) {
                    Box(
                        modifier = Modifier
                            .pressable { expanded = !expanded }
                            .size(40.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = if (expanded) "Collapse accounts" else "Expand accounts",
                            tint = c.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                } else if (!isLoggedIn) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.surfaceContainerHighest)
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                    ) {
                        Text("SIGNED OUT", color = c.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                    }
                }
            }

            if (!isLoggedIn) {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable(onClick = onSignIn)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.primary)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Sign in", color = c.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // Keep switching compact on Settings; the Accounts page contains management actions.
            if (isLoggedIn && expanded) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.take(2).forEach { acc ->
                        val isActive = acc.profile == profile
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressable { Account.switchAccount(acc) }
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isActive) c.surfaceContainerHighest else Color.Transparent)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ChannelAvatar(acc.profile.avatar, acc.profile.name, size = 34)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(acc.profile.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = c.onSurface)
                                acc.profile.handle?.let { Text(it, fontSize = 11.5.sp, color = c.onSurfaceVariant) }
                            }
                            if (isActive) {
                                Icon(Icons.Rounded.Check, contentDescription = "Active", tint = c.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressable(onClick = onOpenAccounts)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${accounts.size} accounts · All accounts", color = c.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Open accounts", tint = c.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(c.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = c.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.5.sp, color = c.onSurfaceVariant, lineHeight = 17.sp)
        }
    }
}
