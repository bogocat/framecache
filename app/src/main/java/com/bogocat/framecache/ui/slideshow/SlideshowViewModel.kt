package com.bogocat.framecache.ui.slideshow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bogocat.framecache.data.db.AssetDao
import com.bogocat.framecache.data.db.CachedAsset
import com.bogocat.framecache.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SlideshowState(
    val currentAsset: CachedAsset? = null,
    val cachedCount: Int = 0,
    val isPaused: Boolean = false,
    val progress: Float = 0f
)

@HiltViewModel
class SlideshowViewModel @Inject constructor(
    private val assetDao: AssetDao,
    private val settings: SettingsRepository
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

    // Slideshow settings
    val crossfadeDuration = settings.crossfadeDuration.stateIn(viewModelScope, SharingStarted.Eagerly, 1500)
    val kenBurnsEnabled = settings.kenBurnsEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val kenBurnsZoom = settings.kenBurnsZoom.stateIn(viewModelScope, SharingStarted.Eagerly, 120)
    val backgroundBlur = settings.backgroundBlur.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val imageScale = settings.imageScale.stateIn(viewModelScope, SharingStarted.Eagerly, "fit")
    val showProgressBar = settings.showProgressBar.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Sleep
    val sleepEnabled = settings.sleepEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val sleepStartHour = settings.sleepStartHour.stateIn(viewModelScope, SharingStarted.Eagerly, 22)
    val sleepEndHour = settings.sleepEndHour.stateIn(viewModelScope, SharingStarted.Eagerly, 7)
    val sleepDim = settings.sleepDim.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // History for previous
    private val history = mutableListOf<CachedAsset>()
    private var historyIndex = -1

    private var slideshowJob: Job? = null

    init {
        startSlideshow()
    }

    fun startSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = viewModelScope.launch {
            while (assetDao.getCachedCount() == 0) {
                _state.value = _state.value.copy(cachedCount = 0)
                delay(2000)
            }

            val first = getNextFiltered()
            if (first != null) {
                assetDao.markDisplayed(first.id)
                history.add(first)
                historyIndex = 0
                _state.value = _state.value.copy(
                    currentAsset = first,
                    cachedCount = assetDao.getCachedCount()
                )
            }

            // Slideshow loop with progress tracking
            while (true) {
                val duration = settings.duration.first()
                val stepMs = 100L
                val totalSteps = (duration * 1000L) / stepMs

                for (step in 0..totalSteps) {
                    if (_state.value.isPaused) {
                        delay(stepMs)
                        continue
                    }
                    _state.value = _state.value.copy(progress = step.toFloat() / totalSteps)
                    delay(stepMs)
                }

                advance()
            }
        }
    }

    private suspend fun getNextFiltered(): CachedAsset? {
        val order = settings.photoOrder.first()
        val orientationFilter = settings.photoOrientationFilter.first()
        val favOnly = settings.favoritesOnly.first()

        return when {
            favOnly -> assetDao.getNextFavorite()
            orientationFilter == "landscape" -> assetDao.getNextLandscape()
            orientationFilter == "portrait" -> assetDao.getNextPortrait()
            order == "chronological" -> assetDao.getNextChronological()
            else -> assetDao.getNextRandom()
        }
    }

    private suspend fun advance() {
        val next = getNextFiltered() ?: return
        assetDao.markDisplayed(next.id)

        // Add to history (keep last 50)
        if (historyIndex < history.size - 1) {
            // Trim future if we navigated back then advanced
            while (history.size > historyIndex + 1) history.removeAt(history.size - 1)
        }
        history.add(next)
        if (history.size > 50) history.removeAt(0)
        historyIndex = history.size - 1

        _state.value = _state.value.copy(
            currentAsset = next,
            cachedCount = assetDao.getCachedCount(),
            progress = 0f
        )
    }

    fun togglePause() {
        _state.value = _state.value.copy(isPaused = !_state.value.isPaused)
    }

    fun nextImage() {
        viewModelScope.launch { advance() }
    }

    fun previousImage() {
        viewModelScope.launch {
            if (historyIndex > 0) {
                historyIndex--
                val prev = history[historyIndex]
                _state.value = _state.value.copy(currentAsset = prev, progress = 0f)
            }
        }
    }
}
