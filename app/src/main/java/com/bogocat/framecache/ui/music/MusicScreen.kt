package com.bogocat.framecache.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.bogocat.framecache.data.db.AlbumWithCounts
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
private val greenCache = Color(0xFF69F0AE)
private val rowBg = Color(0x18FFFFFF)

private enum class MusicTab { NowPlaying, Browse, Queue }

@Composable
fun MusicScreen(
    musicPlayer: MusicPlayer,
    songDao: SongDao,
    settings: SettingsRepository,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val nowPlaying by musicPlayer.nowPlaying.collectAsState()
    val queueState by musicPlayer.queue.collectAsState()
    val scope = rememberCoroutineScope()

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

    var playlists by remember { mutableStateOf<List<CachedPlaylist>>(emptyList()) }
    var cachedCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }
    var cachedSizeMb by remember { mutableStateOf(0L) }
    val syncPlaylistIds by settings.navidromeSyncPlaylistIds.collectAsState(initial = emptySet())
    val lastMusicSync by settings.lastMusicSyncTime.collectAsState(initial = "Never")
    val context = LocalContext.current
    var volume by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) {
        playlists = songDao.getAllPlaylists()
        cachedCount = songDao.getCachedCount()
        totalCount = songDao.getTotalCount()
        cachedSizeMb = songDao.getCachedSizeBytes() / 1024 / 1024
    }

    var tab by remember {
        mutableStateOf(if (nowPlaying.song.id.isEmpty()) MusicTab.Browse else MusicTab.NowPlaying)
    }
    LaunchedEffect(nowPlaying.song.id) {
        if (nowPlaying.song.id.isNotEmpty()) tab = MusicTab.NowPlaying
    }

    // Auto-return to slideshow after 60s of paused/idle
    LaunchedEffect(nowPlaying.isPlaying, nowPlaying.song.id) {
        if (nowPlaying.song.id.isNotEmpty() && !nowPlaying.isPlaying) {
            delay(60_000)
            // Still paused after 60s? Go back to slideshow
            if (!musicPlayer.isActive() || !musicPlayer.nowPlaying.value.isPlaying) {
                onBack()
            }
        }
    }

    // Swipe-down for settings
    var dragTotalY by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)
        .pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { dragTotalY = 0f },
                onDragEnd = { if (dragTotalY > 100f) onOpenSettings(); dragTotalY = 0f },
                onVerticalDrag = { _, dragAmount -> dragTotalY += dragAmount }
            )
        }
    ) {
        if (nowPlaying.coverArtUrl.isNotEmpty()) {
            AsyncImage(
                model = nowPlaying.coverArtUrl, contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(40.dp), alpha = 0.3f
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Tab bar
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabButton("Now Playing", tab == MusicTab.NowPlaying, enabled = nowPlaying.song.id.isNotEmpty()) {
                    tab = MusicTab.NowPlaying
                }
                TabButton("Browse", tab == MusicTab.Browse) { tab = MusicTab.Browse }
                TabButton("Queue", tab == MusicTab.Queue, enabled = queueState.items.isNotEmpty()) {
                    tab = MusicTab.Queue
                }

                Spacer(modifier = Modifier.weight(1f))

                // Volume
                Box(modifier = Modifier.width(120.dp).height(40.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x18FFFFFF)))
                    Box(modifier = Modifier.fillMaxWidth(volume).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x44FFFFFF)).align(Alignment.CenterStart))
                    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                    Slider(
                        value = volume, onValueChange = { volume = it; musicPlayer.setVolume(it) },
                        modifier = Modifier.fillMaxWidth(),
                        thumb = { Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xBBFFFFFF))) },
                        colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("$cachedCount/$totalCount cached  ${cachedSizeMb}MB", color = dimText, fontSize = 10.sp)
                    Text("Synced: $lastMusicSync", color = Color(0x66FFFFFF), fontSize = 10.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(tabBg)
                    .clickable { SyncScheduler.triggerMusicSync(context) }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text("Sync", color = accentColor, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(tabBg)
                    .clickable(onClick = onBack).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text("Photos", color = Color.White, fontSize = 13.sp)
                }
            }

            when (tab) {
                MusicTab.NowPlaying -> NowPlayingTab(nowPlaying, musicPlayer, progress, positionMs, queueState.source)
                MusicTab.Browse -> BrowseTab(musicPlayer, songDao, playlists, syncPlaylistIds, settings)
                MusicTab.Queue -> QueueTab(queueState, musicPlayer)
            }
        }
    }
}

