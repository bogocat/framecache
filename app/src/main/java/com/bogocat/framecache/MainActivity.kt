package com.bogocat.framecache

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.bogocat.framecache.api.navidrome.NavidromeClient
import com.bogocat.framecache.data.cache.ImageCacheManager
import com.bogocat.framecache.data.db.SongDao
import com.bogocat.framecache.data.settings.SettingsRepository
import com.bogocat.framecache.music.MusicPlayer
import com.bogocat.framecache.sync.SyncScheduler
import com.bogocat.framecache.ui.music.MusicScreen
import com.bogocat.framecache.ui.settings.SettingsScreen
import com.bogocat.framecache.ui.setup.SetupScreen
import com.bogocat.framecache.ui.slideshow.SlideshowScreen
import com.bogocat.framecache.ui.theme.FrameCacheTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var cacheManager: ImageCacheManager
    @Inject lateinit var api: com.bogocat.framecache.api.ImmichApi
    @Inject lateinit var navidromeClient: NavidromeClient
    @Inject lateinit var musicPlayer: MusicPlayer
    @Inject lateinit var songDao: SongDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent?.let { handleConfigIntent(it) }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        lifecycleScope.launch {
            SyncScheduler.applySyncInterval(this@MainActivity, settings)
        }
        SyncScheduler.triggerImmediateSync(this)

        setContent {
            FrameCacheTheme {
                val isConfigured by settings.isConfigured.collectAsState(initial = false)
                var screen by remember { mutableStateOf("slideshow") }

                when {
                    !isConfigured -> {
                        SetupScreen(
                            settings = settings,
                            onSetupComplete = {
                                SyncScheduler.triggerImmediateSync(this@MainActivity)
                            }
                        )
                    }
                    screen == "settings" -> {
                        SettingsScreen(
                            settings = settings,
                            cacheManager = cacheManager,
                            api = api,
                            navidromeClient = navidromeClient,
                            musicPlayer = musicPlayer,
                            onBack = {
                                screen = "slideshow"
                                SyncScheduler.triggerImmediateSync(this@MainActivity)
                            }
                        )
                    }
                    screen == "music" -> {
                        MusicScreen(
                            musicPlayer = musicPlayer,
                            songDao = songDao,
                            settings = settings,
                            onBack = { screen = "slideshow" },
                            onOpenSettings = { screen = "settings" }
                        )
                    }
                    else -> {
                        SlideshowScreen(
                            onOpenSettings = { screen = "settings" },
                            onOpenMusic = { screen = "music" }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        musicPlayer.release()
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
