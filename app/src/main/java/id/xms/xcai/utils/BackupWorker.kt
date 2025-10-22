package id.xms.xcai.utils

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val backupManager = BackupManager(applicationContext)
            val result = backupManager.createBackup()

            if (result.isSuccess) {
                Log.d("BackupWorker", "Backup created successfully")
                Result.success()
            } else {
                Log.e("BackupWorker", "Backup failed: ${result.exceptionOrNull()}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("BackupWorker", "Backup failed with exception", e)
            Result.failure()
        }
    }
}
