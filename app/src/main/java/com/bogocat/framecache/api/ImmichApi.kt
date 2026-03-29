package com.bogocat.framecache.api

import com.bogocat.framecache.api.model.AlbumResponse
import com.bogocat.framecache.api.model.ServerAbout
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ImmichApi {

    @GET("api/server/about")
    suspend fun getServerAbout(): ServerAbout

    @GET("api/albums")
    suspend fun getAlbums(): List<AlbumResponse>

    @GET("api/albums/{id}")
    suspend fun getAlbum(@Path("id") albumId: String): AlbumResponse

    @GET("api/assets/{id}/thumbnail")
    suspend fun getThumbnail(
        @Path("id") assetId: String,
        @Query("size") size: String = "preview"
    ): ResponseBody
}
