package com.zero.todo.data.backup

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class WebDavConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val remoteFileName: String = DEFAULT_REMOTE_FILE
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank()

    fun resolvedFileUrl(): String {
        val base = serverUrl.trim().trimEnd('/')
        val file = remoteFileName.trim().ifBlank { DEFAULT_REMOTE_FILE }.trimStart('/')
        return if (base.endsWith(".json", ignoreCase = true)) base else "$base/$file"
    }

    companion object {
        const val DEFAULT_REMOTE_FILE = "zero_backup.json"
    }
}

/**
 * Lightweight zero-dependency WebDAV sync client built on standard [HttpURLConnection].
 * Supports pushing/pulling ZERO's full JSON backup snapshot across phones, foldables, and tablets.
 */
@Singleton
class WebDavSyncManager @Inject constructor() {

    suspend fun testConnection(config: WebDavConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val urlStr = config.serverUrl.trim().ifBlank { error("Server URL is empty") }
            val conn = openConnection(urlStr, "OPTIONS", config)
            try {
                val code = conn.responseCode
                if (code !in 200..299 && code != 204 && code != 207 && code != 405) {
                    error("HTTP $code")
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun pushBackup(config: WebDavConfig, jsonPayload: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val targetUrl = config.resolvedFileUrl()
            val conn = openConnection(targetUrl, "PUT", config).apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            try {
                conn.outputStream.use { out ->
                    out.write(jsonPayload.toByteArray(Charsets.UTF_8))
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    error("HTTP $code")
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun pullBackup(config: WebDavConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val targetUrl = config.resolvedFileUrl()
            val conn = openConnection(targetUrl, "GET", config)
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    error("HTTP $code")
                }
                conn.inputStream.use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun openConnection(urlString: String, method: String, config: WebDavConfig): HttpURLConnection {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            val credentials = "${config.username}:${config.password}"
            val encoded = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            setRequestProperty("Authorization", "Basic $encoded")
            setRequestProperty("User-Agent", "ZERO-Android/1.3.0")
        }
        return conn
    }
}
