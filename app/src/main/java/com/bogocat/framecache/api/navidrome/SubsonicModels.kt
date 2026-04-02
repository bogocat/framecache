package com.bogocat.framecache.api.navidrome

import com.google.gson.annotations.SerializedName

/**
 * Generic Subsonic API response envelope.
 * All responses are wrapped in {"subsonic-response": {...}}.
 */
data class SubsonicEnvelope<T>(
    @SerializedName("subsonic-response")
    val response: T
)

open class SubsonicResponse(
    val status: String = "",
    val version: String = "",
    val error: SubsonicError? = null
)

data class SubsonicError(
    val code: Int = 0,
    val message: String = ""
)

// -- getNowPlaying --

class NowPlayingResponse(
    status: String = "",
    version: String = "",
    error: SubsonicError? = null,
    val nowPlaying: NowPlayingList? = null
) : SubsonicResponse(status, version, error)

data class NowPlayingList(
    val entry: List<NowPlayingEntry>? = null
)

data class NowPlayingEntry(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumId: String = "",
    val coverArt: String? = null,
    val duration: Int = 0,
    val minutesAgo: Int = 0,
    val playerName: String? = null,
    val username: String = ""
)

// -- getPlaylists --

class PlaylistsResponse(
    status: String = "",
    version: String = "",
    error: SubsonicError? = null,
    val playlists: PlaylistList? = null
) : SubsonicResponse(status, version, error)

data class PlaylistList(
    val playlist: List<Playlist>? = null
)

data class Playlist(
    val id: String = "",
    val name: String = "",
    val songCount: Int = 0,
    val duration: Int = 0,
    val owner: String = "",
    val coverArt: String? = null,
    val entry: List<Song>? = null
)

// -- getPlaylist / getAlbum / search3 / getRandomSongs / getSong --

class PlaylistResponse(
    status: String = "",
    version: String = "",
    error: SubsonicError? = null,
    val playlist: Playlist? = null
) : SubsonicResponse(status, version, error)

data class Song(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumId: String = "",
    val artistId: String = "",
    val coverArt: String? = null,
    val duration: Int = 0,
    val track: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val size: Long? = null,
    val suffix: String? = null,
    val bitRate: Int? = null
)

class SongResponse(
    status: String = "",
    version: String = "",
    error: SubsonicError? = null,
    val song: Song? = null
) : SubsonicResponse(status, version, error)

class RandomSongsResponse(
    status: String = "",
    version: String = "",
    error: SubsonicError? = null,
    val randomSongs: SongList? = null
) : SubsonicResponse(status, version, error)

data class SongList(
    val song: List<Song>? = null
)

// -- getAlbum / getAlbumList2 --

data class Album(
    val id: String = "",
    val name: String = "",
    val artist: String = "",
    val artistId: String = "",
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val year: Int? = null,
    val genre: String? = null,
    val song: List<Song>? = null
)

class AlbumResponse(
    status: String = "",
    version: String = "",
    error: SubsonicError? = null,
    val album: Album? = null
) : SubsonicResponse(status, version, error)

class AlbumListResponse(
    status: String = "",
    version: String = "",
    error: SubsonicError? = null,
    val albumList2: AlbumList? = null
) : SubsonicResponse(status, version, error)

data class AlbumList(
    val album: List<Album>? = null
)

// -- getArtist --

data class Artist(
    val id: String = "",
    val name: String = "",
    val coverArt: String? = null,
    val albumCount: Int = 0,
    val album: List<Album>? = null
)

class ArtistResponse(
    status: String = "",
    version: String = "",
    error: SubsonicError? = null,
    val artist: Artist? = null
) : SubsonicResponse(status, version, error)

// -- search3 --

class SearchResponse(
    status: String = "",
    version: String = "",
    error: SubsonicError? = null,
    val searchResult3: SearchResult? = null
) : SubsonicResponse(status, version, error)

data class SearchResult(
    val artist: List<Artist>? = null,
    val album: List<Album>? = null,
    val song: List<Song>? = null
)
