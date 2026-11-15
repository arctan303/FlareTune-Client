package com.flaretune.client.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flaretune.client.R
import com.flaretune.client.model.Song
import com.flaretune.client.ui.theme.FlareTokens

@Stable
internal class SongMultiSelectState {
    var active by mutableStateOf(false)
    var selectedIds by mutableStateOf<Set<String>>(emptySet())

    fun enter(initialId: String? = null) {
        active = true
        if (!initialId.isNullOrBlank()) {
            selectedIds = selectedIds + initialId
        }
    }

    fun exit() {
        active = false
        selectedIds = emptySet()
    }

    fun toggle(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun allSelected(visibleIds: List<String>): Boolean =
        visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }

    fun toggleSelectAll(visibleIds: List<String>) {
        if (visibleIds.isEmpty()) return
        selectedIds = if (allSelected(visibleIds)) {
            selectedIds - visibleIds.toSet()
        } else {
            selectedIds + visibleIds
        }
    }

    fun prune(validIds: Set<String>) {
        if (selectedIds.isNotEmpty()) {
            val pruned = selectedIds.intersect(validIds)
            if (pruned.size != selectedIds.size) selectedIds = pruned
        }
    }

    fun selectedSongs(available: List<Song>): List<Song> {
        if (selectedIds.isEmpty()) return emptyList()
        return available.filter { it.id in selectedIds }.distinctBy { it.id }
    }
}

@Composable
internal fun rememberSongMultiSelectState(): SongMultiSelectState = remember { SongMultiSelectState() }

@Composable
internal fun MultiSelectTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    tagPrefix: String,
    onSelectAllToggle: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth().testTag("${tagPrefix}_multiselect_bar"),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionIcon(
                icon = ClientIcons.Close,
                description = stringResource(R.string.ft_exit_multiselect),
                onClick = onExit,
                modifier = Modifier.testTag("${tagPrefix}_multiselect_close"),
            )
            Text(
                text = stringResource(R.string.ft_listening_selected_count, selectedCount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("${tagPrefix}_multiselect_count"),
            )
            TextButton(
                onClick = onSelectAllToggle,
                modifier = Modifier
                    .heightIn(min = FlareTokens.TouchTargetMin)
                    .testTag("${tagPrefix}_multiselect_select_all"),
            ) {
                Text(
                    text = stringResource(if (allSelected) R.string.ft_clear_selection else R.string.ft_select_all),
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BatchSongActionBar(
    visible: Boolean,
    selectedSongs: List<Song>,
    state: ClientUiState,
    actions: ClientActions,
    canRemoveFromPlaylist: Boolean = false,
    bottomInset: Dp = 0.dp,
    onBarHeightChanged: (Dp) -> Unit = {},
    onExitMultiSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = LocalListeningInteractions.current
    val density = LocalDensity.current
    var pendingMutationVersion by remember { mutableStateOf<Long?>(null) }
    val unavailableText = stringResource(R.string.ft_feedback_track_unavailable)
    val enqueueText = stringResource(R.string.ft_feedback_enqueued_batch)
    val noticeErrorText = state.libraryMutationNotice?.let { stringResource(clientNoticeMessageRes(it)) }

    LaunchedEffect(state.libraryMutationVersion) {
        val pending = pendingMutationVersion
        if (pending != null && state.libraryMutationVersion > pending) {
            pendingMutationVersion = null
            onExitMultiSelect()
        }
    }
    LaunchedEffect(state.libraryMutationBusy, state.libraryMutationNotice, pendingMutationVersion) {
        val pending = pendingMutationVersion
        if (pending != null && !state.libraryMutationBusy && state.libraryMutationNotice != null) {
            noticeErrorText?.let { interaction?.feedback?.invoke(it) }
            actions.dismissLibraryMutationNotice()
            pendingMutationVersion = null
        }
    }
    LaunchedEffect(visible) {
        if (!visible) {
            pendingMutationVersion = null
            onBarHeightChanged(0.dp)
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.padding(bottom = bottomInset + 8.dp, start = 12.dp, end = 12.dp),
        enter = fadeIn(tween(160)) + slideInVertically(tween(180)) { it / 2 },
        exit = fadeOut(tween(130)) + slideOutVertically(tween(150)) { it / 2 },
    ) {
        val hasSelection = selectedSongs.isNotEmpty()
        val enabled = hasSelection && !state.libraryMutationBusy
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    onBarHeightChanged(with(density) { size.height.toDp() })
                }
                .testTag("batch_song_action_bar"),
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BatchBarChip(
                    label = stringResource(R.string.ft_batch_play_next),
                    icon = ClientIcons.Next,
                    enabled = enabled,
                    tag = "batch_action_play_next",
                ) {
                    val playable = selectedSongs.filter { it.canPlay }
                    if (playable.isEmpty()) {
                        interaction?.feedback?.invoke(unavailableText)
                    } else {
                        actions.playNextSongs(playable)
                        onExitMultiSelect()
                    }
                }
                BatchBarChip(
                    label = stringResource(R.string.ft_batch_enqueue),
                    icon = ClientIcons.Queue,
                    enabled = enabled,
                    tag = "batch_action_enqueue",
                ) {
                    val playable = selectedSongs.filter { it.canPlay }
                    if (playable.isEmpty()) {
                        interaction?.feedback?.invoke(unavailableText)
                    } else {
                        actions.enqueue(playable)
                        interaction?.feedback?.invoke(enqueueText)
                        onExitMultiSelect()
                    }
                }
                BatchBarChip(
                    label = stringResource(R.string.ft_batch_add_to_playlist),
                    icon = ClientIcons.PlaylistAdd,
                    enabled = enabled,
                    tag = "batch_action_playlist",
                ) {
                    interaction?.batchAddToPlaylists?.invoke(selectedSongs, onExitMultiSelect)
                }
                BatchBarChip(
                    label = stringResource(R.string.ft_batch_favorite),
                    icon = ClientIcons.Star,
                    enabled = enabled,
                    tag = "batch_action_favorite",
                ) {
                    pendingMutationVersion = state.libraryMutationVersion
                    actions.addFavorites(selectedSongs)
                }
                if (canRemoveFromPlaylist) {
                    BatchBarChip(
                        label = stringResource(R.string.ft_batch_remove),
                        icon = ClientIcons.Trash,
                        enabled = enabled,
                        destructive = true,
                        tag = "batch_action_remove",
                    ) {
                        interaction?.removeSongsFromPlaylist?.invoke(selectedSongs, onExitMultiSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchBarChip(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    tag: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (destructive && enabled) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = Modifier
            .heightIn(min = FlareTokens.TouchTargetMin)
            .testTag(tag),
    ) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
