package com.mic.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class DownloadState {
    object Idle : DownloadState()
    data class Progress(val bytesRead: Long, val totalBytes: Long, val percent: Int) : DownloadState()
    data class Success(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

object InAppUpdateManager {

    /**
     * Downloads the APK file from [downloadUrl] into the app's cache directory
     * while emitting streaming download progress.
     */
    fun downloadApk(context: Context, downloadUrl: String, targetVersion: String): Flow<DownloadState> = flow {
        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) updatesDir.mkdirs()

        val cleanVer = targetVersion.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val apkFile = File(updatesDir, "EZmic_$cleanVer.apk")

        if (apkFile.exists()) {
            apkFile.delete()
        }

        var currentUrl = downloadUrl
        var connection: HttpURLConnection? = null
        var redirectCount = 0
        val maxRedirects = 6

        try {
            while (redirectCount < maxRedirects) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "EZmic-Android-InAppUpdater")
                    setRequestProperty("Accept", "application/octet-stream")
                }

                val code = connection.responseCode
                if (code in 301..308) {
                    val newUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (newUrl.isNullOrBlank()) {
                        emit(DownloadState.Error("Redirect location missing"))
                        return@flow
                    }
                    currentUrl = newUrl
                    redirectCount++
                    continue
                }

                if (code != 200) {
                    emit(DownloadState.Error("Server returned HTTP $code"))
                    return@flow
                }
                break
            }

            val conn = connection ?: run {
                emit(DownloadState.Error("Failed to initialize connection"))
                return@flow
            }

            val totalBytes = conn.contentLengthLong
            val inputStream = conn.inputStream
            val outputStream = FileOutputStream(apkFile)

            val buffer = ByteArray(16384)
            var bytesReadTotal = 0L
            var lastEmittedPercent = -1

            inputStream.use { input ->
                outputStream.use { output ->
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        output.write(buffer, 0, count)
                        bytesReadTotal += count

                        if (totalBytes > 0) {
                            val percent = ((bytesReadTotal * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (percent != lastEmittedPercent) {
                                lastEmittedPercent = percent
                                emit(DownloadState.Progress(bytesReadTotal, totalBytes, percent))
                            }
                        } else {
                            emit(DownloadState.Progress(bytesReadTotal, -1L, -1))
                        }
                    }
                    output.flush()
                }
            }

            // Verify minimum APK size and ZIP/APK magic header ('PK' = 0x50, 0x4B)
            if (apkFile.length() < 1024 * 1024 || !isValidApkHeader(apkFile)) {
                apkFile.delete()
                emit(DownloadState.Error("Downloaded APK package is invalid or corrupted"))
                return@flow
            }

            emit(DownloadState.Success(apkFile))

        } catch (e: Exception) {
            if (apkFile.exists()) apkFile.delete()
            emit(DownloadState.Error(e.localizedMessage ?: "Download failed"))
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Verifies that the downloaded file begins with ZIP magic bytes (0x50, 0x4B).
     */
    private fun isValidApkHeader(file: File): Boolean {
        try {
            FileInputStream(file).use { fis ->
                val b1 = fis.read()
                val b2 = fis.read()
                return b1 == 0x50 && b2 == 0x4B
            }
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Checks if permission to install unknown packages is allowed.
     */
    fun canInstallApks(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Opens system settings to allow installing unknown apps.
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            }
        }
    }

    /**
     * Triggers the native Android Package Installer for the specified APK file.
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) return false

        return try {
            val authority = "${context.packageName}.fileprovider"
            val contentUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
