package com.bogocat.framecache.ui.slideshow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bogocat.framecache.data.db.CachedAsset
import com.bogocat.framecache.data.settings.SettingsRepository
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Presentation options for the clock and photo-info overlays. Centralises what the
 * user previously could not change: text size/colour, whether the pill background
 * is drawn and how opaque it is, corner rounding, and drop shadow.
 */
data class OverlayStyle(
    val scale: Float = 1f,
    val light: Boolean = true,
    val showBackground: Boolean = true,
    val backgroundOpacity: Float = 0.53f,
    val cornerRadius: Int = 16
) {
    val primary: Color get() = if (light) Color.White else Color(0xFF101010)
    val secondary: Color get() = if (light) Color(0xCCFFFFFF) else Color(0xCC101010)
    val tertiary: Color get() = if (light) Color(0x99FFFFFF) else Color(0x99101010)
    val rating: Color get() = if (light) Color(0xFFFFD700) else Color(0xFFB8860B)
    val scrim: Color
        get() = (if (light) Color.Black else Color.White).copy(alpha = backgroundOpacity)
    val shadow: Shadow
        get() = if (light) Shadow(Color(0xAA000000), Offset(0f, 1f), 3f)
                else Shadow(Color(0x66FFFFFF), Offset(0f, 1f), 3f)

    fun size(baseSp: Int): TextUnit = (baseSp * scale).sp
}

/**
 * Animated info-overlay behaviour: periodically swells from a minimal "collapsed"
 * set of fields (larger font + spacing) to the full set, optionally marqueeing
 * lines that are too long for the pill.
 */
data class OverlayAnimation(
    val mode: String = SettingsRepository.OVERLAY_ANIM_STATIC,
    val expandedScale: Float = 1.3f,
    val collapsedScale: Float = 1.0f,
    val collapsedSeconds: Int = 6,
    val expandedSeconds: Int = 6,
    val expandedIndefinite: Boolean = false,
    val collapsedIndefinite: Boolean = false,
    val marquee: Boolean = false,
    val collapsedFields: Set<String> = setOf(SettingsRepository.OVERLAY_FIELD_DATE)
) {
    val enabled: Boolean get() = mode != SettingsRepository.OVERLAY_ANIM_STATIC
}

private data class OverlayLine(val id: String, val text: String, val color: Color, val baseSp: Int)

fun overlayAlignment(value: String): Alignment = when (value) {
    SettingsRepository.OVERLAY_POS_TOP_START -> Alignment.TopStart
    SettingsRepository.OVERLAY_POS_TOP_END -> Alignment.TopEnd
    SettingsRepository.OVERLAY_POS_BOTTOM_END -> Alignment.BottomEnd
    else -> Alignment.BottomStart
}

@Composable
private fun Pill(style: OverlayStyle, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(style.cornerRadius.dp))
            .background(if (style.showBackground) style.scrim else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) { content() }
}

