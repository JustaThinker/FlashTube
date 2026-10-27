package com.thinker.flashtube

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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

    // Intercept back key when inside a sub-category page to return to main settings list
    BackHandler(enabled = currentSection != null) {
        currentSection = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.surface),
    ) {
        AnimatedContent(
            targetState = currentSection,
            transitionSpec = {
                if (targetState != null) {
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
        ) { section ->
            when (section) {
                null -> SettingsMainList(
                    onBack = onBack,
                    onOpenSection = { currentSection = it },
                    onSignIn = onSignIn,
                    onSignedOut = onSignedOut,
                    contentPadding = contentPadding,
                )
                SettingsSection.Appearance -> AppearancePage(
                    onBack = { currentSection = null },
                    contentPadding = contentPadding,
                )
                SettingsSection.Playback -> PlaybackPage(
                    onBack = { currentSection = null },
                    contentPadding = contentPadding,
                )
                SettingsSection.SponsorBlock -> SponsorBlockPage(
                    onBack = { currentSection = null },
                    contentPadding = contentPadding,
                )
                SettingsSection.DataStorage -> DataStoragePage(
                    onBack = { currentSection = null },
                    contentPadding = contentPadding,
                )
                SettingsSection.About -> AboutPage(
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
    onSignIn: () -> Unit,
    onSignedOut: () -> Unit,
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

            // Account card at top
            SettingsProfileCard(
                profile = profile,
                onSignIn = onSignIn,
                onSignOut = {
                    Account.signOut()
                    onSignedOut()
                },
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
        Text(
            text = section.title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.5.sp,
            color = c.onSurface,
            modifier = Modifier.weight(1f),
        )
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

            // Three-option segmented chip selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
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
                            fontSize = 12.5.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Display details",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = c.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            SettingsRow(
                icon = Icons.Rounded.DarkMode,
                title = "OLED Friendly Dark Theme",
                subtitle = "Uses deep dark surfaces for minimal battery consumption on OLED displays.",
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Location & Region",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = c.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            var showRegionDialog by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable { showRegionDialog = true }
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
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        tint = c.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Content Location / Region",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = c.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${RegionSettings.labelFor(RegionSettings.code)} (${RegionSettings.code})",
                        fontSize = 12.5.sp,
                        color = c.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = c.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (showRegionDialog) {
                RegionSelectorDialog(
                    onDismiss = { showRegionDialog = false },
                    onSelectRegion = { newCode ->
                        RegionSettings.set(newCode)
                        showRegionDialog = false
                    },
                )
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
                RegionSettings.REGIONS.forEach { (code, name) ->
                    val isSelected = RegionSettings.code.equals(code, ignoreCase = true)
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

            // Audio only mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable { AudioOnly.toggle() }
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
                    Icon(Icons.Rounded.PlayCircle, null, tint = c.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Audio only mode", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.onSurface)
                    Text(
                        "Disables video decoding to save bandwidth and battery",
                        fontSize = 12.sp,
                        color = c.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = AudioOnly.enabled,
                    onCheckedChange = AudioOnly::set,
                    colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
                )
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
private fun SettingsProfileCard(
    profile: Profile?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val isLoggedIn = profile != null
    val accounts = Account.accounts
    var expanded by remember { mutableStateOf(false) }

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
                        text = if (isLoggedIn) (profile?.handle ?: "Active account") else "Sign in to sync your subscriptions & recommendations",
                        fontSize = 12.5.sp,
                        color = c.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Action Button / Switcher Toggle
                if (isLoggedIn && accounts.size > 1) {
                    Box(
                        modifier = Modifier
                            .pressable { expanded = !expanded }
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.surfaceContainerHighest)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Switch", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = c.primary)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = c.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                } else if (!isLoggedIn) {
                    Box(
                        modifier = Modifier
                            .pressable(onClick = onSignIn)
                            .clip(RoundedCornerShape(20.dp))
                            .background(c.primary)
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    ) {
                        Text("Sign in", color = c.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }
                }
            }

            // Saved Accounts List (Multi-Account Switcher)
            if (isLoggedIn) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = c.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { acc ->
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
                                Spacer(Modifier.width(8.dp))
                            }
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Remove account",
                                tint = c.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(24.dp)
                                    .pressable { Account.removeAccount(acc.id) }
                                    .padding(4.dp),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressable(onClick = onSignIn)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = c.primary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Add another account", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
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
