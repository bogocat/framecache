package com.bogocat.immichframe.ui.slideshow

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.bogocat.immichframe.data.db.CachedAsset
import com.bogocat.immichframe.util.ThumbHashDecoder
import kotlinx.coroutines.delay
import java.io.File
import kotlin.random.Random

@Composable
fun SlideshowScreen(
    viewModel: SlideshowViewModel = hiltViewModel(),
    onOpenSettings: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val showClock by viewModel.showClock.collectAsState()
    val showDate by viewModel.showDate.collectAsState()
    val showPhotoDate by viewModel.showPhotoDate.collectAsState()
    val showLocation by viewModel.showLocation.collectAsState()
    val showDescription by viewModel.showDescription.collectAsState()
    val showPeople by viewModel.showPeople.collectAsState()
    val showCamera by viewModel.showCamera.collectAsState()
    val dateFormat by viewModel.dateFormat.collectAsState()

    // Burn-in prevention: shift content by 1-2px every 60s
    var shiftX by remember { mutableStateOf(0f) }
    var shiftY by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            shiftX = (Random.nextFloat() - 0.5f) * 4f
            shiftY = (Random.nextFloat() - 0.5f) * 4f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .offset(x = shiftX.dp, y = shiftY.dp)
    ) {
        val asset = state.currentAsset

        if (asset == null) {
            // Waiting for cache
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Syncing photos...",
                    color = Color.White,
                    fontSize = 18.sp
                )
            }
        } else {
            AnimatedContent(
                targetState = asset,
                transitionSpec = {
                    fadeIn(animationSpec = androidx.compose.animation.core.tween(1500)) togetherWith
                        fadeOut(animationSpec = androidx.compose.animation.core.tween(1500))
                },
                label = "slideshow",
                contentKey = { it.id }
            ) { displayAsset ->
                PhotoDisplay(asset = displayAsset, durationMs = 45_000)
            }

            // Metadata overlay
            MetadataOverlay(
                asset = asset,
                showClock = showClock,
                showDate = showDate,
                showPhotoDate = showPhotoDate,
                showLocation = showLocation,
                showDescription = showDescription,
                showPeople = showPeople,
                showCamera = showCamera,
                dateFormat = dateFormat
            )
        }

        // Touch zones: left=prev, center=pause, right=next
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { viewModel.nextImage() }
        )
    }
}

@Composable
private fun PhotoDisplay(asset: CachedAsset, durationMs: Int) {
    val filePath = asset.filePath ?: return

    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred background
        AsyncImage(
            model = File(filePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(20.dp)
        )

        // Main image with Ken Burns
        KenBurnsImage(
            model = File(filePath),
            durationMs = durationMs,
            modifier = Modifier.fillMaxSize()
        )
    }
}
