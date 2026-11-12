package com.flaretune.client.ui

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.ImageLoader
import coil.compose.AsyncImage
import com.flaretune.client.R
import com.flaretune.client.model.ClientNotice
import com.flaretune.client.model.Song
import kotlinx.coroutines.launch
import java.util.Locale

private enum class PlayerPanel { NONE, PLAYER, QUEUE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientApp(
    state: ClientUiState,
    actions: ClientActions,
    imageLoader: ImageLoader? = null,
) {
    var panel by rememberSaveable { mutableStateOf(PlayerPanel.NONE) }
    val systemDark = isSystemInDarkTheme()
    val useLightBarIcons = systemDark || (state.screen == ClientScreen.LIBRARY && panel != PlayerPanel.NONE)
    val hostView = LocalView.current
    SideEffect {
        val activity = hostView.context as? ComponentActivity ?: return@SideEffect
        val barStyle = if (useLightBarIcons) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        val window = activity.window
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= 28) {
            val attrs = window.attributes
            if (attrs.layoutInDisplayCutoutMode != WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES) {
                attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = attrs
            }
        }
    }

    LaunchedEffect(state.screen, state.playback.current?.id) {
        if (state.screen != ClientScreen.LIBRARY || state.playback.current == null) panel = PlayerPanel.NONE
    }
    ListeningInteractionHost(state, actions, imageLoader) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (state.screen) {
                ClientScreen.RESTORING -> RestoringScreen()
                ClientScreen.CONNECT -> ConnectionScreen(state, actions)
                ClientScreen.CHANGE_PASSWORD -> PasswordScreen(state, actions)
                ClientScreen.LIBRARY -> Box(
                    Modifier.fillMaxSize().then(
                        if (panel != PlayerPanel.NONE) Modifier.clearAndSetSemantics {} else Modifier,
                    ),
                ) {
                    ListeningShell(
                        state = state,
                        actions = actions,
                        imageLoader = imageLoader,
                        openPlayer = { panel = PlayerPanel.PLAYER },
                    )
                }
            }
            BackHandler(enabled = state.screen == ClientScreen.LIBRARY && panel == PlayerPanel.NONE && state.selectedArtist == null && state.selectedAlbum == null && state.selectedPlaylist == null && state.destination != MainDestination.HOME) { actions.navigate(MainDestination.HOME) }
            BackHandler(enabled = state.screen == ClientScreen.LIBRARY && panel == PlayerPanel.NONE && state.selectedPlaylist != null) { actions.closePlaylist() }
            BackHandler(enabled = state.screen == ClientScreen.LIBRARY && panel == PlayerPanel.NONE && state.selectedAlbum != null) { actions.closeAlbum() }
            BackHandler(enabled = state.screen == ClientScreen.LIBRARY && panel == PlayerPanel.NONE && state.selectedArtist != null) { actions.closeArtist() }
            if (state.screen == ClientScreen.LIBRARY && state.playback.current != null) {
                var skipPlayerExitAnim by remember { mutableStateOf(false) }
                LaunchedEffect(panel) {
                    if (panel != PlayerPanel.NONE) skipPlayerExitAnim = false
                }
                BackHandler(enabled = panel == PlayerPanel.PLAYER) {
                    skipPlayerExitAnim = false
                    panel = PlayerPanel.NONE
                }
                AnimatedVisibility(
                    visible = panel != PlayerPanel.NONE,
                    enter = slideInVertically(tween(250, easing = LinearOutSlowInEasing)) { it } + fadeIn(tween(180)),
                    exit = if (skipPlayerExitAnim) {
                        ExitTransition.None
                    } else {
                        slideOutVertically(tween(300, easing = LinearOutSlowInEasing)) { it } + fadeOut(tween(240))
                    },
                ) {
                    NowPlayingScreen(
                        state = state.playback,
                        lyricState = state.lyricState,
                        translationEnabled = state.translationEnabled,
                        translationNotice = state.translationNotice,
                        actions = actions,
                        imageLoader = imageLoader,
                        onQueue = { panel = PlayerPanel.QUEUE },
                        onClose = {
                            skipPlayerExitAnim = true
                            panel = PlayerPanel.NONE
                        },
                    )
                }
                if (panel == PlayerPanel.QUEUE) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    val scope = rememberCoroutineScope()
                    val closeQueue: () -> Unit = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible && panel == PlayerPanel.QUEUE) panel = PlayerPanel.PLAYER
                        }
                    }
                    ModalBottomSheet(
                        onDismissRequest = { panel = PlayerPanel.PLAYER },
                        sheetState = sheetState,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        QueuePanel(
                            state = state.playback,
                            loader = imageLoader,
                            close = closeQueue,
                            queueNotice = state.queueNotice,
                            onRetryRestore = actions::retryQueueRestore,
                            onRemove = actions::removeQueueItem,
                            onMove = actions::moveQueueItem,
                        ) { index ->
                            actions.selectQueueItem(index)
                            closeQueue()
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun RestoringScreen() {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp).testTag("restoring"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.ft_restoring), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ConnectionScreen(state: ClientUiState, actions: ClientActions) {
    var url by rememberSaveable(state.instanceUrl) { mutableStateOf(state.instanceUrl) }
    var username by rememberSaveable(state.username) { mutableStateOf(state.username) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val canSubmit = url.isNotBlank() && username.isNotBlank() && password.isNotEmpty() && !state.busy
    val submit = {
        if (canSubmit) {
            keyboard?.hide()
            val submitted = password
            password = ""
            actions.login(url.trim(), username.trim(), submitted)
        }
    }
    FormPage {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.ft_connect_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.ft_connect_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        OutlinedTextField(
            value = url, onValueChange = { url = it }, enabled = !state.busy,
            label = { Text(stringResource(R.string.ft_instance_address)) },
            placeholder = { Text("https://music.example.com") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("connect_url"),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = username, onValueChange = { username = it }, enabled = !state.busy,
            label = { Text(stringResource(R.string.ft_username)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("connect_username"),
        )
        Spacer(Modifier.height(12.dp))
        SecretField(
            value = password, onChange = { password = it }, label = stringResource(R.string.ft_password),
            enabled = !state.busy, shown = showPassword, toggle = { showPassword = !showPassword },
            modifier = Modifier.testTag("connect_password"),
            imeAction = ImeAction.Go, keyboardActions = KeyboardActions(onGo = { submit() }),
        )
        state.notice?.let { Notice(it, Modifier.padding(top = 16.dp)) }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = submit, enabled = canSubmit,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("connect_submit"),
        ) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(12.dp))
            }
            Text(stringResource(if (state.busy) R.string.ft_connecting else R.string.ft_connect_action))
        }
    }
}

@Composable
private fun PasswordScreen(state: ClientUiState, actions: ClientActions) {
    var current by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var mismatch by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        if (!state.busy && current.isNotEmpty() && replacement.isNotEmpty() && confirmation.isNotEmpty()) {
            mismatch = replacement != confirmation
            if (!mismatch) {
                keyboard?.hide()
                val old = current
                val new = replacement
                current = ""; replacement = ""; confirmation = ""
                actions.changePassword(old, new)
            }
        }
    }
    FormPage {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.ft_change_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.ft_change_subtitle), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        SecretField(current, { current = it }, stringResource(R.string.ft_current_password), !state.busy, show, { show = !show }, Modifier.testTag("change_current"))
        Spacer(Modifier.height(12.dp))
        SecretField(replacement, { replacement = it; mismatch = false }, stringResource(R.string.ft_new_password), !state.busy, show, { show = !show }, Modifier.testTag("change_new"))
        Spacer(Modifier.height(12.dp))
        SecretField(
            confirmation, { confirmation = it; mismatch = false }, stringResource(R.string.ft_confirm_password),
            !state.busy, show, { show = !show }, Modifier.testTag("change_confirm"),
            imeAction = ImeAction.Done, keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        if (mismatch) {
            Text(
                stringResource(R.string.ft_password_mismatch), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.notice?.let { Notice(it, Modifier.padding(top = 16.dp)) }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = submit,
            enabled = !state.busy && current.isNotEmpty() && replacement.isNotEmpty() && confirmation.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("change_submit"),
        ) {
            if (state.busy) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(12.dp)) }
            Text(stringResource(R.string.ft_change_action))
        }
        TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant), onClick = actions::logout, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
            Text(stringResource(R.string.ft_sign_out))
        }
    }
}

