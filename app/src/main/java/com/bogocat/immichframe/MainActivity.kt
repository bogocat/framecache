package com.bogocat.immichframe

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bogocat.immichframe.data.cache.ImageCacheManager
import com.bogocat.immichframe.data.settings.SettingsRepository
import com.bogocat.immichframe.sync.SyncScheduler
import com.bogocat.immichframe.ui.settings.SettingsScreen
import com.bogocat.immichframe.ui.setup.SetupScreen
import com.bogocat.immichframe.ui.slideshow.SlideshowScreen
import com.bogocat.immichframe.ui.theme.ImmichFrameTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var cacheManager: ImageCacheManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Accept config via adb intent extras:
        // adb shell am start -n com.bogocat.immichframe/.MainActivity \
        //   --es server_url "https://photos.example.com" \
        //   --es api_key "your-api-key" \
        //   --es album_ids "uuid1,uuid2"
        intent?.let { handleConfigIntent(it) }

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Fullscreen immersive
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Trigger sync on every launch
        SyncScheduler.schedulePeriodicSync(this)
        SyncScheduler.triggerImmediateSync(this)

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
                            }
                        )
                    }
                    showSettings -> {
                        SettingsScreen(
                            settings = settings,
                            cacheManager = cacheManager,
                            onBack = {
                                setShowSettings(false)
                                SyncScheduler.triggerImmediateSync(this@MainActivity)
                            }
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

    private fun handleConfigIntent(intent: android.content.Intent) {
        val serverUrl = intent.getStringExtra("server_url")
        val apiKey = intent.getStringExtra("api_key")
        val albumIds = intent.getStringExtra("album_ids")

        if (serverUrl != null || apiKey != null || albumIds != null) {
            Log.i("ImmichFrame", "Received config via intent: url=$serverUrl albums=$albumIds")
            runBlocking {
                settings.saveServerConfig(
                    url = serverUrl ?: "",
                    apiKey = apiKey ?: "",
                    albumIds = albumIds?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                )
            }
        }
    }
}
