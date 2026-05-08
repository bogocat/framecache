package com.bogocat.framecache.ui.slideshow

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

private val pillBg = Color(0xCC000000)
private val pillShape = RoundedCornerShape(16.dp)
private val dividerColor = Color(0x33FFFFFF)

@Composable
fun NowPlayingPill(
    nowPlaying: NowPlaying,
    musicPlayer: MusicPlayer,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isActive = nowPlaying.song.id.isNotEmpty() &&
            (nowPlaying.isPlaying || musicPlayer.isActive())
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = isActive,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .height(68.dp)
                .clip(pillShape)
                .background(pillBg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left zone: album art + text
            // Tap = open music mode, Long-press = stop music
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onClick() },
                            onLongPress = { musicPlayer.clearQueue() }
                        )
                    }
                    .padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (nowPlaying.coverArtUrl.isNotEmpty()) {
                    AsyncImage(
                        model = nowPlaying.coverArtUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }

                Column {
                    Text(
                        text = nowPlaying.song.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = nowPlaying.song.artist,
                        color = Color(0xAAFFFFFF),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .padding(vertical = 12.dp)
                    .background(dividerColor)
            )

            // Play/pause
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .clickable { musicPlayer.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (nowPlaying.isPlaying) "||" else ">",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .padding(vertical = 12.dp)
                    .background(dividerColor)
            )

            // Skip next
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .clickable { scope.launch { musicPlayer.skipNext() } },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ">>",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
