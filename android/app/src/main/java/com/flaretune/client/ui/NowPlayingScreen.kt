package com.flaretune.client.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.flaretune.client.R
import com.flaretune.client.model.ClientNotice
import com.flaretune.client.model.LyricLine
import com.flaretune.client.model.LyricState
import com.flaretune.client.model.LyricStatus
import com.flaretune.client.model.LyricTranslationStatus
import com.flaretune.client.model.LyricWord
import com.flaretune.client.model.Song
import com.flaretune.client.model.showLyricTranslationAction
import com.flaretune.client.ui.theme.FlareTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class ClassicMobilePane { SONG, LYRICS }
private val ClassicTransportHeight = 68.dp
private val ClassicCompactTransportHeight = 56.dp
private val ClassicDragHeaderHeight = 36.dp

private fun canToggleInlineTranslation(lyricState: LyricState): Boolean =
    lyricState.status == LyricStatus.READY &&
        lyricState.translationState != LyricTranslationStatus.NOT_NEEDED &&
        lyricState.lines.any { !it.translation.isNullOrBlank() }

/**
 * Full-screen player modeled after the web classic player (`MobileClassicPlayer` / `ClassicDesktopLayout`):
 * - Always calm dark-ambient cover wash to preserve soft-white typography contrast
 * - Animated playing/paused cover scale & phone Song <-> Lyrics view toggle
 * - Streamlined dotless progress/volume tracks and 5-button transport row
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun NowPlayingScreen(
    state: PlaybackSnapshot,
    lyricState: LyricState = LyricState(),
    translationEnabled: Boolean = false,
    translationNotice: ClientNotice? = null,
    actions: ClientActions,
    imageLoader: ImageLoader?,
    onClose: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.current ?: return
    var mobilePane by rememberSaveable { mutableStateOf(ClassicMobilePane.SONG) }
    val artworkAvailable = imageLoader != null && !song.coverUrl.isNullOrBlank()
    val atmospheric = artworkAvailable
    val scope = rememberCoroutineScope()
    val hostView = LocalView.current
    DisposableEffect(hostView) {
        val previous = hostView.keepScreenOn
        hostView.keepScreenOn = true
        onDispose {
            hostView.keepScreenOn = previous
        }
    }
    val dismissDragY = remember { Animatable(0f) }
    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    var draggingDismiss by remember { mutableStateOf(false) }
    var closingSheet by remember { mutableStateOf(false) }
    var leftRailHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val maxDragThresholdPx = with(LocalDensity.current) { 148.dp.toPx() }
    val settleSpring = remember { spring<Float>(dampingRatio = 0.8f, stiffness = 308f) }

    val closeAnimated: () -> Unit = {
        if (!closingSheet) {
            closingSheet = true
            scope.launch {
                val target = containerHeightPx
                if (target > 1f) {
                    dismissDragY.animateTo(target, settleSpring)
                }
                onClose()
            }
        }
    }

    val settleSheet: (Float) -> Unit = { velocityY ->
        draggingDismiss = false
        if (!closingSheet) {
            val height = containerHeightPx.coerceAtLeast(1f)
            val posThreshold = minOf(height * 0.26f, maxDragThresholdPx)
            val shouldClose = when {
                velocityY <= -1400f -> false
                velocityY >= 1500f -> true
                else -> dismissDragY.value >= posThreshold
            }
            if (shouldClose) {
                closeAnimated()
            } else {
                scope.launch { dismissDragY.animateTo(0f, settleSpring) }
            }
        }
    }

    // Page-level vertical drag-to-dismiss hook (matching Musiver whole-page root draggable semantics).
    val sheetNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || closingSheet) return Offset.Zero
                val currentY = dismissDragY.value
                if (currentY > 0f) {
                    draggingDismiss = true
                    val maxH = containerHeightPx.coerceAtLeast(1f)
                    val next = (currentY + available.y).coerceIn(0f, maxH)
                    val consumedY = next - currentY
                    scope.launch { dismissDragY.snapTo(next) }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || closingSheet) return Offset.Zero
                if (scrollState.value == 0 && available.y > 0f) {
                    draggingDismiss = true
                    val maxH = containerHeightPx.coerceAtLeast(1f)
                    val next = (dismissDragY.value + available.y).coerceIn(0f, maxH)
                    scope.launch { dismissDragY.snapTo(next) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!closingSheet && dismissDragY.value > 0f) {
                    settleSheet(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!closingSheet && scrollState.value == 0 && available.y > 220f) {
                    settleSheet(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier.fillMaxSize()
            .onSizeChanged { containerHeightPx = it.height.toFloat() }
            .graphicsLayer { translationY = dismissDragY.value }
            .testTag("full_player"),
    ) {
        ClassicPlayerBackdrop(song, imageLoader, atmospheric)
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val screenMaxWidth = maxWidth
            val screenMaxHeight = maxHeight
            val twoColumns = screenMaxWidth >= 1000.dp || (screenMaxWidth >= 600.dp && screenMaxWidth > screenMaxHeight * 1.1f)
            BackHandler(enabled = !twoColumns && mobilePane == ClassicMobilePane.LYRICS) {
                mobilePane = ClassicMobilePane.SONG
            }
            val bodyHeight = (screenMaxHeight - ClassicDragHeaderHeight).coerceAtLeast(0.dp)
            val pageWidth = screenMaxWidth.coerceAtMost(1320.dp)
            val narrowContentWidth = (screenMaxWidth - 44.dp).coerceAtLeast(0.dp).coerceAtMost(440.dp)

            Column(
                Modifier.fillMaxSize()
                    .nestedScroll(sheetNestedScroll)
                    .verticalScroll(scrollState)
                    .testTag("player_scroll"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ClassicDragHandleHeader(
                    dragging = draggingDismiss,
                    onClose = closeAnimated,
                    onDragStart = { draggingDismiss = true },
                    onDragDelta = { delta ->
                        if (!closingSheet) {
                            val maxH = containerHeightPx.coerceAtLeast(1f)
                            scope.launch {
                                val next = (dismissDragY.value + delta).coerceIn(0f, maxH)
                                dismissDragY.snapTo(next)
                            }
                        }
                    },
                    onDragEnd = settleSheet,
                )
                if (twoColumns) {
                    val stagePaddingH = (screenMaxWidth * 0.045f).coerceIn(28.dp, 68.dp)
                    val columnGap = (screenMaxWidth * 0.04f).coerceIn(28.dp, 56.dp)
                    val controlsAllowance = 204.dp * density.fontScale.coerceAtLeast(1f)
                    val artSize = minOf(screenMaxWidth * 0.34f, (bodyHeight - controlsAllowance).coerceAtLeast(160.dp))
                        .coerceIn(160.dp, 460.dp)
                    Row(
                        Modifier.width(pageWidth).height(bodyHeight)
                            .padding(start = stagePaddingH, end = stagePaddingH, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(columnGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Asymmetric Web ratio (~42% left compact cover+controls rail / ~58% right floating lyrics).
                        Box(
                            Modifier.weight(0.42f).fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                Modifier.width(artSize)
                                    .heightIn(max = bodyHeight)
                                    .onSizeChanged { leftRailHeightPx = it.height },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                ClassicArtwork(
                                    song = song,
                                    imageLoader = imageLoader,
                                    playing = state.playing,
                                    buffering = state.buffering,
                                    modifier = Modifier.size(artSize),
                                )
                                Spacer(Modifier.height(20.dp))
                                ClassicPlayerControls(
                                    state = state,
                                    actions = actions,
                                    onQueue = onQueue,
                                    showLyricsButton = false,
                                    onToggleLyrics = {},
                                    hideMetadata = false,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Box(
                            Modifier.weight(0.58f).fillMaxHeight(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            ClassicLyricsViewport(
                                song = song,
                                lyricState = lyricState,
                                translationEnabled = translationEnabled,
                                translationNotice = translationNotice,
                                playbackPositionMs = state.positionMs,
                                playing = state.playing,
                                buffering = state.buffering,
                                twoColumns = true,
                                onSeekTo = actions::seekTo,
                                onRetry = actions::retryLyrics,
                                onSetTranslationEnabled = actions::setTranslationEnabled,
                                onTranslate = actions::requestLyricTranslation,
                                modifier = Modifier
                                    .widthIn(max = (artSize * 1.32f).coerceAtLeast(420.dp))
                                    .fillMaxWidth()
                                    .then(if (leftRailHeightPx > 0) {
                                        Modifier.height(with(density) { leftRailHeightPx.toDp() })
                                    } else Modifier.fillMaxHeight()),
                            )
                        }
                    }
                } else {
                    val showDisplayToggle = remember(
                        lyricState.status,
                        lyricState.translationState,
                        lyricState.lines,
                    ) {
                        canToggleInlineTranslation(lyricState)
                    }
                    val useCompactTransport = bodyHeight < 400.dp
                    SubcomposeLayout(Modifier.width(narrowContentWidth)) { constraints ->
                        val widthPx = constraints.maxWidth
                        val bodyPx = bodyHeight.roundToPx()
                        val bottomPadPx = 12.dp.roundToPx()
                        val widthConstraints = Constraints.fixedWidth(widthPx)
                        val transportPlaceable = subcompose("mobile_transport") {
                            ClassicPlayerTransportPanel(
                                state = state,
                                actions = actions,
                                onQueue = onQueue,
                                compactVertical = useCompactTransport,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }.first().measure(widthConstraints)
                        val stageHeightPx = (bodyPx - bottomPadPx - transportPlaceable.height).coerceAtLeast(0)
                        val stagePlaceable = subcompose("mobile_stage") {
                            SharedTransitionLayout(Modifier.fillMaxWidth()) {
                                AnimatedContent(
                                    targetState = mobilePane,
                                    transitionSpec = {
                                        (fadeIn(tween(240)) togetherWith fadeOut(tween(180)))
                                            .using(SizeTransform(clip = false))
                                    },
                                    label = "classic_mobile_pane",
                                ) { pane ->
                                    val sharedScope = this@SharedTransitionLayout
                                    val animatedScope = this@AnimatedContent
                                    if (pane == ClassicMobilePane.LYRICS) {
                                        ClassicMobileLyricsStage(
                                            widthPx = widthPx,
                                            stageHeightPx = stageHeightPx,
                                            song = song,
                                            lyricState = lyricState,
                                            translationEnabled = translationEnabled,
                                            translationNotice = translationNotice,
                                            showTranslationToggle = showDisplayToggle,
                                            playbackPositionMs = state.positionMs,
                                            playing = state.playing,
                                            buffering = state.buffering,
                                            imageLoader = imageLoader,
                                            actions = actions,
                                            onBackToArtwork = { mobilePane = ClassicMobilePane.SONG },
                                            sharedTransitionScope = sharedScope,
                                            animatedVisibilityScope = animatedScope,
                                        )
                                    } else {
                                        ClassicMobileSongStage(
                                            widthPx = widthPx,
                                            stageHeightPx = stageHeightPx,
                                            song = song,
                                            lyricState = lyricState,
                                            translationEnabled = translationEnabled,
                                            playbackPositionMs = state.positionMs,
                                            playing = state.playing,
                                            buffering = state.buffering,
                                            imageLoader = imageLoader,
                                            onOpenLyrics = { mobilePane = ClassicMobilePane.LYRICS },
                                            sharedTransitionScope = sharedScope,
                                            animatedVisibilityScope = animatedScope,
                                        )
                                    }
                                }
                            }
                        }.first().measure(widthConstraints)
                        val totalHeight = maxOf(bodyPx, stagePlaceable.height + transportPlaceable.height + bottomPadPx)
                        layout(widthPx, totalHeight) {
                            stagePlaceable.placeRelative(0, 0)
                            transportPlaceable.placeRelative(0, totalHeight - bottomPadPx - transportPlaceable.height)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ClassicMobileLyricsStage(
    widthPx: Int,
    stageHeightPx: Int,
    song: Song,
    lyricState: LyricState,
    translationEnabled: Boolean,
    translationNotice: ClientNotice?,
    showTranslationToggle: Boolean,
    playbackPositionMs: Long,
    playing: Boolean,
    buffering: Boolean,
    imageLoader: ImageLoader?,
    actions: ClientActions,
    onBackToArtwork: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    SubcomposeLayout(Modifier.fillMaxWidth()) {
        val widthConstraints = Constraints.fixedWidth(widthPx)
        val minViewportPx = minOf(
            200.dp.roundToPx(),
            maxOf(140.dp.roundToPx(), (stageHeightPx * 0.55f).roundToInt()),
        )
        val topPlaceable = subcompose("lyrics_top_row") {
            ClassicLyricsTopRow(
                song = song,
                imageLoader = imageLoader,
                onBackToArtwork = onBackToArtwork,
                showTranslationToggle = showTranslationToggle,
                translationEnabled = translationEnabled,
                onToggleTranslation = { actions.setTranslationEnabled(!translationEnabled) },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }.first().measure(widthConstraints)
        val viewportHeightPx = (stageHeightPx - topPlaceable.height).coerceAtLeast(minViewportPx)
        val viewportPlaceable = subcompose("lyrics_viewport") {
            ClassicLyricsViewport(
                song = song,
                lyricState = lyricState,
                translationEnabled = translationEnabled,
                translationNotice = translationNotice,
                playbackPositionMs = playbackPositionMs,
                playing = playing,
                buffering = buffering,
                twoColumns = false,
                onSeekTo = actions::seekTo,
                onRetry = actions::retryLyrics,
                onSetTranslationEnabled = actions::setTranslationEnabled,
                onTranslate = actions::requestLyricTranslation,
                showInlineTranslationToggle = false,
                modifier = Modifier.fillMaxSize(),
            )
        }.first().measure(Constraints.fixed(widthPx, viewportHeightPx))
        val resolvedHeight = maxOf(stageHeightPx, topPlaceable.height + viewportHeightPx)
        layout(widthPx, resolvedHeight) {
            topPlaceable.placeRelative(0, 0)
            viewportPlaceable.placeRelative(0, topPlaceable.height)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ClassicMobileSongStage(
    widthPx: Int,
    stageHeightPx: Int,
    song: Song,
    lyricState: LyricState,
    translationEnabled: Boolean,
    playbackPositionMs: Long,
    playing: Boolean,
    buffering: Boolean,
    imageLoader: ImageLoader?,
    onOpenLyrics: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    SubcomposeLayout(Modifier.fillMaxWidth()) {
        val topPadPx = 4.dp.roundToPx()
        val metadataGapPx = 10.dp.roundToPx()
        val controlsGapPx = 10.dp.roundToPx()
        val previewGapPx = 12.dp.roundToPx()
        val widthConstraints = Constraints.fixedWidth(widthPx)
        val metadataPlaceable = subcompose("song_metadata") {
            Column(Modifier.fillMaxWidth()) {
                ClassicSongMetadataRow(
                    song = song,
                    showLyricsButton = false,
                    onToggleLyrics = {},
                )
                Spacer(Modifier.height(10.dp))
            }
        }.first().measure(widthConstraints)
        val availableUpperPx = (stageHeightPx - topPadPx - metadataPlaceable.height - metadataGapPx)
            .coerceAtLeast(0)
        val minArtPx = minOf(widthPx, 136.dp.roundToPx())
        val artSizePx = minOf(widthPx, availableUpperPx.coerceAtLeast(minArtPx))
        val artworkPlaceable = subcompose("song_artwork") {
            ClassicArtwork(
                song = song,
                imageLoader = imageLoader,
                playing = playing,
                buffering = buffering,
                modifier = Modifier.size(artSizePx.toDp()),
                onClick = onOpenLyrics,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }.first().measure(Constraints.fixed(artSizePx, artSizePx))
        val middleFreePx = (availableUpperPx - artworkPlaceable.height - previewGapPx).coerceAtLeast(0)
        val selectedPreview = if (middleFreePx > 0) {
            val twoLinePlaceable = subcompose("song_preview_2") {
                ClassicLyricPreviewStrip(
                    song = song,
                    lyricState = lyricState,
                    translationEnabled = translationEnabled,
                    playbackPositionMs = playbackPositionMs,
                    playing = playing,
                    buffering = buffering,
                    maxLines = 2,
                    onClick = onOpenLyrics,
                )
            }.first().measure(widthConstraints)
            if (twoLinePlaceable.height <= middleFreePx) {
                twoLinePlaceable
            } else {
                val oneLinePlaceable = subcompose("song_preview_1") {
                    ClassicLyricPreviewStrip(
                        song = song,
                        lyricState = lyricState,
                        translationEnabled = translationEnabled,
                        playbackPositionMs = playbackPositionMs,
                        playing = playing,
                        buffering = buffering,
                        maxLines = 1,
                        onClick = onOpenLyrics,
                    )
                }.first().measure(widthConstraints)
                if (oneLinePlaceable.height <= middleFreePx) oneLinePlaceable else null
            }
        } else {
            null
        }
        val upperGroupHeight = artworkPlaceable.height +
            if (selectedPreview != null) previewGapPx + selectedPreview.height else 0
        val upperY = topPadPx + ((availableUpperPx - upperGroupHeight).coerceAtLeast(0) / 2)
        val metadataY = maxOf(
            stageHeightPx - metadataPlaceable.height,
            upperY + upperGroupHeight + controlsGapPx,
        )
        val resolvedHeight = maxOf(stageHeightPx, metadataY + metadataPlaceable.height)
        val artX = ((widthPx - artworkPlaceable.width) / 2).coerceAtLeast(0)
        layout(widthPx, resolvedHeight) {
            artworkPlaceable.placeRelative(artX, upperY)
            selectedPreview?.placeRelative(0, upperY + artworkPlaceable.height + previewGapPx)
            metadataPlaceable.placeRelative(0, metadataY)
        }
    }
}

@Composable
private fun ClassicDragHandleHeader(
    dragging: Boolean,
    onClose: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
) {
    val closeDescription = stringResource(R.string.ft_close)
    val velocityTracker = remember { VelocityTracker() }
    var trackedY = remember { 0f }
    Box(
        Modifier.fillMaxWidth()
            .height(ClassicDragHeaderHeight)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        trackedY = 0f
                        velocityTracker.resetTracking()
                        onDragStart()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        trackedY += dragAmount
                        velocityTracker.addPosition(change.uptimeMillis, Offset(0f, trackedY))
                        onDragDelta(dragAmount)
                    },
                    onDragEnd = {
                        val velocityY = velocityTracker.calculateVelocity().y
                        onDragEnd(velocityY)
                    },
                    onDragCancel = { onDragEnd(0f) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Compact 48dp x 36dp collapse target with crisp 36dp x 4dp capsule pill (modeled after Musiver).
        Box(
            Modifier.size(width = 48.dp, height = ClassicDragHeaderHeight)
                .clip(CircleShape)
                .clickable(onClick = onClose)
                .testTag("player_close")
                .semantics { contentDescription = closeDescription },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (dragging) 0.72f else 0.42f)),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ClassicArtwork(
    song: Song,
    imageLoader: ImageLoader?,
    playing: Boolean,
    buffering: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val activePlaying = playing || buffering
    val scale by animateFloatAsState(
        targetValue = if (activePlaying) 0.96f else 0.80f,
        animationSpec = if (activePlaying) {
            spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow)
        } else {
            tween(durationMillis = 320)
        },
        label = "classic_cover_scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (activePlaying) 1f else 0.86f,
        animationSpec = tween(durationMillis = 300),
        label = "classic_cover_alpha",
    )
    val shape = RoundedCornerShape(FlareTokens.RadiusLg)
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "classic_album_artwork_${song.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> spring(dampingRatio = 0.86f, stiffness = 360f) },
            )
        }
    } else {
        Modifier
    }
    Box(
        modifier
            .then(sharedModifier)
            .testTag("player_artwork")
            .scale(scale)
            .alpha(alpha)
            .shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.6f), spotColor = Color.Black.copy(alpha = 0.7f))
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1F2229), Color(0xFF14161B)),
                ),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            ClientIcons.Music, null, Modifier.fillMaxSize(0.24f),
            tint = Color.White.copy(alpha = 0.42f),
        )
        if (imageLoader != null && !song.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = song.coverUrl,
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private data class ClassicPreviewStripTarget(
    val activeIdx: Int,
    val line: LyricLine?,
    val primary: String,
    val secondary: String,
    val wordSynced: Boolean,
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ClassicLyricPreviewStrip(
    song: Song,
    lyricState: LyricState,
    translationEnabled: Boolean,
    playbackPositionMs: Long,
    playing: Boolean,
    buffering: Boolean,
    onClick: () -> Unit,
    maxLines: Int = 2,
) {
    val activeIdx = lyricState.activeLyricIndex
    val activeLine = lyricState.lines.getOrNull(activeIdx)
    val nextLine = if (activeIdx >= 0) lyricState.lines.getOrNull(activeIdx + 1) else lyricState.lines.firstOrNull()
    val line1 = when (lyricState.status) {
        LyricStatus.INSTRUMENTAL -> stringResource(R.string.ft_instrumental_lyrics)
        LyricStatus.READY -> activeLine?.text ?: lyricState.lines.firstOrNull()?.text ?: song.album.ifBlank { stringResource(R.string.ft_tap_for_lyrics) }
        LyricStatus.FAILED -> stringResource(R.string.ft_lyrics_failed)
        else -> song.album.ifBlank { stringResource(R.string.ft_tap_for_lyrics) }
    }
    val line2 = if (maxLines > 1) {
        when (lyricState.status) {
            LyricStatus.READY -> activeLine?.translation?.takeIf { translationEnabled && it.isNotBlank() } ?: nextLine?.text.orEmpty()
            else -> ""
        }
    } else {
        ""
    }
    val wordSynced = lyricState.status == LyricStatus.READY &&
        lyricState.syncMode == "word" &&
        activeLine != null &&
        activeLine.words.isNotEmpty()
    val target = ClassicPreviewStripTarget(
        activeIdx = activeIdx,
        line = activeLine,
        primary = line1,
        secondary = line2,
        wordSynced = wordSynced,
    )

    Box(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("player_lyric_preview")
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = target,
            transitionSpec = {
                (slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(220))) togetherWith
                    (slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(160)))
            },
            label = "classic_preview_strip",
        ) { strip ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (strip.wordSynced && strip.line != null) {
                    val lineHeight = MaterialTheme.typography.titleMedium.lineHeight.let {
                        if (it.isSpecified) it else 22.sp
                    }
                    KaraokeLyricText(
                        line = strip.line,
                        basePositionMs = playbackPositionMs,
                        playing = playing && !buffering,
                        fontSize = 17.sp,
                        lineHeight = lineHeight,
                        sungColor = Color.White.copy(alpha = 0.96f),
                        unsungColor = Color.White.copy(alpha = 0.50f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        trackHorizontalFocus = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = strip.primary,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = if (lyricState.status == LyricStatus.READY) 17.sp else 13.sp,
                            fontWeight = if (lyricState.status == LyricStatus.READY) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = if (lyricState.status == LyricStatus.READY) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.56f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                if (strip.secondary.isNotBlank()) {
                    Text(
                        text = strip.secondary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ClassicLyricsTopRow(
    song: Song,
    imageLoader: ImageLoader?,
    onBackToArtwork: () -> Unit,
    showTranslationToggle: Boolean = false,
    translationEnabled: Boolean = false,
    onToggleTranslation: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val thumbShape = RoundedCornerShape(10.dp)
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "classic_album_artwork_${song.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> spring(dampingRatio = 0.86f, stiffness = 360f) },
            )
        }
    } else {
        Modifier
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.weight(1f)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onBackToArtwork)
                .testTag("player_lyrics_top_row")
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(44.dp)
                    .then(sharedModifier)
                    .clip(thumbShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.16f), thumbShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ClientIcons.Music, null, Modifier.size(20.dp), tint = FlareTokens.ClassicSubtitle)
                if (imageLoader != null && !song.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = song.coverUrl,
                        imageLoader = imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(Modifier.weight(1f).clipToBounds()) {
                key(song.id) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = FlareTokens.ClassicTitle,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                    )
                    val meta = listOf(song.artist.ifBlank { stringResource(R.string.ft_unknown_artist) }, song.album)
                        .filter { it.isNotBlank() }
                        .joinToString(" — ")
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FlareTokens.ClassicSubtitle,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                    )
                }
            }
        }
        if (showTranslationToggle) {
            ClassicTranslationButton(
                translationEnabled = translationEnabled,
                onClick = onToggleTranslation,
            )
        }
        PlayerMoreMenu(song = song)
    }
}

@Composable
private fun ClassicLyricsViewport(
    song: Song,
    lyricState: LyricState,
    translationEnabled: Boolean,
    translationNotice: ClientNotice?,
    playbackPositionMs: Long,
    playing: Boolean,
    buffering: Boolean,
    twoColumns: Boolean,
    onSeekTo: (Long) -> Unit,
    onRetry: () -> Unit,
    onSetTranslationEnabled: (Boolean) -> Unit,
    onTranslate: () -> Unit,
    modifier: Modifier = Modifier,
    showInlineTranslationToggle: Boolean = true,
) {
    val controller = remember(lyricState.songId) { LyricsSpringLayoutController() }
    val context = LocalContext.current
    val animatorsEnabled = remember(context) { systemAnimatorsEnabled(context) }
    val density = LocalDensity.current
    var userScrolling by remember(lyricState.songId) { mutableStateOf(false) }
    val interactionSource = remember(lyricState.songId) { MutableInteractionSource() }
    var scrollGeneration by remember(lyricState.songId) { mutableIntStateOf(0) }
    val livePositionMsState = rememberUpdatedState(playbackPositionMs)

    class AnchorSnapshot(var key: Any? = null, var baselineY: Float? = null, var slotIdx: Int = 0)
    class SceneTracker(
        var activeIdx: Int = Int.MIN_VALUE,
        var hadIntro: Boolean = false,
        var translation: Boolean = false,
        var widthPx: Int = 0,
        var heightPx: Int = 0,
        var userScrolling: Boolean = false,
        var dragAnchorIdx: Int = -1,
    )

    val anchorSnapshot = remember(lyricState.songId) { AnchorSnapshot() }
    val sceneTracker = remember(lyricState.songId) {
        SceneTracker(translation = translationEnabled)
    }

    LaunchedEffect(controller) {
        controller.runAnimationLoop()
    }

    val scrollableState = rememberScrollableState { delta ->
        controller.applyScrollDelta(delta)
    }

    // Programmatic layout solves must never start the user's browsing pause or cancel themselves.
    LaunchedEffect(interactionSource, scrollableState) {
        var resume: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    resume?.cancel()
                    scrollGeneration++
                    if (!userScrolling) {
                        sceneTracker.dragAnchorIdx = anchorSnapshot.slotIdx
                        controller.snapScrollToCurrentAnchor(
                            anchorKey = anchorSnapshot.key,
                            baselineAnchorY = anchorSnapshot.baselineY,
                        )
                        userScrolling = true
                    }
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    val gen = ++scrollGeneration
                    resume?.cancel()
                    resume = launch {
                        snapshotFlow { scrollableState.isScrollInProgress }.first { !it }
                        delay(LyricMotionSpecs.USER_SCROLL_DEBOUNCE_MS)
                        if (scrollGeneration == gen) {
                            sceneTracker.dragAnchorIdx = -1
                            controller.resetScrollOffset()
                            userScrolling = false
                        }
                    }
                }
            }
        }
    }

    val activeIdx = lyricState.activeLyricIndex
    val hasIntroInterlude by remember(
        lyricState.status,
        lyricState.lines,
        activeIdx,
        userScrolling,
        lyricState.syncMode,
    ) {
        derivedStateOf {
            if (lyricState.status == LyricStatus.READY && lyricState.lines.isNotEmpty() && activeIdx <= 0) {
                computeInterludeUiState(
                    currentTimeSeconds = livePositionMsState.value / 1000.0,
                    lines = lyricState.lines,
                    activeIndex = activeIdx,
                    lineIndex = 0,
                    userScrolling = userScrolling,
                    syncMode = lyricState.syncMode,
                ).let { it.isInterlude && it.window.kind == InterludeKind.INTRO }
            } else {
                false
            }
        }
    }

    val primarySize = if (twoColumns) 30.sp else 26.sp
    val primaryLineHeight = if (twoColumns) 39.sp else 34.sp
    val secondarySize = if (twoColumns) 18.sp else 16.sp
    val secondaryLineHeight = if (twoColumns) 26.sp else 23.sp
    val lineSpacingPx = with(density) { (if (twoColumns) 26.dp else 22.dp).toPx() }
    val fallbackHeightPx = with(density) { (if (twoColumns) 54.dp else 46.dp).toPx() }

    val showDisplayToggle = remember(
        lyricState.status,
        lyricState.translationState,
        lyricState.lines,
    ) {
        canToggleInlineTranslation(lyricState)
    }
    val showBottomDisplayToggle = showInlineTranslationToggle && showDisplayToggle
    val showGenerateAction = lyricState.showLyricTranslationAction

    // Direct floating lyrics viewport (borderless & cardless, solved via Musiver spring layout).
    Column(modifier = modifier) {
        Box(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = if (twoColumns) Alignment.CenterStart else Alignment.Center,
        ) {
            when (lyricState.status) {
                LyricStatus.LOADING -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = FlareTokens.ClassicVibrantPrimary)
                    }
                }
                LyricStatus.INSTRUMENTAL -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.ft_instrumental_lyrics),
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = if (twoColumns) 24.sp else 20.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = FlareTokens.ClassicVibrantPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                LyricStatus.FAILED -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                stringResource(R.string.ft_lyrics_failed),
                                style = MaterialTheme.typography.bodyLarge,
                                color = FlareTokens.ClassicVibrantSecondary,
                                textAlign = TextAlign.Center,
                            )
                            TextButton(
                                onClick = onRetry,
                                colors = ButtonDefaults.textButtonColors(contentColor = FlareTokens.ClassicIconActive),
                            ) {
                                Text(stringResource(R.string.ft_retry))
                            }
                        }
                    }
                }
                LyricStatus.READY -> if (lyricState.lines.isNotEmpty()) {
                    // Keep lyrics scroll gestures self-contained so scrolling lyrics does not pull the sheet.
                    val absorbLyricsOverscroll = remember {
                        object : NestedScrollConnection {
                            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                                Offset(0f, available.y)

                            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                                Velocity(0f, available.y)
                        }
                    }
                    val lines = lyricState.lines
                    val totalSlots = lines.size
                    val slotKeys = remember(lyricState.songId, lines) {
                        ArrayList<Any>(totalSlots).apply {
                            lines.forEachIndexed { index, line ->
                                add("line:$index:${line.timeSeconds}:${line.text}")
                            }
                        }
                    }
                    val presentations = remember(activeIdx, lines.size, userScrolling, hasIntroInterlude) {
                        ArrayList<LyricLinePresentation>(totalSlots).apply {
                            val effectiveActiveIndex = if (activeIdx < 0 && hasIntroInterlude) 0 else activeIdx
                            for (index in lines.indices) {
                                add(
                                    resolveLyricPresentation(
                                        index = index,
                                        activeIndex = effectiveActiveIndex,
                                        lineCount = lines.size,
                                        userScrolling = userScrolling,
                                    ),
                                )
                            }
                        }
                    }
                    val pushYPxList = remember(presentations, density) {
                        presentations.map { with(density) { it.verticalOffset.toPx() } }
                    }
                    val blurPxList = remember(presentations, density) {
                        presentations.map { with(density) { it.blurRadius.toPx() } }
                    }

                    SubcomposeLayout(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("player_lyrics_list")
                            .clipToBounds()
                            .nestedScroll(absorbLyricsOverscroll)
                            .scrollable(
                                state = scrollableState,
                                orientation = Orientation.Vertical,
                                reverseDirection = false,
                                flingBehavior = ScrollableDefaults.flingBehavior(),
                                interactionSource = interactionSource,
                            )
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            .drawWithContent {
                                drawContent()
                                val edgeMask = Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.10f to Color.Black,
                                    0.82f to Color.Black,
                                    0.98f to Color.Transparent,
                                    1f to Color.Transparent,
                                )
                                drawRect(brush = edgeMask, blendMode = BlendMode.DstIn)
                            },
                    ) { constraints ->
                        // Subscribe to manual drag/fling offset changes inside layout phase.
                        controller.readScrollInvalidation()

                        val viewportWidth = constraints.maxWidth
                        val viewportHeight = constraints.maxHeight
                        val containerHeight = viewportHeight.toFloat()

                        if (
                            sceneTracker.widthPx != 0 &&
                            (sceneTracker.widthPx != viewportWidth || sceneTracker.translation != translationEnabled)
                        ) {
                            controller.invalidateHeights()
                        }

                        val playbackAnchorIdx = if (activeIdx >= 0) {
                            activeIdx.coerceIn(0, totalSlots - 1)
                        } else {
                            0
                        }
                        val anchorIdx = if (userScrolling && sceneTracker.dragAnchorIdx in 0 until totalSlots) {
                            sceneTracker.dragAnchorIdx
                        } else {
                            playbackAnchorIdx
                        }
                        val itemConstraints = Constraints(
                            minWidth = viewportWidth,
                            maxWidth = viewportWidth,
                            minHeight = 0,
                            maxHeight = Constraints.Infinity,
                        )
                        val overscanPx = max(320f, containerHeight * 0.6f)
                        val measuredPlaceables = HashMap<Int, Placeable>()

                        fun buildItems(): List<LyricsSpringLayoutItem> =
                            ArrayList<LyricsSpringLayoutItem>(totalSlots).apply {
                                for (i in 0 until totalSlots) {
                                    add(
                                        LyricsSpringLayoutItem(
                                            index = i,
                                            height = controller.measuredHeightFor(slotKeys[i]) ?: fallbackHeightPx,
                                            presentation = presentations[i],
                                        ),
                                    )
                                }
                            }

                        fun measureSlot(slotIndex: Int) {
                            if (slotIndex in measuredPlaceables) return
                            val key = slotKeys[slotIndex]
                            val hadCachedHeight = controller.measuredHeightFor(key) != null
                            val measurables = subcompose(key) {
                                val lineIndex = slotIndex
                                val line = lines[lineIndex]
                                val isActive = lineIndex == activeIdx
                                val isInterludeCandidate = isActive || (lineIndex == 0 && activeIdx <= 0)
                                val interludeState = if (isInterludeCandidate) {
                                    computeInterludeUiState(
                                        currentTimeSeconds = livePositionMsState.value / 1000.0,
                                        lines = lines,
                                        activeIndex = activeIdx,
                                        lineIndex = lineIndex,
                                        userScrolling = userScrolling,
                                        syncMode = lyricState.syncMode,
                                    )
                                } else {
                                    null
                                }
                                val isInterlude = interludeState?.isInterlude == true
                                val interludeTransition = updateTransition(
                                    targetState = isInterlude,
                                    label = "interlude_host",
                                )
                                val dotsAlpha by interludeTransition.animateFloat(
                                    transitionSpec = {
                                        when {
                                            !animatorsEnabled -> snap()
                                            targetState -> tween(durationMillis = 450, easing = InterludeHostEasing)
                                            else -> keyframes {
                                                durationMillis = 850
                                                1f at 0
                                                1f at 580
                                                0f at 850 using InterludeExitCollapseEasing
                                            }
                                        }
                                    },
                                    label = "interlude_dots_alpha",
                                ) { active -> if (active) 1f else 0f }
                                val dotsScale by interludeTransition.animateFloat(
                                    transitionSpec = {
                                        when {
                                            !animatorsEnabled -> snap()
                                            targetState -> keyframes {
                                                durationMillis = 450
                                                0.94f at 0
                                                1f at 450 using InterludeHostEasing
                                            }
                                            else -> keyframes {
                                                durationMillis = 850
                                                1f at 0
                                                1.20f at 580 using InterludeExitSwellEasing
                                                0.50f at 850 using InterludeExitCollapseEasing
                                            }
                                        }
                                    },
                                    label = "interlude_dots_scale",
                                ) { active -> if (active) 1f else 0.50f }
                                val dotsBlurReveal by interludeTransition.animateFloat(
                                    transitionSpec = {
                                        when {
                                            !animatorsEnabled -> snap()
                                            targetState -> keyframes {
                                                durationMillis = 450
                                                0f at 0
                                                1f at 450 using InterludeHostEasing
                                            }
                                            else -> keyframes {
                                                durationMillis = 850
                                                1f at 0
                                                1f at 580
                                                0.5f at 850 using InterludeExitCollapseEasing
                                            }
                                        }
                                    },
                                    label = "interlude_dots_blur_reveal",
                                ) { active -> if (active) 1f else 0.5f }
                                val lyricRevealProgress by interludeTransition.animateFloat(
                                    transitionSpec = {
                                        when {
                                            !animatorsEnabled -> snap()
                                            targetState -> tween(durationMillis = 450, easing = InterludeHostEasing)
                                            else -> keyframes {
                                                durationMillis = 850
                                                0f at 0
                                                0f at 500
                                                1f at 850 using InterludeHostEasing
                                            }
                                        }
                                    },
                                    label = "interlude_lyric_reveal",
                                ) { active -> if (active) 0f else 1f }
                                var retainedInterlude by remember(key) { mutableStateOf<InterludeUiState?>(null) }
                                if (isInterlude) {
                                    retainedInterlude = interludeState
                                } else if (dotsAlpha <= 0.01f) {
                                    retainedInterlude = null
                                }

                                val clickableModifier = if (!isInterlude) {
                                    line.timeSeconds?.let { sec ->
                                        Modifier.clickable {
                                            scrollGeneration++
                                            sceneTracker.dragAnchorIdx = -1
                                            controller.resetScrollOffset()
                                            userScrolling = false
                                            onSeekTo((sec * 1000.0).toLong().coerceAtLeast(0L))
                                        }
                                    } ?: Modifier
                                } else {
                                    Modifier
                                }

                                val rowBlurPx = controller.visualStateFor(key)?.displayBlurPx ?: blurPxList[slotIndex]
                                val rowBlurDp = with(density) { rowBlurPx.toDp() }
                                val lyricHiddenProgress = (1f - lyricRevealProgress).coerceIn(0f, 1f)
                                val textSwapBlurDp = (6f * lyricHiddenProgress).dp
                                val combinedTextBlurDp = if (textSwapBlurDp > rowBlurDp) textSwapBlurDp else rowBlurDp
                                val dotsSwapBlurDp = (5f * (1f - dotsBlurReveal).coerceIn(0f, 1f)).dp

                                Box(
                                    Modifier.fillMaxWidth()
                                        .padding(start = 4.dp, end = 8.dp)
                                        .testTag("player_lyric_$lineIndex")
                                        .semantics { selected = isActive || (lineIndex == 0 && activeIdx < 0 && isInterlude) }
                                        .then(clickableModifier),
                                ) {
                                    Column(
                                        Modifier.fillMaxWidth()
                                            .graphicsLayer {
                                                alpha = lyricRevealProgress.coerceIn(0f, 1f)
                                                val scale = 1f - 0.03f * lyricHiddenProgress
                                                scaleX = scale
                                                scaleY = scale
                                                transformOrigin = TransformOrigin(0f, 0.5f)
                                            }
                                            .then(
                                                if (Build.VERSION.SDK_INT >= 31 && combinedTextBlurDp > 0.2.dp) {
                                                    Modifier.blur(combinedTextBlurDp, BlurredEdgeTreatment.Unbounded)
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .padding(vertical = 4.dp),
                                    ) {
                                        val wordSynced = isActive && lyricState.syncMode == "word" && line.words.isNotEmpty()
                                        val primaryColor = if (isActive) FlareTokens.ClassicVibrantPrimary else Color.White
                                        val primaryShadow: Shadow? = if (isActive) {
                                            Shadow(color = Color(0x66000000), offset = Offset(0f, 2f), blurRadius = 10f)
                                        } else {
                                            null
                                        }

                                        if (wordSynced) {
                                            KaraokeLyricText(
                                                line = line,
                                                basePositionMs = livePositionMsState.value,
                                                playing = playing && !buffering,
                                                fontSize = primarySize,
                                                lineHeight = primaryLineHeight,
                                                shadow = primaryShadow,
                                            )
                                        } else {
                                            Text(
                                                text = line.text.ifBlank { "…" },
                                                style = MaterialTheme.typography.headlineSmall.copy(
                                                    fontSize = primarySize,
                                                    lineHeight = primaryLineHeight,
                                                    fontWeight = FontWeight.Bold,
                                                    shadow = primaryShadow,
                                                ),
                                                color = primaryColor,
                                            )
                                        }

                                        val translation = if (translationEnabled) line.translation?.trim().orEmpty() else ""
                                        if (translation.isNotBlank()) {
                                            Text(
                                                text = translation,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontSize = secondarySize,
                                                    lineHeight = secondaryLineHeight,
                                                    fontWeight = FontWeight.Normal,
                                                    shadow = primaryShadow,
                                                ),
                                                color = if (isActive) Color(0xCCFFFFFF) else Color(0x8CFFFFFF),
                                                modifier = Modifier.padding(top = 5.dp),
                                            )
                                        }
                                    }

                                    retainedInterlude?.let { activeInterlude ->
                                        if (isInterlude || dotsAlpha > 0.01f) {
                                            val displayInterlude = if (isInterlude) {
                                                activeInterlude
                                            } else {
                                                activeInterlude.copy(
                                                    dotFills = listOf(1f, 1f, 1f),
                                                    dotStates = List(3) { InterludeDotFillState.COMPLETE },
                                                )
                                            }
                                            Box(
                                                Modifier.matchParentSize()
                                                    .graphicsLayer {
                                                        alpha = dotsAlpha.coerceIn(0f, 1f)
                                                        scaleX = dotsScale
                                                        scaleY = dotsScale
                                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                                    }
                                                    .then(
                                                        if (Build.VERSION.SDK_INT >= 31 && dotsSwapBlurDp > 0.2.dp) {
                                                            Modifier.blur(dotsSwapBlurDp, BlurredEdgeTreatment.Unbounded)
                                                        } else {
                                                            Modifier
                                                        },
                                                    ),
                                                contentAlignment = Alignment.CenterStart,
                                            ) {
                                                InterludeDotsRow(
                                                    interludeState = displayInterlude,
                                                    playbackPositionMs = livePositionMsState.value,
                                                    active = isInterlude && playing && !buffering,
                                                    twoColumns = twoColumns,
                                                    interactive = isInterlude,
                                                    onSeekTo = onSeekTo,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            val placeable = measurables.first().measure(itemConstraints)
                            measuredPlaceables[slotIndex] = placeable
                            val measuredH = placeable.height.toFloat()
                            val prevRecorded = controller.recordMeasuredHeight(key, measuredH)
                            if (userScrolling && !hadCachedHeight && prevRecorded == null && slotIndex < anchorIdx) {
                                controller.compensateOffscreenHeightDelta(measuredH - fallbackHeightPx)
                            }
                        }

                        // Pass 1: estimate visible range and measure those rows + anchor.
                        if (containerHeight > 0f) {
                            val roughItems = buildItems()
                            val roughTargets = solveLyricsSpringLayout(
                                items = roughItems,
                                containerHeight = containerHeight,
                                alignOffset = anchorIdx,
                                scrollOffset = controller.currentScrollOffset(),
                                lineSpacing = lineSpacingPx,
                                staggerEnabled = false,
                            )
                            measureSlot(anchorIdx)
                            for (i in 0 until totalSlots) {
                                val t = roughTargets[i]
                                val bounds = controller.estimateRenderBoundsY(slotKeys[i], t.y, t.height)
                                if (bounds.endInclusive >= -overscanPx && bounds.start <= containerHeight + overscanPx) {
                                    measureSlot(i)
                                }
                            }

                            // Pass 2: if measured heights revealed additional rows inside overscan, measure them too.
                            val refinedItems = buildItems()
                            val refinedTargets = solveLyricsSpringLayout(
                                items = refinedItems,
                                containerHeight = containerHeight,
                                alignOffset = anchorIdx,
                                scrollOffset = controller.currentScrollOffset(),
                                lineSpacing = lineSpacingPx,
                                staggerEnabled = false,
                            )
                            var measuredExtra = false
                            for (i in 0 until totalSlots) {
                                if (i in measuredPlaceables) continue
                                val t = refinedTargets[i]
                                val bounds = controller.estimateRenderBoundsY(slotKeys[i], t.y, t.height)
                                if (bounds.endInclusive >= -overscanPx && bounds.start <= containerHeight + overscanPx) {
                                    measureSlot(i)
                                    measuredExtra = true
                                }
                            }

                            val finalItems = if (measuredExtra) buildItems() else refinedItems
                            val limits = computeLyricsScrollLimits(
                                items = finalItems,
                                containerHeight = containerHeight,
                                alignOffset = anchorIdx,
                                lineSpacing = lineSpacingPx,
                            )
                            controller.updateScrollLimits(limits)

                            val scene = when {
                                userScrolling -> LyricsMotionScene.INSTANT
                                sceneTracker.widthPx != 0 && (
                                    sceneTracker.widthPx != viewportWidth ||
                                        sceneTracker.heightPx != viewportHeight ||
                                        sceneTracker.translation != translationEnabled
                                    ) -> LyricsMotionScene.CONTAINER_RESIZE
                                sceneTracker.userScrolling && !userScrolling -> LyricsMotionScene.CONTAINER_RESIZE
                                sceneTracker.activeIdx == Int.MIN_VALUE -> LyricsMotionScene.INSTANT
                                sceneTracker.activeIdx != activeIdx -> {
                                    val jump = if (
                                        sceneTracker.activeIdx < 0 &&
                                        activeIdx in 0..2 &&
                                        (sceneTracker.hadIntro || activeIdx <= 1)
                                    ) {
                                        activeIdx - sceneTracker.activeIdx
                                    } else if (sceneTracker.activeIdx < 0 || activeIdx < 0) {
                                        Int.MAX_VALUE
                                    } else {
                                        kotlin.math.abs(activeIdx - sceneTracker.activeIdx)
                                    }
                                    if (jump in 1..4) LyricsMotionScene.LINE_ADVANCE else LyricsMotionScene.INSTANT
                                }
                                else -> LyricsMotionScene.SPRING
                            }

                            val staggerEnabled = scene == LyricsMotionScene.LINE_ADVANCE && animatorsEnabled
                            val finalTargets = solveLyricsSpringLayout(
                                items = finalItems,
                                containerHeight = containerHeight,
                                alignOffset = anchorIdx,
                                scrollOffset = controller.currentScrollOffset(),
                                lineSpacing = lineSpacingPx,
                                staggerEnabled = staggerEnabled,
                            )

                            anchorSnapshot.key = slotKeys[anchorIdx]
                            anchorSnapshot.baselineY =
                                containerHeight * LyricMotionSpecs.ALIGN_POSITION - finalItems[anchorIdx].height / 2f
                            anchorSnapshot.slotIdx = anchorIdx

                            sceneTracker.activeIdx = activeIdx
                            sceneTracker.hadIntro = hasIntroInterlude
                            sceneTracker.translation = translationEnabled
                            sceneTracker.widthPx = viewportWidth
                            sceneTracker.heightPx = viewportHeight
                            sceneTracker.userScrolling = userScrolling

                            controller.commitTargets(
                                keys = slotKeys,
                                targets = finalTargets,
                                presentations = presentations,
                                pushYPxList = pushYPxList,
                                blurPxList = blurPxList,
                                scene = scene,
                                animatorsEnabled = animatorsEnabled,
                                visibleIndices = measuredPlaceables.keys,
                            )
                        }

                        layout(viewportWidth, viewportHeight) {
                            measuredPlaceables.forEach { (slotIndex, placeable) ->
                                val key = slotKeys[slotIndex]
                                val rowState = controller.visualStateFor(key)
                                val targetY = (rowState?.displayY ?: 0f) + (rowState?.displayPushYPx ?: 0f)
                                val yInt = targetY.roundToInt()
                                val subPixelY = targetY - yInt
                                placeable.placeRelativeWithLayer(0, yInt) {
                                    val liveState = controller.visualStateFor(key)
                                    if (liveState != null) {
                                        val liveY = liveState.displayY + liveState.displayPushYPx
                                        translationY = liveY - yInt
                                        scaleX = liveState.displayScale
                                        scaleY = liveState.displayScale
                                        alpha = liveState.displayOpacity
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                    } else {
                                        translationY = subPixelY
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ClassicLyricsEmptyPlaceholder(song)
                    }
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ClassicLyricsEmptyPlaceholder(song)
                }
            }
        }

        // Web-matching bottom-left lyric tool strip: display preference switch vs independent generation trigger.
        val translationFailed = lyricState.status == LyricStatus.READY &&
            lyricState.translationState == LyricTranslationStatus.FAILED
        if (showBottomDisplayToggle || showGenerateAction || translationNotice != null || translationFailed) {
            val stripModifier = if (twoColumns) {
                Modifier.fillMaxWidth().height(ClassicTransportHeight).padding(start = 4.dp)
            } else {
                Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
            }
            Box(stripModifier, contentAlignment = Alignment.CenterStart) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showBottomDisplayToggle) {
                        ClassicTranslationButton(
                            translationEnabled = translationEnabled,
                            onClick = { onSetTranslationEnabled(!translationEnabled) },
                        )
                    }
                    if (showGenerateAction) {
                        ClassicGenerateTranslationButton(
                            translationState = lyricState.translationState,
                            polling = lyricState.polling,
                            targetLanguage = lyricState.translationTargetLanguage,
                            onClick = onTranslate,
                        )
                    }
                    if (translationNotice != null || translationFailed) {
                        Text(
                            text = stringResource(if (translationNotice != null) R.string.ft_notice_storage_failed else R.string.ft_lyric_translation_failed),
                            style = MaterialTheme.typography.labelSmall,
                            color = FlareTokens.ClassicVibrantSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

internal fun computeKaraokeFocusCharOffset(words: List<LyricWord>, currentSec: Double): Float {
    var cursor = 0
    for (word in words) {
        val len = word.text.length
        if (currentSec <= word.startTimeSeconds) return cursor.toFloat()
        if (currentSec < word.endTimeSeconds) {
            val span = (word.endTimeSeconds - word.startTimeSeconds).coerceAtLeast(0.05)
            val fraction = ((currentSec - word.startTimeSeconds) / span).toFloat().coerceIn(0f, 1f)
            return cursor + fraction * len
        }
        cursor += len
    }
    return cursor.toFloat()
}

@Composable
internal fun KaraokeLyricText(
    line: LyricLine,
    basePositionMs: Long,
    playing: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    shadow: Shadow? = null,
    sungColor: Color = FlareTokens.ClassicVibrantPrimary,
    unsungColor: Color = Color(0x99FFFFFF),
    fontWeight: FontWeight = FontWeight.Bold,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    trackHorizontalFocus: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var displayPosMs by remember(line) { mutableLongStateOf(basePositionMs) }
    val endMs = remember(line) {
        ((line.endTimeSeconds ?: line.words.lastOrNull()?.endTimeSeconds ?: 0.0) * 1000.0).toLong() + 300L
    }
    LaunchedEffect(line, basePositionMs, playing) {
        displayPosMs = basePositionMs
        if (!playing || basePositionMs >= endMs) return@LaunchedEffect
        val startWall = SystemClock.uptimeMillis()
        while (displayPosMs < endMs) {
            delay(40L)
            displayPosMs = basePositionMs + (SystemClock.uptimeMillis() - startWall)
        }
    }

    val currentSec = displayPosMs / 1000.0
    val annotated: AnnotatedString = buildAnnotatedString {
        line.words.forEach { word ->
            val wordColor = when {
                currentSec >= word.endTimeSeconds -> sungColor
                currentSec <= word.startTimeSeconds -> unsungColor
                else -> {
                    val span = (word.endTimeSeconds - word.startTimeSeconds).coerceAtLeast(0.05)
                    val fraction = ((currentSec - word.startTimeSeconds) / span).toFloat().coerceIn(0f, 1f)
                    lerp(unsungColor, sungColor, fraction)
                }
            }
            withStyle(
                SpanStyle(
                    color = wordColor,
                    fontWeight = fontWeight,
                    shadow = shadow,
                ),
            ) {
                append(word.text)
            }
        }
    }
    val textStyle = MaterialTheme.typography.headlineSmall.copy(
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
    )

    if (!trackHorizontalFocus) {
        Text(
            text = annotated,
            style = textStyle,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign,
            modifier = modifier,
        )
        return
    }

    var textLayout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }
    var overflowMetrics by remember(line) { mutableStateOf(0 to 0) }
    val containerW = overflowMetrics.first
    val textW = overflowMetrics.second
    val maxScrollPx = (textW - containerW).coerceAtLeast(0)
    val targetScrollPx = remember(textLayout, containerW, textW, currentSec) {
        val layout = textLayout
        if (layout == null || maxScrollPx <= 0) {
            0f
        } else {
            val totalChars = layout.layoutInput.text.length
            if (totalChars <= 0) {
                0f
            } else {
                val focusChar = computeKaraokeFocusCharOffset(line.words, currentSec)
                    .coerceIn(0f, totalChars.toFloat())
                val lowIdx = floor(focusChar).toInt().coerceIn(0, totalChars)
                val highIdx = ceil(focusChar).toInt().coerceIn(0, totalChars)
                val lowX = layout.getHorizontalPosition(lowIdx, usePrimaryDirection = true)
                val highX = layout.getHorizontalPosition(highIdx, usePrimaryDirection = true)
                val focusX = lowX + (highX - lowX) * (focusChar - lowIdx)
                (focusX - containerW / 2f).coerceIn(0f, maxScrollPx.toFloat())
            }
        }
    }
    val scrollAnim = remember(line) { Animatable(0f) }
    var snappedLine by remember(line) { mutableStateOf(false) }
    LaunchedEffect(line, maxScrollPx, targetScrollPx) {
        if (maxScrollPx <= 0) {
            scrollAnim.snapTo(0f)
            return@LaunchedEffect
        }
        if (!snappedLine) {
            scrollAnim.snapTo(targetScrollPx)
            snappedLine = true
        } else {
            scrollAnim.animateTo(
                targetScrollPx,
                animationSpec = tween(durationMillis = 120, easing = LinearEasing),
            )
        }
    }

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .then(
                if (maxScrollPx > 0) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val edgePx = 14.dp.toPx().coerceAtMost(size.width * 0.16f)
                            val edgeFrac = (edgePx / size.width.coerceAtLeast(1f)).coerceIn(0.01f, 0.25f)
                            val leftFaded = scrollAnim.value > 1f
                            val rightFaded = scrollAnim.value < maxScrollPx - 1f
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Black.copy(alpha = if (leftFaded) 0f else 1f),
                                    edgeFrac to Color.Black,
                                    (1f - edgeFrac) to Color.Black,
                                    1f to Color.Black.copy(alpha = if (rightFaded) 0f else 1f),
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                } else {
                    Modifier
                },
            ),
        content = {
            Text(
                text = annotated,
                style = textStyle,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { textLayout = it },
                modifier = Modifier.graphicsLayer {
                    if (maxScrollPx > 0) {
                        translationX = -scrollAnim.value
                    }
                },
            )
        },
    ) { measurables, constraints ->
        val textPlaceable = measurables.first().measure(
            constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity),
        )
        val viewportW = if (constraints.maxWidth == Constraints.Infinity) {
            textPlaceable.width
        } else {
            constraints.maxWidth
        }
        if (overflowMetrics.first != viewportW || overflowMetrics.second != textPlaceable.width) {
            overflowMetrics = viewportW to textPlaceable.width
        }
        layout(viewportW, textPlaceable.height) {
            val xOffset = if (textPlaceable.width <= viewportW) {
                (viewportW - textPlaceable.width) / 2
            } else {
                0
            }
            textPlaceable.place(xOffset, 0)
        }
    }
}

private fun systemAnimatorsEnabled(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 26) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }

private val InterludeHostEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val InterludeDotFillEasing = CubicBezierEasing(0f, 0.5f, 1f, 0.6f)
private val InterludeExitSwellEasing = CubicBezierEasing(0.5f, 0.1f, 0.5f, 1.0f)
private val InterludeExitCollapseEasing = CubicBezierEasing(0.5f, 0f, 1f, 0.2f)

@Composable
private fun InterludeDotsRow(
    interludeState: InterludeUiState,
    playbackPositionMs: Long,
    active: Boolean,
    twoColumns: Boolean,
    interactive: Boolean = true,
    onSeekTo: (Long) -> Unit,
) {
    val context = LocalContext.current
    val animatorsEnabled = remember(context) { systemAnimatorsEnabled(context) }
    val window = interludeState.window
    val startMs = remember(window) { ((window.startSeconds ?: 0.0) * 1000.0).toLong() }
    val endMs = remember(window) { ((window.endSeconds ?: 0.0) * 1000.0).toLong() }
    var displayPosMs by remember(window, playbackPositionMs) { mutableLongStateOf(playbackPositionMs) }

    LaunchedEffect(window, playbackPositionMs, active, animatorsEnabled) {
        displayPosMs = playbackPositionMs
        if (!active || !animatorsEnabled || !window.active || playbackPositionMs >= endMs) return@LaunchedEffect
        val startWall = SystemClock.uptimeMillis()
        while (displayPosMs < endMs) {
            delay(33L)
            displayPosMs = (playbackPositionMs + (SystemClock.uptimeMillis() - startWall)).coerceAtMost(endMs)
        }
    }

    val liveState = remember(displayPosMs, interludeState, active) {
        if (!active && interludeState.dotStates.all { it == InterludeDotFillState.COMPLETE }) {
            interludeState
        } else {
            val computed = interludeUiFromWindow(displayPosMs / 1000.0, window)
            if (computed.isInterlude) computed else interludeState
        }
    }

    // Apple-inspired whole-row ~4s breath aligned cleanly before the final stage-3 completion tail.
    val totalWindowMs = remember(window, startMs, endMs) { (endMs - startMs).coerceAtLeast(1L) }
    val breatheSpanMs = remember(totalWindowMs) {
        val tailLeadMs = (InterludeConfig.STAGE_3_LEAD_SECONDS * 1000.0).toLong()
        (totalWindowMs - tailLeadMs).coerceAtLeast(totalWindowMs / 2L).coerceAtLeast(1L)
    }
    val breatheCycles = remember(breatheSpanMs) {
        max(1, (breatheSpanMs / 4000.0).roundToInt())
    }
    val rowBreathScale = if (active && animatorsEnabled && window.active) {
        val elapsedMs = (displayPosMs - startMs).coerceAtLeast(0L)
        if (elapsedMs < breatheSpanMs) {
            val cycleMs = breatheSpanMs.toFloat() / breatheCycles
            val u = ((elapsedMs % cycleMs) / cycleMs).coerceIn(0f, 1f)
            val swell = sin(PI * u).toFloat().coerceIn(0f, 1f).pow(1.6f)
            1f + 0.16f * swell
        } else {
            1f
        }
    } else {
        1f
    }

    val dotSize = if (twoColumns) 16.dp else 14.dp
    val containerLabel = stringResource(R.string.ft_interlude_progress, liveState.stage)
    val dotLabels = listOf(
        stringResource(R.string.ft_interlude_dot_stage_1),
        stringResource(R.string.ft_interlude_dot_stage_2),
        stringResource(R.string.ft_interlude_dot_stage_3),
    )

    Row(
        Modifier.padding(vertical = 6.dp)
            .graphicsLayer {
                scaleX = rowBreathScale
                scaleY = rowBreathScale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .then(if (interactive) Modifier.testTag("player_interlude_dots") else Modifier)
            .semantics { if (interactive) contentDescription = containerLabel },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val dotState = liveState.dotStates.getOrElse(index) { InterludeDotFillState.PENDING }
            val dotFill = liveState.dotFills.getOrElse(index) { 0f }.coerceIn(0f, 1f)
            val easedFill = InterludeDotFillEasing.transform(dotFill)
            val targetSec = liveState.dotTimesSeconds?.getOrNull(index)

            // Keep ringed dot aesthetic while adopting Apple's smooth fill curve without 1s per-dot flicker.
            val fillAlpha = when (dotState) {
                InterludeDotFillState.PENDING -> 0.06f
                InterludeDotFillState.FILLING -> 0.24f + 0.76f * easedFill
                InterludeDotFillState.COMPLETE -> 1.0f
            }
            val borderAlpha = when (dotState) {
                InterludeDotFillState.PENDING -> 0.30f
                InterludeDotFillState.FILLING -> 0.50f + 0.46f * easedFill
                InterludeDotFillState.COMPLETE -> 0.96f
            }
            val glowAlpha = when (dotState) {
                InterludeDotFillState.PENDING -> 0f
                InterludeDotFillState.FILLING -> 0.26f * easedFill
                InterludeDotFillState.COMPLETE -> 0.32f
            }

            Box(
                Modifier.size(dotSize),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.requiredSize(38.dp)
                        .then(if (interactive) Modifier.testTag("interlude_dot_$index") else Modifier)
                        .drawBehind {
                            if (glowAlpha > 0.01f) {
                                val dotRadiusPx = dotSize.toPx() * 0.5f
                                val haloRadius = dotRadiusPx * if (dotState == InterludeDotFillState.COMPLETE) {
                                    1.58f
                                } else {
                                    1.36f + 0.22f * easedFill
                                }
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = glowAlpha),
                                            Color.White.copy(alpha = glowAlpha * 0.35f),
                                            Color.White.copy(alpha = 0f),
                                        ),
                                        radius = haloRadius,
                                    ),
                                    radius = haloRadius,
                                )
                            }
                        }
                        .clip(CircleShape)
                        .clickable(
                            enabled = interactive && targetSec != null,
                            role = Role.Button,
                            onClickLabel = dotLabels[index],
                        ) {
                            targetSec?.let { onSeekTo((it * 1000.0).toLong().coerceAtLeast(0L)) }
                        }
                        .semantics {
                            if (interactive) {
                                contentDescription = dotLabels[index]
                                selected = dotState == InterludeDotFillState.COMPLETE
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(dotSize)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = fillAlpha))
                            .border(1.25.dp, Color.White.copy(alpha = borderAlpha), CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassicTranslationButton(
    translationEnabled: Boolean,
    onClick: () -> Unit,
) {
    ClassicGlyphButton(
        icon = ClientIcons.Translate,
        description = stringResource(R.string.ft_translate_lyrics),
        onClick = onClick,
        selected = translationEnabled,
        iconSize = 22.dp,
        buttonSize = 48.dp,
        modifier = Modifier.testTag("player_translate_button"),
    )
}

@Composable
private fun ClassicGenerateTranslationButton(
    translationState: LyricTranslationStatus,
    polling: Boolean,
    targetLanguage: String?,
    onClick: () -> Unit,
) {
    val pending = translationState == LyricTranslationStatus.PENDING || polling
    val failed = translationState == LyricTranslationStatus.FAILED
    val displayLocale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: java.util.Locale.ROOT
    val targetName = remember(targetLanguage, displayLocale) {
        targetLanguage?.replace('_', '-')?.let(java.util.Locale::forLanguageTag)
            ?.takeIf { it.language in java.util.Locale.getISOLanguages() }
            ?.getDisplayName(displayLocale)
            ?.takeIf(String::isNotBlank)
    }
    val label = if (!pending && !failed && targetName != null) stringResource(R.string.ft_translate_to_language, targetName) else stringResource(
        when {
            pending -> R.string.ft_translating_lyrics
            failed -> R.string.ft_retry
            else -> R.string.ft_request_translation
        },
    )
    val tint = FlareTokens.ClassicIcon

    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !pending, onClick = onClick)
            .testTag("player_generate_translation")
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (pending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.6.dp,
                    color = tint,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = tint,
            )
        }
    }
}

@Composable
private fun ClassicLyricsEmptyPlaceholder(song: Song) {
    Column(
        Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(ClientIcons.Lyrics, null, Modifier.size(28.dp), tint = FlareTokens.ClassicVibrantSecondary)
        Text(
            stringResource(R.string.ft_lyrics),
            style = MaterialTheme.typography.titleMedium,
            color = FlareTokens.ClassicVibrantPrimary,
        )
        Text(
            stringResource(R.string.ft_lyrics_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = FlareTokens.ClassicVibrantSecondary,
            textAlign = TextAlign.Center,
        )
        if (song.album.isNotBlank()) {
            Text(
                "${song.title} · ${song.album}",
                style = MaterialTheme.typography.labelMedium,
                color = FlareTokens.ClassicVibrantTertiary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ClassicSongMetadataRow(
    song: Song,
    showLyricsButton: Boolean,
    onToggleLyrics: () -> Unit,
    lyricsActive: Boolean = false,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clipToBounds().padding(end = 10.dp)) {
            key(song.id) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold,
                    ),
                    color = FlareTokens.ClassicTitle,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                )
                val meta = listOf(
                    song.artist.ifBlank { stringResource(R.string.ft_unknown_artist) },
                    song.album,
                ).filter { it.isNotBlank() }.joinToString(" — ")
                Text(
                    meta,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = FlareTokens.ClassicSubtitle,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp).basicMarquee(iterations = Int.MAX_VALUE),
                )
            }
        }
        if (showLyricsButton) {
            ClassicGlyphButton(
                icon = ClientIcons.Lyrics,
                description = stringResource(R.string.ft_lyrics),
                onClick = onToggleLyrics,
                selected = lyricsActive,
                iconSize = 20.dp,
                buttonSize = 44.dp,
                modifier = Modifier.testTag("player_lyrics_toggle"),
            )
        }
        PlayerMoreMenu(song = song)
    }
}

@Composable
private fun ClassicPlayerTransportPanel(
    state: PlaybackSnapshot,
    actions: ClientActions,
    onQueue: () -> Unit,
    compactVertical: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val song = state.current ?: return
    var dragging by remember(song.id) { mutableStateOf<Float?>(null) }
    LaunchedEffect(state.durationMs) {
        if (state.durationMs <= 0) dragging = null
    }
    val duration = state.durationMs.coerceAtLeast(0)
    val position = (dragging?.toLong() ?: state.positionMs).coerceIn(0, duration)

    Column(modifier) {
        if (state.failed) {
            Text(
                stringResource(R.string.ft_playback_error),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFCA5A5),
                modifier = Modifier.padding(top = 10.dp),
            )
            TextButton(
                onClick = actions::retryPlayback,
                colors = ButtonDefaults.textButtonColors(contentColor = FlareTokens.ClassicIconActive),
                modifier = Modifier.heightIn(min = 48.dp).testTag("playback_retry"),
            ) { Text(stringResource(R.string.ft_retry)) }
        }

        // Inline left-time / streamlined dotless track / right-time matching web classic-controls__timeline
        val timeStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(playerTime(position), style = timeStyle, color = FlareTokens.ClassicTime)
            StreamlinedTrackBar(
                value = position.toFloat(),
                range = 0f..duration.coerceAtLeast(1).toFloat(),
                enabled = duration > 0,
                description = stringResource(R.string.ft_seek_position),
                onChange = { dragging = it },
                onFinished = { target ->
                    actions.seekTo(target.toLong().coerceIn(0, duration))
                    dragging = null
                },
                modifier = Modifier.weight(1f).testTag("player_seek"),
            )
            Text(playerTime(duration), style = timeStyle, color = FlareTokens.ClassicTime)
        }

        if (!compactVertical) {
            Spacer(Modifier.height(6.dp))
        }
        ClassicTransportRow(state, actions, onQueue, compact = compactVertical)
    }
}

@Composable
private fun ClassicPlayerControls(
    state: PlaybackSnapshot,
    actions: ClientActions,
    onQueue: () -> Unit,
    showLyricsButton: Boolean,
    onToggleLyrics: () -> Unit,
    hideMetadata: Boolean,
    modifier: Modifier = Modifier,
    lyricsActive: Boolean = false,
    compactVertical: Boolean = false,
) {
    val song = state.current ?: return
    Column(modifier) {
        if (!hideMetadata) {
            ClassicSongMetadataRow(
                song = song,
                showLyricsButton = showLyricsButton,
                onToggleLyrics = onToggleLyrics,
                lyricsActive = lyricsActive,
            )
            Spacer(Modifier.height(10.dp))
        }
        ClassicPlayerTransportPanel(
            state = state,
            actions = actions,
            onQueue = onQueue,
            compactVertical = compactVertical,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private enum class ClassicPlaybackMode { SEQUENCE, LOOP, SINGLE, RANDOM }

private fun currentPlaybackMode(state: PlaybackSnapshot): ClassicPlaybackMode = when {
    state.shuffle -> ClassicPlaybackMode.RANDOM
    state.repeatMode == 2 -> ClassicPlaybackMode.LOOP
    state.repeatMode == 1 -> ClassicPlaybackMode.SINGLE
    else -> ClassicPlaybackMode.SEQUENCE
}

private fun nextPlaybackMode(mode: ClassicPlaybackMode): ClassicPlaybackMode = when (mode) {
    ClassicPlaybackMode.SEQUENCE -> ClassicPlaybackMode.LOOP
    ClassicPlaybackMode.LOOP -> ClassicPlaybackMode.SINGLE
    ClassicPlaybackMode.SINGLE -> ClassicPlaybackMode.RANDOM
    ClassicPlaybackMode.RANDOM -> ClassicPlaybackMode.SEQUENCE
}

private fun playbackModeLabelRes(mode: ClassicPlaybackMode): Int = when (mode) {
    ClassicPlaybackMode.SEQUENCE -> R.string.ft_mode_sequence
    ClassicPlaybackMode.LOOP -> R.string.ft_repeat_all
    ClassicPlaybackMode.SINGLE -> R.string.ft_repeat_one
    ClassicPlaybackMode.RANDOM -> R.string.ft_shuffle_on
}

private fun advancePlaybackMode(state: PlaybackSnapshot, actions: ClientActions): ClassicPlaybackMode {
    val next = nextPlaybackMode(currentPlaybackMode(state))
    val (targetShuffle, targetRepeat) = when (next) {
        ClassicPlaybackMode.SEQUENCE -> false to 0
        ClassicPlaybackMode.LOOP -> false to 2
        ClassicPlaybackMode.SINGLE -> false to 1
        ClassicPlaybackMode.RANDOM -> true to 0
    }
    actions.setPlaybackMode(
        shuffle = targetShuffle,
        repeatMode = targetRepeat,
        currentShuffle = state.shuffle,
        currentRepeatMode = state.repeatMode,
    )
    return next
}

@Composable
private fun ClassicTransportRow(
    state: PlaybackSnapshot,
    actions: ClientActions,
    onQueue: () -> Unit,
    compact: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val listeningInteractions = LocalListeningInteractions.current
    val playing = state.playing || state.buffering
    val playState = stringResource(
        when {
            state.buffering -> R.string.ft_buffering
            state.playing -> R.string.ft_playing
            else -> R.string.ft_paused
        },
    )
    val mode = currentPlaybackMode(state)
    val nextMode = nextPlaybackMode(mode)
    val modeDescription = stringResource(playbackModeLabelRes(mode))
    val nextModeDescription = stringResource(playbackModeLabelRes(nextMode))
    val modeIcon = when (mode) {
        ClassicPlaybackMode.SEQUENCE -> ClientIcons.Sequence
        ClassicPlaybackMode.LOOP -> ClientIcons.Repeat
        ClassicPlaybackMode.SINGLE -> ClientIcons.RepeatOne
        ClassicPlaybackMode.RANDOM -> ClientIcons.Shuffle
    }

    val rowHeight = if (compact) ClassicCompactTransportHeight else ClassicTransportHeight
    val playButtonSize = if (compact) 56.dp else 64.dp
    val playIconSize = if (compact) 32.dp else 36.dp

    Row(
        Modifier.fillMaxWidth().heightIn(min = rowHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EdgeSlotIconButton(
            slotWidth = 22.dp,
            slotHeight = 48.dp,
            touchSize = 48.dp,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                advancePlaybackMode(state, actions)
                listeningInteractions?.feedback?.invoke(nextModeDescription)
            },
            contentDescription = modeDescription,
            selected = mode != ClassicPlaybackMode.SEQUENCE,
            modifier = Modifier.testTag("player_mode")
                .semantics {
                    stateDescription = modeDescription
                    contentDescription = modeDescription
                },
        ) {
            Icon(
                modeIcon, null, Modifier.size(22.dp),
                tint = if (mode != ClassicPlaybackMode.SEQUENCE) FlareTokens.ClassicIconActive else FlareTokens.ClassicIcon,
            )
        }
        ClassicGlyphButton(
            icon = ClientIcons.Previous,
            description = stringResource(R.string.ft_previous),
            onClick = actions::previous,
            enabled = state.currentIndex > 0 || state.positionMs > 3000 ||
                state.repeatMode == 2 || (state.shuffle && state.queue.size > 1),
            iconSize = 28.dp,
            buttonSize = 48.dp,
            modifier = Modifier.testTag("player_previous"),
        )
        // Web classic player: pure solid rounded play/pause glyph without heavy circular background
        IconButton(
            onClick = actions::togglePlay,
            modifier = Modifier.size(playButtonSize).testTag("player_play")
                .semantics { stateDescription = playState },
        ) {
            if (state.buffering) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        Modifier.size(44.dp), strokeWidth = 2.dp,
                        color = Color.White.copy(alpha = 0.45f),
                    )
                    Icon(
                        ClientIcons.Pause, stringResource(R.string.ft_pause),
                        modifier = Modifier.size(28.dp), tint = FlareTokens.ClassicPlayGlyph,
                    )
                }
            } else {
                Icon(
                    if (playing) ClientIcons.Pause else ClientIcons.Play,
                    stringResource(if (playing) R.string.ft_pause else R.string.ft_play),
                    modifier = Modifier.size(playIconSize), tint = FlareTokens.ClassicPlayGlyph,
                )
            }
        }
        ClassicGlyphButton(
            icon = ClientIcons.Next,
            description = stringResource(R.string.ft_next),
            onClick = actions::next,
            enabled = state.currentIndex < state.queue.lastIndex || state.repeatMode == 2 ||
                (state.shuffle && state.queue.size > 1),
            iconSize = 28.dp,
            buttonSize = 48.dp,
            modifier = Modifier.testTag("player_next"),
        )
        EdgeSlotIconButton(
            slotWidth = 22.dp,
            slotHeight = 48.dp,
            touchSize = 48.dp,
            onClick = onQueue,
            contentDescription = stringResource(R.string.ft_queue),
            modifier = Modifier.testTag("open_queue"),
        ) {
            Icon(
                ClientIcons.Queue, stringResource(R.string.ft_queue),
                Modifier.size(22.dp), tint = FlareTokens.ClassicIcon,
            )
        }
    }
}

@Composable
private fun PlayerMoreMenu(song: Song) {
    val listeningInteractions = LocalListeningInteractions.current
    EdgeSlotIconButton(
        slotWidth = 24.dp,
        slotHeight = 48.dp,
        touchSize = 48.dp,
        enabled = listeningInteractions != null,
        onClick = { listeningInteractions?.song(song) },
        contentDescription = stringResource(R.string.ft_listening_song_actions),
        modifier = Modifier.testTag("player_more"),
    ) {
        Icon(
            ClientIcons.More,
            stringResource(R.string.ft_listening_song_actions),
            modifier = Modifier.size(24.dp),
            tint = FlareTokens.ClassicIcon,
        )
    }
}

/**
 * Keeps a 48dp circular touch target while aligning the visible icon slot flush with outer column edges.
 */
