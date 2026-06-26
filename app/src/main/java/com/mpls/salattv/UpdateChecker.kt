package com.mpls.salattv

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Over-the-air updates.
 *
 * Every push to the repo triggers a GitHub Actions build that publishes the new
 * APK as a GitHub Release, tagged with its integer versionCode. This checker asks
 * GitHub for the latest release; if its versionCode is higher than the one baked
 * into the running app, it downloads the APK and launches the system installer.
 *
 * The check + download are fully automatic. The final "Install" tap is shown by
 * Android's package installer (unless the device is provisioned as device-owner).
 */
object UpdateChecker {

    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/marhabamediterranean-png/marhaba-grill-tv/releases/latest"

    data class Update(val versionCode: Int, val apkUrl: String)

    /** Returns an Update if a newer release exists, else null. Never throws. */
    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "MarhabaTV/1.0")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val body = try {
                if (conn.responseCode !in 200..299) return@withContext null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "").filter { it.isDigit() }
            val remoteCode = tag.toIntOrNull() ?: return@withContext null
            if (remoteCode <= BuildConfig.VERSION_CODE) return@withContext null

            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url")
                    break
                }
            }
            apkUrl?.let { Update(remoteCode, it) }
        } catch (e: Exception) {
            null
        }
    }

    /** Downloads the APK to cache and launches the installer. Never throws. */
    suspend fun downloadAndInstall(context: Context, update: Update) {
        try {
            val apk = withContext(Dispatchers.IO) {
                val outFile = File(context.externalCacheDir ?: context.cacheDir, "update.apk")
                val conn = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 20000
                    readTimeout = 60000
                    setRequestProperty("User-Agent", "MarhabaTV/1.0")
                }
                conn.inputStream.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                conn.disconnect()
                outFile
            }

            val uri: Uri = FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Swallow — updating must never crash the display.
        }
    }
}
