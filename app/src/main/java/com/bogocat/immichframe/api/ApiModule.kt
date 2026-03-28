package com.bogocat.immichframe.api

import com.bogocat.immichframe.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(settings: SettingsRepository): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                val apiKey = runBlocking { settings.apiKey.first() }
                val request = chain.request().newBuilder()
                    .addHeader("x-api-key", apiKey)
                    .build()
                chain.proceed(request)
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, settings: SettingsRepository): Retrofit {
        // Use a placeholder URL on first launch (before settings are configured).
        // The interceptor below rewrites the base URL on each request using the
        // current value from DataStore, so the placeholder is never actually hit.
        val baseUrl = runBlocking { settings.serverUrl.first() }
        val url = if (baseUrl.isNotBlank() && baseUrl.startsWith("http")) {
            if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        } else {
            "http://localhost/"
        }
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client.newBuilder()
                .addInterceptor(Interceptor { chain ->
                    val currentUrl = runBlocking { settings.serverUrl.first() }
                    if (currentUrl.isBlank() || !currentUrl.startsWith("http")) {
                        chain.proceed(chain.request())
                    } else {
                        val originalUrl = chain.request().url
                        val newBaseUrl = (if (currentUrl.endsWith("/")) currentUrl else "$currentUrl/")
                            .toHttpUrlOrNull()
                            ?: return@Interceptor chain.proceed(chain.request())
                        val newUrl = originalUrl.newBuilder()
                            .scheme(newBaseUrl.scheme)
                            .host(newBaseUrl.host)
                            .port(newBaseUrl.port)
                            .build()
                        chain.proceed(chain.request().newBuilder().url(newUrl).build())
                    }
                })
                .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideImmichApi(retrofit: Retrofit): ImmichApi {
        return retrofit.create(ImmichApi::class.java)
    }
}
