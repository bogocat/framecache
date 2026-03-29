package com.bogocat.framecache

import android.service.dreams.DreamService
import androidx.compose.ui.platform.ComposeView
import com.bogocat.framecache.ui.slideshow.SlideshowScreen
import com.bogocat.framecache.ui.theme.FrameCacheTheme

class PhotoFrameDreamService : DreamService() {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true

        val view = ComposeView(this)
        view.setContent {
            FrameCacheTheme {
                SlideshowScreen()
            }
        }
        setContentView(view)
    }
}
