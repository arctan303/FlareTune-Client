package com.arcinks.flaretune.preview

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import com.arcinks.flaretune.MainActivity
import com.arcinks.flaretune.R
import com.arcinks.flaretune.model.Album
import com.arcinks.flaretune.model.Song
import com.arcinks.flaretune.ui.ClientActions
import com.arcinks.flaretune.ui.ClientApp
import com.arcinks.flaretune.ui.ClientScreen
import com.arcinks.flaretune.ui.ClientUiState
import com.arcinks.flaretune.ui.LibraryTab
import com.arcinks.flaretune.ui.MainDestination
import com.arcinks.flaretune.ui.PlaybackSnapshot
import com.arcinks.flaretune.ui.theme.FlareTuneTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

/**
 * Debug-only, local UI preview. Intent data and extras are intentionally ignored.
 * This entry never obtains the application connection or playback runtime.
 */
class PreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlareTuneTheme {
                LocalPreview()
            }
        }
    }

    private fun openApplication() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    @Composable
    private fun LocalPreview() {
        val fixture = remember { PreviewFixture() }
        val localState = remember {
            mutableStateOf(fixture.initialState(getString(R.string.ft_preview_mode)))
        }
        val actions = remember { PreviewActions(localState, fixture, ::openApplication) }
        val loader = remember { createPreviewImageLoader() }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val state by localState

        DisposableEffect(loader) {
            onDispose { loader.shutdown() }
        }
        LaunchedEffect(actions, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    delay(500)
                    actions.tick(500)
                }
            }
        }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().testTag("preview_banner"),
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.ft_preview_mode),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        )
                        TextButton(
                            onClick = ::openApplication,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("preview_open_app"),
                        ) { Text(stringResource(R.string.ft_open_app)) }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    ClientApp(state, actions, loader)
                }
            }
        }
    }

    private fun createPreviewImageLoader(): ImageLoader {
        val images = (0..7).map { index ->
            BitmapDrawable(resources, createCover(index))
        }
        return ImageLoader.Builder(this)
            .diskCache(null)
            .crossfade(false)
            .components {
                add(object : Interceptor {
                    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                        // Always terminate here, including unexpected data. No chain.proceed/network.
                        val index = chain.request.data.toString()
                            .substringAfterLast('/').toIntOrNull()?.mod(images.size) ?: 0
                        return SuccessResult(images[index], chain.request, DataSource.MEMORY)
                    }
                })
            }
            .build()
    }
}

private class PreviewFixture {
    val albums = listOf(
        Album("preview-album-0", "Afterglow", "Mira Vale", "preview://art/0", 2),
        Album("preview-album-1", "Blue Hour", "Northline", "preview://art/1", 2),
        Album("preview-album-2", "Signal Garden", "Juniper Arc", "preview://art/2", 2),
        Album("preview-album-3", "Quiet Tides", "Noor Ellery", "preview://art/3", 2),
        Album("preview-album-4", "Paper Satellites", "Sol & River", "preview://art/4", 2),
        Album("preview-album-5", "Velvet Dawn", "The Lumen Room", "preview://art/5", 2),
        Album("preview-album-6", "City Between", "Niko Atlas", "preview://art/6", 2),
        Album("preview-album-7", "Northern Lines", "Aster Field", "preview://art/7", 2),
    )
    private val titles = listOf(
        "Last Light", "Amber Skies", "A Little Further", "Midnight Windows",
        "Small Signals", "Wild Geometry", "Tidepool", "When the Water Sleeps",
        "Folded Wings", "Orbit Home", "Soft Horizon", "First Train",
        "Between Stations", "Rain on Glass", "Northern Lines", "Still Moving",
    )
    val songs = titles.mapIndexed { index, title ->
        val album = albums[index / 2]
        Song(
            id = "preview-song-$index",
            title = title,
            artist = album.artist,
            album = album.title,
            durationSeconds = (180 + index * 11).toDouble(),
            audioUrl = "preview://audio/$index",
            coverUrl = album.coverUrl,
        )
    }
    val favoriteIds = songs.filterIndexed { index, _ -> index % 3 == 0 }.map { it.id }.toSet()

    fun albumSongs(album: Album): List<Song> =
        songs.filter { it.album == album.title && it.artist == album.artist }

    fun initialState(accountLabel: String) = ClientUiState(
        screen = ClientScreen.LIBRARY,
        accountName = accountLabel,
        songs = songs,
        albums = albums,
        hasMore = false,
        albumsHasMore = false,
        playback = PlaybackSnapshot(
            current = songs.first(),
            queue = songs,
            currentIndex = 0,
            playing = false,
            positionMs = 42_000,
            durationMs = (songs.first().durationSeconds * 1000).toLong(),
        ),
    )
}

