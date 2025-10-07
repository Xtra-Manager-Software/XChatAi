package id.xms.xcai.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import id.xms.xcai.data.local.ChatEntity
import id.xms.xcai.data.local.ConversationEntity
import id.xms.xcai.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentConversationId: Long? = null,
    val remainingRequests: Int = 20,
    val isLoadingCounter: Boolean = false
)

data class ConversationUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val isLoading: Boolean = false
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)
    private val firebaseDb = FirebaseDatabase.getInstance()

    private val _chatUiState = MutableStateFlow(ChatUiState())
    val chatUiState: StateFlow<ChatUiState> = _chatUiState.asStateFlow()

    private val _conversationUiState = MutableStateFlow(ConversationUiState())
    val conversationUiState: StateFlow<ConversationUiState> = _conversationUiState.asStateFlow()

    private var rateLimitListener: ValueEventListener? = null
    private var currentUserId: String? = null
    private var pollingJob: Job? = null

    // Setup real-time listener dengan logging detail
    fun setupRateLimitListener(userId: String) {
        Log.d("ChatViewModel", "=== SETUP LISTENER ===")
        Log.d("ChatViewModel", "User ID: $userId")

        currentUserId = userId
        rateLimitListener?.let {
            Log.d("ChatViewModel", "Removing previous listener")
            firebaseDb.getReference("rate_limits/$userId").removeEventListener(it)
        }

        // Stop previous polling
        pollingJob?.cancel()

        val rateLimitRef = firebaseDb.getReference("rate_limits/$userId")

        // Load initial value first
        loadRemainingRequests(userId)

        // Create new listener
        rateLimitListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("ChatViewModel", "=== LISTENER ON DATA CHANGE ===")
                Log.d("ChatViewModel", "Snapshot exists: ${snapshot.exists()}")
                Log.d("ChatViewModel", "Snapshot value: ${snapshot.value}")

                if (!snapshot.exists()) {
                    Log.d("ChatViewModel", "No data, setting to 20")
                    _chatUiState.value = _chatUiState.value.copy(
                        remainingRequests = 20,
                        isLoadingCounter = false
                    )
                    return
                }

                val currentTime = System.currentTimeMillis()
                val count = snapshot.child("count").getValue(Int::class.java) ?: 0
                val windowStart = snapshot.child("windowStart").getValue(Long::class.java) ?: 0L

                Log.d("ChatViewModel", "Count: $count")
                Log.d("ChatViewModel", "WindowStart: $windowStart")
                Log.d("ChatViewModel", "Current time: $currentTime")

                // Check if window expired
                val timeDiff = currentTime - windowStart
                val isExpired = timeDiff > 30 * 60 * 1000L

                Log.d("ChatViewModel", "Time diff: $timeDiff ms (${timeDiff/1000}s)")
                Log.d("ChatViewModel", "Is expired: $isExpired")

                val remaining = if (isExpired || windowStart == 0L) {
                    Log.d("ChatViewModel", "Window expired, returning 20")
                    20
                } else {
                    (20 - count).coerceAtLeast(0)
                }

                Log.d("ChatViewModel", "Calculated remaining: $remaining")

                _chatUiState.value = _chatUiState.value.copy(
                    remainingRequests = remaining,
                    isLoadingCounter = false
                )

                Log.d("ChatViewModel", "State updated: ${_chatUiState.value.remainingRequests}/20")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatViewModel", "=== LISTENER CANCELLED ===")
                Log.e("ChatViewModel", "Error: ${error.message}")
                Log.e("ChatViewModel", "Details: ${error.details}")

                _chatUiState.value = _chatUiState.value.copy(
                    remainingRequests = 20,
                    isLoadingCounter = false
                )
            }
        }

        // Attach listener
        Log.d("ChatViewModel", "Attaching listener to: rate_limits/$userId")
        rateLimitRef.addValueEventListener(rateLimitListener!!)

        // Start polling sebagai backup
        startPolling(userId)

        Log.d("ChatViewModel", "=== LISTENER SETUP COMPLETE ===")
    }

    // Polling sebagai backup jika listener fail
    private fun startPolling(userId: String) {
        Log.d("ChatViewModel", "Starting polling every 3 seconds")
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            delay(3000) // Delay initial
            while(isActive) {
                try {
                    Log.d("ChatViewModel", "Polling: Loading remaining requests")
                    loadRemainingRequests(userId)
                    delay(3000) // Poll setiap 3 detik
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Polling error: ${e.message}")
                }
            }
        }
    }

    private fun stopPolling() {
        Log.d("ChatViewModel", "Stopping polling")
        pollingJob?.cancel()
    }

    fun loadRemainingRequests(userId: String) {
        viewModelScope.launch {
            try {
                _chatUiState.value = _chatUiState.value.copy(isLoadingCounter = true)

                Log.d("ChatViewModel", "=== DIRECT LOAD ===")
                val remaining = repository.getServerRemainingRequests(userId)

                Log.d("ChatViewModel", "Direct load result: $remaining/20")

                _chatUiState.value = _chatUiState.value.copy(
                    remainingRequests = remaining,
                    isLoadingCounter = false
                )
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading rate limit: ${e.message}")
                e.printStackTrace()
                _chatUiState.value = _chatUiState.value.copy(
                    remainingRequests = 20,
                    isLoadingCounter = false
                )
            }
        }
    }

    fun loadConversations(userId: String) {
        viewModelScope.launch {
            _conversationUiState.value = _conversationUiState.value.copy(isLoading = true)
            repository.getConversationsByUser(userId).collect { conversations ->
                _conversationUiState.value = ConversationUiState(
                    conversations = conversations,
                    isLoading = false
                )
            }
        }

        // Setup listener (di luar coroutine)
        setupRateLimitListener(userId)
    }

    fun loadChats(conversationId: Long) {
        viewModelScope.launch {
            _chatUiState.value = _chatUiState.value.copy(
                currentConversationId = conversationId,
                isLoading = true
            )
            repository.getChatsByConversation(conversationId).collect { chats ->
                _chatUiState.value = _chatUiState.value.copy(
                    messages = chats,
                    isLoading = false
                )
            }
        }
    }

    fun createNewConversation(userId: String, firstMessage: String) {
        viewModelScope.launch {
            val conversationId = repository.createConversation(
                userId = userId,
                title = firstMessage.take(50)
            )
            loadChats(conversationId)
        }
    }

    fun sendMessage(userId: String, message: String) {
        viewModelScope.launch {
            _chatUiState.value = _chatUiState.value.copy(isLoading = true, error = null)

            var conversationId = _chatUiState.value.currentConversationId

            if (conversationId == null) {
                conversationId = repository.createConversation(
                    userId = userId,
                    title = message.take(50)
                )
                _chatUiState.value = _chatUiState.value.copy(currentConversationId = conversationId)
                loadChats(conversationId)
            }

            repository.insertChat(
                ChatEntity(
                    conversationId = conversationId,
                    message = message,
                    isUser = true
                )
            )

            val result = repository.sendMessageToGroq(conversationId, message, userId)

            result.onSuccess { aiResponse ->
                repository.insertChat(
                    ChatEntity(
                        conversationId = conversationId,
                        message = aiResponse,
                        isUser = false
                    )
                )

                repository.getConversationById(conversationId)?.let { conversation ->
                    repository.updateConversation(
                        conversation.copy(updatedAt = System.currentTimeMillis())
                    )
                }

                _chatUiState.value = _chatUiState.value.copy(isLoading = false)

                // Force reload
                loadRemainingRequests(userId)
            }.onFailure { exception ->
                _chatUiState.value = _chatUiState.value.copy(
                    isLoading = false,
                    error = exception.message ?: "Unknown error occurred"
                )

                loadRemainingRequests(userId)
            }
        }
    }

    fun deleteConversation(conversation: ConversationEntity) {
        viewModelScope.launch {
            repository.deleteConversation(conversation)
            if (_chatUiState.value.currentConversationId == conversation.id) {
                _chatUiState.value = _chatUiState.value.copy(
                    messages = emptyList(),
                    currentConversationId = null,
                    error = null,
                    isLoading = false
                )
            }
        }
    }

    fun clearError() {
        _chatUiState.value = _chatUiState.value.copy(error = null)
    }

    fun clearCurrentConversation() {
        _chatUiState.value = _chatUiState.value.copy(
            messages = emptyList(),
            currentConversationId = null,
            error = null,
            isLoading = false
        )
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("ChatViewModel", "=== ON CLEARED ===")

        stopPolling()

        rateLimitListener?.let { listener ->
            currentUserId?.let { userId ->
                Log.d("ChatViewModel", "Removing listener on cleanup")
                firebaseDb.getReference("rate_limits/$userId")
                    .removeEventListener(listener)
            }
        }
    }
}
