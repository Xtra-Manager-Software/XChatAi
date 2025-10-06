package id.xms.xcai.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import id.xms.xcai.ui.screens.ChatScreen
import id.xms.xcai.ui.screens.HistoryScreen
import id.xms.xcai.ui.screens.LoginScreen
import id.xms.xcai.ui.theme.XChatAiTheme
import id.xms.xcai.ui.viewmodel.AuthViewModel
import id.xms.xcai.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            XChatAiTheme {
                XChatAiApp(
                    authViewModel = authViewModel,
                    chatViewModel = chatViewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XChatAiApp(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel
) {
    val authUiState by authViewModel.authUiState.collectAsState()
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val startDestination = if (authUiState.user != null) "chat" else "login"

    if (authUiState.user != null) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                DrawerContent(
                    authViewModel = authViewModel,
                    chatViewModel = chatViewModel,
                    navController = navController,
                    onCloseDrawer = {
                        scope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            NavigationHost(
                navController = navController,
                authViewModel = authViewModel,
                chatViewModel = chatViewModel,
                startDestination = startDestination,
                onOpenDrawer = {
                    scope.launch { drawerState.open() }
                }
            )
        }
    } else {
        NavigationHost(
            navController = navController,
            authViewModel = authViewModel,
            chatViewModel = chatViewModel,
            startDestination = startDestination,
            onOpenDrawer = {}
        )
    }
}

@Composable
fun DrawerContent(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    navController: NavHostController,
    onCloseDrawer: () -> Unit
) {
    val authUiState by authViewModel.authUiState.collectAsState()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // User info
            authUiState.user?.let { user ->
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = {
                        Column {
                            Text(
                                text = user.displayName ?: "User",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = user.email ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    selected = false,
                    onClick = { }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // New Chat
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                label = { Text("New Chat") },
                selected = currentRoute == "chat",
                onClick = {
                    chatViewModel.clearCurrentConversation()
                    navController.navigate("chat") {
                        popUpTo("chat") { inclusive = true }
                    }
                    onCloseDrawer()
                },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Sign Out
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
                label = { Text("Sign Out") },
                selected = false,
                onClick = {
                    authViewModel.signOut()
                    chatViewModel.clearCurrentConversation()
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                    onCloseDrawer()
                }
            )
        }
    }
}

@Composable
fun NavigationHost(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    startDestination: String,
    onOpenDrawer: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("login") {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate("chat") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("chat") {
            ChatScreen(
                chatViewModel = chatViewModel,
                authViewModel = authViewModel,
                onNavigateToHistory = {
                    navController.navigate("history")
                },
                onOpenDrawer = onOpenDrawer
            )
        }

        composable("history") {
            HistoryScreen(
                chatViewModel = chatViewModel,
                authViewModel = authViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onConversationClick = { conversationId ->
                    navController.popBackStack()
                }
            )
        }
    }
}
