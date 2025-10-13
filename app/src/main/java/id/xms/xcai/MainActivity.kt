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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import id.xms.xcai.data.preferences.UserPreferences
import id.xms.xcai.ui.components.DrawerContent
import id.xms.xcai.ui.screens.ChatScreen
import id.xms.xcai.ui.screens.LoginScreen
import id.xms.xcai.ui.screens.OnboardingScreen
import id.xms.xcai.ui.screens.SettingsScreen
import id.xms.xcai.ui.theme.XChatAiTheme
import id.xms.xcai.ui.viewmodel.AuthViewModel
import id.xms.xcai.ui.viewmodel.ChatViewModel
import id.xms.xcai.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
                AppContent(
                    authViewModel = authViewModel,
                    chatViewModel = chatViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}

@Composable
private fun AppContent(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val preferencesManager = remember { UserPreferences(context) }

    // States
    val authUiState by authViewModel.authUiState.collectAsState()
    val chatUiState by chatViewModel.chatUiState.collectAsState()
    val conversationUiState by chatViewModel.conversationUiState.collectAsState()
    val premiumStatus by chatViewModel.premiumStatus.collectAsState()
    val onboardingCompleted by preferencesManager.onboardingCompleted.collectAsState(initial = true)

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Screen state management
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Loading) }

    // Load conversations when user logs in
    LaunchedEffect(authUiState.user) {
        authUiState.user?.uid?.let { userId ->
            chatViewModel.loadConversations(userId)
        }
    }

    // Determine which screen to show
    LaunchedEffect(authUiState.user, onboardingCompleted) {
        currentScreen = when {
            authUiState.user == null -> Screen.Login
            !onboardingCompleted -> Screen.Onboarding
            else -> Screen.Chat
        }
    }

    // Render current screen
    when (currentScreen) {
        Screen.Loading -> {
            // Optional: Show loading screen while checking auth state
            // Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            //     CircularProgressIndicator()
            // }
        }

        Screen.Login -> {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = {
                    currentScreen = if (onboardingCompleted) {
                        Screen.Chat
                    } else {
                        Screen.Onboarding
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Screen.Onboarding -> {
            OnboardingScreen(
                onComplete = {
                    // Save onboarding completed
                    CoroutineScope(Dispatchers.IO).launch {
                        preferencesManager.setOnboardingCompleted(true)
                    }
                    currentScreen = Screen.Chat
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Screen.Chat -> {
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
                            currentScreen = Screen.Settings
                            scope.launch { drawerState.close() }
                        },
                        onLogout = {
                            authViewModel.signOut()
                            chatViewModel.clearCurrentConversation()
                            currentScreen = Screen.Login
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

        Screen.Settings -> {
            SettingsScreen(
                settingsViewModel = settingsViewModel,
                onNavigateBack = {
                    currentScreen = Screen.Chat
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// Screen enum for better state management
private enum class Screen {
    Loading,
    Login,
    Onboarding,
    Chat,
    Settings
}