@Composable
private fun EdgeSlotIconButton(
    slotWidth: Dp,
    slotHeight: Dp,
    touchSize: Dp,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier.size(width = slotWidth, height = slotHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = modifier
                .requiredSize(touchSize)
                .clip(CircleShape)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = contentDescription,
                    onClick = onClick,
                )
                .semantics {
                    this.selected = selected
                },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
private fun ClassicGlyphButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    iconSize: Dp = 22.dp,
    buttonSize: Dp = 48.dp,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(buttonSize).semantics { this.selected = selected },
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = if (selected) FlareTokens.ClassicIconActive else FlareTokens.ClassicIcon,
            disabledContentColor = Color.White.copy(alpha = 0.25f),
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(iconSize))
            if (selected) {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp)
                        .size(4.dp).background(FlareTokens.ClassicIconActive, CircleShape),
                )
            }
        }
    }
}

/**
 * Streamlined dotless track bar modeled after web `.classic-controls__track`:
 * 3.5dp resting height -> 5dp while scrubbing, 48dp touch height, full semantics SetProgress support.
 */
@Composable
private fun StreamlinedTrackBar(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    description: String,
    onChange: (Float) -> Unit,
    onFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val span = (range.endInclusive - range.start).coerceAtLeast(1e-4f)
    val clamped = value.coerceIn(range.start, range.endInclusive)
    val fraction = ((clamped - range.start) / span).coerceIn(0f, 1f)
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    var activeDrag by remember { mutableStateOf(false) }
    var latestValue by remember { mutableFloatStateOf(clamped) }
    val haptic = LocalHapticFeedback.current
    val trackHeight by animateDpAsState(if (activeDrag) 5.dp else 3.5.dp, tween(160), label = "track_height")

    fun valueForOffset(x: Float): Float {
        val ratio = (x / trackWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        return range.start + ratio * span
    }

    Box(
        modifier
            .heightIn(min = 48.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .semantics {
                contentDescription = description
                setProgress { target ->
                    if (!enabled) return@setProgress false
                    val next = target.coerceIn(range.start, range.endInclusive)
                    onChange(next)
                    onFinished(next)
                    true
                }
            }
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(range) {
                            detectTapGestures { offset ->
                                val next = valueForOffset(offset.x)
                                onChange(next)
                                onFinished(next)
                            }
                        }
                        .pointerInput(range) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    activeDrag = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    latestValue = valueForOffset(offset.x)
                                    onChange(latestValue)
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    latestValue = valueForOffset(change.position.x)
                                    onChange(latestValue)
                                },
                                onDragEnd = {
                                    activeDrag = false
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onFinished(latestValue)
                                },
                                onDragCancel = {
                                    activeDrag = false
                                    onFinished(latestValue)
                                },
                            )
                        }
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxWidth().height(trackHeight)
                .clip(CircleShape)
                .background(FlareTokens.ClassicTrackInactive),
        ) {
            Box(
                Modifier.fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(
                        if (enabled) FlareTokens.ClassicTrackActive
                        else FlareTokens.ClassicTrackActive.copy(alpha = 0.35f),
                        CircleShape,
                    ),
            )
        }
    }
}

