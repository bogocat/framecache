package com.bogocat.framecache.ui.slideshow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bogocat.framecache.data.db.AssetDao
import com.bogocat.framecache.data.db.CachedAsset
import com.bogocat.framecache.data.settings.SettingsRepository
import com.bogocat.framecache.music.MusicPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SlideshowState(
    val currentAsset: CachedAsset? = null,
    val secondAsset: CachedAsset? = null,  // side-by-side portrait pair
    val cachedCount: Int = 0,
    val isPaused: Boolean = false,
    val progress: Float = 0f
)

@HiltViewModel
class SlideshowViewModel @Inject constructor(
    private val assetDao: AssetDao,
    private val settings: SettingsRepository,
    val musicPlayer: MusicPlayer
) : ViewModel() {

    private val _state = MutableStateFlow(SlideshowState())
    val state: StateFlow<SlideshowState> = _state.asStateFlow()

    // Overlay settings
    val showClock = settings.showClock.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showDate = settings.showDate.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showPhotoDate = settings.showPhotoDate.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showLocation = settings.showLocation.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showDescription = settings.showDescription.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showPeople = settings.showPeople.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val showCamera = settings.showCamera.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val dateFormat = settings.dateFormat.stateIn(viewModelScope, SharingStarted.Eagerly, "MMM dd, yyyy")
    val showRating = settings.showRating.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val showPersonAge = settings.showPersonAge.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val clockFormat = settings.clockFormat.stateIn(viewModelScope, SharingStarted.Eagerly, "12")

    // Overlay presentation
    val overlayTextSize = settings.overlayTextSize.stateIn(viewModelScope, SharingStarted.Eagerly, "medium")
    val overlayTextColor = settings.overlayTextColor.stateIn(viewModelScope, SharingStarted.Eagerly, "light")
    val overlayBackground = settings.overlayBackground.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val overlayBackgroundOpacity = settings.overlayBackgroundOpacity.stateIn(viewModelScope, SharingStarted.Eagerly, 53)
    val overlayClockPosition = settings.overlayClockPosition.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.OVERLAY_POS_TOP_START)
    val overlayInfoPosition = settings.overlayInfoPosition.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.OVERLAY_POS_BOTTOM_START)
    val overlayCornerRadius = settings.overlayCornerRadius.stateIn(viewModelScope, SharingStarted.Eagerly, 16)
    val overlayAnimation = settings.overlayAnimation.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.OVERLAY_ANIM_STATIC)
    val overlayMarquee = settings.overlayMarquee.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val overlayExpandScale = settings.overlayExpandScale.stateIn(viewModelScope, SharingStarted.Eagerly, 130)
    val overlayCollapsedSeconds = settings.overlayCollapsedSeconds.stateIn(viewModelScope, SharingStarted.Eagerly, 6)
    val overlayExpandedSeconds = settings.overlayExpandedSeconds.stateIn(viewModelScope, SharingStarted.Eagerly, 6)
    val overlayExpandedIndefinite = settings.overlayExpandedIndefinite.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val overlayCollapsedIndefinite = settings.overlayCollapsedIndefinite.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val overlayCollapsedFields = settings.overlayCollapsedFields.stateIn(viewModelScope, SharingStarted.Eagerly, setOf(SettingsRepository.OVERLAY_FIELD_DATE))

    // Slideshow settings
    val crossfadeDuration = settings.crossfadeDuration.stateIn(viewModelScope, SharingStarted.Eagerly, 1500)
    val kenBurnsEnabled = settings.kenBurnsEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val kenBurnsZoom = settings.kenBurnsZoom.stateIn(viewModelScope, SharingStarted.Eagerly, 120)
    val backgroundBlur = settings.backgroundBlur.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val imageScale = settings.imageScale.stateIn(viewModelScope, SharingStarted.Eagerly, "fit")
    val showProgressBar = settings.showProgressBar.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Music
    val navidromeEnabled = settings.navidromeEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Sleep
    val sleepEnabled = settings.sleepEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val sleepStartHour = settings.sleepStartHour.stateIn(viewModelScope, SharingStarted.Eagerly, 22)
    val sleepEndHour = settings.sleepEndHour.stateIn(viewModelScope, SharingStarted.Eagerly, 7)
    val sleepDim = settings.sleepDim.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // History for previous
    private val history = mutableListOf<CachedAsset>()
    private var historyIndex = -1

    private var slideshowJob: Job? = null

    // Bumped on manual skip; the timer loop checks this to abandon stale iterations
    // so a skip at e.g. 40/45s gives the next photo a fresh full duration.
    @Volatile private var advanceGeneration: Int = 0

    // Pushed by SlideshowScreen on configuration changes; drives pair direction.
    @Volatile private var isDeviceLandscape: Boolean = true

    fun setDeviceLandscape(landscape: Boolean) {
        val changed = isDeviceLandscape != landscape
        isDeviceLandscape = landscape
        // Re-pick under the new orientation so "match"/"match + pairs" apply
        // immediately after a rotation instead of waiting out the current photo.
        if (changed && _state.value.currentAsset != null) {
            viewModelScope.launch {
                advanceGeneration++
                advance()
            }
        }
    }

    init {
        startSlideshow()
        // Apply a changed orientation policy without waiting for the current
        // (possibly very long) duration to elapse.
        viewModelScope.launch {
            settings.orientationMode.drop(1).collect {
                advanceGeneration++
                advance()
            }
        }
    }

    fun startSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = viewModelScope.launch {
            var cached = assetDao.getCachedCount()
            while (cached == 0) {
                _state.value = _state.value.copy(cachedCount = 0)
                delay(2000)
                cached = assetDao.getCachedCount()
                android.util.Log.d("Slideshow", "Waiting for cache: $cached cached")
            }

            val first = getNextFiltered()
            if (first != null) {
                assetDao.markDisplayed(first.id)
                val second = pairPartnerFor(first)
                if (second != null) assetDao.markDisplayed(second.id)
                history.add(first)
                historyIndex = 0
                _state.value = _state.value.copy(
                    currentAsset = first,
                    secondAsset = second,
                    cachedCount = assetDao.getCachedCount()
                )
            }

            // Slideshow loop with progress tracking
            while (true) {
                val gen = advanceGeneration
                val duration = settings.duration.first()
                val stepMs = 100L
                val totalSteps = (duration * 1000L) / stepMs

                for (step in 0..totalSteps) {
                    if (advanceGeneration != gen) break
                    if (_state.value.isPaused) {
                        delay(stepMs)
                        continue
                    }
                    _state.value = _state.value.copy(progress = step.toFloat() / totalSteps)
                    delay(stepMs)
                }

                // Only auto-advance if no manual skip happened during this cycle.
                if (advanceGeneration == gen) {
                    advanceGeneration++
                    advance()
                }
            }
        }
    }

    private suspend fun getNextFiltered(): CachedAsset? {
        val order = settings.photoOrder.first()
        val favOnly = settings.favoritesOnly.first()
        val orientationMode = settings.orientationMode.first()
        val currentId = _state.value.currentAsset?.id ?: ""

        // In "match" mode the primary pick is restricted to the screen's own
        // orientation. Other modes draw from every orientation.
        val matchOnly = orientationMode == SettingsRepository.ORIENTATION_MATCH

        val filtered = when {
            favOnly -> when {
                matchOnly && isDeviceLandscape -> assetDao.getNextFavoriteLandscape(currentId)
                matchOnly -> assetDao.getNextFavoritePortrait(currentId)
                else -> assetDao.getNextFavorite(currentId)
            }
            order == "chronological" -> when {
                matchOnly && isDeviceLandscape -> assetDao.getNextChronologicalLandscape(currentId)
                matchOnly -> assetDao.getNextChronologicalPortrait(currentId)
                else -> assetDao.getNextChronological(currentId)
            }
            matchOnly && isDeviceLandscape -> assetDao.getNextLandscape(currentId)
            matchOnly -> assetDao.getNextPortrait(currentId)
            else -> null
        }

        // Fall back to any orientation so the frame never goes blank (e.g. the
        // orientation filter matches nothing yet).
        return filtered
            ?: assetDao.getNextRandom(currentId)
            ?: assetDao.getNextRandom("")
    }

    // Pair when the photo's orientation is opposite the device's orientation,
    // so two portraits fill a landscape frame, two landscapes stack on a portrait frame.
    // Prefer a partner from the same time period: 7d → 30d → 365d → any.
    private suspend fun pairPartnerFor(asset: CachedAsset): CachedAsset? {
        // Only "match + pairs" pairs opposite-orientation photos; "match" shows
        // only same-orientation and "all" shows everything one at a time.
        if (settings.orientationMode.first() != SettingsRepository.ORIENTATION_MATCH_PAIR) return null

        val w = asset.width ?: return null
        val h = asset.height ?: return null
        val isPhotoPortrait = h > w
        val shouldPair = isPhotoPortrait == isDeviceLandscape
        if (!shouldPair) return null

        val date = asset.dateTaken
        if (date != null) {
            val day = 86_400_000L
            for (windowDays in listOf(7L, 30L, 365L)) {
                val min = date - windowDays * day
                val max = date + windowDays * day
                val partner = if (isPhotoPortrait) {
                    assetDao.getNextPortraitNearDate(asset.id, min, max)
                } else {
                    assetDao.getNextLandscapeNearDate(asset.id, min, max)
                }
                if (partner != null) return partner
            }
        }
        return if (isPhotoPortrait) assetDao.getNextPortrait(asset.id)
               else                 assetDao.getNextLandscape(asset.id)
    }

    private suspend fun advance() {
        val next = getNextFiltered() ?: return
        assetDao.markDisplayed(next.id)

        val second = pairPartnerFor(next)
        if (second != null) assetDao.markDisplayed(second.id)

        // Add to history (keep last 50)
        if (historyIndex < history.size - 1) {
            while (history.size > historyIndex + 1) history.removeAt(history.size - 1)
        }
        history.add(next)
        if (history.size > 50) history.removeAt(0)
        historyIndex = history.size - 1

        _state.value = _state.value.copy(
            currentAsset = next,
            secondAsset = second,
            cachedCount = assetDao.getCachedCount(),
            progress = 0f
        )
    }

    fun togglePause() {
        _state.value = _state.value.copy(isPaused = !_state.value.isPaused)
    }

    fun nextImage() {
        viewModelScope.launch {
            advanceGeneration++
            advance()
        }
    }

    fun previousImage() {
        viewModelScope.launch {
            if (historyIndex > 0) {
                advanceGeneration++
                historyIndex--
                val prev = history[historyIndex]
                _state.value = _state.value.copy(currentAsset = prev, progress = 0f)
            }
        }
    }
}
