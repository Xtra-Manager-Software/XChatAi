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
    private var isUserTyping = false
    private var chatCollectionJob: Job? = null

    fun setupRateLimitListener(userId: String) {
        Log.d("ChatViewModel", "=== SETUP LISTENER ===")
        currentUserId = userId

        rateLimitListener?.let {
            firebaseDb.getReference("rate_limits/$userId").removeEventListener(it)
        }

        pollingJob?.cancel()

        val rateLimitRef = firebaseDb.getReference("rate_limits/$userId")
        loadRemainingRequests(userId)

        rateLimitListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
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

                val remaining = if (isExpired || windowStart == 0L) 20 else (20 - count).coerceAtLeast(0)

                _chatUiState.value = _chatUiState.value.copy(
                    remainingRequests = remaining,
                    isLoadingCounter = false
                )
            }

            override fun onCancelled(error: DatabaseError) {
                _chatUiState.value = _chatUiState.value.copy(
                    remainingRequests = 20,
                    isLoadingCounter = false
                )
            }
        }

        rateLimitRef.addValueEventListener(rateLimitListener!!)
        startPolling(userId)
    }

    private fun startPolling(userId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            delay(5000)
            while(isActive) {
                try {
                    if (!isUserTyping) {
                        loadRemainingRequests(userId)
                    }
                    delay(10000)
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Polling error: ${e.message}")
                }
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
    }

    fun setUserTyping(typing: Boolean) {
        isUserTyping = typing
    }

    fun loadRemainingRequests(userId: String) {
        viewModelScope.launch {
            try {
                _chatUiState.value = _chatUiState.value.copy(isLoadingCounter = true)
                val remaining = repository.getServerRemainingRequests(userId)
                _chatUiState.value = _chatUiState.value.copy(
                    remainingRequests = remaining,
                    isLoadingCounter = false
                )
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading rate limit: ${e.message}")
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
        Log.d("ChatViewModel", "=== LOAD CHATS ===")
        Log.d("ChatViewModel", "Loading conversation: $conversationId")

        // Cancel previous flow collection
        chatCollectionJob?.cancel()

        chatCollectionJob = viewModelScope.launch {
            _chatUiState.value = _chatUiState.value.copy(
                currentConversationId = conversationId,
                isLoading = true
            )

            try {
                repository.getChatsByConversation(conversationId).collect { chats ->
                    // Only update if we're still on the same conversation
                    if (_chatUiState.value.currentConversationId == conversationId) {
                        _chatUiState.value = _chatUiState.value.copy(
                            messages = chats,
                            isLoading = false
                        )
                        Log.d("ChatViewModel", "Messages loaded: ${chats.size}")
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading chats: ${e.message}")
                _chatUiState.value = _chatUiState.value.copy(isLoading = false)
            }
        }
    }

    fun sendMessage(userId: String, message: String) {
        viewModelScope.launch {
            Log.d("ChatViewModel", "=== SEND MESSAGE ===")
            Log.d("ChatViewModel", "Message: $message")
            Log.d("ChatViewModel", "Current conversation: ${_chatUiState.value.currentConversationId}")

            _chatUiState.value = _chatUiState.value.copy(isLoading = true, error = null)

            var conversationId = _chatUiState.value.currentConversationId

            if (conversationId == null) {
                Log.d("ChatViewModel", "Creating new conversation...")
                conversationId = repository.createConversation(
                    userId = userId,
                    title = message.take(50)
                )
                Log.d("ChatViewModel", "New conversation created: $conversationId")

                // Set conversation ID and start loading
                _chatUiState.value = _chatUiState.value.copy(currentConversationId = conversationId)
                loadChats(conversationId)
            }

            // Insert user message
            repository.insertChat(
                ChatEntity(
                    conversationId = conversationId,
                    message = message,
                    isUser = true
                )
            )

            // Call API
            val result = repository.sendMessageToGroq(conversationId, message, userId)

            result.onSuccess { aiResponse ->
                Log.d("ChatViewModel", "API success, inserting AI response")
                repository.insertChat(
                    ChatEntity(
                        conversationId = conversationId,
                        message = aiResponse,
                        isUser = false
                    )
                )

                // Update conversation timestamp
                repository.getConversationById(conversationId)?.let { conversation ->
                    repository.updateConversation(
                        conversation.copy(updatedAt = System.currentTimeMillis())
                    )
                }

                _chatUiState.value = _chatUiState.value.copy(isLoading = false)
                loadRemainingRequests(userId)
            }.onFailure { exception ->
                Log.e("ChatViewModel", "API failed: ${exception.message}")
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
                clearCurrentConversation()
            }
        }
    }

    fun renameConversation(conversation: ConversationEntity, newTitle: String) {
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "Renaming conversation ${conversation.id} to: $newTitle")
                repository.updateConversation(
                    conversation.copy(
                        title = newTitle.trim(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error renaming conversation: ${e.message}")
            }
        }
    }

    fun clearError() {
        _chatUiState.value = _chatUiState.value.copy(error = null)
    }

    fun clearCurrentConversation() {
        Log.d("ChatViewModel", "=== CLEAR CONVERSATION ===")

        // Cancel active flow collection
        chatCollectionJob?.cancel()
        chatCollectionJob = null

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
        chatCollectionJob?.cancel()

        rateLimitListener?.let { listener ->
            currentUserId?.let { userId ->
                firebaseDb.getReference("rate_limits/$userId")
                    .removeEventListener(listener)
            }
        }
    }
}
