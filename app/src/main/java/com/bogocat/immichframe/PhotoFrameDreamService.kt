package com.bogocat.immichframe

import android.service.dreams.DreamService
import androidx.compose.ui.platform.ComposeView
import com.bogocat.immichframe.ui.slideshow.SlideshowScreen
import com.bogocat.immichframe.ui.theme.ImmichFrameTheme

class PhotoFrameDreamService : DreamService() {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true

        val view = ComposeView(this)
        view.setContent {
            ImmichFrameTheme {
                SlideshowScreen()
            }
        }
        setContentView(view)
    }
}
