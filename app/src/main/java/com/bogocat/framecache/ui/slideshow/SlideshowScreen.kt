package com.bogocat.framecache.ui.slideshow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.input.pointer.pointerInput
import coil3.compose.AsyncImage
import com.bogocat.framecache.data.db.CachedAsset
import kotlinx.coroutines.delay
import java.io.File
import kotlin.random.Random

@OptIn(ExperimentalFoundationApi::class)
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
    val crossfadeDuration by viewModel.crossfadeDuration.collectAsState()
    val kenBurnsEnabled by viewModel.kenBurnsEnabled.collectAsState()
    val kenBurnsZoom by viewModel.kenBurnsZoom.collectAsState()
    val backgroundBlur by viewModel.backgroundBlur.collectAsState()
    val imageScale by viewModel.imageScale.collectAsState()
    val sleepEnabled by viewModel.sleepEnabled.collectAsState()
    val sleepStartHour by viewModel.sleepStartHour.collectAsState()
    val sleepEndHour by viewModel.sleepEndHour.collectAsState()
    val sleepDim by viewModel.sleepDim.collectAsState()

    // Check if in sleep hours
    var isSleeping by remember { mutableStateOf(false) }
    LaunchedEffect(sleepEnabled, sleepStartHour, sleepEndHour) {
        while (true) {
            if (sleepEnabled) {
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                isSleeping = if (sleepStartHour > sleepEndHour) {
                    hour >= sleepStartHour || hour < sleepEndHour
                } else {
                    hour in sleepStartHour until sleepEndHour
                }
            } else {
                isSleeping = false
            }
            delay(60_000)
        }
    }

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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (state.cachedCount == 0) "Syncing photos..." else "Loading...",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "${state.cachedCount} photos cached",
                        color = Color(0x99FFFFFF),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = "Long-press to open settings",
                        color = Color(0x66FFFFFF),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        } else {
            AnimatedContent(
                targetState = asset,
                transitionSpec = {
                    fadeIn(animationSpec = tween(crossfadeDuration)) togetherWith
                        fadeOut(animationSpec = tween(crossfadeDuration))
                },
                label = "slideshow",
                contentKey = { it.id }
            ) { displayAsset ->
                PhotoDisplay(
                    asset = displayAsset,
                    durationMs = 45_000,
                    kenBurnsEnabled = kenBurnsEnabled,
                    kenBurnsZoom = kenBurnsZoom,
                    backgroundBlur = backgroundBlur,
                    imageScale = imageScale
                )
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

        // Sleep overlay
        if (isSleeping) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (sleepDim) Color(0xDD000000) else Color.Black)
            )
        }

        // Touch: tap=next, long-press=settings, swipe-down=settings
        var dragTotal by remember { mutableStateOf(0f) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onDragEnd = {
                            if (dragTotal > 100f) onOpenSettings()
                            dragTotal = 0f
                        },
                        onVerticalDrag = { _, dragAmount -> dragTotal += dragAmount }
                    )
                }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { viewModel.nextImage() },
                    onLongClick = { onOpenSettings() }
                )
        )
    }
}

@Composable
private fun PhotoDisplay(
    asset: CachedAsset,
    durationMs: Int,
    kenBurnsEnabled: Boolean,
    kenBurnsZoom: Int,
    backgroundBlur: Boolean,
    imageScale: String
) {
    val filePath = asset.filePath ?: return
    val contentScale = if (imageScale == "fill") ContentScale.Crop else ContentScale.Fit

    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred background
        if (backgroundBlur) {
            AsyncImage(
                model = File(filePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp)
            )
        }

        // Main image
        if (kenBurnsEnabled) {
            KenBurnsImage(
                model = File(filePath),
                durationMs = durationMs,
                zoomAmount = kenBurnsZoom / 100f,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = File(filePath),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
