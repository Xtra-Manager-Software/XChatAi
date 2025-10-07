package id.xms.xcai.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import id.xms.xcai.data.local.ChatDatabase
import id.xms.xcai.data.local.ChatEntity
import id.xms.xcai.data.local.ConversationEntity
import id.xms.xcai.data.remote.GroqApiService
import id.xms.xcai.data.remote.GroqChatRequest
import id.xms.xcai.data.remote.Message
import id.xms.xcai.data.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class RateLimitData(
    @get:com.google.firebase.database.PropertyName("count")
    @set:com.google.firebase.database.PropertyName("count")
    var count: Int = 0,

    @get:com.google.firebase.database.PropertyName("windowStart")
    @set:com.google.firebase.database.PropertyName("windowStart")
    var windowStart: Long = 0L,

    @get:com.google.firebase.database.PropertyName("lastRequest")
    @set:com.google.firebase.database.PropertyName("lastRequest")
    var lastRequest: Long = 0L
) {
    constructor() : this(0, 0L, 0L)
}

class ChatRepository(context: Context) {

    private val database = ChatDatabase.getDatabase(context)
    private val chatDao = database.chatDao()
    private val conversationDao = database.conversationDao()
    private val groqApi = GroqApiService.create()
    private val firebaseDb = FirebaseDatabase.getInstance()
    private val preferencesManager = UserPreferences(context)

    private val MAX_HISTORY_MESSAGES = 15

    companion object {
        private const val MAX_REQUESTS = 20
        private const val TIME_WINDOW_MS = 30 * 60 * 1000L // 30 menit
    }

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

    fun getChatsByConversation(conversationId: Long): Flow<List<ChatEntity>> {
        return chatDao.getChatsByConversation(conversationId)
    }

    suspend fun insertChat(chat: ChatEntity): Long {
        return chatDao.insertChat(chat)
    }

    suspend fun deleteChatsInConversation(conversationId: Long) {
        chatDao.deleteChatsInConversation(conversationId)
    }

