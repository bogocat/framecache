package com.bogocat.immichframe.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bogocat.immichframe.data.cache.ImageCacheManager
import com.bogocat.immichframe.data.settings.SettingsRepository
import com.bogocat.immichframe.sync.SyncScheduler
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    cacheManager: ImageCacheManager,
    onBack: () -> Unit
) {
    val showClock by settings.showClock.collectAsState(initial = true)
    val showDate by settings.showDate.collectAsState(initial = true)
    val showPhotoDate by settings.showPhotoDate.collectAsState(initial = true)
    val showLocation by settings.showLocation.collectAsState(initial = true)
    val showDescription by settings.showDescription.collectAsState(initial = true)
    val showPeople by settings.showPeople.collectAsState(initial = false)
    val showCamera by settings.showCamera.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(24.dp))

        Text("Display Options", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        SettingsToggle("Show Clock", showClock) { value ->
            scope.launch {
                settings.saveDisplaySettings(
                    value, showDate, showPhotoDate, showLocation,
                    showDescription, showPeople, showCamera, "MMM dd, yyyy"
                )
            }
        }
        SettingsToggle("Show Date", showDate) { value ->
            scope.launch {
                settings.saveDisplaySettings(
                    showClock, value, showPhotoDate, showLocation,
                    showDescription, showPeople, showCamera, "MMM dd, yyyy"
                )
            }
        }
        SettingsToggle("Show Photo Date", showPhotoDate) { value ->
            scope.launch {
                settings.saveDisplaySettings(
                    showClock, showDate, value, showLocation,
                    showDescription, showPeople, showCamera, "MMM dd, yyyy"
                )
            }
        }
        SettingsToggle("Show Location", showLocation) { value ->
            scope.launch {
                settings.saveDisplaySettings(
                    showClock, showDate, showPhotoDate, value,
                    showDescription, showPeople, showCamera, "MMM dd, yyyy"
                )
            }
        }
        SettingsToggle("Show Description", showDescription) { value ->
            scope.launch {
                settings.saveDisplaySettings(
                    showClock, showDate, showPhotoDate, showLocation,
                    value, showPeople, showCamera, "MMM dd, yyyy"
                )
            }
        }
        SettingsToggle("Show People", showPeople) { value ->
            scope.launch {
                settings.saveDisplaySettings(
                    showClock, showDate, showPhotoDate, showLocation,
                    showDescription, value, showCamera, "MMM dd, yyyy"
                )
            }
        }
        SettingsToggle("Show Camera", showCamera) { value ->
            scope.launch {
                settings.saveDisplaySettings(
                    showClock, showDate, showPhotoDate, showLocation,
                    showDescription, showPeople, value, "MMM dd, yyyy"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Cache", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("${cacheManager.getCacheFileCount()} images cached")
        Text("${cacheManager.getCacheSizeBytes() / 1024 / 1024} MB used")

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = {
                SyncScheduler.triggerImmediateSync(context)
            }) {
                Text("Sync Now")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onBack) {
            Text("Back to Slideshow")
        }
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
