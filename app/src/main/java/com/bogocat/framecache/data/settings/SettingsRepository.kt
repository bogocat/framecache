package com.bogocat.framecache.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object Keys {
        // Connection
        val SERVER_URL = stringPreferencesKey("server_url")
        val API_KEY = stringPreferencesKey("api_key")
        val ALBUM_IDS = stringPreferencesKey("album_ids")

        // Slideshow
        val DURATION = intPreferencesKey("duration")
        val CROSSFADE_DURATION = intPreferencesKey("crossfade_duration")
        val KEN_BURNS_ENABLED = booleanPreferencesKey("ken_burns_enabled")
        val KEN_BURNS_ZOOM = intPreferencesKey("ken_burns_zoom")
        val BACKGROUND_BLUR = booleanPreferencesKey("background_blur")
        val IMAGE_SCALE = stringPreferencesKey("image_scale")
        val SHOW_PROGRESS_BAR = booleanPreferencesKey("show_progress_bar")
        val PHOTO_ORDER = stringPreferencesKey("photo_order")
        val FAVORITES_ONLY = booleanPreferencesKey("favorites_only")
        val CLOCK_FORMAT = stringPreferencesKey("clock_format")
        val SHOW_RATING = booleanPreferencesKey("show_rating")
        val SHOW_PERSON_AGE = booleanPreferencesKey("show_person_age")
        val ORIENTATION_MODE = stringPreferencesKey("orientation_mode")

        // Orientation modes: which photos may appear on screen.
        const val ORIENTATION_ALL = "all"                 // every photo, shown alone
        const val ORIENTATION_MATCH = "match"             // only photos matching the screen orientation
        const val ORIENTATION_MATCH_PAIR = "match_pair"   // matching photos + opposite orientation two-up

        // Overlays
        val SHOW_CLOCK = booleanPreferencesKey("show_clock")
        val SHOW_DATE = booleanPreferencesKey("show_date")
        val SHOW_PHOTO_DATE = booleanPreferencesKey("show_photo_date")
        val SHOW_LOCATION = booleanPreferencesKey("show_location")
        val SHOW_DESCRIPTION = booleanPreferencesKey("show_description")
        val SHOW_PEOPLE = booleanPreferencesKey("show_people")
        val SHOW_CAMERA = booleanPreferencesKey("show_camera")
        val DATE_FORMAT = stringPreferencesKey("date_format")

        // Overlay presentation
        val OVERLAY_TEXT_SIZE = stringPreferencesKey("overlay_text_size")
        val OVERLAY_TEXT_COLOR = stringPreferencesKey("overlay_text_color")
        val OVERLAY_BACKGROUND = booleanPreferencesKey("overlay_background")
        val OVERLAY_BACKGROUND_OPACITY = intPreferencesKey("overlay_background_opacity")
        val OVERLAY_CLOCK_POSITION = stringPreferencesKey("overlay_clock_position")
        val OVERLAY_INFO_POSITION = stringPreferencesKey("overlay_info_position")
        val OVERLAY_CORNER_RADIUS = intPreferencesKey("overlay_corner_radius")
        val OVERLAY_ANIMATION = stringPreferencesKey("overlay_animation")
        val OVERLAY_MARQUEE = booleanPreferencesKey("overlay_marquee")
        val OVERLAY_EXPAND_SCALE = intPreferencesKey("overlay_expand_scale")
        val OVERLAY_COLLAPSED_SCALE = intPreferencesKey("overlay_collapsed_scale")
        val OVERLAY_COLLAPSED_SECONDS = intPreferencesKey("overlay_collapsed_seconds")
        val OVERLAY_EXPANDED_SECONDS = intPreferencesKey("overlay_expanded_seconds")
        val OVERLAY_EXPANDED_INDEFINITE = booleanPreferencesKey("overlay_expanded_indefinite")
        val OVERLAY_COLLAPSED_INDEFINITE = booleanPreferencesKey("overlay_collapsed_indefinite")
        val OVERLAY_COLLAPSED_FIELDS = stringPreferencesKey("overlay_collapsed_fields")

        // Overlay position values
        const val OVERLAY_POS_TOP_START = "top_start"
        const val OVERLAY_POS_TOP_END = "top_end"
        const val OVERLAY_POS_BOTTOM_START = "bottom_start"
        const val OVERLAY_POS_BOTTOM_END = "bottom_end"

        // Overlay animation modes
        const val OVERLAY_ANIM_STATIC = "static"
        const val OVERLAY_ANIM_LOOP = "loop"
        const val OVERLAY_ANIM_ONCE = "once"
        // Legacy value kept only for reading pre-existing preferences.
        const val OVERLAY_ANIM_LEGACY_EXPAND = "expand"

        // Overlay field ids (used by the collapsed-fields selection)
        const val OVERLAY_FIELD_DATE = "date"
        const val OVERLAY_FIELD_LOCATION = "location"
        const val OVERLAY_FIELD_DESCRIPTION = "description"
        const val OVERLAY_FIELD_PEOPLE = "people"
        const val OVERLAY_FIELD_CAMERA = "camera"
        const val OVERLAY_FIELD_RATING = "rating"

        // Now-playing pill (over the slideshow)
        val NP_SHOW_ART = booleanPreferencesKey("np_show_art")
        val NP_SHOW_TITLE = booleanPreferencesKey("np_show_title")
        val NP_SHOW_ARTIST = booleanPreferencesKey("np_show_artist")
        val NP_SHOW_CONTROLS = booleanPreferencesKey("np_show_controls")
        val NP_EXPANDED_SCALE = intPreferencesKey("np_expanded_scale")
        val NP_COLLAPSED_SCALE = intPreferencesKey("np_collapsed_scale")
        // Legacy single-size key; used as a fallback so existing installs keep their size.
        val NP_SCALE_LEGACY = intPreferencesKey("np_scale")
        val NP_BACKGROUND_OPACITY = intPreferencesKey("np_background_opacity")
        val NP_CORNER_RADIUS = intPreferencesKey("np_corner_radius")
        val NP_ANIMATION = stringPreferencesKey("np_animation")
        val NP_LOOP = booleanPreferencesKey("np_loop")
        val NP_EXPANDED_SECONDS = intPreferencesKey("np_expanded_seconds")
        val NP_COLLAPSED_SECONDS = intPreferencesKey("np_collapsed_seconds")
        val NP_COLLAPSED_ELEMENTS = stringPreferencesKey("np_collapsed_elements")

        // Sync
        val SYNC_INTERVAL_MINUTES = intPreferencesKey("sync_interval_minutes")
        val APPLIED_SYNC_INTERVAL_MINUTES = intPreferencesKey("applied_sync_interval_minutes")
        val MAX_CACHED_IMAGES = intPreferencesKey("max_cached_images")
        val LAST_SYNC_TIME = stringPreferencesKey("last_sync_time")

        // Sleep
        val SLEEP_ENABLED = booleanPreferencesKey("sleep_enabled")
        val SLEEP_START_HOUR = intPreferencesKey("sleep_start_hour")
        val SLEEP_END_HOUR = intPreferencesKey("sleep_end_hour")
        val SLEEP_DIM = booleanPreferencesKey("sleep_dim")

        // Local photos
        val LOCAL_FOLDER_URI = stringPreferencesKey("local_folder_uri")
        val LOCAL_FOLDER_ENABLED = booleanPreferencesKey("local_folder_enabled")

        // Navidrome
        val NAVIDROME_URL = stringPreferencesKey("navidrome_url")
        val NAVIDROME_USERNAME = stringPreferencesKey("navidrome_username")
        val NAVIDROME_PASSWORD = stringPreferencesKey("navidrome_password")
        val NAVIDROME_ENABLED = booleanPreferencesKey("navidrome_enabled")
        val NAVIDROME_SYNC_PLAYLIST_IDS = stringPreferencesKey("navidrome_sync_playlist_ids")
        val NAVIDROME_SYNC_FAVORITES = booleanPreferencesKey("navidrome_sync_favorites")
        val NAVIDROME_MAX_CACHED_SONGS = intPreferencesKey("navidrome_max_cached_songs")
        val DENON_HOST = stringPreferencesKey("denon_host")
        val LAST_MUSIC_SYNC_TIME = stringPreferencesKey("last_music_sync_time")
    }

    // Connection
    val serverUrl: Flow<String> = context.dataStore.data.map { it[SERVER_URL] ?: "" }
    val apiKey: Flow<String> = context.dataStore.data.map { it[API_KEY] ?: "" }
    val albumIds: Flow<List<String>> = context.dataStore.data.map {
        (it[ALBUM_IDS] ?: "").split(",").filter { id -> id.isNotBlank() }
    }

    // Slideshow
    val duration: Flow<Int> = context.dataStore.data.map { it[DURATION] ?: 45 }
    val crossfadeDuration: Flow<Int> = context.dataStore.data.map { it[CROSSFADE_DURATION] ?: 1500 }
    val kenBurnsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEN_BURNS_ENABLED] ?: true }
    val kenBurnsZoom: Flow<Int> = context.dataStore.data.map { it[KEN_BURNS_ZOOM] ?: 120 }
    val backgroundBlur: Flow<Boolean> = context.dataStore.data.map { it[BACKGROUND_BLUR] ?: true }
    val imageScale: Flow<String> = context.dataStore.data.map { it[IMAGE_SCALE] ?: "fit" }
    val showProgressBar: Flow<Boolean> = context.dataStore.data.map { it[SHOW_PROGRESS_BAR] ?: false }
    val photoOrder: Flow<String> = context.dataStore.data.map { it[PHOTO_ORDER] ?: "random" }
    val favoritesOnly: Flow<Boolean> = context.dataStore.data.map { it[FAVORITES_ONLY] ?: false }
    val clockFormat: Flow<String> = context.dataStore.data.map { it[CLOCK_FORMAT] ?: "12" }
    val showRating: Flow<Boolean> = context.dataStore.data.map { it[SHOW_RATING] ?: false }
    val showPersonAge: Flow<Boolean> = context.dataStore.data.map { it[SHOW_PERSON_AGE] ?: false }
    val orientationMode: Flow<String> = context.dataStore.data.map { it[ORIENTATION_MODE] ?: ORIENTATION_MATCH_PAIR }

    // Overlays
    val showClock: Flow<Boolean> = context.dataStore.data.map { it[SHOW_CLOCK] ?: true }
    val showDate: Flow<Boolean> = context.dataStore.data.map { it[SHOW_DATE] ?: true }
    val showPhotoDate: Flow<Boolean> = context.dataStore.data.map { it[SHOW_PHOTO_DATE] ?: true }
    val showLocation: Flow<Boolean> = context.dataStore.data.map { it[SHOW_LOCATION] ?: true }
    val showDescription: Flow<Boolean> = context.dataStore.data.map { it[SHOW_DESCRIPTION] ?: true }
    val showPeople: Flow<Boolean> = context.dataStore.data.map { it[SHOW_PEOPLE] ?: false }
    val showCamera: Flow<Boolean> = context.dataStore.data.map { it[SHOW_CAMERA] ?: false }
    val dateFormat: Flow<String> = context.dataStore.data.map { it[DATE_FORMAT] ?: "MMM dd, yyyy" }

    // Overlay presentation
    val overlayTextSize: Flow<String> = context.dataStore.data.map { it[OVERLAY_TEXT_SIZE] ?: "medium" }
    val overlayTextColor: Flow<String> = context.dataStore.data.map { it[OVERLAY_TEXT_COLOR] ?: "light" }
    val overlayBackground: Flow<Boolean> = context.dataStore.data.map { it[OVERLAY_BACKGROUND] ?: true }
    val overlayBackgroundOpacity: Flow<Int> = context.dataStore.data.map { it[OVERLAY_BACKGROUND_OPACITY] ?: 53 }
    val overlayClockPosition: Flow<String> = context.dataStore.data.map { it[OVERLAY_CLOCK_POSITION] ?: OVERLAY_POS_TOP_START }
    val overlayInfoPosition: Flow<String> = context.dataStore.data.map { it[OVERLAY_INFO_POSITION] ?: OVERLAY_POS_BOTTOM_START }
    val overlayCornerRadius: Flow<Int> = context.dataStore.data.map { it[OVERLAY_CORNER_RADIUS] ?: 16 }
    val overlayAnimation: Flow<String> = context.dataStore.data.map {
        when (it[OVERLAY_ANIMATION]) {
            null -> OVERLAY_ANIM_STATIC
            OVERLAY_ANIM_LEGACY_EXPAND -> OVERLAY_ANIM_LOOP  // pre-existing "expand" == loop
            else -> it[OVERLAY_ANIMATION] ?: OVERLAY_ANIM_STATIC
        }
    }
    val overlayMarquee: Flow<Boolean> = context.dataStore.data.map { it[OVERLAY_MARQUEE] ?: false }
    val overlayExpandScale: Flow<Int> = context.dataStore.data.map { it[OVERLAY_EXPAND_SCALE] ?: 130 }
    val overlayCollapsedScale: Flow<Int> = context.dataStore.data.map { it[OVERLAY_COLLAPSED_SCALE] ?: 100 }
    val overlayCollapsedSeconds: Flow<Int> = context.dataStore.data.map { it[OVERLAY_COLLAPSED_SECONDS] ?: 6 }
    val overlayExpandedSeconds: Flow<Int> = context.dataStore.data.map { it[OVERLAY_EXPANDED_SECONDS] ?: 6 }
    val overlayExpandedIndefinite: Flow<Boolean> = context.dataStore.data.map { it[OVERLAY_EXPANDED_INDEFINITE] ?: false }
    val overlayCollapsedIndefinite: Flow<Boolean> = context.dataStore.data.map { it[OVERLAY_COLLAPSED_INDEFINITE] ?: false }
    val overlayCollapsedFields: Flow<Set<String>> = context.dataStore.data.map {
        (it[OVERLAY_COLLAPSED_FIELDS] ?: OVERLAY_FIELD_DATE)
            .split(",").map { id -> id.trim() }.filter { id -> id.isNotBlank() }.toSet()
    }

    suspend fun saveOverlayCollapsedFields(ids: Set<String>) {
        context.dataStore.edit { it[OVERLAY_COLLAPSED_FIELDS] = ids.joinToString(",") }
    }

    // Now-playing pill
    val npShowArt: Flow<Boolean> = context.dataStore.data.map { it[NP_SHOW_ART] ?: true }
    val npShowTitle: Flow<Boolean> = context.dataStore.data.map { it[NP_SHOW_TITLE] ?: true }
    val npShowArtist: Flow<Boolean> = context.dataStore.data.map { it[NP_SHOW_ARTIST] ?: true }
    val npShowControls: Flow<Boolean> = context.dataStore.data.map { it[NP_SHOW_CONTROLS] ?: true }
    val npExpandedScale: Flow<Int> = context.dataStore.data.map { it[NP_EXPANDED_SCALE] ?: it[NP_SCALE_LEGACY] ?: 100 }
    val npCollapsedScale: Flow<Int> = context.dataStore.data.map {
        it[NP_COLLAPSED_SCALE] ?: (((it[NP_SCALE_LEGACY] ?: 100) * 85) / 100)
    }
    val npBackgroundOpacity: Flow<Int> = context.dataStore.data.map { it[NP_BACKGROUND_OPACITY] ?: 80 }
    val npCornerRadius: Flow<Int> = context.dataStore.data.map { it[NP_CORNER_RADIUS] ?: 16 }
    val npAnimation: Flow<String> = context.dataStore.data.map { it[NP_ANIMATION] ?: OVERLAY_ANIM_STATIC }
    val npLoop: Flow<Boolean> = context.dataStore.data.map { it[NP_LOOP] ?: false }
    val npExpandedSeconds: Flow<Int> = context.dataStore.data.map { it[NP_EXPANDED_SECONDS] ?: 10 }
    val npCollapsedSeconds: Flow<Int> = context.dataStore.data.map { it[NP_COLLAPSED_SECONDS] ?: 10 }
    val npCollapsedElements: Flow<Set<String>> = context.dataStore.data.map {
        (it[NP_COLLAPSED_ELEMENTS] ?: "art")
            .split(",").map { id -> id.trim() }.filter { id -> id.isNotBlank() }.toSet()
    }

    suspend fun saveNpCollapsedElements(ids: Set<String>) {
        context.dataStore.edit { it[NP_COLLAPSED_ELEMENTS] = ids.joinToString(",") }
    }

    // Sync
    val syncIntervalMinutes: Flow<Int> = context.dataStore.data.map { it[SYNC_INTERVAL_MINUTES] ?: 60 }
    val appliedSyncIntervalMinutes: Flow<Int> = context.dataStore.data.map { it[APPLIED_SYNC_INTERVAL_MINUTES] ?: 0 }
    val maxCachedImages: Flow<Int> = context.dataStore.data.map { it[MAX_CACHED_IMAGES] ?: 300 }
    val lastSyncTime: Flow<String> = context.dataStore.data.map { it[LAST_SYNC_TIME] ?: "Never" }

    // Sleep
    val sleepEnabled: Flow<Boolean> = context.dataStore.data.map { it[SLEEP_ENABLED] ?: false }
    val sleepStartHour: Flow<Int> = context.dataStore.data.map { it[SLEEP_START_HOUR] ?: 22 }
    val sleepEndHour: Flow<Int> = context.dataStore.data.map { it[SLEEP_END_HOUR] ?: 7 }
    val sleepDim: Flow<Boolean> = context.dataStore.data.map { it[SLEEP_DIM] ?: true }

    // Local photos
    val localFolderUri: Flow<String> = context.dataStore.data.map { it[LOCAL_FOLDER_URI] ?: "" }
    val localFolderEnabled: Flow<Boolean> = context.dataStore.data.map { it[LOCAL_FOLDER_ENABLED] ?: false }

    // Navidrome
    val navidromeUrl: Flow<String> = context.dataStore.data.map { it[NAVIDROME_URL] ?: "" }
    val navidromeUsername: Flow<String> = context.dataStore.data.map { it[NAVIDROME_USERNAME] ?: "" }
    val navidromePassword: Flow<String> = context.dataStore.data.map { it[NAVIDROME_PASSWORD] ?: "" }
    val navidromeEnabled: Flow<Boolean> = context.dataStore.data.map { it[NAVIDROME_ENABLED] ?: false }
    val navidromeSyncPlaylistIds: Flow<Set<String>> = context.dataStore.data.map {
        (it[NAVIDROME_SYNC_PLAYLIST_IDS] ?: "").split(",").filter { id -> id.isNotBlank() }.toSet()
    }

    val lastMusicSyncTime: Flow<String> = context.dataStore.data.map { it[LAST_MUSIC_SYNC_TIME] ?: "Never" }
    val navidromeSyncFavorites: Flow<Boolean> = context.dataStore.data.map { it[NAVIDROME_SYNC_FAVORITES] ?: false }
    val navidromeMaxCachedSongs: Flow<Int> = context.dataStore.data.map { it[NAVIDROME_MAX_CACHED_SONGS] ?: 200 }
    val denonHost: Flow<String> = context.dataStore.data.map { it[DENON_HOST] ?: "10.89.97.15" }

    suspend fun saveSyncPlaylistIds(ids: Set<String>) {
        context.dataStore.edit { it[NAVIDROME_SYNC_PLAYLIST_IDS] = ids.joinToString(",") }
    }

    val isConfigured: Flow<Boolean> = context.dataStore.data.map {
        val hasImmich = !it[SERVER_URL].isNullOrBlank() && !it[API_KEY].isNullOrBlank()
        val hasLocal = it[LOCAL_FOLDER_ENABLED] == true && !it[LOCAL_FOLDER_URI].isNullOrBlank()
        hasImmich || hasLocal
    }

    suspend fun saveServerConfig(url: String, apiKey: String, albumIds: List<String>) {
        context.dataStore.edit {
            it[SERVER_URL] = url.trimEnd('/')
            it[API_KEY] = apiKey
            it[ALBUM_IDS] = albumIds.joinToString(",")
        }
    }

    suspend fun saveNavidromeConfig(url: String, username: String, password: String) {
        context.dataStore.edit {
            it[NAVIDROME_URL] = url.trimEnd('/')
            it[NAVIDROME_USERNAME] = username
            it[NAVIDROME_PASSWORD] = password
            it[NAVIDROME_ENABLED] = true
        }
    }

    suspend fun <T> save(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository {
        return SettingsRepository(context)
    }
}
