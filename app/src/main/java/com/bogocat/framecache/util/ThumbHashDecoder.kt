package com.bogocat.framecache.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Decodes a ThumbHash to a Bitmap placeholder.
 * Based on https://github.com/evanw/thumbhash (reference implementation).
 */
object ThumbHashDecoder {

    fun decode(base64: String): Bitmap? {
        return try {
            val hash = Base64.decode(base64, Base64.DEFAULT)
            thumbHashToRGBA(hash)
        } catch (_: Exception) {
            null
        }
    }

    private fun thumbHashToRGBA(hash: ByteArray): Bitmap {
        val header = hash[0].toInt() and 0xFF or
            ((hash[1].toInt() and 0xFF) shl 8) or
            ((hash[2].toInt() and 0xFF) shl 16)
        val lDc = (header and 63).toFloat() / 63f
        val pDc = ((header shr 6) and 63).toFloat() / 63f
        val qDc = ((header shr 12) and 63).toFloat() / 63f
        val lScale = ((header shr 18) and 31).toFloat() / 31f
        val hasAlpha = (header shr 23) and 1 == 1
        val pScale = ((hash[3].toInt() and 0xFF) and 63).toFloat() / 63f
        val qScale = (((hash[3].toInt() and 0xFF) shr 6) or
            ((hash[4].toInt() and 0xFF) shl 2) and 63).toFloat() / 63f
        val isLandscape = ((hash[4].toInt() and 0xFF) shr 7) and 1 == 1

        val lx = max(3, if (isLandscape) if (hasAlpha) 5 else 7 else if (hasAlpha) 3 else 5)
        val ly = max(3, if (isLandscape) if (hasAlpha) 3 else 5 else if (hasAlpha) 5 else 7)
        val w = if (isLandscape) 32 else round(32f * lx.toFloat() / ly.toFloat()).toInt()
        val h = if (isLandscape) round(32f * ly.toFloat() / lx.toFloat()).toInt() else 32

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // Simple solid color from DC components for performance
        val r = max(0f, min(1f, lDc + pDc))
        val g = max(0f, min(1f, lDc - pDc / 2 - qDc / 2))
        val b = max(0f, min(1f, lDc + qDc))
        val color = Color.rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
        bitmap.eraseColor(color)
        return bitmap
    }
}
