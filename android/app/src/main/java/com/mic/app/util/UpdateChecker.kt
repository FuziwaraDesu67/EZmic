package com.mic.app.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val releaseName: String,
    val releaseNotes: String,
    val apkUrl: String,
    val releasePageUrl: String
)

object UpdateChecker {
    const val CURRENT_VERSION = "v1.1.4"
    private const val REPO_OWNER = "FuziwaraDesu67"
    private const val REPO_NAME = "EZmic"
    private const val API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    fun getAppVersion(context: Context? = null): String {
        if (context != null) {
            try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val vName = pInfo.versionName
                if (!vName.isNullOrBlank()) {
                    return if (vName.startsWith("v", ignoreCase = true)) vName else "v$vName"
                }
            } catch (_: Exception) {}
        }
        return CURRENT_VERSION
    }

    suspend fun check(context: Context? = null): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val appVer = getAppVersion(context)
            val url = URL(API_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "EZmic-Android-Updater")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            if (connection.responseCode != 200) return@withContext null

            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)

            val tagName = root.optString("tag_name", "")
            val name = root.optString("name", tagName)
            val body = root.optString("body", "")
            val htmlUrl = root.optString("html_url", "https://github.com/$REPO_OWNER/$REPO_NAME/releases")

            var apkUrl = ""
            val assets = root.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            if (apkUrl.isEmpty()) {
                apkUrl = htmlUrl
            }

            val hasUpdate = isNewer(tagName, appVer)

            AppUpdateInfo(
                hasUpdate = hasUpdate,
                latestVersion = tagName,
                releaseName = name,
                releaseNotes = body,
                apkUrl = apkUrl,
                releasePageUrl = htmlUrl
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        if (latest.isBlank()) return false
        val cleanL = latest.trimStart('v', 'V').trim()
        val cleanC = current.trimStart('v', 'V').trim()
        if (cleanL.equals(cleanC, ignoreCase = true)) return false

        val partsL = cleanL.split(".").mapNotNull { it.toIntOrNull() }
        val partsC = cleanC.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(partsL.size, partsC.size)
        for (i in 0 until maxLen) {
            val numL = partsL.getOrElse(i) { 0 }
            val numC = partsC.getOrElse(i) { 0 }
            if (numL > numC) return true
            if (numL < numC) return false
        }
        return false
    }
}
