package com.bogocat.immichframe.api.model

data class AssetDto(
    val id: String,
    val type: String = "IMAGE",
    val thumbhash: String? = null,
    val originalFileName: String? = null,
    val localDateTime: String? = null,
    val fileCreatedAt: String? = null,
    val isFavorite: Boolean = false,
    val exifInfo: ExifInfoDto? = null,
    val people: List<PersonDto>? = null,
    val width: Int? = null,
    val height: Int? = null
)

data class ExifInfoDto(
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val description: String? = null,
    val make: String? = null,
    val model: String? = null,
    val dateTimeOriginal: String? = null,
    val rating: Int? = null
)

data class PersonDto(
    val id: String,
    val name: String? = null,
    val birthDate: String? = null
)
