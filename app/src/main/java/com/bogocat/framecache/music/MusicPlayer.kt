package com.bogocat.framecache.music

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.bogocat.framecache.api.navidrome.NavidromeClient
import com.bogocat.framecache.api.navidrome.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

data class NowPlaying(
    val song: Song = Song(),
    val coverArtUrl: String = "",
    val isPlaying: Boolean = false,
    val duration: Long = 0L,
    val position: Long = 0L
)

data class QueueState(
    val items: List<Song> = emptyList(),
    val currentIndex: Int = -1
)

@Singleton
class MusicPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val navidromeClient: NavidromeClient
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
                scope.launch { skipNext() }
            }
        }

        override fun onMediaMetadataChanged(metadata: MediaMetadata) {
            // Position/duration tracked via polling in UI layer
        }
    }

    @OptIn(UnstableApi::class)
    private fun getOrCreatePlayer(): ExoPlayer {
        return player ?: ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    OkHttpDataSource.Factory(OkHttpClient())
                )
            )
            .build()
            .also {
                it.addListener(playerListener)
                player = it
            }
    }

    suspend fun play(song: Song) {
        val streamUrl = navidromeClient.getStreamUrl(song.id)
        val coverUrl = song.coverArt?.let { navidromeClient.getCoverArtUrl(it, 600) } ?: ""

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
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
            duration = song.duration * 1000L
        )

        navidromeClient.scrobble(song.id, submission = false)
    }

    suspend fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        _queue.value = QueueState(items = songs, currentIndex = startIndex)
        play(songs[startIndex])
    }

    suspend fun skipNext() {
        val q = _queue.value
        if (q.items.isEmpty()) return

        val nextIndex = (q.currentIndex + 1) % q.items.size
        _queue.value = q.copy(currentIndex = nextIndex)

        // Scrobble the completed track
        val finished = q.items.getOrNull(q.currentIndex)
        if (finished != null) {
            navidromeClient.scrobble(finished.id, submission = true)
        }

        play(q.items[nextIndex])
    }

    suspend fun skipPrevious() {
        val q = _queue.value
        if (q.items.isEmpty()) return

        // If past 3 seconds, restart current track
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
        if (exo.isPlaying) {
            exo.pause()
        } else {
            exo.play()
        }
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
}
