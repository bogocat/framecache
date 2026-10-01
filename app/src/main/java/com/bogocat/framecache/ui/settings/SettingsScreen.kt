package com.bogocat.framecache.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bogocat.framecache.api.ImmichApi
import com.bogocat.framecache.api.navidrome.NavidromeClient
import com.bogocat.framecache.data.cache.ImageCacheManager
import com.bogocat.framecache.data.settings.SettingsRepository
import com.bogocat.framecache.music.MusicPlayer
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
    navidromeClient: NavidromeClient? = null,
    musicPlayer: MusicPlayer? = null,
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
    val favoritesOnly by settings.favoritesOnly.collectAsState(initial = false)
    val orientationMode by settings.orientationMode.collectAsState(initial = SettingsRepository.ORIENTATION_MATCH_PAIR)
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
    val overlayTextSize by settings.overlayTextSize.collectAsState(initial = "medium")
    val overlayTextColor by settings.overlayTextColor.collectAsState(initial = "light")
    val overlayBackground by settings.overlayBackground.collectAsState(initial = true)
    val overlayBackgroundOpacity by settings.overlayBackgroundOpacity.collectAsState(initial = 53)
    val overlayClockPosition by settings.overlayClockPosition.collectAsState(initial = SettingsRepository.OVERLAY_POS_TOP_START)
    val overlayInfoPosition by settings.overlayInfoPosition.collectAsState(initial = SettingsRepository.OVERLAY_POS_BOTTOM_START)
    val overlayCornerRadius by settings.overlayCornerRadius.collectAsState(initial = 16)
    val overlayAnimation by settings.overlayAnimation.collectAsState(initial = SettingsRepository.OVERLAY_ANIM_STATIC)
    val overlayMarquee by settings.overlayMarquee.collectAsState(initial = false)
    val overlayExpandScale by settings.overlayExpandScale.collectAsState(initial = 130)
    val overlayCollapsedScale by settings.overlayCollapsedScale.collectAsState(initial = 100)
    val overlayCollapsedSeconds by settings.overlayCollapsedSeconds.collectAsState(initial = 6)
    val overlayExpandedSeconds by settings.overlayExpandedSeconds.collectAsState(initial = 6)
    val overlayExpandedIndefinite by settings.overlayExpandedIndefinite.collectAsState(initial = false)
    val overlayCollapsedIndefinite by settings.overlayCollapsedIndefinite.collectAsState(initial = false)
    val overlayCollapsedFields by settings.overlayCollapsedFields.collectAsState(initial = setOf(SettingsRepository.OVERLAY_FIELD_DATE))
    val npShowArt by settings.npShowArt.collectAsState(initial = true)
    val npShowTitle by settings.npShowTitle.collectAsState(initial = true)
    val npShowArtist by settings.npShowArtist.collectAsState(initial = true)
    val npShowControls by settings.npShowControls.collectAsState(initial = true)
    val npExpandedScale by settings.npExpandedScale.collectAsState(initial = 100)
    val npCollapsedScale by settings.npCollapsedScale.collectAsState(initial = 85)
    val npBackgroundOpacity by settings.npBackgroundOpacity.collectAsState(initial = 80)
    val npCornerRadius by settings.npCornerRadius.collectAsState(initial = 16)
    val npAnimation by settings.npAnimation.collectAsState(initial = SettingsRepository.OVERLAY_ANIM_STATIC)
    val npExpandedSeconds by settings.npExpandedSeconds.collectAsState(initial = 10)
    val npCollapsedSeconds by settings.npCollapsedSeconds.collectAsState(initial = 10)
    val npCollapsedElements by settings.npCollapsedElements.collectAsState(initial = setOf("art"))
    val syncInterval by settings.syncIntervalMinutes.collectAsState(initial = 60)
    val maxCached by settings.maxCachedImages.collectAsState(initial = 300)
    val lastSync by settings.lastSyncTime.collectAsState(initial = "Never")
    val sleepEnabled by settings.sleepEnabled.collectAsState(initial = false)
    val sleepStartHour by settings.sleepStartHour.collectAsState(initial = 22)
    val sleepEndHour by settings.sleepEndHour.collectAsState(initial = 7)
    val sleepDim by settings.sleepDim.collectAsState(initial = true)
    val localFolderEnabled by settings.localFolderEnabled.collectAsState(initial = false)
    val localFolderUri by settings.localFolderUri.collectAsState(initial = "")

    // Navidrome
    val navidromeEnabled by settings.navidromeEnabled.collectAsState(initial = false)
    val navidromeUrl by settings.navidromeUrl.collectAsState(initial = "")
    val navidromeUsername by settings.navidromeUsername.collectAsState(initial = "")
    val navidromePassword by settings.navidromePassword.collectAsState(initial = "")
    val navidromeSyncFavorites by settings.navidromeSyncFavorites.collectAsState(initial = false)
    val navidromeMaxCachedSongs by settings.navidromeMaxCachedSongs.collectAsState(initial = 200)
    val denonHost by settings.denonHost.collectAsState(initial = "10.89.97.15")

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

        Spacer(modifier = Modifier.height(12.dp))

        // ── Brightness ──
        val window = (context as? android.app.Activity)?.window
        var brightness by remember {
            mutableStateOf(
                window?.attributes?.screenBrightness?.let {
                    if (it < 0) 0.5f else it  // -1 means system default
                } ?: 0.5f
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Brightness", color = textColor, fontSize = 15.sp)
            Slider(
                value = brightness,
                onValueChange = {
                    brightness = it
                    window?.let { w ->
                        val params = w.attributes
                        params.screenBrightness = it.coerceIn(0.01f, 1f)
                        w.attributes = params
                    }
                },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = sectionColor,
                    activeTrackColor = sectionColor,
                    inactiveTrackColor = Color(0x33FFFFFF)
                )
            )
        }

        SectionDivider()

        // ── Server Connection ──
        var serverOpen by remember { mutableStateOf(false) }
        CollapsibleSection("Server Connection", serverOpen, { serverOpen = !serverOpen }) {

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
            } else if (connStatus.isNotBlank()) {
                Text("\u2717", color = errorColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(connStatus, color = errorColor, fontSize = 13.sp)
            }
        }

        if (!isEditing) {
            InfoRow("Server", serverUrl.ifBlank { "Not configured" })
            InfoRow("API Key", if (apiKey.isNotBlank()) "\u2022\u2022\u2022${apiKey.takeLast(8)}" else "Not set")
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { isEditing = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = subtextColor)
            ) { Text("Edit Server", color = subtextColor) }
        } else {
            OutlinedTextField(
                value = editUrl, onValueChange = { editUrl = it },
                label = { Text("Server URL") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = editApiKey, onValueChange = { editApiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            settings.saveServerConfig(editUrl, editApiKey, selectedAlbumIds.toList())
                            isEditing = false
                            fetchedAlbums = emptyList()
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
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = sectionColor, contentColor = Color.Black)
                ) { Text("Save") }
                OutlinedButton(
                    onClick = { editUrl = serverUrl; editApiKey = apiKey; isEditing = false },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = subtextColor)
                ) { Text("Cancel", color = subtextColor) }
            }
        }

        }
        SectionDivider()

        // ── Photo Sources ──
        var sourcesOpen by remember { mutableStateOf(false) }
        CollapsibleSection("Photo Sources", sourcesOpen, { sourcesOpen = !sourcesOpen }) {

        // Albums (foldable)
        var albumsExpanded by remember { mutableStateOf(false) }

        // Auto-fetch albums when connection is OK
        LaunchedEffect(connOk) {
            if (connOk && fetchedAlbums.isEmpty()) {
                albumsLoading = true
                try {
                    fetchedAlbums = api.getAlbums()
                    val validIds = fetchedAlbums.map { it.id }.toSet()
                    selectedAlbumIds.clear()
                    selectedAlbumIds.addAll(albumIdsRaw.filter { it in validIds })
                    // Clean up stale IDs in DataStore
                    val cleaned = albumIdsRaw.filter { it in validIds }
                    if (cleaned.size != albumIdsRaw.size) {
                        settings.saveServerConfig(serverUrl, apiKey, cleaned)
                    }
                } catch (_: Exception) {}
                albumsLoading = false
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (fetchedAlbums.isEmpty() && !albumsLoading && !albumsExpanded) {
                        // Try to load albums on expand
                        scope.launch {
                            albumsLoading = true
                            try {
                                fetchedAlbums = api.getAlbums()
                                val validIds = fetchedAlbums.map { it.id }.toSet()
                                selectedAlbumIds.clear()
                                selectedAlbumIds.addAll(albumIdsRaw.filter { it in validIds })
                            } catch (_: Exception) {}
                            albumsLoading = false
                        }
                    }
                    albumsExpanded = !albumsExpanded
                }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val albumCount = if (fetchedAlbums.isNotEmpty()) selectedAlbumIds.size else albumIdsRaw.size
            Text("Immich Albums ($albumCount selected)", color = textColor, fontSize = 16.sp)
            Text(if (albumsExpanded) "\u25B2" else "\u25BC", color = subtextColor, fontSize = 12.sp)
        }

        if (albumsExpanded) {
            if (albumsLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = sectionColor, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Loading albums...", color = subtextColor, fontSize = 13.sp)
                }
            } else if (fetchedAlbums.isNotEmpty()) {
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
                                // Save immediately but don't sync — sync fires when leaving settings
                                scope.launch {
                                    settings.saveServerConfig(serverUrl, apiKey, selectedAlbumIds.toList())
                                }
                            },
                            colors = androidx.compose.material3.CheckboxDefaults.colors(
                                checkedColor = sectionColor, uncheckedColor = subtextColor, checkmarkColor = Color.Black
                            )
                        )
                        Text("${album.albumName} (${album.assetCount})", color = textColor, fontSize = 14.sp)
                    }
                }
            } else {
                Text("Could not load albums — check server connection", color = subtextColor, fontSize = 13.sp)
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            albumsLoading = true
                            try {
                                fetchedAlbums = api.getAlbums()
                                val validIds = fetchedAlbums.map { it.id }.toSet()
                                selectedAlbumIds.clear()
                                selectedAlbumIds.addAll(albumIdsRaw.filter { it in validIds })
                            } catch (_: Exception) {}
                            albumsLoading = false
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = sectionColor)
                ) { Text("Retry", color = sectionColor) }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Local Folder
        Text("Local Folder", color = textColor, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))

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

        }
        SectionDivider()

        // ── Slideshow ──
        var slideshowOpen by remember { mutableStateOf(false) }
        CollapsibleSection("Slideshow", slideshowOpen, { slideshowOpen = !slideshowOpen }) {

        DurationSetting("Photo Duration", duration) {
            scope.launch { settings.save(SettingsRepository.DURATION, it) }
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

        SettingsToggle("Favorites Only", favoritesOnly) {
            scope.launch { settings.save(SettingsRepository.FAVORITES_ONLY, it) }
        }

        // Photo orientation policy
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text("Photo Orientation", color = textColor, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    SettingsRepository.ORIENTATION_ALL to "All",
                    SettingsRepository.ORIENTATION_MATCH to "Match",
                    SettingsRepository.ORIENTATION_MATCH_PAIR to "Match + Pairs"
                ).forEach { (value, label) ->
                    OutlinedButton(
                        onClick = { scope.launch { settings.save(SettingsRepository.ORIENTATION_MODE, value) } },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (orientationMode == value) Color.Black else textColor,
                            containerColor = if (orientationMode == value) sectionColor else Color.Transparent
                        ),
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) { Text(label, fontSize = 12.sp) }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(orientationModeDescription(orientationMode), color = subtextColor, fontSize = 12.sp)
        }

        }
        SectionDivider()

        // ── Overlays ──
        var overlaysOpen by remember { mutableStateOf(false) }
        CollapsibleSection("Overlays", overlaysOpen, { overlaysOpen = !overlaysOpen }) {

        Text(
            "Fields",
            color = subtextColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )

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

        // ── Overlay presentation ──
        Text(
            "Overlay Style",
            color = subtextColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
        )

        OverlayPositionRow("Clock Position", overlayClockPosition) {
            scope.launch { settings.save(SettingsRepository.OVERLAY_CLOCK_POSITION, it) }
        }
        OverlayPositionRow("Photo Info Position", overlayInfoPosition) {
            scope.launch { settings.save(SettingsRepository.OVERLAY_INFO_POSITION, it) }
        }
        OverlayChoiceRow(
            "Text Size",
            listOf("small" to "Small", "medium" to "Medium", "large" to "Large"),
            overlayTextSize
        ) { scope.launch { settings.save(SettingsRepository.OVERLAY_TEXT_SIZE, it) } }
        OverlayChoiceRow(
            "Text Color",
            listOf("light" to "Light", "dark" to "Dark"),
            overlayTextColor
        ) { scope.launch { settings.save(SettingsRepository.OVERLAY_TEXT_COLOR, it) } }
        OverlayChoiceRow(
            "Background",
            listOf("pill" to "Pill", "none" to "None"),
            if (overlayBackground) "pill" else "none"
        ) { scope.launch { settings.save(SettingsRepository.OVERLAY_BACKGROUND, it == "pill") } }

        if (overlayBackground) {
            SliderSetting("Background Opacity", overlayBackgroundOpacity.toFloat(), 0f..100f, "%") {
                scope.launch { settings.save(SettingsRepository.OVERLAY_BACKGROUND_OPACITY, it.roundToInt()) }
            }
        }
        SliderSetting("Corner Radius", overlayCornerRadius.toFloat(), 0f..32f, "dp") {
            scope.launch { settings.save(SettingsRepository.OVERLAY_CORNER_RADIUS, it.roundToInt()) }
        }

        Text(
            "Motion",
            color = subtextColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
        )

        OverlayChoiceRow(
            "Animation",
            listOf(
                SettingsRepository.OVERLAY_ANIM_STATIC to "Static",
                SettingsRepository.OVERLAY_ANIM_LOOP to "Loop",
                SettingsRepository.OVERLAY_ANIM_ONCE to "Once"
            ),
            overlayAnimation
        ) { scope.launch { settings.save(SettingsRepository.OVERLAY_ANIMATION, it) } }

        SettingsToggle("Marquee Long Text", overlayMarquee) {
            scope.launch { settings.save(SettingsRepository.OVERLAY_MARQUEE, it) }
        }

        if (overlayAnimation != SettingsRepository.OVERLAY_ANIM_STATIC) {
            Text(
                overlayAnimationDescription(overlayAnimation),
                color = subtextColor,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
            )

            SliderSetting("Expanded Size", overlayExpandScale.toFloat(), 50f..200f, "%") {
                scope.launch { settings.save(SettingsRepository.OVERLAY_EXPAND_SCALE, it.roundToInt()) }
            }
            SliderSetting("Collapsed Size", overlayCollapsedScale.toFloat(), 50f..200f, "%") {
                scope.launch { settings.save(SettingsRepository.OVERLAY_COLLAPSED_SCALE, it.roundToInt()) }
            }

            if (!overlayExpandedIndefinite) {
                SliderSetting("Expanded Hold", overlayExpandedSeconds.toFloat(), 1f..120f, "s") {
                    scope.launch { settings.save(SettingsRepository.OVERLAY_EXPANDED_SECONDS, it.roundToInt()) }
                }
            }
            SettingsToggle("Hold Open Forever", overlayExpandedIndefinite) {
                scope.launch { settings.save(SettingsRepository.OVERLAY_EXPANDED_INDEFINITE, it) }
            }

            if (overlayAnimation == SettingsRepository.OVERLAY_ANIM_LOOP) {
                if (!overlayCollapsedIndefinite) {
                    SliderSetting("Collapsed Hold", overlayCollapsedSeconds.toFloat(), 1f..120f, "s") {
                        scope.launch { settings.save(SettingsRepository.OVERLAY_COLLAPSED_SECONDS, it.roundToInt()) }
                    }
                }
                SettingsToggle("Stay Collapsed", overlayCollapsedIndefinite) {
                    scope.launch { settings.save(SettingsRepository.OVERLAY_COLLAPSED_INDEFINITE, it) }
                }
            }

            Text(
                "Collapsed Shows",
                color = textColor,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            listOf(
                SettingsRepository.OVERLAY_FIELD_DATE to "Date",
                SettingsRepository.OVERLAY_FIELD_LOCATION to "Location",
                SettingsRepository.OVERLAY_FIELD_DESCRIPTION to "Description",
                SettingsRepository.OVERLAY_FIELD_PEOPLE to "People",
                SettingsRepository.OVERLAY_FIELD_CAMERA to "Camera",
                SettingsRepository.OVERLAY_FIELD_RATING to "Rating"
            ).chunked(3).forEach { rowFields ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    rowFields.forEach { (field, label) ->
                        val selected = field in overlayCollapsedFields
                        OutlinedButton(
                            onClick = {
                                val updated =
                                    if (selected) overlayCollapsedFields - field
                                    else overlayCollapsedFields + field
                                scope.launch { settings.saveOverlayCollapsedFields(updated) }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (selected) Color.Black else textColor,
                                containerColor = if (selected) sectionColor else Color.Transparent
                            ),
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) { Text(label, fontSize = 12.sp) }
                    }
                }
            }
        }

        }
        SectionDivider()

        // ── Now Playing Pill (over the slideshow) ──
        var npOpen by remember { mutableStateOf(false) }
        CollapsibleSection("Now Playing Pill", npOpen, { npOpen = !npOpen }) {
            Text(
                "Shown over the slideshow while music plays.",
                color = subtextColor, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp)
            )
            SettingsToggle("Album Art", npShowArt) { scope.launch { settings.save(SettingsRepository.NP_SHOW_ART, it) } }
            SettingsToggle("Title", npShowTitle) { scope.launch { settings.save(SettingsRepository.NP_SHOW_TITLE, it) } }
            SettingsToggle("Artist", npShowArtist) { scope.launch { settings.save(SettingsRepository.NP_SHOW_ARTIST, it) } }
            SettingsToggle("Play / Skip Controls", npShowControls) { scope.launch { settings.save(SettingsRepository.NP_SHOW_CONTROLS, it) } }

            SliderSetting("Expanded Size", npExpandedScale.toFloat(), 50f..200f, "%") {
                scope.launch { settings.save(SettingsRepository.NP_EXPANDED_SCALE, it.roundToInt()) }
            }
            SliderSetting("Collapsed Size", npCollapsedScale.toFloat(), 50f..200f, "%") {
                scope.launch { settings.save(SettingsRepository.NP_COLLAPSED_SCALE, it.roundToInt()) }
            }
            SliderSetting("Background Opacity", npBackgroundOpacity.toFloat(), 0f..100f, "%") {
                scope.launch { settings.save(SettingsRepository.NP_BACKGROUND_OPACITY, it.roundToInt()) }
            }
            SliderSetting("Corner Radius", npCornerRadius.toFloat(), 0f..32f, "dp") {
                scope.launch { settings.save(SettingsRepository.NP_CORNER_RADIUS, it.roundToInt()) }
            }

            OverlayChoiceRow(
                "Collapse",
                listOf(
                    SettingsRepository.OVERLAY_ANIM_STATIC to "Static",
                    SettingsRepository.OVERLAY_ANIM_LOOP to "Loop",
                    SettingsRepository.OVERLAY_ANIM_ONCE to "Once"
                ),
                npAnimation
            ) { scope.launch { settings.save(SettingsRepository.NP_ANIMATION, it) } }

            if (npAnimation != SettingsRepository.OVERLAY_ANIM_STATIC) {
                SliderSetting("Expanded Hold", npExpandedSeconds.toFloat(), 1f..120f, "s") {
                    scope.launch { settings.save(SettingsRepository.NP_EXPANDED_SECONDS, it.roundToInt()) }
                }
                if (npAnimation == SettingsRepository.OVERLAY_ANIM_LOOP) {
                    SliderSetting("Collapsed Hold", npCollapsedSeconds.toFloat(), 1f..120f, "s") {
                        scope.launch { settings.save(SettingsRepository.NP_COLLAPSED_SECONDS, it.roundToInt()) }
                    }
                }
                Text("Collapsed Shows", color = textColor, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                listOf(
                    "art" to "Album Art",
                    "title" to "Title",
                    "artist" to "Artist",
                    "controls" to "Controls"
                ).chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        rowItems.forEach { (element, label) ->
                            val selected = element in npCollapsedElements
                            OutlinedButton(
                                onClick = {
                                    val updated =
                                        if (selected) npCollapsedElements - element
                                        else npCollapsedElements + element
                                    scope.launch { settings.saveNpCollapsedElements(updated) }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (selected) Color.Black else textColor,
                                    containerColor = if (selected) sectionColor else Color.Transparent
                                ),
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) { Text(label, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }

        SectionDivider()

        // ── Sync & Cache ──
        var syncOpen by remember { mutableStateOf(false) }
        CollapsibleSection("Sync & Cache", syncOpen, { syncOpen = !syncOpen }) {

        SliderSetting(
            "Sync Interval", syncInterval.toFloat(), 15f..360f, "min",
            onValueChange = {
                scope.launch { settings.save(SettingsRepository.SYNC_INTERVAL_MINUTES, it.roundToInt()) }
            },
            onValueChangeFinished = {
                // Reschedule once the drag settles so the new cadence takes effect
                // without a restart.
                scope.launch { SyncScheduler.applySyncInterval(context, settings) }
            }
        )
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

        }
        SectionDivider()

        // ── Sleep ──
        var sleepOpen by remember { mutableStateOf(false) }
        CollapsibleSection("Sleep Schedule", sleepOpen, { sleepOpen = !sleepOpen }) {
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

        }
        SectionDivider()

        // ── Music (Navidrome) ──
        var musicOpen by remember { mutableStateOf(false) }
        CollapsibleSection("Music (Navidrome)", musicOpen, { musicOpen = !musicOpen }) {

        SettingsToggle("Enable Music", navidromeEnabled) {
            scope.launch { settings.save(SettingsRepository.NAVIDROME_ENABLED, it) }
        }

        if (navidromeEnabled) {
            var editingMusic by remember { mutableStateOf(navidromeUrl.isBlank()) }
            var editNavUrl by remember(navidromeUrl) { mutableStateOf(navidromeUrl) }
            var editNavUser by remember(navidromeUsername) { mutableStateOf(navidromeUsername) }
            var editNavPass by remember(navidromePassword) { mutableStateOf(navidromePassword) }
            var navTesting by remember { mutableStateOf(false) }
            var navStatus by remember { mutableStateOf("") }
            var navOk by remember { mutableStateOf(false) }

            // Auto-test on open if configured
            LaunchedEffect(navidromeUrl) {
                if (navidromeUrl.isNotBlank() && navidromeClient != null) {
                    navTesting = true
                    navOk = navidromeClient.ping()
                    navStatus = if (navOk) "Connected" else "Connection failed"
                    navTesting = false
                }
            }

            // Status
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (navTesting) {
                    CircularProgressIndicator(color = sectionColor, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Testing...", color = subtextColor, fontSize = 13.sp)
                } else if (navOk) {
                    Text("\u2713", color = successColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(navStatus, color = successColor, fontSize = 13.sp)
                } else if (navStatus.isNotBlank()) {
                    Text("\u2717", color = errorColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(navStatus, color = errorColor, fontSize = 13.sp)
                }
            }

            if (!editingMusic && navidromeUrl.isNotBlank()) {
                InfoRow("Server", navidromeUrl)
                InfoRow("User", navidromeUsername)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { editingMusic = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = subtextColor)
                ) { Text("Edit Connection", color = subtextColor) }
            } else {
                OutlinedTextField(
                    value = editNavUrl, onValueChange = { editNavUrl = it },
                    label = { Text("Navidrome URL") },
                    placeholder = { Text("https://music.bogocat.com", color = Color(0x44FFFFFF)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editNavUser, onValueChange = { editNavUser = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editNavPass, onValueChange = { editNavPass = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                settings.saveNavidromeConfig(editNavUrl, editNavUser, editNavPass)
                                editingMusic = false
                                // Test after save
                                navTesting = true
                                navOk = navidromeClient?.ping() == true
                                navStatus = if (navOk) "Connected" else "Connection failed"
                                navTesting = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = sectionColor, contentColor = Color.Black)
                    ) { Text("Save") }
                    if (navidromeUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                editNavUrl = navidromeUrl
                                editNavUser = navidromeUsername
                                editNavPass = navidromePassword
                                editingMusic = false
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = subtextColor)
                        ) { Text("Cancel", color = subtextColor) }
                    }
                }
            }

            // Offline caching sources
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Offline Cache",
                color = subtextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            SettingsToggle("Cache Favorites Locally", navidromeSyncFavorites) {
                scope.launch {
                    settings.save(SettingsRepository.NAVIDROME_SYNC_FAVORITES, it)
                    SyncScheduler.triggerMusicSync(context)
                }
            }
            SliderSetting("Max Cached Songs", navidromeMaxCachedSongs.toFloat(), 50f..2000f, "", steps = 38) {
                scope.launch { settings.save(SettingsRepository.NAVIDROME_MAX_CACHED_SONGS, it.roundToInt()) }
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = { SyncScheduler.triggerMusicSync(context) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = sectionColor)
            ) { Text("Sync Music Now", color = sectionColor) }
            Text(
                "Songs in playlists marked \u201cCached\u201d (Music \u2192 Browse) plus favorites are stored on the device. Everything else streams.",
                color = Color(0x88FFFFFF),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            // Denon receiver output
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Denon Receiver",
                color = subtextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            var denonHostEdit by remember(denonHost) { mutableStateOf(denonHost) }
            OutlinedTextField(
                value = denonHostEdit,
                onValueChange = {
                    denonHostEdit = it
                    scope.launch { settings.save(SettingsRepository.DENON_HOST, it) }
                },
                label = { Text("Denon IP") },
                placeholder = { Text("10.89.97.15", color = Color(0x44FFFFFF)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors
            )
            Text(
                "Casting sends the Navidrome stream to the receiver over DLNA (it pulls directly, so audio survives the frame). Toggle it on the Now Playing screen.",
                color = Color(0x88FFFFFF),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Quick play test
            if (navOk && musicPlayer != null) {
                Spacer(modifier = Modifier.height(12.dp))
                var testPlaying by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            if (testPlaying) {
                                musicPlayer.togglePlayPause()
                                testPlaying = false
                            } else {
                                val songs = navidromeClient?.getRandomSongs(1) ?: emptyList()
                                if (songs.isNotEmpty()) {
                                    musicPlayer.playQueue(songs)
                                    testPlaying = true
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = sectionColor)
                ) { Text(if (testPlaying) "Stop Test" else "Test Playback", color = sectionColor) }
            }
        }

        }
        SectionDivider()

        // ── System ──
        var systemOpen by remember { mutableStateOf(false) }
        CollapsibleSection("System", systemOpen, { systemOpen = !systemOpen }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
            ) { Text("Android Settings", color = textColor) }
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
            ) { Text("WiFi", color = textColor) }
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
            ) { Text("Bluetooth", color = textColor) }
        }
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

// A collapsible settings submenu: tap the title row to expand/collapse its contents.
@Composable
private fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = sectionColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(if (expanded) "\u25B2" else "\u25BC", color = subtextColor, fontSize = 13.sp)
    }
    AnimatedVisibility(visible = expanded) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
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

// Duration picker for values that need to reach well past a slider's range
// (e.g. leave one photo up for 30 minutes). Presets cover common short and
// long intervals; "Custom" accepts any value from 1 second to 24 hours.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationSetting(
    label: String,
    seconds: Int,
    onValueChange: (Int) -> Unit
) {
    val presets = listOf(5, 10, 15, 30, 45, 60, 120, 300, 600, 900, 1800, 3600)

    var customOpen by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    var unitScale by remember { mutableStateOf(60) } // 1 = sec, 60 = min, 3600 = hr

    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = textColor, fontSize = 15.sp)
            Text(formatDuration(seconds), color = sectionColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                val selected = seconds == preset
                OutlinedButton(
                    onClick = {
                        customOpen = false
                        onValueChange(preset)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (selected) Color.Black else textColor,
                        containerColor = if (selected) sectionColor else Color.Transparent
                    ),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) { Text(formatDuration(preset), fontSize = 12.sp) }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (!customOpen) {
            OutlinedButton(
                onClick = { customOpen = true; customText = "" },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = subtextColor),
                modifier = Modifier.height(34.dp)
            ) { Text("Custom…", fontSize = 12.sp, color = subtextColor) }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it.filter(Char::isDigit).take(6) },
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = fieldColors
                )
                listOf(1 to "sec", 60 to "min", 3600 to "hr").forEach { (scale, unitLabel) ->
                    OutlinedButton(
                        onClick = { unitScale = scale },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (unitScale == scale) Color.Black else textColor,
                            containerColor = if (unitScale == scale) sectionColor else Color.Transparent
                        ),
                        modifier = Modifier.height(40.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) { Text(unitLabel, fontSize = 12.sp) }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val amount = customText.toIntOrNull()
                        if (amount != null && amount > 0) {
                            onValueChange((amount.toLong() * unitScale).coerceIn(1L, 86400L).toInt())
                            customOpen = false
                            customText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = sectionColor, contentColor = Color.Black),
                    modifier = Modifier.height(36.dp)
                ) { Text("Set", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = { customOpen = false; customText = "" },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = subtextColor),
                    modifier = Modifier.height(36.dp)
                ) { Text("Cancel", fontSize = 13.sp, color = subtextColor) }
            }
        }
    }
}

