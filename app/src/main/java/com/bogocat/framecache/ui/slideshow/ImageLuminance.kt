package com.bogocat.framecache.ui.slideshow

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.Alignment
import com.bogocat.framecache.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Brightness of the photo behind each corner of the screen, 0 = black, 1 = white.
 *
 * The overlays sit in corners, and the two corners of one photo can be wildly
 * different (a bright sky top-left, a dark treeline bottom-right), so the reading is
 * per corner rather than one number for the whole image.
 */
data class CornerLuminance(
    val topStart: CornerSample,
    val topEnd: CornerSample,
    val bottomStart: CornerSample,
    val bottomEnd: CornerSample
) {
    fun forAlignment(alignment: Alignment): CornerSample = when (alignment) {
        Alignment.TopStart -> topStart
        Alignment.TopEnd -> topEnd
        Alignment.BottomEnd -> bottomEnd
        else -> bottomStart
    }
}

/**
 * Two readings of one corner band.
 *
 * [typical] is the median — what most of the area behind the pill looks like, so a
 * small bright cloud or a specular highlight does not decide the text colour.
 * [bright] is the 80th percentile, used as a guard: switching to dark text needs the
 * corner to be bright *and* broadly so, because dark text dies on any dark patch
 * behind it while white text (with its shadow) survives most mid-tones.
 */
data class CornerSample(val typical: Float, val bright: Float)

/**
 * Downscale the cached photo and measure the brightness of the four corner bands.
 *
 * Reads the same file Coil draws (a cached JPEG, or a content:// URI for local
 * folders) at roughly 96px wide, so the cost is a small decode rather than a second
 * full-size load. The band under each pill is scored at its 80th percentile: text has
 * to survive the brightest part of what is behind it, not the average, otherwise a
 * white cloud in an otherwise dark corner still swallows white text.
 *
 * Returns null when the image cannot be read, which callers treat as "assume dark"
 * (white text), matching the default look.
 */
suspend fun sampleCornerLuminance(context: Context, filePath: String?): CornerLuminance? =
    withContext(Dispatchers.IO) {
        if (filePath.isNullOrBlank()) return@withContext null
        val bitmap = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream(context, filePath)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            val widest = max(bounds.outWidth, bounds.outHeight)
            if (widest <= 0) return@withContext null
            // Downscale to ~96px on the long edge; inSampleSize must be a power of two.
            var sample = 1
            while (widest / (sample * 2) >= 96) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            openStream(context, filePath)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (_: Exception) {
            null
        } ?: return@withContext null

        try {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

            // Corner bands: 45% of the width, 30% of the height, flush to each corner.
            val bandW = max(1, (w * 0.45f).toInt())
            val bandH = max(1, (h * 0.30f).toInt())

            fun brightness(x0: Int, y0: Int): CornerSample {
                val values = ArrayList<Float>(bandW * bandH)
                for (y in y0 until minOf(y0 + bandH, h)) {
                    for (x in x0 until minOf(x0 + bandW, w)) {
                        val p = pixels[y * w + x]
                        val r = (p shr 16 and 0xFF) / 255f
                        val g = (p shr 8 and 0xFF) / 255f
                        val b = (p and 0xFF) / 255f
                        values.add(0.2126f * r + 0.7152f * g + 0.0722f * b)
                    }
                }
                if (values.isEmpty()) return CornerSample(0.5f, 0.5f)
                values.sort()
                return CornerSample(
                    typical = values[(values.size - 1) * 50 / 100],
                    bright = values[(values.size - 1) * 80 / 100]
                )
            }

            CornerLuminance(
                topStart = brightness(0, 0),
                topEnd = brightness(w - bandW, 0),
                bottomStart = brightness(0, h - bandH),
                bottomEnd = brightness(w - bandW, h - bandH)
            )
        } finally {
            bitmap.recycle()
        }
    }

private fun openStream(context: Context, filePath: String) = try {
    if (filePath.startsWith("content://")) {
        context.contentResolver.openInputStream(Uri.parse(filePath))
    } else {
        File(filePath).inputStream()
    }
} catch (_: Exception) {
    null
}
