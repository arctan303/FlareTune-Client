package com.flaretune.client.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import coil.ImageLoader
import coil.compose.AsyncImage
import com.flaretune.client.R
import com.flaretune.client.data.PlayedCollectionType
import com.flaretune.client.model.ClientNotice
import com.flaretune.client.model.PlaylistSummary
import com.flaretune.client.model.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Spring press-scale micro-interaction applied exclusively on [graphicsLayer] so touch feedback
 * never triggers layout or recomposition, and springs back cleanly if an ancestor claims a drag.
 */
@Composable
internal fun Modifier.flarePressScale(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    hapticOnTap: Boolean = false,
): Modifier {
    if (!enabled) return this
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    return this
        .graphicsLayer {
            val s = scale.value
            scaleX = s
            scaleY = s
        }
        .pointerInput(enabled, pressedScale, hapticOnTap) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val pressJob = scope.launch {
                    scale.animateTo(
                        pressedScale,
                        spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium),
                    )
                }
                val up = waitForUpOrCancellation()
                pressJob.cancel()
                if (up != null && hapticOnTap) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                scope.launch {
                    scale.animateTo(
                        1f,
                        spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
                    )
                }
            }
        }
}

/**
 * Shared nested scroll connection that absorbs downward overscroll at the bottom of sheet lists
 * so ModalBottomSheet does not jitter or bounce when reaching the bottom.
 */
@Composable
internal fun rememberAbsorbBottomOverscroll(): NestedScrollConnection = remember {
    object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0f) available.copy(x = 0f) else Offset.Zero

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
            if (available.y < 0f) available.copy(x = 0f) else Velocity.Zero
    }
}

/**
 * Unified ModalBottomSheet shell across Queue, Action Menus, Song Pickers, and Ordering lists.
 */
internal val LocalFlareSheetDismiss = compositionLocalOf<((() -> Unit) -> Unit)?> { null }

class FlareSheetDismissHandle {
    internal var dismissWithAction: ((() -> Unit) -> Unit)? = null

    fun dismiss(afterHide: () -> Unit) {
        val handler = dismissWithAction
        if (handler != null) {
            handler(afterHide)
        } else {
            afterHide()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FlareModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetGesturesEnabled: Boolean = true,
    dismissHandle: FlareSheetDismissHandle? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val latestOnDismiss by rememberUpdatedState(onDismissRequest)
    var dismissing by remember { mutableStateOf(false) }
    val animatedDismiss: (() -> Unit) -> Unit = { afterHide ->
        if (!dismissing) {
            dismissing = true
            scope.launch {
                var completed = false
                try {
                    if (sheetState.isVisible) {
                        sheetState.hide()
                    }
                    completed = true
                } finally {
                    dismissing = false
                    if (completed || !sheetState.isVisible) {
                        afterHide()
                    }
                }
            }
        }
    }
    SideEffect {
        dismissHandle?.dismissWithAction = animatedDismiss
    }
    DisposableEffect(dismissHandle) {
        onDispose {
            dismissHandle?.dismissWithAction = null
        }
    }
    ModalBottomSheet(
        onDismissRequest = { if (!dismissing) latestOnDismiss() },
        sheetState = sheetState,
        sheetGesturesEnabled = sheetGesturesEnabled,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier,
    ) {
        CompositionLocalProvider(LocalFlareSheetDismiss provides animatedDismiss) {
            content()
        }
    }
}

@Composable
internal fun FlareSheetHeader(
    title: String,
    subtitle: String? = null,
    onClose: (() -> Unit)? = null,
    closeEnabled: Boolean = true,
    trailingActions: @Composable RowScope.() -> Unit = {},
) {
    val dismissSheet = LocalFlareSheetDismiss.current
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailingActions()
        if (onClose != null) {
            ActionIcon(
                ClientIcons.Close,
                stringResource(R.string.ft_close),
                { dismissSheet?.invoke(onClose) ?: onClose() },
                enabled = closeEnabled,
            )
        }
    }
}

/**
 * Animated trash icon whose lid progressively hinges open as swipe progress approaches delete threshold,
 * reversing smoothly when the user drags back.
 */
@Composable
internal fun AnimatedTrashIcon(
    progress: Float,
    armed: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val lidAngle by animateFloatAsState(
        targetValue = -36f * progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 60),
        label = "trash_lid",
    )
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val sx = w / 24f
        val sy = h / 24f
        val stroke = Stroke(width = 2.0f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Body bin outline + ribs
        val bin = Path().apply {
            moveTo(6.2f * sx, 7.2f * sy)
            lineTo(7.2f * sx, 19.3f * sy)
            quadraticTo(7.35f * sx, 21.0f * sy, 9.0f * sx, 21.0f * sy)
            lineTo(15.0f * sx, 21.0f * sy)
            quadraticTo(16.65f * sx, 21.0f * sy, 16.8f * sx, 19.3f * sy)
            lineTo(17.8f * sx, 7.2f * sy)
        }
        drawPath(bin, color = tint, style = stroke)
        drawLine(tint, Offset(10f * sx, 11f * sy), Offset(10.3f * sx, 17f * sy), strokeWidth = 1.9f * sx, cap = StrokeCap.Round)
        drawLine(tint, Offset(14f * sx, 11f * sy), Offset(13.7f * sx, 17f * sy), strokeWidth = 1.9f * sx, cap = StrokeCap.Round)

        // Hinged lid rotating around the left edge of the bin mouth
        rotate(degrees = lidAngle, pivot = Offset(4.5f * sx, 6.2f * sy)) {
            drawLine(
                color = tint,
                start = Offset(4.2f * sx, 6.2f * sy),
                end = Offset(19.8f * sx, 6.2f * sy),
                strokeWidth = 2.1f * sx,
                cap = StrokeCap.Round,
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(9.2f * sx, 3.2f * sy),
                size = Size(5.6f * sx, 3.0f * sy),
                cornerRadius = CornerRadius(1.2f * sx, 1.2f * sy),
                style = Stroke(width = 1.9f * sx),
            )
        }
    }
}

