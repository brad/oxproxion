package io.github.stardomains3.oxproxion

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object UpdateManager {

    private val client = OkHttpClient()

    sealed class UpdateResult {
        object NoUpdate : UpdateResult()
        data class UpdateAvailable(val version: String, val downloadUrl: String, val body: String) : UpdateResult()
        data class Error(val message: String) : UpdateResult()
    }

    fun isNewerVersion(current: String, latest: String): Boolean {
        val cleanCurrent = current.trim().removePrefix("v").removePrefix("V")
        val cleanLatest = latest.trim().removePrefix("v").removePrefix("V")

        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLength) {
            val currVal = currentParts.getOrElse(i) { 0 }
            val latVal = latestParts.getOrElse(i) { 0 }
            if (latVal > currVal) return true
            if (latVal < currVal) return false
        }
        return false
    }

    fun checkForUpdates(
        context: Context,
        isStartup: Boolean,
        onComplete: ((UpdateResult) -> Unit)? = null
    ) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/brad/oxproxion/releases/latest")
                    .header("User-Agent", "oxproxion-app")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorResult = UpdateResult.Error("Failed to connect: ${response.code}")
                        withContext(Dispatchers.Main) {
                            onComplete?.invoke(errorResult)
                            if (!isStartup) {
                                Toast.makeText(context, "Failed to check for updates: ${response.code}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        return@launch
                    }

                    val bodyStr = response.body?.string() ?: ""
                    if (bodyStr.isBlank()) {
                        val errorResult = UpdateResult.Error("Empty response from server")
                        withContext(Dispatchers.Main) {
                            onComplete?.invoke(errorResult)
                        }
                        return@launch
                    }

                    val json = JSONObject(bodyStr)
                    val tagName = json.optString("tag_name", "")
                    val releaseNotes = json.optString("body", "")
                    val assets = json.optJSONArray("assets")

                    var downloadUrl: String? = null
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                    }

                    if (tagName.isBlank() || downloadUrl.isNullOrBlank()) {
                        val errorResult = UpdateResult.Error("No valid APK found in release")
                        withContext(Dispatchers.Main) {
                            onComplete?.invoke(errorResult)
                        }
                        return@launch
                    }

                    val currentVersion = BuildConfig.VERSION_NAME
                    if (isNewerVersion(currentVersion, tagName)) {
                        withContext(Dispatchers.Main) {
                            val result = UpdateResult.UpdateAvailable(tagName, downloadUrl, releaseNotes)
                            onComplete?.invoke(result)
                            showUpdateDialog(context, tagName, downloadUrl, releaseNotes)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onComplete?.invoke(UpdateResult.NoUpdate)
                            if (!isStartup) {
                                Toast.makeText(context, "App is up to date (v$currentVersion)", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(UpdateResult.Error(e.message ?: "Unknown error"))
                    if (!isStartup) {
                        Toast.makeText(context, "Error checking for updates: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showUpdateDialog(
        context: Context,
        latestVersion: String,
        downloadUrl: String,
        releaseNotes: String
    ) {
        val message = if (releaseNotes.isNotBlank()) {
            "A new version ($latestVersion) is available.\n\nRelease Notes:\n$releaseNotes"
        } else {
            "A new version ($latestVersion) is available. Would you like to download and install it?"
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Update Available")
            .setMessage(message)
            .setPositiveButton("Download & Install") { _, _ ->
                startDownload(context, downloadUrl)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun startDownload(context: Context, downloadUrl: String) {
        val density = context.resources.displayMetrics.density
        val padding = (16 * density).toInt()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val messageView = TextView(context).apply {
            text = "Downloading update..."
            textSize = 16f
        }
        layout.addView(messageView)

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, (12 * density).toInt())
        }
        layout.addView(spacer)

        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
        }
        layout.addView(progressBar)

        val downloadDialog = MaterialAlertDialogBuilder(context)
            .setTitle("Downloading Update")
            .setView(layout)
            .setCancelable(false)
            .create()

        downloadDialog.show()

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val request = Request.Builder()
                    .url(downloadUrl)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Failed to download file: ${response.code}")

                    val body = response.body ?: throw IOException("Empty response body")
                    val contentLength = body.contentLength()
                    val inputStream = body.byteStream()
                    val apkFile = File(context.cacheDir, "oxproxion-update.apk")
                    val outputStream = FileOutputStream(apkFile)

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (contentLength > 0) {
                            val progress = ((totalBytesRead * 100) / contentLength).toInt()
                            withContext(Dispatchers.Main) {
                                progressBar.progress = progress
                                messageView.text = "Downloaded $progress%"
                            }
                        }
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

                    withContext(Dispatchers.Main) {
                        downloadDialog.dismiss()
                        promptInstall(context, apkFile)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    downloadDialog.dismiss()
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun promptInstall(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            MaterialAlertDialogBuilder(context)
                .setTitle("Permission Required")
                .setMessage("To install updates, you need to allow oxproxion to install unknown apps. Please enable the permission in settings.")
                .setPositiveButton("Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            val apkUri = FileProvider.getUriForFile(
                context,
                "io.github.stardomains3.oxproxion.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