// ─── Tab Button ───

@Composable
private fun TabButton(label: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (active) tabActiveBg else Color.Transparent)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, color = when { active -> accentColor; enabled -> Color(0xBBFFFFFF); else -> Color(0x44FFFFFF) },
            fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

// ─── Now Playing ───

@Composable
private fun NowPlayingTab(
    nowPlaying: com.bogocat.framecache.music.NowPlaying,
    musicPlayer: MusicPlayer, progress: Float, positionMs: Float, queueSource: String
) {
    val scope = rememberCoroutineScope()
    val denonOn by musicPlayer.denonOutput.collectAsState()
    var showDenonMenu by remember { mutableStateOf(false) }
    if (nowPlaying.song.id.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No music playing", color = dimText, fontSize = 16.sp)
        }
        return
    }

    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .pointerInput(Unit) { detectTapGestures(onLongPress = { showDenonMenu = true }) },
            contentAlignment = Alignment.Center
        ) {
            if (nowPlaying.coverArtUrl.isNotEmpty()) {
                AsyncImage(model = nowPlaying.coverArtUrl, contentDescription = "Album art",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(260.dp).clip(RoundedCornerShape(16.dp)))
            }
            // Long-press the album art for output options (e.g. cast to the Denon).
            DropdownMenu(expanded = showDenonMenu, onDismissRequest = { showDenonMenu = false }) {
                DropdownMenuItem(
                    text = { Text(if (denonOn) "Stop Denon" else "Play on Denon") },
                    onClick = {
                        showDenonMenu = false
                        musicPlayer.setDenonOutput(!denonOn)
                    }
                )
            }
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(nowPlaying.song.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(4.dp))
            Text(nowPlaying.song.artist, color = Color(0xBBFFFFFF), fontSize = 16.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(nowPlaying.song.album, color = dimText, fontSize = 13.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (nowPlaying.cached) "Cached" else "Streaming",
                    color = if (nowPlaying.cached) greenCache else Color(0xFFFFAB40), fontSize = 11.sp)
                if (denonOn) Text("Denon", color = Color(0xFFFFB74D), fontSize = 11.sp)
                if (queueSource.isNotEmpty()) Text(queueSource, color = Color(0x66FFFFFF), fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Slider(value = progress, onValueChange = {
                val dur = musicPlayer.getDuration(); if (dur > 0) musicPlayer.seekTo((it * dur).toLong())
            }, modifier = Modifier.fillMaxWidth(0.85f), colors = SliderDefaults.colors(
                thumbColor = Color.White, activeTrackColor = accentColor, inactiveTrackColor = Color(0x33FFFFFF)))
            Row(modifier = Modifier.fillMaxWidth(0.85f), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(positionMs.toLong()), color = dimText, fontSize = 11.sp)
                Text(formatTime(musicPlayer.getDuration()), color = dimText, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                ControlButton("<<", 56) { scope.launch { musicPlayer.skipPrevious() } }
                ControlButton(if (nowPlaying.isPlaying) "||" else ">", 68, Color(0x55FFFFFF)) { musicPlayer.togglePlayPause() }
                ControlButton(">>", 56) { scope.launch { musicPlayer.skipNext() } }
            }
        }
    }
}

// ─── Browse ───

private sealed class BrowseRoute {
    data object Home : BrowseRoute()
    data object PlaylistList : BrowseRoute()
    data class PlaylistDetail(val id: String, val name: String) : BrowseRoute()
    data object ArtistList : BrowseRoute()
    data class ArtistAlbums(val name: String) : BrowseRoute()
    data class AlbumDetail(val artist: String, val albumId: String, val album: String) : BrowseRoute()
    data object SongList : BrowseRoute()
    data object Favorites : BrowseRoute()
    data class Search(val query: String) : BrowseRoute()
}