/**
 * Programmatically drawn coral-red five-pointed star on a crisp white canvas for the special
 * "Favorites / 我的收藏" playlist, matching the web `collection-star.svg` artwork.
 */
@Composable
internal fun FavoriteHeartCover(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val minDim = minOf(w, h)
        drawRect(color = Color(0xFFFFFFFF))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x18EF3150), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.5f),
                radius = minDim * 0.44f,
            ),
            radius = minDim * 0.44f,
            center = Offset(w * 0.5f, h * 0.5f),
        )
        val s = minDim / 256f
        val ox = (w - minDim) * 0.5f
        val oy = (h - minDim) * 0.5f
        val path = Path().apply {
            moveTo(ox + 129.43f * s, oy + 63.74f * s)
            lineTo(ox + 144.15f * s, oy + 102.2f * s)
            quadraticTo(ox + 145.05f * s, oy + 104.54f * s, ox + 147.54f * s, oy + 104.67f * s)
            lineTo(ox + 188.68f * s, oy + 106.78f * s)
            quadraticTo(ox + 192.67f * s, oy + 106.99f * s, ox + 189.56f * s, oy + 109.5f * s)
            lineTo(ox + 157.53f * s, oy + 135.39f * s)
            quadraticTo(ox + 155.58f * s, oy + 136.96f * s, ox + 156.23f * s, oy + 139.38f * s)
            lineTo(ox + 166.93f * s, oy + 179.15f * s)
            quadraticTo(ox + 167.97f * s, oy + 183.01f * s, ox + 164.62f * s, oy + 180.83f * s)
            lineTo(ox + 130.1f * s, oy + 158.36f * s)
            quadraticTo(ox + 128f * s, oy + 157f * s, ox + 125.9f * s, oy + 158.36f * s)
            lineTo(ox + 91.38f * s, oy + 180.83f * s)
            quadraticTo(ox + 88.03f * s, oy + 183.01f * s, ox + 89.07f * s, oy + 179.15f * s)
            lineTo(ox + 99.77f * s, oy + 139.38f * s)
            quadraticTo(ox + 100.42f * s, oy + 136.96f * s, ox + 98.47f * s, oy + 135.39f * s)
            lineTo(ox + 66.44f * s, oy + 109.5f * s)
            quadraticTo(ox + 63.33f * s, oy + 106.99f * s, ox + 67.32f * s, oy + 106.78f * s)
            lineTo(ox + 108.46f * s, oy + 104.67f * s)
            quadraticTo(ox + 110.95f * s, oy + 104.54f * s, ox + 111.85f * s, oy + 102.2f * s)
            lineTo(ox + 126.57f * s, oy + 63.74f * s)
            quadraticTo(ox + 128f * s, oy + 60f * s, ox + 129.43f * s, oy + 63.74f * s)
            close()
        }
        drawPath(
            path = path,
            color = Color(0xFFEF3150),
        )
    }
}

/**
 * Playlist / favorite artwork renderer: supports special drawn heart cover for favorites,
 * custom cover, 2x2 multi-cover collage, single cover, and thematic gradient placeholder.
 */
