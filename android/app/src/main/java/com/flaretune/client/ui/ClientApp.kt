package com.flaretune.client.ui

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.disabled
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
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flaretune.client.R
import com.flaretune.client.data.InstanceAddress
import com.flaretune.client.model.ClientNotice
import com.flaretune.client.model.Song
import com.flaretune.client.playback.SleepTimerState
import com.flaretune.client.ui.theme.FlareTokens
import com.flaretune.client.ui.theme.isFlareDarkTheme
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
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
    sleepTimerFlow: StateFlow<SleepTimerState>? = null,
    playColdOpenSplash: Boolean = false,
) {
    CompositionLocalProvider(
        LocalPlaybackPositionMs provides positionMsFlow,
        LocalSleepTimer provides sleepTimerFlow,
    ) {
        ClientAppContent(
            state = state,
            actions = actions,
            imageLoader = imageLoader,
            playColdOpenSplash = playColdOpenSplash,
        )
    }
}

@Composable
private fun ClientAppContent(
    state: ClientUiState,
    actions: ClientActions,
    imageLoader: ImageLoader?,
    playColdOpenSplash: Boolean,
) {
    val avatarRegistry = rememberAvatarAnchorRegistry()
    val playerRegistry = rememberPlayerTransitionRegistry()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var splashNonceShown by rememberSaveable { mutableIntStateOf(state.splashNonce) }
    var splashDone by rememberSaveable { mutableStateOf(!playColdOpenSplash) }
    val splashVisible = !splashDone || state.splashNonce != splashNonceShown
    var lastObservedScreen by remember { mutableStateOf(state.screen) }
    var activeRelayBounds by remember { mutableStateOf<Rect?>(null) }
    var activeRelayInitial by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, focusManager, keyboardController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.splashNonce) {
        if (state.splashNonce != splashNonceShown) {
            splashDone = false
            activeRelayBounds = null
        }
    }
    LaunchedEffect(state.screen, state.accountName, state.username) {
        val prev = lastObservedScreen
        lastObservedScreen = state.screen
        if (prev == ClientScreen.CONNECT && state.screen == ClientScreen.LIBRARY) {
            val fromBounds = avatarRegistry.lastLoginAvatarBounds
            if (fromBounds != null && !splashVisible) {
                val resolvedInitial = state.accountName
                    .ifBlank { state.username.ifBlank { avatarRegistry.lastLoginInitial } }
                    .trim().take(1).uppercase(Locale.ROOT)
                activeRelayInitial = resolvedInitial
                activeRelayBounds = fromBounds
            }
        } else if (state.screen != ClientScreen.LIBRARY) {
            activeRelayBounds = null
        }
    }
    var panel by rememberSaveable { mutableStateOf(PlayerPanel.NONE) }
    var queueReturnPanel by rememberSaveable { mutableStateOf(PlayerPanel.NONE) }
    var panelIdentity by rememberSaveable { mutableStateOf<String?>(null) }
    var playerMobileLyricsMode by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(panel, state.screen) {
        if (panel != PlayerPanel.NONE || state.screen == ClientScreen.RESTORING) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }
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
    var lastHandledFeedbackId by rememberSaveable { mutableLongStateOf(0L) }
    val pendingPlaybackFeedback = state.playbackFeedback
    val pendingFeedbackMessage = pendingPlaybackFeedback?.let { event ->
        when (event.kind) {
            PlaybackFeedbackKind.PLAYED_SINGLE_NEXT ->
                stringResource(R.string.ft_feedback_play_single_next)
            PlaybackFeedbackKind.PLAYED_BATCH_NEXT ->
                stringResource(R.string.ft_feedback_play_batch_next)
            PlaybackFeedbackKind.ENQUEUED_BATCH ->
                stringResource(R.string.ft_feedback_enqueued_batch)
            PlaybackFeedbackKind.FAVORITED_BATCH ->
                stringResource(R.string.ft_feedback_favorited_batch)
            PlaybackFeedbackKind.QUEUE_RESTORED ->
                stringResource(R.string.ft_history_queue_restored)
            PlaybackFeedbackKind.QUEUE_RESTORED_PARTIAL ->
                pluralStringResource(R.plurals.ft_history_queue_restored_partial, event.omittedCount, event.omittedCount)
            PlaybackFeedbackKind.TRACK_UNAVAILABLE ->
                stringResource(R.string.ft_feedback_track_unavailable)
            PlaybackFeedbackKind.QUEUE_RESTORE_UNAVAILABLE ->
                stringResource(R.string.ft_feedback_queue_restore_unavailable)
        }
    }
    LaunchedEffect(pendingPlaybackFeedback?.id) {
        val event = pendingPlaybackFeedback ?: return@LaunchedEffect
        if (event.id == lastHandledFeedbackId) return@LaunchedEffect
        lastHandledFeedbackId = event.id
        pendingFeedbackMessage?.let(showFeedback)
        actions.dismissPlaybackFeedback(event.id)
    }
    val playerOverlayVisible = state.screen == ClientScreen.LIBRARY &&
        state.playback.current != null &&
        (panel == PlayerPanel.PLAYER || (panel == PlayerPanel.QUEUE && queueReturnPanel == PlayerPanel.PLAYER))
    val prewarmContext = LocalContext.current
    val prewarmCoverUrl = if (state.screen == ClientScreen.LIBRARY) state.playback.current?.coverUrl else null
    LaunchedEffect(prewarmCoverUrl, imageLoader) {
        prewarmClassicPlayerBackdrop(prewarmContext, prewarmCoverUrl, imageLoader)
    }
    val initialConfigWidthDp = LocalConfiguration.current.screenWidthDp.dp
    var rootWidthDp by remember { mutableStateOf(initialConfigWidthDp) }
    val isWideMode = rootWidthDp >= 840.dp
    val playerExpandProgress = remember { Animatable(if (playerOverlayVisible) 1f else 0f) }
    val expandProgressLambda = remember(playerExpandProgress) { { playerExpandProgress.value } }
    var playerDragging by remember { mutableStateOf(false) }
    val activeSettleJob = remember { arrayOfNulls<Job>(1) }
    val dragDrivenTarget = remember { floatArrayOf(Float.NaN) }
    val minTravelPx = with(density) { 260.dp.toPx() }
    fun computeMiniBarTopPx(): Float {
        val rootH = playerRegistry.rootSize.height.toFloat()
        return (playerRegistry.miniBarBounds?.top
            ?: (rootH - with(density) { FlareTokens.CompactMiniPlayerHeight.toPx() }))
            .coerceAtLeast(1f)
    }
    fun computeTravelDistancePx(): Float {
        val rootH = playerRegistry.rootSize.height.toFloat()
        return computeMiniBarTopPx().coerceAtLeast(maxOf(rootH * 0.45f, minTravelPx))
    }
    val latestOverlayVisible by rememberUpdatedState(playerOverlayVisible)
    val latestWideMode by rememberUpdatedState(isWideMode)
    val startSettleAnimation = remember(playerExpandProgress, coroutineScope) {
        { target: Float, initialVelocity: Float ->
            activeSettleJob[0]?.cancel()
            activeSettleJob[0] = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                val remaining = abs(playerExpandProgress.value - target)
                if (remaining > 0.001f) {
                    if (latestWideMode) {
                        playerExpandProgress.animateTo(target, WidePlayerSlideSpring, initialVelocity = initialVelocity)
                    } else {
                        playerExpandProgress.animateTo(target, CompactPlayerSettleSpring, initialVelocity = initialVelocity)
                    }
                } else {
                    playerExpandProgress.snapTo(target)
                }
            }
            Unit
        }
    }
    LaunchedEffect(playerOverlayVisible, state.screen, state.playback.current?.id) {
        if (state.screen != ClientScreen.LIBRARY || state.playback.current == null) {
            playerDragging = false
            activeSettleJob[0]?.cancel()
            activeSettleJob[0] = null
            dragDrivenTarget[0] = Float.NaN
            playerExpandProgress.snapTo(0f)
            return@LaunchedEffect
        }
        if (playerDragging) return@LaunchedEffect
        val target = if (playerOverlayVisible) 1f else 0f
        if (!dragDrivenTarget[0].isNaN() && abs(dragDrivenTarget[0] - target) < 0.01f) {
            dragDrivenTarget[0] = Float.NaN
            return@LaunchedEffect
        }
        dragDrivenTarget[0] = Float.NaN
        startSettleAnimation(target, 0f)
    }
    val onExpandDragStart: () -> Unit = remember(playerExpandProgress, coroutineScope) {
        {
            playerDragging = true
            dragDrivenTarget[0] = Float.NaN
            activeSettleJob[0]?.cancel()
            activeSettleJob[0] = null
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                playerExpandProgress.stop()
            }
            Unit
        }
    }
    val onExpandDragDelta: (Float) -> Float = remember(playerExpandProgress, coroutineScope, playerRegistry, density) {
        { deltaY ->
            val travel = computeTravelDistancePx()
            val current = playerExpandProgress.value
            val next = (current - deltaY / travel).coerceIn(0f, 1f)
            val consumedDeltaY = -(next - current) * travel
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                playerExpandProgress.snapTo(next)
            }
            consumedDeltaY
        }
    }
    val onExpandDragEnd: (Float) -> Unit = remember(playerExpandProgress, playerRegistry, density, startSettleAnimation) {
        { velocityY ->
            playerDragging = false
            val travel = computeTravelDistancePx()
            val p = playerExpandProgress.value
            val expandFlingVelocityPx = with(density) { 220.dp.toPx() }
            val collapseFlingVelocityPx = with(density) { 220.dp.toPx() }
            val threshold = 0.5f
            val shouldExpand = when {
                velocityY <= -expandFlingVelocityPx -> true
                velocityY >= collapseFlingVelocityPx -> false
                else -> p >= threshold
            }
            val initialVelProgress = (-velocityY / travel).coerceIn(-6f, 6f)
            val wasOverlayVisible = latestOverlayVisible
            val target = if (shouldExpand) 1f else 0f
            dragDrivenTarget[0] = if (shouldExpand != wasOverlayVisible) target else Float.NaN
            startSettleAnimation(target, initialVelProgress)
            if (shouldExpand != wasOverlayVisible) {
                panel = if (shouldExpand) PlayerPanel.PLAYER else PlayerPanel.NONE
                queueReturnPanel = PlayerPanel.NONE
            }
        }
    }

    val systemDark = isFlareDarkTheme()
    val useLightBarIcons = systemDark ||
        playerOverlayVisible ||
        (state.screen == ClientScreen.LIBRARY && panel == PlayerPanel.QUEUE && queueReturnPanel == PlayerPanel.PLAYER)
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
        WindowCompat.getInsetsController(window, hostView).apply {
            isAppearanceLightStatusBars = !useLightBarIcons
            isAppearanceLightNavigationBars = !useLightBarIcons
        }
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
    ProvideAvatarAnchorRegistry(avatarRegistry) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier.fillMaxSize().onGloballyPositioned {
                val origin = it.positionInRoot()
                avatarRegistry.rootOrigin = origin
                playerRegistry.updateRootLayout(origin, it.size)
                val measuredWidthDp = with(density) { it.size.width.toDp() }
                if (abs(measuredWidthDp.value - rootWidthDp.value) > 0.5f) {
                    rootWidthDp = measuredWidthDp
                }
            },
        ) {
            when (state.screen) {
                ClientScreen.RESTORING -> RestoringScreen()
                ClientScreen.CONNECT -> ConnectionScreen(state, actions)
                ClientScreen.CHANGE_PASSWORD -> PasswordScreen(state, actions)
                ClientScreen.LIBRARY -> Box(
                    Modifier.fillMaxSize()
                        .focusProperties { canFocus = panel == PlayerPanel.NONE && !splashVisible }
                        .then(
                            if (panel != PlayerPanel.NONE) Modifier.clearAndSetSemantics {} else Modifier,
                        ),
                ) {
                    val miniPlayerActive by remember(panel, playerDragging) {
                        derivedStateOf {
                            panel == PlayerPanel.NONE && !playerDragging && playerExpandProgress.value <= 0.001f
                        }
                    }
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
                        miniPlayerActive = miniPlayerActive,
                        playerTransitionRegistry = playerRegistry,
                        playerExpandProgress = expandProgressLambda,
                        playerSheetMounted = {
                            state.screen == ClientScreen.LIBRARY &&
                                state.playback.current != null &&
                                (playerOverlayVisible || playerDragging || playerExpandProgress.value > 0.001f)
                        },
                        onExpandDragStart = onExpandDragStart,
                        onExpandDragDelta = onExpandDragDelta,
                        onExpandDragEnd = onExpandDragEnd,
                    ) }
                }
            }
            val activeSong = state.playback.current
            val sheetPrewarmed = state.screen == ClientScreen.LIBRARY && activeSong != null
            val sheetActive by remember(state.screen, activeSong != null, playerOverlayVisible, playerDragging) {
                derivedStateOf {
                    state.screen == ClientScreen.LIBRARY &&
                        activeSong != null &&
                        (playerOverlayVisible || playerDragging || playerExpandProgress.value > 0.001f)
                }
            }

            if (sheetPrewarmed) {
                BackHandler(enabled = panel == PlayerPanel.PLAYER) {
                    playerDragging = false
                    panel = PlayerPanel.NONE
                    queueReturnPanel = PlayerPanel.NONE
                }
                if (sheetActive) {
                    Box(
                        Modifier.fillMaxSize()
                            .pointerInput(playerOverlayVisible) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (playerOverlayVisible || playerExpandProgress.value > 0.05f) {
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }
                            .graphicsLayer {
                                val p = playerExpandProgress.value.coerceIn(0f, 1f)
                                alpha = (if (isWideMode) 0.36f else 0.44f) * p
                            }
                            .background(Color.Black),
                    )
                }
                val slideStartY = computeMiniBarTopPx()
                val rootHeightPx = playerRegistry.rootSize.height.toFloat().coerceAtLeast(1f)
                val compactSheetShape = remember(playerRegistry, expandProgressLambda, slideStartY) {
                    CompactMorphingSheetShape(playerRegistry, expandProgressLambda) { slideStartY }
                }
                val sheetTranslationYLambda = remember(isWideMode, slideStartY, rootHeightPx, playerExpandProgress) {
                    {
                        val p = playerExpandProgress.value.coerceIn(0f, 1f)
                        (1f - p) * (if (isWideMode) rootHeightPx else slideStartY)
                    }
                }
                Box(
                    Modifier.fillMaxSize()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                if (sheetActive) {
                                    placeable.placeRelative(0, 0)
                                }
                            }
                        }
                        .focusProperties { canFocus = sheetActive }
                        .then(if (!sheetActive) Modifier.clearAndSetSemantics {} else Modifier)
                        .graphicsLayer {
                            val p = playerExpandProgress.value.coerceIn(0f, 1f)
                            val isRest = p >= 0.999f
                            if (isWideMode) {
                                translationY = (1f - p) * size.height
                                clip = false
                            } else {
                                translationY = (1f - p) * slideStartY
                                shape = if (isRest) RectangleShape else compactSheetShape
                                clip = !isRest
                            }
                        },
                ) {
                    NowPlayingScreen(
                        state = state.playback,
                        lyricState = state.lyricState,
                        translationEnabled = state.translationEnabled,
                        translationNotice = state.translationNotice,
                        keepScreenOnEnabled = state.keepScreenOnEnabled,
                        actions = actions,
                        imageLoader = imageLoader,
                        onQueue = {
                            queueReturnPanel = PlayerPanel.PLAYER
                            panel = PlayerPanel.QUEUE
                        },
                        onClose = {
                            playerDragging = false
                            panel = PlayerPanel.NONE
                            queueReturnPanel = PlayerPanel.NONE
                        },
                        expandProgress = expandProgressLambda,
                        sheetTranslationY = sheetTranslationYLambda,
                        isWideTransition = isWideMode,
                        onCollapseDragStart = onExpandDragStart,
                        onCollapseDragDelta = onExpandDragDelta,
                        onCollapseDragEnd = onExpandDragEnd,
                        transitionRegistry = playerRegistry,
                        mobileLyricsMode = playerMobileLyricsMode,
                        onMobileLyricsModeChange = { playerMobileLyricsMode = it },
                        sheetVisible = playerOverlayVisible,
                    )
                }
                if (!isWideMode && sheetActive) {
                    FlyingArtworkOverlay(
                        song = activeSong,
                        imageLoader = imageLoader,
                        progress = expandProgressLambda,
                        registry = playerRegistry,
                        isWideMode = false,
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
            val relayFrom = activeRelayBounds
            if (relayFrom != null && !splashVisible && state.screen == ClientScreen.LIBRARY) {
                Box(Modifier.fillMaxSize().zIndex(30f)) {
                    AvatarRelayOverlay(
                        fromRect = relayFrom,
                        initial = activeRelayInitial,
                        registry = avatarRegistry,
                        onFinished = { activeRelayBounds = null },
                    )
                }
            }
            if (splashVisible) {
                Box(Modifier.fillMaxSize().zIndex(40f)) {
                    key(state.splashNonce) {
                        ColdOpenSplashOverlay(
                            state = state,
                            registry = avatarRegistry,
                            onFinished = {
                                splashNonceShown = state.splashNonce
                                splashDone = true
                            },
                        )
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
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.ft_restoring), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ConnectionScreen(state: ClientUiState, actions: ClientActions) {
    var url by rememberSaveable(state.instanceUrl, state.addingAccount) {
        mutableStateOf(state.instanceUrl)
    }
    var username by rememberSaveable(state.instanceUrl, state.username, state.addingAccount, state.connectStep) {
        mutableStateOf(state.username)
    }
    var password by remember(state.instanceUrl, state.username, state.addingAccount, state.connectStep) {
        mutableStateOf("")
    }
    var showPassword by remember(state.instanceUrl, state.username, state.addingAccount, state.connectStep) {
        mutableStateOf(false)
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    BackHandler(enabled = state.connectStep == ConnectStep.CREDENTIALS && !state.busy) {
        actions.editInstanceAddress()
    }
    BackHandler(enabled = state.connectStep == ConnectStep.INSTANCE && state.addingAccount && !state.busy) {
        actions.cancelAddAccount()
    }
    val activeOrigin = state.instanceUrl.ifBlank { url.trim() }
    val retrySavedAccount = remember(state.savedAccounts, state.addingAccount, activeOrigin, username) {
        if (state.addingAccount) null
        else {
            val normalizedOrigin = runCatching { InstanceAddress.parse(activeOrigin).origin }
                .getOrElse { activeOrigin.trim().trimEnd('/') }
            val trimmedUsername = username.trim()
            state.savedAccounts.firstOrNull { acc ->
                !acc.needsReauth &&
                    acc.origin.equals(normalizedOrigin, ignoreCase = true) &&
                    acc.username == trimmedUsername
            }
        }
    }
    val canCheckInstance = url.isNotBlank() && !state.busy
    val checkInstance = {
        if (canCheckInstance) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            actions.checkInstance(url.trim())
        }
    }
    val canSubmitLogin = activeOrigin.isNotBlank() && username.isNotBlank() &&
        (password.isNotEmpty() || retrySavedAccount != null) && !state.busy
    val submitLogin = {
        if (canSubmitLogin) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            val submitted = password
            password = ""
            if (submitted.isEmpty() && retrySavedAccount != null) {
                actions.switchAccount(retrySavedAccount.accountKey)
            } else {
                actions.login(activeOrigin, username.trim(), submitted)
            }
        }
    }
    val titleRes = if (state.addingAccount) R.string.ft_add_account else R.string.ft_connect_title
    val subtitleRes = when {
        state.connectStep == ConnectStep.INSTANCE && state.addingAccount -> R.string.ft_add_account_instance_subtitle
        state.connectStep == ConnectStep.INSTANCE -> R.string.ft_connect_instance_subtitle
        state.addingAccount -> R.string.ft_add_account_subtitle
        else -> R.string.ft_connect_subtitle
    }

    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val imeBottomDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }
        val isTabletWide = maxWidth >= 720.dp && (maxHeight + imeBottomDp) >= 440.dp
        if (isTabletWide) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 36.dp, vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ConnectionBrandHeroPane(
                        username = if (state.connectStep == ConnectStep.CREDENTIALS) username else "",
                        titleRes = titleRes,
                        subtitleRes = subtitleRes,
                        addingAccount = state.addingAccount,
                        busy = state.busy,
                        onCancelAddAccount = actions::cancelAddAccount,
                        modifier = Modifier.weight(1f),
                    )
                    ConnectionFormCard(
                        url = url,
                        onUrlChange = { url = it },
                        username = username,
                        onUsernameChange = { username = it },
                        password = password,
                        onPasswordChange = { password = it },
                        showPassword = showPassword,
                        onTogglePassword = { showPassword = !showPassword },
                        state = state,
                        canCheckInstance = canCheckInstance,
                        onCheckInstance = checkInstance,
                        canSubmitLogin = canSubmitLogin,
                        onSubmitLogin = submitLogin,
                        actions = actions,
                        contentPadding = 24.dp,
                        modifier = Modifier.widthIn(min = 360.dp, max = 440.dp).weight(1f, fill = false),
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                ) {
                    if (state.addingAccount) {
                        ConnectionCancelRow(busy = state.busy, onCancel = actions::cancelAddAccount)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LoginAvatarBadge(
                            username = if (state.connectStep == ConnectStep.CREDENTIALS) username else "",
                            busy = state.busy,
                            size = 56.dp,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(titleRes),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                    }
                    Text(
                        stringResource(subtitleRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
                    )
                    ConnectionFormCard(
                        url = url,
                        onUrlChange = { url = it },
                        username = username,
                        onUsernameChange = { username = it },
                        password = password,
                        onPasswordChange = { password = it },
                        showPassword = showPassword,
                        onTogglePassword = { showPassword = !showPassword },
                        state = state,
                        canCheckInstance = canCheckInstance,
                        onCheckInstance = checkInstance,
                        canSubmitLogin = canSubmitLogin,
                        onSubmitLogin = submitLogin,
                        actions = actions,
                        contentPadding = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionCancelRow(busy: Boolean, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onCancel,
            enabled = !busy,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.heightIn(min = 48.dp).testTag("connect_cancel"),
        ) {
            Icon(ClientIcons.ChevronLeft, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.ft_cancel))
        }
    }
}

@Composable
private fun ConnectionBrandHeroPane(
    username: String,
    titleRes: Int,
    subtitleRes: Int,
    addingAccount: Boolean,
    busy: Boolean,
    onCancelAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.size(260.dp).align(Alignment.TopStart).offset(x = (-32).dp, y = (-28).dp)) {
            val center = Offset(size.width * 0.38f, size.height * 0.36f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(FlareBrandTeal.copy(alpha = 0.14f), FlareBrandIndigo.copy(alpha = 0.04f), Color.Transparent),
                    center = center,
                    radius = size.minDimension * 0.62f,
                ),
                radius = size.minDimension * 0.62f,
                center = center,
            )
            drawCircle(
                color = primary.copy(alpha = 0.10f),
                radius = size.minDimension * 0.38f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            drawCircle(
                color = FlareBrandBlue.copy(alpha = 0.08f),
                radius = size.minDimension * 0.50f,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            if (addingAccount) {
                ConnectionCancelRow(busy = busy, onCancel = onCancelAddAccount)
            }
            LoginAvatarBadge(
                username = username,
                busy = busy,
                size = 76.dp,
            )
            Spacer(Modifier.height(22.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(subtitleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 380.dp),
            )
        }
    }
}

@Composable
private fun ConnectionFormCard(
    url: String,
    onUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
    state: ClientUiState,
    canCheckInstance: Boolean,
    onCheckInstance: () -> Unit,
    canSubmitLogin: Boolean,
    onSubmitLogin: () -> Unit,
    actions: ClientActions,
    contentPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        AnimatedContent(
            targetState = state.connectStep,
            transitionSpec = {
                val forward = targetState == ConnectStep.CREDENTIALS
                (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { full ->
                    if (forward) full / 4 else -full / 4
                }) togetherWith (fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { full ->
                    if (forward) -full / 4 else full / 4
                })
            },
            label = "connection_step_transition",
        ) { step ->
            Column(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
                if (step == ConnectStep.INSTANCE) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = onUrlChange,
                        enabled = !state.busy,
                        label = { Text(stringResource(R.string.ft_instance_address)) },
                        placeholder = { Text("https://music.example.com") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onCheckInstance() }),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("connect_url"),
                    )
                    state.notice?.let { Notice(it, Modifier.padding(top = 14.dp)) }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onCheckInstance,
                        enabled = canCheckInstance,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("connect_check"),
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(stringResource(if (state.busy) R.string.ft_checking_instance else R.string.ft_check_and_continue))
                    }
                    val switchableAccounts = remember(state.savedAccounts) {
                        state.savedAccounts.filter { !it.needsReauth || !it.active }
                    }
                    if (!state.addingAccount && switchableAccounts.isNotEmpty()) {
                        Spacer(Modifier.height(22.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.ft_saved_accounts),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Column(
                            Modifier.fillMaxWidth().testTag("connect_saved_accounts"),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            switchableAccounts.forEach { acc ->
                                val name = acc.displayName.ifBlank { acc.username }
                                Surface(
                                    onClick = { if (!state.busy) actions.switchAccount(acc.accountKey) },
                                    enabled = !state.busy,
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("connect_saved_${acc.accountKey}"),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                name.take(1).uppercase(Locale.ROOT),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                "${acc.username} · ${formatAccountOriginHost(acc.origin)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (acc.needsReauth) {
                                            Text(
                                                stringResource(R.string.ft_account_reauth_required),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        } else {
                                            Text(
                                                stringResource(R.string.ft_switch_account),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val displayedOrigin = state.instanceUrl.ifBlank { url.trim() }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().testTag("connect_instance_bar"),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                                Text(
                                    text = formatAccountOriginHost(displayedOrigin).ifBlank { displayedOrigin },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(R.string.ft_instance_ready_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            TextButton(
                                onClick = actions::editInstanceAddress,
                                enabled = !state.busy,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("connect_change_instance"),
                            ) {
                                Text(stringResource(R.string.ft_change_instance))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        enabled = !state.busy,
                        label = { Text(stringResource(R.string.ft_username)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("connect_username"),
                    )
                    Spacer(Modifier.height(12.dp))
                    SecretField(
                        value = password,
                        onChange = onPasswordChange,
                        label = stringResource(R.string.ft_password),
                        enabled = !state.busy,
                        shown = showPassword,
                        toggle = onTogglePassword,
                        modifier = Modifier.testTag("connect_password"),
                        imeAction = ImeAction.Go,
                        keyboardActions = KeyboardActions(onGo = { onSubmitLogin() }),
                    )
                    state.notice?.let { Notice(it, Modifier.padding(top = 14.dp)) }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onSubmitLogin,
                        enabled = canSubmitLogin,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("connect_submit"),
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(stringResource(if (state.busy) R.string.ft_connecting else R.string.ft_sign_in_action))
                    }
                    if (state.googleLoginEnabled) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            )
                            Text(
                                stringResource(R.string.ft_or_divider),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        if (state.googleNativeLoginEnabled) {
                            OutlinedButton(
                                onClick = actions::startGoogleLogin,
                                enabled = !state.busy,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("connect_google"),
                            ) {
                                GoogleBrandMark(modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.ft_continue_with_google))
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.ft_google_server_upgrade_needed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().testTag("connect_google_upgrade_hint"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoogleBrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.21f
        val inset = strokeWidth / 2f
        val arcSize = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(inset, inset)
        // Red top arc
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = -140f,
            sweepAngle = 105f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Butt),
        )
        // Yellow left arc
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 140f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Butt),
        )
        // Green bottom arc
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 35f,
            sweepAngle = 105f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Butt),
        )
        // Blue right-bottom arc + horizontal bar
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -5f,
            sweepAngle = 45f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Butt),
        )
        val midY = size.height * 0.5f
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width * 0.52f, midY),
            end = Offset(size.width - inset * 0.3f, midY),
            strokeWidth = strokeWidth,
        )
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
        shape = RoundedCornerShape(14.dp),
        trailingIcon = {
            TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant), onClick = toggle, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (shown) R.string.ft_hide_password else R.string.ft_show_password), style = MaterialTheme.typography.labelMedium)
            }
        },
        singleLine = true, modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalFoundationApi::class)
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
    selectionActive: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = LocalListeningInteractions.current
    val status = stringResource(if (buffering) R.string.ft_buffering else if (playing) R.string.ft_playing else R.string.ft_paused)
    val longPressOnlyInteraction = remember { MutableInteractionSource() }
    val rowClickable = if (onLongClick != null) {
        if (!clickEnabled && !selectionActive) {
            Modifier
                .combinedClickable(
                    interactionSource = longPressOnlyInteraction,
                    indication = null,
                    enabled = true,
                    onClick = {},
                    onLongClick = onLongClick,
                )
                .semantics { disabled() }
        } else {
            Modifier.combinedClickable(
                enabled = clickEnabled || selectionActive,
                onClick = onClick,
                onLongClick = if (!selectionActive) onLongClick else null,
            )
        }
    } else {
        Modifier.clickable(enabled = clickEnabled || selectionActive, onClick = onClick)
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = 78.dp)
            .flarePressScale(enabled = clickEnabled || selectionActive, pressedScale = 0.985f)
            .then(
                when {
                    selectionActive && selected -> Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    current -> Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    else -> Modifier
                },
            )
            .then(rowClickable)
            .semantics {
                if (current) stateDescription = status
                if (selectionActive) this.selected = selected
            }
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (selectionActive) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                modifier = Modifier.size(FlareTokens.TouchTargetMin).testTag("song_check_" + song.id),
            )
        }
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
        }
        if (song.durationSeconds > 0) {
            Text(
                formatTime((song.durationSeconds * 1000).toLong()),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!selectionActive && interaction != null) ActionIcon(ClientIcons.More, stringResource(R.string.ft_listening_song_actions),
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
        val coverUrl = song.coverUrl
        if (loader != null && !coverUrl.isNullOrBlank()) {
            val rawPx = if (decodeSize.isSpecified) {
                with(density) { decodeSize.roundToPx().coerceAtLeast(1) }
            } else {
                0
            }
            val targetPx = quantizeArtworkDecodePx(rawPx)
            val request = remember(context, coverUrl, targetPx, loader) {
                buildCoverImageRequest(
                    context = context,
                    coverUrl = coverUrl,
                    targetPx = targetPx,
                    imageLoader = loader,
                )
            }
            AsyncImage(
                model = request, imageLoader = loader, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal data class UiQueueEntry(val key: String, val song: Song)

internal class UiQueueEntryKeyGenerator {
    private var nextInstanceId = 0L

    fun nextKey(song: Song): String {
        val instanceId = nextInstanceId++
        return "q-$instanceId-${song.id}"
    }

    fun build(queue: List<Song>): List<UiQueueEntry> =
        queue.map { song -> UiQueueEntry(nextKey(song), song) }
}

internal fun reconcileUiQueueEntries(
    currentEntries: List<UiQueueEntry>,
    incomingQueue: List<Song>,
    keyGenerator: UiQueueEntryKeyGenerator,
): List<UiQueueEntry> {
    if (incomingQueue.isEmpty()) return emptyList()
    val reusableBySongId = LinkedHashMap<String, ArrayDeque<UiQueueEntry>>()
    currentEntries.forEach { entry ->
        reusableBySongId.getOrPut(entry.song.id) { ArrayDeque() }.addLast(entry)
    }
    return incomingQueue.map { song ->
        val pool = reusableBySongId[song.id]
        val existing = if (pool != null && pool.isNotEmpty()) pool.removeFirst() else null
        if (existing != null) {
            if (existing.song == song) existing else existing.copy(song = song)
        } else {
            UiQueueEntry(keyGenerator.nextKey(song), song)
        }
    }
}

internal fun computeQueueCenterScrollOffsetPx(viewportHeightPx: Int, itemHeightPx: Int): Int {
    if (viewportHeightPx <= 0 || itemHeightPx <= 0) return 0
    return ((viewportHeightPx - itemHeightPx) / 2).coerceAtLeast(0)
}

private data class OptimisticQueueState(
    val expectedIds: List<String>,
    val expectedCurrentIndex: Int,
    val baselineRevision: Long,
)

private fun applyQueueEntryUpdatesInPlace(
    entries: MutableList<UiQueueEntry>,
    target: List<UiQueueEntry>,
) {
    if (entries.size == target.size &&
        entries.indices.all { idx ->
            entries[idx].key == target[idx].key && entries[idx].song == target[idx].song
        }
    ) {
        return
    }
    val targetKeys = target.mapTo(HashSet(target.size)) { it.key }
    for (idx in entries.lastIndex downTo 0) {
        if (entries[idx].key !in targetKeys) {
            entries.removeAt(idx)
        }
    }
    target.forEachIndexed { targetIdx, targetEntry ->
        val existingIdx = entries.indexOfFirst { it.key == targetEntry.key }
        when {
            existingIdx < 0 -> entries.add(targetIdx, targetEntry)
            existingIdx == targetIdx -> {
                if (entries[targetIdx].song != targetEntry.song) {
                    entries[targetIdx] = targetEntry
                }
            }
            else -> {
                val moved = entries.removeAt(existingIdx)
                val updated = if (moved.song == targetEntry.song) moved else targetEntry
                entries.add(targetIdx, updated)
            }
        }
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
    val keyGenerator = remember { UiQueueEntryKeyGenerator() }
    val entries = remember {
        mutableStateListOf<UiQueueEntry>().apply {
            addAll(keyGenerator.build(state.queue))
        }
    }
    val listState = rememberLazyListState()
    val absorbBottomOverscroll = rememberAbsorbBottomOverscroll()
    val haptic = LocalHapticFeedback.current

    var draggedKey by remember { mutableStateOf<String?>(null) }
    var optimisticState by remember { mutableStateOf<OptimisticQueueState?>(null) }
    var pinnedCurrentKey by remember { mutableStateOf<String?>(null) }
    var snapshotRevision by remember { mutableLongStateOf(0L) }
    var lastObservedSnapshot by remember {
        mutableStateOf(state.queue.map { it.id } to state.currentIndex)
    }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragOriginInList by remember { mutableFloatStateOf(0f) }
    var dragPointerInList by remember { mutableFloatStateOf(0f) }
    var dragRawPointerInList by remember { mutableFloatStateOf(0f) }
    var dragFingerOffsetFromCenter by remember { mutableFloatStateOf(0f) }
    var listTopInWindow by remember { mutableFloatStateOf(0f) }
    var listHeightPx by remember { mutableFloatStateOf(0f) }
    var didInitialCenterScroll by remember { mutableStateOf(false) }

    val observedSnapshotNow = state.queue.map { it.id } to state.currentIndex
    SideEffect {
        if (observedSnapshotNow != lastObservedSnapshot) {
            lastObservedSnapshot = observedSnapshotNow
            snapshotRevision += 1L
        }
    }

    // Optimistic sync: preserve dragged/reordered instance keys in-place without recreating LazyColumn items or images.
    LaunchedEffect(state.queue, state.currentIndex, draggedKey, optimisticState, snapshotRevision) {
        if (draggedKey != null) return@LaunchedEffect
        val pending = optimisticState
        val incomingIds = state.queue.map { it.id }
        if (pending != null) {
            val snapshotUpdatedSinceCommit = snapshotRevision > pending.baselineRevision
            val orderMatched = incomingIds == pending.expectedIds
            val currentMatched = pending.expectedCurrentIndex < 0 ||
                state.currentIndex == pending.expectedCurrentIndex
            if (snapshotUpdatedSinceCommit && orderMatched && currentMatched) {
                optimisticState = null
                pinnedCurrentKey = null
            } else {
                // Wait briefly for MediaController async commit before reconciling rejected or external updates.
                delay(650L)
                optimisticState = null
                pinnedCurrentKey = null
            }
        }
        val reconciled = reconcileUiQueueEntries(
            currentEntries = entries.toList(),
            incomingQueue = state.queue,
            keyGenerator = keyGenerator,
        )
        applyQueueEntryUpdatesInPlace(entries, reconciled)
    }

    // Center the active queue track once when the sheet opens and viewport metrics are measured.
    LaunchedEffect(entries.isNotEmpty(), listHeightPx > 0f) {
        if (didInitialCenterScroll || entries.isEmpty() || listHeightPx <= 0f) return@LaunchedEffect
        val targetIndex = state.currentIndex
        if (targetIndex !in entries.indices) {
            didInitialCenterScroll = true
            return@LaunchedEffect
        }
        val fallbackItemPx = with(density) { 64.dp.roundToPx() }
        val measuredItemPx = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: fallbackItemPx
        val centerOffsetPx = computeQueueCenterScrollOffsetPx(
            viewportHeightPx = listHeightPx.roundToInt(),
            itemHeightPx = measuredItemPx,
        )
        listState.scrollToItem(targetIndex, -centerOffsetPx)
        didInitialCenterScroll = true
    }

    fun reorderDraggedTowardPointer(key: String, pointerY: Float) {
        val currentIdx = entries.indexOfFirst { it.key == key }
        if (currentIdx < 0) return
        val draggedCenter = pointerY - dragFingerOffsetFromCenter
        val visible = listState.layoutInfo.visibleItemsInfo
        if (currentIdx < entries.lastIndex) {
            val nextEntry = entries[currentIdx + 1]
            val nextInfo = visible.firstOrNull { it.key == nextEntry.key }
            if (nextInfo != null && draggedCenter >= nextInfo.offset + nextInfo.size * 0.55f) {
                entries.add(currentIdx + 1, entries.removeAt(currentIdx))
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                return
            }
        }
        if (currentIdx > 0) {
            val prevEntry = entries[currentIdx - 1]
            val prevInfo = visible.firstOrNull { it.key == prevEntry.key }
            if (prevInfo != null && draggedCenter <= prevInfo.offset + prevInfo.size * 0.45f) {
                entries.add(currentIdx - 1, entries.removeAt(currentIdx))
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    // Controlled auto-scroll only after deliberate movement toward top or bottom edge.
    LaunchedEffect(draggedKey) {
        val key = draggedKey ?: return@LaunchedEffect
        val edgeZonePx = with(density) { 42.dp.toPx() }
        val maxStepPx = with(density) { 5.dp.toPx() }
        val minTravelPx = with(density) { 12.dp.toPx() }
        var lastFrameNanos = withFrameNanos { it }
        while (isActive && draggedKey == key) {
            val nowNanos = withFrameNanos { it }
            val frameScale = ((nowNanos - lastFrameNanos).coerceIn(1_000_000L, 48_000_000L)) / 16_666_667f
            lastFrameNanos = nowNanos
            val height = listHeightPx
            if (height > edgeZonePx * 2f) {
                val delta = when {
                    dragPointerInList < edgeZonePx &&
                        dragRawPointerInList < dragOriginInList - minTravelPx &&
                        listState.canScrollBackward -> {
                        val factor = ((edgeZonePx - dragPointerInList) / edgeZonePx).coerceIn(0f, 1f)
                        -maxStepPx * factor * factor * frameScale
                    }
                    dragPointerInList > height - edgeZonePx &&
                        dragRawPointerInList > dragOriginInList + minTravelPx &&
                        listState.canScrollForward -> {
                        val factor = ((dragPointerInList - (height - edgeZonePx)) / edgeZonePx).coerceIn(0f, 1f)
                        maxStepPx * factor * factor * frameScale
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

                    val itemModifier = Modifier
                        .animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                            placementSpec = if (isDragging) null else spring(stiffness = Spring.StiffnessMediumLow),
                        )
                        .zIndex(if (isDragging) 10f else 0f)
                        .graphicsLayer {
                            translationY = dragTranslationY
                            scaleX = if (isDragging) 1.015f else 1f
                            scaleY = if (isDragging) 1.015f else 1f
                        }

                    val activeCurrentKey = pinnedCurrentKey ?: entries.getOrNull(state.currentIndex)?.key
                    val isCurrent = entry.key == activeCurrentKey

                    SwipeToRemoveQueueRow(
                        entryKey = entry.key,
                        gesturesEnabled = draggedKey == null,
                        modifier = itemModifier,
                        onRemove = {
                            val currentIdx = entries.indexOfFirst { it.key == entry.key }
                            if (currentIdx >= 0) {
                                val currentKeyBeforeRemove = pinnedCurrentKey ?: entries.getOrNull(state.currentIndex)?.key
                                val removedCurrent = currentKeyBeforeRemove == entry.key
                                entries.removeAt(currentIdx)
                                val nextExpectedCurrentKey = when {
                                    entries.isEmpty() -> null
                                    removedCurrent -> entries[currentIdx.coerceAtMost(entries.lastIndex)].key
                                    else -> currentKeyBeforeRemove
                                }
                                pinnedCurrentKey = nextExpectedCurrentKey
                                val expectedCurrentIdx = nextExpectedCurrentKey?.let { key ->
                                    entries.indexOfFirst { it.key == key }
                                } ?: -1
                                optimisticState = OptimisticQueueState(
                                    expectedIds = entries.map { it.song.id },
                                    expectedCurrentIndex = expectedCurrentIdx,
                                    baselineRevision = snapshotRevision,
                                )
                                onRemove?.invoke(currentIdx)
                            }
                        },
                    ) {
                        QueueTrackRow(
                            song = entry.song,
                            entryKey = entry.key,
                            loader = loader,
                            current = isCurrent,
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
                                if (pinnedCurrentKey == null) {
                                    pinnedCurrentKey = entries.getOrNull(state.currentIndex)?.key
                                }
                                draggedKey = entry.key
                                dragStartIndex = startIdx
                                dragOriginInList = fingerY
                                dragRawPointerInList = fingerY
                                dragPointerInList = fingerY.coerceIn(0f, listHeightPx.coerceAtLeast(1f))
                                dragFingerOffsetFromCenter = if (slotInfo != null) {
                                    fingerY - (slotInfo.offset + slotInfo.size / 2f)
                                } else 0f
                            },
                            onVerticalDrag = { pointerWindowY ->
                                val rawFingerY = pointerWindowY - listTopInWindow
                                val fingerY = rawFingerY.coerceIn(0f, listHeightPx.coerceAtLeast(1f))
                                dragRawPointerInList = rawFingerY
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
                                    val expectedCurrentIdx = pinnedCurrentKey?.let { key ->
                                        entries.indexOfFirst { it.key == key }
                                    } ?: state.currentIndex
                                    optimisticState = OptimisticQueueState(
                                        expectedIds = entries.map { it.song.id },
                                        expectedCurrentIndex = expectedCurrentIdx,
                                        baselineRevision = snapshotRevision,
                                    )
                                    onMove?.invoke(startIndex, finalIndex)
                                } else if (optimisticState == null) {
                                    pinnedCurrentKey = null
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
    entryKey: String = song.id,
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
    val highlightSurface = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f).compositeOver(MaterialTheme.colorScheme.surface)
    val rowBg = when {
        dragging -> MaterialTheme.colorScheme.surfaceContainerHighest
        current -> highlightSurface
        else -> MaterialTheme.colorScheme.surface
    }
    var handleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val pickupThresholdPx = with(density) { 8.dp.toPx() }
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
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                ),
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
        }
        Box(
            Modifier.width(56.dp).height(52.dp)
                .testTag(dragTag)
                .onGloballyPositioned { handleCoords = it }
                .pointerInput(entryKey, pickupThresholdPx) {
                    var startWindowY = 0f
                    var trackedWindowY = 0f
                    var pendingDeltaY = 0f
                    var dragActivated = false
                    detectVerticalDragGestures(
                        onDragStart = { startOffset ->
                            startWindowY = handleCoords?.localToWindow(startOffset)?.y ?: return@detectVerticalDragGestures
                            trackedWindowY = startWindowY
                            pendingDeltaY = 0f
                            dragActivated = false
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            if (!dragActivated) {
                                pendingDeltaY += dragAmount
                                if (abs(pendingDeltaY) < pickupThresholdPx) return@detectVerticalDragGestures
                                dragActivated = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                latestOnStart(startWindowY)
                                trackedWindowY = startWindowY + pendingDeltaY
                                latestOnDrag(trackedWindowY)
                            } else {
                                trackedWindowY += dragAmount
                                latestOnDrag(trackedWindowY)
                            }
                        },
                        onDragEnd = {
                            if (dragActivated) latestOnEnd()
                            dragActivated = false
                        },
                        onDragCancel = {
                            if (dragActivated) latestOnEnd()
                            dragActivated = false
                        },
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

internal fun clientNoticeMessageRes(notice: ClientNotice): Int = when (notice) {
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
    ClientNotice.GOOGLE_UNAVAILABLE -> R.string.ft_notice_google_unavailable
    ClientNotice.GOOGLE_UNBOUND -> R.string.ft_notice_google_unbound
    ClientNotice.GOOGLE_CANCELLED -> R.string.ft_notice_google_cancelled
    ClientNotice.GOOGLE_FAILED -> R.string.ft_notice_google_failed
    ClientNotice.GOOGLE_NO_BROWSER -> R.string.ft_notice_google_no_browser
}

@Composable
internal fun Notice(notice: ClientNotice, modifier: Modifier = Modifier) {
    val resource = clientNoticeMessageRes(notice)
    val informational = notice in setOf(
        ClientNotice.PASSWORD_CHANGED,
        ClientNotice.PAGE_LIMIT,
        ClientNotice.SOME_SOURCES_UNAVAILABLE,
        ClientNotice.GOOGLE_CANCELLED,
    )
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
