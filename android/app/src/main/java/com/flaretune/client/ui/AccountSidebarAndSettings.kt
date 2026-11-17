package com.flaretune.client.ui

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.flaretune.client.R
import com.flaretune.client.data.AppThemeMode
import com.flaretune.client.data.AppUpdateError
import com.flaretune.client.data.BluetoothLyricsMode
import com.flaretune.client.data.SavedAccountSummary
import com.flaretune.client.playback.SleepTimerMode
import com.flaretune.client.ui.theme.isFlareDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.Locale

internal val LocalOpenAccountDrawer = staticCompositionLocalOf<(() -> Unit)?> { null }
internal val LocalAccountImageLoader = staticCompositionLocalOf<ImageLoader?> { null }

/**
 * Circular (or rounded) account avatar rendering the authenticated user image when available,
 * falling back cleanly to the account's first character.
 */
@Composable
internal fun AccountAvatarBadge(
    label: String,
    avatarUrl: String?,
    size: Dp,
    filled: Boolean,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentDescription: String? = null,
) {
    val loader = LocalAccountImageLoader.current
    val initial = label.trim().take(1).uppercase(Locale.ROOT).ifEmpty { "F" }
    val bgColor = if (filled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    }
    val fgColor = if (filled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = textStyle,
            fontWeight = FontWeight.Bold,
            color = fgColor,
        )
        if (loader != null && !avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                imageLoader = loader,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal fun formatAccountOriginHost(origin: String): String {
    val trimmed = origin.trim()
    if (trimmed.isEmpty()) return ""
    val parsed = runCatching { URI(trimmed) }.getOrNull()
    val host = parsed?.host?.takeIf { it.isNotBlank() }
    val port = parsed?.port ?: -1
    return when {
        host != null && port > 0 -> "$host:$port"
        host != null -> host
        else -> trimmed.removePrefix("https://").removePrefix("http://").substringBefore('/')
    }
}

internal fun formatSleepRemainingText(remainingMs: Long): String {
    val totalSeconds = ((remainingMs.coerceAtLeast(0L) + 999L) / 1000L).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

@Composable
private fun rememberLiveSleepTimerSubtitle(): String? {
    val sleepState by rememberSleepTimerState()
    var nowMs by remember(sleepState) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(sleepState.mode, sleepState.endElapsedRealtimeMs) {
        if (sleepState.mode == SleepTimerMode.TIMED && sleepState.endElapsedRealtimeMs != null) {
            while (true) {
                nowMs = SystemClock.elapsedRealtime()
                delay(1000L)
            }
        }
    }
    return when (sleepState.mode) {
        SleepTimerMode.OFF -> null
        SleepTimerMode.END_OF_TRACK -> stringResource(R.string.ft_sleep_timer_active_track)
        SleepTimerMode.TIMED -> {
            val end = sleepState.endElapsedRealtimeMs ?: return null
            val remaining = (end - nowMs).coerceAtLeast(0L)
            val text = formatSleepRemainingText(remaining)
            if (sleepState.finishCurrentTrackAfterTimer) {
                stringResource(R.string.ft_sleep_timer_remaining_after_track, text)
            } else {
                stringResource(R.string.ft_sleep_timer_remaining, text)
            }
        }
    }
}

/**
 * Spotify-style left drawer paired with push-aside shell translation:
 * direct multi-account switching + Add account + clean action rows.
 */
@Composable
internal fun SpotifyAccountDrawer(
    state: ClientUiState,
    actions: ClientActions,
    tabletMode: Boolean,
    topInset: Dp,
    bottomInset: Dp,
    onClose: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isFlareDarkTheme()
    val containerColor = if (dark) Color(0xFF1F1F1F) else MaterialTheme.colorScheme.surfaceContainer
    val dividerColor = if (dark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val drawerShape = if (tabletMode) RoundedCornerShape(18.dp) else RoundedCornerShape(0.dp)
    val sleepSubtitle = rememberLiveSleepTimerSubtitle()

    val accountName = state.accountName.ifBlank { state.username.ifBlank { stringResource(R.string.app_name) } }
    val activeSummary = state.savedAccounts.firstOrNull { it.active }
    val accountMeta = remember(state.username, state.instanceUrl, activeSummary) {
        val user = activeSummary?.username?.ifBlank { state.username } ?: state.username
        val host = formatAccountOriginHost(activeSummary?.origin?.ifBlank { state.instanceUrl } ?: state.instanceUrl)
        listOfNotNull(
            user.takeIf { it.isNotBlank() }?.let { "@$it" },
            host.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    }
    val otherAccounts = remember(state.savedAccounts) {
        state.savedAccounts.filter { !it.active }
    }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .then(
                if (!tabletMode) {
                    Modifier.border(
                        width = 0.6.dp,
                        color = dividerColor,
                        shape = drawerShape,
                    )
                } else {
                    Modifier
                },
            )
            .testTag("account_drawer"),
        shape = drawerShape,
        color = containerColor,
        tonalElevation = if (dark) 0.dp else 4.dp,
        shadowElevation = if (tabletMode) 12.dp else 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = if (tabletMode) 16.dp else maxOf(topInset, 18.dp),
                    bottom = if (tabletMode) 16.dp else maxOf(bottomInset, 18.dp),
                ),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onClose()
                        onOpenSettings()
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .testTag("drawer_header"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountAvatarBadge(
                    label = accountName,
                    avatarUrl = state.avatarUrl,
                    size = 40.dp,
                    filled = true,
                    textStyle = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        accountName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (accountMeta.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            accountMeta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            HorizontalDivider(color = dividerColor, thickness = 0.8.dp)

            if (otherAccounts.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                otherAccounts.forEach { account ->
                    DrawerSavedAccountRow(
                        account = account,
                        enabled = !state.busy,
                        onClick = {
                            onClose()
                            actions.switchAccount(account.accountKey)
                        },
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable(enabled = !state.busy, role = Role.Button) {
                        onClose()
                        actions.beginAddAccount()
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("drawer_add_account"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        ClientIcons.Plus,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    stringResource(R.string.ft_add_account),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider(color = dividerColor, thickness = 0.8.dp)
            Spacer(Modifier.height(6.dp))

            DrawerMenuItem(
                icon = ClientIcons.Stats,
                label = stringResource(R.string.ft_drawer_statistics),
                tag = "drawer_stats",
                onClick = {
                    onClose()
                    onOpenStats()
                },
            )
            DrawerMenuItem(
                icon = ClientIcons.History,
                label = stringResource(R.string.ft_drawer_recent_plays),
                tag = "drawer_history",
                onClick = {
                    onClose()
                    onOpenHistory()
                },
            )
            DrawerMenuItem(
                icon = ClientIcons.SleepTimer,
                label = stringResource(R.string.ft_drawer_sleep_timer),
                subtitle = sleepSubtitle,
                tag = "drawer_sleep_timer",
                onClick = {
                    onClose()
                    onOpenSleepTimer()
                },
            )
            DrawerMenuItem(
                icon = ClientIcons.Refresh,
                label = stringResource(R.string.ft_refresh),
                tag = "account_refresh",
                onClick = {
                    onClose()
                    actions.refresh()
                },
            )
            DrawerMenuItem(
                icon = ClientIcons.Settings,
                label = stringResource(R.string.ft_drawer_settings),
                tag = "drawer_settings",
                onClick = {
                    onClose()
                    onOpenSettings()
                },
            )
        }
    }
}

@Composable
private fun DrawerSavedAccountRow(
    account: SavedAccountSummary,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = account.displayName.ifBlank { account.username }
    val meta = remember(account.username, account.origin) {
        val host = formatAccountOriginHost(account.origin)
        if (host.isNotBlank()) "@${account.username} · $host" else "@${account.username}"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("drawer_account_${account.accountKey}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatarBadge(
            label = label,
            avatarUrl = null,
            size = 32.dp,
            filled = false,
            textStyle = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (account.needsReauth) {
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.ft_account_reauth_required),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DrawerMenuItem(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Clean modal bottom sheet for configuring Sleep Timer durations or canceling active timer.
 */
@Composable
internal fun SleepTimerSheet(
    actions: ClientActions,
    hasCurrentTrack: Boolean = true,
    onDismiss: () -> Unit,
) {
    val sleepState by rememberSleepTimerState()
    val liveSubtitle = rememberLiveSleepTimerSubtitle()
    val durations = remember { listOf(15, 30, 45, 60) }
    var customOpen by remember { mutableStateOf(false) }
    var customMinutesInput by remember { mutableStateOf("") }
    val parsedCustomMinutes = customMinutesInput.trim().toIntOrNull()?.takeIf { it in 1..480 }

    FlareModalSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("sleep_timer_sheet"),
    ) {
        val sheetDismiss = LocalFlareSheetDismiss.current
        val dismissSheet: () -> Unit = { sheetDismiss?.invoke(onDismiss) ?: onDismiss() }
        FlareSheetHeader(
            title = stringResource(R.string.ft_sleep_timer_title),
            subtitle = liveSubtitle ?: stringResource(R.string.ft_sleep_timer_subtitle),
            onClose = onDismiss,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            durations.chunked(2).forEach { pair ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { minutes ->
                        SleepTimerPresetChip(
                            label = stringResource(R.string.ft_sleep_timer_minutes, minutes),
                            tag = "sleep_timer_$minutes",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                actions.setSleepTimer(minutes)
                                dismissSheet()
                            },
                        )
                    }
                }
            }

            SleepTimerOptionRow(
                label = stringResource(R.string.ft_sleep_timer_custom),
                selected = customOpen,
                tag = "sleep_timer_custom",
                onClick = { customOpen = !customOpen },
            )
            if (customOpen) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = customMinutesInput,
                        onValueChange = { raw ->
                            val digits = raw.filter(Char::isDigit).take(3)
                            customMinutesInput = digits
                        },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.ft_sleep_timer_custom_hint)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                parsedCustomMinutes?.let { mins ->
                                    actions.setSleepTimer(mins)
                                    dismissSheet()
                                }
                            },
                        ),
                        modifier = Modifier.weight(1f).testTag("sleep_timer_custom_input"),
                    )
                    Button(
                        onClick = {
                            parsedCustomMinutes?.let { mins ->
                                actions.setSleepTimer(mins)
                                dismissSheet()
                            }
                        },
                        enabled = parsedCustomMinutes != null,
                        modifier = Modifier.heightIn(min = 52.dp).testTag("sleep_timer_custom_confirm"),
                    ) {
                        Text(stringResource(R.string.ft_sleep_timer_custom_start))
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(vertical = 2.dp),
            )

            val endOfTrackEnabled = hasCurrentTrack || sleepState.mode != SleepTimerMode.OFF
            val endOfTrackAlpha = if (endOfTrackEnabled) 1f else 0.38f
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = endOfTrackEnabled) {
                        actions.setSleepTimerFinishCurrentTrack(!sleepState.stopAfterCurrentTrack)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("sleep_timer_end_of_track"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        stringResource(R.string.ft_sleep_timer_end_of_track),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = endOfTrackAlpha),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.ft_sleep_timer_end_of_track_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = endOfTrackAlpha),
                    )
                }
                Switch(
                    checked = sleepState.stopAfterCurrentTrack,
                    onCheckedChange = null,
                    enabled = endOfTrackEnabled,
                )
            }

            if (sleepState.mode != SleepTimerMode.OFF) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) {
                            actions.cancelSleepTimer()
                            dismissSheet()
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .testTag("sleep_timer_off"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.ft_sleep_timer_turn_off),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SleepTimerPresetChip(
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
    ) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SleepTimerOptionRow(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                ClientIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberDockSafeBringIntoViewSpec(bottomInset: Dp): BringIntoViewSpec {
    val bottomInsetPx = with(LocalDensity.current) { bottomInset.toPx() }
    val base = LocalBringIntoViewSpec.current
    return remember(bottomInsetPx, base) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float {
                val safeContainer = (containerSize - bottomInsetPx).coerceAtLeast(size)
                return base.calculateScrollDistance(offset, size, safeContainer)
            }
        }
    }
}

/**
 * Secondary Settings & Account management page pushed onto the shell overlay stack.
 */
@Composable
internal fun SettingsScreen(
    state: ClientUiState,
    actions: ClientActions,
    topInset: Dp,
    bottomInset: Dp,
    onBack: () -> Unit,
    onOpenSleepTimer: () -> Unit,
) {
    val context = LocalContext.current
    val listeningInteractions = LocalListeningInteractions.current
    var pendingRemove by remember { mutableStateOf<SavedAccountSummary?>(null) }
    var editingNickname by remember { mutableStateOf(false) }
    var showAvatarViewer by remember { mutableStateOf(false) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            showAvatarViewer = false
            actions.setAccountAvatar(uri.toString())
        }
    }
    val launchAvatarPicker = {
        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val versionName = remember(context) {
        runCatching {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: "FlareTune"
        }.getOrDefault("FlareTune")
    }
    val sleepSubtitle = rememberLiveSleepTimerSubtitle()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("settings_screen"),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = topInset, bottom = 6.dp, start = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp).testTag("settings_back"),
                ) {
                    Icon(ClientIcons.ChevronLeft, contentDescription = stringResource(R.string.ft_back))
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.ft_settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val dockSafeBringIntoViewSpec = rememberDockSafeBringIntoViewSpec(bottomInset)
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            @OptIn(ExperimentalFoundationApi::class)
            CompositionLocalProvider(LocalBringIntoViewSpec provides dockSafeBringIntoViewSpec) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + bottomInset)
                        .testTag("settings_scroll_content"),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                val activeDisplayName = state.accountName.ifBlank { state.username.ifBlank { stringResource(R.string.app_name) } }
                val activeMeta = remember(state.username, state.instanceUrl) {
                    listOfNotNull(
                        state.username.takeIf { it.isNotBlank() }?.let { "@$it" },
                        formatAccountOriginHost(state.instanceUrl).takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                }
                SettingsSectionCard(title = stringResource(R.string.ft_settings_section_profile)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !state.busy && !state.profileBusy) { showAvatarViewer = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AccountAvatarBadge(
                            label = activeDisplayName,
                            avatarUrl = state.avatarUrl,
                            size = 56.dp,
                            filled = true,
                            textStyle = MaterialTheme.typography.headlineSmall,
                            contentDescription = stringResource(R.string.ft_profile_view_avatar),
                            modifier = Modifier
                                .clickable(enabled = !state.busy && !state.profileBusy) { showAvatarViewer = true }
                                .testTag("settings_profile_avatar"),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = activeDisplayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("settings_profile_name"),
                            )
                            if (activeMeta.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = activeMeta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (state.profileBusy) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp).testTag("settings_profile_busy"),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                ClientIcons.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    state.profileNotice?.let { notice ->
                        Notice(notice, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                    SettingsDivider()
                    SettingsActionRow(
                        icon = ClientIcons.Edit,
                        title = stringResource(R.string.ft_profile_edit_nickname),
                        subtitle = state.accountName.takeIf { it.isNotBlank() && it != state.username },
                        enabled = !state.profileBusy && !state.busy,
                        tag = "settings_edit_nickname",
                        onClick = {
                            actions.dismissProfileNotice()
                            editingNickname = true
                        },
                    )
                }

                SettingsSectionCard(title = stringResource(R.string.ft_settings_section_accounts)) {
                    val accounts = state.savedAccounts
                    if (accounts.isEmpty()) {
                        val fallbackLabel = state.accountName.ifBlank { state.username }
                        if (fallbackLabel.isNotBlank()) {
                            SettingsInfoRow(
                                title = fallbackLabel,
                                subtitle = listOfNotNull(
                                    state.username.takeIf { it.isNotBlank() }?.let { "@$it" },
                                    formatAccountOriginHost(state.instanceUrl).takeIf { it.isNotBlank() },
                                ).joinToString(" · "),
                                trailingText = stringResource(R.string.ft_account_current),
                            )
                        }
                    } else {
                        accounts.forEachIndexed { index, account ->
                            if (index > 0) SettingsDivider()
                            val label = account.displayName.ifBlank { account.username }
                            val host = formatAccountOriginHost(account.origin)
                            val subline = if (host.isNotBlank()) "@${account.username} · $host" else "@${account.username}"
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable(enabled = !state.busy && !account.active) {
                                        actions.switchAccount(account.accountKey)
                                    }
                                    .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)
                                    .testTag("settings_account_${account.accountKey}"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AccountAvatarBadge(
                                    label = label,
                                    avatarUrl = if (account.active) state.avatarUrl else null,
                                    size = 34.dp,
                                    filled = account.active,
                                    textStyle = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (account.active) FontWeight.SemiBold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (account.active) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                stringResource(R.string.ft_account_current),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        } else if (account.needsReauth) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                stringResource(R.string.ft_account_reauth_required),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                    Text(
                                        subline,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(
                                    onClick = { pendingRemove = account },
                                    enabled = !state.busy,
                                    modifier = Modifier.size(48.dp).testTag("settings_remove_account_${account.accountKey}"),
                                ) {
                                    Icon(
                                        ClientIcons.Trash,
                                        contentDescription = stringResource(R.string.ft_remove_account),
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    SettingsDivider()
                    SettingsActionRow(
                        icon = ClientIcons.Plus,
                        title = stringResource(R.string.ft_add_account),
                        tag = "settings_add_account",
                        enabled = !state.busy,
                        onClick = actions::beginAddAccount,
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = ClientIcons.Logout,
                        title = stringResource(R.string.ft_sign_out),
                        tint = MaterialTheme.colorScheme.error,
                        tag = "sign_out",
                        enabled = !state.busy,
                        onClick = actions::logout,
                    )
                }

                SettingsSectionCard(title = stringResource(R.string.ft_settings_section_appearance)) {
                    SettingsThemeSelectorRow(
                        selected = state.themeMode,
                        onSelect = actions::setThemeMode,
                    )
                }

                SettingsSectionCard(
                    title = stringResource(R.string.ft_settings_section_playback),
                    tag = "ft_settings_section_playback",
                ) {
                    SettingsSwitchRow(
                        icon = ClientIcons.Translate,
                        title = stringResource(R.string.ft_settings_translation_title),
                        subtitle = stringResource(R.string.ft_settings_translation_desc),
                        checked = state.translationEnabled,
                        onCheckedChange = actions::setTranslationEnabled,
                        tag = "settings_translation_switch",
                    )
                    SettingsDivider()
                    SettingsBluetoothLyricsSelectorRow(
                        selected = state.bluetoothLyricsMode,
                        onSelect = actions::setBluetoothLyricsMode,
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = ClientIcons.Sun,
                        title = stringResource(R.string.ft_settings_keep_screen_on_title),
                        subtitle = stringResource(R.string.ft_settings_keep_screen_on_desc),
                        checked = state.keepScreenOnEnabled,
                        onCheckedChange = actions::setKeepScreenOnEnabled,
                        tag = "settings_keep_screen_on_switch",
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = ClientIcons.SleepTimer,
                        title = stringResource(R.string.ft_drawer_sleep_timer),
                        subtitle = sleepSubtitle ?: stringResource(R.string.ft_settings_sleep_fade_note),
                        tag = "settings_sleep_timer",
                        onClick = onOpenSleepTimer,
                    )
                }

                SettingsSectionCard(title = stringResource(R.string.ft_settings_section_storage)) {
                    val cacheSubtitle = when (state.imageCacheClearResult) {
                        ImageCacheClearResult.CLEARING -> stringResource(R.string.ft_settings_clear_cover_cache_clearing)
                        ImageCacheClearResult.CLEARED -> stringResource(R.string.ft_settings_clear_cover_cache_done)
                        ImageCacheClearResult.FAILED -> stringResource(R.string.ft_settings_clear_cover_cache_failed)
                        ImageCacheClearResult.UNAVAILABLE -> stringResource(R.string.ft_settings_clear_cover_cache_unavailable)
                        null -> stringResource(R.string.ft_settings_clear_cover_cache_desc)
                    }
                    SettingsActionRow(
                        icon = ClientIcons.Trash,
                        title = stringResource(R.string.ft_settings_clear_cover_cache),
                        subtitle = cacheSubtitle,
                        subtitleColor = when (state.imageCacheClearResult) {
                            ImageCacheClearResult.CLEARED -> MaterialTheme.colorScheme.primary
                            ImageCacheClearResult.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        enabled = state.imageCacheClearResult != ImageCacheClearResult.CLEARING,
                        tag = "settings_clear_cache",
                        onClick = actions::clearImageCache,
                    )
                    if (listeningInteractions != null) {
                        SettingsDivider()
                        SettingsActionRow(
                            icon = ClientIcons.History,
                            title = stringResource(R.string.ft_settings_clear_history),
                            subtitle = pluralStringOrEmptyHistory(state.playHistory.size),
                            enabled = state.playHistory.isNotEmpty(),
                            tag = "settings_clear_history",
                            onClick = listeningInteractions.clearHistory,
                        )
                    }
                }

                SettingsSectionCard(
                    title = stringResource(R.string.ft_settings_section_about),
                    tag = "ft_settings_section_about",
                ) {
                    val resolvedVersionName = state.appUpdate.currentVersion.ifBlank { versionName }
                    if (state.instanceUrl.isNotBlank()) {
                        SettingsInfoRow(
                            title = stringResource(R.string.ft_settings_current_instance),
                            subtitle = state.instanceUrl,
                        )
                        SettingsDivider()
                    }
                    SettingsInfoRow(
                        title = stringResource(R.string.ft_settings_version),
                        subtitle = resolvedVersionName,
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = stringResource(R.string.ft_settings_include_prereleases_title),
                        subtitle = stringResource(R.string.ft_settings_include_prereleases_desc),
                        checked = state.appUpdate.includePrereleases,
                        onCheckedChange = actions::setIncludePrereleaseUpdates,
                        tag = "settings_include_prereleases_switch",
                    )
                    SettingsDivider()
                    val updateState = state.appUpdate
                    val updateSubtitle = appUpdateStatusSubtitle(updateState)
                    val updateSubtitleColor = when (updateState.status) {
                        AppUpdateStatus.AVAILABLE,
                        AppUpdateStatus.READY_TO_INSTALL -> MaterialTheme.colorScheme.primary
                        AppUpdateStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val hasPendingDialogAction = updateState.availableRelease != null && (
                        updateState.status == AppUpdateStatus.AVAILABLE ||
                            updateState.status == AppUpdateStatus.DOWNLOADING ||
                            updateState.status == AppUpdateStatus.READY_TO_INSTALL ||
                            updateState.status == AppUpdateStatus.INSTALL_PERMISSION_REQUIRED
                        )
                    SettingsActionRow(
                        icon = ClientIcons.Download,
                        title = stringResource(R.string.ft_settings_check_update_title),
                        subtitle = updateSubtitle,
                        subtitleColor = updateSubtitleColor,
                        enabled = updateState.status != AppUpdateStatus.CHECKING,
                        tag = "settings_check_updates",
                        onClick = {
                            if (hasPendingDialogAction) {
                                actions.openAppUpdateDialog()
                            } else {
                                actions.checkForAppUpdate()
                            }
                        },
                    )
                }
            }
            }
        }
    }

    pendingRemove?.let { target ->
        val display = target.displayName.ifBlank { target.username }
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(stringResource(R.string.ft_remove_account_confirm)) },
            text = { Text(stringResource(R.string.ft_remove_account_body, display)) },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text(stringResource(R.string.ft_cancel))
                }
            },
                confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemove = null
                        actions.removeSavedAccount(target.accountKey)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("settings_confirm_remove_account"),
                ) {
                    Text(stringResource(R.string.ft_remove))
                }
            },
        )
    }

    if (editingNickname) {
        EditNicknameDialog(
            initialValue = if (state.accountName == state.username) "" else state.accountName,
            onDismiss = { editingNickname = false },
            onSave = { nextName ->
                editingNickname = false
                actions.updateDisplayName(nextName)
            },
        )
    }

    if (showAvatarViewer) {
        AvatarViewerDialog(
            label = state.accountName.ifBlank { state.username.ifBlank { stringResource(R.string.app_name) } },
            username = state.username,
            avatarUrl = state.avatarUrl,
            onDismiss = { showAvatarViewer = false },
            onChangeAvatar = {
                actions.dismissProfileNotice()
                launchAvatarPicker()
            },
        )
    }

    val dialogRelease = state.appUpdate.availableRelease
    if (state.appUpdate.dialogVisible && dialogRelease != null) {
        AppUpdateDialog(
            state = state.appUpdate,
            fallbackVersionName = versionName,
            release = dialogRelease,
            onDismiss = actions::dismissAppUpdateDialog,
            onDownload = actions::startAppUpdateDownload,
            onCancelDownload = actions::cancelAppUpdateDownload,
            onInstall = actions::installDownloadedAppUpdate,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditNicknameDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember(initialValue) { mutableStateOf(initialValue) }
    // Bound dialog width via BasicAlertDialog instead of AlertDialog's text-slot measurement
    // so OutlinedTextField layout does not trigger repeated intrinsic remeasurement passes.
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().testTag("edit_nickname_dialog"),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.ft_profile_edit_nickname),
                    style = MaterialTheme.typography.headlineSmall,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 80) draft = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.ft_profile_nickname_label)) },
                    supportingText = { Text(stringResource(R.string.ft_profile_nickname_hint)) },
                    modifier = Modifier.fillMaxWidth().testTag("edit_nickname_input"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ft_cancel))
                    }
                    TextButton(
                        onClick = { onSave(draft.trim()) },
                        modifier = Modifier.testTag("edit_nickname_confirm"),
                    ) {
                        Text(stringResource(R.string.ft_library_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun appUpdateStatusSubtitle(state: AppUpdateState): String {
    val latestVersion = state.availableRelease?.versionName.orEmpty()
    return when (state.status) {
        AppUpdateStatus.IDLE -> stringResource(R.string.ft_settings_update_idle)
        AppUpdateStatus.CHECKING -> stringResource(R.string.ft_settings_update_checking)
        AppUpdateStatus.UP_TO_DATE -> stringResource(R.string.ft_settings_update_up_to_date)
        AppUpdateStatus.NO_RELEASE_FOUND -> stringResource(R.string.ft_settings_update_none)
        AppUpdateStatus.AVAILABLE -> stringResource(R.string.ft_settings_update_available, latestVersion)
        AppUpdateStatus.DOWNLOADING -> {
            if (state.totalBytes > 0L) {
                val pct = ((state.downloadedBytes * 100L) / state.totalBytes).toInt().coerceIn(0, 100)
                stringResource(R.string.ft_settings_update_downloading, pct)
            } else {
                stringResource(R.string.ft_settings_update_downloading_unknown)
            }
        }
        AppUpdateStatus.READY_TO_INSTALL -> stringResource(R.string.ft_settings_update_ready, latestVersion)
        AppUpdateStatus.INSTALL_PERMISSION_REQUIRED -> stringResource(R.string.ft_settings_update_permission_required)
        AppUpdateStatus.FAILED -> appUpdateErrorMessage(state.errorKind)
    }
}

@Composable
private fun appUpdateErrorMessage(error: AppUpdateError?): String = when (error) {
    AppUpdateError.REPOSITORY_UNAVAILABLE -> stringResource(R.string.ft_settings_update_error_unavailable)
    AppUpdateError.NO_APK_ASSET -> stringResource(R.string.ft_settings_update_error_no_apk)
    AppUpdateError.INTEGRITY_MISMATCH -> stringResource(R.string.ft_settings_update_error_integrity)
    AppUpdateError.INVALID_PACKAGE -> stringResource(R.string.ft_settings_update_error_invalid_package)
    AppUpdateError.INSTALL_FAILED -> stringResource(R.string.ft_settings_update_error_install)
    AppUpdateError.STORAGE -> stringResource(R.string.ft_settings_update_error_storage)
    AppUpdateError.NETWORK, null -> stringResource(R.string.ft_settings_update_error_network)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppUpdateDialog(
    state: AppUpdateState,
    fallbackVersionName: String,
    release: com.flaretune.client.data.AppUpdateRelease,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val currentVersion = state.currentVersion.ifBlank { fallbackVersionName }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().testTag("app_update_dialog"),
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.ft_update_dialog_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (release.isPrerelease) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.testTag("app_update_prerelease_badge"),
                        ) {
                            Text(
                                text = stringResource(R.string.ft_update_prerelease_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.ft_update_current_version_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = currentVersion,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.testTag("app_update_current_version"),
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.ft_update_latest_version_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = release.versionName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("app_update_latest_version"),
                                    )
                                }
                            }
                            if (release.apkSizeBytes > 0L) {
                                Text(
                                    text = stringResource(
                                        R.string.ft_update_apk_size,
                                        Formatter.formatShortFileSize(context, release.apkSizeBytes),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (state.status == AppUpdateStatus.DOWNLOADING) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (state.totalBytes > 0L) {
                                val fraction = (state.downloadedBytes.toFloat() / state.totalBytes.toFloat()).coerceIn(0f, 1f)
                                val pct = (fraction * 100f).toInt().coerceIn(0, 100)
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier.fillMaxWidth().testTag("app_update_progress"),
                                )
                                Text(
                                    text = stringResource(
                                        R.string.ft_update_download_progress,
                                        Formatter.formatShortFileSize(context, state.downloadedBytes),
                                        Formatter.formatShortFileSize(context, state.totalBytes),
                                        pct,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().testTag("app_update_progress"),
                                )
                                Text(
                                    text = stringResource(
                                        R.string.ft_update_download_progress_unknown,
                                        Formatter.formatShortFileSize(context, state.downloadedBytes),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else if (state.status == AppUpdateStatus.INSTALL_PERMISSION_REQUIRED) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.ft_update_permission_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(12.dp).testTag("app_update_permission_hint"),
                            )
                        }
                    } else if (state.status == AppUpdateStatus.FAILED) {
                        Text(
                            text = appUpdateErrorMessage(state.errorKind),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("app_update_error"),
                        )
                    } else if (release.apkDownloadUrl.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.ft_update_no_apk_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.ft_update_notes_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = release.body.ifBlank { stringResource(R.string.ft_update_notes_empty) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("app_update_notes"),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        state.status == AppUpdateStatus.DOWNLOADING -> {
                            OutlinedButton(
                                onClick = onCancelDownload,
                                modifier = Modifier.fillMaxWidth().testTag("app_update_cancel_button"),
                            ) {
                                Text(stringResource(R.string.ft_update_action_cancel_download))
                            }
                        }
                        state.status == AppUpdateStatus.READY_TO_INSTALL -> {
                            Button(
                                onClick = onInstall,
                                modifier = Modifier.fillMaxWidth().testTag("app_update_install_button"),
                            ) {
                                Text(stringResource(R.string.ft_update_action_install))
                            }
                        }
                        state.status == AppUpdateStatus.INSTALL_PERMISSION_REQUIRED -> {
                            Button(
                                onClick = onInstall,
                                modifier = Modifier.fillMaxWidth().testTag("app_update_grant_permission_button"),
                            ) {
                                Text(stringResource(R.string.ft_update_action_grant_permission))
                            }
                        }
                        !release.apkDownloadUrl.isNullOrBlank() -> {
                            Button(
                                onClick = onDownload,
                                modifier = Modifier.fillMaxWidth().testTag("app_update_download_button"),
                            ) {
                                Text(
                                    stringResource(
                                        if (state.status == AppUpdateStatus.FAILED) {
                                            R.string.ft_update_action_retry_download
                                        } else {
                                            R.string.ft_update_action_download
                                        },
                                    ),
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val safeHtmlUrl = release.htmlUrl.trim().takeIf { it.startsWith("https://", ignoreCase = true) }
                        if (safeHtmlUrl != null) {
                            TextButton(
                                onClick = { runCatching { uriHandler.openUri(safeHtmlUrl) } },
                                modifier = Modifier.weight(1f, fill = false).testTag("app_update_open_release_button"),
                            ) {
                                Text(stringResource(R.string.ft_update_action_open_release))
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("app_update_dismiss_button"),
                        ) {
                            Text(
                                stringResource(
                                    if (state.status == AppUpdateStatus.DOWNLOADING) {
                                        R.string.ft_close
                                    } else {
                                        R.string.ft_update_later
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarViewerDialog(
    label: String,
    username: String,
    avatarUrl: String?,
    onDismiss: () -> Unit,
    onChangeAvatar: () -> Unit,
) {
    val context = LocalContext.current
    val imageLoader = LocalAccountImageLoader.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss)
                .padding(24.dp)
                .testTag("avatar_viewer_dialog"),
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .testTag("avatar_viewer_close"),
            ) {
                Icon(
                    ClientIcons.Close,
                    contentDescription = stringResource(R.string.ft_close),
                    tint = Color.White,
                )
            }

            Column(
                Modifier
                    .align(Alignment.Center)
                    .clickable(enabled = false) {}
                    .widthIn(max = 340.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                AccountAvatarBadge(
                    label = label,
                    avatarUrl = avatarUrl,
                    size = 280.dp,
                    filled = true,
                    shape = RoundedCornerShape(24.dp),
                    textStyle = MaterialTheme.typography.displayLarge,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onChangeAvatar,
                        enabled = !saving,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.testTag("avatar_viewer_change"),
                    ) {
                        Icon(ClientIcons.Background, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ft_profile_change_avatar))
                    }
                    if (!avatarUrl.isNullOrBlank()) {
                        Button(
                            onClick = {
                                if (saving) return@Button
                                saving = true
                                scope.launch {
                                    val saved = saveAvatarToGallery(context, imageLoader, avatarUrl, username)
                                    saving = false
                                    val msgRes = if (saved) R.string.ft_profile_avatar_saved else R.string.ft_profile_avatar_save_failed
                                    Toast.makeText(context, msgRes, Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !saving,
                            modifier = Modifier.testTag("avatar_viewer_save"),
                        ) {
                            if (saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(ClientIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ft_profile_save_avatar))
                        }
                    }
                }
            }
        }
    }
}

private suspend fun saveAvatarToGallery(
    context: Context,
    imageLoader: ImageLoader?,
    avatarUrl: String,
    username: String,
): Boolean = withContext(Dispatchers.IO) {
    val loader = imageLoader ?: return@withContext false
    val result = runCatching {
        loader.execute(
            ImageRequest.Builder(context)
                .data(avatarUrl)
                .allowHardware(false)
                .build(),
        )
    }.getOrNull() as? SuccessResult ?: return@withContext false
    val bitmap = drawableToBitmap(result.drawable) ?: return@withContext false
    val safeUser = username.ifBlank { "user" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
    val filename = "FlareTune_Avatar_${safeUser}_${System.currentTimeMillis()}.png"
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FlareTune")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    var insertedUri: Uri? = null
    runCatching {
        val uri = resolver.insert(collection, values) ?: return@runCatching false
        insertedUri = uri
        resolver.openOutputStream(uri)?.use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw IllegalStateException("compress failed")
            }
        } ?: throw IllegalStateException("null output stream")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    }.getOrElse {
        insertedUri?.let { uri -> runCatching { resolver.delete(uri, null, null) } }
        false
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap? {
    if (drawable is BitmapDrawable) {
        drawable.bitmap?.let { bmp -> if (!bmp.isRecycled) return bmp }
    }
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 512
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 512
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

@Composable
private fun pluralStringOrEmptyHistory(count: Int): String? = if (count > 0) {
    androidx.compose.ui.res.pluralStringResource(R.plurals.ft_track_count, count, count)
} else {
    null
}

@Composable
private fun SettingsSectionCard(
    title: String,
    tag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        thickness = 0.6.dp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun SettingsThemeSelectorRow(
    selected: AppThemeMode,
    onSelect: (AppThemeMode) -> Unit,
) {
    val options = remember {
        listOf(
            Triple(AppThemeMode.LIGHT, R.string.ft_settings_theme_light, ClientIcons.Sun),
            Triple(AppThemeMode.DARK, R.string.ft_settings_theme_dark, ClientIcons.Moon),
            Triple(AppThemeMode.SYSTEM, R.string.ft_settings_theme_system, ClientIcons.Settings),
        )
    }
    val tagFor: (AppThemeMode) -> String = { mode ->
        when (mode) {
            AppThemeMode.LIGHT -> "settings_theme_light"
            AppThemeMode.DARK -> "settings_theme_dark"
            AppThemeMode.SYSTEM -> "settings_theme_system"
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.ft_settings_theme_title),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { (mode, labelRes, icon) ->
                val isSelected = selected == mode
                val containerBg = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                val contentTint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(containerBg)
                        .clickable(role = Role.RadioButton) { onSelect(mode) }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .testTag(tagFor(mode)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = contentTint,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = contentTint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsBluetoothLyricsSelectorRow(
    selected: BluetoothLyricsMode,
    onSelect: (BluetoothLyricsMode) -> Unit,
) {
    val options = remember {
        listOf(
            Triple(BluetoothLyricsMode.OFF, R.string.ft_settings_bluetooth_lyrics_off, "settings_bluetooth_lyrics_off"),
            Triple(BluetoothLyricsMode.BLUETOOTH_AUDIO, R.string.ft_settings_bluetooth_lyrics_bluetooth_audio, "settings_bluetooth_lyrics_bluetooth_audio"),
            Triple(BluetoothLyricsMode.ALWAYS, R.string.ft_settings_bluetooth_lyrics_always, "settings_bluetooth_lyrics_always"),
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("settings_bluetooth_lyrics"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                ClientIcons.Lyrics,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.ft_settings_bluetooth_lyrics_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(R.string.ft_settings_bluetooth_lyrics_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { (mode, labelRes, tag) ->
                val isSelected = selected == mode
                val containerBg = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                val contentTint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(containerBg)
                        .semantics { this.selected = isSelected }
                        .clickable(role = Role.RadioButton) { onSelect(mode) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag(tag),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = contentTint,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            ClientIcons.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = contentTint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
    icon: ImageVector? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    tag: String,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.45f
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = tint.copy(alpha = alpha),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = tint.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor.copy(alpha = alpha),
                )
            }
        }
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    subtitle: String,
    trailingText: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!trailingText.isNullOrBlank()) {
            Spacer(Modifier.width(12.dp))
            Text(
                trailingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
