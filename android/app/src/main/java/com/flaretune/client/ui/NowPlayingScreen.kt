package com.flaretune.client.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
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
import com.flaretune.client.model.Song
import com.flaretune.client.ui.theme.FlareTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class ClassicMobilePane { SONG, LYRICS }
private val ClassicTransportHeight = 68.dp

/**
 * Full-screen player modeled after the web classic player (`MobileClassicPlayer` / `ClassicDesktopLayout`):
 * - Always calm dark-ambient cover wash to preserve soft-white typography contrast
 * - Animated playing/paused cover scale & phone Song <-> Lyrics view toggle
 * - Streamlined dotless progress/volume tracks and 5-button transport row
 */
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
    var mobilePane by rememberSaveable(song.id) { mutableStateOf(ClassicMobilePane.SONG) }
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
            val bodyHeight = (screenMaxHeight - 44.dp).coerceAtLeast(0.dp)
            val pageWidth = screenMaxWidth.coerceAtMost(1320.dp)
            val narrowContentWidth = (screenMaxWidth - 44.dp).coerceAtLeast(0.dp).coerceAtMost(440.dp)
            val portraitArtSize = narrowContentWidth.coerceAtMost((screenMaxHeight - 310.dp).coerceAtLeast(216.dp))

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
                    AnimatedContent(
                        targetState = mobilePane,
                        transitionSpec = {
                            (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 18 }) togetherWith
                                (fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 18 })
                        },
                        label = "classic_mobile_pane",
                    ) { pane ->
                        if (pane == ClassicMobilePane.LYRICS) {
                            Column(
                                Modifier.width(narrowContentWidth).heightIn(min = bodyHeight)
                                    .padding(top = 4.dp, bottom = 20.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                ClassicLyricsTopRow(
                                    song = song,
                                    imageLoader = imageLoader,
                                    onBackToArtwork = { mobilePane = ClassicMobilePane.SONG },
                                )
                                ClassicLyricsViewport(
                                    song = song,
                                    lyricState = lyricState,
                                    translationEnabled = translationEnabled,
                                    translationNotice = translationNotice,
                                    playbackPositionMs = state.positionMs,
                                    playing = state.playing,
                                    buffering = state.buffering,
                                    twoColumns = false,
                                    onSeekTo = actions::seekTo,
                                    onRetry = actions::retryLyrics,
                                    onSetTranslationEnabled = actions::setTranslationEnabled,
                                    onTranslate = actions::requestLyricTranslation,
                                    modifier = Modifier.fillMaxWidth()
                                        .height((screenMaxHeight - 300.dp).coerceAtLeast(220.dp))
                                        .padding(vertical = 8.dp),
                                )
                                ClassicPlayerControls(
                                    state = state,
                                    actions = actions,
                                    onQueue = onQueue,
                                    showLyricsButton = true,
                                    lyricsActive = true,
                                    onToggleLyrics = { mobilePane = ClassicMobilePane.SONG },
                                    hideMetadata = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else {
                            Column(
                                Modifier.width(narrowContentWidth).heightIn(min = bodyHeight)
                                    .padding(top = 8.dp, bottom = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().weight(1f, fill = false),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    ClassicArtwork(
                                        song = song,
                                        imageLoader = imageLoader,
                                        playing = state.playing,
                                        buffering = state.buffering,
                                        modifier = Modifier.size(portraitArtSize),
                                        onClick = { mobilePane = ClassicMobilePane.LYRICS },
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    ClassicLyricPreviewStrip(
                                        song = song,
                                        lyricState = lyricState,
                                        translationEnabled = translationEnabled,
                                        onClick = { mobilePane = ClassicMobilePane.LYRICS },
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                ClassicPlayerControls(
                                    state = state,
                                    actions = actions,
                                    onQueue = onQueue,
                                    showLyricsButton = true,
                                    lyricsActive = false,
                                    onToggleLyrics = { mobilePane = ClassicMobilePane.LYRICS },
                                    hideMetadata = false,
                                    modifier = Modifier.fillMaxWidth(),
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
            .height(44.dp)
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
        // Compact 38dp x 38dp collapse target with crisp 36dp x 4dp capsule pill (modeled after Musiver).
        Box(
            Modifier.size(38.dp)
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

@Composable
private fun ClassicArtwork(
    song: Song,
    imageLoader: ImageLoader?,
    playing: Boolean,
    buffering: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val activePlaying = playing && !buffering
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
    Box(
        modifier
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

@Composable
private fun ClassicLyricPreviewStrip(
    song: Song,
    lyricState: LyricState,
    translationEnabled: Boolean,
    onClick: () -> Unit,
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
    val line2 = when (lyricState.status) {
        LyricStatus.READY -> activeLine?.translation?.takeIf { translationEnabled && it.isNotBlank() } ?: nextLine?.text.orEmpty()
        else -> ""
    }

    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = Triple(activeIdx, line1, line2),
            transitionSpec = {
                (slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(220))) togetherWith
                    (slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(160)))
            },
            label = "classic_preview_strip",
        ) { (_, primary, secondary) ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = primary,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = if (lyricState.status == LyricStatus.READY) 17.sp else 13.sp,
                        fontWeight = if (lyricState.status == LyricStatus.READY) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (lyricState.status == LyricStatus.READY) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.56f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (secondary.isNotBlank()) {
                    Text(
                        text = secondary,
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

@Composable
private fun ClassicLyricsTopRow(
    song: Song,
    imageLoader: ImageLoader?,
    onBackToArtwork: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                .clickable(onClick = onBackToArtwork),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ClientIcons.Music, null, Modifier.size(22.dp), tint = FlareTokens.ClassicSubtitle)
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
        Column(
            Modifier.weight(1f).clipToBounds().clickable(onClick = onBackToArtwork),
        ) {
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
) {
    val listState = key(lyricState.songId) { rememberLazyListState() }
    val waveController = remember(lyricState.songId) { LyricsWaveSpringController() }
    val context = LocalContext.current
    val animatorsEnabled = remember(context) { systemAnimatorsEnabled(context) }
    val density = LocalDensity.current
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var userScrolling by remember(lyricState.songId) { mutableStateOf(false) }

    var lastScrollSongId by remember { mutableStateOf<String?>(null) }
    var lastScrollActiveIdx by remember(lyricState.songId) { mutableIntStateOf(-1) }
    var lastScrollTranslation by remember(lyricState.songId) { mutableStateOf(translationEnabled) }
    var lastScrollViewportHeight by remember(lyricState.songId) { mutableIntStateOf(0) }
    var lastUserScrolling by remember(lyricState.songId) { mutableStateOf(false) }

    // Programmatic scrolls must never start the user's browsing pause or cancel themselves.
    LaunchedEffect(listState) {
        var resume: Job? = null
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    resume?.cancel()
                    waveController.clear()
                    userScrolling = true
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    resume?.cancel()
                    resume = launch {
                        snapshotFlow { listState.isScrollInProgress }.first { !it }
                        delay(LyricMotionSpecs.USER_SCROLL_DEBOUNCE_MS)
                        userScrolling = false
                    }
                }
            }
        }
    }

    val activeIdx = lyricState.activeLyricIndex
    val introInterludeState = remember(
        playbackPositionMs,
        lyricState.lines,
        activeIdx,
        userScrolling,
        lyricState.syncMode,
    ) {
        if (lyricState.status == LyricStatus.READY && lyricState.lines.isNotEmpty()) {
            computeInterludeUiState(
                currentTimeSeconds = playbackPositionMs / 1000.0,
                lines = lyricState.lines,
                activeIndex = activeIdx,
                lineIndex = 0,
                userScrolling = userScrolling,
                syncMode = lyricState.syncMode,
            ).takeIf { it.isInterlude && it.window.kind == InterludeKind.INTRO }
        } else null
    }
    val introSlotOffset = if (introInterludeState != null) 1 else 0

    LaunchedEffect(
        lyricState.activeLyricIndex,
        lyricState.songId,
        lyricState.status,
        lyricState.lines,
        translationEnabled,
        userScrolling,
        viewportHeightPx,
        introSlotOffset,
    ) {
        if (userScrolling) {
            waveController.clear()
            lastUserScrolling = true
            return@LaunchedEffect
        }
        if (lyricState.status != LyricStatus.READY || lyricState.activeLyricIndex < 0 || lyricState.lines.isEmpty()) {
            waveController.clear()
            lastScrollSongId = lyricState.songId
            lastScrollActiveIdx = -1
            lastScrollTranslation = translationEnabled
            lastScrollViewportHeight = viewportHeightPx
            lastUserScrolling = false
            return@LaunchedEffect
        }

        val target = lyricState.activeLyricIndex.coerceIn(0, lyricState.lines.lastIndex)
        val listTarget = target + introSlotOffset
        // Let translated rows finish measuring before calculating their actual distance.
        withFrameNanos { }

        val visible = listState.layoutInfo.visibleItemsInfo
        val item = visible.firstOrNull { it.index == listTarget }
        val isResizeOrResume = lastScrollTranslation != translationEnabled ||
            lastScrollViewportHeight != viewportHeightPx ||
            (lastUserScrolling && !userScrolling)
        val isSmallLineAdvance = !isResizeOrResume &&
            lastScrollSongId == lyricState.songId &&
            lastScrollActiveIdx >= 0 &&
            kotlin.math.abs(target - lastScrollActiveIdx) in 1..3 &&
            item != null

        lastScrollSongId = lyricState.songId
        lastScrollActiveIdx = target
        lastScrollTranslation = translationEnabled
        lastScrollViewportHeight = viewportHeightPx
        lastUserScrolling = false

        if (item == null) {
            waveController.clear()
            listState.scrollToItem(listTarget, 0)
        } else if (kotlin.math.abs(item.offset.toFloat()) >= LyricMotionSpecs.SCROLL_DEADZONE_PX) {
            val deltaPx = item.offset.toFloat()
            when {
                isResizeOrResume && animatorsEnabled -> {
                    waveController.clear()
                    listState.animateScrollBy(deltaPx, LyricMotionSpecs.ResizeTween)
                }
                isSmallLineAdvance && animatorsEnabled -> {
                    val minIdx = ((visible.firstOrNull()?.index ?: listTarget) - 2).coerceAtLeast(0)
                    val maxIdx = (visible.lastOrNull()?.index ?: listTarget) + 4
                    listState.scrollToItem(listTarget, 0)
                    waveController.animateLineAdvance(
                        deltaPx = deltaPx,
                        anchorListIndex = listTarget,
                        visibleIndices = minIdx..maxIdx,
                    )
                }
                else -> {
                    waveController.clear()
                    listState.scrollToItem(listTarget, 0)
                }
            }
        }
    }

    val topFocusPad = with(density) { (viewportHeightPx * 0.30f).toDp() }.coerceAtLeast(if (twoColumns) 72.dp else 44.dp)
    val bottomFocusPad = with(density) { (viewportHeightPx * 0.70f).toDp() }.coerceAtLeast(if (twoColumns) 128.dp else 88.dp)
    val primarySize = if (twoColumns) 30.sp else 26.sp
    val primaryLineHeight = if (twoColumns) 39.sp else 34.sp
    val secondarySize = if (twoColumns) 18.sp else 16.sp
    val secondaryLineHeight = if (twoColumns) 26.sp else 23.sp

    val hasInlineTranslation = remember(lyricState.lines) {
        lyricState.lines.any { !it.translation.isNullOrBlank() }
    }
    val showDisplayToggle = lyricState.status == LyricStatus.READY &&
        lyricState.translationState != LyricTranslationStatus.NOT_NEEDED && hasInlineTranslation
    val showGenerateAction = lyricState.status == LyricStatus.READY &&
        lyricState.translationSupported &&
        lyricState.translationState in setOf(
            LyricTranslationStatus.IDLE,
            LyricTranslationStatus.PENDING,
            LyricTranslationStatus.FAILED,
        )

    // Direct floating lyrics viewport (borderless & cardless, modeled after Web LyricsScroller).
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
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("player_lyrics_list")
                            .nestedScroll(absorbLyricsOverscroll)
                            .onSizeChanged { viewportHeightPx = it.height }
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
                        contentPadding = PaddingValues(top = topFocusPad, bottom = bottomFocusPad, start = 4.dp, end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(if (twoColumns) 26.dp else 22.dp),
                    ) {
                        if (introInterludeState != null) {
                            item(key = "lyric-intro-dots") {
                                InterludeDotsRow(
                                    interludeState = introInterludeState,
                                    playbackPositionMs = playbackPositionMs,
                                    active = playing && !buffering,
                                    twoColumns = twoColumns,
                                    onSeekTo = onSeekTo,
                                )
                            }
                        }
                        itemsIndexed(lyricState.lines, key = { index, line -> "$index:${line.timeSeconds}:${line.text}" }) { index, line ->
                            val listIndex = index + introSlotOffset
                            val isActive = index == activeIdx
                            val presentation = resolveLyricPresentation(
                                index = index,
                                activeIndex = activeIdx,
                                lineCount = lyricState.lines.size,
                                userScrolling = userScrolling,
                            )
                            val lineAlpha by animateFloatAsState(
                                presentation.opacity,
                                tween(280, easing = FastOutSlowInEasing),
                                label = "lyric_alpha_$index",
                            )
                            val lineScale by animateFloatAsState(
                                presentation.scale,
                                LyricMotionSpecs.ScaleSpring,
                                label = "lyric_scale_$index",
                            )
                            val lineBlur by animateDpAsState(
                                presentation.blurRadius,
                                tween(300, easing = FastOutSlowInEasing),
                                label = "lyric_blur_$index",
                            )
                            val linePushY by animateDpAsState(
                                presentation.verticalOffset,
                                LyricMotionSpecs.PushSpring,
                                label = "lyric_push_$index",
                            )
                            val clickableModifier = line.timeSeconds?.let { sec ->
                                Modifier.clickable {
                                    userScrolling = false
                                    onSeekTo((sec * 1000.0).toLong().coerceAtLeast(0L))
                                }
                            } ?: Modifier

                            val betweenInterludeState = if (isActive && index < lyricState.lines.lastIndex) {
                                computeInterludeUiState(
                                    currentTimeSeconds = playbackPositionMs / 1000.0,
                                    lines = lyricState.lines,
                                    activeIndex = activeIdx,
                                    lineIndex = index,
                                    userScrolling = userScrolling,
                                    syncMode = lyricState.syncMode,
                                ).takeIf { it.isInterlude && it.window.kind == InterludeKind.BETWEEN_LINES }
                            } else null

                            Column(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.fillMaxWidth()
                                        .testTag("player_lyric_$index")
                                        .semantics { selected = isActive }
                                        .clip(RoundedCornerShape(12.dp))
                                        .then(clickableModifier)
                                        .graphicsLayer {
                                            alpha = lineAlpha
                                            scaleX = lineScale
                                            scaleY = lineScale
                                            translationY = waveController.offsetFor(listIndex) + linePushY.toPx()
                                            transformOrigin = TransformOrigin(0f, 0.5f)
                                        }
                                        .then(
                                            if (Build.VERSION.SDK_INT >= 31 && lineBlur > 0.2.dp) {
                                                Modifier.blur(lineBlur, BlurredEdgeTreatment.Unbounded)
                                            } else Modifier,
                                        )
                                        .padding(vertical = 4.dp),
                                ) {
                                    val wordSynced = isActive && lyricState.syncMode == "word" && line.words.isNotEmpty()
                                    val primaryColor = if (isActive) FlareTokens.ClassicVibrantPrimary else Color.White
                                    val primaryShadow: Shadow? = null

                                    if (wordSynced) {
                                        KaraokeLyricText(
                                            line = line,
                                            basePositionMs = playbackPositionMs,
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
                                            ),
                                            color = if (isActive) Color(0xCCFFFFFF) else Color(0xA6FFFFFF),
                                            modifier = Modifier.padding(top = 5.dp),
                                        )
                                    }
                                }
                                AnimatedVisibility(
                                    visible = betweenInterludeState != null,
                                    enter = fadeIn(tween(240)) + expandVertically(tween(240)),
                                    exit = fadeOut(tween(180)) + shrinkVertically(tween(180)),
                                ) {
                                    betweenInterludeState?.let { interlude ->
                                        Column {
                                            Spacer(Modifier.height(10.dp))
                                            InterludeDotsRow(
                                                interludeState = interlude,
                                                playbackPositionMs = playbackPositionMs,
                                                active = playing && !buffering,
                                                twoColumns = twoColumns,
                                                onSeekTo = onSeekTo,
                                            )
                                        }
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
        if (showDisplayToggle || showGenerateAction || translationNotice != null || translationFailed) {
            Box(Modifier.fillMaxWidth().height(ClassicTransportHeight).padding(start = 4.dp),
                contentAlignment = Alignment.CenterStart) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showDisplayToggle) {
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

@Composable
private fun KaraokeLyricText(
    line: LyricLine,
    basePositionMs: Long,
    playing: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    shadow: Shadow?,
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
                currentSec >= word.endTimeSeconds -> FlareTokens.ClassicVibrantPrimary
                currentSec <= word.startTimeSeconds -> Color(0x6BFFFFFF)
                else -> {
                    val span = (word.endTimeSeconds - word.startTimeSeconds).coerceAtLeast(0.05)
                    val fraction = ((currentSec - word.startTimeSeconds) / span).toFloat().coerceIn(0f, 1f)
                    lerp(Color(0x6BFFFFFF), FlareTokens.ClassicVibrantPrimary, fraction)
                }
            }
            withStyle(
                SpanStyle(
                    color = wordColor,
                    fontWeight = FontWeight.Bold,
                    shadow = shadow,
                ),
            ) {
                append(word.text)
            }
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = fontSize,
            lineHeight = lineHeight,
        ),
    )
}

private fun systemAnimatorsEnabled(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 26) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }

@Composable
private fun InterludeDotsRow(
    interludeState: InterludeUiState,
    playbackPositionMs: Long,
    active: Boolean,
    twoColumns: Boolean,
    onSeekTo: (Long) -> Unit,
) {
    val context = LocalContext.current
    val animatorsEnabled = remember(context) { systemAnimatorsEnabled(context) }
    val window = interludeState.window
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

    val liveState = remember(displayPosMs, interludeState) {
        val computed = interludeUiFromWindow(displayPosMs / 1000.0, window)
        if (computed.isInterlude) computed else interludeState
    }

    val hasFillingDot = liveState.dotStates.any { it == InterludeDotFillState.FILLING }
    val breathePhase = if (active && animatorsEnabled && hasFillingDot) {
        val transition = rememberInfiniteTransition(label = "interlude_breathe")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "interlude_breathe_phase",
        )
        value
    } else 0.5f

    val dotSize = if (twoColumns) 16.dp else 14.dp
    val containerLabel = stringResource(R.string.ft_interlude_progress, liveState.stage)
    val dotLabels = listOf(
        stringResource(R.string.ft_interlude_dot_stage_1),
        stringResource(R.string.ft_interlude_dot_stage_2),
        stringResource(R.string.ft_interlude_dot_stage_3),
    )

    Row(
        Modifier.padding(vertical = 6.dp)
            .testTag("player_interlude_dots")
            .semantics { contentDescription = containerLabel },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val dotState = liveState.dotStates.getOrElse(index) { InterludeDotFillState.PENDING }
            val dotFill = liveState.dotFills.getOrElse(index) { 0f }.coerceIn(0f, 1f)
            val targetSec = liveState.dotTimesSeconds?.getOrNull(index)

            // Full fixed circles that brighten & whiten smoothly with progress while preserving web breathing glow.
            val fillAlpha = when (dotState) {
                InterludeDotFillState.PENDING -> 0.14f
                InterludeDotFillState.FILLING -> 0.24f + 0.72f * dotFill
                InterludeDotFillState.COMPLETE -> 1.0f
            }
            val borderAlpha = when (dotState) {
                InterludeDotFillState.PENDING -> 0.22f
                InterludeDotFillState.FILLING -> (0.45f + 0.40f * breathePhase).coerceAtLeast(0.45f + 0.45f * dotFill)
                InterludeDotFillState.COMPLETE -> 0.96f
            }
            val glowAlpha = when (dotState) {
                InterludeDotFillState.PENDING -> 0f
                InterludeDotFillState.FILLING -> (0.14f + 0.34f * breathePhase) * (0.45f + 0.55f * dotFill)
                InterludeDotFillState.COMPLETE -> 0.28f
            }

            Box(
                Modifier.size(dotSize),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.requiredSize(38.dp)
                        .testTag("interlude_dot_$index")
                        .clip(CircleShape)
                        .clickable(
                            enabled = targetSec != null,
                            role = Role.Button,
                            onClickLabel = dotLabels[index],
                        ) {
                            targetSec?.let { onSeekTo((it * 1000.0).toLong().coerceAtLeast(0L)) }
                        }
                        .semantics {
                            contentDescription = dotLabels[index]
                            selected = dotState == InterludeDotFillState.COMPLETE
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(dotSize)
                            .drawBehind {
                                if (glowAlpha > 0.01f) {
                                    val haloRadius = size.minDimension * if (dotState == InterludeDotFillState.FILLING) {
                                        0.72f + 0.26f * breathePhase
                                    } else {
                                        0.78f
                                    }
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = glowAlpha),
                                                Color.White.copy(alpha = 0f),
                                            ),
                                            radius = haloRadius,
                                        ),
                                        radius = haloRadius,
                                    )
                                }
                            }
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = fillAlpha))
                            .border(1.5.dp, Color.White.copy(alpha = borderAlpha), CircleShape),
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
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = tint,
        )
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
private fun ClassicPlayerControls(
    state: PlaybackSnapshot,
    actions: ClientActions,
    onQueue: () -> Unit,
    showLyricsButton: Boolean,
    onToggleLyrics: () -> Unit,
    hideMetadata: Boolean,
    modifier: Modifier = Modifier,
    lyricsActive: Boolean = false,
) {
    val song = state.current ?: return
    var dragging by remember(song.id) { mutableStateOf<Float?>(null) }
    LaunchedEffect(state.durationMs) {
        if (state.durationMs <= 0) dragging = null
    }
    val duration = state.durationMs.coerceAtLeast(0)
    val position = (dragging?.toLong() ?: state.positionMs).coerceIn(0, duration)

    Column(modifier) {
        if (!hideMetadata) {
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

        Spacer(Modifier.height(10.dp))

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

        Spacer(Modifier.height(6.dp))
        ClassicTransportRow(state, actions, onQueue)
    }
}

private enum class ClassicPlaybackMode { SEQUENCE, LOOP, SINGLE, RANDOM }

private fun currentPlaybackMode(state: PlaybackSnapshot): ClassicPlaybackMode = when {
    state.shuffle -> ClassicPlaybackMode.RANDOM
    state.repeatMode == 2 -> ClassicPlaybackMode.LOOP
    state.repeatMode == 1 -> ClassicPlaybackMode.SINGLE
    else -> ClassicPlaybackMode.SEQUENCE
}

private fun advancePlaybackMode(state: PlaybackSnapshot, actions: ClientActions) {
    when (currentPlaybackMode(state)) {
        // sequence (repeat=0, shuffle=false) -> loop (repeat=2, shuffle=false)
        ClassicPlaybackMode.SEQUENCE -> {
            actions.cycleRepeat()
            actions.cycleRepeat()
        }
        // loop (repeat=2, shuffle=false) -> single (repeat=1, shuffle=false)
        ClassicPlaybackMode.LOOP -> {
            actions.cycleRepeat()
            actions.cycleRepeat()
        }
        // single (repeat=1, shuffle=false) -> random (repeat=0, shuffle=true)
        ClassicPlaybackMode.SINGLE -> {
            actions.cycleRepeat()
            actions.cycleRepeat()
            actions.toggleShuffle()
        }
        // random (shuffle=true) -> sequence (repeat=0, shuffle=false)
        ClassicPlaybackMode.RANDOM -> {
            actions.toggleShuffle()
            when (state.repeatMode) {
                1 -> { actions.cycleRepeat(); actions.cycleRepeat() }
                2 -> actions.cycleRepeat()
            }
        }
    }
}

@Composable
private fun ClassicTransportRow(
    state: PlaybackSnapshot,
    actions: ClientActions,
    onQueue: () -> Unit,
) {
    val playing = state.playing || state.buffering
    val playState = stringResource(
        when {
            state.buffering -> R.string.ft_buffering
            state.playing -> R.string.ft_playing
            else -> R.string.ft_paused
        },
    )
    val mode = currentPlaybackMode(state)
    val modeDescription = stringResource(
        when (mode) {
            ClassicPlaybackMode.SEQUENCE -> R.string.ft_mode_sequence
            ClassicPlaybackMode.LOOP -> R.string.ft_repeat_all
            ClassicPlaybackMode.SINGLE -> R.string.ft_repeat_one
            ClassicPlaybackMode.RANDOM -> R.string.ft_shuffle_on
        },
    )
    val modeIcon = when (mode) {
        ClassicPlaybackMode.SEQUENCE -> ClientIcons.Sequence
        ClassicPlaybackMode.LOOP -> ClientIcons.Repeat
        ClassicPlaybackMode.SINGLE -> ClientIcons.RepeatOne
        ClassicPlaybackMode.RANDOM -> ClientIcons.Shuffle
    }

    Row(
        Modifier.fillMaxWidth().heightIn(min = ClassicTransportHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EdgeSlotIconButton(
            slotWidth = 22.dp,
            slotHeight = 48.dp,
            touchSize = 48.dp,
            onClick = { advancePlaybackMode(state, actions) },
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
            modifier = Modifier.size(64.dp).testTag("player_play")
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
                    modifier = Modifier.size(36.dp), tint = FlareTokens.ClassicPlayGlyph,
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
    var palette by remember(song.id) { mutableStateOf(ClassicBackdropPalette.Default) }

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
                        palette.dominant.copy(alpha = 0.85f),
                        palette.secondary.copy(alpha = 0.65f),
                        palette.deep,
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