@Composable
private fun BrowseTab(
    musicPlayer: MusicPlayer, songDao: SongDao,
    playlists: List<CachedPlaylist>, syncPlaylistIds: Set<String>, settings: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    var route by remember { mutableStateOf<BrowseRoute>(BrowseRoute.Home) }
    var cachedOnly by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        // Header with back + cached toggle
        if (route != BrowseRoute.Home) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("< Back", color = accentColor, fontSize = 14.sp,
                    modifier = Modifier.clickable {
                        route = when (val r = route) {
                            is BrowseRoute.PlaylistDetail -> BrowseRoute.PlaylistList
                            is BrowseRoute.ArtistAlbums -> BrowseRoute.ArtistList
                            is BrowseRoute.AlbumDetail -> BrowseRoute.ArtistAlbums(r.artist)
                            else -> BrowseRoute.Home
                        }
                    }.padding(8.dp))

                val title = when (route) {
                    is BrowseRoute.PlaylistList -> "Playlists"
                    is BrowseRoute.PlaylistDetail -> (route as BrowseRoute.PlaylistDetail).name
                    is BrowseRoute.ArtistList -> "Artists"
                    is BrowseRoute.ArtistAlbums -> (route as BrowseRoute.ArtistAlbums).name
                    is BrowseRoute.AlbumDetail -> (route as BrowseRoute.AlbumDetail).album
                    is BrowseRoute.SongList -> "All Songs"
                    is BrowseRoute.Search -> "Search: ${(route as BrowseRoute.Search).query}"
                    else -> ""
                }
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                // Cached only toggle
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (cachedOnly) Color(0x3369F0AE) else tabBg)
                        .clickable { cachedOnly = !cachedOnly }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(if (cachedOnly) "Cached" else "All", color = if (cachedOnly) greenCache else dimText, fontSize = 12.sp)
                }
            }
        }

        when (route) {
            BrowseRoute.Home -> BrowseHome(
                playlists = playlists, songDao = songDao, cachedOnly = cachedOnly,
                onCachedToggle = { cachedOnly = it },
                onPlaylists = { route = BrowseRoute.PlaylistList },
                onArtists = { route = BrowseRoute.ArtistList },
                onSongs = { route = BrowseRoute.SongList },
                onFavorites = { route = BrowseRoute.Favorites },
                onSearch = { route = BrowseRoute.Search(it) },
                musicPlayer = musicPlayer
            )
            BrowseRoute.PlaylistList -> PlaylistListView(
                playlists = playlists, syncPlaylistIds = syncPlaylistIds, settings = settings,
                onPlaylist = { route = BrowseRoute.PlaylistDetail(it.id, it.name) },
                musicPlayer = musicPlayer
            )
            is BrowseRoute.PlaylistDetail -> {
                val detail = route as BrowseRoute.PlaylistDetail
                SongListView(
                    loadSongs = { songDao.getSongsForPlaylist(detail.id) },
                    source = detail.name, musicPlayer = musicPlayer, cachedOnly = cachedOnly,
                    songDao = songDao
                )
            }
            BrowseRoute.ArtistList -> ArtistListView(
                songDao = songDao, cachedOnly = cachedOnly,
                onArtist = { route = BrowseRoute.ArtistAlbums(it) },
                musicPlayer = musicPlayer
            )
            is BrowseRoute.ArtistAlbums -> {
                val artist = (route as BrowseRoute.ArtistAlbums).name
                ArtistAlbumsView(
                    artist = artist, songDao = songDao, musicPlayer = musicPlayer,
                    onAlbum = { route = BrowseRoute.AlbumDetail(artist, it.albumId, it.album) }
                )
            }
            is BrowseRoute.AlbumDetail -> {
                val detail = route as BrowseRoute.AlbumDetail
                SongListView(
                    loadSongs = { songDao.getSongsByAlbum(detail.albumId) },
                    source = detail.album, musicPlayer = musicPlayer, cachedOnly = cachedOnly,
                    songDao = songDao
                )
            }
            BrowseRoute.SongList -> SongListView(
                loadSongs = { if (cachedOnly) songDao.getAllCachedSongs() else songDao.getAllSongs() },
                source = "All Songs", musicPlayer = musicPlayer, cachedOnly = cachedOnly,
                songDao = songDao
            )
            BrowseRoute.Favorites -> SongListView(
                loadSongs = { songDao.getStarredSongs() },
                source = "Favorites", musicPlayer = musicPlayer, cachedOnly = cachedOnly,
                songDao = songDao
            )
            is BrowseRoute.Search -> {
                val q = (route as BrowseRoute.Search).query
                SongListView(
                    loadSongs = { songDao.search(q) },
                    source = "Search: $q", musicPlayer = musicPlayer, cachedOnly = cachedOnly,
                    songDao = songDao
                )
            }
        }
    }
}