private data class ClassicBackdropPalette(
    val dominant: Color,
    val secondary: Color,
    val deep: Color,
) {
    companion object {
        val Default = ClassicBackdropPalette(
            dominant = Color(0xFF343238),
            secondary = Color(0xFF4A464C),
            deep = FlareTokens.ClassicBase,
        )
    }
}

private suspend fun extractBackdropPalette(bitmap: Bitmap): ClassicBackdropPalette =
    withContext(Dispatchers.Default) {
        val scaled = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        val pixels = IntArray(32 * 32)
        scaled.getPixels(pixels, 0, 32, 0, 0, 32, 32)
        if (scaled !== bitmap) scaled.recycle()

        var sumR = 0f
        var sumG = 0f
        var sumB = 0f
        var weight = 0f
        var bestR = -1f
        var bestG = -1f
        var bestB = -1f
        var maxVibrance = -1f

        for (px in pixels) {
            val a = android.graphics.Color.alpha(px)
            if (a < 128) continue
            val r = android.graphics.Color.red(px).toFloat()
            val g = android.graphics.Color.green(px).toFloat()
            val b = android.graphics.Color.blue(px).toFloat()
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val delta = maxC - minC
            val lightness = (maxC + minC) / 510f
            val chromaWeight = 0.55f + (delta / 255f) * 0.45f
            sumR += r * chromaWeight
            sumG += g * chromaWeight
            sumB += b * chromaWeight
            weight += chromaWeight

            if (lightness < 0.12f || lightness > 0.88f || delta < 35f) continue
            val saturation = delta / (1f - kotlin.math.abs(2f * lightness - 1f) + 0.001f)
            val vibrance = saturation * 2f + (maxC / 255f)
            if (vibrance > maxVibrance) {
                maxVibrance = vibrance
                bestR = r
                bestG = g
                bestB = b
            }
        }
        if (weight <= 0f) return@withContext ClassicBackdropPalette.Default
        val avgR = (sumR / weight).coerceIn(0f, 255f)
        val avgG = (sumG / weight).coerceIn(0f, 255f)
        val avgB = (sumB / weight).coerceIn(0f, 255f)
        val domR = (if (bestR >= 0f) bestR else avgR).roundToInt().coerceIn(0, 255)
        val domG = (if (bestG >= 0f) bestG else avgG).roundToInt().coerceIn(0, 255)
        val domB = (if (bestB >= 0f) bestB else avgB).roundToInt().coerceIn(0, 255)
        val secR = (avgR * 1.18f).roundToInt().coerceIn(0, 255)
        val secG = (avgG * 1.18f).roundToInt().coerceIn(0, 255)
        val secB = (avgB * 1.18f).roundToInt().coerceIn(0, 255)
        val deepR = (avgR * 0.14f).roundToInt().coerceIn(8, 255)
        val deepG = (avgG * 0.14f).roundToInt().coerceIn(8, 255)
        val deepB = (avgB * 0.14f).roundToInt().coerceIn(8, 255)
        ClassicBackdropPalette(
            dominant = Color(domR, domG, domB),
            secondary = Color(secR, secG, secB),
            deep = Color(deepR, deepG, deepB),
        )
    }

