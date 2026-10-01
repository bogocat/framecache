package com.bogocat.framecache.music

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.bogocat.framecache.api.denon.DenonClient
import com.bogocat.framecache.api.navidrome.NavidromeClient
import com.bogocat.framecache.api.navidrome.Song
import com.bogocat.framecache.data.cache.MusicCacheManager
import com.bogocat.framecache.data.db.CachedSong
import com.bogocat.framecache.data.db.SongDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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
    private val cacheManager: MusicCacheManager,
    private val denonClient: DenonClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _nowPlaying = MutableStateFlow(NowPlaying())
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    private val _queue = MutableStateFlow(QueueState())
    val queue: StateFlow<QueueState> = _queue.asStateFlow()

    // When true, audio is sent to the Denon receiver instead of the local speaker.
    private val _denonOutput = MutableStateFlow(false)
    val denonOutput: StateFlow<Boolean> = _denonOutput.asStateFlow()
    private var denonPollJob: Job? = null
    // Denon position tracking (the receiver reports position on a ~5s poll; we
    // interpolate locally so the progress bar moves smoothly in between).
    private var denonPositionMs: Long = 0
    private var denonPositionAt: Long = 0
    private var denonDurationMs: Long = 0

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
     * When Denon output is enabled the receiver streams the track directly.
     */
    suspend fun play(song: Song) {
        if (_denonOutput.value) {
            playOnDenon(song)
            return
        }
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

    // -- Denon receiver output --

    /** Route playback to the Denon receiver (and re-cast the current track). */
    fun setDenonOutput(on: Boolean) {
        if (_denonOutput.value == on) return
        _denonOutput.value = on
        if (on) {
            player?.pause()
            startDenonPoller()
            val current = _nowPlaying.value.song
            if (current.id.isNotEmpty()) scope.launch { playOnDenon(current) }
        } else {
            denonPollJob?.cancel()
            denonPollJob = null
            scope.launch { denonClient.stop() }
        }
    }

    private suspend fun playOnDenon(song: Song) {
        val url = navidromeClient.getStreamUrl(song.id)
        val ok = denonClient.playStream(url, song.title, song.artist, song.album)
        denonPositionMs = 0
        denonPositionAt = SystemClock.elapsedRealtime()
        denonDurationMs = song.duration * 1000L
        val coverUrl = song.coverArt?.let { navidromeClient.getCoverArtUrl(it, 600) } ?: ""
        _nowPlaying.value = NowPlaying(
            song = song,
            coverArtUrl = coverUrl,
            isPlaying = ok,
            duration = song.duration * 1000L,
            cached = false
        )
        songDao.markPlayed(song.id)
        try { navidromeClient.scrobble(song.id, submission = false) } catch (_: Exception) {}
    }

    /** The Denon tells us when a track ends, so poll and advance the queue from there. */
    private fun startDenonPoller() {
        denonPollJob?.cancel()
        denonPollJob = scope.launch {
            while (isActive && _denonOutput.value) {
                delay(5000)
                if (!_denonOutput.value) break
                val state = denonClient.getTransportState()
                if (state == "PLAYING") {
                    denonClient.getPositionInfo()?.let { info ->
                        denonPositionMs = info.positionMs
                        if (info.durationMs > 0) denonDurationMs = info.durationMs
                        denonPositionAt = SystemClock.elapsedRealtime()
                    }
                }
                val playing = state == "PLAYING"
                if (_nowPlaying.value.isPlaying != playing) {
                    _nowPlaying.value = _nowPlaying.value.copy(isPlaying = playing)
                }
                if (state == "STOPPED" || state == "NO_MEDIA_PRESENT") {
                    autoAdvance()
                }
            }
        }
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
    suspend fun playCachedQueue(
        songs: List<CachedSong>,
        startIndex: Int = 0,
        source: String = "",
        shuffle: Boolean = false
    ) {
        playQueue(songs.map { it.toSong() }, startIndex = startIndex, source = source, shuffle = shuffle)
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

    fun removeFromQueue(index: Int) {
        val q = _queue.value
        if (index < 0 || index >= q.items.size) return
        val newItems = q.items.toMutableList().apply { removeAt(index) }
        val newIndex = when {
            newItems.isEmpty() -> -1
            index < q.currentIndex -> q.currentIndex - 1
            index == q.currentIndex -> q.currentIndex.coerceAtMost(newItems.size - 1)
            else -> q.currentIndex
        }
        _queue.value = q.copy(items = newItems, currentIndex = newIndex)
    }

    fun clearQueue() {
        player?.stop()
        if (_denonOutput.value) scope.launch { denonClient.stop() }
        _queue.value = QueueState()
        _nowPlaying.value = NowPlaying()
    }

    suspend fun addToQueue(song: Song) {
        val q = _queue.value
        _queue.value = q.copy(items = q.items + song)
    }

    suspend fun addToQueue(songs: List<Song>) {
        val q = _queue.value
        _queue.value = q.copy(items = q.items + songs)
    }

    fun togglePlayPause() {
        if (_denonOutput.value) {
            scope.launch {
                val playing = _nowPlaying.value.isPlaying
                if (playing) denonClient.pause() else denonClient.play()
                _nowPlaying.value = _nowPlaying.value.copy(isPlaying = !playing)
            }
            return
        }
        val exo = player ?: return
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun seekTo(positionMs: Long) {
        if (_denonOutput.value) {
            scope.launch {
                if (denonClient.seek(positionMs)) {
                    denonPositionMs = positionMs
                    denonPositionAt = SystemClock.elapsedRealtime()
                }
            }
            return
        }
        player?.seekTo(positionMs)
    }

    fun setVolume(volume: Float) {
        player?.volume = volume.coerceIn(0f, 1f)
    }

    fun getPosition(): Long {
        if (_denonOutput.value) {
            if (!_nowPlaying.value.isPlaying) return denonPositionMs
            val pos = denonPositionMs + (SystemClock.elapsedRealtime() - denonPositionAt)
            return if (denonDurationMs > 0) pos.coerceIn(0L, denonDurationMs) else pos
        }
        return player?.currentPosition ?: 0L
    }

    fun getDuration(): Long {
        if (_denonOutput.value) {
            return denonDurationMs.takeIf { it > 0 } ?: _nowPlaying.value.duration
        }
        return player?.duration?.takeIf { it > 0 } ?: _nowPlaying.value.duration
    }

    fun isActive(): Boolean = (_denonOutput.value && _nowPlaying.value.isPlaying) ||
            player?.isPlaying == true ||
            (player?.playbackState == Player.STATE_READY && player?.playWhenReady == false)

    fun release() {
        denonPollJob?.cancel()
        denonPollJob = null
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
        artistId = artistId,
        coverArt = coverArt,
        duration = duration,
        track = track,
        year = year,
        genre = genre
    )
}
