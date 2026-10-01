package com.bogocat.framecache.ui.slideshow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import coil3.compose.AsyncImage
import com.bogocat.framecache.data.db.CachedAsset
import com.bogocat.framecache.data.settings.SettingsRepository
import com.bogocat.framecache.music.NowPlaying
import kotlinx.coroutines.delay
import java.io.File
import kotlin.random.Random

@Composable
fun SlideshowScreen(
    viewModel: SlideshowViewModel = hiltViewModel(),
    onOpenSettings: () -> Unit = {},
    onOpenMusic: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val nowPlaying by viewModel.musicPlayer.nowPlaying.collectAsState()
    val navidromeEnabled by viewModel.navidromeEnabled.collectAsState()
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
    val showProgressBar by viewModel.showProgressBar.collectAsState()
    val showRating by viewModel.showRating.collectAsState()
    val showPersonAge by viewModel.showPersonAge.collectAsState()
    val clockFormat by viewModel.clockFormat.collectAsState()
    val overlayTextSize by viewModel.overlayTextSize.collectAsState()
    val overlayTextColor by viewModel.overlayTextColor.collectAsState()
    val overlayBackground by viewModel.overlayBackground.collectAsState()
    val overlayBackgroundOpacity by viewModel.overlayBackgroundOpacity.collectAsState()
    val overlayClockPosition by viewModel.overlayClockPosition.collectAsState()
    val overlayInfoPosition by viewModel.overlayInfoPosition.collectAsState()
    val overlayCornerRadius by viewModel.overlayCornerRadius.collectAsState()
    val overlayAnimationMode by viewModel.overlayAnimation.collectAsState()
    val overlayMarquee by viewModel.overlayMarquee.collectAsState()
    val overlayExpandScale by viewModel.overlayExpandScale.collectAsState()
    val overlayCollapsedSeconds by viewModel.overlayCollapsedSeconds.collectAsState()
    val overlayExpandedSeconds by viewModel.overlayExpandedSeconds.collectAsState()
    val overlayExpandedIndefinite by viewModel.overlayExpandedIndefinite.collectAsState()
    val overlayCollapsedIndefinite by viewModel.overlayCollapsedIndefinite.collectAsState()
    val overlayCollapsedFields by viewModel.overlayCollapsedFields.collectAsState()
    val npShowArt by viewModel.npShowArt.collectAsState()
    val npShowTitle by viewModel.npShowTitle.collectAsState()
    val npShowArtist by viewModel.npShowArtist.collectAsState()
    val npShowControls by viewModel.npShowControls.collectAsState()
    val npScale by viewModel.npScale.collectAsState()
    val npBackgroundOpacity by viewModel.npBackgroundOpacity.collectAsState()
    val npCornerRadius by viewModel.npCornerRadius.collectAsState()
    val npAnimation by viewModel.npAnimation.collectAsState()
    val npExpandedSeconds by viewModel.npExpandedSeconds.collectAsState()
    val npCollapsedSeconds by viewModel.npCollapsedSeconds.collectAsState()
    val npCollapsedElements by viewModel.npCollapsedElements.collectAsState()
    val sleepEnabled by viewModel.sleepEnabled.collectAsState()
    val sleepStartHour by viewModel.sleepStartHour.collectAsState()
    val sleepEndHour by viewModel.sleepEndHour.collectAsState()
    val sleepDim by viewModel.sleepDim.collectAsState()

    // Reactive device orientation — re-composes on rotation.
    val isLandscapeDevice = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(isLandscapeDevice) { viewModel.setDeviceLandscape(isLandscapeDevice) }

    // Overlay presentation, assembled from the user's style options.
    val overlayStyle = OverlayStyle(
        scale = when (overlayTextSize) {
            "small" -> 0.85f
            "large" -> 1.3f
            else -> 1f
        },
        light = overlayTextColor != "dark",
        showBackground = overlayBackground,
        backgroundOpacity = overlayBackgroundOpacity / 100f,
        cornerRadius = overlayCornerRadius
    )
    val clockAlignment = remember(overlayClockPosition) { overlayAlignment(overlayClockPosition) }
    val infoAlignment = remember(overlayInfoPosition) { overlayAlignment(overlayInfoPosition) }
    val overlayAnimation = OverlayAnimation(
        mode = overlayAnimationMode,
        scale = overlayExpandScale / 100f,
        collapsedSeconds = overlayCollapsedSeconds,
        expandedSeconds = overlayExpandedSeconds,
        expandedIndefinite = overlayExpandedIndefinite,
        collapsedIndefinite = overlayCollapsedIndefinite,
        marquee = overlayMarquee,
        collapsedFields = overlayCollapsedFields
    )
    val nowPlayingConfig = NowPlayingConfig(
        showArt = npShowArt,
        showTitle = npShowTitle,
        showArtist = npShowArtist,
        showControls = npShowControls,
        scale = npScale / 100f,
        backgroundOpacity = npBackgroundOpacity / 100f,
        cornerRadius = npCornerRadius,
        animationEnabled = npAnimation != SettingsRepository.OVERLAY_ANIM_STATIC,
        loop = npAnimation == SettingsRepository.OVERLAY_ANIM_LOOP,
        expandedSeconds = npExpandedSeconds,
        collapsedSeconds = npCollapsedSeconds,
        collapsedElements = npCollapsedElements
    )

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
        // Touch zones sit on the BOTTOM layer so the info pill's / music pill's own
        // clickables win; empty areas fall through to here. Advance / previous are
        // only the outer ~10% edges — the middle is reserved for UI.
        var dragTotalY by remember { mutableStateOf(0f) }
        var boxWidth by remember { mutableStateOf(1f) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { boxWidth = it.width.toFloat() }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragTotalY = 0f },
                        onDragEnd = {
                            if (dragTotalY > 100f) onOpenSettings()
                            dragTotalY = 0f
                        },
                        onVerticalDrag = { _, dragAmount -> dragTotalY += dragAmount }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            when {
                                offset.x < boxWidth * 0.10f -> viewModel.previousImage()
                                offset.x > boxWidth * 0.90f -> viewModel.nextImage()
                            }
                        },
                        onLongPress = { onOpenSettings() }
                    )
                }
        )

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
            val secondAsset = state.secondAsset

            AnimatedContent(
                targetState = Pair(asset, secondAsset),
                transitionSpec = {
                    fadeIn(animationSpec = tween(crossfadeDuration)) togetherWith
                        fadeOut(animationSpec = tween(crossfadeDuration))
                },
                label = "slideshow",
                contentKey = { it.first.id + (it.second?.id ?: "") }
            ) { (displayAsset, displaySecond) ->
                // Pair makes sense only when the photo orientation is opposite the device orientation.
                // Guards rotation-mid-display: a stale pair is dropped to solo until next advance.
                val photoIsPortrait = displayAsset.width != null && displayAsset.height != null &&
                    displayAsset.height > displayAsset.width
                val showPair = displaySecond != null && (photoIsPortrait == isLandscapeDevice)

                @Composable
                fun cell(a: CachedAsset, blur: Boolean, scale: String) {
                    PhotoDisplay(
                        asset = a,
                        durationMs = 45_000,
                        kenBurnsEnabled = kenBurnsEnabled,
                        kenBurnsZoom = kenBurnsZoom,
                        backgroundBlur = blur,
                        imageScale = scale
                    )
                    PhotoInfoPill(
                        asset = a,
                        alignment = infoAlignment,
                        showPhotoDate = showPhotoDate,
                        showLocation = showLocation,
                        showDescription = showDescription,
                        showPeople = showPeople,
                        showCamera = showCamera,
                        showRating = showRating,
                        showPersonAge = showPersonAge,
                        dateFormat = dateFormat,
                        style = overlayStyle,
                        animation = overlayAnimation
                    )
                }

                when {
                    showPair && displaySecond != null && isLandscapeDevice -> {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { cell(displayAsset, blur = false, scale = "fit") }
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { cell(displaySecond, blur = false, scale = "fit") }
                        }
                    }
                    showPair && displaySecond != null -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { cell(displayAsset, blur = false, scale = "fit") }
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { cell(displaySecond, blur = false, scale = "fit") }
                        }
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize()) { cell(displayAsset, blur = backgroundBlur, scale = imageScale) }
                    }
                }
            }

            // Global overlay (clock + current date only)
            MetadataOverlay(
                showClock = showClock,
                showDate = showDate,
                clockFormat = clockFormat,
                dateFormat = dateFormat,
                alignment = clockAlignment,
                style = overlayStyle
            )

            // Progress bar
            if (showProgressBar) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(2.dp),
                    color = Color.White.copy(alpha = 0.5f),
                    trackColor = Color.Transparent
                )
            }

            // Pause indicator
            if (state.isPaused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xAA000000))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text("PAUSED", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

        }

        // Sleep overlay
        if (isSleeping) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (sleepDim) Color(0xDD000000) else Color.Black)
            )
        }

        // Now playing pill — ABOVE touch zone so controls receive taps
        val musicActive = nowPlaying.song.id.isNotEmpty() &&
                (nowPlaying.isPlaying || viewModel.musicPlayer.isActive())

        if (musicActive) {
            NowPlayingPill(
                nowPlaying = nowPlaying,
                musicPlayer = viewModel.musicPlayer,
                onClick = onOpenMusic,
                config = nowPlayingConfig,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
            )
        } else if (navidromeEnabled) {
            // Music launcher button when not playing
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0x14000000))
                    .clickable(onClick = onOpenMusic),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u266B",  // beamed music note
                    color = Color(0xE6FFFFFF),
                    fontSize = 22.sp
                )
            }
        }
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
    // Support both file paths (Immich downloads) and content URIs (local folders)
    val imageModel: Any = if (filePath.startsWith("content://")) {
        android.net.Uri.parse(filePath)
    } else {
        File(filePath)
    }
    val contentScale = if (imageScale == "fill") ContentScale.Crop else ContentScale.Fit

    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred background
        if (backgroundBlur) {
            AsyncImage(
                model = imageModel,
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
                model = imageModel,
                durationMs = durationMs,
                zoomAmount = kenBurnsZoom / 100f,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
