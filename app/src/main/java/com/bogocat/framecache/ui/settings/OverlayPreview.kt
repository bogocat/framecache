package com.bogocat.framecache.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bogocat.framecache.ui.slideshow.ClockStyle
import com.bogocat.framecache.ui.slideshow.OverlayAnimation
import com.bogocat.framecache.ui.slideshow.OverlayStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A miniature of the frame showing where the overlay lands and how big it is.
 *
 * The size sliders are meaningless on their own ("170% of what?") and settings is
 * a full-screen screen, so without this you have to save, exit, look and come back.
 * Everything here is scaled by [scaleFactor] — the pill's real padding, corner
 * radius, font sizes and position — so the proportions match the device exactly;
 * the text is only as readable as it would be from across the room.
 */
private const val PREVIEW_HEIGHT_DP = 190f

private val previewBackdrop = Brush.linearGradient(
    listOf(Color(0xFF3A4A57), Color(0xFF141B22))
)

@Composable
fun ClockPreview(
    deviceWidthDp: Int,
    deviceHeightDp: Int,
    alignment: Alignment,
    style: OverlayStyle,
    clock: ClockStyle,
    clockFormat: String,
    dateFormat: String,
    showDate: Boolean,
    modifier: Modifier = Modifier
) {
    PreviewFrame(deviceWidthDp, deviceHeightDp, alignment, style, modifier) { k ->
        val family = clock.fontFamily
        Column {
            Text(
                text = sampleTime(clockFormat),
                color = style.primary,
                fontSize = (clock.timeSize().value * k).sp,
                fontFamily = family,
                letterSpacing = (clock.letterSpacing * k).sp,
                maxLines = 1,
                softWrap = false,
                style = LocalTextStyle.current.copy(shadow = style.shadow)
            )
            if (showDate) {
                Text(
                    text = sampleDate(dateFormat),
                    color = style.secondary,
                    fontSize = (clock.dateSize().value * k).sp,
                    fontFamily = family,
                    maxLines = 1,
                    softWrap = false,
                    style = LocalTextStyle.current.copy(shadow = style.shadow)
                )
            }
        }
    }
}

/** One sample photo-info line: the real base size it will be drawn at. */
data class PreviewLine(val text: String, val baseSp: Int, val color: (OverlayStyle) -> Color)

@Composable
fun PhotoInfoPreview(
    deviceWidthDp: Int,
    deviceHeightDp: Int,
    alignment: Alignment,
    style: OverlayStyle,
    scale: Float,
    animation: OverlayAnimation,
    lines: List<PreviewLine>,
    modifier: Modifier = Modifier
) {
    // Preview the expanded state when the overlay animates: that is the state the
    // expanded/collapsed size sliders are tuning, and the one that can overflow.
    val effScale = scale * if (animation.enabled) animation.expandedScale else 1f
    PreviewFrame(deviceWidthDp, deviceHeightDp, alignment, style, modifier) { k ->
        Column {
            lines.forEachIndexed { index, line ->
                Text(
                    text = line.text,
                    color = line.color(style),
                    fontSize = (line.baseSp * effScale * k).sp,
                    maxLines = 1,
                    softWrap = false,
                    style = LocalTextStyle.current.copy(shadow = style.shadow)
                )
                if (index < lines.lastIndex) Spacer(modifier = Modifier.height((6 * k).dp))
            }
        }
    }
}

/** Caption line under/next to a preview: what the current numbers work out to. */
@Composable
fun PreviewCaption(title: String, detail: String, note: String) {
    Column {
        Text(title, color = Color(0xFF66D9EF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = Color.White, fontSize = 13.sp)
        Text(note, color = Color(0xFFAAAAAA), fontSize = 11.sp)
    }
}

@Composable
private fun PreviewFrame(
    deviceWidthDp: Int,
    deviceHeightDp: Int,
    alignment: Alignment,
    style: OverlayStyle,
    modifier: Modifier = Modifier,
    content: @Composable (k: Float) -> Unit
) {
    val k = PREVIEW_HEIGHT_DP / deviceHeightDp.coerceAtLeast(1)
    Box(
        modifier = modifier
            .height(PREVIEW_HEIGHT_DP.dp)
            .width((deviceWidthDp * k).dp)
            .clip(RoundedCornerShape(6.dp))
            .background(previewBackdrop)
    ) {
        Box(
            modifier = Modifier
                .align(alignment)
                .padding((20 * k).dp)
                .clip(RoundedCornerShape((style.cornerRadius * k).dp))
                .then(if (style.showBackground) Modifier.background(style.scrim) else Modifier)
                .padding(horizontal = (14 * k).dp, vertical = (8 * k).dp)
        ) { content(k) }
    }
}

private fun sampleTime(clockFormat: String): String =
    SimpleDateFormat(if (clockFormat == "24") "HH:mm" else "h:mm a", Locale.getDefault())
        .format(Date())

private fun sampleDate(dateFormat: String): String = try {
    SimpleDateFormat(dateFormat, Locale.getDefault()).format(Date())
} catch (_: Exception) {
    SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date())
}

/** Sample content for the photo-info preview, based on which fields are enabled. */
fun previewLines(
    photoDate: Boolean,
    location: Boolean,
    description: Boolean,
    people: Boolean,
    personAge: Boolean,
    camera: Boolean,
    rating: Boolean,
    dateFormat: String
): List<PreviewLine> = buildList {
    if (photoDate) add(PreviewLine(sampleDate(dateFormat), 15) { it.primary })
    if (location) add(PreviewLine("Asheville, North Carolina", 13) { it.secondary })
    if (camera) add(PreviewLine("Pixel 7 Pro", 11) { it.tertiary })
    if (rating) add(PreviewLine("\u2605\u2605\u2605\u2605\u2606", 13) { it.rating })
    if (people) add(PreviewLine(if (personAge) "Alice (34), Bob (36)" else "Alice, Bob", 13) { it.secondary })
    if (description) add(PreviewLine("Blue Ridge Parkway", 14) { it.primary })
}

/** "44 sp" for the clock, whose base is a single 22sp size. */
fun clockSizeLabel(scale: Int): String = "${(22f * scale / 100f).roundToInt()} sp"

/** "×1.6" for the photo info, which scales six different line sizes. */
fun infoScaleLabel(scale: Int): String = "\u00D7%.1f".format(scale / 100f)
