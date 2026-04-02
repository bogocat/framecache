package com.bogocat.framecache.api.navidrome

import retrofit2.http.GET
import retrofit2.http.Query

interface SubsonicApi {

    @GET("rest/ping")
    suspend fun ping(): SubsonicEnvelope<SubsonicResponse>

    @GET("rest/getNowPlaying")
    suspend fun getNowPlaying(): SubsonicEnvelope<NowPlayingResponse>

    @GET("rest/getPlaylists")
    suspend fun getPlaylists(): SubsonicEnvelope<PlaylistsResponse>

    @GET("rest/getPlaylist")
    suspend fun getPlaylist(@Query("id") id: String): SubsonicEnvelope<PlaylistResponse>

    @GET("rest/getSong")
    suspend fun getSong(@Query("id") id: String): SubsonicEnvelope<SongResponse>

    @GET("rest/getRandomSongs")
    suspend fun getRandomSongs(
        @Query("size") size: Int = 20
    ): SubsonicEnvelope<RandomSongsResponse>

    @GET("rest/getAlbum")
    suspend fun getAlbum(@Query("id") id: String): SubsonicEnvelope<AlbumResponse>

    @GET("rest/getAlbumList2")
    suspend fun getAlbumList(
        @Query("type") type: String = "recent",
        @Query("size") size: Int = 20,
        @Query("offset") offset: Int = 0
    ): SubsonicEnvelope<AlbumListResponse>

    @GET("rest/getArtist")
    suspend fun getArtist(@Query("id") id: String): SubsonicEnvelope<ArtistResponse>

    @GET("rest/search3")
    suspend fun search(
        @Query("query") query: String,
        @Query("songCount") songCount: Int = 20,
        @Query("albumCount") albumCount: Int = 10,
        @Query("artistCount") artistCount: Int = 10
    ): SubsonicEnvelope<SearchResponse>

    @GET("rest/scrobble")
    suspend fun scrobble(
        @Query("id") id: String,
        @Query("submission") submission: Boolean = false
    ): SubsonicEnvelope<SubsonicResponse>
}