@Composable
private fun BrowseHome(
    playlists: List<CachedPlaylist>, songDao: SongDao, cachedOnly: Boolean,
    onCachedToggle: (Boolean) -> Unit,
    onPlaylists: () -> Unit, onArtists: () -> Unit, onSongs: () -> Unit,
    onFavorites: () -> Unit,
    onSearch: (String) -> Unit,
    musicPlayer: MusicPlayer
) {
    val scope = rememberCoroutineScope()
    var artistCount by remember { mutableStateOf(0) }
    var songCount by remember { mutableStateOf(0) }
    var cachedSongCount by remember { mutableStateOf(0) }
    var starredCount by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        artistCount = songDao.getAllArtists().size
        songCount = songDao.getTotalCount()
        cachedSongCount = songDao.getCachedCount()
        starredCount = songDao.getStarredCount()
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Search bar + action buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search songs, artists, albums...", color = Color(0x55FFFFFF), fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).height(48.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        cursorColor = accentColor
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                // Search button
                if (searchQuery.isNotBlank()) {
                    Box(
                        modifier = Modifier.height(48.dp).clip(RoundedCornerShape(10.dp)).background(accentColor)
                            .clickable { onSearch(searchQuery) }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Go", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }

                // Shuffle
                Box(
                    modifier = Modifier.height(48.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x22FFFFFF))
                        .clickable { scope.launch { musicPlayer.shuffleAll() } }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("\u21C4", color = accentColor, fontSize = 18.sp) }

                // Cached toggle
                Box(
                    modifier = Modifier.height(48.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (cachedOnly) Color(0x3369F0AE) else Color(0x22FFFFFF))
                        .clickable { onCachedToggle(!cachedOnly) }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text(if (cachedOnly) "Cached" else "All", color = if (cachedOnly) greenCache else dimText, fontSize = 13.sp) }
            }
        }

        // Navigation rows
        item { BrowseRow("Favorites", "$starredCount starred", onClick = onFavorites) }
        item { BrowseRow("Playlists", "${playlists.size}", onClick = onPlaylists) }
        item { BrowseRow("Artists", "$artistCount", onClick = onArtists) }
        item { BrowseRow("All Songs", "$songCount ($cachedSongCount cached)", onClick = onSongs) }
    }
}

@Composable
private fun BrowseRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(rowBg)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, color = Color.White, fontSize = 16.sp)
            Text(subtitle, color = dimText, fontSize = 12.sp)
        }
        Text(">", color = dimText, fontSize = 18.sp)
    }
}

// ─── Playlist List ───