    suspend fun checkServerRateLimit(userId: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            suspendCoroutine { continuation ->
                val rateLimitRef = firebaseDb.getReference("rate_limits/$userId")

                rateLimitRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        val currentTime = System.currentTimeMillis()

                        val data = currentData.getValue(RateLimitData::class.java)
                            ?: RateLimitData()

                        Log.d("ChatRepository", "Transaction - Current time: $currentTime")
                        Log.d("ChatRepository", "Transaction - Window start: ${data.windowStart}")
                        Log.d("ChatRepository", "Transaction - Count: ${data.count}")

                        // Check if this is first request or window expired
                        val isFirstRequest = data.windowStart == 0L || data.count == 0
                        val timeDiff = currentTime - data.windowStart
                        val isExpired = timeDiff > TIME_WINDOW_MS && data.windowStart != 0L

                        if (isFirstRequest || isExpired) {
                            // Reset window - ini request pertama di window baru
                            Log.d("ChatRepository", "Starting new window")
                            data.count = 1  // Set ke 1, bukan 0!
                            data.windowStart = currentTime
                            data.lastRequest = currentTime
                            currentData.value = data
                            return Transaction.success(currentData)
                        }

                        // Check if limit exceeded
                        if (data.count >= MAX_REQUESTS) {
                            Log.d("ChatRepository", "Limit exceeded: ${data.count}/$MAX_REQUESTS")
                            return Transaction.abort()
                        }

                        // Increment count
                        data.count += 1
                        data.lastRequest = currentTime
                        currentData.value = data

                        Log.d("ChatRepository", "Count incremented to: ${data.count}")
                        return Transaction.success(currentData)
                    }

                    override fun onComplete(
                        error: com.google.firebase.database.DatabaseError?,
                        committed: Boolean,
                        dataSnapshot: com.google.firebase.database.DataSnapshot?
                    ) {
                        if (error != null) {
                            Log.e("ChatRepository", "Transaction error: ${error.message}")
                            continuation.resume(Pair(false, "Rate limit check failed: ${error.message}"))
                            return
                        }

                        if (!committed) {
                            val data = dataSnapshot?.getValue(RateLimitData::class.java)
                            val currentTime = System.currentTimeMillis()
                            val timeRemaining = TIME_WINDOW_MS - (currentTime - (data?.windowStart ?: 0L))
                            val minutesRemaining = (timeRemaining / 60000).toInt() + 1

                            Log.d("ChatRepository", "Transaction aborted - Rate limit reached")
                            Log.d("ChatRepository", "Minutes remaining: $minutesRemaining")

                            continuation.resume(
                                Pair(
                                    false,
                                    "Rate limit reached. Please wait $minutesRemaining minutes before sending more messages."
                                )
                            )
                        } else {
                            Log.d("ChatRepository", "Transaction committed successfully")
                            continuation.resume(Pair(true, ""))
                        }
                    }
                })
            }
        }

    suspend fun getServerRemainingRequests(userId: String): Int = withContext(Dispatchers.IO) {
        try {
            val snapshot = firebaseDb.getReference("rate_limits/$userId")
                .get()
                .await()

            if (!snapshot.exists()) {
                Log.d("ChatRepository", "No rate limit data exists, returning 20")
                return@withContext MAX_REQUESTS
            }

            val data = snapshot.getValue(RateLimitData::class.java)
            if (data == null) {
                Log.d("ChatRepository", "Failed to parse rate limit data, returning 20")
                return@withContext MAX_REQUESTS
            }

            val currentTime = System.currentTimeMillis()
            val timeDiff = currentTime - data.windowStart

            Log.d("ChatRepository", "=== Get Remaining Requests ===")
            Log.d("ChatRepository", "Current time: $currentTime")
            Log.d("ChatRepository", "Window start: ${data.windowStart}")
            Log.d("ChatRepository", "Time diff: $timeDiff ms (${timeDiff/1000}s)")
            Log.d("ChatRepository", "Count: ${data.count}")

            // Check if window expired OR invalid timestamp
            if (timeDiff > TIME_WINDOW_MS || data.windowStart == 0L || data.windowStart > currentTime) {
                Log.d("ChatRepository", "Window expired/invalid, returning 20")
                return@withContext MAX_REQUESTS
            }

            val remaining = (MAX_REQUESTS - data.count).coerceAtLeast(0)
            Log.d("ChatRepository", "Remaining requests: $remaining")

            return@withContext remaining
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error getting remaining: ${e.message}")
            e.printStackTrace()
            return@withContext MAX_REQUESTS
        }
    }

    suspend fun sendMessageToGroq(
        conversationId: Long,
        userMessage: String,
        userId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val (canProceed, errorMessage) = checkServerRateLimit(userId)
            if (!canProceed) {
                return@withContext Result.failure(Exception(errorMessage))
            }

            val apiKey = getApiKeyFromFirebase()
            val selectedModelId = preferencesManager.selectedModelId.first()
            val allHistory = chatDao.getChatsByConversationSync(conversationId)

            val limitedHistory = if (allHistory.size > MAX_HISTORY_MESSAGES) {
                allHistory.takeLast(MAX_HISTORY_MESSAGES)
            } else {
                allHistory
            }

            val messages = mutableListOf<Message>()

            messages.add(
                Message(
                    role = "system",
                    content = "You are a helpful AI assistant. Provide clear, accurate, and concise responses."
                )
            )

            limitedHistory.forEach { chat ->
                messages.add(
                    Message(
                        role = if (chat.isUser) "user" else "assistant",
                        content = chat.message
                    )
                )
            }

            messages.add(Message(role = "user", content = userMessage))

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

    suspend fun getSelectedModel(): String {
        return preferencesManager.selectedModelId.first()
    }

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

    suspend fun deleteAllConversationsByUser(userId: String) {
        withContext(Dispatchers.IO) {
            conversationDao.deleteAllConversationsByUser(userId)
        }
    }
}
