package com.flaretune.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.flaretune.client.R
import com.flaretune.client.model.ClientNotice
import com.flaretune.client.model.PlaylistSong
import com.flaretune.client.model.PlaylistSummary
import com.flaretune.client.model.PlaylistTargetResult
import com.flaretune.client.model.Song
import com.flaretune.client.ui.theme.FlareTokens

/** Pure UI: the owner performs mutations, supplies busy/errors, and closes on success. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(
    song: Song,
    favorite: Boolean,
    busy: Boolean,
    notice: ClientNotice?,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onEnqueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onArtist: () -> Unit,
    onAlbum: () -> Unit,
    onDismiss: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    artistAvailable: Boolean = song.artist.isNotBlank(),
    albumAvailable: Boolean = song.album.isNotBlank(),
) {
    val subtitle = remember(song.artist, song.durationSeconds) {
        listOfNotNull(
            song.artist.takeIf { it.isNotBlank() },
            song.durationSeconds.takeIf { it > 0 }?.let { formatTime((it * 1000).toLong()) },
        ).joinToString(" · ")
    }
    LibrarySheet(busy, onDismiss, "song_actions_sheet") {
        Row(
            Modifier.fillMaxWidth().padding(bottom = FlareTokens.SpaceXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FlareTokens.SpaceSm),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ActionIcon(
                icon = ClientIcons.Close,
                description = stringResource(R.string.ft_close),
                onClick = onDismiss,
                enabled = !busy,
                modifier = Modifier.testTag("song_actions_close"),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        notice?.let { Notice(it) }
        Row(
            Modifier.fillMaxWidth().padding(vertical = FlareTokens.SpaceXs),
            horizontalArrangement = Arrangement.spacedBy(FlareTokens.SpaceMd),
            verticalAlignment = Alignment.Top,
        ) {
            SongQuickActionTile(
                label = stringResource(R.string.ft_library_quick_next),
                icon = ClientIcons.Next,
                enabled = !busy && song.canPlay,
                tag = "song_action_next",
                onClick = onPlayNext,
                modifier = Modifier.weight(1f),
            )
            SongQuickActionTile(
                label = stringResource(R.string.ft_library_quick_playlist),
                icon = ClientIcons.PlaylistAdd,
                enabled = !busy,
                tag = "song_action_playlist",
                onClick = onAddToPlaylist,
                modifier = Modifier.weight(1f),
            )
            SongQuickActionTile(
                label = stringResource(if (favorite) R.string.ft_library_quick_favorited else R.string.ft_library_quick_favorite),
                icon = if (favorite) ClientIcons.Heart else ClientIcons.HeartOutlined,
                enabled = !busy,
                selected = favorite,
                stateText = stringResource(if (favorite) R.string.ft_library_unfavorite else R.string.ft_library_favorite),
                tag = "song_action_favorite",
                onClick = onToggleFavorite,
                modifier = Modifier.weight(1f),
            )
        }
        LibraryActionRow(R.string.ft_library_enqueue, ClientIcons.Queue, !busy && song.canPlay, "song_action_enqueue", onEnqueue)
        if (artistAvailable) LibraryActionRow(R.string.ft_library_view_artist, ClientIcons.Artist, !busy, "song_action_artist", onArtist)
        if (albumAvailable) LibraryActionRow(R.string.ft_library_view_album, ClientIcons.Disc, !busy, "song_action_album", onAlbum)
        onRemoveFromPlaylist?.let {
            LibraryActionRow(R.string.ft_library_remove_song, ClientIcons.Trash, !busy, "song_action_remove", it)
        }
        LibraryBusy(busy)
    }
}

@Composable
private fun SongQuickActionTile(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    stateText: String? = null,
) {
    val tileShape = RoundedCornerShape(14.dp)
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val iconTint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val labelTint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            .heightIn(min = FlareTokens.TouchTargetMin)
            .clip(tileShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                this.selected = selected
                if (!stateText.isNullOrBlank()) stateDescription = stateText
            }
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = containerColor,
            shape = tileShape,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = iconTint)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = labelTint,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDraftDialog(
    playlist: PlaylistSummary? = null,
    busy: Boolean,
    notice: ClientNotice?,
    conflict: Boolean = false,
    onSubmit: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val id = playlist?.id
    val baseRevision by rememberSaveable(id) { mutableStateOf(playlist?.revision) }
    var name by rememberSaveable(id) { mutableStateOf(playlist?.name.orEmpty()) }
    var description by rememberSaveable(id) { mutableStateOf(playlist?.description.orEmpty()) }
    val stale = conflict || baseRevision != playlist?.revision
    val protected = playlist?.kind == "favorite"
    val nameCount = name.trim().codePointLength()
    val descriptionCount = description.trim().codePointLength()
    val valid = nameCount in 1..40 && descriptionCount <= 300 && !protected
    val editable = !busy && !stale && !protected
    val maxHeight = (LocalConfiguration.current.screenHeightDp.dp - 48.dp).coerceAtLeast(160.dp)
    // Bound the whole form once. The body scrolls within the remaining height instead of
    // participating in AlertDialog's text-slot measurement with editable multiline fields.
    BasicAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).testTag("playlist_draft_dialog"),
        ) {
            Column(Modifier.padding(FlareTokens.SpaceXl), verticalArrangement = Arrangement.spacedBy(FlareTokens.SpaceBase)) {
                Text(
                    stringResource(if (playlist == null) R.string.ft_library_create_playlist else R.string.ft_library_edit_playlist),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Column(
                    Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(FlareTokens.SpaceMd),
                ) {
                    if (stale) LibraryConflict()
                    if (protected) Text(stringResource(R.string.ft_library_favorite_protected), color = MaterialTheme.colorScheme.error)
                    notice?.let { Notice(it) }
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, enabled = editable,
                        label = { Text(stringResource(R.string.ft_library_playlist_name)) }, singleLine = true,
                        isError = nameCount > 40,
                        supportingText = { Text(stringResource(R.string.ft_library_character_count, nameCount, 40)) },
                        modifier = Modifier.fillMaxWidth().testTag("playlist_name"),
                    )
                    OutlinedTextField(
                        value = description, onValueChange = { description = it }, enabled = editable,
                        label = { Text(stringResource(R.string.ft_library_playlist_description)) }, minLines = 3,
                        isError = descriptionCount > 300,
                        supportingText = { Text(stringResource(R.string.ft_library_character_count, descriptionCount, 300)) },
                        modifier = Modifier.fillMaxWidth().testTag("playlist_description"),
                    )
                    LibraryBusy(busy)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.heightIn(min = FlareTokens.TouchTargetMin).testTag("playlist_draft_cancel")) {
                        Text(stringResource(R.string.ft_library_cancel))
                    }
                    TextButton(
                        enabled = !busy && !stale && valid,
                        onClick = { onSubmit(name.trim(), description.trim()) },
                        modifier = Modifier.heightIn(min = FlareTokens.TouchTargetMin).testTag("playlist_draft_save"),
                    ) { Text(stringResource(R.string.ft_library_save)) }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistTargetSheet(
    songCount: Int,
    playlists: List<PlaylistSummary>,
    busy: Boolean,
    notice: ClientNotice?,
    conflict: Boolean,
    onSubmit: (List<String>) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
    results: List<PlaylistTargetResult> = emptyList(),
) {
    val choices = playlists.distinctBy { it.id }
    val byId = choices.associateBy { it.id }
    val selected = remember { mutableStateListOf<String>() }
    val selectionBase = remember { mutableStateMapOf<String, Long>() }
    // The owner clears current results when retrying. Keep acknowledgements for this open sheet,
    // so a successful target is never retried or treated as a stale pending draft after refresh.
    val acknowledgements = remember { mutableStateMapOf<String, PlaylistTargetResult>() }
    val successes = results.filter { it.playlistId in selected && it.outcome in setOf("applied", "noop") }
    SideEffect {
        successes.forEach { if (acknowledgements[it.playlistId] != it) acknowledgements[it.playlistId] = it }
    }
    val completed = acknowledgements.keys + successes.map { it.playlistId }
    val pendingSelection = selected.filter { it !in completed }
    val stale = conflict || results.any { it.error.equals("REVISION_CONFLICT", ignoreCase = true) } ||
        pendingSelection.any { it !in byId || selectionBase[it] != byId[it]?.revision }
    val validSongCount = songCount in 1..100
    val maxTargets = if (validSongCount) minOf(50, 500 / songCount) else 0
    val pendingIds = choices.filter { it.id in pendingSelection }.map { it.id }
    val valid = validSongCount && pendingIds.isNotEmpty() && selected.size <= maxTargets && pendingSelection.all { it in byId }
    val visibleResults = (acknowledgements.values + results).associateBy { it.playlistId }.values
    LibrarySheet(busy, onDismiss, "playlist_target_sheet") {
        Text(stringResource(R.string.ft_library_choose_playlists), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.ft_library_target_count, selected.size, maxTargets), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!validSongCount) Text(stringResource(R.string.ft_library_invalid_song_count), color = MaterialTheme.colorScheme.error)
        if (stale) LibraryConflict()
        notice?.let { Notice(it) }
        if (choices.isEmpty()) Text(stringResource(R.string.ft_library_no_playlists), Modifier.padding(vertical = FlareTokens.SpaceMd))
        choices.forEach { playlist ->
            val checked = playlist.id in selected
            val enabled = !busy && !stale && playlist.id !in completed && validSongCount && (checked || selected.size < maxTargets)
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = FlareTokens.TouchTargetMin)
                    .toggleable(value = checked, enabled = enabled, role = Role.Checkbox) {
                        if (checked) {
                            selected.remove(playlist.id)
                            selectionBase.remove(playlist.id)
                        } else {
                            selectionBase[playlist.id] = playlist.revision
                            selected.add(playlist.id)
                        }
                    }.testTag("playlist_target_" + playlist.id).padding(vertical = FlareTokens.SpaceSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FlareTokens.SpaceMd),
            ) {
                Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.ft_library_song_count, playlist.songCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        visibleResults.forEach { result ->
            val label = choices.firstOrNull { it.id == result.playlistId }?.name
                ?: stringResource(R.string.ft_library_unknown_playlist)
            val message = when {
                result.outcome == "applied" -> R.string.ft_library_target_applied
                result.outcome == "noop" -> R.string.ft_library_target_noop
                result.error.equals("REVISION_CONFLICT", ignoreCase = true) -> R.string.ft_library_target_conflict
                result.error == "PLAYLIST_SONG_LIMIT_REACHED" -> R.string.ft_library_target_full
                result.error == "PLAYLIST_NOT_FOUND" -> R.string.ft_library_target_missing
                else -> R.string.ft_library_target_failed
            }
            Text(
                stringResource(message, label),
                color = if (result.outcome == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("playlist_target_result_" + result.playlistId).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        TextButton(onClick = onCreate, enabled = !busy, modifier = Modifier.heightIn(min = FlareTokens.TouchTargetMin).testTag("playlist_target_create")) {
            Text(stringResource(R.string.ft_library_create_playlist))
        }
        LibraryBusy(busy)
        Button(
            enabled = !busy && !stale && valid,
            onClick = { onSubmit(pendingIds) },
            modifier = Modifier.fillMaxWidth().heightIn(min = FlareTokens.TouchTargetMin).testTag("playlist_target_submit"),
        ) { Text(stringResource(R.string.ft_library_add_selected)) }
        LibraryCloseButton(!busy, "playlist_target_cancel", onDismiss)
    }
}

private class PlaylistOrderController(
    val draft: androidx.compose.runtime.snapshots.SnapshotStateList<PlaylistSong>,
    val stale: Boolean,
    val valid: Boolean,
    val canEdit: Boolean,
    val modified: Boolean,
    val draggedIdState: MutableState<String?>,
    val dragCenterYState: MutableFloatState,
) {
    var draggedId: String?
        get() = draggedIdState.value
        set(value) { draggedIdState.value = value }
    var dragCenterY: Float
        get() = dragCenterYState.floatValue
        set(value) { dragCenterYState.floatValue = value }
}

@Composable
private fun rememberPlaylistOrderController(
    playlist: PlaylistSummary,
    entries: List<PlaylistSong>,
    busy: Boolean,
    conflict: Boolean,
): PlaylistOrderController {
    // Keep complete membership, including unavailable tracks. Playback projections are unsuitable here.
    val initial = remember(playlist.id) { entries.toList() }
    val initialIds = remember(initial) { initial.map { it.song.id } }
    val baseRevision by rememberSaveable(playlist.id) { mutableStateOf(playlist.revision) }
    val draft = remember(playlist.id) { mutableStateListOf<PlaylistSong>().apply { addAll(initial) } }
    val stale = conflict || baseRevision != playlist.revision || initial != entries
    val valid = draft.map { it.song.id }.distinct().size == draft.size
    val canEdit = !busy && !stale
    val modified by remember(initialIds) { derivedStateOf { draft.map { it.song.id } != initialIds } }
    val draggedIdState = remember(playlist.id) { mutableStateOf<String?>(null) }
    val dragCenterYState = remember(playlist.id) { mutableFloatStateOf(0f) }
    LaunchedEffect(canEdit) { if (!canEdit) draggedIdState.value = null }
    return PlaylistOrderController(
        draft = draft,
        stale = stale,
        valid = valid,
        canEdit = canEdit,
        modified = modified,
        draggedIdState = draggedIdState,
        dragCenterYState = dragCenterYState,
    )
}

@Composable
private fun PlaylistOrderListContent(
    controller: PlaylistOrderController,
    busy: Boolean,
    notice: ClientNotice?,
    modifier: Modifier = Modifier,
) {
    val draft = controller.draft
    val canEdit = controller.canEdit
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val edgeThresholdPx = remember(density) { with(density) { 56.dp.toPx() } }
    val maxScrollStepPx = remember(density) { with(density) { 14.dp.toPx() } }
    var listHeightPx by remember { mutableFloatStateOf(0f) }
    fun finishDrag() { controller.draggedId = null }
    fun reorderDragged(id: String, direction: Int) {
        if (direction == 0) return
        val visible = listState.layoutInfo.visibleItemsInfo
        var current = draft.indexOfFirst { it.song.id == id }
        while (current >= 0 && current + direction in draft.indices) {
            val next = current + direction
            val nextInfo = visible.firstOrNull { it.key == draft[next].song.id } ?: break
            val threshold = if (direction > 0) nextInfo.offset + nextInfo.size * 0.35f else nextInfo.offset + nextInfo.size * 0.65f
            if ((direction > 0 && controller.dragCenterY < threshold) || (direction < 0 && controller.dragCenterY > threshold)) break
            draft.add(next, draft.removeAt(current))
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            current = next
        }
    }
    LaunchedEffect(controller.draggedId) {
        val activeId = controller.draggedId ?: return@LaunchedEffect
        while (isActive && controller.draggedId == activeId) {
            val pointerY = controller.dragCenterY
            val delta = when {
                listHeightPx <= 0f -> 0f
                pointerY < edgeThresholdPx && listState.canScrollBackward -> {
                    val ratio = ((edgeThresholdPx - pointerY) / edgeThresholdPx).coerceIn(0.2f, 1f)
                    -maxScrollStepPx * ratio
                }
                pointerY > listHeightPx - edgeThresholdPx && listState.canScrollForward -> {
                    val ratio = ((pointerY - (listHeightPx - edgeThresholdPx)) / edgeThresholdPx).coerceIn(0.2f, 1f)
                    maxScrollStepPx * ratio
                }
                else -> 0f
            }
            if (delta != 0f) {
                listState.scrollBy(delta)
                reorderDragged(activeId, if (delta > 0f) 1 else -1)
            }
            delay(16L)
        }
    }
    LazyColumn(
        modifier = modifier.onSizeChanged { listHeightPx = it.height.toFloat() }.testTag("playlist_order_list"),
        state = listState,
        userScrollEnabled = controller.draggedId == null,
        verticalArrangement = Arrangement.spacedBy(FlareTokens.SpaceSm),
    ) {
        if (controller.stale) item(key = "order-stale") { LibraryConflict() }
        if (notice != null) item(key = "order-notice") { Notice(notice) }
        if (!controller.valid) item(key = "order-invalid") {
            Text(stringResource(R.string.ft_library_invalid_membership), color = MaterialTheme.colorScheme.error)
        }
        if (draft.isEmpty()) item(key = "order-empty") {
            Text(stringResource(R.string.ft_library_empty_playlist))
        }
        itemsIndexed(draft, key = { _, entry -> entry.song.id }) { index, entry ->
            val id = entry.song.id
            val dragging = controller.draggedId == id
            val slotInfo = if (dragging) listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } else null
            Box(
                Modifier.fillMaxWidth().zIndex(if (dragging) 1f else 0f).testTag("playlist_order_entry_" + id),
            ) {
                Column(
                    Modifier.fillMaxWidth().graphicsLayer {
                        translationY = if (dragging && slotInfo != null) {
                            controller.dragCenterY - (slotInfo.offset + slotInfo.size / 2f)
                        } else 0f
                    }.then(if (dragging) Modifier.background(MaterialTheme.colorScheme.surfaceContainer) else Modifier),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.song.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        val dragLabel = stringResource(R.string.ft_library_drag_song, entry.song.title)
                        Box(
                            Modifier.size(FlareTokens.TouchTargetMin).testTag("playlist_order_drag_" + id)
                                .semantics { contentDescription = dragLabel; if (!canEdit) disabled() }
                                .pointerInput(id, canEdit) {
                                    if (canEdit) detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                                                ?: return@detectDragGesturesAfterLongPress
                                            controller.dragCenterY = info.offset + info.size / 2f
                                            controller.draggedId = id
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            if (controller.draggedId == id) {
                                                controller.dragCenterY += amount.y
                                                val direction = if (amount.y > 0f) 1 else if (amount.y < 0f) -1 else 0
                                                reorderDragged(id, direction)
                                            }
                                        },
                                        onDragEnd = ::finishDrag,
                                        onDragCancel = ::finishDrag,
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(ClientIcons.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (entry.song.artist.isNotBlank()) Text(entry.song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!entry.canPlay) Text(stringResource(R.string.ft_library_unavailable_member), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ft_library_position, index + 1, draft.size), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                draft.add(index - 1, draft.removeAt(index))
                            },
                            enabled = canEdit && controller.draggedId == null && index > 0,
                            modifier = Modifier.size(FlareTokens.TouchTargetMin).testTag("playlist_order_up_" + id),
                        ) { Icon(ClientIcons.ChevronDown, stringResource(R.string.ft_library_move_up, entry.song.title), Modifier.rotate(180f)) }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                draft.add(index + 1, draft.removeAt(index))
                            },
                            enabled = canEdit && controller.draggedId == null && index < draft.lastIndex,
                            modifier = Modifier.size(FlareTokens.TouchTargetMin).testTag("playlist_order_down_" + id),
                        ) { Icon(ClientIcons.ChevronDown, stringResource(R.string.ft_library_move_down, entry.song.title)) }
                    }
                    HorizontalDivider()
                }
            }
        }
        if (busy) item(key = "order-busy") { LibraryBusy(busy) }
    }
}

@Composable
fun PlaylistOrderDialog(
    playlist: PlaylistSummary,
    entries: List<PlaylistSong>,
    busy: Boolean,
    notice: ClientNotice?,
    conflict: Boolean,
    onSubmit: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val controller = rememberPlaylistOrderController(playlist, entries, busy, conflict)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        modifier = Modifier.testTag("playlist_order_dialog"),
        title = { Text(stringResource(R.string.ft_library_order_title, playlist.name)) },
        text = {
            PlaylistOrderListContent(
                controller = controller,
                busy = busy,
                notice = notice,
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
            )
        },
        confirmButton = {
            TextButton(
                enabled = controller.canEdit && controller.valid && controller.modified && controller.draggedId == null,
                onClick = { onSubmit(controller.draft.map { it.song.id }) },
                modifier = Modifier.heightIn(min = FlareTokens.TouchTargetMin).testTag("playlist_order_save"),
            ) { Text(stringResource(R.string.ft_library_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.heightIn(min = FlareTokens.TouchTargetMin).testTag("playlist_order_cancel")) {
                Text(stringResource(R.string.ft_library_cancel))
            }
        },
    )
}

@Composable
internal fun PlaylistOrderSheet(
    playlist: PlaylistSummary,
    entries: List<PlaylistSong>,
    busy: Boolean,
    notice: ClientNotice?,
    conflict: Boolean,
    onSubmit: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val controller = rememberPlaylistOrderController(playlist, entries, busy, conflict)
    FlareModalSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetGesturesEnabled = !busy && controller.draggedId == null,
        modifier = Modifier.testTag("playlist_order_dialog"),
    ) {
        val absorbBottom = rememberAbsorbBottomOverscroll()
        Column(
            Modifier.fillMaxWidth().heightIn(max = 680.dp).navigationBarsPadding().nestedScroll(absorbBottom),
        ) {
            FlareSheetHeader(
                title = stringResource(R.string.ft_library_order_title, playlist.name),
                subtitle = stringResource(R.string.ft_library_song_count, controller.draft.size),
                onClose = onDismiss,
                closeEnabled = !busy,
            )
            PlaylistOrderListContent(
                controller = controller,
                busy = busy,
                notice = notice,
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth().padding(horizontal = FlareTokens.SpaceXl),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = FlareTokens.SpaceXl, vertical = FlareTokens.SpaceMd),
                horizontalArrangement = Arrangement.spacedBy(FlareTokens.SpaceSm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = FlareTokens.TouchTargetMin).testTag("playlist_order_cancel"),
                ) { Text(stringResource(R.string.ft_library_cancel)) }
                Button(
                    enabled = controller.canEdit && controller.valid && controller.modified && controller.draggedId == null,
                    onClick = { onSubmit(controller.draft.map { it.song.id }) },
                    modifier = Modifier.heightIn(min = FlareTokens.TouchTargetMin).testTag("playlist_order_save"),
                ) { Text(stringResource(R.string.ft_library_save)) }
            }
        }
    }
}

@Composable
private fun LibrarySheet(busy: Boolean, onDismiss: () -> Unit, tag: String, content: @Composable ColumnScope.() -> Unit) {
    FlareModalSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetGesturesEnabled = !busy,
        modifier = Modifier.testTag(tag),
    ) {
        val absorbBottom = rememberAbsorbBottomOverscroll()
        Column(
            Modifier.fillMaxWidth().heightIn(max = 680.dp).nestedScroll(absorbBottom).verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = FlareTokens.SpaceXl, vertical = FlareTokens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(FlareTokens.SpaceSm), content = content,
        )
    }
}

@Composable
private fun LibraryActionRow(label: Int, icon: ImageVector, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = FlareTokens.TouchTargetMin)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).testTag(tag)
            .padding(vertical = FlareTokens.SpaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FlareTokens.SpaceBase),
    ) {
        val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f)
        Icon(icon, null, Modifier.size(24.dp), tint = color)
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LibraryCloseButton(enabled: Boolean, tag: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = FlareTokens.TouchTargetMin).testTag(tag)) {
        Text(stringResource(R.string.ft_library_cancel))
    }
}

@Composable
private fun LibraryBusy(busy: Boolean) {
    if (busy) Row(Modifier.fillMaxWidth().padding(vertical = FlareTokens.SpaceSm).testTag("library_busy"), horizontalArrangement = Arrangement.spacedBy(FlareTokens.SpaceMd), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(stringResource(R.string.ft_library_saving), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun LibraryConflict() {
    Text(
        stringResource(R.string.ft_library_conflict), color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag("library_conflict").semantics { liveRegion = LiveRegionMode.Polite },
    )
}

private fun String.codePointLength(): Int = codePointCount(0, length)