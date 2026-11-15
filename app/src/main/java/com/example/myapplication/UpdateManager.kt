package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

object UpdateManager {

    private const val BUCKET_URL = "https://myqchelper.oss-cn-hongkong.aliyuncs.com"

    data class UpdateInfo(val version: String, val downloadUrl: String)

    sealed interface CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult
        data object UpToDate : CheckResult
        data class Error(val cause: Exception) : CheckResult
    }

    suspend fun checkForUpdate(currentVersion: String): CheckResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val activeConnection = openConnection("$BUCKET_URL/?list-type=2")
            connection = activeConnection
            activeConnection.requestMethod = "GET"
            activeConnection.connectTimeout = 15000
            activeConnection.readTimeout = 15000
            if (activeConnection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${activeConnection.responseCode}")
            }
            val xml = activeConnection.inputStream.bufferedReader().use { it.readText() }
            val latest = findLatestUpdate(xml, currentVersion)
            if (latest == null) CheckResult.UpToDate else CheckResult.Available(latest)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            CheckResult.Error(e)
        } finally {
            connection?.disconnect()
        }
    }

    internal fun findLatestUpdate(xml: String, currentVersion: String): UpdateInfo? {
        val regex = Regex("""<Key>([^<]*Punta_destroyer_v(\d+(?:\.\d+)*)[^<]*\.zip)</Key>""")
        val latest = regex.findAll(xml)
            .map { it.groupValues[1] to it.groupValues[2] }
            .distinct()
            .maxWithOrNull { a, b -> compareVersions(a.second, b.second) }
            ?: return null
        if (!isNewerVersion(latest.second, currentVersion)) return null
        val encodedPath = java.net.URLEncoder.encode(latest.first, "UTF-8")
            .replace("+", "%20")
            .replace("%2F", "/")
        return UpdateInfo(latest.second, "$BUCKET_URL/$encodedPath")
    }

    private fun openConnection(url: String): HttpURLConnection {
        val proxy = ProxySelector.getDefault()?.select(URI(url))?.firstOrNull()
        return if (proxy != null && proxy.type() != Proxy.Type.DIRECT) {
            URL(url).openConnection(proxy) as HttpURLConnection
        } else {
            URL(url).openConnection() as HttpURLConnection
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

    private fun isNewerVersion(latest: String, current: String): Boolean =
        compareVersions(latest, current) > 0

    suspend fun downloadAndExtractApk(
        context: Context,
        url: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val zipFile = File(context.cacheDir, "update.zip")
        val apkFile = File(context.cacheDir, "update.apk")
        var connection: HttpURLConnection? = null
        try {
            val activeConnection = openConnection(url)
            connection = activeConnection
            zipFile.delete()
            apkFile.delete()
            activeConnection.instanceFollowRedirects = true
            activeConnection.connectTimeout = 20000
            activeConnection.readTimeout = 20000
            activeConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MyQcHelper/1.0")
            activeConnection.connect()
            if (activeConnection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${activeConnection.responseCode}")
            }

            val totalSize = activeConnection.contentLengthLong
            var downloadedSize = 0L
            activeConnection.inputStream.use { input ->
                zipFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        withContext(Dispatchers.Main) {
                            onProgress(downloadedSize, totalSize)
                        }
                    }
                }
            }

            ZipInputStream(zipFile.inputStream()).use { zipInput ->
                var entry = zipInput.nextEntry
                while (entry != null) {
                    coroutineContext.ensureActive()
                    if (!entry.isDirectory && entry.name.endsWith(".apk", true)) {
                        FileOutputStream(apkFile).use { output -> zipInput.copyTo(output) }
                        break
                    }
                    zipInput.closeEntry()
                    entry = zipInput.nextEntry
                }
            }
            if (apkFile.exists() && apkFile.length() > 0L) apkFile else null
        } catch (e: kotlinx.coroutines.CancellationException) {
            apkFile.delete()
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            apkFile.delete()
            null
        } finally {
            zipFile.delete()
            connection?.disconnect()
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
