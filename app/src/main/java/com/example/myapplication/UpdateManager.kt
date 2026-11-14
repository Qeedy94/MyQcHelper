package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.net.URL
import java.util.zip.ZipInputStream

object UpdateManager {

    private const val BUCKET_URL = "https://myqchelper.oss-cn-hongkong.aliyuncs.com"

    data class UpdateInfo(val version: String, val downloadUrl: String)

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val listUrl = "$BUCKET_URL/?list-type=2"
            val uri = URI(listUrl)
            val proxy = ProxySelector.getDefault()?.select(uri)?.firstOrNull()
            val connection = if (proxy != null && proxy.type() != Proxy.Type.DIRECT) {
                URL(listUrl).openConnection(proxy) as HttpURLConnection
            } else {
                URL(listUrl).openConnection() as HttpURLConnection
            }

            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            if (connection.responseCode != 200) return@withContext null

            val xml = connection.inputStream.bufferedReader().readText()

            // 👇 Ищем ZIP-архивы, а не APK
            val regex = Regex("""<Key>([^<]*Punta_destroyer_v(\d+(?:\.\d+)*)[^<]*\.zip)</Key>""")
            val matches = regex.findAll(xml).map {
                Pair(it.groupValues[1], it.groupValues[2])
            }.distinct().toList()

            if (matches.isEmpty()) return@withContext null

            val latest = matches.maxWithOrNull { a, b -> compareVersions(a.second, b.second) }
                ?: return@withContext null

            val latestFileName = latest.first
            val latestVersion = latest.second

            if (isNewerVersion(latestVersion, currentVersion)) {
                val encodedPath = java.net.URLEncoder.encode(latestFileName, "UTF-8")
                    .replace("+", "%20")
                    .replace("%2F", "/")
                val downloadUrl = "$BUCKET_URL/$encodedPath"
                return@withContext UpdateInfo(version = latestVersion, downloadUrl = downloadUrl)
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

    }

    private fun compareVersions(a: String, b: String): Int {
        val aParts = a.split(".").mapNotNull { it.toIntOrNull() }
        val bParts = b.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(aParts.size, bParts.size)) {
            val av = aParts.getOrElse(i) { 0 }
            val bv = bParts.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        return compareVersions(latest, current) > 0
    }

    // 👇 Эта функция теперь скачивает ZIP и распаковывает из него APK
    suspend fun downloadAndExtractApk(
        context: Context,
        url: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val zipFile = File(context.cacheDir, "update.zip")
            if (zipFile.exists()) zipFile.delete()

            val uri = URI(url)
            val proxy = ProxySelector.getDefault()?.select(uri)?.firstOrNull()
            val connection = if (proxy != null && proxy.type() != Proxy.Type.DIRECT) {
                URL(url).openConnection(proxy) as HttpURLConnection
            } else {
                URL(url).openConnection() as HttpURLConnection
            }

            connection.instanceFollowRedirects = true
            connection.connectTimeout = 20000
            connection.readTimeout = 20000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MyQcHelper/1.0")
            connection.connect()

            if (connection.responseCode != 200) return@withContext null

            val totalSize = connection.contentLengthLong
            var downloadedSize = 0L

            // Скачиваем ZIP
            connection.inputStream.use { input ->
                zipFile.outputStream().use { output ->
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

            // 👇 Распаковываем APK из ZIP
            val apkFile = File(context.cacheDir, "update.apk")
            if (apkFile.exists()) apkFile.delete()

            ZipInputStream(zipFile.inputStream()).use { zipInput ->
                var entry = zipInput.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".apk", true)) {
                        FileOutputStream(apkFile).use { output ->
                            zipInput.copyTo(output)
                        }
                        break // Нашли APK — выходим
                    }
                    zipInput.closeEntry()
                    entry = zipInput.nextEntry
                }
            }

            zipFile.delete() // Удаляем временный ZIP

            if (apkFile.exists()) apkFile else null
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