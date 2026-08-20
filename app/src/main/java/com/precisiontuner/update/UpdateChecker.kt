package com.precisiontuner.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Metadata of the newest GitHub release of the app repository. */
data class RemoteRelease(
    val tagName: String,
    val htmlUrl: String,
    /** Direct download URL of the release APK asset, or null when absent. */
    val apkUrl: String?,
)

/**
 * Queries the GitHub releases API for the newest published release and
 * downloads the APK asset. Pure network/JSON/file IO — no UI state. The
 * repository must be public for the unauthenticated endpoint used here.
 */
object UpdateChecker {

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/yangbrea/precision-tuner/releases/latest"
    private const val TIMEOUT_MS = 8_000

    sealed interface Result {
        data class Success(val release: RemoteRelease) : Result
        data object Error : Result
    }

    suspend fun checkLatest(): Result = withContext(Dispatchers.IO) {
        try {
            val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "PrecisionTuner")
            try {
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    Result.Success(
                        RemoteRelease(
                            tagName = json.getString("tag_name"),
                            htmlUrl = json.getString("html_url"),
                            apkUrl = findApkUrl(json),
                        ),
                    )
                } else {
                    Result.Error
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Result.Error
        }
    }

    /**
     * Downloads [url] (an APK asset, redirects followed) into [destination],
     * reporting [onProgress] in 0f..1f. Returns the file on success or null on
     * any failure. Runs on the IO dispatcher; callers must check the result.
     */
    suspend fun downloadApk(
        url: String,
        destination: File,
        onProgress: (Float) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "PrecisionTuner")
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val total = connection.contentLengthLong
                val input = connection.inputStream
                destination.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
                if (destination.length() > 0) destination else null
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            destination.delete()
            null
        }
    }

    /** First `.apk` asset's browser_download_url, or null. */
    private fun findApkUrl(json: JSONObject): String? {
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name").endsWith(".apk")) {
                return asset.optString("browser_download_url").takeIf { it.isNotEmpty() }
            }
        }
        return null
    }
}
