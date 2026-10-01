package com.bogocat.framecache.api.denon

import android.util.Log
import com.bogocat.framecache.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controls a Denon AVR (e.g. AVR-X1700H) using the same protocols as the
 * `music-control` project: UPnP/DLNA AVTransport for streaming (the receiver
 * pulls the URL itself) and the telnet control port to wake it.
 *
 * DLNA endpoint:  POST http://<host>:60006/upnp/control/renderer_dvc/AVTransport
 * Telnet control: <host>:23  (e.g. "PWON")
 */
@Singleton
class DenonClient @Inject constructor(
    private val settings: SettingsRepository
) {
    companion object {
        const val TAG = "Denon"
        const val DEFAULT_HOST = "10.89.97.15"
        const val DLNA_PORT = 60006
        const val TELNET_PORT = 23
        private const val AV_TRANSPORT_PATH = "/upnp/control/renderer_dvc/AVTransport"
        private const val AVT_SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private suspend fun host(): String =
        settings.denonHost.first().trim().ifBlank { DEFAULT_HOST }

    /** Point the receiver at a stream URL and start playing. Wakes it first. */
    suspend fun playStream(
        url: String,
        title: String,
        artist: String,
        album: String
    ): Boolean {
        val h = host()
        powerOn(h)

        val didl = """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
            <item id="0" parentID="-1" restricted="false">
            <dc:title>${escape(title)}</dc:title>
            <dc:creator>${escape(artist)}</dc:creator>
            <upnp:album>${escape(album)}</upnp:album>
            <upnp:class>object.item.audioItem.musicTrack</upnp:class>
            <res protocolInfo="http-get:*:audio/mpeg:*">${escape(url)}</res>
            </item>
            </DIDL-Lite>
        """.trimIndent()

        val setBody = """
            <u:SetAVTransportURI xmlns:u="$AVT_SERVICE">
            <InstanceID>0</InstanceID>
            <CurrentURI>${escape(url)}</CurrentURI>
            <CurrentURIMetaData>${escape(didl)}</CurrentURIMetaData>
            </u:SetAVTransportURI>
        """.trimIndent()

        val setOk = soap(h, "SetAVTransportURI", setBody) != null
        if (!setOk) {
            Log.w(TAG, "SetAVTransportURI failed for $title")
            return false
        }
        delay(250)
        return play()
    }

    suspend fun play(): Boolean =
        soap(host(), "Play", "<u:Play xmlns:u=\"$AVT_SERVICE\"><InstanceID>0</InstanceID><Speed>1</Speed></u:Play>") != null

    suspend fun pause(): Boolean =
        soap(host(), "Pause", "<u:Pause xmlns:u=\"$AVT_SERVICE\"><InstanceID>0</InstanceID></u:Pause>") != null

    suspend fun stop(): Boolean =
        soap(host(), "Stop", "<u:Stop xmlns:u=\"$AVT_SERVICE\"><InstanceID>0</InstanceID></u:Stop>") != null

    /** One of STOPPED / PLAYING / PAUSED_PLAYBACK / TRANSITIONING / NO_MEDIA_PRESENT / UNKNOWN. */
    suspend fun getTransportState(): String {
        val body = soap(
            host(),
            "GetTransportInfo",
            "<u:GetTransportInfo xmlns:u=\"$AVT_SERVICE\"><InstanceID>0</InstanceID></u:GetTransportInfo>"
        ) ?: return "UNKNOWN"
        return Regex("<CurrentTransportState>\\s*(\\w+)\\s*</CurrentTransportState>")
            .find(body)?.groupValues?.get(1) ?: "UNKNOWN"
    }

    suspend fun ping(): Boolean = getTransportState() != "UNKNOWN"

    // -- internals --

    private suspend fun soap(host: String, action: String, inner: String): String? =
        withContext(Dispatchers.IO) {
            val envelope = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body>$inner</s:Body>
</s:Envelope>"""
            val request = Request.Builder()
                .url("http://$host:$DLNA_PORT$AV_TRANSPORT_PATH")
                .post(envelope.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
                .header("SOAPAction", "\"$AVT_SERVICE#$action\"")
                .header("Connection", "close")
                .build()
            try {
                http.newCall(request).execute().use { resp ->
                    Log.d(TAG, "SOAP $action -> HTTP ${resp.code}")
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            } catch (e: Exception) {
                Log.w(TAG, "SOAP $action failed: ${e.message}")
                null
            }
        }

    /** Denon accepts telnet commands as soon as it's connected; no reply needed. */
    private suspend fun powerOn(host: String) = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, TELNET_PORT), 3000)
                socket.getOutputStream().write("PWON\r".toByteArray())
                socket.getOutputStream().flush()
                delay(200)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Telnet power-on failed: ${e.message}")
        }
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
