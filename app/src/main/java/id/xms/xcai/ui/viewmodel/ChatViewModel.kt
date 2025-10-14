package id.xms.xcai.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import id.xms.xcai.data.local.ChatEntity
import id.xms.xcai.data.local.ConversationEntity
import id.xms.xcai.data.model.ResponseMode
import id.xms.xcai.data.preferences.UserPreferences
import id.xms.xcai.data.repository.ChatRepository
import id.xms.xcai.data.repository.PremiumManager
import id.xms.xcai.data.repository.PremiumStatus
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
    val isLoadingCounter: Boolean = false,
    val streamingText: String = "",
    val isStreaming: Boolean = false,
    val isThinking: Boolean = false,
    val currentResponseMode: ResponseMode = ResponseMode.CHAT
)

data class ConversationUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val isLoading: Boolean = false
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)
    private val premiumManager = PremiumManager()
    private val firebaseDb = FirebaseDatabase.getInstance()
    private val preferencesManager = UserPreferences(application)

    private val _chatUiState = MutableStateFlow(ChatUiState())
    val chatUiState: StateFlow<ChatUiState> = _chatUiState.asStateFlow()

    private val _conversationUiState = MutableStateFlow(ConversationUiState())
    val conversationUiState: StateFlow<ConversationUiState> = _conversationUiState.asStateFlow()

    private val _premiumStatus = MutableStateFlow(PremiumStatus())
    val premiumStatus: StateFlow<PremiumStatus> = _premiumStatus.asStateFlow()

    private var rateLimitListener: ValueEventListener? = null
    private var currentUserId: String? = null
    private var pollingJob: Job? = null
    private var isUserTyping = false
    private var chatCollectionJob: Job? = null
    private var streamingJob: Job? = null
    private var premiumJob: Job? = null
    private var responseModeJob: Job? = null

    init {
        responseModeJob = viewModelScope.launch {
            preferencesManager.responseMode.collect { mode ->
                _chatUiState.value = _chatUiState.value.copy(currentResponseMode = mode)
                Log.d("ChatViewModel", "Response mode changed to: ${mode.displayName}")
            }
        }
    }

    fun setupRateLimitListener(userId: String) {
        Log.d("ChatViewModel", "=== SETUP LISTENER ===")
        currentUserId = userId

        rateLimitListener?.let {
            firebaseDb.getReference("rate_limits/$userId").removeEventListener(it)
        }

        pollingJob?.cancel()

        val userEmail = FirebaseAuth.getInstance().currentUser?.email

        if (userEmail != null) {
            viewModelScope.launch {
                try {
                    val status = premiumManager.checkPremiumStatus(userEmail)
                    _premiumStatus.value = status

                    val maxReq = premiumManager.getMaxRequests(status)
                    Log.d("ChatViewModel", "User is ${status.tier}, max requests: $maxReq")
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error checking premium status: ${e.message}")
                }
            }

            premiumJob?.cancel()
            premiumJob = viewModelScope.launch {
                try {
                    premiumManager.observePremiumStatus(userEmail).collect { status ->
                        _premiumStatus.value = status
                        Log.d("ChatViewModel", "Premium status updated: ${status.tier}")
                    }
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error observing premium status: ${e.message}")
                }
            }
        }

        val rateLimitRef = firebaseDb.getReference("rate_limits/$userId")
        loadRemainingRequests(userId)

        // ✅ IMPROVED: Wrapped in try-catch
        rateLimitListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val maxRequests = premiumManager.getMaxRequests(_premiumStatus.value)

                    if (!snapshot.exists()) {
                        _chatUiState.value = _chatUiState.value.copy(
                            remainingRequests = maxRequests,
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
                        maxRequests
                    } else {
                        (maxRequests - count).coerceAtLeast(0)
                    }

                    _chatUiState.value = _chatUiState.value.copy(
                        remainingRequests = remaining,
                        isLoadingCounter = false
                    )
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error in onDataChange: ${e.message}")
                    val maxRequests = premiumManager.getMaxRequests(_premiumStatus.value)
                    _chatUiState.value = _chatUiState.value.copy(
                        remainingRequests = maxRequests,
                        isLoadingCounter = false
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatViewModel", "Rate limit listener cancelled: ${error.message}")
                val maxRequests = premiumManager.getMaxRequests(_premiumStatus.value)
                _chatUiState.value = _chatUiState.value.copy(
                    remainingRequests = maxRequests,
                    isLoadingCounter = false
                )
            }
        }

        try {
            rateLimitRef.addValueEventListener(rateLimitListener!!)
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error adding rate limit listener: ${e.message}")
        }

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
                    delay(10000) // Continue polling despite error
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
                val maxRequests = premiumManager.getMaxRequests(_premiumStatus.value)
                val remaining = repository.getServerRemainingRequests(userId, maxRequests)
                _chatUiState.value = _chatUiState.value.copy(
                    remainingRequests = remaining,
                    isLoadingCounter = false
                )
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading rate limit: ${e.message}")
                val maxRequests = premiumManager.getMaxRequests(_premiumStatus.value)
                _chatUiState.value = _chatUiState.value.copy(
                    remainingRequests = maxRequests,
                    isLoadingCounter = false
                )
            }
        }
    }

    fun loadConversations(userId: String) {
        viewModelScope.launch {
            try {
                _conversationUiState.value = _conversationUiState.value.copy(isLoading = true)
                repository.getConversationsByUser(userId).collect { conversations ->
                    _conversationUiState.value = ConversationUiState(
                        conversations = conversations,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading conversations: ${e.message}")
                _conversationUiState.value = _conversationUiState.value.copy(isLoading = false)
            }
        }
        setupRateLimitListener(userId)
    }

    fun loadChats(conversationId: Long) {
        Log.d("ChatViewModel", "=== LOAD CHATS ===")
        Log.d("ChatViewModel", "Loading conversation: $conversationId")

        chatCollectionJob?.cancel()

        chatCollectionJob = viewModelScope.launch {
            _chatUiState.value = _chatUiState.value.copy(
                currentConversationId = conversationId,
                isLoading = true
            )

            try {
                repository.getChatsByConversation(conversationId).collect { chats ->
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
                _chatUiState.value = _chatUiState.value.copy(
                    isLoading = false,
                    error = "Failed to load conversation"
                )
            }
        }
    }

    // ✅ IMPROVED: Better error handling
    fun sendMessage(userId: String, message: String) {
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "=== SEND MESSAGE ===")
                Log.d("ChatViewModel", "Message: $message")
                Log.d("ChatViewModel", "Current conversation: ${_chatUiState.value.currentConversationId}")

                val responseMode = _chatUiState.value.currentResponseMode
                Log.d("ChatViewModel", "Using response mode: ${responseMode.displayName}")

                _chatUiState.value = _chatUiState.value.copy(
                    isLoading = true,
                    error = null,
                    isThinking = false
                )

                var conversationId = _chatUiState.value.currentConversationId

                if (conversationId == null) {
                    Log.d("ChatViewModel", "Creating new conversation...")
                    conversationId = repository.createConversation(
                        userId = userId,
                        title = message.take(50)
                    )
                    Log.d("ChatViewModel", "New conversation created: $conversationId")

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

                _chatUiState.value = _chatUiState.value.copy(isThinking = true)

                val maxRequests = premiumManager.getMaxRequests(_premiumStatus.value)

                val result = repository.sendMessageToGroq(
                    conversationId = conversationId,
                    userMessage = message,
                    userId = userId,
                    maxRequests = maxRequests,
                    systemPrompt = responseMode.systemPrompt
                )

                result.onSuccess { aiResponse ->
                    Log.d("ChatViewModel", "API success, starting typewriter effect")

                    _chatUiState.value = _chatUiState.value.copy(
                        isThinking = false,
                        isLoading = false
                    )

                    startTypewriterEffect(conversationId, aiResponse)
                    loadRemainingRequests(userId)

                }.onFailure { exception ->
                    Log.e("ChatViewModel", "API failed: ${exception.message}")

                    // ✅ Clean user-friendly error message
                    val userMessage = when {
                        exception.message?.contains("Rate limit", ignoreCase = true) == true -> {
                            exception.message ?: "Rate limit reached"
                        }
                        exception.message?.contains("network", ignoreCase = true) == true ||
                                exception.message?.contains("unable to resolve", ignoreCase = true) == true -> {
                            "Network error. Please check your connection."
                        }
                        exception.message?.contains("timeout", ignoreCase = true) == true -> {
                            "Request timed out. Please try again."
                        }
                        else -> {
                            "Unable to send message. Please try again."
                        }
                    }

                    _chatUiState.value = _chatUiState.value.copy(
                        isLoading = false,
                        isThinking = false,
                        error = userMessage
                    )

                    loadRemainingRequests(userId)
                }

            } catch (e: Exception) {
                // ✅ Catch any unexpected errors
                Log.e("ChatViewModel", "Unexpected error in sendMessage: ${e.message}", e)

                _chatUiState.value = _chatUiState.value.copy(
                    isLoading = false,
                    isThinking = false,
                    error = "An unexpected error occurred. Please try again."
                )

                loadRemainingRequests(userId)
            }
        }
    }

    private fun startTypewriterEffect(conversationId: Long, fullText: String) {
        streamingJob?.cancel()

        streamingJob = viewModelScope.launch {
            try {
                _chatUiState.value = _chatUiState.value.copy(
                    isStreaming = true,
                    streamingText = ""
                )

                val delayPerChar = 20L

                fullText.forEachIndexed { index, _ ->
                    if (isActive) {
                        _chatUiState.value = _chatUiState.value.copy(
                            streamingText = fullText.substring(0, index + 1)
                        )
                        delay(delayPerChar)
                    }
                }

                if (isActive) {
                    repository.insertChat(
                        ChatEntity(
                            conversationId = conversationId,
                            message = fullText,
                            isUser = false
                        )
                    )

                    repository.getConversationById(conversationId)?.let { conversation ->
                        repository.updateConversation(
                            conversation.copy(updatedAt = System.currentTimeMillis())
                        )
                    }

                    _chatUiState.value = _chatUiState.value.copy(
                        isStreaming = false,
                        streamingText = ""
                    )
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error in typewriter effect: ${e.message}")
                _chatUiState.value = _chatUiState.value.copy(
                    isStreaming = false,
                    streamingText = ""
                )
            }
        }
    }

    fun stopStreaming() {
        streamingJob?.cancel()
        _chatUiState.value = _chatUiState.value.copy(
            isStreaming = false,
            streamingText = ""
        )
    }

    fun deleteConversation(conversation: ConversationEntity) {
        viewModelScope.launch {
            try {
                repository.deleteConversation(conversation)
                if (_chatUiState.value.currentConversationId == conversation.id) {
                    clearCurrentConversation()
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error deleting conversation: ${e.message}")
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

        chatCollectionJob?.cancel()
        chatCollectionJob = null
        stopStreaming()

        _chatUiState.value = _chatUiState.value.copy(
            messages = emptyList(),
            currentConversationId = null,
            error = null,
            isLoading = false,
            isStreaming = false,
            streamingText = "",
            isThinking = false
        )
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("ChatViewModel", "=== ON CLEARED ===")

        try {
            stopPolling()
            chatCollectionJob?.cancel()
            streamingJob?.cancel()
            premiumJob?.cancel()
            responseModeJob?.cancel()

            rateLimitListener?.let { listener ->
                currentUserId?.let { userId ->
                    firebaseDb.getReference("rate_limits/$userId")
                        .removeEventListener(listener)
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error in onCleared: ${e.message}")
        }
    }
}