@Composable
fun MetadataOverlay(
    showClock: Boolean,
    showDate: Boolean,
    clockFormat: String = "12",
    dateFormat: String,
    alignment: Alignment = Alignment.TopStart,
    style: OverlayStyle = OverlayStyle(),
    modifier: Modifier = Modifier
) {
    if (!showClock && !showDate) return

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Pill(style = style, modifier = Modifier.align(alignment).padding(20.dp)) {
            Column {
                if (showClock) {
                    val clockFmt = if (clockFormat == "24") "HH:mm" else "h:mm a"
                    Text(
                        text = SimpleDateFormat(clockFmt, Locale.getDefault())
                            .format(Date(currentTime)),
                        color = style.primary,
                        fontSize = style.size(22),
                        fontWeight = FontWeight.Light,
                        style = LocalTextStyle.current.copy(shadow = style.shadow)
                    )
                }
                if (showDate) {
                    Text(
                        text = formatDate(dateFormat, currentTime),
                        color = style.secondary,
                        fontSize = style.size(13),
                        style = LocalTextStyle.current.copy(shadow = style.shadow)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoxScope.PhotoInfoPill(
    asset: CachedAsset?,
    alignment: Alignment,
    showPhotoDate: Boolean,
    showLocation: Boolean,
    showDescription: Boolean,
    showPeople: Boolean,
    showCamera: Boolean,
    showRating: Boolean,
    showPersonAge: Boolean,
    dateFormat: String,
    style: OverlayStyle = OverlayStyle(),
    animation: OverlayAnimation = OverlayAnimation()
) {
    // Assemble the available lines for this asset, in display order.
    val lines = buildList {
        if (showPhotoDate && asset?.dateTaken != null) {
            add(OverlayLine(SettingsRepository.OVERLAY_FIELD_DATE, formatDate(dateFormat, asset.dateTaken), style.primary, 15))
        }
        if (showLocation && asset?.location != null) {
            add(OverlayLine(SettingsRepository.OVERLAY_FIELD_LOCATION, asset.location, style.secondary, 13))
        }
        if (showCamera && asset?.cameraModel != null) {
            add(OverlayLine(SettingsRepository.OVERLAY_FIELD_CAMERA, asset.cameraModel, style.tertiary, 11))
        }
        if (showRating && asset?.rating != null && asset.rating > 0) {
            add(OverlayLine(SettingsRepository.OVERLAY_FIELD_RATING, "\u2605".repeat(asset.rating) + "\u2606".repeat(5 - asset.rating), style.rating, 13))
        }
        if (showPeople && asset?.peopleName != null) {
            val peopleText = if (showPersonAge && asset.peopleBirthDates != null && asset.dateTaken != null) {
                formatPeopleWithAge(asset.peopleName, asset.peopleBirthDates, asset.dateTaken)
            } else {
                asset.peopleName
            }
            add(OverlayLine(SettingsRepository.OVERLAY_FIELD_PEOPLE, peopleText, style.secondary, 13))
        }
        if (showDescription && asset?.description != null) {
            add(OverlayLine(SettingsRepository.OVERLAY_FIELD_DESCRIPTION, asset.description, style.primary, 14))
        }
    }

    if (lines.isEmpty()) return

    // Expand/collapse lifecycle. Always starts big (visible immediately).
    //  - Static: never moves.
    //  - Expand-forever: holds expanded indefinitely.
    //  - Loop: oscillates between expanded and collapsed.
    //  - Once: collapses after the first hold and stays collapsed.
    // Either hold set to "indefinite" pins the overlay in that state.
    // Tapping the pill while collapsed re-opens it and pauses the cycle for this
    // photo (userPinned), so a manual peek isn't immediately snapped away.
    var expanded by remember { mutableStateOf(true) }
    var userPinned by remember(asset?.id) { mutableStateOf(false) }
    LaunchedEffect(
        animation.mode,
        asset?.id,
        animation.collapsedSeconds,
        animation.expandedSeconds,
        animation.expandedIndefinite,
        animation.collapsedIndefinite
    ) {
        userPinned = false
        if (!animation.enabled) {
            expanded = true
            return@LaunchedEffect
        }
        expanded = true
        if (animation.expandedIndefinite) return@LaunchedEffect
        while (!userPinned) {
            delay(animation.expandedSeconds * 1000L)
            if (userPinned) break
            expanded = false
            if (animation.collapsedIndefinite ||
                animation.mode == SettingsRepository.OVERLAY_ANIM_ONCE
            ) {
                return@LaunchedEffect
            }
            delay(animation.collapsedSeconds * 1000L)
            if (userPinned) break
            expanded = true
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (!animation.enabled || expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "overlayExpand"
    )
    val effScale = style.scale * (animation.collapsedScale + (animation.expandedScale - animation.collapsedScale) * progress)

    // Lines the user pinned to survive the collapsed phase.
    val pinnedIds = lines.filter { it.id in animation.collapsedFields }.map { it.id }.toSet()
    val nothingPinned = pinnedIds.isEmpty()
    val showAllLines = !animation.enabled || expanded
    // The affordance only appears when the collapse would otherwise show nothing.
    val showIcon = animation.enabled && !expanded && nothingPinned
    val lineSpacing = if (animation.enabled) 6f * progress else 0f

    val pillModifier = Modifier
        .align(alignment)
        .padding(20.dp)
        .let { if (animation.marquee) it.widthIn(max = 600.dp) else it }
        .then(
            if (animation.enabled) Modifier.clickable {
                expanded = !expanded
                userPinned = true
            } else Modifier
        )

    @Composable
    fun lineText(line: OverlayLine) {
        Text(
            text = line.text,
            color = line.color,
            fontSize = (line.baseSp * effScale).sp,
            style = LocalTextStyle.current.copy(shadow = style.shadow),
            maxLines = if (animation.marquee) 1 else Int.MAX_VALUE,
            modifier = if (animation.marquee) Modifier.basicMarquee() else Modifier
        )
    }

    Pill(style = style, modifier = pillModifier) {
        Column {
            if (nothingPinned) {
                // Nothing pinned: collapsed is just the info glyph, expanded is all lines.
                if (showAllLines) {
                    lines.forEachIndexed { index, line ->
                        lineText(line)
                        if (index < lines.lastIndex) Spacer(modifier = Modifier.height(lineSpacing.dp))
                    }
                } else if (showIcon) {
                    InfoGlyph(style.primary)
                }
            } else {
                lines.forEachIndexed { index, line ->
                    val isPinned = line.id in pinnedIds
                    @Composable
                    fun renderLine() {
                        lineText(line)
                    }
                    if (isPinned) {
                        renderLine()
                    } else {
                        AnimatedVisibility(
                            visible = showAllLines,
                            enter = fadeIn(tween(400)) + expandVertically(tween(400)),
                            exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
                        ) { renderLine() }
                    }
                    if (index < lines.lastIndex) {
                        Spacer(modifier = Modifier.height(lineSpacing.dp))
                    }
                }
            }
        }
    }
}

// Small circled "i" used as the collapsed overlay's affordance to tap for more info.
@Composable
private fun InfoGlyph(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(CircleShape)
            .border(1.5.dp, color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("i", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatDate(format: String, millis: Long): String {
    return try {
        SimpleDateFormat(format, Locale.getDefault()).format(Date(millis))
    } catch (_: Exception) {
        SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(millis))
    }
}

private fun formatPeopleWithAge(names: String, birthDates: String, photoDateMillis: Long): String {
    // birthDates format: "Alice=1990-01-15;Bob=1988-06-20"
    val bdMap = birthDates.split(";").associate { entry ->
        val parts = entry.split("=", limit = 2)
        parts[0] to parts.getOrElse(1) { "" }
    }
    val photoCal = Calendar.getInstance().apply { timeInMillis = photoDateMillis }

    return names.split(", ").joinToString(", ") { name ->
        val bd = bdMap[name]
        if (bd != null) {
            try {
                val birthCal = Calendar.getInstance().apply {
                    time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(bd)!!
                }
                var age = photoCal.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
                if (photoCal.get(Calendar.DAY_OF_YEAR) < birthCal.get(Calendar.DAY_OF_YEAR)) age--
                if (age >= 0) "$name ($age)" else name
            } catch (_: Exception) { name }
        } else name
    }
}
