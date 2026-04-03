package com.bogocat.framecache.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.bogocat.framecache.data.db.CachedPlaylist
import com.bogocat.framecache.data.db.CachedSong
import com.bogocat.framecache.data.db.SongDao
import com.bogocat.framecache.data.settings.SettingsRepository
import com.bogocat.framecache.music.MusicPlayer
import com.bogocat.framecache.sync.SyncScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val bgColor = Color(0xFF0A0A0A)
private val accentColor = Color(0xFF4FC3F7)
private val dimText = Color(0x99FFFFFF)
private val tabBg = Color(0x22FFFFFF)
private val tabActiveBg = Color(0x44FFFFFF)

private enum class MusicTab { NowPlaying, Playlists, Queue }

@Composable
fun MusicScreen(
    musicPlayer: MusicPlayer,
    songDao: SongDao,
    settings: SettingsRepository,
    onBack: () -> Unit
) {
    val nowPlaying by musicPlayer.nowPlaying.collectAsState()
    val queueState by musicPlayer.queue.collectAsState()
    val scope = rememberCoroutineScope()

    // Progress polling
    var progress by remember { mutableFloatStateOf(0f) }
    var positionMs by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(nowPlaying.song.id, nowPlaying.isPlaying) {
        while (true) {
            val pos = musicPlayer.getPosition()
            val dur = musicPlayer.getDuration()
            positionMs = pos.toFloat()
            progress = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }

    // Data
    var playlists by remember { mutableStateOf<List<CachedPlaylist>>(emptyList()) }
    var cachedCount by remember { mutableStateOf(0) }
    var cachedSizeMb by remember { mutableStateOf(0L) }
    val syncPlaylistIds by settings.navidromeSyncPlaylistIds.collectAsState(initial = emptySet())
    val lastMusicSync by settings.lastMusicSyncTime.collectAsState(initial = "Never")
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        playlists = songDao.getAllPlaylists()
        cachedCount = songDao.getCachedCount()
        cachedSizeMb = songDao.getCachedSizeBytes() / 1024 / 1024
    }

    var volume by remember { mutableFloatStateOf(1f) }

    // Default to Playlists if nothing is playing, else Now Playing
    var tab by remember {
        mutableStateOf(if (nowPlaying.song.id.isEmpty()) MusicTab.Playlists else MusicTab.NowPlaying)
    }

    // Switch to Now Playing when music starts
    LaunchedEffect(nowPlaying.song.id) {
        if (nowPlaying.song.id.isNotEmpty()) tab = MusicTab.NowPlaying
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        // Blurred background
        if (nowPlaying.coverArtUrl.isNotEmpty()) {
            AsyncImage(
                model = nowPlaying.coverArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(40.dp),
                alpha = 0.3f
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Tab bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabButton("Now Playing", tab == MusicTab.NowPlaying, enabled = nowPlaying.song.id.isNotEmpty()) {
                    tab = MusicTab.NowPlaying
                }
                TabButton("Playlists", tab == MusicTab.Playlists) {
                    tab = MusicTab.Playlists
                }
                TabButton("Queue", tab == MusicTab.Queue, enabled = queueState.items.isNotEmpty()) {
                    tab = MusicTab.Queue
                }

                Spacer(modifier = Modifier.weight(1f))

                // Volume
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0x18FFFFFF))
                    )
                    // Filled
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(volume)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0x44FFFFFF))
                            .align(Alignment.CenterStart)
                    )
                    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                    Slider(
                        value = volume,
                        onValueChange = {
                            volume = it
                            musicPlayer.setVolume(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xBBFFFFFF))
                            )
                        },
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent,
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Cache info + last sync
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$cachedCount songs  ${cachedSizeMb}MB",
                        color = dimText,
                        fontSize = 10.sp
                    )
                    Text(
                        "Synced: $lastMusicSync",
                        color = Color(0x66FFFFFF),
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Sync now
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(tabBg)
                        .clickable { SyncScheduler.triggerMusicSync(context) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text("Sync", color = accentColor, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Back to photos
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(tabBg)
                        .clickable(onClick = onBack)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text("Photos", color = Color.White, fontSize = 13.sp)
                }
            }

            // Content
            when (tab) {
                MusicTab.NowPlaying -> NowPlayingTab(
                    nowPlaying = nowPlaying,
                    musicPlayer = musicPlayer,
                    progress = progress,
                    positionMs = positionMs,
                    queueSource = queueState.source
                )
                MusicTab.Playlists -> PlaylistsTab(
                    playlists = playlists,
                    cachedCount = cachedCount,
                    musicPlayer = musicPlayer,
                    songDao = songDao,
                    syncPlaylistIds = syncPlaylistIds,
                    settings = settings
                )
                MusicTab.Queue -> QueueTab(
                    queueState = queueState,
                    musicPlayer = musicPlayer
                )
            }
        }
    }
}