@Composable
internal fun PlaylistCoverArt(
    name: String,
    customCoverUrl: String?,
    previewCovers: List<String>,
    isFavorite: Boolean,
    loader: ImageLoader?,
    modifier: Modifier = Modifier,
    radius: Dp = 14.dp,
    fallbackIcon: ImageVector = if (isFavorite) ClientIcons.Heart else ClientIcons.Music,
) {
    val validPreviews = remember(previewCovers) { previewCovers.filter { it.isNotBlank() }.distinct() }
    val shape = RoundedCornerShape(radius)
    val bgBrush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
    Box(
        modifier
            .clip(shape)
            .background(bgBrush)
            .border(
                0.7.dp,
                if (isFavorite) Color(0x14000000) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isFavorite -> {
                FavoriteHeartCover(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("favorite_heart_cover")
                        .semantics { contentDescription = name },
                )
            }
            loader != null && !customCoverUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = customCoverUrl,
                    imageLoader = loader,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            loader != null && validPreviews.size >= 4 -> {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(validPreviews[0], null, loader, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                        AsyncImage(validPreviews[1], null, loader, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                    }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(validPreviews[2], null, loader, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                        AsyncImage(validPreviews[3], null, loader, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                    }
                }
            }
            loader != null && validPreviews.isNotEmpty() -> {
                AsyncImage(
                    model = validPreviews.first(),
                    imageLoader = loader,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                Icon(
                    fallbackIcon,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = if (isFavorite) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Multi-row horizontally scrolling shelf of unified [SongRow] items (up to [limit] tracks,
 * [rowsPerColumn] tracks per vertical column, defaulting to 4 rows per column). Wide layouts
 * (>= 700dp) size each column to ~2/5 of the available width so two full columns show while the
 * third peeks ~1/5 on the right.
 */
@Composable
internal fun UnifiedSongSectionList(
    songs: List<Song>,
    currentSongId: String?,
    playing: Boolean,
    buffering: Boolean,
    loader: ImageLoader?,
    modifier: Modifier = Modifier,
    limit: Int = 16,
    rowsPerColumn: Int = 4,
    tagPrefix: String = "shelf_song_",
    availableWidth: Dp = Dp.Unspecified,
    selectionActive: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onLongSelect: ((Song) -> Unit)? = null,
    onSelect: (Song, Int) -> Unit,
) {
    val visible = remember(songs, limit) { songs.take(limit) }
    if (visible.isEmpty()) return
    val safeRows = rowsPerColumn.coerceAtLeast(1)
    val columns = remember(visible, safeRows) { visible.chunked(safeRows) }
    if (availableWidth.isSpecified) {
        val columnWidth = resolveUnifiedShelfColumnWidth(availableWidth)
        UnifiedSongSectionListContent(
            columns = columns,
            safeRows = safeRows,
            columnWidth = columnWidth,
            currentSongId = currentSongId,
            playing = playing,
            buffering = buffering,
            loader = loader,
            tagPrefix = tagPrefix,
            selectionActive = selectionActive,
            selectedIds = selectedIds,
            onLongSelect = onLongSelect,
            onSelect = onSelect,
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val columnWidth = resolveUnifiedShelfColumnWidth(maxWidth)
            UnifiedSongSectionListContent(
                columns = columns,
                safeRows = safeRows,
                columnWidth = columnWidth,
                currentSongId = currentSongId,
                playing = playing,
                buffering = buffering,
                loader = loader,
                tagPrefix = tagPrefix,
                selectionActive = selectionActive,
                selectedIds = selectedIds,
                onLongSelect = onLongSelect,
                onSelect = onSelect,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun resolveUnifiedShelfColumnWidth(availableWidth: Dp): Dp =
    if (availableWidth >= 700.dp) {
        ((availableWidth - 26.dp) * 0.40f).coerceIn(272.dp, 540.dp)
    } else {
        ((availableWidth - 20.dp) * 0.86f).coerceIn(264.dp, 360.dp)
    }

@Composable
private fun UnifiedSongSectionListContent(
    columns: List<List<Song>>,
    safeRows: Int,
    columnWidth: Dp,
    currentSongId: String?,
    playing: Boolean,
    buffering: Boolean,
    loader: ImageLoader?,
    tagPrefix: String,
    selectionActive: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onLongSelect: ((Song) -> Unit)? = null,
    onSelect: (Song, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowState = rememberLazyListState()
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = rowState)
    LazyRow(
        state = rowState,
        flingBehavior = snapFlingBehavior,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(columns, key = { colIdx, col -> "col_${colIdx}_${col.firstOrNull()?.id}" }) { colIdx, columnSongs ->
            Column(
                Modifier.width(columnWidth),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                columnSongs.forEachIndexed { rowIdx, song ->
                    val flatIndex = colIdx * safeRows + rowIdx
                    val current = song.id == currentSongId
                    SongRow(
                        song = song,
                        imageLoader = loader,
                        current = current,
                        playing = playing,
                        buffering = current && buffering,
                        horizontalPadding = 6.dp,
                        selectionActive = selectionActive,
                        selected = song.id in selectedIds,
                        onLongClick = onLongSelect?.let { { it(song) } },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .testTag(tagPrefix + song.id),
                        onClick = { onSelect(song, flatIndex) },
                    )
                }
            }
        }
    }
}

/**
 * Unified collection detail page skeleton shared by Playlists, Favorites, Albums, and Play History.
 */
@Composable
internal fun CollectionDetailScaffold(
    kindLabel: String,
    title: String,
    subtitle: String? = null,
    onSubtitleClick: (() -> Unit)? = null,
    metaLine: String? = null,
    coverUrl: String? = null,
    previewCovers: List<String> = emptyList(),
    isFavorite: Boolean = false,
    fallbackIcon: ImageVector = ClientIcons.Music,
    loader: ImageLoader?,
    topInset: Dp,
    bottomInset: Dp,
    testTag: String,
    backTag: String = "detail_back",
    onBack: () -> Unit,
    headerExtraActions: @Composable RowScope.() -> Unit = {},
    primaryActions: @Composable () -> Unit,
    loading: Boolean = false,
    notice: ClientNotice? = null,
    noticeTag: String = "detail_retry",
    onRetry: (() -> Unit)? = null,
    secondaryNotice: ClientNotice? = null,
    emptyMessage: String,
    isEmpty: Boolean,
    listTestTag: String = "detail_track_list",
    topBarOverride: (@Composable () -> Unit)? = null,
    bottomOverlay: (@Composable BoxScope.() -> Unit)? = null,
    tracksContent: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val scrolledPastHero by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 220 }
    }
    val topBarAlpha by animateFloatAsState(
        targetValue = if (scrolledPastHero) 0.94f else 0f,
        animationSpec = tween(180),
        label = "detail_bar_alpha",
    )
    val bgColor = MaterialTheme.colorScheme.background
    val density = LocalDensity.current
    var overlayHeightDp by remember { mutableStateOf(0.dp) }
    LaunchedEffect(bottomOverlay == null) {
        if (bottomOverlay == null) overlayHeightDp = 0.dp
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(bgColor).testTag(testTag),
    ) {
        val wide = maxWidth >= 700.dp
        val ambientUrl = coverUrl ?: previewCovers.firstOrNull { it.isNotBlank() }

        // Soft ambient cover wash behind top masthead
        if (loader != null && !ambientUrl.isNullOrBlank()) {
            Box(
                Modifier.fillMaxWidth().height(340.dp).align(Alignment.TopCenter),
            ) {
                PreblurredCoverBackdrop(
                    coverUrl = ambientUrl,
                    imageLoader = loader,
                    alpha = 0.24f,
                    targetSize = 64,
                    radius = 14,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to bgColor.copy(alpha = 0.12f),
                            0.55f to bgColor.copy(alpha = 0.65f),
                            1f to bgColor,
                        ),
                    ),
                )
            }
        }

        Column(Modifier.fillMaxSize()) {
            // Sticky Top Bar
            if (topBarOverride != null) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(top = topInset),
                ) {
                    topBarOverride()
                }
            } else {
                Row(
                    Modifier.fillMaxWidth()
                        .background(bgColor.copy(alpha = topBarAlpha))
                        .padding(top = topInset + 4.dp, start = 8.dp, end = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionIcon(
                        ClientIcons.ChevronLeft,
                        stringResource(R.string.ft_back),
                        onBack,
                        modifier = Modifier.testTag(backTag),
                    )
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = scrolledPastHero,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(150)),
                        ) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp),
                            )
                        }
                    }
                    headerExtraActions()
                }
            }

            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f).testTag(listTestTag),
                contentPadding = PaddingValues(
                    bottom = if (bottomOverlay != null && overlayHeightDp > 0.dp) {
                        maxOf(bottomInset + 76.dp, overlayHeightDp + 12.dp)
                    } else {
                        bottomInset
                    },
                ),
            ) {
                item(key = "collection-masthead") {
                    if (wide) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            PlaylistCoverArt(
                                name = title,
                                customCoverUrl = coverUrl,
                                previewCovers = previewCovers,
                                isFavorite = isFavorite,
                                loader = loader,
                                fallbackIcon = fallbackIcon,
                                modifier = Modifier.size(216.dp),
                                radius = 16.dp,
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    kindLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    title,
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!subtitle.isNullOrBlank()) {
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (onSubtitleClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = if (onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick) else Modifier,
                                    )
                                }
                                if (!metaLine.isNullOrBlank()) {
                                    Text(
                                        metaLine,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                primaryActions()
                            }
                        }
                    } else {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PlaylistCoverArt(
                                name = title,
                                customCoverUrl = coverUrl,
                                previewCovers = previewCovers,
                                isFavorite = isFavorite,
                                loader = loader,
                                fallbackIcon = fallbackIcon,
                                modifier = Modifier.widthIn(max = 200.dp).fillMaxWidth().aspectRatio(1f),
                                radius = 16.dp,
                            )
                            Spacer(Modifier.height(18.dp))
                            Text(
                                kindLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                title,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (onSubtitleClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = if (onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick) else Modifier,
                                )
                            }
                            if (!metaLine.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    metaLine,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            primaryActions()
                        }
                    }
                }

                if (notice != null) {
                    item(key = "collection-notice") {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Notice(notice)
                            if (onRetry != null) {
                                TextButton(onClick = onRetry, modifier = Modifier.testTag(noticeTag)) {
                                    Text(stringResource(R.string.ft_retry))
                                }
                            }
                        }
                    }
                }
                if (secondaryNotice != null) {
                    item(key = "collection-secondary-notice") {
                        Notice(secondaryNotice, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                }
                if (isEmpty && !loading) {
                    item(key = "collection-empty") {
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 180.dp).padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                emptyMessage,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    tracksContent()
                }
            }
        }
        bottomOverlay?.let { overlay ->
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        overlayHeightDp = with(density) { size.height.toDp() }
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                overlay()
            }
        }
    }
}

