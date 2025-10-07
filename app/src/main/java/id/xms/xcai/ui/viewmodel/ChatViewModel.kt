package id.xms.xcai.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    private var isUserTyping = false // Track typing state

    fun setupRateLimitListener(userId: String) {
        Log.d("ChatViewModel", "=== SETUP LISTENER ===")
        Log.d("ChatViewModel", "User ID: $userId")

        currentUserId = userId

        rateLimitListener?.let {
            Log.d("ChatViewModel", "Removing previous listener")
            firebaseDb.getReference("rate_limits/$userId").removeEventListener(it)
        }

        pollingJob?.cancel()

        val rateLimitRef = firebaseDb.getReference("rate_limits/$userId")

        loadRemainingRequests(userId)

        rateLimitListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("ChatViewModel", "=== LISTENER ON DATA CHANGE ===")
                Log.d("ChatViewModel", "Snapshot exists: ${snapshot.exists()}")

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

                val timeDiff = currentTime - windowStart
                val isExpired = timeDiff > 30 * 60 * 1000L

                val remaining = if (isExpired || windowStart == 0L) {
                    20
                } else {
                    (20 - count).coerceAtLeast(0)
                }

                _chatUiState.value = _chatUiState.value.copy(
                    remainingRequests = remaining,
                    isLoadingCounter = false
                )

                Log.d("ChatViewModel", "State updated: ${_chatUiState.value.remainingRequests}/20")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatViewModel", "=== LISTENER CANCELLED ===")
                Log.e("ChatViewModel", "Error: ${error.message}")

                _chatUiState.value = _chatUiState.value.copy(
                    remainingRequests = 20,
                    isLoadingCounter = false
                )
            }
        }

        Log.d("ChatViewModel", "Attaching listener to: rate_limits/$userId")
        rateLimitRef.addValueEventListener(rateLimitListener!!)

        // Start polling with typing detection
        startPolling(userId)

        Log.d("ChatViewModel", "=== LISTENER SETUP COMPLETE ===")
    }

    // Polling with typing detection
    private fun startPolling(userId: String) {
        Log.d("ChatViewModel", "Starting smart polling")
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            delay(5000) // Initial delay
            while(isActive) {
                try {
                    // Only poll if user is NOT typing
                    if (!isUserTyping) {
                        Log.d("ChatViewModel", "Polling: Loading remaining requests")
                        loadRemainingRequests(userId)
                    } else {
                        Log.d("ChatViewModel", "Polling: Skipped (user typing)")
                    }
                    delay(10000) // Poll every 10 seconds (lebih lambat)
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

    // Call this when user starts typing
    fun setUserTyping(typing: Boolean) {
        isUserTyping = typing
        Log.d("ChatViewModel", "User typing state: $typing")
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