@Composable
private fun OverlayPositionRow(label: String, value: String, onSelect: (String) -> Unit) {
    val options = listOf(
        SettingsRepository.OVERLAY_POS_TOP_START to "\u2196",
        SettingsRepository.OVERLAY_POS_TOP_END to "\u2197",
        SettingsRepository.OVERLAY_POS_BOTTOM_START to "\u2199",
        SettingsRepository.OVERLAY_POS_BOTTOM_END to "\u2198"
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = textColor, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (optionValue, glyph) ->
                OutlinedButton(
                    onClick = { onSelect(optionValue) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (value == optionValue) Color.Black else textColor,
                        containerColor = if (value == optionValue) sectionColor else Color.Transparent
                    ),
                    modifier = Modifier.size(40.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text(glyph, fontSize = 16.sp) }
            }
        }
    }
}

@Composable
private fun OverlayChoiceRow(
    label: String,
    options: List<Pair<String, String>>,
    value: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = textColor, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (optionValue, optionLabel) ->
                OutlinedButton(
                    onClick = { onSelect(optionValue) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (value == optionValue) Color.Black else textColor,
                        containerColor = if (value == optionValue) sectionColor else Color.Transparent
                    ),
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) { Text(optionLabel, fontSize = 12.sp) }
            }
        }
    }
}

