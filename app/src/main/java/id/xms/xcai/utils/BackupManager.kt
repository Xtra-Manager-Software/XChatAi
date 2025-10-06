package id.xms.xcai.utils

import android.content.Context
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

            val backupDir = File(
                Environment.getExternalStorageDirectory(),
                "XChatAi/backup"
            )

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
            val backupFile = File(
                Environment.getExternalStorageDirectory(),
                "XChatAi/backup/xchat_backup.json"
            )

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
