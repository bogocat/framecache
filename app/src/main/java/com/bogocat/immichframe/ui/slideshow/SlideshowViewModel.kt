package com.bogocat.immichframe.ui.slideshow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bogocat.immichframe.data.db.AssetDao
import com.bogocat.immichframe.data.db.CachedAsset
import com.bogocat.immichframe.data.settings.SettingsRepository
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
    val nextAsset: CachedAsset? = null,
    val isTransitioning: Boolean = false,
    val cachedCount: Int = 0,
    val isPaused: Boolean = false
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

    // Slideshow settings
    val crossfadeDuration = settings.crossfadeDuration.stateIn(viewModelScope, SharingStarted.Eagerly, 1500)
    val kenBurnsEnabled = settings.kenBurnsEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val kenBurnsZoom = settings.kenBurnsZoom.stateIn(viewModelScope, SharingStarted.Eagerly, 120)
    val backgroundBlur = settings.backgroundBlur.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val imageScale = settings.imageScale.stateIn(viewModelScope, SharingStarted.Eagerly, "fit")

    private var slideshowJob: Job? = null

    init {
        startSlideshow()
    }

    fun startSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = viewModelScope.launch {
            // Wait for cache to have images
            while (assetDao.getCachedCount() == 0) {
                _state.value = _state.value.copy(cachedCount = 0)
                delay(2000)
            }

            // Load first image
            val first = assetDao.getNextAsset()
            if (first != null) {
                assetDao.markDisplayed(first.id)
                _state.value = _state.value.copy(
                    currentAsset = first,
                    cachedCount = assetDao.getCachedCount()
                )
            }

            // Slideshow loop
            while (true) {
                val duration = settings.duration.first()
                delay(duration * 1000L)

                if (_state.value.isPaused) {
                    delay(1000)
                    continue
                }

                advance()
            }
        }
    }

    private suspend fun advance() {
        val next = assetDao.getNextAsset() ?: return
        assetDao.markDisplayed(next.id)

        // Check if all images have been shown at least once
        val minCount = _state.value.currentAsset?.displayCount ?: 0
        if (minCount > 5) {
            assetDao.resetDisplayCounts()
        }

        _state.value = _state.value.copy(
            currentAsset = next,
            cachedCount = assetDao.getCachedCount()
        )
    }

    fun togglePause() {
        _state.value = _state.value.copy(isPaused = !_state.value.isPaused)
    }

    fun nextImage() {
        viewModelScope.launch { advance() }
    }

    fun previousImage() {
        // For now, just advance (would need history stack for true previous)
        viewModelScope.launch { advance() }
    }
}
