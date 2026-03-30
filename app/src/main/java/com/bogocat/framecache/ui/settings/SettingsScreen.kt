package com.bogocat.framecache.ui.settings

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.bogocat.framecache.api.ImmichApi
import com.bogocat.framecache.data.cache.ImageCacheManager
import com.bogocat.framecache.data.settings.SettingsRepository
import com.bogocat.framecache.sync.SyncScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val textColor = Color.White
private val subtextColor = Color(0xAAFFFFFF)
private val sectionColor = Color(0xFF4FC3F7)
private val bgColor = Color(0xFF1A1A1A)
private val successColor = Color(0xFF69F0AE)
private val errorColor = Color(0xFFFF5252)
private val fieldColors
    @Composable get() = OutlinedTextFieldDefaults.colors(
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,
        focusedBorderColor = sectionColor,
        unfocusedBorderColor = Color(0x55FFFFFF),
        focusedLabelColor = sectionColor,
        unfocusedLabelColor = subtextColor,
        cursorColor = sectionColor,
        disabledTextColor = subtextColor,
        disabledBorderColor = Color(0x33FFFFFF),
        disabledLabelColor = Color(0x55FFFFFF)
    )

@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    cacheManager: ImageCacheManager,
    api: ImmichApi,
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
    val showProgressBar by settings.showProgressBar.collectAsState(initial = false)
    val photoOrder by settings.photoOrder.collectAsState(initial = "random")
    val orientationFilter by settings.photoOrientationFilter.collectAsState(initial = "all")
    val favoritesOnly by settings.favoritesOnly.collectAsState(initial = false)
    val clockFormat by settings.clockFormat.collectAsState(initial = "12")
    val showRating by settings.showRating.collectAsState(initial = false)
    val showPersonAge by settings.showPersonAge.collectAsState(initial = false)
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
    val localFolderEnabled by settings.localFolderEnabled.collectAsState(initial = false)
    val localFolderUri by settings.localFolderUri.collectAsState(initial = "")

    // Connection editing state
    var isEditing by remember { mutableStateOf(false) }
    var editUrl by remember(serverUrl) { mutableStateOf(serverUrl) }
    var editApiKey by remember(apiKey) { mutableStateOf(apiKey) }
    var editAlbums by remember(albumIdsRaw) { mutableStateOf(albumIdsRaw.joinToString(",")) }
    var fetchedAlbums by remember { mutableStateOf<List<com.bogocat.framecache.api.model.AlbumResponse>>(emptyList()) }
    val selectedAlbumIds = remember { mutableStateListOf<String>() }
    var albumsLoading by remember { mutableStateOf(false) }

    // Connection test state
    var connStatus by remember { mutableStateOf("") }
    var connAlbumCount by remember { mutableStateOf(0) }
    var connPhotoCount by remember { mutableStateOf(0) }
    var connTesting by remember { mutableStateOf(false) }
    var connOk by remember { mutableStateOf(false) }

    // Sync state
    var isSyncing by remember { mutableStateOf(false) }

    // Auto-test connection on open
    LaunchedEffect(Unit) {
        if (serverUrl.isNotBlank() && apiKey.isNotBlank()) {
            connTesting = true
            try {
                val about = api.getServerAbout()
                val albums = api.getAlbums()
                val totalPhotos = albums.sumOf { it.assetCount }
                connStatus = "Immich ${about.version}"
                connAlbumCount = albums.size
                connPhotoCount = totalPhotos
                connOk = true
            } catch (e: Exception) {
                connStatus = "Error: ${e.message?.take(50)}"
                connOk = false
            }
            connTesting = false
        }
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("FrameCache Settings", color = textColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(20.dp))

        // ── Photo Sources ──
        SectionHeader("Photo Sources — Immich")

        // Status indicator
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (connTesting) {
                CircularProgressIndicator(color = sectionColor, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Testing...", color = subtextColor, fontSize = 13.sp)
            } else if (connOk) {
                Text("\u2713", color = successColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(connStatus, color = successColor, fontSize = 13.sp)
                Text("\u2022 ${connAlbumCount} albums \u2022 ${connPhotoCount} photos", color = subtextColor, fontSize = 13.sp)
            } else if (connStatus.isNotBlank()) {
                Text("\u2717", color = errorColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(connStatus, color = errorColor, fontSize = 13.sp)
            }
        }

        if (!isEditing) {
            // Collapsed view
            InfoRow("Server", serverUrl.ifBlank { "Not configured" })
            InfoRow("API Key", if (apiKey.isNotBlank()) "\u2022\u2022\u2022${apiKey.takeLast(8)}" else "Not set")
            InfoRow("Albums", "${albumIdsRaw.size} selected")

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { isEditing = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = subtextColor)
            ) { Text("Edit Connection", color = subtextColor) }
        } else {
            // Expanded edit mode
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

            // Album picker
            Spacer(modifier = Modifier.height(4.dp))
            if (fetchedAlbums.isEmpty() && !albumsLoading) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            albumsLoading = true
                            try {
                                // Create a temporary API client with the edited credentials
                                val testClient = okhttp3.OkHttpClient.Builder()
                                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                                    .addInterceptor { chain ->
                                        chain.proceed(chain.request().newBuilder()
                                            .addHeader("x-api-key", editApiKey).build())
                                    }.build()
                                val testApi = retrofit2.Retrofit.Builder()
                                    .baseUrl(if (editUrl.endsWith("/")) editUrl else "$editUrl/")
                                    .client(testClient)
                                    .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                                    .build()
                                    .create(com.bogocat.framecache.api.ImmichApi::class.java)
                                fetchedAlbums = testApi.getAlbums()
                                // Pre-select currently configured albums
                                selectedAlbumIds.clear()
                                selectedAlbumIds.addAll(albumIdsRaw)
                            } catch (e: Exception) {
                                connStatus = "Error loading albums: ${e.message?.take(50)}"
                            }
                            albumsLoading = false
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = sectionColor)
                ) { Text("Load Albums", color = sectionColor) }
            } else if (albumsLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = sectionColor, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Loading albums...", color = subtextColor, fontSize = 13.sp)
                }
            } else {
                Text("Select albums:", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                fetchedAlbums.forEach { album ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = album.id in selectedAlbumIds,
                            onCheckedChange = { checked ->
                                if (checked) selectedAlbumIds.add(album.id)
                                else selectedAlbumIds.remove(album.id)
                            },
                            colors = androidx.compose.material3.CheckboxDefaults.colors(
                                checkedColor = sectionColor,
                                uncheckedColor = subtextColor,
                                checkmarkColor = Color.Black
                            )
                        )
                        Text("${album.albumName} (${album.assetCount})", color = textColor, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val albumIds = if (fetchedAlbums.isNotEmpty()) {
                                selectedAlbumIds.toList()
                            } else {
                                editAlbums.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            }
                            settings.saveServerConfig(editUrl, editApiKey, albumIds)
                            isEditing = false
                            fetchedAlbums = emptyList()
                            // Re-test connection
                            connTesting = true
                            try {
                                val about = api.getServerAbout()
                                val albums = api.getAlbums()
                                connStatus = "Immich ${about.version}"
                                connAlbumCount = albums.size
                                connPhotoCount = albums.sumOf { it.assetCount }
                                connOk = true
                            } catch (e: Exception) {
                                connStatus = "Error: ${e.message?.take(50)}"
                                connOk = false
                            }
                            connTesting = false
                            SyncScheduler.triggerImmediateSync(context)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = sectionColor, contentColor = Color.Black)
                ) { Text("Save") }

                OutlinedButton(
                    onClick = {
                        editUrl = serverUrl
                        editApiKey = apiKey
                        editAlbums = albumIdsRaw.joinToString(",")
                        fetchedAlbums = emptyList()
                        isEditing = false
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = subtextColor)
                ) { Text("Cancel", color = subtextColor) }
            }
        }

        SectionDivider()

        // ── Local Photos ──
        SectionHeader("Photo Sources — Local Folder")

        SettingsToggle("Enable Local Folder", localFolderEnabled) {
            scope.launch { settings.save(SettingsRepository.LOCAL_FOLDER_ENABLED, it) }
        }

        if (localFolderEnabled) {
            if (localFolderUri.isNotBlank()) {
                val folderName = try {
                    android.net.Uri.parse(localFolderUri).lastPathSegment ?: localFolderUri
                } catch (_: Exception) { localFolderUri }
                InfoRow("Folder", folderName)
            }

            val folderLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (uri != null) {
                    // Persist permission across reboots
                    context.contentResolver.takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    scope.launch {
                        settings.save(SettingsRepository.LOCAL_FOLDER_URI, uri.toString())
                        SyncScheduler.triggerImmediateSync(context)
                    }
                }
            }

            OutlinedButton(
                onClick = { folderLauncher.launch(null) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = sectionColor)
            ) { Text(if (localFolderUri.isBlank()) "Choose Folder" else "Change Folder", color = sectionColor) }
        }

        SectionDivider()

        // ── Slideshow ──
        SectionHeader("Slideshow")

        SliderSetting("Photo Duration", duration.toFloat(), 5f..120f, "s") {
            scope.launch { settings.save(SettingsRepository.DURATION, it.roundToInt()) }
        }
        SliderSetting("Crossfade", crossfadeDuration.toFloat(), 500f..5000f, "ms", steps = 8) {
            scope.launch { settings.save(SettingsRepository.CROSSFADE_DURATION, it.roundToInt()) }
        }
        SettingsToggle("Ken Burns Effect", kenBurnsEnabled) {
            scope.launch { settings.save(SettingsRepository.KEN_BURNS_ENABLED, it) }
        }
        if (kenBurnsEnabled) {
            SliderSetting("Zoom Amount", kenBurnsZoom.toFloat(), 100f..150f, "%") {
                scope.launch { settings.save(SettingsRepository.KEN_BURNS_ZOOM, it.roundToInt()) }
            }
        }
        SettingsToggle("Background Blur", backgroundBlur) {
            scope.launch { settings.save(SettingsRepository.BACKGROUND_BLUR, it) }
        }
        SettingsToggle("Fill Screen (crop)", imageScale == "fill") {
            scope.launch { settings.save(SettingsRepository.IMAGE_SCALE, if (it) "fill" else "fit") }
        }
        SettingsToggle("Progress Bar", showProgressBar) {
            scope.launch { settings.save(SettingsRepository.SHOW_PROGRESS_BAR, it) }
        }

        // Photo order
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Order", color = textColor, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("random" to "Random", "chronological" to "Date").forEach { (value, label) ->
                    OutlinedButton(
                        onClick = { scope.launch { settings.save(SettingsRepository.PHOTO_ORDER, value) } },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (photoOrder == value) Color.Black else textColor,
                            containerColor = if (photoOrder == value) sectionColor else Color.Transparent
                        ),
                        modifier = Modifier.height(36.dp)
                    ) { Text(label, fontSize = 12.sp) }
                }
            }
        }

        // Orientation filter
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Photos", color = textColor, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "All", "landscape" to "Landscape", "portrait" to "Portrait").forEach { (value, label) ->
                    OutlinedButton(
                        onClick = { scope.launch { settings.save(SettingsRepository.PHOTO_ORIENTATION_FILTER, value) } },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (orientationFilter == value) Color.Black else textColor,
                            containerColor = if (orientationFilter == value) sectionColor else Color.Transparent
                        ),
                        modifier = Modifier.height(36.dp)
                    ) { Text(label, fontSize = 12.sp) }
                }
            }
        }

        SettingsToggle("Favorites Only", favoritesOnly) {
            scope.launch { settings.save(SettingsRepository.FAVORITES_ONLY, it) }
        }

        SectionDivider()

        // ── Overlays ──
        SectionHeader("Overlays")

        // Clock format
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Clock", color = textColor, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("12" to "12h", "24" to "24h").forEach { (value, label) ->
                    OutlinedButton(
                        onClick = { scope.launch { settings.save(SettingsRepository.CLOCK_FORMAT, value) } },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (clockFormat == value) Color.Black else textColor,
                            containerColor = if (clockFormat == value) sectionColor else Color.Transparent
                        ),
                        modifier = Modifier.height(36.dp)
                    ) { Text(label, fontSize = 12.sp) }
                }
            }
        }
        SettingsToggle("Clock", showClock) { scope.launch { settings.save(SettingsRepository.SHOW_CLOCK, it) } }
        SettingsToggle("Current Date", showDate) { scope.launch { settings.save(SettingsRepository.SHOW_DATE, it) } }
        SettingsToggle("Photo Date", showPhotoDate) { scope.launch { settings.save(SettingsRepository.SHOW_PHOTO_DATE, it) } }
        SettingsToggle("Location", showLocation) { scope.launch { settings.save(SettingsRepository.SHOW_LOCATION, it) } }
        SettingsToggle("Description", showDescription) { scope.launch { settings.save(SettingsRepository.SHOW_DESCRIPTION, it) } }
        SettingsToggle("People", showPeople) { scope.launch { settings.save(SettingsRepository.SHOW_PEOPLE, it) } }
        SettingsToggle("Camera", showCamera) { scope.launch { settings.save(SettingsRepository.SHOW_CAMERA, it) } }
        SettingsToggle("Star Rating", showRating) { scope.launch { settings.save(SettingsRepository.SHOW_RATING, it) } }
        SettingsToggle("Person Age", showPersonAge) { scope.launch { settings.save(SettingsRepository.SHOW_PERSON_AGE, it) } }

        SectionDivider()

        // ── Sync & Cache ──
        SectionHeader("Sync & Cache")

        SliderSetting("Sync Interval", syncInterval.toFloat(), 15f..360f, "min") {
            scope.launch { settings.save(SettingsRepository.SYNC_INTERVAL_MINUTES, it.roundToInt()) }
        }
        SliderSetting("Max Cached Photos", maxCached.toFloat(), 50f..1000f, "", steps = 18) {
            scope.launch { settings.save(SettingsRepository.MAX_CACHED_IMAGES, it.roundToInt()) }
        }

        InfoRow("Photos cached", "${cacheManager.getCacheFileCount()}")
        InfoRow("Disk usage", "${cacheManager.getCacheSizeBytes() / 1024 / 1024} MB")
        InfoRow("Last sync", lastSync)

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    isSyncing = true
                    SyncScheduler.triggerImmediateSync(context)
                    scope.launch {
                        val startSync = lastSync
                        repeat(30) {
                            kotlinx.coroutines.delay(1000)
                            val current = settings.lastSyncTime.first()
                            if (current != startSync) {
                                isSyncing = false
                                return@launch
                            }
                        }
                        isSyncing = false
                    }
                },
                enabled = !isSyncing,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
            ) { Text(if (isSyncing) "Syncing..." else "Sync Now", color = textColor) }

            if (isSyncing) {
                CircularProgressIndicator(color = sectionColor, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }

        SectionDivider()

        // ── Sleep ──
        SectionHeader("Sleep Schedule")
        SettingsToggle("Enable Sleep Mode", sleepEnabled) {
            scope.launch { settings.save(SettingsRepository.SLEEP_ENABLED, it) }
        }
        if (sleepEnabled) {
            SliderSetting("Sleep Start", sleepStartHour.toFloat(), 0f..23f, ":00", steps = 22) {
                scope.launch { settings.save(SettingsRepository.SLEEP_START_HOUR, it.roundToInt()) }
            }
            SliderSetting("Wake Up", sleepEndHour.toFloat(), 0f..23f, ":00", steps = 22) {
                scope.launch { settings.save(SettingsRepository.SLEEP_END_HOUR, it.roundToInt()) }
            }
            SettingsToggle("Dim (vs black)", sleepDim) {
                scope.launch { settings.save(SettingsRepository.SLEEP_DIM, it) }
            }
        }

        SectionDivider()

        // ── System ──
        SectionHeader("System")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
            ) { Text("Android Settings", color = textColor) }
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
            ) { Text("WiFi", color = textColor) }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333), contentColor = textColor)
        ) { Text("Back to Slideshow", color = textColor) }

        Spacer(modifier = Modifier.height(16.dp))
        Text("FrameCache v1.0.0", color = Color(0x44FFFFFF), fontSize = 11.sp)

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
    label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    unit: String, steps: Int = 0, onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = textColor, fontSize = 15.sp)
            Text("${value.roundToInt()}$unit", color = subtextColor, fontSize = 14.sp)
        }
        Slider(
            value = value, onValueChange = onValueChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = sectionColor, activeTrackColor = sectionColor, inactiveTrackColor = Color(0x33FFFFFF))
        )
    }
}