@Composable
private fun ClassicPlayerBackdrop(song: Song, imageLoader: ImageLoader?, atmospheric: Boolean) {
    val context = LocalContext.current
    var palette by remember { mutableStateOf(ClassicBackdropPalette.Default) }
    val animDominant by animateColorAsState(palette.dominant, tween(650), label = "backdrop_dominant")
    val animSecondary by animateColorAsState(palette.secondary, tween(650), label = "backdrop_secondary")
    val animDeep by animateColorAsState(palette.deep, tween(650), label = "backdrop_deep")

    LaunchedEffect(song.id, song.coverUrl, atmospheric, imageLoader) {
        if (!atmospheric || imageLoader == null || song.coverUrl.isNullOrBlank()) {
            palette = ClassicBackdropPalette.Default
            return@LaunchedEffect
        }
        try {
            val request = ImageRequest.Builder(context)
                .data(song.coverUrl)
                .size(96)
                .allowHardware(false)
                .build()
            val result = imageLoader.execute(request)
            val bmp = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            if (bmp != null) {
                palette = extractBackdropPalette(bmp)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            palette = ClassicBackdropPalette.Default
        }
    }

    val saturationFilter = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(2.0f) })
    }
    val base = if (atmospheric) FlareTokens.ClassicBase else FlareTokens.ClassicSolidBase
    Box(Modifier.fillMaxSize().clipToBounds().background(if (atmospheric) FlareTokens.ClassicFallbackBase else base)) {
        if (atmospheric) Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    listOf(
                        animDominant.copy(alpha = 0.85f),
                        animSecondary.copy(alpha = 0.65f),
                        animDeep,
                    ),
                ),
            ),
        )
        if (atmospheric && imageLoader != null) {
            Crossfade(
                targetState = song.coverUrl.orEmpty(),
                animationSpec = tween(700),
                label = "backdrop_cover_crossfade",
            ) { cover ->
                val layerModifier = Modifier.fillMaxSize().let {
                    if (Build.VERSION.SDK_INT >= 31) it.blur(50.dp, BlurredEdgeTreatment.Unbounded) else it
                }
                val coverRequest = remember(context, cover) {
                    ImageRequest.Builder(context).data(cover).size(256).build()
                }
                // A single static, bounded cover layer preserves atmosphere without continuously
                // invalidating several full-screen blur targets during lyric scrolling.
                Box(layerModifier) {
                    AsyncImage(
                        model = coverRequest,
                        imageLoader = imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.66f,
                        colorFilter = saturationFilter,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = 1.25f
                            scaleY = 1.25f
                        },
                    )
                }
            }
            // Calibrated readability gradient veils preserving ambient cover warmth without muddying typography.
            Box(Modifier.fillMaxSize().background(FlareTokens.ClassicLegibilityScrim.copy(alpha = 0.42f)))
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to palette.deep.copy(alpha = 0.34f),
                        0.45f to Color.Black.copy(alpha = 0.28f),
                        1f to palette.deep.copy(alpha = 0.72f),
                    ),
                ),
            )
        }
    }
}

private fun playerTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    val minutes = seconds / 60
    return "${minutes}:${(seconds % 60).toString().padStart(2, '0')}"
}