@Composable
private fun PlaylistListView(
    playlists: List<CachedPlaylist>, syncPlaylistIds: Set<String>, settings: SettingsRepository,
    onPlaylist: (CachedPlaylist) -> Unit, musicPlayer: MusicPlayer
) {
    val scope = rememberCoroutineScope()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(playlists.size) { index ->
            val playlist = playlists[index]
            val isSynced = playlist.id in syncPlaylistIds
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(rowBg)
                    .clickable { onPlaylist(playlist) }
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(playlist.name, color = Color.White, fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${playlist.songCount} songs", color = dimText, fontSize = 12.sp)
                        if (isSynced) Text("Cached", color = greenCache, fontSize = 12.sp)
                    }
                }
                // Cache toggle
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(if (isSynced) Color(0x3369F0AE) else Color(0x22FFFFFF))
                        .clickable { scope.launch {
                            settings.saveSyncPlaylistIds(if (isSynced) syncPlaylistIds - playlist.id else syncPlaylistIds + playlist.id)
                        } },
                    contentAlignment = Alignment.Center
                ) { Text(if (isSynced) "\u2713" else "\u2193", color = if (isSynced) greenCache else dimText, fontSize = 16.sp) }

                // Shuffle
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0x22FFFFFF))
                        .clickable { scope.launch { musicPlayer.playPlaylist(playlist.id, playlist.name, shuffle = true) } },
                    contentAlignment = Alignment.Center
                ) { Text("\u21C4", color = accentColor, fontSize = 16.sp) }
            }
        }
    }
}

// ─── Artist List ───

@Composable
private fun ArtistListView(
    songDao: SongDao, cachedOnly: Boolean, onArtist: (String) -> Unit, musicPlayer: MusicPlayer
) {
    val scope = rememberCoroutineScope()
    var artists by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(cachedOnly) {
        artists = if (cachedOnly) songDao.getCachedArtists() else songDao.getAllArtists()
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(artists.size) { index ->
            val artist = artists[index]
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .clickable { onArtist(artist) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text(artist, color = Color.White, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("\u21C4", color = accentColor, fontSize = 14.sp,
                        modifier = Modifier.clip(CircleShape)
                            .clickable { scope.launch {
                                val songs = songDao.getSongsByArtist(artist)
                                musicPlayer.playCachedQueue(songs, source = artist, shuffle = true)
                            } }.padding(8.dp))
                    Text(">", color = dimText, fontSize = 14.sp)
                }
            }
        }
    }
}

// ─── Song List (reusable for playlist/album/artist/all songs) ───

@Composable
private fun SongListView(
    loadSongs: suspend () -> List<CachedSong>,
    source: String, musicPlayer: MusicPlayer, cachedOnly: Boolean, songDao: SongDao
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var songs by remember { mutableStateOf<List<CachedSong>>(emptyList()) }
    var pendingBulk by remember { mutableStateOf<BulkCache?>(null) }

    suspend fun reload() {
        val all = loadSongs()
        songs = if (cachedOnly) all.filter { it.filePath != null && it.filePath.isNotEmpty() } else all
    }
    LaunchedEffect(source, cachedOnly) { reload() }

    fun setPinned(ids: List<String>, pinned: Boolean) {
        if (ids.isEmpty()) return
        scope.launch {
            ids.chunked(500).forEach { songDao.setPinnedBatch(it, pinned) }
            SyncScheduler.triggerMusicSync(context)
            reload()
        }
    }

    val cachedCount = songs.count { it.filePath != null && it.filePath.isNotEmpty() }
    val bulkState = when {
        songs.isNotEmpty() && songs.all { it.pinned } -> CacheState.Cached
        songs.any { it.pinned || (it.filePath != null && it.filePath.isNotEmpty()) } -> CacheState.Pending
        else -> CacheState.None
    }

    Column {
        // Play / Shuffle / Queue + bulk cache toggle
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF))
                    .clickable { scope.launch { musicPlayer.playCachedQueue(songs, source = source) } }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("Play All", color = Color.White, fontSize = 13.sp) }
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF))
                    .clickable { scope.launch { musicPlayer.playCachedQueue(songs, source = source, shuffle = true) } }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("Shuffle", color = accentColor, fontSize = 13.sp) }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF))
                    .clickable { scope.launch {
                        musicPlayer.addToQueue(songs.map { it.toSong() })
                    } }.padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("+ Queue", color = dimText, fontSize = 13.sp) }
            CacheButton(bulkState) {
                if (bulkState == CacheState.None) {
                    val ids = songs.map { it.id }
                    if (ids.size > BULK_CACHE_CONFIRM_THRESHOLD) {
                        pendingBulk = BulkCache(source, ids.size) { setPinned(ids, true) }
                    } else setPinned(ids, true)
                } else {
                    setPinned(songs.map { it.id }, false)
                }
            }
        }

        Text("${songs.size} songs · $cachedCount cached", color = dimText, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            items(songs.size) { index ->
                val song = songs[index]
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .clickable {
                            scope.launch { musicPlayer.playCachedQueue(songs, source = source) }
                        }.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("${index + 1}", color = dimText, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(song.title, color = Color(0xCCFFFFFF), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${song.artist} — ${song.album}", color = dimText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(formatTime(song.duration * 1000L), color = dimText, fontSize = 11.sp)
                    CacheButton(songCacheState(song), size = 34.dp) {
                        setPinned(listOf(song.id), !song.pinned)
                    }
                }
            }
        }
    }
    BulkCacheDialog(pendingBulk) { pendingBulk = null }
}