@Composable
private fun FormPage(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            content = content,
        )
    }
}

@Composable
private fun SecretField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    shown: Boolean,
    toggle: () -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, enabled = enabled, label = { Text(label) },
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = keyboardActions,
        trailingIcon = {
            TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant), onClick = toggle, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (shown) R.string.ft_hide_password else R.string.ft_show_password), style = MaterialTheme.typography.labelMedium)
            }
        },
        singleLine = true, modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun SongRow(
    song: Song,
    imageLoader: ImageLoader?,
    current: Boolean,
    playing: Boolean,
    buffering: Boolean = false,
    modifier: Modifier = Modifier,
    clickEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = LocalListeningInteractions.current
    val status = stringResource(if (buffering) R.string.ft_buffering else if (playing) R.string.ft_playing else R.string.ft_paused)
    Row(
        modifier.fillMaxWidth().heightIn(min = 72.dp)
            .clickable(enabled = clickEnabled, onClick = onClick)
            .then(if (current) Modifier.semantics { stateDescription = status } else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(song, imageLoader, Modifier.size(48.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                song.artist.ifBlank { stringResource(R.string.ft_unknown_artist) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (current) Text(
                status, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (song.durationSeconds > 0) {
            Text(
                formatTime((song.durationSeconds * 1000).toLong()),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (interaction != null) ActionIcon(ClientIcons.More, stringResource(R.string.ft_listening_song_actions),
            { interaction.song(song) }, modifier = Modifier.testTag("song_more_" + song.id))
    }
}

@Composable
internal fun Artwork(
    song: Song,
    loader: ImageLoader?,
    modifier: Modifier,
    radius: Dp = 8.dp,
    placeholderSize: Dp = 28.dp,
) {
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ClientIcons.Music, null, Modifier.size(placeholderSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (loader != null && !song.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = song.coverUrl, imageLoader = loader, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private data class QueueEntry(val key: String, val song: Song, val sourceIndex: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueuePanel(
    state: PlaybackSnapshot,
    loader: ImageLoader?,
    close: () -> Unit,
    queueNotice: ClientNotice? = null,
    onRetryRestore: (() -> Unit)? = null,
    onRemove: ((Int) -> Unit)? = null,
    onMove: ((Int, Int) -> Unit)? = null,
    select: (Int) -> Unit,
) {
    val listeningInteractions = LocalListeningInteractions.current
    val windowHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp()
    }
    val sheetHeight = (windowHeight * 0.74f).coerceAtLeast(320.dp)
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val entries = remember(state.queue) {
        mutableStateListOf<QueueEntry>().apply {
            state.queue.forEachIndexed { idx, song -> add(QueueEntry("$idx:${song.id}", song, idx)) }
        }
    }
    val listState = rememberLazyListState()

    // Prevent bottom overscroll deltas from leaking into ModalBottomSheet and causing sheet jitter.
    val absorbBottomOverscroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (available.y < 0f) available.copy(x = 0f) else Offset.Zero

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                if (available.y < 0f) available.copy(x = 0f) else Velocity.Zero
        }
    }

    Column(
        Modifier.fillMaxWidth()
            .then(if (entries.isEmpty()) Modifier.heightIn(max = sheetHeight) else Modifier.height(sheetHeight))
            .navigationBarsPadding()
            .testTag("queue_panel"),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ft_queue), style = MaterialTheme.typography.titleLarge)
                Text(
                    pluralStringResource(R.plurals.ft_track_count, entries.size, entries.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entries.isNotEmpty() && listeningInteractions != null) ActionIcon(ClientIcons.Trash,
                stringResource(R.string.ft_listening_clear_queue), listeningInteractions.clearQueue, modifier = Modifier.testTag("queue_clear"))
            ActionIcon(ClientIcons.Close, stringResource(R.string.ft_close), close)
        }
        queueNotice?.let { notice ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Notice(notice, Modifier.weight(1f))
                if (entries.isEmpty() && onRetryRestore != null) {
                    TextButton(onClick = onRetryRestore) { Text(stringResource(R.string.ft_retry)) }
                }
            }
        }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.ft_queue_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f).nestedScroll(absorbBottomOverscroll).testTag("queue_list"),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                itemsIndexed(entries, key = { _, item -> item.key }) { index, entry ->
                    val isDragging = draggedKey == entry.key
                    val dismissState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                            val currentIdx = entries.indexOfFirst { it.key == entry.key }
                            if (currentIdx >= 0) {
                                entries.removeAt(currentIdx)
                                onRemove?.invoke(currentIdx)
                            }
                        }
                    }

                    SwipeToDismissBox(
                        state = dismissState,
                        gesturesEnabled = draggedKey == null,
                        modifier = Modifier
                            .animateItem()
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer { if (isDragging) translationY = dragOffsetY },
                        backgroundContent = {
                            val direction = dismissState.dismissDirection
                            val align = when (direction) {
                                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                SwipeToDismissBoxValue.Settled -> Alignment.CenterEnd
                            }
                            val activeSwipe = direction != SwipeToDismissBoxValue.Settled
                            Box(
                                Modifier.fillMaxSize()
                                    .background(
                                        if (activeSwipe) Color(0xFF991B1B).copy(alpha = 0.28f)
                                        else Color.Transparent,
                                    )
                                    .padding(horizontal = 24.dp),
                                contentAlignment = align,
                            ) {
                                if (activeSwipe) {
                                    Icon(
                                        ClientIcons.Trash,
                                        contentDescription = stringResource(R.string.ft_remove),
                                        tint = Color(0xFFFCA5A5),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        },
                    ) {
                        QueueTrackRow(
                            song = entry.song,
                            loader = loader,
                            current = index == state.currentIndex,
                            playing = state.playing,
                            buffering = state.buffering,
                            dragging = isDragging,
                            modifier = Modifier.testTag("queue_item_" + index),
                            onClick = { select(index) },
                            onDragStart = {
                                draggedKey = entry.key
                                dragStartIndex = entries.indexOfFirst { it.key == entry.key }
                                dragOffsetY = 0f
                            },
                            onDragDelta = { deltaY ->
                                dragOffsetY += deltaY
                                val currentIdx = entries.indexOfFirst { it.key == entry.key }
                                if (currentIdx >= 0) {
                                    val visibleInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == entry.key }
                                    val stepPx = (visibleInfo?.size ?: 180).toFloat() * 0.72f
                                    if (dragOffsetY > stepPx && currentIdx < entries.lastIndex) {
                                        entries.add(currentIdx + 1, entries.removeAt(currentIdx))
                                        dragOffsetY -= stepPx
                                    } else if (dragOffsetY < -stepPx && currentIdx > 0) {
                                        entries.add(currentIdx - 1, entries.removeAt(currentIdx))
                                        dragOffsetY += stepPx
                                    }
                                }
                            },
                            onDragEnd = {
                                val finalIndex = entries.indexOfFirst { it.key == entry.key }
                                val startIndex = dragStartIndex
                                draggedKey = null
                                dragStartIndex = -1
                                dragOffsetY = 0f
                                if (startIndex >= 0 && finalIndex >= 0 && startIndex != finalIndex) {
                                    onMove?.invoke(startIndex, finalIndex)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    song: Song,
    loader: ImageLoader?,
    current: Boolean,
    playing: Boolean,
    buffering: Boolean,
    dragging: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val status = stringResource(if (buffering) R.string.ft_buffering else if (playing) R.string.ft_playing else R.string.ft_paused)
    val rowBg = if (dragging) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface
    Row(
        modifier.fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(rowBg)
            .clickable(onClick = onClick)
            .then(if (current) Modifier.semantics { stateDescription = status } else Modifier)
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(song, loader, Modifier.size(48.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            val meta = buildString {
                append(song.artist.ifBlank { stringResource(R.string.ft_unknown_artist) })
                if (song.durationSeconds > 0) {
                    append(" · ")
                    append(formatTime((song.durationSeconds * 1000).toLong()))
                }
            }
            Text(
                meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (current) {
                Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        Box(
            Modifier.size(48.dp)
                .pointerInput(song.id) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDragDelta(dragAmount.y)
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    )
                }
                .semantics { contentDescription = "" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ClientIcons.DragHandle,
                contentDescription = stringResource(R.string.ft_reorder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
internal fun ActionIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
        Icon(
            icon, contentDescription = description, modifier = Modifier.size(24.dp),
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = .32f)
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
internal fun Notice(notice: ClientNotice, modifier: Modifier = Modifier) {
    val resource = when (notice) {
        ClientNotice.INVALID_URL -> R.string.ft_notice_invalid_url
        ClientNotice.NOT_READY -> R.string.ft_notice_not_ready
        ClientNotice.NETWORK -> R.string.ft_notice_network
        ClientNotice.INVALID_LOGIN -> R.string.ft_notice_invalid_login
        ClientNotice.SESSION_EXPIRED -> R.string.ft_notice_session_expired
        ClientNotice.PASSWORD_CHANGED -> R.string.ft_notice_password_changed
        ClientNotice.REQUEST_FAILED -> R.string.ft_notice_request_failed
        ClientNotice.RATE_LIMITED -> R.string.ft_notice_rate_limited
        ClientNotice.INVALID_RESPONSE -> R.string.ft_notice_invalid_response
        ClientNotice.STORAGE_FAILED -> R.string.ft_notice_storage_failed
        ClientNotice.PAGE_LIMIT -> R.string.ft_notice_page_limit
        ClientNotice.PLAYBACK_FAILED -> R.string.ft_playback_error
        ClientNotice.SOME_SOURCES_UNAVAILABLE -> R.string.ft_notice_partial_sources
        ClientNotice.FORBIDDEN -> R.string.ft_notice_forbidden
        ClientNotice.PLAYLIST_LIMIT -> R.string.ft_listening_playlist_limit
        ClientNotice.PLAYLIST_SONG_LIMIT -> R.string.ft_listening_playlist_song_limit
        ClientNotice.IMAGE_UPLOAD_LIMIT -> R.string.ft_listening_image_upload_limit
        ClientNotice.IMAGE_TOO_LARGE -> R.string.ft_listening_image_too_large
    }
    val informational = notice in setOf(ClientNotice.PASSWORD_CHANGED, ClientNotice.PAGE_LIMIT, ClientNotice.SOME_SOURCES_UNAVAILABLE)
    Text(
        stringResource(resource),
        style = MaterialTheme.typography.bodyMedium,
        color = if (informational) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("notice_" + notice.name),
    )
}

internal fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
    else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}
