package id.xms.xcai.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.xms.xcai.data.local.ConversationEntity
import id.xms.xcai.ui.components.ConversationItem
import id.xms.xcai.ui.viewmodel.AuthViewModel
import id.xms.xcai.ui.viewmodel.ChatViewModel
import id.xms.xcai.utils.BackupManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    chatViewModel: ChatViewModel,
    authViewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onConversationClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val conversationUiState by chatViewModel.conversationUiState.collectAsState()
    val authUiState by authViewModel.authUiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var conversationToDelete by remember { mutableStateOf<ConversationEntity?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var isBackupInProgress by remember { mutableStateOf(false) }

    // Load conversations
    LaunchedEffect(authUiState.user) {
        authUiState.user?.uid?.let { userId ->
            chatViewModel.loadConversations(userId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conversation History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isBackupInProgress = true
                                val backupManager = BackupManager(context)
                                val result = backupManager.createBackup()
                                isBackupInProgress = false

                                result.onSuccess {
                                    snackbarHostState.showSnackbar("Backup created successfully")
                                }.onFailure { error ->
                                    snackbarHostState.showSnackbar("Backup failed: ${error.message}")
                                }
                            }
                        },
                        enabled = !isBackupInProgress
                    ) {
                        if (isBackupInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.FileDownload, "Backup")
                        }
                    }

                    IconButton(onClick = { showRestoreDialog = true }) {
                        Icon(Icons.Default.FileUpload, "Restore")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (conversationUiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (conversationUiState.conversations.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "📝",
                        style = MaterialTheme.typography.displayLarge
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Text(
                        text = "No conversations yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = conversationUiState.conversations,
                        key = { it.id }
                    ) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            onClick = {
                                chatViewModel.loadChats(conversation.id)
                                onConversationClick(conversation.id)
                            },
                            onDelete = {
                                conversationToDelete = conversation
                            }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    conversationToDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = { Text("Delete Conversation") },
            text = { Text("Are you sure you want to delete this conversation?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        chatViewModel.deleteConversation(conversation)
                        conversationToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Restore dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Backup") },
            text = { Text("This will restore conversations from backup. Continue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreDialog = false
                        scope.launch {
                            val backupManager = BackupManager(context)
                            val result = backupManager.restoreBackup()

                            result.onSuccess {
                                snackbarHostState.showSnackbar("Backup restored successfully")
                                authUiState.user?.uid?.let { userId ->
                                    chatViewModel.loadConversations(userId)
                                }
                            }.onFailure { error ->
                                snackbarHostState.showSnackbar("Restore failed: ${error.message}")
                            }
                        }
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
