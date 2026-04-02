package com.bogocat.framecache.api.navidrome

import com.bogocat.framecache.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * OkHttp interceptor that adds Subsonic authentication parameters to every request.
 * Generates a fresh random salt per request (same approach as Ultrasonic).
 *
 * Auth scheme: t = md5(password + salt), sent alongside u, s, v, c, f.
 */
class SubsonicAuthInterceptor(
    private val settings: SettingsRepository
) : Interceptor {

    private val secureRandom = SecureRandom()

    override fun intercept(chain: Interceptor.Chain): Response {
        val username = runBlocking { settings.navidromeUsername.first() }
        val password = runBlocking { settings.navidromePassword.first() }

        val salt = generateSalt()
        val token = md5("$password$salt")

        val url = chain.request().url.newBuilder()
            .addQueryParameter("u", username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "FrameCache")
            .addQueryParameter("f", "json")
            .build()

        return chain.proceed(chain.request().newBuilder().url(url).build())
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(12)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
