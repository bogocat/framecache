package com.bogocat.framecache.api.navidrome

import android.net.Uri
import com.bogocat.framecache.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level Navidrome client that wraps the Subsonic API.
 * Provides convenience methods and authenticated URL builders for
 * cover art (Coil) and audio streaming (ExoPlayer).
 */
@Singleton
class NavidromeClient @Inject constructor(
    private val api: SubsonicApi,
    private val settings: SettingsRepository
) {
    private val secureRandom = SecureRandom()

    suspend fun ping(): Boolean {
        return try {
            api.ping().response.status == "ok"
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getNowPlaying(): List<NowPlayingEntry> {
        return api.getNowPlaying().response.nowPlaying?.entry.orEmpty()
    }

    suspend fun getPlaylists(): List<Playlist> {
        return api.getPlaylists().response.playlists?.playlist.orEmpty()
    }

    suspend fun getPlaylist(id: String): Pair<Playlist, List<Song>> {
        val playlist = api.getPlaylist(id).response.playlist
            ?: return Pair(Playlist(), emptyList())
        return Pair(playlist, playlist.entry.orEmpty())
    }

    suspend fun getSong(id: String): Song? {
        return try {
            api.getSong(id).response.song
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getRandomSongs(size: Int = 20): List<Song> {
        return api.getRandomSongs(size).response.randomSongs?.song.orEmpty()
    }

    suspend fun getAlbum(id: String): Pair<Album, List<Song>> {
        val album = api.getAlbum(id).response.album
            ?: return Pair(Album(), emptyList())
        return Pair(album, album.song.orEmpty())
    }

    suspend fun getAlbumList(type: String = "recent", size: Int = 20): List<Album> {
        return api.getAlbumList(type, size).response.albumList2?.album.orEmpty()
    }

    suspend fun getArtist(id: String): Pair<Artist, List<Album>> {
        val artist = api.getArtist(id).response.artist
            ?: return Pair(Artist(), emptyList())
        return Pair(artist, artist.album.orEmpty())
    }

    suspend fun search(query: String): SearchResult {
        return api.search(query).response.searchResult3 ?: SearchResult()
    }

    suspend fun scrobble(songId: String, submission: Boolean = false) {
        api.scrobble(songId, submission)
    }

    /**
     * Build an authenticated cover art URL for use with Coil image loader.
     */
    suspend fun getCoverArtUrl(coverArt: String, size: Int = 300): String {
        val baseUrl = settings.navidromeUrl.first().trimEnd('/')
        return buildAuthenticatedUrl("$baseUrl/rest/getCoverArt") {
            appendQueryParameter("id", coverArt)
            appendQueryParameter("size", size.toString())
        }
    }

    /**
     * Build an authenticated stream URL for use with ExoPlayer.
     */
    suspend fun getStreamUrl(songId: String, maxBitRate: Int? = null): String {
        val baseUrl = settings.navidromeUrl.first().trimEnd('/')
        return buildAuthenticatedUrl("$baseUrl/rest/stream") {
            appendQueryParameter("id", songId)
            maxBitRate?.let { appendQueryParameter("maxBitRate", it.toString()) }
        }
    }

    private suspend fun buildAuthenticatedUrl(
        base: String,
        block: Uri.Builder.() -> Unit
    ): String {
        val username = settings.navidromeUsername.first()
        val password = settings.navidromePassword.first()
        val salt = generateSalt()
        val token = md5("$password$salt")

        return Uri.parse(base).buildUpon()
            .appendQueryParameter("u", username)
            .appendQueryParameter("t", token)
            .appendQueryParameter("s", salt)
            .appendQueryParameter("v", "1.16.1")
            .appendQueryParameter("c", "FrameCache")
            .apply(block)
            .build()
            .toString()
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(12)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
