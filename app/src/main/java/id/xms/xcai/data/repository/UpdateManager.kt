package id.xms.xcai.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

data class AppUpdateInfo(
    val latestVersion: String = "",
    val minimumVersion: String = "",
    val downloadUrl: String = "",
    val forceUpdate: Boolean = false,
    val updateTitle: String = "Update Available",
    val updateMessage: String = "A new version is available.",
    val releaseNotes: List<String> = emptyList()
)

class UpdateManager(private val context: Context) {

    private val firebaseDb = FirebaseDatabase.getInstance()

    companion object {
        private const val TAG = "UpdateManager"
    }

    /**
     * Get current app version from build.gradle
     */
    fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "0.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Error getting app version: ${e.message}")
            "0.0.0"
        }
    }

    /**
     * Fetch update info from Firebase
     */
    suspend fun checkForUpdates(): Result<AppUpdateInfo> {
        return try {
            Log.d(TAG, "=== CHECK FOR UPDATES ===")
            Log.d(TAG, "Current version: ${getCurrentVersion()}")

            val snapshot = firebaseDb.getReference("config/app_update")
                .get()
                .await()

            if (!snapshot.exists()) {
                Log.d(TAG, "No update config found in Firebase")
                return Result.success(AppUpdateInfo())
            }

            val latestVersion = snapshot.child("latest_version").getValue(String::class.java) ?: ""
            val minimumVersion =
                snapshot.child("minimum_version").getValue(String::class.java) ?: ""
            val downloadUrl = snapshot.child("download_url").getValue(String::class.java) ?: ""
            val forceUpdate = snapshot.child("force_update").getValue(Boolean::class.java) ?: false
            val updateTitle =
                snapshot.child("update_title").getValue(String::class.java) ?: "Update Available"
            val updateMessage = snapshot.child("update_message").getValue(String::class.java)
                ?: "A new version is available."

            val releaseNotes = mutableListOf<String>()
            snapshot.child("release_notes").children.forEach { noteSnapshot ->
                noteSnapshot.getValue(String::class.java)?.let { releaseNotes.add(it) }
            }

            val updateInfo = AppUpdateInfo(
                latestVersion = latestVersion,
                minimumVersion = minimumVersion,
                downloadUrl = downloadUrl,
                forceUpdate = forceUpdate,
                updateTitle = updateTitle,
                updateMessage = updateMessage,
                releaseNotes = releaseNotes
            )

            Log.d(TAG, "Latest version: $latestVersion")
            Log.d(TAG, "Minimum version: $minimumVersion")
            Log.d(TAG, "Force update: $forceUpdate")
            Log.d(TAG, "Download URL: $downloadUrl")

            Result.success(updateInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Check if update is required
     */
    fun isUpdateRequired(updateInfo: AppUpdateInfo): Boolean {
        val currentVersion = getCurrentVersion()
        val latestVersion = updateInfo.latestVersion
        val minimumVersion = updateInfo.minimumVersion

        Log.d(TAG, "=== UPDATE CHECK ===")
        Log.d(TAG, "Current: $currentVersion")
        Log.d(TAG, "Latest: $latestVersion")
        Log.d(TAG, "Minimum: $minimumVersion")

        // If force update is enabled and current version < latest version
        if (updateInfo.forceUpdate && compareVersions(currentVersion, latestVersion) < 0) {
            Log.d(TAG, "Force update required!")
            return true
        }

        // If current version < minimum supported version
        if (compareVersions(currentVersion, minimumVersion) < 0) {
            Log.d(TAG, "App version below minimum required!")
            return true
        }

        Log.d(TAG, "No update required")
        return false
    }

    /**
     * Check if update is available (optional update)
     */
    fun isUpdateAvailable(updateInfo: AppUpdateInfo): Boolean {
        val currentVersion = getCurrentVersion()
        val latestVersion = updateInfo.latestVersion

        return compareVersions(currentVersion, latestVersion) < 0 && !updateInfo.forceUpdate
    }

    /**
     * Compare two version strings (e.g., "1.0.0" vs "1.2.3")
     * Returns: -1 if v1 < v2, 0 if equal, 1 if v1 > v2
     */
    private fun compareVersions(v1: String, v2: String): Int {
        if (v1 == v2) return 0

        // Split and clean versions
        val parts1 = v1.trim().split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.trim().split(".").map { it.toIntOrNull() ?: 0 }

        // Get max length to compare all parts
        val maxLength = maxOf(parts1.size, parts2.size)

        // Compare each part
        for (i in 0 until maxLength) {
            val part1 = parts1.getOrNull(i) ?: 0
            val part2 = parts2.getOrNull(i) ?: 0

            when {
                part1 < part2 -> return -1
                part1 > part2 -> return 1
            }
        }

        return 0
    }
}