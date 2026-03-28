package com.bogocat.immichframe.data.settings

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
        val SERVER_URL = stringPreferencesKey("server_url")
        val API_KEY = stringPreferencesKey("api_key")
        val ALBUM_IDS = stringPreferencesKey("album_ids")
        val DURATION = intPreferencesKey("duration")
        val SHOW_CLOCK = booleanPreferencesKey("show_clock")
        val SHOW_DATE = booleanPreferencesKey("show_date")
        val SHOW_PHOTO_DATE = booleanPreferencesKey("show_photo_date")
        val SHOW_LOCATION = booleanPreferencesKey("show_location")
        val SHOW_DESCRIPTION = booleanPreferencesKey("show_description")
        val SHOW_PEOPLE = booleanPreferencesKey("show_people")
        val SHOW_CAMERA = booleanPreferencesKey("show_camera")
        val DATE_FORMAT = stringPreferencesKey("date_format")
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { it[SERVER_URL] ?: "" }
    val apiKey: Flow<String> = context.dataStore.data.map { it[API_KEY] ?: "" }
    val albumIds: Flow<List<String>> = context.dataStore.data.map {
        (it[ALBUM_IDS] ?: "").split(",").filter { id -> id.isNotBlank() }
    }
    val duration: Flow<Int> = context.dataStore.data.map { it[DURATION] ?: 45 }
    val showClock: Flow<Boolean> = context.dataStore.data.map { it[SHOW_CLOCK] ?: true }
    val showDate: Flow<Boolean> = context.dataStore.data.map { it[SHOW_DATE] ?: true }
    val showPhotoDate: Flow<Boolean> = context.dataStore.data.map { it[SHOW_PHOTO_DATE] ?: true }
    val showLocation: Flow<Boolean> = context.dataStore.data.map { it[SHOW_LOCATION] ?: true }
    val showDescription: Flow<Boolean> = context.dataStore.data.map { it[SHOW_DESCRIPTION] ?: true }
    val showPeople: Flow<Boolean> = context.dataStore.data.map { it[SHOW_PEOPLE] ?: false }
    val showCamera: Flow<Boolean> = context.dataStore.data.map { it[SHOW_CAMERA] ?: false }
    val dateFormat: Flow<String> = context.dataStore.data.map { it[DATE_FORMAT] ?: "MMM dd, yyyy" }

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

    suspend fun saveDuration(seconds: Int) {
        context.dataStore.edit { it[DURATION] = seconds }
    }

    suspend fun saveDisplaySettings(
        showClock: Boolean,
        showDate: Boolean,
        showPhotoDate: Boolean,
        showLocation: Boolean,
        showDescription: Boolean,
        showPeople: Boolean,
        showCamera: Boolean,
        dateFormat: String
    ) {
        context.dataStore.edit {
            it[SHOW_CLOCK] = showClock
            it[SHOW_DATE] = showDate
            it[SHOW_PHOTO_DATE] = showPhotoDate
            it[SHOW_LOCATION] = showLocation
            it[SHOW_DESCRIPTION] = showDescription
            it[SHOW_PEOPLE] = showPeople
            it[SHOW_CAMERA] = showCamera
            it[DATE_FORMAT] = dateFormat
        }
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
