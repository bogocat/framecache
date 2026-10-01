package com.bogocat.framecache.ui.slideshow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.bogocat.framecache.music.MusicPlayer
import com.bogocat.framecache.music.NowPlaying
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val dividerColor = Color(0x33FFFFFF)

// Elements the pill can show (expanded) and keep (collapsed).
const val NP_ELEMENT_ART = "art"
const val NP_ELEMENT_TITLE = "title"
const val NP_ELEMENT_ARTIST = "artist"
const val NP_ELEMENT_CONTROLS = "controls"

/**
 * Presentation config for the now-playing pill shown over the slideshow. Mirrors the
 * photo-info overlay: a set of always/expanded elements, a size scale, and an optional
 * expand->collapse cycle that keeps a configurable subset.
 */
data class NowPlayingConfig(
    val showArt: Boolean = true,
    val showTitle: Boolean = true,
    val showArtist: Boolean = true,
    val showControls: Boolean = true,
    val scale: Float = 1f,
    val backgroundOpacity: Float = 0.80f,
    val cornerRadius: Int = 16,
    val animationEnabled: Boolean = false,
    val loop: Boolean = false,
    val expandedSeconds: Int = 10,
    val collapsedSeconds: Int = 10,
    val collapsedElements: Set<String> = setOf(NP_ELEMENT_ART)
)

@Composable
fun NowPlayingPill(
    nowPlaying: NowPlaying,
    musicPlayer: MusicPlayer,
    onClick: () -> Unit = {},
    config: NowPlayingConfig = NowPlayingConfig(),
    modifier: Modifier = Modifier
) {
    val isActive = nowPlaying.song.id.isNotEmpty() &&
            (nowPlaying.isPlaying || musicPlayer.isActive())
    val scope = rememberCoroutineScope()

    // Expand -> collapse lifecycle (same shape as the photo overlay). Starts full.
    var autoExpanded by remember { mutableStateOf(true) }
    // Manual override: double-tapping the pill flips size; null = follow the auto cycle.
    var manualOverride by remember(nowPlaying.song.id) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(
        config.animationEnabled,
        config.loop,
        nowPlaying.song.id,
        config.expandedSeconds,
        config.collapsedSeconds
    ) {
        if (!config.animationEnabled) {
            autoExpanded = true
            return@LaunchedEffect
        }
        autoExpanded = true
        while (true) {
            delay(config.expandedSeconds * 1000L)
            autoExpanded = false
            if (!config.loop) return@LaunchedEffect
            delay(config.collapsedSeconds * 1000L)
            autoExpanded = true
        }
    }
    val expanded = manualOverride ?: autoExpanded
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(500),
        label = "npCollapse"
    )
    val showFull = expanded
    val scale = config.scale * (0.85f + 0.15f * progress)

    fun show(element: String, enabled: Boolean) = enabled && (showFull || element in config.collapsedElements)
    val showArt = show(NP_ELEMENT_ART, config.showArt)
    val showTitle = show(NP_ELEMENT_TITLE, config.showTitle)
    val showArtist = show(NP_ELEMENT_ARTIST, config.showArtist)
    val showControls = show(NP_ELEMENT_CONTROLS, config.showControls)

    AnimatedVisibility(
        visible = isActive,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .height((64 * scale).dp)
                .clip(RoundedCornerShape(config.cornerRadius.dp))
                .background(Color.Black.copy(alpha = config.backgroundOpacity))
                // Tap = expand/collapse the pill; long-press = open the music screen.
                // (Play/skip/close buttons are children and consume their own taps.)
                .pointerInput(nowPlaying.song.id) {
                    detectTapGestures(
                        onTap = { manualOverride = !(manualOverride ?: autoExpanded) },
                        onLongPress = { onClick() }
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left zone: art + text (tap = open music, long-press = stop music).
            if (showArt || showTitle || showArtist) {
                Row(
                    modifier = Modifier.padding(horizontal = (10 * scale).dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((10 * scale).dp)
                ) {
                    if (showArt) {
                        if (nowPlaying.coverArtUrl.isNotEmpty()) {
                            AsyncImage(
                                model = nowPlaying.coverArtUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size((46 * scale).dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            // Placeholder so the collapsed pill always has a tappable body.
                            Box(
                                modifier = Modifier
                                    .size((46 * scale).dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x22FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) { Text("\u266A", color = Color(0x99FFFFFF), fontSize = (20 * scale).sp) }
                        }
                    }
                    if (showTitle || showArtist) {
                        Column {
                            if (showTitle) {
                                Text(
                                    text = nowPlaying.song.title,
                                    color = Color.White,
                                    fontSize = (13 * scale).sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 220.dp)
                                )
                            }
                            if (showArtist) {
                                Text(
                                    text = nowPlaying.song.artist,
                                    color = Color(0xAAFFFFFF),
                                    fontSize = (11 * scale).sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 220.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (showControls) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .padding(vertical = 12.dp)
                        .background(dividerColor)
                )
                Box(
                    modifier = Modifier
                        .width((52 * scale).dp)
                        .fillMaxHeight()
                        .clickable { musicPlayer.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (nowPlaying.isPlaying) "||" else ">",
                        color = Color.White,
                        fontSize = (18 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .padding(vertical = 12.dp)
                        .background(dividerColor)
                )
                Box(
                    modifier = Modifier
                        .width((52 * scale).dp)
                        .fillMaxHeight()
                        .clickable { scope.launch { musicPlayer.skipNext() } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ">>",
                        color = Color.White,
                        fontSize = (18 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Close: stop playback and clear the queue.
            Box(
                modifier = Modifier
                    .width((30 * scale).dp)
                    .fillMaxHeight()
                    .clickable { musicPlayer.clearQueue() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u2715",
                    color = Color(0xAAFFFFFF),
                    fontSize = (14 * scale).sp
                )
            }
        }
    }
}
