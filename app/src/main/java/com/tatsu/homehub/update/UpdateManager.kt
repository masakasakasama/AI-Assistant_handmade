package com.tatsu.homehub.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.tatsu.homehub.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val releaseUrl: String,
    val sizeBytes: Long? = null
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
            var sizeBytes: Long? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    sizeBytes = asset.optLong("size").takeIf { it > 0 }
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
                    releaseUrl = json.optString("html_url"),
                    sizeBytes = sizeBytes
                )
            }
        }
    }

    suspend fun downloadAndOpenInstaller(info: UpdateInfo, onProgress: (DownloadProgress) -> Unit = {}): Result<Unit> =
        withContext(Dispatchers.IO) {
            val downloadContext = coroutineContext
            runCatching {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !context.packageManager.canRequestPackageInstalls()
                ) {
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.packageName)
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    error("提供元不明アプリのインストール許可後、更新を再実行してください")
                }

                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val apk = File(dir, "tatsu-home-" + info.version + ".apk")
                val partial = File(dir, apk.name + ".part")
                // Updates are serialized by the ViewModel; discard obsolete cached installers.
                dir.listFiles()?.filter { it != apk }?.forEach { it.delete() }

                val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "TatsuHome/" + BuildConfig.VERSION_NAME)
                }

                try {
                    val code = connection.responseCode
                    if (code != 200) error("APK download HTTP " + code)
                    val headerSize = connection.contentLengthLong.takeIf { it > 0 }
                    if (headerSize != null && info.sizeBytes != null && headerSize != info.sizeBytes) {
                        error("APKのサイズが一致しません。もう一度更新確認してください")
                    }
                    val total = info.sizeBytes ?: headerSize
                    connection.inputStream.use { input ->
                        partial.outputStream().use { output ->
                            copyDownload(input, output, total, onProgress) { downloadContext.ensureActive() }
                        }
                    }
                    if (!partial.renameTo(apk)) error("更新ファイルを保存できませんでした")
                } finally {
                    connection.disconnect()
                    partial.delete()
                }



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
            }.onFailure { if (it is CancellationException) throw it }
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
