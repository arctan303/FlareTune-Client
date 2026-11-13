package com.flaretune.client.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.flaretune.client.R
import com.flaretune.client.model.ClientNotice
import com.flaretune.client.model.PlaylistSummary
import com.flaretune.client.model.Song

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FlareModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetGesturesEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = sheetGesturesEnabled,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier,
        content = content,
    )
}

@Composable
internal fun FlareSheetHeader(
    title: String,
    subtitle: String? = null,
    onClose: (() -> Unit)? = null,
    closeEnabled: Boolean = true,
    trailingActions: @Composable RowScope.() -> Unit = {},
) {
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
                onClose,
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
 * Programmatically drawn red heart on a crisp white canvas for the special "Favorites / 我喜欢" playlist.
 */
@Composable
internal fun FavoriteHeartCover(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val minDim = minOf(w, h)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFF5F7)),
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x1CF43F5E), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.52f),
                radius = minDim * 0.44f,
            ),
            radius = minDim * 0.44f,
            center = Offset(w * 0.5f, h * 0.52f),
        )
        val heartW = minDim * 0.52f
        val heartH = minDim * 0.46f
        val left = (w - heartW) * 0.5f
        val top = (h - heartH) * 0.5f + minDim * 0.015f
        val cx = left + heartW * 0.5f
        val dipY = top + heartH * 0.28f
        val bottomY = top + heartH
        val path = Path().apply {
            moveTo(cx, bottomY)
            cubicTo(
                left + heartW * 0.14f, top + heartH * 0.72f,
                left, top + heartH * 0.46f,
                left, top + heartH * 0.28f,
            )
            cubicTo(
                left, top,
                left + heartW * 0.40f, top,
                cx, dipY,
            )
            cubicTo(
                left + heartW * 0.60f, top,
                left + heartW, top,
                left + heartW, top + heartH * 0.28f,
            )
            cubicTo(
                left + heartW, top + heartH * 0.46f,
                left + heartW * 0.86f, top + heartH * 0.72f,
                cx, bottomY,
            )
            close()
        }
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFF4766), Color(0xFFE11D48)),
                startY = top,
                endY = bottomY,
            ),
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
 * Web-aligned multi-row horizontally scrolling song shelf (3 or 4 tracks per vertical column).
 * Adapts smoothly across narrow phones, split-screen windows, and wide tablets.
 */
