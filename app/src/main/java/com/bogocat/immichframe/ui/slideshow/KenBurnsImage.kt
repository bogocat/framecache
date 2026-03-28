package com.bogocat.immichframe.ui.slideshow

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import kotlin.random.Random

private data class KenBurnsTarget(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
)

private fun randomTarget(maxZoom: Float = 1.2f) = KenBurnsTarget(
    scale = 1.0f + Random.nextFloat() * (maxZoom - 1.0f),
    offsetX = (Random.nextFloat() - 0.5f) * 2f,
    offsetY = (Random.nextFloat() - 0.5f) * 2f
)

@Composable
fun KenBurnsImage(
    model: Any?,
    modifier: Modifier = Modifier,
    durationMs: Int = 45_000,
    zoomAmount: Float = 1.2f,
    contentDescription: String? = null
) {
    var target by remember { mutableStateOf(randomTarget(zoomAmount)) }

    val scale by animateFloatAsState(
        targetValue = target.scale,
        animationSpec = tween(durationMs, easing = LinearEasing),
        finishedListener = { target = randomTarget(zoomAmount) },
        label = "kb-scale"
    )
    val offsetX by animateFloatAsState(
        targetValue = target.offsetX,
        animationSpec = tween(durationMs, easing = LinearEasing),
        label = "kb-offsetX"
    )
    val offsetY by animateFloatAsState(
        targetValue = target.offsetY,
        animationSpec = tween(durationMs, easing = LinearEasing),
        label = "kb-offsetY"
    )

    // Restart animation on new image
    LaunchedEffect(model) {
        target = randomTarget(zoomAmount)
    }

    Box(modifier = modifier.clip(RectangleShape)) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    val maxOffsetX = (scale - 1f) * size.width / 2f
                    val maxOffsetY = (scale - 1f) * size.height / 2f
                    translationX = offsetX * maxOffsetX
                    translationY = offsetY * maxOffsetY
                }
        )
    }
}
