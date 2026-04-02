package com.bogocat.framecache.api.navidrome

import com.bogocat.framecache.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NavidromeModule {

    @Provides
    @Singleton
    @Named("navidrome")
    fun provideOkHttpClient(settings: SettingsRepository): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(SubsonicAuthInterceptor(settings))
            .build()
    }

    @Provides
    @Singleton
    @Named("navidrome")
    fun provideRetrofit(
        @Named("navidrome") client: OkHttpClient,
        settings: SettingsRepository
    ): Retrofit {
        // Dynamic base URL — interceptor rewrites per-request from settings.
        val baseUrl = runBlocking { settings.navidromeUrl.first() }
        val url = if (baseUrl.isNotBlank() && baseUrl.startsWith("http")) {
            if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        } else {
            "http://localhost/"
        }
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client.newBuilder()
                .addInterceptor(Interceptor { chain ->
                    val currentUrl = runBlocking { settings.navidromeUrl.first() }
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
    fun provideSubsonicApi(@Named("navidrome") retrofit: Retrofit): SubsonicApi {
        return retrofit.create(SubsonicApi::class.java)
    }
}
