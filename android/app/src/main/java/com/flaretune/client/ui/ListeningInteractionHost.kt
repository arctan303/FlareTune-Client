package com.flaretune.client.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.flaretune.client.R
import com.flaretune.client.model.*
import java.util.Locale

/** One action surface for rows, shelves, the queue and the full-screen player. */
internal data class ListeningInteractions(
    val song: (Song) -> Unit,
    val createPlaylist: (List<String>) -> Unit,
    val addToPlaylists: (List<Song>) -> Unit,
    val playlist: (PlaylistSummary) -> Unit,
    val orderPlaylist: (PlaylistSummary) -> Unit = {},
    val addSongs: (PlaylistSummary) -> Unit,
    val artists: () -> Unit,
    val albums: (String) -> Unit,
    val statistics: () -> Unit,
    val clearHistory: () -> Unit,
    val clearQueue: () -> Unit,
    val feedback: (String) -> Unit = {},
)
internal val LocalListeningInteractions = staticCompositionLocalOf<ListeningInteractions?> { null }

internal fun listeningArtistNames(raw: String): List<String> = raw
    .replace(Regex("\\s+(feat\\.?|ft\\.?|featuring|with|vs\\.?|v\\.s\\.?|x)\\s+", RegexOption.IGNORE_CASE), " / ")
    .replace(Regex("[&,;、•·\\\\|]"), " / ").split('/')
    .map(String::trim).filter { Regex("[\\p{L}\\p{N}]").containsMatchIn(it) }
    .distinctBy { it.lowercase(Locale.ROOT) }