/** Every callback mutates only this in-memory preview model. */
private class PreviewActions(
    private val state: MutableState<ClientUiState>,
    private val fixture: PreviewFixture,
    private val openApplication: () -> Unit,
) : ClientActions {
    private fun browse(query: String = state.value.query, tab: LibraryTab = state.value.tab) {
        val term = query.trim()
        val songs = fixture.songs.filter { song ->
            (tab != LibraryTab.FAVORITES || song.id in fixture.favoriteIds) &&
                (term.isBlank() || song.title.contains(term, ignoreCase = true) ||
                    song.artist.contains(term, ignoreCase = true))
        }
        val albums = fixture.albums.filter { album ->
            term.isBlank() || album.title.contains(term, ignoreCase = true) ||
                album.artist.contains(term, ignoreCase = true)
        }
        state.value = state.value.copy(
            query = query,
            tab = tab,
            songs = songs,
            albums = albums,
            notice = null,
            albumsNotice = null,
            loading = false,
            albumsLoading = false,
            hasMore = false,
            albumsHasMore = false,
        )
    }

    override fun navigate(destination: MainDestination) {
        state.value = state.value.copy(
            destination = destination, selectedAlbum = null, albumSongs = emptyList(),
        )
        val tab = if (destination == MainDestination.LIBRARY) state.value.tab else LibraryTab.CATALOG
        browse(
            query = if (destination == MainDestination.SEARCH) state.value.query else "",
            tab = tab,
        )
    }

    override fun search(query: String) = browse(query.trim().take(200))
    override fun selectTab(tab: LibraryTab) = browse(query = "", tab = tab)
    override fun refresh() = browse()

    override fun loadMore() {
        state.value = state.value.copy(loadingMore = false, hasMore = false)
    }

    override fun loadMoreAlbums() {
        state.value = state.value.copy(albumsLoadingMore = false, albumsHasMore = false)
    }

    override fun openAlbum(album: Album) {
        val localAlbum = fixture.albums.firstOrNull { it.id == album.id } ?: return
        state.value = state.value.copy(
            selectedAlbum = localAlbum,
            albumSongs = fixture.albumSongs(localAlbum),
            albumLoading = false,
            albumNotice = null,
        )
    }

    override fun closeAlbum() {
        state.value = state.value.copy(
            selectedAlbum = null, albumSongs = emptyList(), albumNotice = null,
        )
    }

    override fun login(instanceUrl: String, username: String, password: String) = openApplication()
    override fun changePassword(currentPassword: String, newPassword: String) = openApplication()
    override fun logout() = openApplication()

    private fun updatePlayback(transform: (PlaybackSnapshot) -> PlaybackSnapshot) {
        state.value = state.value.copy(playback = transform(state.value.playback))
    }

    override fun playSong(song: Song) {
        val localSong = fixture.songs.firstOrNull { it.id == song.id } ?: return
        val currentState = state.value
        val candidates = when {
            currentState.selectedAlbum != null -> currentState.albumSongs
            currentState.songs.any { it.id == localSong.id } -> currentState.songs
            else -> fixture.songs
        }
        val queue = candidates.ifEmpty { fixture.songs }
        val index = queue.indexOfFirst { it.id == localSong.id }
        if (index < 0) return
        updatePlayback {
            it.copy(
                current = localSong, queue = queue, currentIndex = index,
                playing = true, positionMs = 0, buffering = false, failed = false,
                durationMs = (localSong.durationSeconds * 1000).toLong(),
            )
        }
    }

    override fun togglePlay() = updatePlayback {
        val position = if (it.positionMs >= it.durationMs) 0 else it.positionMs
        it.copy(playing = !it.playing, positionMs = position, buffering = false)
    }

    private fun goTo(index: Int) {
        val song = state.value.playback.queue.getOrNull(index) ?: return
        updatePlayback {
            it.copy(
                current = song, currentIndex = index, playing = true, positionMs = 0,
                durationMs = (song.durationSeconds * 1000).toLong(), failed = false, buffering = false,
            )
        }
    }

    override fun next() {
        val playback = state.value.playback
        if (playback.queue.isEmpty()) return
        val next = when {
            playback.shuffle && playback.queue.size > 1 ->
                (playback.currentIndex + Random.nextInt(1, playback.queue.size)).mod(playback.queue.size)
            playback.currentIndex < playback.queue.lastIndex -> playback.currentIndex + 1
            playback.repeatMode == 2 -> 0
            else -> return
        }
        goTo(next)
    }

    override fun previous() {
        val playback = state.value.playback
        when {
            playback.positionMs > 3000 -> seekTo(0)
            playback.currentIndex > 0 -> goTo(playback.currentIndex - 1)
            playback.repeatMode == 2 && playback.queue.isNotEmpty() -> goTo(playback.queue.lastIndex)
            playback.shuffle && playback.queue.size > 1 -> next()
            else -> seekTo(0)
        }
    }

    override fun seekTo(positionMs: Long) = updatePlayback {
        it.copy(positionMs = positionMs.coerceIn(0, it.durationMs.coerceAtLeast(0)))
    }

    override fun setVolume(volume: Float) = updatePlayback {
        it.copy(volume = if (volume.isFinite()) volume.coerceIn(0f, 1f) else 1f)
    }

    override fun toggleShuffle() = updatePlayback { it.copy(shuffle = !it.shuffle) }
    override fun cycleRepeat() = updatePlayback { it.copy(repeatMode = (it.repeatMode + 1) % 3) }
    override fun selectQueueItem(index: Int) = goTo(index)
    override fun retryPlayback() = updatePlayback {
        it.copy(failed = false, buffering = false, playing = true)
    }

    fun tick(milliseconds: Long) {
        val playback = state.value.playback
        if (!playback.playing) return
        val nextPosition = playback.positionMs + milliseconds
        when {
            nextPosition < playback.durationMs -> updatePlayback { it.copy(positionMs = nextPosition) }
            playback.repeatMode == 1 -> seekTo(0)
            playback.currentIndex < playback.queue.lastIndex || playback.repeatMode == 2 ||
                (playback.shuffle && playback.queue.size > 1) -> next()
            else -> updatePlayback { it.copy(positionMs = it.durationMs, playing = false) }
        }
    }
}

