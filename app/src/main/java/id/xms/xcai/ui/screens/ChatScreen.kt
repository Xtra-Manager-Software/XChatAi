package id.xms.xcai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.xms.xcai.ui.components.MessageItem
import id.xms.xcai.ui.components.ThinkingIndicator
import id.xms.xcai.ui.viewmodel.AuthViewModel
import id.xms.xcai.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel,
    authViewModel: AuthViewModel,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chatUiState by chatViewModel.chatUiState.collectAsState()
    val authUiState by authViewModel.authUiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var messageText by remember { mutableStateOf("") }
    var showRateLimitDialog by remember { mutableStateOf(false) }
    var showLowQuotaWarning by remember { mutableStateOf(false) }
    var rateLimitMessage by remember { mutableStateOf("") }

    // Show alert saat pertama buka app jika quota habis
    LaunchedEffect(chatUiState.remainingRequests, chatUiState.isLoadingCounter) {
        if (!chatUiState.isLoadingCounter && chatUiState.remainingRequests == 0) {
            rateLimitMessage = "You've reached the maximum of 20 requests per 30 minutes. Please wait before sending more messages."
            showRateLimitDialog = true
        }
    }

    // Auto scroll to bottom when new messages arrive
    LaunchedEffect(chatUiState.messages.size) {
        if (chatUiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(chatUiState.messages.size - 1)
        }
    }

    // Show error snackbar or dialog
    LaunchedEffect(chatUiState.error) {
        chatUiState.error?.let { error ->
            if (error.contains("Rate limit", ignoreCase = true)) {
                rateLimitMessage = error
                showRateLimitDialog = true
            } else {
                snackbarHostState.showSnackbar(error)
            }
            chatViewModel.clearError()
        }
    }

    // Show low quota warning when <= 5 requests left
    LaunchedEffect(chatUiState.remainingRequests) {
        if (chatUiState.remainingRequests in 1..5 && !showLowQuotaWarning && !chatUiState.isLoadingCounter) {
            showLowQuotaWarning = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("XChatAi")

                            // Manual refresh button
                            IconButton(
                                onClick = {
                                    authUiState.user?.uid?.let { userId ->
                                        chatViewModel.loadRemainingRequests(userId)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh counter",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (chatUiState.remainingRequests <= 5 && !chatUiState.isLoadingCounter) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }

                            if (chatUiState.isLoadingCounter) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.dp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Loading...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            } else {
                                Text(
                                    text = "${chatUiState.remainingRequests}/20 requests left",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        chatUiState.remainingRequests == 0 -> MaterialTheme.colorScheme.error
                                        chatUiState.remainingRequests <= 5 -> MaterialTheme.colorScheme.error
                                        chatUiState.remainingRequests <= 10 -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    },
                                    fontWeight = if (chatUiState.remainingRequests <= 5) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, "Menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .navigationBarsPadding()
        ) {
            // Rate limit progress bar
            if (chatUiState.remainingRequests < 20 && !chatUiState.isLoadingCounter) {
                val progress = chatUiState.remainingRequests / 20f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = when {
                        chatUiState.remainingRequests <= 5 -> MaterialTheme.colorScheme.error
                        chatUiState.remainingRequests <= 10 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
            }

            // Messages List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (chatUiState.messages.isEmpty() && !chatUiState.isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "👋",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Spacer(modifier = Modifier.size(16.dp))
                        Text(
                            text = "Start a conversation",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Ask me anything!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.size(24.dp))

                        if (chatUiState.isLoadingCounter) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Text(
                                text = "You have ${chatUiState.remainingRequests} requests left",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = chatUiState.messages,
                            key = { it.id }
                        ) { message ->
                            MessageItem(message = message)
                        }

                        if (chatUiState.isLoading) {
                            item {
                                ThinkingIndicator()
                            }
                        }
                    }
                }
            }



            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (chatUiState.remainingRequests <= 5 && chatUiState.remainingRequests > 0 && !chatUiState.isLoadingCounter) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Only ${chatUiState.remainingRequests} requests left! Limit resets in 30 minutes.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        enabled = !chatUiState.isLoading && chatUiState.remainingRequests > 0 && !chatUiState.isLoadingCounter
                    )

                    Spacer(modifier = Modifier.size(8.dp))

                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank() && chatUiState.remainingRequests > 0) {
                                authUiState.user?.uid?.let { userId ->
                                    chatViewModel.sendMessage(userId, messageText.trim())
                                    messageText = ""
                                }
                            } else if (chatUiState.remainingRequests == 0) {
                                showRateLimitDialog = true
                                rateLimitMessage = "You've reached the maximum of 20 requests per 30 minutes. Please wait before sending more messages."
                            }
                        },
                        enabled = messageText.isNotBlank() && !chatUiState.isLoading && chatUiState.remainingRequests > 0 && !chatUiState.isLoadingCounter
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (messageText.isNotBlank() && !chatUiState.isLoading && chatUiState.remainingRequests > 0 && !chatUiState.isLoadingCounter) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }
                }
            }
        }
    }

    // Rate Limit Dialog
    if (showRateLimitDialog) {
        AlertDialog(
            onDismissRequest = { showRateLimitDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "Rate Limit Reached",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = rateLimitMessage,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = "Rate Limit Policy:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "• Maximum 20 requests per 30 minutes\n• Applies to all conversations\n• Automatically resets after 30 minutes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showRateLimitDialog = false }) {
                    Text("Got it")
                }
            },
            containerColor = MaterialTheme.colorScheme.errorContainer,
            iconContentColor = MaterialTheme.colorScheme.error,
            titleContentColor = MaterialTheme.colorScheme.onErrorContainer,
            textContentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    }

    // Low Quota Warning Dialog
    if (showLowQuotaWarning && chatUiState.remainingRequests in 1..5 && !chatUiState.isLoadingCounter) {
        AlertDialog(
            onDismissRequest = { showLowQuotaWarning = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "Low Quota Warning",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "You have only ${chatUiState.remainingRequests} requests remaining. Your quota will reset automatically in 30 minutes from your first request.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showLowQuotaWarning = false }) {
                    Text("Understood")
                }
            },
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconContentColor = MaterialTheme.colorScheme.tertiary,
            titleContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            textContentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}
