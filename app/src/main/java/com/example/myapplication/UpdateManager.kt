package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.net.URL

object UpdateManager {

    // 👇 ЗАМЕНИ НА СВОИ ДАННЫЕ
    private const val GITHUB_OWNER = "Qeedy94"
    private const val GITHUB_REPO = "MyQcHelper"
    private const val APK_ASSET_NAME = "app-release.apk"

    data class UpdateInfo(val version: String, val downloadUrl: String)

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val apiUrl = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")

            if (connection.responseCode != 200) return@withContext null

            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val tagName = json.getString("tag_name").removePrefix("v")

            if (isNewerVersion(tagName, currentVersion)) {
                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name") == APK_ASSET_NAME) {
                        return@withContext UpdateInfo(
                            version = tagName,
                            downloadUrl = asset.getString("browser_download_url")
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "update.apk")
            if (file.exists()) file.delete()

            val uri = URI(url)
            // 👇 Пытаемся получить системный прокси (если включен VPN/Clash)
            val proxy = ProxySelector.getDefault()?.select(uri)?.firstOrNull()

            val connection = if (proxy != null && proxy.type() != Proxy.Type.DIRECT) {
                URL(url).openConnection(proxy) as HttpURLConnection
            } else {
                URL(url).openConnection() as HttpURLConnection
            }

            // 👇 ВАЖНО: GitHub часто редиректит на CDN, HttpURLConnection по умолчанию не следует редиректам
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 20000
            connection.readTimeout = 20000
            connection.connect()

            val totalSize = connection.contentLengthLong
            var downloadedSize = 0L

            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        withContext(Dispatchers.Main) {
                            onProgress(downloadedSize, totalSize)
                        }
                    }
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun installApk(context: Context, file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        context.startActivity(intent)
    }
}