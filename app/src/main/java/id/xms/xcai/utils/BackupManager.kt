package id.xms.xcai.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import com.google.gson.Gson
import id.xms.xcai.data.local.ChatEntity
import id.xms.xcai.data.local.ConversationEntity
import id.xms.xcai.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import java.io.FileWriter

data class BackupData(
    val conversations: List<ConversationEntity>,
    val chats: List<ChatEntity>,
    val timestamp: Long = System.currentTimeMillis()
)

class BackupManager(private val context: Context) {

    private val gson = Gson()
    private val repository = ChatRepository(context)

    suspend fun createBackup(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val conversations = repository.getAllConversations()
            val chats = repository.getAllChats()

            val backupData = BackupData(
                conversations = conversations,
                chats = chats
            )

            // Use app-specific directory instead of external storage
            val backupDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ use app-specific directory
                File(context.getExternalFilesDir(null), "backup")
            } else {
                // Android 9- use traditional external storage
                File(
                    Environment.getExternalStorageDirectory(),
                    "XChatAi/backup"
                )
            }

            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            val backupFile = File(backupDir, "xchat_backup.json")

            FileWriter(backupFile).use { writer ->
                gson.toJson(backupData, writer)
            }

            Result.success(backupFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreBackup(): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Use app-specific directory
            val backupDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                File(context.getExternalFilesDir(null), "backup")
            } else {
                File(
                    Environment.getExternalStorageDirectory(),
                    "XChatAi/backup"
                )
            }

            val backupFile = File(backupDir, "xchat_backup.json")

            if (!backupFile.exists()) {
                return@withContext Result.failure(Exception("Backup file not found"))
            }

            val backupData = FileReader(backupFile).use { reader ->
                gson.fromJson(reader, BackupData::class.java)
            }

            repository.restoreData(backupData.conversations, backupData.chats)

            Result.success("Backup restored successfully")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
