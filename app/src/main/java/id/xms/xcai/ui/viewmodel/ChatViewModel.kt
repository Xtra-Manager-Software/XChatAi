package id.xms.xcai.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.xms.xcai.data.local.ChatEntity
import id.xms.xcai.data.local.ConversationEntity
import id.xms.xcai.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentConversationId: Long? = null
)

data class ConversationUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val isLoading: Boolean = false
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)

    private val _chatUiState = MutableStateFlow(ChatUiState())
    val chatUiState: StateFlow<ChatUiState> = _chatUiState.asStateFlow()

    private val _conversationUiState = MutableStateFlow(ConversationUiState())
    val conversationUiState: StateFlow<ConversationUiState> = _conversationUiState.asStateFlow()

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
            // FIX: Langsung load conversation yang baru dibuat
            loadChats(conversationId)
        }
    }

    fun sendMessage(userId: String, message: String) {
        viewModelScope.launch {
            _chatUiState.value = _chatUiState.value.copy(isLoading = true, error = null)

            var conversationId = _chatUiState.value.currentConversationId

            // Create new conversation if none exists
            if (conversationId == null) {
                conversationId = repository.createConversation(
                    userId = userId,
                    title = message.take(50)
                )
                // FIX: Set conversation ID immediately and start observing
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

            // Get AI response
            val result = repository.sendMessageToGroq(conversationId, message)

            result.onSuccess { aiResponse ->
                // Insert AI response
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
            }.onFailure { exception ->
                _chatUiState.value = _chatUiState.value.copy(
                    isLoading = false,
                    error = exception.message ?: "Unknown error occurred"
                )
            }
        }
    }

    fun deleteConversation(conversation: ConversationEntity) {
        viewModelScope.launch {
            repository.deleteConversation(conversation)
            if (_chatUiState.value.currentConversationId == conversation.id) {
                _chatUiState.value = ChatUiState()
            }
        }
    }

    fun clearError() {
        _chatUiState.value = _chatUiState.value.copy(error = null)
    }

    fun clearCurrentConversation() {
        _chatUiState.value = ChatUiState()
    }
}
