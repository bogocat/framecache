package com.bogocat.immichframe.ui.slideshow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bogocat.immichframe.data.db.CachedAsset
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val pillBackground = Color(0x88000000)
private val pillShape = RoundedCornerShape(16.dp)

@Composable
private fun Pill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(pillShape)
            .background(pillBackground)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) { content() }
}

@Composable
fun MetadataOverlay(
    asset: CachedAsset?,
    showClock: Boolean,
    showDate: Boolean,
    showPhotoDate: Boolean,
    showLocation: Boolean,
    showDescription: Boolean,
    showPeople: Boolean,
    showCamera: Boolean,
    dateFormat: String,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {

        // Top-left: clock and current date
        if (showClock || showDate) {
            Pill(modifier = Modifier.align(Alignment.TopStart).padding(20.dp)) {
                Column {
                    if (showClock) {
                        Text(
                            text = SimpleDateFormat("h:mm a", Locale.getDefault())
                                .format(Date(currentTime)),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Light
                        )
                    }
                    if (showDate) {
                        Text(
                            text = formatDate(dateFormat, currentTime),
                            color = Color(0xCCFFFFFF),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Top-right: people
        if (showPeople && asset?.peopleName != null) {
            Pill(modifier = Modifier.align(Alignment.TopEnd).padding(20.dp)) {
                Text(text = asset.peopleName, color = Color.White, fontSize = 14.sp)
            }
        }

        // Bottom-left: photo info (date, location, camera)
        val hasPhotoInfo = (showPhotoDate && asset?.dateTaken != null) ||
            (showLocation && asset?.location != null) ||
            (showCamera && asset?.cameraModel != null)

        if (hasPhotoInfo) {
            Pill(modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Column {
                    if (showPhotoDate && asset?.dateTaken != null) {
                        Text(
                            text = formatDate(dateFormat, asset.dateTaken),
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    }
                    if (showLocation && asset?.location != null) {
                        Text(
                            text = asset.location,
                            color = Color(0xCCFFFFFF),
                            fontSize = 13.sp
                        )
                    }
                    if (showCamera && asset?.cameraModel != null) {
                        Text(
                            text = asset.cameraModel,
                            color = Color(0x99FFFFFF),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Bottom-right: description
        if (showDescription && asset?.description != null) {
            Pill(modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                Text(
                    text = asset.description,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
}

private fun formatDate(format: String, millis: Long): String {
    return try {
        SimpleDateFormat(format, Locale.getDefault()).format(Date(millis))
    } catch (_: Exception) {
        SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(millis))
    }
}