private enum class ListeningConfirmation { DELETE_PLAYLIST, CLEAR_HISTORY, CLEAR_QUEUE, REMOVE_SONG }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListeningInteractionHost(
    state: ClientUiState,
    actions: ClientActions,
    imageLoader: ImageLoader?,
    onNavigateToContent: () -> Unit = {},
    onFeedback: (String) -> Unit = {},
    content: @Composable () -> Unit,
) {
    var song by remember { mutableStateOf<Song?>(null) }
    var targetSongs by remember { mutableStateOf<List<Song>?>(null) }
    var playlistOptions by remember { mutableStateOf<PlaylistSummary?>(null) }
    var formOpen by remember { mutableStateOf(false) }
    var formPlaylist by remember { mutableStateOf<PlaylistSummary?>(null) }
    var initialIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var orderPlaylist by remember { mutableStateOf<PlaylistSummary?>(null) }
    var waitingOrderId by remember { mutableStateOf<String?>(null) }
    var pickerTarget by remember { mutableStateOf<PlaylistSummary?>(null) }
    var waitingPickerId by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<ListeningConfirmation?>(null) }
    var confirmingPlaylist by remember { mutableStateOf<PlaylistSummary?>(null) }
    var confirmingSong by remember { mutableStateOf<Song?>(null) }
    var artistChoices by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var artistsOpen by remember { mutableStateOf(false) }
    var albumsOpen by remember { mutableStateOf(false) }
    var statsOpen by remember { mutableStateOf(false) }
    var submittedVersion by remember { mutableStateOf<Long?>(null) }
    var pendingFeedback by remember { mutableStateOf<String?>(null) }
    var coverTarget by remember { mutableStateOf<Pair<String, PlaylistSummary>?>(null) }
    val latestState by rememberUpdatedState(state)
    val owner = state.instanceUrl + "\n" + state.username

    fun closeEditors() {
        song = null; targetSongs = null; playlistOptions = null; formOpen = false
        formPlaylist = null; initialIds = emptyList(); orderPlaylist = null; waitingOrderId = null
        pickerTarget = null; waitingPickerId = null; confirm = null; confirmingPlaylist = null; confirmingSong = null
        submittedVersion = null
        actions.closePlaylistSongPicker()
    }
    fun submit(work: () -> Unit) {
        if (latestState.libraryMutationBusy) return
        submittedVersion = latestState.libraryMutationVersion
        work()
    }
    fun beginIntent() { actions.dismissLibraryMutationNotice(); submittedVersion = null; pendingFeedback = null }
    fun openOrderEditor(target: PlaylistSummary) {
        beginIntent()
        val current = latestState
        if (current.selectedPlaylist?.id == target.id && !current.playlistLoading) {
            orderPlaylist = current.selectedPlaylist
        } else {
            waitingOrderId = target.id
            actions.openPlaylist(target)
        }
    }
    fun openSongPicker(target: PlaylistSummary) {
        beginIntent()
        val current = latestState
        if (current.selectedPlaylist?.id == target.id && !current.playlistLoading && current.playlistNotice == null) {
            pickerTarget = current.selectedPlaylist
            actions.searchPlaylistSongs("")
        } else {
            waitingPickerId = target.id
            actions.openPlaylist(target)
        }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = coverTarget
        coverTarget = null
        val current = latestState
        if (uri != null && target != null && current.screen == ClientScreen.LIBRARY
            && target.first == current.instanceUrl + "\n" + current.username) {
            submit { actions.setPlaylistCover(target.second, uri.toString()) }
        }
    }
    LaunchedEffect(state.screen, owner) {
        closeEditors(); coverTarget = null; artistChoices = emptyList()
        artistsOpen = false; albumsOpen = false; statsOpen = false
    }
    LaunchedEffect(state.libraryMutationVersion) {
        val previous = submittedVersion
        if (previous != null && state.libraryMutationVersion > previous) {
            val feedbackText = pendingFeedback
            pendingFeedback = null
            closeEditors()
            if (feedbackText != null) onFeedback(feedbackText)
        }
    }
    LaunchedEffect(state.selectedPlaylist?.id, state.playlistLoading, state.selectedPlaylistEntries, waitingPickerId) {
        if (orderPlaylist != null && orderPlaylist?.id != state.selectedPlaylist?.id) orderPlaylist = null
        if (waitingOrderId != null && waitingOrderId == state.selectedPlaylist?.id && !state.playlistLoading && state.playlistNotice == null) {
            orderPlaylist = state.selectedPlaylist; waitingOrderId = null
        }
        if (pickerTarget != null && pickerTarget?.id != state.selectedPlaylist?.id) {
            pickerTarget = null; actions.closePlaylistSongPicker()
        }
        if (waitingPickerId == state.selectedPlaylist?.id && waitingPickerId != null && !state.playlistLoading && state.playlistNotice == null) {
            pickerTarget = state.selectedPlaylist; waitingPickerId = null
            actions.searchPlaylistSongs("")
        } else if (state.selectedPlaylist == null && !state.playlistLoading) {
            waitingPickerId = null; waitingOrderId = null
        }
    }
    val interactions = ListeningInteractions(
        song = { beginIntent(); song = it },
        createPlaylist = { ids -> beginIntent(); initialIds = ids; formPlaylist = null; formOpen = true },
        addToPlaylists = { songs -> beginIntent(); targetSongs = songs.distinctBy { it.id } },
        playlist = { beginIntent(); playlistOptions = it },
        orderPlaylist = ::openOrderEditor,
        addSongs = ::openSongPicker,
        artists = { artistsOpen = true; actions.refreshArtists() },
        albums = { query -> albumsOpen = true; actions.searchAlbums(query) },
        statistics = { statsOpen = true; actions.refreshPlayStats() },
        clearHistory = { confirm = ListeningConfirmation.CLEAR_HISTORY },
        clearQueue = { confirm = ListeningConfirmation.CLEAR_QUEUE },
        feedback = onFeedback,
    )
    CompositionLocalProvider(LocalListeningInteractions provides interactions) { content() }
    if (state.screen != ClientScreen.LIBRARY) return

    song?.let { selected ->
        val relatedArtists = selected.relatedArtists()
        val relatedAlbum = selected.relatedAlbum() ?: if (!selected.isYouTube) {
            // Legacy local rows may omit the reference; use only an already loaded local entity.
            (listOfNotNull(state.selectedAlbum) + state.albums + state.searchAlbums + state.artistAlbums)
                .firstOrNull { !it.id.startsWith("yt:") && it.title == selected.album && it.artist == selected.artist }
        } else null
        val canRemove = state.selectedPlaylist?.kind != "favorite"
            && state.selectedPlaylistEntries.any { it.song.id == selected.id }
        val isFavorite = selected.id in state.favoriteSongIds
        val favoriteFeedback = stringResource(
            if (isFavorite) R.string.ft_feedback_unfavorited else R.string.ft_feedback_favorited,
        )
        val playNextFeedback = stringResource(R.string.ft_feedback_play_next)
        val enqueuedFeedback = stringResource(R.string.ft_feedback_enqueued)
        SongActionsSheet(
            song = selected, favorite = isFavorite,
            busy = state.libraryMutationBusy, notice = state.libraryMutationNotice,
            onToggleFavorite = {
                pendingFeedback = favoriteFeedback
                submit { actions.toggleFavorite(selected) }
            },
            onPlayNext = {
                actions.playNext(selected)
                song = null
                onFeedback(playNextFeedback)
            },
            onEnqueue = {
                actions.enqueue(listOf(selected))
                song = null
                onFeedback(enqueuedFeedback)
            },
            onAddToPlaylist = { song = null; targetSongs = listOf(selected); beginIntent() },
            onArtist = {
                song = null
                if (relatedArtists.size == 1) {
                    onNavigateToContent()
                    actions.openArtist(relatedArtists.single())
                } else if (relatedArtists.isNotEmpty()) artistChoices = relatedArtists
            },
            onAlbum = {
                song = null
                relatedAlbum?.let {
                    onNavigateToContent()
                    actions.openAlbum(it)
                }
            },
            artistAvailable = relatedArtists.isNotEmpty(),
            albumAvailable = relatedAlbum != null,
            onDismiss = { if (!state.libraryMutationBusy) song = null },
            onRemoveFromPlaylist = if (canRemove) {{ confirmingSong = selected; song = null; confirm = ListeningConfirmation.REMOVE_SONG }} else null,
        )
    }
    targetSongs?.let { selected ->
        PlaylistTargetSheet(
            songCount = selected.size, playlists = state.playlists, busy = state.libraryMutationBusy,
            notice = state.libraryMutationNotice, conflict = state.libraryMutationConflict,
            onSubmit = { ids -> submit { actions.addPlaylistSongs(ids, selected.map { it.id }) } },
            onCreate = { initialIds = selected.map { it.id }; targetSongs = null; formPlaylist = null; formOpen = true; beginIntent() },
            onDismiss = { if (!state.libraryMutationBusy) targetSongs = null },
            results = state.libraryMutationResults,
        )
    }
    if (formOpen) PlaylistDraftDialog(
        playlist = formPlaylist, busy = state.libraryMutationBusy,
        notice = state.libraryMutationNotice, conflict = state.libraryMutationConflict,
        onSubmit = { name, description ->
            submit {
                val target = formPlaylist
                if (target == null) actions.createPlaylist(name, description, initialIds)
                else actions.updatePlaylist(target, name, description)
            }
        }, onDismiss = { if (!state.libraryMutationBusy) formOpen = false },
    )
    orderPlaylist?.let { target ->
        PlaylistOrderSheet(
            playlist = state.selectedPlaylist?.takeIf { it.id == target.id } ?: target,
            entries = state.selectedPlaylistEntries, busy = state.libraryMutationBusy,
            notice = state.libraryMutationNotice, conflict = state.libraryMutationConflict,
            onSubmit = { ids -> submit { actions.replacePlaylistOrder(ids) } },
            onDismiss = { if (!state.libraryMutationBusy) { orderPlaylist = null; if (state.libraryMutationConflict) actions.refreshPlaylist() } },
        )
    }
    playlistOptions?.let { target ->
        FlareModalSheet(onDismissRequest = { playlistOptions = null }) {
            val absorbBottom = rememberAbsorbBottomOverscroll()
            Column(Modifier.fillMaxWidth().navigationBarsPadding().nestedScroll(absorbBottom).verticalScroll(rememberScrollState())) {
                FlareSheetHeader(
                    title = target.name,
                    subtitle = stringResource(R.string.ft_library_song_count, target.songCount),
                    onClose = { playlistOptions = null },
                )
                ListeningMenuRow(stringResource(R.string.ft_listening_play_playlist), "playlist_menu_play") {
                    actions.playPlaylist(target); playlistOptions = null
                }
                ListeningMenuRow(stringResource(R.string.ft_listening_shuffle_playlist), "playlist_menu_shuffle") {
                    actions.playPlaylist(target, true); playlistOptions = null
                }
                ListeningMenuRow(stringResource(R.string.ft_listening_add_songs), "playlist_menu_add") {
                    playlistOptions = null; openSongPicker(target)
                }
                ListeningMenuRow(stringResource(R.string.ft_listening_edit_order), "playlist_menu_order") {
                    playlistOptions = null
                    openOrderEditor(target)
                }
                if (target.kind != "favorite") {
                    ListeningMenuRow(stringResource(R.string.ft_listening_edit_playlist), "playlist_menu_edit") {
                        formPlaylist = target; formOpen = true; playlistOptions = null
                    }
                    ListeningMenuRow(stringResource(R.string.ft_listening_change_cover), "playlist_menu_cover") {
                        coverTarget = owner to target; playlistOptions = null
                        coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    if (target.customCoverUrl != null) ListeningMenuRow(stringResource(R.string.ft_listening_reset_cover), "playlist_menu_reset_cover") {
                        submit { actions.clearPlaylistCover(target) }
                    }
                    ListeningMenuRow(stringResource(R.string.ft_listening_delete_playlist), "playlist_menu_delete") {
                        confirmingPlaylist = target; playlistOptions = null; confirm = ListeningConfirmation.DELETE_PLAYLIST
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    pickerTarget?.let { target ->
        PlaylistSongPickerSheet(state, actions, imageLoader,
            onSubmit = { ids -> submit { actions.addPlaylistSongs(listOf(target.id), ids) } },
            onDismiss = { if (!state.libraryMutationBusy) { pickerTarget = null; actions.closePlaylistSongPicker() } },
        )
    }
    confirm?.let { operation ->
        val title = stringResource(when (operation) {
            ListeningConfirmation.DELETE_PLAYLIST -> R.string.ft_listening_delete_playlist
            ListeningConfirmation.CLEAR_HISTORY -> R.string.ft_listening_clear_history
            ListeningConfirmation.CLEAR_QUEUE -> R.string.ft_listening_clear_queue
            ListeningConfirmation.REMOVE_SONG -> R.string.ft_listening_remove_song
        })
        AlertDialog(
            onDismissRequest = { if (!state.libraryMutationBusy) confirm = null },
            title = { Text(title) }, text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(when (operation) {
                        ListeningConfirmation.DELETE_PLAYLIST -> stringResource(R.string.ft_listening_delete_message, confirmingPlaylist?.name.orEmpty())
                        ListeningConfirmation.CLEAR_HISTORY -> stringResource(R.string.ft_listening_clear_history_message)
                        ListeningConfirmation.CLEAR_QUEUE -> stringResource(R.string.ft_listening_clear_queue_message)
                        ListeningConfirmation.REMOVE_SONG -> stringResource(R.string.ft_listening_remove_message, confirmingSong?.title.orEmpty())
                    })
                    state.libraryMutationNotice?.let { Notice(it) }
                }
            }, dismissButton = { TextButton(onClick = { confirm = null }, enabled = !state.libraryMutationBusy) { Text(stringResource(R.string.ft_library_cancel)) } },
            confirmButton = {
                TextButton(enabled = !state.libraryMutationBusy, modifier = Modifier.testTag("listening_confirm"), onClick = {
                    when (operation) {
                        ListeningConfirmation.DELETE_PLAYLIST -> confirmingPlaylist?.let { target -> submit { actions.deletePlaylist(target) } }
                        ListeningConfirmation.REMOVE_SONG -> confirmingSong?.let { selected -> submit { actions.removePlaylistSongs(listOf(selected.id)) } }
                        ListeningConfirmation.CLEAR_HISTORY -> { actions.clearHistory(); confirm = null }
                        ListeningConfirmation.CLEAR_QUEUE -> { actions.clearQueue(); confirm = null }
                    }
                }) { Text(title) }
            },
        )
    }
    if (artistChoices.isNotEmpty()) FlareModalSheet(onDismissRequest = { artistChoices = emptyList() }) {
        val absorbBottom = rememberAbsorbBottomOverscroll()
        Column(Modifier.fillMaxWidth().navigationBarsPadding().nestedScroll(absorbBottom).verticalScroll(rememberScrollState())) {
            FlareSheetHeader(title = stringResource(R.string.ft_listening_artists), onClose = { artistChoices = emptyList() })
            artistChoices.forEach { artist ->
                ListeningMenuRow(artist.name, "artist_chooser_" + (artist.id ?: artist.name)) {
                    artistChoices = emptyList()
                    onNavigateToContent()
                    actions.openArtist(artist)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (artistsOpen) ArtistBrowseSheet(state, actions, imageLoader) { artistsOpen = false }
    if (albumsOpen) ListeningAlbumSearchSheet(state, actions, imageLoader) { albumsOpen = false }
    if (statsOpen) ListeningStatsSheet(state, actions, imageLoader) { statsOpen = false }
}

@Composable
internal fun ListeningMenuRow(label: String, tag: String, enabled: Boolean = true, action: () -> Unit) {
    TextButton(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
        Text(label, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PlaylistSongPickerSheet(
    state: ClientUiState, actions: ClientActions, loader: ImageLoader?,
    onSubmit: (List<String>) -> Unit, onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    FlareModalSheet(onDismissRequest = onDismiss, sheetGesturesEnabled = !state.libraryMutationBusy) {
        val absorbBottom = rememberAbsorbBottomOverscroll()
        Column(Modifier.fillMaxWidth().fillMaxHeight(.90f).navigationBarsPadding().nestedScroll(absorbBottom).testTag("playlist_song_picker")) {
            FlareSheetHeader(
                title = stringResource(R.string.ft_listening_add_songs),
                subtitle = if (selected.isNotEmpty()) stringResource(R.string.ft_listening_selected_count, selected.size) else null,
                onClose = onDismiss,
                closeEnabled = !state.libraryMutationBusy,
            )
            OutlinedTextField(
                value = state.pickerQuery,
                onValueChange = actions::searchPlaylistSongs,
                singleLine = true,
                label = { Text(stringResource(R.string.ft_search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("playlist_song_picker_query"),
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                state.pickerNotice?.let { Notice(it) }
                state.libraryMutationNotice?.let { Notice(it) }
            }
            if (state.pickerLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                if (!state.pickerLoading && state.pickerSongs.isEmpty()) item { Text(stringResource(R.string.ft_empty_search), Modifier.padding(16.dp)) }
                items(state.pickerSongs, key = { it.id }) { candidate ->
                    val checked = candidate.id in selected
                    val toggle = { if (checked) selected = selected - candidate.id else if (selected.size < 100) selected = selected + candidate.id }
                    Row(Modifier.fillMaxWidth().clickable(enabled = !state.libraryMutationBusy, onClick = toggle).padding(vertical = 8.dp).testTag("picker_song_" + candidate.id), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked, onCheckedChange = { toggle() }, enabled = !state.libraryMutationBusy && (checked || selected.size < 100))
                        Artwork(candidate, loader, Modifier.size(44.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(candidate.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(candidate.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (state.pickerHasMore) item {
                    TextButton(onClick = actions::loadMorePlaylistSongs, enabled = !state.pickerLoadingMore, modifier = Modifier.fillMaxWidth().testTag("playlist_picker_more")) { Text(stringResource(R.string.ft_load_more)) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ft_listening_selected_count, selected.size), Modifier.weight(1f))
                TextButton(onClick = onDismiss, enabled = !state.libraryMutationBusy) { Text(stringResource(R.string.ft_library_cancel)) }
                Button(onClick = { onSubmit(selected.toList()) }, enabled = selected.isNotEmpty() && !state.libraryMutationBusy && !state.libraryMutationConflict,
                    modifier = Modifier.testTag("playlist_picker_submit")) { Text(stringResource(R.string.ft_listening_add_songs)) }
            }
        }
    }
}
