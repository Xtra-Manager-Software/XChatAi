package id.xms.xcai

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import id.xms.xcai.utils.BackupWorker
import java.util.concurrent.TimeUnit

class XChatApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Schedule daily backup
        scheduleBackup()
    }

    private fun scheduleBackup() {
        val backupWorkRequest = PeriodicWorkRequestBuilder<BackupWorker>(
            1, TimeUnit.DAYS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ChatBackupWork",
            ExistingPeriodicWorkPolicy.KEEP,
            backupWorkRequest
        )
    }
}
