package com.tatsu.homehub.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.tatsu.homehub.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val releaseUrl: String
)

class UpdateManager(private val context: Context) {
    suspend fun checkLatest(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "TatsuHome/" + BuildConfig.VERSION_NAME)
            }

            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()

            if (code == 404) return@runCatching null
            if (code !in 200..299) error("GitHub Release HTTP " + code)

            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v")
            val assets = json.optJSONArray("assets") ?: return@runCatching null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
            if (apkUrl.isNullOrBlank()) return@runCatching null

            if (compareVersions(tag, BuildConfig.VERSION_NAME) <= 0) {
                null
            } else {
                UpdateInfo(
                    version = tag,
                    apkUrl = apkUrl,
                    releaseUrl = json.optString("html_url")
                )
            }
        }
    }

    suspend fun downloadAndOpenInstaller(info: UpdateInfo): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val apk = File(dir, "tatsu-home-" + info.version + ".apk")

                val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "TatsuHome/" + BuildConfig.VERSION_NAME)
                }

                val code = connection.responseCode
                if (code !in 200..299) {
                    connection.disconnect()
                    error("APK download HTTP " + code)
                }

                connection.inputStream.use { input ->
                    apk.outputStream().use { output -> input.copyTo(output) }
                }
                connection.disconnect()

                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".files",
                    apk
                )
                val install = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(install)
            }
        }

    private fun compareVersions(a: String, b: String): Int {
        val av = a.split(".").map { it.toIntOrNull() ?: 0 }
        val bv = b.split(".").map { it.toIntOrNull() ?: 0 }
        val max = maxOf(av.size, bv.size)
        for (i in 0 until max) {
            val x = av.getOrElse(i) { 0 }
            val y = bv.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    companion object {
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/masakasakasama/AI-Assistant_handmade/releases/latest"
    }
}