@Composable
private fun TabButton(label: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) tabActiveBg else Color.Transparent)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = when {
                active -> accentColor
                enabled -> Color(0xBBFFFFFF)
                else -> Color(0x44FFFFFF)
            },
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ─── Now Playing ───

@Composable
private fun NowPlayingTab(
    nowPlaying: com.bogocat.framecache.music.NowPlaying,
    musicPlayer: MusicPlayer,
    progress: Float,
    positionMs: Float,
    queueSource: String
) {
    val scope = rememberCoroutineScope()

    if (nowPlaying.song.id.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No music playing", color = dimText, fontSize = 16.sp)
        }
        return
    }

    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        // Left: album art
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            if (nowPlaying.coverArtUrl.isNotEmpty()) {
                AsyncImage(
                    model = nowPlaying.coverArtUrl,
                    contentDescription = "Album art",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }
        }

        // Right: info + controls
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = nowPlaying.song.title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                nowPlaying.song.artist,
                color = Color(0xBBFFFFFF),
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                nowPlaying.song.album,
                color = dimText,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (nowPlaying.cached) "Cached" else "Streaming",
                    color = if (nowPlaying.cached) Color(0xFF69F0AE) else Color(0xFFFFAB40),
                    fontSize = 11.sp
                )
                if (queueSource.isNotEmpty()) {
                    Text(queueSource, color = Color(0x66FFFFFF), fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Seek bar
            Slider(
                value = progress,
                onValueChange = {
                    val dur = musicPlayer.getDuration()
                    if (dur > 0) musicPlayer.seekTo((it * dur).toLong())
                },
                modifier = Modifier.fillMaxWidth(0.85f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = Color(0x33FFFFFF)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(positionMs.toLong()), color = dimText, fontSize = 11.sp)
                Text(formatTime(musicPlayer.getDuration()), color = dimText, fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlButton(text = "<<", size = 56) {
                    scope.launch { musicPlayer.skipPrevious() }
                }
                ControlButton(
                    text = if (nowPlaying.isPlaying) "||" else ">",
                    size = 68,
                    bg = Color(0x55FFFFFF)
                ) {
                    musicPlayer.togglePlayPause()
                }
                ControlButton(text = ">>", size = 56) {
                    scope.launch { musicPlayer.skipNext() }
                }
            }
        }
    }
}

// ─── Playlists ───