// ─── Artist albums ───

@Composable
private fun ArtistAlbumsView(
    artist: String, songDao: SongDao, musicPlayer: MusicPlayer,
    onAlbum: (AlbumWithCounts) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var albums by remember { mutableStateOf<List<AlbumWithCounts>>(emptyList()) }
    var reloadKey by remember { mutableStateOf(0) }
    var pendingBulk by remember { mutableStateOf<BulkCache?>(null) }

    LaunchedEffect(artist, reloadKey) { albums = songDao.getAlbumsByArtist(artist) }

    val totalSongs = albums.sumOf { it.songCount }
    val cachedSongs = albums.sumOf { it.cachedCount }
    val pinnedSongs = albums.sumOf { it.pinnedCount }
    val artistState = when {
        totalSongs > 0 && cachedSongs == totalSongs -> CacheState.Cached
        pinnedSongs > 0 || cachedSongs > 0 -> CacheState.Pending
        else -> CacheState.None
    }

    fun pinAlbum(album: AlbumWithCounts, pinned: Boolean) {
        scope.launch {
            songDao.setPinnedForAlbum(album.albumId, pinned)
            SyncScheduler.triggerMusicSync(context)
            reloadKey++
        }
    }
    fun pinArtist(pinned: Boolean) {
        scope.launch {
            songDao.setPinnedForArtist(artist, pinned)
            SyncScheduler.triggerMusicSync(context)
            reloadKey++
        }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF))
                    .clickable { scope.launch { musicPlayer.playCachedQueue(songDao.getSongsByArtist(artist), source = artist) } }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("Play All", color = Color.White, fontSize = 13.sp) }
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF))
                    .clickable { scope.launch { musicPlayer.playCachedQueue(songDao.getSongsByArtist(artist), source = artist, shuffle = true) } }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("Shuffle", color = accentColor, fontSize = 13.sp) }
            CacheButton(artistState) {
                if (artistState == CacheState.None) {
                    if (totalSongs > BULK_CACHE_CONFIRM_THRESHOLD) {
                        pendingBulk = BulkCache(artist, totalSongs) { pinArtist(true) }
                    } else pinArtist(true)
                } else pinArtist(false)
            }
        }

        Text(
            "${albums.size} albums · $cachedSongs/$totalSongs cached",
            color = dimText, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(albums.size) { index ->
                val album = albums[index]
                val state = albumCacheState(album)
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(rowBg)
                        .clickable { onAlbum(album) }
                        .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(album.album, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val meta = buildList {
                            album.year?.let { add(it.toString()) }
                            add("${album.songCount} songs")
                            if (album.cachedCount > 0) add("${album.cachedCount} cached")
                        }
                        Text(meta.joinToString(" · "), color = dimText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    CacheButton(state, size = 36.dp) {
                        if (state == CacheState.None) {
                            if (album.songCount > BULK_CACHE_CONFIRM_THRESHOLD) {
                                pendingBulk = BulkCache(album.album, album.songCount) { pinAlbum(album, true) }
                            } else pinAlbum(album, true)
                        } else pinAlbum(album, false)
                    }
                }
            }
        }
    }
    BulkCacheDialog(pendingBulk) { pendingBulk = null }
}

// ─── Cache affordances ───