/** Original geometric artwork is generated in memory; no files or media are fetched. */
private fun createCover(index: Int): Bitmap {
    val palettes = arrayOf(
        intArrayOf(0xFF7A3F28.toInt(), 0xFFE5A35C.toInt(), 0xFFF8DDAC.toInt()),
        intArrayOf(0xFF14384C.toInt(), 0xFF2D7F94.toInt(), 0xFFB4D7D7.toInt()),
        intArrayOf(0xFF244A38.toInt(), 0xFF98AF60.toInt(), 0xFFE7EAC5.toInt()),
        intArrayOf(0xFF4A5C70.toInt(), 0xFF829DA5.toInt(), 0xFFEEE2C7.toInt()),
        intArrayOf(0xFF72534B.toInt(), 0xFFC48D72.toInt(), 0xFFF2DCC1.toInt()),
        intArrayOf(0xFF45394F.toInt(), 0xFF9B6382.toInt(), 0xFFE5C4CF.toInt()),
        intArrayOf(0xFF3F4651.toInt(), 0xFFB06C53.toInt(), 0xFFEBD3B0.toInt()),
        intArrayOf(0xFF284A4D.toInt(), 0xFF73A6A1.toInt(), 0xFFDFE8D5.toInt()),
    )
    val palette = palettes[index.mod(palettes.size)]
    val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f, 0f, 512f, 512f, palette[0], palette[1], Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, 512f, 512f, paint)
    paint.shader = null
    paint.color = palette[2]
    when (index % 4) {
        0 -> {
            canvas.drawCircle(350f, 160f, 104f, paint)
            paint.color = palette[0]
            val path = Path().apply {
                moveTo(0f, 430f); lineTo(190f, 210f); lineTo(512f, 400f)
                lineTo(512f, 512f); lineTo(0f, 512f); close()
            }
            canvas.drawPath(path, paint)
        }
        1 -> {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 18f
            for (line in 0..6) {
                canvas.drawCircle(420f, 100f, 95f + line * 48f, paint)
            }
            paint.style = Paint.Style.FILL
        }
        2 -> {
            for (column in 0..3) {
                for (row in 0..3) {
                    paint.alpha = 90 + (column + row) * 20
                    canvas.drawCircle(80f + column * 115f, 80f + row * 115f, 35f, paint)
                }
            }
            paint.alpha = 255
        }
        else -> {
            for (band in 0..5) {
                paint.alpha = 60 + band * 25
                val path = Path().apply {
                    moveTo(-80f, 160f + band * 60f)
                    cubicTo(110f, 20f + band * 60f, 300f, 330f + band * 60f, 600f, 100f + band * 60f)
                    lineTo(600f, 600f); lineTo(-80f, 600f); close()
                }
                canvas.drawPath(path, paint)
            }
            paint.alpha = 255
        }
    }
    paint.color = Color.WHITE
    paint.alpha = 210
    paint.textSize = 28f
    canvas.drawText("FLARE / ${index + 1}", 32f, 470f, paint)
    return bitmap
}
