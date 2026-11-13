package com.flaretune.client.ui

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flaretune.client.R
import com.flaretune.client.model.ClientNotice
import com.flaretune.client.model.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private enum class PlayerPanel { NONE, PLAYER, QUEUE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientApp(
    state: ClientUiState,
    actions: ClientActions,
    imageLoader: ImageLoader? = null,
    positionMsFlow: StateFlow<Long>? = null,
) {
    CompositionLocalProvider(LocalPlaybackPositionMs provides positionMsFlow) {
        ClientAppContent(
            state = state,
            actions = actions,
            imageLoader = imageLoader,
        )
    }
}

@Composable
private fun ClientAppContent(
    state: ClientUiState,
    actions: ClientActions,
    imageLoader: ImageLoader?,
) {
    var panel by rememberSaveable { mutableStateOf(PlayerPanel.NONE) }
    var queueReturnPanel by rememberSaveable { mutableStateOf(PlayerPanel.NONE) }
    var panelIdentity by rememberSaveable { mutableStateOf<String?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var feedbackNonce by remember { mutableIntStateOf(0) }
    LaunchedEffect(feedbackNonce) {
        if (feedbackNonce > 0) {
            delay(1800)
            feedbackMessage = null
        }
    }
    val showFeedback: (String) -> Unit = { msg ->
        if (msg.isNotBlank()) {
            feedbackMessage = msg
            feedbackNonce++
        }
    }
    val systemDark = isSystemInDarkTheme()
    val useLightBarIcons = systemDark || (state.screen == ClientScreen.LIBRARY && panel != PlayerPanel.NONE)
    val hostView = LocalView.current
    LaunchedEffect(useLightBarIcons, hostView) {
        val activity = hostView.context as? ComponentActivity ?: return@LaunchedEffect
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

    LaunchedEffect(state.screen, state.navigationIdentity, state.playback.current?.id, state.playbackReady, state.queueRestoring) {
        if (state.screen == ClientScreen.RESTORING) return@LaunchedEffect
        if (state.screen != ClientScreen.LIBRARY) {
            panel = PlayerPanel.NONE; queueReturnPanel = PlayerPanel.NONE; panelIdentity = null
        } else {
            if (panelIdentity != null && panelIdentity != state.navigationIdentity) {
                panel = PlayerPanel.NONE
                queueReturnPanel = PlayerPanel.NONE
            }
            panelIdentity = state.navigationIdentity
            if (state.playbackReady && !state.queueRestoring && state.playback.current == null &&
                (panel == PlayerPanel.PLAYER || (panel == PlayerPanel.QUEUE && queueReturnPanel == PlayerPanel.PLAYER))
            ) {
                panel = PlayerPanel.NONE
                queueReturnPanel = PlayerPanel.NONE
            }
        }
    }
    ListeningInteractionHost(
        state = state,
        actions = actions,
        imageLoader = imageLoader,
        onNavigateToContent = { panel = PlayerPanel.NONE },
        onFeedback = showFeedback,
    ) {
    val playerOverlayVisible = state.screen == ClientScreen.LIBRARY &&
        state.playback.current != null &&
        (panel == PlayerPanel.PLAYER || (panel == PlayerPanel.QUEUE && queueReturnPanel == PlayerPanel.PLAYER))
    val shellDepthProgress by animateFloatAsState(
        targetValue = if (playerOverlayVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 360f),
        label = "shell_depth_progress",
    )
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (state.screen) {
                ClientScreen.RESTORING -> RestoringScreen()
                ClientScreen.CONNECT -> ConnectionScreen(state, actions)
                ClientScreen.CHANGE_PASSWORD -> PasswordScreen(state, actions)
                ClientScreen.LIBRARY -> Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            val depth = shellDepthProgress
                            if (depth > 0.001f) {
                                val scale = 1f - 0.036f * depth
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - 0.14f * depth
                            }
                        }
                        .then(
                            if (panel != PlayerPanel.NONE) Modifier.clearAndSetSemantics {} else Modifier,
                        ),
                ) {
                    BackHandler(enabled = panel == PlayerPanel.NONE && state.selectedArtist == null && state.selectedAlbum == null && state.selectedPlaylist == null && state.destination != MainDestination.HOME) { actions.navigate(MainDestination.HOME) }
                    BackHandler(enabled = panel == PlayerPanel.NONE && state.selectedPlaylist != null) { actions.closePlaylist() }
                    BackHandler(enabled = panel == PlayerPanel.NONE && state.selectedAlbum != null) { actions.closeAlbum() }
                    BackHandler(enabled = panel == PlayerPanel.NONE && state.selectedArtist != null) { actions.closeArtist() }
                    key(state.navigationIdentity) { ListeningShell(
                        state = state,
                        actions = actions,
                        imageLoader = imageLoader,
                        openPlayer = { panel = PlayerPanel.PLAYER },
                        openQueue = {
                            queueReturnPanel = PlayerPanel.NONE
                            panel = PlayerPanel.QUEUE
                        },
                        miniPlayerActive = panel == PlayerPanel.NONE,
                    ) }
                }
            }
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
                    visible = panel == PlayerPanel.PLAYER || (panel == PlayerPanel.QUEUE && queueReturnPanel == PlayerPanel.PLAYER),
                    enter = slideInVertically(spring(dampingRatio = 0.88f, stiffness = 360f)) { it } + fadeIn(tween(180)),
                    exit = if (skipPlayerExitAnim) {
                        ExitTransition.None
                    } else {
                        slideOutVertically(spring(dampingRatio = 0.92f, stiffness = 380f)) { it } + fadeOut(tween(160))
                    },
                ) {
                    NowPlayingScreen(
                        state = state.playback,
                        lyricState = state.lyricState,
                        translationEnabled = state.translationEnabled,
                        translationNotice = state.translationNotice,
                        actions = actions,
                        imageLoader = imageLoader,
                        onQueue = {
                            queueReturnPanel = PlayerPanel.PLAYER
                            panel = PlayerPanel.QUEUE
                        },
                        onClose = {
                            skipPlayerExitAnim = true
                            panel = PlayerPanel.NONE
                        },
                    )
                }
            }
            if (state.screen == ClientScreen.LIBRARY && panel == PlayerPanel.QUEUE) {
                FlareModalSheet(onDismissRequest = { panel = queueReturnPanel }) {
                    val closeQueue: () -> Unit = { panel = queueReturnPanel }
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
            AnimatedVisibility(
                visible = feedbackMessage != null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp, start = 24.dp, end = 24.dp)
                    .zIndex(20f),
                enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { -it / 2 },
                exit = fadeOut(tween(220)) + slideOutVertically(tween(200)) { -it / 2 },
            ) {
                feedbackMessage?.let { text ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag("action_feedback_toast"),
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
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
    horizontalPadding: Dp = 16.dp,
    onClick: () -> Unit,
) {
    val interaction = LocalListeningInteractions.current
    val status = stringResource(if (buffering) R.string.ft_buffering else if (playing) R.string.ft_playing else R.string.ft_paused)
    Row(
        modifier.fillMaxWidth().heightIn(min = 78.dp)
            .flarePressScale(enabled = clickEnabled, pressedScale = 0.985f)
            .then(if (current) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) else Modifier)
            .clickable(enabled = clickEnabled, onClick = onClick)
            .then(if (current) Modifier.semantics { stateDescription = status } else Modifier)
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Artwork(song, imageLoader, Modifier.size(56.dp), radius = 10.dp, placeholderSize = 26.dp, decodeSize = 56.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    lineHeight = 20.sp,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist.ifBlank { stringResource(R.string.ft_unknown_artist) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (current) Text(
                status,
                style = MaterialTheme.typography.labelMedium,
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
    decodeSize: Dp = Dp.Unspecified,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ClientIcons.Music, null, Modifier.size(placeholderSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (loader != null && !song.coverUrl.isNullOrBlank()) {
            val targetPx = if (decodeSize.isSpecified) {
                with(density) { decodeSize.roundToPx().coerceAtLeast(1) }
            } else {
                0
            }
            val request = remember(context, song.coverUrl, targetPx) {
                ImageRequest.Builder(context)
                    .data(song.coverUrl)
                    .apply {
                        if (targetPx > 0) size(targetPx, targetPx)
                    }
                    .build()
            }
            AsyncImage(
                model = request, imageLoader = loader, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private data class QueueEntry(val key: String, val song: Song, val sourceIndex: Int)

private fun buildKeyedQueueEntries(queue: List<Song>): List<QueueEntry> {
    val counts = mutableMapOf<String, Int>()
    return queue.mapIndexed { idx, song ->
        val occ = counts.getOrElse(song.id) { 0 }
        counts[song.id] = occ + 1
        QueueEntry("${song.id}#$occ", song, idx)
    }
}

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
    val density = LocalDensity.current
    val windowHeight = with(density) {
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp()
    }
    val sheetHeight = (windowHeight * 0.74f).coerceAtLeast(320.dp)
    val entries = remember {
        mutableStateListOf<QueueEntry>().apply {
            addAll(buildKeyedQueueEntries(state.queue))
        }
    }
    val listState = rememberLazyListState()
    val absorbBottomOverscroll = rememberAbsorbBottomOverscroll()

    var draggedKey by remember { mutableStateOf<String?>(null) }
    var optimisticIds by remember { mutableStateOf<List<String>?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragPointerInList by remember { mutableFloatStateOf(0f) }
    var dragFingerOffsetFromCenter by remember { mutableFloatStateOf(0f) }
    var listTopInWindow by remember { mutableFloatStateOf(0f) }
    var listHeightPx by remember { mutableFloatStateOf(0f) }

    // Optimistic sync: preserve dragged/reordered order in-place without recreating LazyColumn items or images.
    LaunchedEffect(state.queue, draggedKey, optimisticIds) {
        if (draggedKey != null) return@LaunchedEffect
        val expectedIds = optimisticIds
        val incomingIds = state.queue.map { it.id }
        if (expectedIds != null) {
            if (incomingIds == expectedIds) {
                optimisticIds = null
                return@LaunchedEffect
            }
            // Wait briefly for ViewModel / player store commit before reconciling a rejected/external update.
            delay(650L)
            optimisticIds = null
        }
        val currentIds = entries.map { it.song.id }
        if (currentIds != incomingIds) {
            entries.clear()
            entries.addAll(buildKeyedQueueEntries(state.queue))
        }
    }

    fun reorderDraggedTowardPointer(key: String, pointerY: Float) {
        val currentIdx = entries.indexOfFirst { it.key == key }
        if (currentIdx < 0) return
        val draggedCenter = pointerY - dragFingerOffsetFromCenter
        val visible = listState.layoutInfo.visibleItemsInfo
        if (currentIdx < entries.lastIndex) {
            val nextEntry = entries[currentIdx + 1]
            val nextInfo = visible.firstOrNull { it.key == nextEntry.key }
            if (nextInfo != null && draggedCenter >= nextInfo.offset + nextInfo.size * 0.35f) {
                entries.add(currentIdx + 1, entries.removeAt(currentIdx))
                return
            }
        }
        if (currentIdx > 0) {
            val prevEntry = entries[currentIdx - 1]
            val prevInfo = visible.firstOrNull { it.key == prevEntry.key }
            if (prevInfo != null && draggedCenter <= prevInfo.offset + prevInfo.size * 0.65f) {
                entries.add(currentIdx - 1, entries.removeAt(currentIdx))
            }
        }
    }

    // Smooth auto-scroll when holding dragged queue item near top or bottom edge.
    LaunchedEffect(draggedKey) {
        val key = draggedKey ?: return@LaunchedEffect
        val edgeZonePx = with(density) { 56.dp.toPx() }
        val maxStepPx = with(density) { 14.dp.toPx() }
        var lastFrameNanos = withFrameNanos { it }
        while (isActive && draggedKey == key) {
            val nowNanos = withFrameNanos { it }
            val frameScale = ((nowNanos - lastFrameNanos).coerceIn(1_000_000L, 48_000_000L)) / 16_666_667f
            lastFrameNanos = nowNanos
            val height = listHeightPx
            if (height > edgeZonePx * 2f) {
                val delta = when {
                    dragPointerInList < edgeZonePx -> {
                        val factor = ((edgeZonePx - dragPointerInList) / edgeZonePx).coerceIn(0f, 1f)
                        -maxStepPx * factor * frameScale
                    }
                    dragPointerInList > height - edgeZonePx -> {
                        val factor = ((dragPointerInList - (height - edgeZonePx)) / edgeZonePx).coerceIn(0f, 1f)
                        maxStepPx * factor * frameScale
                    }
                    else -> 0f
                }
                if (delta != 0f) {
                    listState.scrollBy(delta)
                    reorderDraggedTowardPointer(key, dragPointerInList)
                }
            }
        }
    }

    Column(
        Modifier.fillMaxWidth()
            .then(if (entries.isEmpty()) Modifier.heightIn(max = sheetHeight) else Modifier.height(sheetHeight))
            .navigationBarsPadding()
            .testTag("queue_panel"),
    ) {
        FlareSheetHeader(
            title = stringResource(R.string.ft_queue),
            subtitle = pluralStringResource(R.plurals.ft_track_count, entries.size, entries.size),
            onClose = close,
            trailingActions = {
                if (entries.isNotEmpty() && listeningInteractions != null) {
                    ActionIcon(
                        ClientIcons.Trash,
                        stringResource(R.string.ft_listening_clear_queue),
                        listeningInteractions.clearQueue,
                        modifier = Modifier.testTag("queue_clear"),
                    )
                }
            },
        )
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
                userScrollEnabled = draggedKey == null,
                modifier = Modifier.fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(absorbBottomOverscroll)
                    .onGloballyPositioned { coords ->
                        listTopInWindow = coords.positionInWindow().y
                        listHeightPx = coords.size.height.toFloat()
                    }
                    .testTag("queue_list"),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                itemsIndexed(entries, key = { _, item -> item.key }) { index, entry ->
                    val isDragging = draggedKey == entry.key
                    val dragTranslationY = if (isDragging) {
                        val slotInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == entry.key }
                        if (slotInfo != null) {
                            val slotCenter = slotInfo.offset + slotInfo.size / 2f
                            dragPointerInList - slotCenter - dragFingerOffsetFromCenter
                        } else 0f
                    } else 0f

                    val itemModifier = if (isDragging) {
                        Modifier.zIndex(10f).graphicsLayer {
                            translationY = dragTranslationY
                            scaleX = 1.015f
                            scaleY = 1.015f
                        }
                    } else {
                        Modifier.animateItem().zIndex(0f)
                    }

                    SwipeToRemoveQueueRow(
                        entryKey = entry.key,
                        gesturesEnabled = draggedKey == null,
                        modifier = itemModifier,
                        onRemove = {
                            val currentIdx = entries.indexOfFirst { it.key == entry.key }
                            if (currentIdx >= 0) {
                                entries.removeAt(currentIdx)
                                optimisticIds = entries.map { it.song.id }
                                onRemove?.invoke(currentIdx)
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
                            dragTag = "queue_drag_" + index,
                            modifier = Modifier.testTag("queue_item_" + index),
                            onClick = { if (draggedKey == null) select(index) },
                            onVerticalDragStart = { startWindowY ->
                                val fingerY = startWindowY - listTopInWindow
                                val startIdx = entries.indexOfFirst { it.key == entry.key }
                                val slotInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == entry.key }
                                draggedKey = entry.key
                                dragStartIndex = startIdx
                                dragPointerInList = fingerY
                                dragFingerOffsetFromCenter = if (slotInfo != null) {
                                    fingerY - (slotInfo.offset + slotInfo.size / 2f)
                                } else 0f
                            },
                            onVerticalDrag = { pointerWindowY ->
                                val fingerY = (pointerWindowY - listTopInWindow).coerceIn(0f, listHeightPx.coerceAtLeast(1f))
                                dragPointerInList = fingerY
                                reorderDraggedTowardPointer(entry.key, fingerY)
                            },
                            onVerticalDragEnd = {
                                val finalIndex = entries.indexOfFirst { it.key == entry.key }
                                val startIndex = dragStartIndex
                                draggedKey = null
                                dragStartIndex = -1
                                dragFingerOffsetFromCenter = 0f
                                if (startIndex >= 0 && finalIndex >= 0 && startIndex != finalIndex) {
                                    optimisticIds = entries.map { it.song.id }
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
private fun SwipeToRemoveQueueRow(
    entryKey: String,
    gesturesEnabled: Boolean,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val offsetX = remember(entryKey) { Animatable(0f) }
    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    var removing by remember(entryKey) { mutableStateOf(false) }

    val thresholdCapPx = with(density) { 104.dp.toPx() }
    val minThresholdPx = with(density) { 78.dp.toPx() }
    val flingVelocityPx = with(density) { 820.dp.toPx() }
    val thresholdPx = if (rowWidthPx > 0f) {
        (rowWidthPx * 0.25f).coerceIn(minThresholdPx, thresholdCapPx)
    } else thresholdCapPx

    val absOffset = abs(offsetX.value)
    val rawProgress = if (thresholdPx > 0f) absOffset / thresholdPx else 0f
    val clampedProgress = rawProgress.coerceIn(0f, 1f)
    val armed = absOffset >= thresholdPx

    var wasArmed by remember(entryKey) { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed != wasArmed && absOffset > 0f) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        wasArmed = armed
    }

    val baseBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    val dangerRed = Color(0xFFDC2626)
    val targetBg = when {
        absOffset < 2f -> Color.Transparent
        armed -> dangerRed.copy(alpha = 0.90f)
        else -> lerp(baseBg, dangerRed.copy(alpha = 0.68f), clampedProgress)
    }
    val animatedBg by animateColorAsState(targetBg, tween(80), label = "swipe_remove_bg")
    val iconScale by animateFloatAsState(
        targetValue = if (armed) 1.14f else 0.86f + 0.18f * clampedProgress,
        animationSpec = tween(70),
        label = "swipe_icon_scale",
    )

    Box(
        modifier.fillMaxWidth()
            .clipToBounds()
            .onGloballyPositioned { rowWidthPx = it.size.width.toFloat() },
    ) {
        if (absOffset > 1f) {
            val align = if (offsetX.value < 0f) Alignment.CenterEnd else Alignment.CenterStart
            Box(
                Modifier.matchParentSize()
                    .background(animatedBg)
                    .padding(horizontal = 24.dp),
                contentAlignment = align,
            ) {
                AnimatedTrashIcon(
                    progress = clampedProgress,
                    armed = armed,
                    tint = if (armed) Color.White else Color.White.copy(alpha = 0.72f + 0.28f * clampedProgress),
                    modifier = Modifier.graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                )
            }
        }

        val tracker = remember { VelocityTracker() }
        Box(
            Modifier.fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(entryKey, gesturesEnabled, thresholdPx) {
                    if (!gesturesEnabled || removing) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val next = offsetX.value + dragAmount
                            // Gentle rubberband once far past the delete threshold
                            val softLimit = thresholdPx * 1.6f
                            val damped = if (abs(next) > softLimit) {
                                sign(next) * (softLimit + (abs(next) - softLimit) * 0.35f)
                            } else next
                            scope.launch { offsetX.snapTo(damped) }
                        },
                        onDragEnd = {
                            val vx = tracker.calculateVelocity().x
                            val cur = offsetX.value
                            val shouldRemove = abs(cur) >= thresholdPx ||
                                (abs(vx) >= flingVelocityPx && abs(cur) >= thresholdPx * 0.35f && sign(vx) == sign(cur))
                            scope.launch {
                                if (shouldRemove && !removing) {
                                    removing = true
                                    val exitTarget = (if (cur != 0f) sign(cur) else -1f) * (rowWidthPx.takeIf { it > 0f } ?: 1080f)
                                    offsetX.animateTo(exitTarget, tween(160))
                                    onRemove()
                                } else {
                                    offsetX.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                            }
                        },
                    )
                },
        ) {
            content()
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
    dragTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onVerticalDragStart: (Float) -> Unit,
    onVerticalDrag: (Float) -> Unit,
    onVerticalDragEnd: () -> Unit,
) {
    val status = stringResource(if (buffering) R.string.ft_buffering else if (playing) R.string.ft_playing else R.string.ft_paused)
    val rowBg = if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface
    var handleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val latestOnStart by rememberUpdatedState(onVerticalDragStart)
    val latestOnDrag by rememberUpdatedState(onVerticalDrag)
    val latestOnEnd by rememberUpdatedState(onVerticalDragEnd)

    Row(
        modifier.fillMaxWidth()
            .heightIn(min = 72.dp)
            .then(if (dragging) Modifier.shadow(10.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)) else Modifier)
            .background(rowBg)
            .clickable(enabled = !dragging, onClick = onClick)
            .then(if (current) Modifier.semantics { stateDescription = status } else Modifier)
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(song, loader, Modifier.size(48.dp), decodeSize = 48.dp)
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
            Modifier.width(56.dp).height(52.dp)
                .testTag(dragTag)
                .onGloballyPositioned { handleCoords = it }
                .pointerInput(song.id) {
                    var trackedWindowY = 0f
                    detectVerticalDragGestures(
                        onDragStart = { startOffset ->
                            trackedWindowY = handleCoords?.localToWindow(startOffset)?.y ?: return@detectVerticalDragGestures
                            latestOnStart(trackedWindowY)
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            trackedWindowY += dragAmount
                            latestOnDrag(trackedWindowY)
                        },
                        onDragEnd = { latestOnEnd() },
                        onDragCancel = { latestOnEnd() },
                    )
                }
                .semantics { contentDescription = "" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ClientIcons.DragHandle,
                contentDescription = stringResource(R.string.ft_reorder),
                tint = if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
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