private const val BULK_CACHE_CONFIRM_THRESHOLD = 50

private enum class CacheState { Cached, Pending, None }

private fun songCacheState(song: CachedSong): CacheState = when {
    song.filePath != null && song.filePath.isNotEmpty() -> CacheState.Cached
    song.pinned -> CacheState.Pending
    else -> CacheState.None
}

private fun albumCacheState(album: AlbumWithCounts): CacheState = when {
    album.songCount > 0 && album.cachedCount == album.songCount -> CacheState.Cached
    album.pinnedCount > 0 || album.cachedCount > 0 -> CacheState.Pending
    else -> CacheState.None
}

@Composable
private fun CacheButton(state: CacheState, size: Dp = 40.dp, onClick: () -> Unit) {
    val glyph: String
    val fg: Color
    val bg: Color
    when (state) {
        CacheState.Cached -> { glyph = "\u2713"; fg = greenCache; bg = Color(0x3369F0AE) }
        CacheState.Pending -> { glyph = "\u2193"; fg = Color(0xFFFFAB40); bg = Color(0x33FFAB40) }
        CacheState.None -> { glyph = "\u2193"; fg = dimText; bg = Color(0x22FFFFFF) }
    }
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(glyph, color = fg, fontSize = 15.sp) }
}

private data class BulkCache(val label: String, val count: Int, val run: () -> Unit)

@Composable
private fun BulkCacheDialog(action: BulkCache?, onDismiss: () -> Unit) {
    if (action == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cache ${action.count} songs?") },
        text = { Text("Download ${action.count} songs from \"${action.label}\" for offline playback?") },
        confirmButton = { TextButton(onClick = { action.run(); onDismiss() }) { Text("Cache") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun CachedSong.toSong() = com.bogocat.framecache.api.navidrome.Song(
    id = id, title = title, artist = artist, album = album, albumId = albumId,
    artistId = artistId, coverArt = coverArt, duration = duration, track = track, year = year, genre = genre
)

// ─── Queue ───

@Composable
private fun QueueTab(queueState: com.bogocat.framecache.music.QueueState, musicPlayer: MusicPlayer) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(queueState.currentIndex) {
        if (queueState.currentIndex >= 0) listState.animateScrollToItem(queueState.currentIndex)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        // Queue header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                if (queueState.source.isNotEmpty()) Text("Playing from: ${queueState.source}", color = dimText, fontSize = 12.sp)
                Text("${queueState.items.size} tracks", color = Color(0x66FFFFFF), fontSize = 11.sp)
            }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF))
                    .clickable { musicPlayer.clearQueue() }.padding(horizontal = 12.dp, vertical = 8.dp)
            ) { Text("Clear", color = Color(0xFFFF5252), fontSize = 12.sp) }
        }

        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(queueState.items.size) { index ->
                val song = queueState.items[index]
                val isCurrent = index == queueState.currentIndex
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(if (isCurrent) Color(0x22FFFFFF) else Color.Transparent)
                        .clickable {
                            scope.launch {
                                musicPlayer.playQueue(queueState.items, startIndex = index,
                                    source = queueState.source, shuffle = false)
                            }
                        }.padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("${index + 1}", color = if (isCurrent) accentColor else dimText,
                        fontSize = 13.sp, modifier = Modifier.width(28.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(song.title, color = if (isCurrent) Color.White else Color(0xCCFFFFFF),
                            fontSize = 14.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, color = dimText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(formatTime(song.duration * 1000L), color = dimText, fontSize = 11.sp)
                    // Remove button
                    if (!isCurrent) {
                        Text("\u2715", color = Color(0x66FFFFFF), fontSize = 14.sp,
                            modifier = Modifier.clip(CircleShape)
                                .clickable { musicPlayer.removeFromQueue(index) }
                                .padding(8.dp))
                    }
                }
            }
        }
    }
}

// ─── Shared ───

@Composable
private fun ControlButton(text: String, size: Int, bg: Color = Color(0x33FFFFFF), onClick: () -> Unit) {
    Box(modifier = Modifier.size(size.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = (size / 3).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60)
}
