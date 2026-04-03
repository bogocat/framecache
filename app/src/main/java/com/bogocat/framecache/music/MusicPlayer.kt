package com.bogocat.framecache.music

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.bogocat.framecache.api.navidrome.NavidromeClient
import com.bogocat.framecache.api.navidrome.Song
import com.bogocat.framecache.data.cache.MusicCacheManager
import com.bogocat.framecache.data.db.CachedSong
import com.bogocat.framecache.data.db.SongDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class NowPlaying(
    val song: Song = Song(),
    val coverArtUrl: String = "",
    val isPlaying: Boolean = false,
    val duration: Long = 0L,
    val position: Long = 0L,
    val cached: Boolean = false
)

data class QueueState(
    val items: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val shuffle: Boolean = false,
    val source: String = ""
)

@Singleton
class MusicPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val navidromeClient: NavidromeClient,
    private val songDao: SongDao,
    private val cacheManager: MusicCacheManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _nowPlaying = MutableStateFlow(NowPlaying())
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    private val _queue = MutableStateFlow(QueueState())
    val queue: StateFlow<QueueState> = _queue.asStateFlow()

    private var player: ExoPlayer? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _nowPlaying.value = _nowPlaying.value.copy(isPlaying = isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                scope.launch { autoAdvance() }
            }
        }
    }

    /** Called only by the player listener on track end — not by UI skip buttons. */
    private suspend fun autoAdvance() {
        val q = _queue.value
        if (q.items.isEmpty()) return
        val nextIndex = (q.currentIndex + 1) % q.items.size
        _queue.value = q.copy(currentIndex = nextIndex)
        play(q.items[nextIndex])
    }

    @OptIn(UnstableApi::class)
    private fun getOrCreatePlayer(): ExoPlayer {
        // DefaultDataSource handles file:// URIs locally, delegates http(s) to OkHttp
        val dataSourceFactory = DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(OkHttpClient())
        )
        return player ?: ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .also {
                it.addListener(playerListener)
                player = it
            }
    }

    /**
     * Play a song. Uses local cache if available, otherwise streams.
     */
    suspend fun play(song: Song) {
        // Check cache first
        val cachedSong = songDao.getById(song.id)
        val isCached = cachedSong?.filePath != null && File(cachedSong.filePath).exists()
        val audioUri = if (isCached) {
            Uri.fromFile(File(cachedSong!!.filePath!!))
        } else {
            Uri.parse(navidromeClient.getStreamUrl(song.id))
        }

        // Cover: local cache or remote URL
        val coverUrl = if (cachedSong?.coverPath != null && File(cachedSong.coverPath).exists()) {
            Uri.fromFile(File(cachedSong.coverPath)).toString()
        } else {
            song.coverArt?.let { navidromeClient.getCoverArtUrl(it, 600) } ?: ""
        }

        val mediaItem = MediaItem.Builder()
            .setUri(audioUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(if (coverUrl.isNotEmpty()) Uri.parse(coverUrl) else null)
                    .build()
            )
            .build()

        val exo = getOrCreatePlayer()
        exo.setMediaItem(mediaItem)
        exo.prepare()
        exo.play()

        _nowPlaying.value = NowPlaying(
            song = song,
            coverArtUrl = coverUrl,
            isPlaying = true,
            duration = song.duration * 1000L,
            cached = isCached
        )

        // Track play count
        songDao.markPlayed(song.id)

        // Scrobble (best effort)
        try { navidromeClient.scrobble(song.id, submission = false) } catch (_: Exception) {}
    }

    suspend fun playQueue(songs: List<Song>, startIndex: Int = 0, source: String = "", shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val queue = if (shuffle) songs.shuffled() else songs
        _queue.value = QueueState(items = queue, currentIndex = startIndex, shuffle = shuffle, source = source)
        play(queue[startIndex])
    }

    /**
     * Play from cached songs. Converts CachedSong → Song for the queue.
     */
    suspend fun playCachedQueue(songs: List<CachedSong>, source: String = "", shuffle: Boolean = false) {
        playQueue(songs.map { it.toSong() }, source = source, shuffle = shuffle)
    }

    /**
     * Shuffle all cached songs.
     */
    suspend fun shuffleAll() {
        val songs = songDao.getShuffleQueue(50)
        if (songs.isEmpty()) return
        playCachedQueue(songs, source = "Shuffle All", shuffle = true)
    }

    /**
     * Play a cached playlist.
     */
    suspend fun playPlaylist(playlistId: String, playlistName: String, shuffle: Boolean = false) {
        val songs = songDao.getSongsForPlaylist(playlistId)
        if (songs.isEmpty()) return
        playCachedQueue(songs, source = playlistName, shuffle = shuffle)
    }

    suspend fun skipNext() {
        val q = _queue.value
        if (q.items.isEmpty()) return

        // Scrobble completed track
        val finished = q.items.getOrNull(q.currentIndex)
        if (finished != null) {
            try { navidromeClient.scrobble(finished.id, submission = true) } catch (_: Exception) {}
        }

        val nextIndex = (q.currentIndex + 1) % q.items.size
        _queue.value = q.copy(currentIndex = nextIndex)
        play(q.items[nextIndex])
    }

    suspend fun skipPrevious() {
        val q = _queue.value
        if (q.items.isEmpty()) return

        val exo = player
        if (exo != null && exo.currentPosition > 3000) {
            exo.seekTo(0)
            return
        }

        val prevIndex = if (q.currentIndex > 0) q.currentIndex - 1 else q.items.size - 1
        _queue.value = q.copy(currentIndex = prevIndex)
        play(q.items[prevIndex])
    }

    fun togglePlayPause() {
        val exo = player ?: return
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun setVolume(volume: Float) {
        player?.volume = volume.coerceIn(0f, 1f)
    }

    fun getPosition(): Long = player?.currentPosition ?: 0L

    fun getDuration(): Long = player?.duration?.takeIf { it > 0 } ?: _nowPlaying.value.duration

    fun isActive(): Boolean = player?.isPlaying == true ||
            (player?.playbackState == Player.STATE_READY && player?.playWhenReady == false)

    fun release() {
        player?.removeListener(playerListener)
        player?.release()
        player = null
        _nowPlaying.value = NowPlaying()
        _queue.value = QueueState()
    }

    private fun CachedSong.toSong() = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        coverArt = coverArt,
        duration = duration,
        track = track,
        year = year,
        genre = genre
    )
}