@Composable
private fun PlaylistsTab(
    playlists: List<CachedPlaylist>,
    cachedCount: Int,
    musicPlayer: MusicPlayer,
    songDao: SongDao,
    syncPlaylistIds: Set<String>,
    settings: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    var cachedSongs by remember { mutableStateOf<List<CachedSong>>(emptyList()) }
    var showCached by remember { mutableStateOf(false) }
    var artists by remember { mutableStateOf<List<String>>(emptyList()) }
    var showArtists by remember { mutableStateOf(false) }
    var expandedArtist by remember { mutableStateOf<String?>(null) }
    var artistSongs by remember { mutableStateOf<List<CachedSong>>(emptyList()) }

    LaunchedEffect(Unit) {
        artists = songDao.getAllArtists()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        // Shuffle all button
        if (cachedCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor)
                    .clickable { scope.launch { musicPlayer.shuffleAll() } }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Shuffle All ($cachedCount songs)", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Playlists
            if (playlists.isNotEmpty()) {
                item {
                    Text("Playlists", color = dimText, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                items(playlists.size) { index ->
                    val playlist = playlists[index]
                    val isSynced = playlist.id in syncPlaylistIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x18FFFFFF))
                            .clickable {
                                scope.launch { musicPlayer.playPlaylist(playlist.id, playlist.name) }
                            }
                            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Playlist info
                        Column(modifier = Modifier.weight(1f)) {
                            Text(playlist.name, color = Color.White, fontSize = 15.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${playlist.songCount} songs", color = dimText, fontSize = 12.sp)
                                if (isSynced) {
                                    Text("Cached", color = Color(0xFF69F0AE), fontSize = 12.sp)
                                }
                            }
                        }

                        // Cache toggle
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSynced) Color(0x3369F0AE) else Color(0x22FFFFFF))
                                .clickable {
                                    scope.launch {
                                        val newIds = if (isSynced) {
                                            syncPlaylistIds - playlist.id
                                        } else {
                                            syncPlaylistIds + playlist.id
                                        }
                                        settings.saveSyncPlaylistIds(newIds)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                if (isSynced) "Cached" else "Cache",
                                color = if (isSynced) Color(0xFF69F0AE) else dimText,
                                fontSize = 11.sp
                            )
                        }

                        // Shuffle
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable {
                                    scope.launch { musicPlayer.playPlaylist(playlist.id, playlist.name, shuffle = true) }
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("Shuffle", color = accentColor, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Artists browser
            if (artists.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showArtists = !showArtists }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Artists (${artists.size})", color = dimText, fontSize = 12.sp)
                        Text(if (showArtists) "Hide" else "Show", color = accentColor, fontSize = 12.sp)
                    }
                }

                if (showArtists) {
                    items(artists.size) { index ->
                        val artist = artists[index]
                        val isExpanded = expandedArtist == artist
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isExpanded) Color(0x18FFFFFF) else Color.Transparent)
                                    .clickable {
                                        if (isExpanded) {
                                            expandedArtist = null
                                        } else {
                                            expandedArtist = artist
                                            scope.launch { artistSongs = songDao.getSongsByArtist(artist) }
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    artist,
                                    color = if (isExpanded) Color.White else Color(0xCCFFFFFF),
                                    fontSize = 14.sp,
                                    fontWeight = if (isExpanded) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isExpanded) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            "Play",
                                            color = accentColor,
                                            fontSize = 12.sp,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    scope.launch { musicPlayer.playCachedQueue(artistSongs, source = artist) }
                                                }
                                                .padding(6.dp)
                                        )
                                        Text(
                                            "Shuffle",
                                            color = accentColor,
                                            fontSize = 12.sp,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    scope.launch { musicPlayer.playCachedQueue(artistSongs, source = artist, shuffle = true) }
                                                }
                                                .padding(6.dp)
                                        )
                                    }
                                }
                            }

                            if (isExpanded) {
                                artistSongs.forEach { song ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scope.launch {
                                                    musicPlayer.playCachedQueue(
                                                        artistSongs,
                                                        source = artist
                                                    )
                                                }
                                            }
                                            .padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                song.title,
                                                color = Color(0xBBFFFFFF),
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                song.album,
                                                color = Color(0x66FFFFFF),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(formatTime(song.duration * 1000L), color = dimText, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Cached songs browser
            if (cachedCount > 0) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!showCached) {
                                    scope.launch { cachedSongs = songDao.getRandomCached(100) }
                                }
                                showCached = !showCached
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cached Songs ($cachedCount)", color = dimText, fontSize = 12.sp)
                        Text(if (showCached) "Hide" else "Show", color = accentColor, fontSize = 12.sp)
                    }
                }

                if (showCached) {
                    items(cachedSongs.size) { index ->
                        val song = cachedSongs[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    scope.launch {
                                        musicPlayer.playCachedQueue(cachedSongs, source = "Cached Songs")
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    color = Color(0xCCFFFFFF),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${song.artist} - ${song.album}",
                                    color = dimText,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(formatTime(song.duration * 1000L), color = dimText, fontSize = 11.sp)
                        }
                    }
                }
            }

            if (playlists.isEmpty() && cachedCount == 0) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No music synced yet.\nPlaylists sync automatically on WiFi.",
                            color = dimText,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ─── Queue ───

@Composable
private fun QueueTab(
    queueState: com.bogocat.framecache.music.QueueState,
    musicPlayer: MusicPlayer
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(queueState.currentIndex) {
        if (queueState.currentIndex >= 0) {
            listState.animateScrollToItem(queueState.currentIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        if (queueState.source.isNotEmpty()) {
            Text(
                "Playing from: ${queueState.source}",
                color = dimText,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(queueState.items.size) { index ->
                val song = queueState.items[index]
                val isCurrent = index == queueState.currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCurrent) Color(0x22FFFFFF) else Color.Transparent)
                        .clickable {
                            scope.launch {
                                musicPlayer.playQueue(
                                    queueState.items,
                                    startIndex = index,
                                    source = queueState.source,
                                    shuffle = false
                                )
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${index + 1}",
                        color = if (isCurrent) accentColor else dimText,
                        fontSize = 13.sp,
                        modifier = Modifier.width(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song.title,
                            color = if (isCurrent) Color.White else Color(0xCCFFFFFF),
                            fontSize = 14.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            song.artist,
                            color = dimText,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(formatTime(song.duration * 1000L), color = dimText, fontSize = 11.sp)
                }
            }
        }
    }
}

// ─── Shared ───

@Composable
private fun ControlButton(
    text: String,
    size: Int,
    bg: Color = Color(0x33FFFFFF),
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = (size / 3).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