private fun overlayAnimationDescription(mode: String): String = when (mode) {
    SettingsRepository.OVERLAY_ANIM_LOOP ->
        "Starts big, then alternates between expanded and collapsed (or pin either state with the toggles below)."
    SettingsRepository.OVERLAY_ANIM_ONCE ->
        "Starts big, collapses once, then stays collapsed."
    else ->
        "Always expanded."
}

private fun orientationModeDescription(mode: String): String = when (mode) {
    SettingsRepository.ORIENTATION_ALL ->
        "Every photo, each shown alone (opposite orientation is fit to the screen)."
    SettingsRepository.ORIENTATION_MATCH ->
        "Only photos matching the screen orientation — landscape screen shows landscape photos, portrait screen shows portrait photos."
    else ->
        "Matching photos plus opposite orientation shown two-up: side-by-side portraits in landscape, stacked landscapes in portrait."
}

private fun formatDuration(seconds: Int): String {
    if (seconds < 60) return "${seconds}s"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return buildString {
        if (h > 0) append("${h}h")
        if (m > 0) {
            if (isNotEmpty()) append(" ")
            append("${m}m")
        }
        if (s > 0 && h == 0) {
            if (isNotEmpty()) append(" ")
            append("${s}s")
        }
    }
}

@Composable
private fun SliderSetting(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    unit: String, steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = textColor, fontSize = 15.sp)
            Text("${value.roundToInt()}$unit", color = subtextColor, fontSize = 14.sp)
        }
        Slider(
            value = value, onValueChange = onValueChange, valueRange = range, steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(thumbColor = sectionColor, activeTrackColor = sectionColor, inactiveTrackColor = Color(0x33FFFFFF))
        )
    }
}
