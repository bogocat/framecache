package com.bogocat.immichframe.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bogocat.immichframe.data.cache.ImageCacheManager
import com.bogocat.immichframe.data.settings.SettingsRepository
import com.bogocat.immichframe.sync.SyncScheduler
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val textColor = Color.White
private val subtextColor = Color(0xAAFFFFFF)
private val sectionColor = Color(0xFF4FC3F7)
private val bgColor = Color(0xFF1A1A1A)
private val fieldColors
    @Composable get() = OutlinedTextFieldDefaults.colors(
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,
        focusedBorderColor = sectionColor,
        unfocusedBorderColor = Color(0x55FFFFFF),
        focusedLabelColor = sectionColor,
        unfocusedLabelColor = subtextColor,
        cursorColor = sectionColor
    )

@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    cacheManager: ImageCacheManager,
    onBack: () -> Unit
) {
    val serverUrl by settings.serverUrl.collectAsState(initial = "")
    val apiKey by settings.apiKey.collectAsState(initial = "")
    val albumIdsRaw by settings.albumIds.collectAsState(initial = emptyList())
    val duration by settings.duration.collectAsState(initial = 45)
    val crossfadeDuration by settings.crossfadeDuration.collectAsState(initial = 1500)
    val kenBurnsEnabled by settings.kenBurnsEnabled.collectAsState(initial = true)
    val kenBurnsZoom by settings.kenBurnsZoom.collectAsState(initial = 120)
    val backgroundBlur by settings.backgroundBlur.collectAsState(initial = true)
    val imageScale by settings.imageScale.collectAsState(initial = "fit")
    val showClock by settings.showClock.collectAsState(initial = true)
    val showDate by settings.showDate.collectAsState(initial = true)
    val showPhotoDate by settings.showPhotoDate.collectAsState(initial = true)
    val showLocation by settings.showLocation.collectAsState(initial = true)
    val showDescription by settings.showDescription.collectAsState(initial = true)
    val showPeople by settings.showPeople.collectAsState(initial = false)
    val showCamera by settings.showCamera.collectAsState(initial = false)
    val syncInterval by settings.syncIntervalMinutes.collectAsState(initial = 60)
    val maxCached by settings.maxCachedImages.collectAsState(initial = 300)
    val lastSync by settings.lastSyncTime.collectAsState(initial = "Never")
    val sleepEnabled by settings.sleepEnabled.collectAsState(initial = false)
    val sleepStartHour by settings.sleepStartHour.collectAsState(initial = 22)
    val sleepEndHour by settings.sleepEndHour.collectAsState(initial = 7)
    val sleepDim by settings.sleepDim.collectAsState(initial = true)
    val orientationLock by settings.orientationLock.collectAsState(initial = "auto")

    var editUrl by remember(serverUrl) { mutableStateOf(serverUrl) }
    var editApiKey by remember(apiKey) { mutableStateOf(apiKey) }
    var editAlbums by remember(albumIdsRaw) { mutableStateOf(albumIdsRaw.joinToString(",")) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("ImmichFrame Settings", color = textColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(20.dp))

        // ── Connection ──
        SectionHeader("Connection")

        OutlinedTextField(
            value = editUrl,
            onValueChange = { editUrl = it },
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = fieldColors
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = editApiKey,
            onValueChange = { editApiKey = it },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = fieldColors
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = editAlbums,
            onValueChange = { editAlbums = it },
            label = { Text("Album IDs (comma-separated)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = fieldColors
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                scope.launch {
                    settings.saveServerConfig(
                        editUrl,
                        editApiKey,
                        editAlbums.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    )
                    SyncScheduler.triggerImmediateSync(context)
                }
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = sectionColor)
        ) { Text("Save & Sync", color = sectionColor) }

        SectionDivider()

        // ── Slideshow ──
        SectionHeader("Slideshow")

        SliderSetting(
            label = "Photo Duration",
            value = duration.toFloat(),
            range = 5f..120f,
            unit = "s",
            onValueChange = { scope.launch { settings.save(SettingsRepository.DURATION, it.roundToInt()) } }
        )

        SliderSetting(
            label = "Crossfade",
            value = crossfadeDuration.toFloat(),
            range = 500f..5000f,
            unit = "ms",
            steps = 8,
            onValueChange = { scope.launch { settings.save(SettingsRepository.CROSSFADE_DURATION, it.roundToInt()) } }
        )

        SettingsToggle("Ken Burns Effect", kenBurnsEnabled) {
            scope.launch { settings.save(SettingsRepository.KEN_BURNS_ENABLED, it) }
        }

        if (kenBurnsEnabled) {
            SliderSetting(
                label = "Zoom Amount",
                value = kenBurnsZoom.toFloat(),
                range = 100f..150f,
                unit = "%",
                onValueChange = { scope.launch { settings.save(SettingsRepository.KEN_BURNS_ZOOM, it.roundToInt()) } }
            )
        }

        SettingsToggle("Background Blur", backgroundBlur) {
            scope.launch { settings.save(SettingsRepository.BACKGROUND_BLUR, it) }
        }

        // Image scale: fit vs fill
        SettingsToggle("Fill Screen (crop)", imageScale == "fill") {
            scope.launch { settings.save(SettingsRepository.IMAGE_SCALE, if (it) "fill" else "fit") }
        }

        SectionDivider()

        // ── Overlays ──
        SectionHeader("Overlays")

        SettingsToggle("Clock", showClock) {
            scope.launch { settings.save(SettingsRepository.SHOW_CLOCK, it) }
        }
        SettingsToggle("Current Date", showDate) {
            scope.launch { settings.save(SettingsRepository.SHOW_DATE, it) }
        }
        SettingsToggle("Photo Date", showPhotoDate) {
            scope.launch { settings.save(SettingsRepository.SHOW_PHOTO_DATE, it) }
        }
        SettingsToggle("Location", showLocation) {
            scope.launch { settings.save(SettingsRepository.SHOW_LOCATION, it) }
        }
        SettingsToggle("Description", showDescription) {
            scope.launch { settings.save(SettingsRepository.SHOW_DESCRIPTION, it) }
        }
        SettingsToggle("People", showPeople) {
            scope.launch { settings.save(SettingsRepository.SHOW_PEOPLE, it) }
        }
        SettingsToggle("Camera", showCamera) {
            scope.launch { settings.save(SettingsRepository.SHOW_CAMERA, it) }
        }

        SectionDivider()

        // ── Sync ──
        SectionHeader("Sync & Cache")

        SliderSetting(
            label = "Sync Interval",
            value = syncInterval.toFloat(),
            range = 15f..360f,
            unit = "min",
            onValueChange = { scope.launch { settings.save(SettingsRepository.SYNC_INTERVAL_MINUTES, it.roundToInt()) } }
        )

        SliderSetting(
            label = "Max Cached Photos",
            value = maxCached.toFloat(),
            range = 50f..1000f,
            unit = "",
            steps = 18,
            onValueChange = { scope.launch { settings.save(SettingsRepository.MAX_CACHED_IMAGES, it.roundToInt()) } }
        )

        InfoRow("Photos cached", "${cacheManager.getCacheFileCount()}")
        InfoRow("Disk usage", "${cacheManager.getCacheSizeBytes() / 1024 / 1024} MB")
        InfoRow("Last sync", lastSync)

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { SyncScheduler.triggerImmediateSync(context) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
        ) { Text("Sync Now", color = textColor) }

        SectionDivider()

        // ── Sleep ──
        SectionHeader("Sleep Schedule")

        SettingsToggle("Enable Sleep Mode", sleepEnabled) {
            scope.launch { settings.save(SettingsRepository.SLEEP_ENABLED, it) }
        }

        if (sleepEnabled) {
            SliderSetting(
                label = "Sleep Start",
                value = sleepStartHour.toFloat(),
                range = 0f..23f,
                unit = ":00",
                steps = 22,
                onValueChange = { scope.launch { settings.save(SettingsRepository.SLEEP_START_HOUR, it.roundToInt()) } }
            )
            SliderSetting(
                label = "Wake Up",
                value = sleepEndHour.toFloat(),
                range = 0f..23f,
                unit = ":00",
                steps = 22,
                onValueChange = { scope.launch { settings.save(SettingsRepository.SLEEP_END_HOUR, it.roundToInt()) } }
            )
            SettingsToggle("Dim (vs black)", sleepDim) {
                scope.launch { settings.save(SettingsRepository.SLEEP_DIM, it) }
            }
        }

        SectionDivider()

        // ── Display ──
        SectionHeader("Display")

        // Orientation selector
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Orientation", color = textColor, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("auto" to "Auto", "landscape" to "Landscape", "portrait" to "Portrait").forEach { (value, label) ->
                    OutlinedButton(
                        onClick = { scope.launch { settings.save(SettingsRepository.ORIENTATION_LOCK, value) } },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (orientationLock == value) Color.Black else textColor,
                            containerColor = if (orientationLock == value) sectionColor else Color.Transparent
                        ),
                        modifier = Modifier.height(36.dp)
                    ) { Text(label, fontSize = 12.sp) }
                }
            }
        }

        SectionDivider()

        // ── System ──
        SectionHeader("System")

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
            ) { Text("Android Settings", color = textColor) }

            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
            ) { Text("WiFi", color = textColor) }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333), contentColor = textColor)
        ) { Text("Back to Slideshow", color = textColor) }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, color = sectionColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SectionDivider() {
    Spacer(modifier = Modifier.height(16.dp))
    Divider(color = Color(0x33FFFFFF))
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = subtextColor, fontSize = 14.sp)
        Text(value, color = textColor, fontSize = 14.sp)
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = textColor, fontSize = 16.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = textColor, fontSize = 15.sp)
            Text("${value.roundToInt()}$unit", color = subtextColor, fontSize = 14.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = sectionColor,
                activeTrackColor = sectionColor,
                inactiveTrackColor = Color(0x33FFFFFF)
            )
        )
    }
}