private enum class PlayHistoryTab { SONGS, ARTISTS, COLLECTIONS, QUEUES }

/**
 * Play history secondary page with 4 top tabs: Songs, Artists, Albums & Playlists, and Past Queues.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayHistoryPage(
    state: ClientUiState,
    actions: ClientActions,
    loader: ImageLoader?,
    topInset: Dp,
    bottomInset: Dp,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit = {},
    selectedTabIndex: Int? = null,
    onSelectTabIndex: ((Int) -> Unit)? = null,
) {
    val listeningInteractions = LocalListeningInteractions.current
    var localTab by rememberSaveable { mutableStateOf(PlayHistoryTab.SONGS) }
    val selectedTab = selectedTabIndex
        ?.let { PlayHistoryTab.entries.getOrNull(it.coerceIn(0, PlayHistoryTab.entries.lastIndex)) }
        ?: localTab
    val setSelectedTab: (PlayHistoryTab) -> Unit = { tab ->
        if (onSelectTabIndex != null) onSelectTabIndex(tab.ordinal) else localTab = tab
    }
    val historyItems = state.playHistory
    val historyKeys = remember(historyItems) {
        val seen = HashMap<String, Int>(historyItems.size)
        historyItems.map { item ->
            val count = (seen[item.id] ?: 0) + 1
            seen[item.id] = count
            if (count == 1) item.id else "${item.id}#$count"
        }
    }

    val artistCoverLookup = remember(state.artists, state.songs, state.playHistory) {
        buildMap {
            state.artists.forEach { a ->
                val url = a.photoUrl ?: a.coverUrl
                if (!url.isNullOrBlank()) {
                    a.id?.let { put("id:$it", url) }
                    put("name:${a.name.lowercase()}", url)
                }
            }
            (state.playHistory + state.songs).forEach { song ->
                val url = song.coverUrl?.takeIf(String::isNotBlank) ?: return@forEach
                song.artistId?.let { id -> getOrPut("id:$id") { url } }
                if (song.artist.isNotBlank()) getOrPut("name:${song.artist.lowercase()}") { url }
            }
        }
    }

    val albumLookup = remember(state.albums, state.searchAlbums, state.artistAlbums) {
        (state.albums + state.searchAlbums + state.artistAlbums).associateBy { it.id }
    }
    val playlistLookup = remember(state.playlists) { state.playlists.associateBy { it.id } }

    var pendingClearTab by remember { mutableStateOf<PlayHistoryTab?>(null) }
    val fontScale = LocalDensity.current.fontScale

    Column(Modifier.fillMaxSize().testTag("history_panel")) {
        // Sticky Top Header
        Row(
            Modifier.fillMaxWidth()
                .padding(top = topInset + 4.dp, start = 8.dp, end = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIcon(
                ClientIcons.ChevronLeft,
                stringResource(R.string.ft_back),
                onBack,
                modifier = Modifier.testTag("history_back"),
            )
            Text(
                stringResource(R.string.ft_play_history),
                Modifier.weight(1f).padding(horizontal = 6.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (selectedTab) {
                PlayHistoryTab.SONGS -> if (historyItems.isNotEmpty() && listeningInteractions != null) {
                    ActionIcon(
                        ClientIcons.Trash,
                        stringResource(R.string.ft_listening_clear_history),
                        listeningInteractions.clearHistory,
                        modifier = Modifier.testTag("history_clear"),
                    )
                }
                PlayHistoryTab.ARTISTS -> if (state.visitedArtists.isNotEmpty()) {
                    ActionIcon(
                        ClientIcons.Trash,
                        stringResource(R.string.ft_history_clear_tab),
                        { pendingClearTab = PlayHistoryTab.ARTISTS },
                        modifier = Modifier.testTag("history_clear_artists"),
                    )
                }
                PlayHistoryTab.COLLECTIONS -> if (state.playedCollections.isNotEmpty()) {
                    ActionIcon(
                        ClientIcons.Trash,
                        stringResource(R.string.ft_history_clear_tab),
                        { pendingClearTab = PlayHistoryTab.COLLECTIONS },
                        modifier = Modifier.testTag("history_clear_collections"),
                    )
                }
                PlayHistoryTab.QUEUES -> if (state.queueSnapshots.isNotEmpty()) {
                    ActionIcon(
                        ClientIcons.Trash,
                        stringResource(R.string.ft_history_clear_tab),
                        { pendingClearTab = PlayHistoryTab.QUEUES },
                        modifier = Modifier.testTag("history_clear_queues"),
                    )
                }
            }
        }

        // 4-Tab Capsule Strip
        val tabsContent: @Composable () -> Unit = {
            listOf(
                Triple(PlayHistoryTab.SONGS, R.string.ft_history_tab_songs, "history_tab_songs"),
                Triple(PlayHistoryTab.ARTISTS, R.string.ft_history_tab_artists, "history_tab_artists"),
                Triple(PlayHistoryTab.COLLECTIONS, R.string.ft_history_tab_collections, "history_tab_collections"),
                Triple(PlayHistoryTab.QUEUES, R.string.ft_history_tab_queues, "history_tab_queues"),
            ).forEach { (tab, labelRes, tag) ->
                val active = selectedTab == tab
                Surface(
                    onClick = { setSelectedTab(tab) },
                    shape = CircleShape,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.heightIn(min = 48.dp)
                        .testTag(tag)
                        .semantics { selected = active },
                ) {
                    Box(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        if (fontScale >= 1.35f) {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabsContent()
            }
        } else {
            Row(
                Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabsContent()
            }
        }

        state.historyNotice?.let { notice ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Notice(notice)
            }
        }

        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val contentModifier = if (maxWidth >= 720.dp) {
                Modifier.widthIn(max = 900.dp).fillMaxSize()
            } else {
                Modifier.fillMaxSize()
            }
            when (selectedTab) {
                PlayHistoryTab.SONGS -> {
                    val removedMessage = stringResource(R.string.ft_feedback_history_removed)
                    LazyColumn(
                        modifier = contentModifier.testTag("history_list"),
                        contentPadding = PaddingValues(bottom = bottomInset),
                    ) {
                        if (historyItems.isNotEmpty()) {
                            item(key = "history-songs-header") {
                                val countLabel = pluralStringResource(R.plurals.ft_track_count, historyItems.size, historyItems.size)
                                val playAllButton: @Composable () -> Unit = {
                                    Button(
                                        onClick = { actions.playSongs(historyItems, 0) },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                                        modifier = Modifier.heightIn(min = 48.dp).testTag("history_play_all"),
                                    ) {
                                        Icon(ClientIcons.Play, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.ft_play_all))
                                    }
                                }
                                if (fontScale >= 1.35f) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            countLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        playAllButton()
                                    }
                                } else {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Text(
                                            countLabel,
                                            Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        playAllButton()
                                    }
                                }
                            }
                            itemsIndexed(historyItems, key = { index, _ -> historyKeys[index] }) { _, song ->
                                val playedAt = listeningTime(state.playHistoryPlayedAt[song.id], rememberAppLocale())
                                Column(Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        SongRow(
                                            song = song,
                                            imageLoader = loader,
                                            current = song.id == state.playback.current?.id,
                                            playing = state.playback.playing,
                                            buffering = state.playback.buffering,
                                            modifier = Modifier.weight(1f).testTag("history_song_" + song.id),
                                        ) {
                                            actions.playHistorySong(song)
                                        }
                                        ActionIcon(
                                            ClientIcons.Trash,
                                            stringResource(R.string.ft_listening_remove_history),
                                            {
                                                actions.removeHistory(song.id)
                                                listeningInteractions?.feedback?.invoke(removedMessage)
                                            },
                                            modifier = Modifier.testTag("history_remove_" + song.id),
                                        )
                                    }
                                    if (playedAt.isNotBlank()) {
                                        Text(
                                            playedAt,
                                            Modifier.padding(start = 76.dp, bottom = 6.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        } else {
                            item(key = "history-songs-empty") {
                                HistoryTabEmptyMessage(stringResource(R.string.ft_history_empty_songs))
                            }
                        }
                    }
                }

                PlayHistoryTab.ARTISTS -> {
                    val removedMessage = stringResource(R.string.ft_history_artist_removed)
                    LazyColumn(
                        modifier = contentModifier.testTag("history_artists_list"),
                        contentPadding = PaddingValues(bottom = bottomInset),
                    ) {
                        if (state.visitedArtists.isEmpty()) {
                            item(key = "history-artists-empty") {
                                HistoryTabEmptyMessage(stringResource(R.string.ft_history_empty_artists))
                            }
                        } else {
                            items(state.visitedArtists, key = { it.key }) { artist ->
                                val cover = artist.coverUrl
                                    ?: artist.id?.let { artistCoverLookup["id:$it"] }
                                    ?: artistCoverLookup["name:${artist.name.lowercase()}"]
                                val visitedAt = listeningTime(artist.visitedAtMs, rememberAppLocale())
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable { actions.openArtist(artist.toArtist()) }
                                        .testTag("history_artist_" + artist.key)
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.size(48.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(ClientIcons.Artist, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (!cover.isNullOrBlank() && loader != null) {
                                            AsyncImage(
                                                model = cover,
                                                contentDescription = null,
                                                imageLoader = loader,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            artist.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (visitedAt.isNotBlank()) {
                                            Text(
                                                visitedAt,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    ActionIcon(
                                        ClientIcons.Trash,
                                        stringResource(R.string.ft_history_remove_item),
                                        {
                                            actions.removeVisitedArtist(artist.key)
                                            listeningInteractions?.feedback?.invoke(removedMessage)
                                        },
                                        modifier = Modifier.testTag("history_remove_artist_" + artist.key),
                                    )
                                }
                            }
                        }
                    }
                }

                PlayHistoryTab.COLLECTIONS -> {
                    val removedMessage = stringResource(R.string.ft_history_collection_removed)
                    LazyColumn(
                        modifier = contentModifier.testTag("history_collections_list"),
                        contentPadding = PaddingValues(bottom = bottomInset),
                    ) {
                        if (state.playedCollections.isEmpty()) {
                            item(key = "history-collections-empty") {
                                HistoryTabEmptyMessage(stringResource(R.string.ft_history_empty_collections))
                            }
                        } else {
                            items(state.playedCollections, key = { it.key }) { collection ->
                                val liveAlbum = if (collection.type == PlayedCollectionType.ALBUM) albumLookup[collection.id] else null
                                val livePlaylist = if (collection.type == PlayedCollectionType.PLAYLIST) playlistLookup[collection.id] else null
                                val cover = collection.coverUrl ?: liveAlbum?.coverUrl ?: livePlaylist?.customCoverUrl
                                val previews = collection.previewCovers.ifEmpty { livePlaylist?.previewCovers.orEmpty() }
                                val isFavorite = collection.kind == "favorite"
                                val typeLabel = when {
                                    isFavorite -> stringResource(R.string.ft_liked_songs)
                                    collection.type == PlayedCollectionType.ALBUM -> stringResource(R.string.ft_album_label)
                                    else -> stringResource(R.string.ft_playlist_label)
                                }
                                val playedAt = listeningTime(collection.playedAtMs, rememberAppLocale())
                                val metaParts = buildList {
                                    add(typeLabel)
                                    if (collection.subtitle.isNotBlank()) add(collection.subtitle)
                                    if (collection.trackCount > 0) add(pluralStringResource(R.plurals.ft_track_count, collection.trackCount, collection.trackCount))
                                    if (playedAt.isNotBlank()) add(playedAt)
                                }
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable { actions.openPlayedCollection(collection) }
                                        .testTag("history_collection_" + collection.key)
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PlaylistCoverArt(
                                        name = collection.title,
                                        customCoverUrl = cover,
                                        previewCovers = previews,
                                        isFavorite = isFavorite,
                                        loader = loader,
                                        fallbackIcon = if (collection.type == PlayedCollectionType.ALBUM) ClientIcons.Disc else ClientIcons.Music,
                                        modifier = Modifier.size(54.dp),
                                        radius = 10.dp,
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            collection.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            metaParts.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    ActionIcon(
                                        ClientIcons.Play,
                                        stringResource(R.string.ft_play),
                                        { actions.playPlayedCollection(collection) },
                                        modifier = Modifier.testTag("history_play_collection_" + collection.key),
                                    )
                                    ActionIcon(
                                        ClientIcons.Trash,
                                        stringResource(R.string.ft_history_remove_item),
                                        {
                                            actions.removePlayedCollection(collection.key)
                                            listeningInteractions?.feedback?.invoke(removedMessage)
                                        },
                                        modifier = Modifier.testTag("history_remove_collection_" + collection.key),
                                    )
                                }
                            }
                        }
                    }
                }

                PlayHistoryTab.QUEUES -> {
                    val removedMessage = stringResource(R.string.ft_history_queue_removed)
                    LazyColumn(
                        modifier = contentModifier.testTag("history_queues_list"),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.playback.queue.isNotEmpty()) {
                            item(key = "history-current-queue") {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth().testTag("history_current_queue_card"),
                                ) {
                                    val summaryContent: @Composable (Modifier) -> Unit = { summaryModifier ->
                                        Column(summaryModifier) {
                                            Text(
                                                stringResource(R.string.ft_history_current_queue),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                state.playback.current?.title
                                                    ?: pluralStringResource(R.plurals.ft_track_count, state.playback.queue.size, state.playback.queue.size),
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                pluralStringResource(R.plurals.ft_track_count, state.playback.queue.size, state.playback.queue.size),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    val openQueueButton: @Composable () -> Unit = {
                                        TextButton(
                                            onClick = onOpenQueue,
                                            modifier = Modifier.heightIn(min = 48.dp).testTag("history_open_current_queue"),
                                        ) {
                                            Text(stringResource(R.string.ft_history_view_current_queue))
                                        }
                                    }
                                    if (fontScale >= 1.35f) {
                                        Column(
                                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            summaryContent(Modifier.fillMaxWidth())
                                            openQueueButton()
                                        }
                                    } else {
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            summaryContent(Modifier.weight(1f))
                                            openQueueButton()
                                        }
                                    }
                                }
                            }
                        }
                        if (state.queueSnapshots.isEmpty()) {
                            item(key = "history-queues-empty") {
                                HistoryTabEmptyMessage(stringResource(R.string.ft_history_empty_queues))
                            }
                        } else {
                            items(state.queueSnapshots, key = { it.id }) { snapshot ->
                                val sourceText = queueSnapshotSourceTitle(snapshot)
                                val capturedAt = listeningTime(snapshot.capturedAtMs, rememberAppLocale())
                                val activeTrack = snapshot.currentSong
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.fillMaxWidth().testTag("history_queue_" + snapshot.id),
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    sourceText,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                val sub = listOfNotNull(
                                                    pluralStringResource(R.plurals.ft_track_count, snapshot.songs.size, snapshot.songs.size),
                                                    capturedAt.takeIf(String::isNotBlank),
                                                ).joinToString(" · ")
                                                Text(
                                                    sub,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            ActionIcon(
                                                ClientIcons.Trash,
                                                stringResource(R.string.ft_history_remove_item),
                                                {
                                                    actions.removeQueueSnapshot(snapshot.id)
                                                    listeningInteractions?.feedback?.invoke(removedMessage)
                                                },
                                                modifier = Modifier.testTag("history_remove_queue_" + snapshot.id),
                                            )
                                        }
                                        if (activeTrack != null) {
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                stringResource(R.string.ft_history_snapshot_now_playing, activeTrack.title),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        val previewLines = snapshot.songs.take(3)
                                        if (previewLines.isNotEmpty()) {
                                            Spacer(Modifier.height(6.dp))
                                            previewLines.forEachIndexed { idx, song ->
                                                val line = if (song.artist.isNotBlank()) "${idx + 1}. ${song.title} · ${song.artist}" else "${idx + 1}. ${song.title}"
                                                Text(
                                                    line,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            Button(
                                                onClick = { actions.restoreQueueSnapshot(snapshot.id) },
                                                shape = CircleShape,
                                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                                                modifier = Modifier.heightIn(min = 48.dp).testTag("history_restore_queue_" + snapshot.id),
                                            ) {
                                                Icon(ClientIcons.Play, null, Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(stringResource(R.string.ft_history_restore_queue))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingClearTab?.let { clearTab ->
        val (titleRes, messageRes) = when (clearTab) {
            PlayHistoryTab.ARTISTS -> R.string.ft_history_clear_artists_title to R.string.ft_history_clear_artists_message
            PlayHistoryTab.COLLECTIONS -> R.string.ft_history_clear_collections_title to R.string.ft_history_clear_collections_message
            PlayHistoryTab.QUEUES -> R.string.ft_history_clear_queues_title to R.string.ft_history_clear_queues_message
            PlayHistoryTab.SONGS -> R.string.ft_listening_clear_history to R.string.ft_listening_clear_history_message
        }
        AlertDialog(
            onDismissRequest = { pendingClearTab = null },
            modifier = Modifier.testTag("history_clear_dialog"),
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(messageRes)) },
            dismissButton = {
                TextButton(
                    onClick = { pendingClearTab = null },
                    modifier = Modifier.testTag("history_clear_cancel"),
                ) {
                    Text(stringResource(R.string.ft_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (clearTab) {
                            PlayHistoryTab.ARTISTS -> actions.clearVisitedArtists()
                            PlayHistoryTab.COLLECTIONS -> actions.clearPlayedCollections()
                            PlayHistoryTab.QUEUES -> actions.clearQueueSnapshots()
                            PlayHistoryTab.SONGS -> actions.clearHistory()
                        }
                        pendingClearTab = null
                    },
                    modifier = Modifier.testTag("history_clear_confirm"),
                ) {
                    Text(stringResource(R.string.ft_history_clear_tab))
                }
            },
        )
    }
}

@Composable
private fun HistoryTabEmptyMessage(message: String) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun queueSnapshotSourceTitle(snapshot: QueueSnapshot): String {
    val kind = when (snapshot.sourceType) {
        "album" -> stringResource(R.string.ft_album_label)
        "playlist" -> stringResource(R.string.ft_playlist_label)
        "favorites" -> stringResource(R.string.ft_liked_songs)
        "artist" -> stringResource(R.string.ft_artist_label)
        "continue" -> stringResource(R.string.ft_continue_listening)
        "discovery" -> stringResource(R.string.ft_listening_random)
        "history" -> stringResource(R.string.ft_play_history)
        "search" -> stringResource(R.string.ft_search_page)
        "stats" -> stringResource(R.string.ft_listening_statistics)
        "library" -> stringResource(R.string.ft_from_your_library)
        else -> ""
    }
    return when {
        snapshot.sourceLabel.isNotBlank() && kind.isNotBlank() && snapshot.sourceType != "favorites" ->
            stringResource(R.string.ft_history_snapshot_source_named, kind, snapshot.sourceLabel)
        snapshot.sourceLabel.isNotBlank() -> snapshot.sourceLabel
        kind.isNotBlank() -> kind
        else -> stringResource(R.string.ft_history_snapshot_source_fallback)
    }
}

/**
 * Returns normalized `[leftStrength, rightStrength]` in `0f..1f` for single-line marquee edge
 * fading. Left fade ramps in only while the primary pass is scrolling past the left boundary so
 * characters resting at `x = 0` stay crisp.
 */
