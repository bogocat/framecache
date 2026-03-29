package com.bogocat.framecache.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bogocat.framecache.api.ImmichApi
import com.bogocat.framecache.api.model.AlbumResponse
import com.bogocat.framecache.data.settings.SettingsRepository
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private val textColor = Color.White
private val accentColor = Color(0xFF4FC3F7)
private val bgColor = Color(0xFF1A1A1A)
private val fieldColors
    @Composable get() = OutlinedTextFieldDefaults.colors(
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,
        focusedBorderColor = accentColor,
        unfocusedBorderColor = Color(0x55FFFFFF),
        focusedLabelColor = accentColor,
        unfocusedLabelColor = Color(0xAAFFFFFF),
        cursorColor = accentColor,
        focusedPlaceholderColor = Color(0x55FFFFFF),
        unfocusedPlaceholderColor = Color(0x55FFFFFF)
    )

@Composable
fun SetupScreen(
    settings: SettingsRepository,
    onSetupComplete: () -> Unit
) {
    var serverUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var albums by remember { mutableStateOf<List<AlbumResponse>>(emptyList()) }
    val selectedAlbums = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "FrameCache Setup",
            color = textColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Connect to your Immich server",
            color = Color(0xAAFFFFFF),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server URL") },
            placeholder = { Text("https://photos.example.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = fieldColors
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = fieldColors
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    status = "Connecting..."
                    try {
                        val api = createTestApi(serverUrl, apiKey)
                        val about = api.getServerAbout()
                        status = "Connected to Immich ${about.version}"
                        albums = api.getAlbums()
                    } catch (e: Exception) {
                        status = "Error: ${e.message}"
                    }
                    isLoading = false
                }
            },
            enabled = serverUrl.isNotBlank() && apiKey.isNotBlank() && !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = Color.Black,
                disabledContainerColor = Color(0x33FFFFFF)
            )
        ) {
            Text("Test Connection")
        }

        if (isLoading) {
            CircularProgressIndicator(
                color = accentColor,
                modifier = Modifier.padding(16.dp)
            )
        }

        if (status.isNotBlank()) {
            Text(
                text = status,
                color = if (status.startsWith("Error")) Color(0xFFFF5252) else Color(0xFF69F0AE),
                modifier = Modifier.padding(8.dp)
            )
        }

        if (albums.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Select albums:",
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                items(albums) { album ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = album.id in selectedAlbums,
                            onCheckedChange = { checked ->
                                if (checked) selectedAlbums.add(album.id)
                                else selectedAlbums.remove(album.id)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = accentColor,
                                uncheckedColor = Color(0xAAFFFFFF),
                                checkmarkColor = Color.Black
                            )
                        )
                        Text(
                            "${album.albumName} (${album.assetCount})",
                            color = textColor,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    scope.launch {
                        settings.saveServerConfig(serverUrl, apiKey, selectedAlbums.toList())
                        onSetupComplete()
                    }
                },
                enabled = selectedAlbums.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0x33FFFFFF)
                )
            ) {
                Text("Start")
            }
        }
    }
}

private fun createTestApi(serverUrl: String, apiKey: String): ImmichApi {
    val url = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("x-api-key", apiKey)
                .build()
            chain.proceed(request)
        }
        .build()

    return Retrofit.Builder()
        .baseUrl(url)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ImmichApi::class.java)
}
