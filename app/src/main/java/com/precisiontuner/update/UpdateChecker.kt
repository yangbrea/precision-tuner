package com.precisiontuner.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Metadata of the newest GitHub release of the app repository. */
data class RemoteRelease(
    val tagName: String,
    val htmlUrl: String,
)

/**
 * Queries the GitHub releases API for the newest published release. Pure
 * network/JSON — no UI state. The repository must be public for the
 * unauthenticated endpoint used here.
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
}
