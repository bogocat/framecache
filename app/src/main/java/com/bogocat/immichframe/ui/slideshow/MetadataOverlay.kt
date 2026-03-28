package com.bogocat.immichframe.ui.slideshow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bogocat.immichframe.data.db.CachedAsset
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        // Top left: description
        if (showDescription && asset?.description != null) {
            Text(
                text = asset.description,
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
                    .background(Color(0x66000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Top right: people
        if (showPeople && asset?.peopleName != null) {
            Text(
                text = asset.peopleName,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(Color(0x66000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Bottom bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0x44000000))
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // Left: photo date, location, camera
            Column {
                if (showPhotoDate && asset?.dateTaken != null) {
                    val formatted = try {
                        SimpleDateFormat(dateFormat, Locale.getDefault())
                            .format(Date(asset.dateTaken))
                    } catch (_: Exception) {
                        SimpleDateFormat("MMM dd, yyyy", Locale.US)
                            .format(Date(asset.dateTaken))
                    }
                    Text(
                        text = formatted,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
                if (showLocation && asset?.location != null) {
                    Text(
                        text = asset.location,
                        color = Color(0xCCFFFFFF),
                        fontSize = 14.sp
                    )
                }
                if (showCamera && asset?.cameraModel != null) {
                    Text(
                        text = asset.cameraModel,
                        color = Color(0x99FFFFFF),
                        fontSize = 12.sp
                    )
                }
            }

            // Right: clock and date
            Column(horizontalAlignment = Alignment.End) {
                if (showClock) {
                    Text(
                        text = SimpleDateFormat("h:mm a", Locale.getDefault())
                            .format(Date(currentTime)),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.End
                    )
                }
                if (showDate) {
                    Text(
                        text = try {
                            SimpleDateFormat(dateFormat, Locale.getDefault())
                                .format(Date(currentTime))
                        } catch (_: Exception) {
                            SimpleDateFormat("MMM dd, yyyy", Locale.US)
                                .format(Date(currentTime))
                        },
                        color = Color(0xCCFFFFFF),
                        fontSize = 14.sp,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}