internal fun computeMarqueeEdgeFadeStrengths(
    scrollOffsetPx: Float,
    textWidthPx: Float,
    leftFadePx: Float,
    rightFadePx: Float,
): Pair<Float, Float> {
    val leftStrength = if (leftFadePx > 0f && scrollOffsetPx > 0.5f && scrollOffsetPx < textWidthPx) {
        kotlin.math.min(scrollOffsetPx / leftFadePx, (textWidthPx - scrollOffsetPx) / leftFadePx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val rightStrength = if (rightFadePx > 0f) 1f else 0f
    return leftStrength to rightStrength
}

/**
 * Single-line marquee text that activates an offscreen horizontal alpha feather strictly when
 * [text] exceeds the viewport width and scrolling is enabled, keeping fitting text at zero extra cost.
 */
@Composable
internal fun MarqueeFadedText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    iterations: Int = Int.MAX_VALUE,
    initialDelayMillis: Int = 1200,
    overflow: TextOverflow = TextOverflow.Clip,
    leftFade: Dp = 8.dp,
    rightFade: Dp = 16.dp,
) {
    if (iterations <= 0) {
        Box(modifier = modifier.fillMaxWidth()) {
            Text(
                text = text,
                style = style,
                color = color,
                fontWeight = fontWeight,
                maxLines = 1,
                softWrap = false,
                overflow = overflow,
            )
        }
        return
    }

    val density = LocalDensity.current
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    var textWidthPx by remember { mutableIntStateOf(0) }
    val activeMarquee = viewportWidthPx > 0 && textWidthPx > viewportWidthPx
    val gapPx = kotlin.math.max(viewportWidthPx / 3f, with(density) { 24.dp.toPx() })
    val cycleDistancePx = if (activeMarquee) textWidthPx.toFloat() + gapPx else 0f
    val velocityPxPerSec = with(density) { 30.dp.toPx() }.coerceAtLeast(1f)
    val scrollDurationMs = if (activeMarquee) {
        ((cycleDistancePx / velocityPxPerSec) * 1000f).toInt().coerceAtLeast(1)
    } else {
        0
    }
    val scrollOffset = remember { Animatable(0f) }

    LaunchedEffect(activeMarquee, text, cycleDistancePx, scrollDurationMs, iterations, initialDelayMillis) {
        scrollOffset.snapTo(0f)
        if (!activeMarquee || cycleDistancePx <= 0f) return@LaunchedEffect
        var remaining = iterations
        while (remaining > 0) {
            delay(initialDelayMillis.toLong())
            scrollOffset.snapTo(0f)
            scrollOffset.animateTo(
                targetValue = cycleDistancePx,
                animationSpec = tween(durationMillis = scrollDurationMs, easing = LinearEasing),
            )
            scrollOffset.snapTo(0f)
            if (iterations != Int.MAX_VALUE) remaining--
        }
    }

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .then(
                if (activeMarquee) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            if (size.width <= 1f || size.height <= 1f) return@drawWithContent
                            val maxLeftPx = leftFade.toPx().coerceAtMost(size.width * 0.14f)
                            val maxRightPx = rightFade.toPx().coerceAtMost(size.width * 0.20f)
                            val (leftStrength, rightStrength) = computeMarqueeEdgeFadeStrengths(
                                scrollOffsetPx = scrollOffset.value,
                                textWidthPx = textWidthPx.toFloat(),
                                leftFadePx = maxLeftPx,
                                rightFadePx = maxRightPx,
                            )
                            val leftFrac = (maxLeftPx / size.width).coerceIn(0.005f, 0.25f)
                            val rightFrac = (maxRightPx / size.width).coerceIn(0.005f, 0.30f)
                            val safeRightStart = (1f - rightFrac).coerceAtLeast(leftFrac + 0.01f)
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Black.copy(alpha = 1f - leftStrength),
                                    leftFrac to Color.Black,
                                    safeRightStart to Color.Black,
                                    1f to Color.Black.copy(alpha = 1f - rightStrength),
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                } else {
                    Modifier
                },
            ),
        content = {
            Box(
                Modifier.drawWithContent {
                    val offset = scrollOffset.value
                    if (!activeMarquee || offset <= 0f) {
                        drawContent()
                    } else {
                        translate(left = -offset) { this@drawWithContent.drawContent() }
                        val secondLeft = cycleDistancePx - offset
                        if (secondLeft < viewportWidthPx.toFloat()) {
                            translate(left = secondLeft) { this@drawWithContent.drawContent() }
                        }
                    }
                },
            ) {
                Text(
                    text = text,
                    style = style,
                    color = color,
                    fontWeight = fontWeight,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        },
    ) { measurables, constraints ->
        val placeable = measurables[0].measure(
            constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity),
        )
        val containerW = if (constraints.hasBoundedWidth) {
            constraints.maxWidth.coerceAtLeast(1)
        } else {
            placeable.width.coerceAtLeast(1)
        }
        if (viewportWidthPx != containerW) viewportWidthPx = containerW
        if (textWidthPx != placeable.width) textWidthPx = placeable.width
        layout(containerW, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
}
