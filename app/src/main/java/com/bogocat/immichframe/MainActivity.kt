package com.bogocat.immichframe

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bogocat.immichframe.data.cache.ImageCacheManager
import com.bogocat.immichframe.data.settings.SettingsRepository
import com.bogocat.immichframe.sync.SyncScheduler
import com.bogocat.immichframe.ui.setup.SetupScreen
import com.bogocat.immichframe.ui.settings.SettingsScreen
import com.bogocat.immichframe.ui.slideshow.SlideshowScreen
import com.bogocat.immichframe.ui.theme.ImmichFrameTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var cacheManager: ImageCacheManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Fullscreen immersive
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            ImmichFrameTheme {
                val isConfigured by settings.isConfigured.collectAsState(initial = false)
                val (showSettings, setShowSettings) = remember { mutableStateOf(false) }

                when {
                    !isConfigured -> {
                        SetupScreen(
                            settings = settings,
                            onSetupComplete = {
                                SyncScheduler.triggerImmediateSync(this@MainActivity)
                                SyncScheduler.schedulePeriodicSync(this@MainActivity)
                            }
                        )
                    }
                    showSettings -> {
                        SettingsScreen(
                            settings = settings,
                            cacheManager = cacheManager,
                            onBack = { setShowSettings(false) }
                        )
                    }
                    else -> {
                        SlideshowScreen(
                            onOpenSettings = { setShowSettings(true) }
                        )
                    }
                }
            }
        }
    }
}
