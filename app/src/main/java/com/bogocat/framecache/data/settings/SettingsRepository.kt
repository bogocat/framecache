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
        val PHOTO_ORIENTATION_FILTER = stringPreferencesKey("photo_orientation_filter")
        val FAVORITES_ONLY = booleanPreferencesKey("favorites_only")
        val CLOCK_FORMAT = stringPreferencesKey("clock_format")
        val SHOW_RATING = booleanPreferencesKey("show_rating")
        val SHOW_PERSON_AGE = booleanPreferencesKey("show_person_age")

        // Overlays
        val SHOW_CLOCK = booleanPreferencesKey("show_clock")
        val SHOW_DATE = booleanPreferencesKey("show_date")
        val SHOW_PHOTO_DATE = booleanPreferencesKey("show_photo_date")
        val SHOW_LOCATION = booleanPreferencesKey("show_location")
        val SHOW_DESCRIPTION = booleanPreferencesKey("show_description")
        val SHOW_PEOPLE = booleanPreferencesKey("show_people")
        val SHOW_CAMERA = booleanPreferencesKey("show_camera")
        val DATE_FORMAT = stringPreferencesKey("date_format")

        // Sync
        val SYNC_INTERVAL_MINUTES = intPreferencesKey("sync_interval_minutes")
        val MAX_CACHED_IMAGES = intPreferencesKey("max_cached_images")
        val LAST_SYNC_TIME = stringPreferencesKey("last_sync_time")

        // Sleep
        val SLEEP_ENABLED = booleanPreferencesKey("sleep_enabled")
        val SLEEP_START_HOUR = intPreferencesKey("sleep_start_hour")
        val SLEEP_END_HOUR = intPreferencesKey("sleep_end_hour")
        val SLEEP_DIM = booleanPreferencesKey("sleep_dim")

        // Display
        val ORIENTATION_LOCK = stringPreferencesKey("orientation_lock")
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
    val photoOrientationFilter: Flow<String> = context.dataStore.data.map { it[PHOTO_ORIENTATION_FILTER] ?: "all" }
    val favoritesOnly: Flow<Boolean> = context.dataStore.data.map { it[FAVORITES_ONLY] ?: false }
    val clockFormat: Flow<String> = context.dataStore.data.map { it[CLOCK_FORMAT] ?: "12" }
    val showRating: Flow<Boolean> = context.dataStore.data.map { it[SHOW_RATING] ?: false }
    val showPersonAge: Flow<Boolean> = context.dataStore.data.map { it[SHOW_PERSON_AGE] ?: false }

    // Overlays
    val showClock: Flow<Boolean> = context.dataStore.data.map { it[SHOW_CLOCK] ?: true }
    val showDate: Flow<Boolean> = context.dataStore.data.map { it[SHOW_DATE] ?: true }
    val showPhotoDate: Flow<Boolean> = context.dataStore.data.map { it[SHOW_PHOTO_DATE] ?: true }
    val showLocation: Flow<Boolean> = context.dataStore.data.map { it[SHOW_LOCATION] ?: true }
    val showDescription: Flow<Boolean> = context.dataStore.data.map { it[SHOW_DESCRIPTION] ?: true }
    val showPeople: Flow<Boolean> = context.dataStore.data.map { it[SHOW_PEOPLE] ?: false }
    val showCamera: Flow<Boolean> = context.dataStore.data.map { it[SHOW_CAMERA] ?: false }
    val dateFormat: Flow<String> = context.dataStore.data.map { it[DATE_FORMAT] ?: "MMM dd, yyyy" }

    // Sync
    val syncIntervalMinutes: Flow<Int> = context.dataStore.data.map { it[SYNC_INTERVAL_MINUTES] ?: 60 }
    val maxCachedImages: Flow<Int> = context.dataStore.data.map { it[MAX_CACHED_IMAGES] ?: 300 }
    val lastSyncTime: Flow<String> = context.dataStore.data.map { it[LAST_SYNC_TIME] ?: "Never" }

    // Sleep
    val sleepEnabled: Flow<Boolean> = context.dataStore.data.map { it[SLEEP_ENABLED] ?: false }
    val sleepStartHour: Flow<Int> = context.dataStore.data.map { it[SLEEP_START_HOUR] ?: 22 }
    val sleepEndHour: Flow<Int> = context.dataStore.data.map { it[SLEEP_END_HOUR] ?: 7 }
    val sleepDim: Flow<Boolean> = context.dataStore.data.map { it[SLEEP_DIM] ?: true }

    // Display
    val orientationLock: Flow<String> = context.dataStore.data.map { it[ORIENTATION_LOCK] ?: "auto" }

    val isConfigured: Flow<Boolean> = context.dataStore.data.map {
        !it[SERVER_URL].isNullOrBlank() && !it[API_KEY].isNullOrBlank()
    }

    suspend fun saveServerConfig(url: String, apiKey: String, albumIds: List<String>) {
        context.dataStore.edit {
            it[SERVER_URL] = url.trimEnd('/')
            it[API_KEY] = apiKey
            it[ALBUM_IDS] = albumIds.joinToString(",")
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
