package id.xms.xcai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import id.xms.xcai.ui.components.DrawerContent
import id.xms.xcai.ui.screens.ChatScreen
import id.xms.xcai.ui.screens.LoginScreen
import id.xms.xcai.ui.screens.SettingsScreen
import id.xms.xcai.ui.theme.XChatAiTheme
import id.xms.xcai.ui.viewmodel.AuthViewModel
import id.xms.xcai.ui.viewmodel.ChatViewModel
import id.xms.xcai.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            XChatAiTheme {
                val authUiState by authViewModel.authUiState.collectAsState()
                val chatUiState by chatViewModel.chatUiState.collectAsState()
                val conversationUiState by chatViewModel.conversationUiState.collectAsState()
                val premiumStatus by chatViewModel.premiumStatus.collectAsState()

                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                var showSettings by remember { mutableStateOf(false) }

                LaunchedEffect(authUiState.user) {
                    authUiState.user?.uid?.let { userId ->
                        chatViewModel.loadConversations(userId)
                    }
                }

                if (authUiState.user != null) {
                    if (showSettings) {
                        SettingsScreen(
                            settingsViewModel = settingsViewModel,
                            onNavigateBack = { showSettings = false },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                DrawerContent(
                                    user = authUiState.user,
                                    conversations = conversationUiState.conversations,
                                    currentConversationId = chatUiState.currentConversationId,
                                    premiumStatus = premiumStatus,
                                    isLoading = conversationUiState.isLoading,
                                    onConversationClick = { conversationId ->
                                        chatViewModel.loadChats(conversationId)
                                        scope.launch { drawerState.close() }
                                    },
                                    onNewChat = {
                                        chatViewModel.clearCurrentConversation()
                                        scope.launch { drawerState.close() }
                                    },
                                    onDeleteConversation = { conversation ->
                                        chatViewModel.deleteConversation(conversation)
                                    },
                                    onRenameConversation = { conversation, newTitle ->
                                        chatViewModel.renameConversation(conversation, newTitle)
                                    },
                                    onSettingsClick = {
                                        showSettings = true
                                        scope.launch { drawerState.close() }
                                    },
                                    onLogout = {
                                        authViewModel.signOut()
                                    }
                                )
                            }
                        ) {
                            ChatScreen(
                                chatViewModel = chatViewModel,
                                authViewModel = authViewModel,
                                onOpenDrawer = {
                                    scope.launch { drawerState.open() }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else {
                    LoginScreen(
                        authViewModel = authViewModel,
                        onLoginSuccess = { },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
