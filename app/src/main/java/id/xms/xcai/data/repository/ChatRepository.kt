package id.xms.xcai.data.repository

import android.content.Context
import com.google.firebase.database.FirebaseDatabase
import id.xms.xcai.data.local.ChatDatabase
import id.xms.xcai.data.local.ChatEntity
import id.xms.xcai.data.local.ConversationEntity
import id.xms.xcai.data.remote.GroqApiService
import id.xms.xcai.data.remote.GroqChatRequest
import id.xms.xcai.data.remote.Message
import id.xms.xcai.data.preferences.UserPreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository(context: Context) {

    private val database = ChatDatabase.getDatabase(context)
    private val chatDao = database.chatDao()
    private val conversationDao = database.conversationDao()
    private val groqApi = GroqApiService.create()
    private val firebaseDb = FirebaseDatabase.getInstance()
    private val preferencesManager = UserPreferencesManager(context)

    // Conversation operations
    fun getConversationsByUser(userId: String): Flow<List<ConversationEntity>> {
        return conversationDao.getConversationsByUser(userId)
    }

    suspend fun createConversation(userId: String, title: String): Long {
        val conversation = ConversationEntity(
            title = title,
            userId = userId
        )
        return conversationDao.insertConversation(conversation)
    }

    suspend fun updateConversation(conversation: ConversationEntity) {
        conversationDao.updateConversation(conversation)
    }

    suspend fun deleteConversation(conversation: ConversationEntity) {
        conversationDao.deleteConversation(conversation)
    }

    suspend fun getConversationById(id: Long): ConversationEntity? {
        return conversationDao.getConversationById(id)
    }

    // Chat operations
    fun getChatsByConversation(conversationId: Long): Flow<List<ChatEntity>> {
        return chatDao.getChatsByConversation(conversationId)
    }

    suspend fun insertChat(chat: ChatEntity): Long {
        return chatDao.insertChat(chat)
    }

    suspend fun deleteChatsInConversation(conversationId: Long) {
        chatDao.deleteChatsInConversation(conversationId)
    }

    // Groq API call with selected model from preferences
    suspend fun sendMessageToGroq(
        conversationId: Long,
        userMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Get API key from Firebase
            val apiKey = getApiKeyFromFirebase()

            // Get selected model from preferences
            val selectedModelId = preferencesManager.selectedModelId.first()

            // Get conversation history
            val history = chatDao.getChatsByConversationSync(conversationId)

            // Build messages list with conversation history
            val messages = mutableListOf<Message>()

            // Add system message (optional, for better context)
            messages.add(
                Message(
                    role = "system",
                    content = "You are a helpful AI assistant. Provide clear, accurate, and concise responses."
                )
            )

            // Add conversation history
            history.forEach { chat ->
                messages.add(
                    Message(
                        role = if (chat.isUser) "user" else "assistant",
                        content = chat.message
                    )
                )
            }

            // Add current user message
            messages.add(Message(role = "user", content = userMessage))

            // Make API call with selected model
            val request = GroqChatRequest(
                model = selectedModelId,
                messages = messages,
                temperature = 0.7,
                maxTokens = 2048
            )

            val response = groqApi.sendMessage(
                authorization = "Bearer $apiKey",
                request = request
            )

            val assistantMessage = response.choices.firstOrNull()?.message?.content
                ?: throw Exception("No response from API")

            Result.success(assistantMessage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getApiKeyFromFirebase(): String = withContext(Dispatchers.IO) {
        try {
            val snapshot = firebaseDb.getReference("config/groq_api_key")
                .get()
                .await()
            snapshot.getValue(String::class.java)
                ?: throw Exception("API key not found in Firebase")
        } catch (e: Exception) {
            throw Exception("Failed to get API key: ${e.message}")
        }
    }

    // Get current selected model (useful for displaying in UI)
    suspend fun getSelectedModel(): String {
        return preferencesManager.selectedModelId.first()
    }

    // Backup and restore operations
    suspend fun getAllConversations(): List<ConversationEntity> {
        return conversationDao.getAllConversations()
    }

    suspend fun getAllChats(): List<ChatEntity> {
        return chatDao.getAllChats()
    }

    suspend fun restoreData(
        conversations: List<ConversationEntity>,
        chats: List<ChatEntity>
    ) {
        withContext(Dispatchers.IO) {
            conversations.forEach { conversation ->
                conversationDao.insertConversation(conversation)
            }
            chats.forEach { chat ->
                chatDao.insertChat(chat)
            }
        }
    }

    // Clear all conversations for a user
    suspend fun deleteAllConversationsByUser(userId: String) {
        withContext(Dispatchers.IO) {
            conversationDao.deleteAllConversationsByUser(userId)
        }
    }
}