@Composable
internal fun SongColumnShelf(
    songs: List<Song>,
    currentSongId: String?,
    playing: Boolean,
    buffering: Boolean,
    loader: ImageLoader?,
    modifier: Modifier = Modifier,
    rowsPerColumn: Int = if (songs.size >= 8) 4 else 3,
    tagPrefix: String = "shelf_song_",
    onSelect: (Song, Int) -> Unit,
) {
    if (songs.isEmpty()) return
    val columns = remember(songs, rowsPerColumn) { songs.chunked(rowsPerColumn.coerceAtLeast(1)) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columnWidth = ((maxWidth - 40.dp) * 0.86f).coerceIn(272.dp, 348.dp)
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(columns, key = { colIdx, col -> "col_${colIdx}_${col.firstOrNull()?.id}" }) { colIdx, columnSongs ->
                Column(
                    Modifier.width(columnWidth),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    columnSongs.forEachIndexed { rowIdx, song ->
                        val flatIndex = colIdx * rowsPerColumn + rowIdx
                        CompactShelfSongRow(
                            song = song,
                            loader = loader,
                            current = song.id == currentSongId,
                            playing = playing,
                            buffering = buffering,
                            modifier = Modifier.testTag(tagPrefix + song.id),
                            onClick = { onSelect(song, flatIndex) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompactShelfSongRow(
    song: Song,
    loader: ImageLoader?,
    current: Boolean,
    playing: Boolean,
    buffering: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = LocalListeningInteractions.current
    val status = stringResource(if (buffering) R.string.ft_buffering else if (playing) R.string.ft_playing else R.string.ft_paused)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .then(if (current) Modifier.semantics { stateDescription = status } else Modifier)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Artwork(song, loader, Modifier.size(48.dp), radius = 10.dp, placeholderSize = 22.dp)
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
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
        if (interaction != null) {
            ActionIcon(
                ClientIcons.More,
                stringResource(R.string.ft_listening_song_actions),
                { interaction.song(song) },
                modifier = Modifier.testTag("song_more_" + song.id),
            )
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
    refreshTag: String? = null,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
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
                AsyncImage(
                    model = ambientUrl,
                    imageLoader = loader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.24f,
                    modifier = Modifier.fillMaxSize().let {
                        if (Build.VERSION.SDK_INT >= 31) it.blur(48.dp) else it
                    },
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
                AnimatedVisibility(
                    visible = scrolledPastHero,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(150)),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
                if (!scrolledPastHero) {
                    Spacer(Modifier.weight(1f))
                }
                headerExtraActions()
                if (onRefresh != null) {
                    ActionIcon(
                        ClientIcons.Refresh,
                        stringResource(R.string.ft_refresh),
                        onRefresh,
                        enabled = !refreshing,
                        modifier = if (refreshTag != null) Modifier.testTag(refreshTag) else Modifier,
                    )
                }
            }

            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f).testTag(listTestTag),
                contentPadding = PaddingValues(bottom = bottomInset),
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
    }
}

/**
 * Full-page Play History view reusing CollectionDetailScaffold.
 */
@Composable
internal fun PlayHistoryPage(
    state: ClientUiState,
    actions: ClientActions,
    loader: ImageLoader?,
    topInset: Dp,
    bottomInset: Dp,
    onBack: () -> Unit,
) {
    val listeningInteractions = LocalListeningInteractions.current
    val historyItems = state.playHistory
    val leadCover = historyItems.firstOrNull { !it.coverUrl.isNullOrBlank() }?.coverUrl
    val previewCovers = remember(historyItems) {
        historyItems.mapNotNull { it.coverUrl?.takeIf(String::isNotBlank) }.distinct().take(4)
    }

    CollectionDetailScaffold(
        kindLabel = stringResource(R.string.ft_continue_listening),
        title = stringResource(R.string.ft_play_history),
        metaLine = pluralStringResource(R.plurals.ft_track_count, historyItems.size, historyItems.size),
        coverUrl = if (previewCovers.size >= 4) null else leadCover,
        previewCovers = previewCovers,
        fallbackIcon = ClientIcons.History,
        loader = loader,
        topInset = topInset,
        bottomInset = bottomInset,
        testTag = "history_panel",
        backTag = "history_back",
        onBack = onBack,
        headerExtraActions = {
            if (historyItems.isNotEmpty() && listeningInteractions != null) {
                ActionIcon(
                    ClientIcons.Trash,
                    stringResource(R.string.ft_listening_clear_history),
                    listeningInteractions.clearHistory,
                    modifier = Modifier.testTag("history_clear"),
                )
            }
        },
        primaryActions = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { actions.playSongs(historyItems, 0) },
                    enabled = historyItems.isNotEmpty(),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("history_play_all"),
                ) {
                    Icon(ClientIcons.Play, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ft_play_all))
                }
            }
        },
        notice = state.historyNotice,
        emptyMessage = stringResource(R.string.ft_empty_catalog),
        isEmpty = historyItems.isEmpty(),
        listTestTag = "history_list",
    ) {
        itemsIndexed(historyItems, key = { index, song -> "$index:${song.id}" }) { _, song ->
            val playedAt = listeningTime(state.playHistoryPlayedAt[song.id])
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SongRow(
                        song = song,
                        imageLoader = loader,
                        current = song.id == state.playback.current?.id,
                        playing = state.playback.playing,
                        buffering = state.playback.buffering,
                        modifier = Modifier.weight(1f).testTag("history_song_" + song.id),
                    ) { actions.playHistorySong(song) }
                    ActionIcon(
                        ClientIcons.Trash,
                        stringResource(R.string.ft_listening_remove_history),
                        { actions.removeHistory(song.id) },
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
    }
}
