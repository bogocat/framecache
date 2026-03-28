package com.bogocat.immichframe.api.model

import com.google.gson.annotations.SerializedName

data class AlbumResponse(
    val id: String,
    val albumName: String,
    val assetCount: Int = 0,
    val assets: List<AssetDto> = emptyList()
)

data class ServerAbout(
    val version: String = ""
)
