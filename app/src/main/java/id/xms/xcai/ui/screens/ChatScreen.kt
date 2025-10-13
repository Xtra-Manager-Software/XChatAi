package id.xms.xcai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xms.xcai.ui.components.AIThinkingIndicator
import id.xms.xcai.ui.components.AITypingIndicator
import id.xms.xcai.ui.components.MessageItem
import id.xms.xcai.ui.components.StreamingMessageItem
import id.xms.xcai.ui.viewmodel.AuthViewModel
import id.xms.xcai.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel,
    authViewModel: AuthViewModel,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()  // Theme detection

    val chatUiState by chatViewModel.chatUiState.collectAsState()
    val premiumStatus by chatViewModel.premiumStatus.collectAsState()
    val authUiState by authViewModel.authUiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var messageText by remember { mutableStateOf("") }
    var showRateLimitDialog by remember { mutableStateOf(false) }
    var showLowQuotaWarning by remember { mutableStateOf(false) }
    var rateLimitMessage by remember { mutableStateOf("") }

    LaunchedEffect(messageText) {
        chatViewModel.setUserTyping(messageText.isNotEmpty())
    }

    DisposableEffect(Unit) {
        onDispose {
            chatViewModel.setUserTyping(false)
        }
    }

    LaunchedEffect(chatUiState.remainingRequests, chatUiState.isLoadingCounter) {
        if (!chatUiState.isLoadingCounter && chatUiState.remainingRequests == 0 && !premiumStatus.isPremium) {
            rateLimitMessage = "You've reached the maximum of 20 requests per 30 minutes. Please wait before sending more messages."
            showRateLimitDialog = true
        }
    }

    // Safe auto-scroll
    LaunchedEffect(chatUiState.messages.size) {
        if (chatUiState.messages.isNotEmpty() && !chatUiState.isStreaming) {
            delay(100)
            val targetIndex = (chatUiState.messages.size - 1).coerceAtLeast(0)
            try {
                listState.animateScrollToItem(targetIndex)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

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

    LaunchedEffect(chatUiState.remainingRequests) {
        if (chatUiState.remainingRequests in 1..5 && !showLowQuotaWarning && !chatUiState.isLoadingCounter && !premiumStatus.isPremium) {
            showLowQuotaWarning = true
        }
    }

    // Theme-aware gradient background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color(0xFF1A1A1A),
                            Color(0xFF0D0D0D)
                        )
                    } else {
                        listOf(
                            Color(0xFFFAFAFA),
                            Color(0xFFEEEEEE)
                        )
                    }
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "XChatAi",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = if (isDark) Color.White else Color.Black
                                )

                                if (premiumStatus.isPremium) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = when (premiumStatus.tier) {
                                            "premium_plus" -> Color(0xFFFFD700)
                                            else -> Color(0xFF4285F4).copy(alpha = 0.2f)
                                        }
                                    ) {
                                        Text(
                                            text = when (premiumStatus.tier) {
                                                "premium_plus" -> "💎 PLUS"
                                                else -> "⭐ PRO"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = when (premiumStatus.tier) {
                                                "premium_plus" -> Color(0xFF1A1A1A)
                                                else -> Color(0xFF4285F4)
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (chatUiState.remainingRequests <= 5 && !chatUiState.isLoadingCounter && !premiumStatus.isPremium) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = Color(0xFFEA4335)
                                    )
                                }

                                if (chatUiState.isLoadingCounter) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.dp,
                                        color = Color(0xFF4285F4)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Syncing...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isDark) {
                                            Color.White.copy(alpha = 0.6f)
                                        } else {
                                            Color.Black.copy(alpha = 0.6f)
                                        }
                                    )
                                } else {
                                    val displayText = if (premiumStatus.maxRequests == -1) {
                                        "✨ Unlimited requests"
                                    } else {
                                        "${chatUiState.remainingRequests}/${premiumStatus.maxRequests} requests"
                                    }

                                    Text(
                                        text = displayText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            premiumStatus.isPremium -> Color(0xFF4285F4)
                                            chatUiState.remainingRequests <= 5 -> Color(0xFFEA4335)
                                            else -> if (isDark) {
                                                Color.White.copy(alpha = 0.6f)
                                            } else {
                                                Color.Black.copy(alpha = 0.6f)
                                            }
                                        },
                                        fontWeight = if (premiumStatus.isPremium) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                Icons.Default.Menu,
                                "Menu",
                                tint = if (isDark) Color.White else Color.Black
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isDark) {
                            Color(0xFF1A1A1A).copy(alpha = 0.95f)
                        } else {
                            Color.White.copy(alpha = 0.95f)
                        },
                        titleContentColor = if (isDark) Color.White else Color.Black
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                if (chatUiState.remainingRequests < 20 && !chatUiState.isLoadingCounter && !premiumStatus.isPremium) {
                    val maxReq = if (premiumStatus.maxRequests == -1) Int.MAX_VALUE else premiumStatus.maxRequests
                    val progress = chatUiState.remainingRequests.toFloat() / maxReq.toFloat()
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = when {
                            chatUiState.remainingRequests <= 5 -> Color(0xFFEA4335)
                            chatUiState.remainingRequests <= 10 -> Color(0xFFFBBC04)
                            else -> Color(0xFF4285F4)
                        },
                        trackColor = if (isDark) {
                            Color.White.copy(alpha = 0.1f)
                        } else {
                            Color.Black.copy(alpha = 0.1f)
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (chatUiState.messages.isEmpty() && !chatUiState.isLoading) {
                        // Theme-aware greeting
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Hello, ${authUiState.user?.displayName?.split(" ")?.firstOrNull() ?: "User"}",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Normal,
                                color = if (isDark) Color(0xFF8AB4F8) else Color(0xFF1A73E8),
                                fontSize = 32.sp
                            )

                            if (premiumStatus.isPremium) {
                                Spacer(modifier = Modifier.size(12.dp))
                                Text(
                                    text = "✨ You have premium access!",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFFFFD700)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(
                                items = chatUiState.messages,
                                key = { it.id }
                            ) { message ->
                                MessageItem(
                                    message = message
                                )
                            }

                            if (chatUiState.isStreaming && chatUiState.streamingText.isNotEmpty()) {
                                item(key = "streaming_message") {
                                    StreamingMessageItem(
                                        text = chatUiState.streamingText
                                    )
                                }
                            }

                            if (chatUiState.isThinking) {
                                item(key = "thinking_indicator") {
                                    AIThinkingIndicator()
                                }
                            } else if (chatUiState.isLoading && !chatUiState.isStreaming && chatUiState.messages.isNotEmpty()) {
                                item(key = "typing_indicator") {
                                    AITypingIndicator()
                                }
                            }
                        }
                    }
                }

                // Theme-aware input section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(16.dp)
                ) {
                    if (chatUiState.remainingRequests <= 5 && chatUiState.remainingRequests > 0 && !chatUiState.isLoadingCounter && !premiumStatus.isPremium) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFEA4335),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Only ${chatUiState.remainingRequests} requests left",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFEA4335)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Theme-aware glass input field
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(28.dp),
                            color = if (isDark) {
                                Color(0xFF2D2D2D).copy(alpha = 0.8f)
                            } else {
                                Color.White.copy(alpha = 0.9f)
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isDark) {
                                    Color.White.copy(alpha = 0.1f)
                                } else {
                                    Color.Black.copy(alpha = 0.1f)
                                }
                            ),
                            shadowElevation = 4.dp
                        ) {
                            OutlinedTextField(
                                value = messageText,
                                onValueChange = {
                                    messageText = it
                                    chatViewModel.setUserTyping(it.isNotEmpty())
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        "Ask me anything...",
                                        color = if (isDark) {
                                            Color.White.copy(alpha = 0.5f)
                                        } else {
                                            Color.Black.copy(alpha = 0.5f)
                                        }
                                    )
                                },
                                shape = RoundedCornerShape(28.dp),
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = if (isDark) Color.White else Color.Black,
                                    unfocusedTextColor = if (isDark) Color.White else Color.Black,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    cursorColor = Color(0xFF4285F4)
                                ),
                                enabled = !chatUiState.isLoading && (chatUiState.remainingRequests > 0 || premiumStatus.isPremium) && !chatUiState.isLoadingCounter
                            )
                        }

                        // Send button
                        Surface(
                            onClick = {
                                if (messageText.isNotBlank() && (chatUiState.remainingRequests > 0 || premiumStatus.isPremium)) {
                                    authUiState.user?.uid?.let { userId ->
                                        chatViewModel.sendMessage(userId, messageText.trim())
                                        messageText = ""
                                        chatViewModel.setUserTyping(false)
                                    }
                                } else if (chatUiState.remainingRequests == 0 && !premiumStatus.isPremium) {
                                    showRateLimitDialog = true
                                    rateLimitMessage = "You've reached the maximum of 20 requests per 30 minutes."
                                }
                            },
                            shape = RoundedCornerShape(28.dp),
                            color = if (messageText.isNotBlank() && !chatUiState.isLoading && (chatUiState.remainingRequests > 0 || premiumStatus.isPremium) && !chatUiState.isLoadingCounter) {
                                Color(0xFF4285F4)
                            } else {
                                if (isDark) {
                                    Color(0xFF2D2D2D).copy(alpha = 0.5f)
                                } else {
                                    Color(0xFFCCCCCC).copy(alpha = 0.5f)
                                }
                            },
                            enabled = messageText.isNotBlank() && !chatUiState.isLoading && (chatUiState.remainingRequests > 0 || premiumStatus.isPremium) && !chatUiState.isLoadingCounter,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (messageText.isNotBlank() && !chatUiState.isLoading) {
                                        Color.White
                                    } else {
                                        Color.White.copy(alpha = 0.3f)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showRateLimitDialog) {
        AlertDialog(
            onDismissRequest = { showRateLimitDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFEA4335),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "Rate Limit Reached",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(rateLimitMessage)
            },
            confirmButton = {
                TextButton(onClick = { showRateLimitDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }

    if (showLowQuotaWarning && chatUiState.remainingRequests in 1..5 && !chatUiState.isLoadingCounter && !premiumStatus.isPremium) {
        AlertDialog(
            onDismissRequest = { showLowQuotaWarning = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFBBC04),
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text("Low Quota Warning")
            },
            text = {
                Text("You have only ${chatUiState.remainingRequests} requests remaining.")
            },
            confirmButton = {
                TextButton(onClick = { showLowQuotaWarning = false }) {
                    Text("Understood")
                }
            }
        )
    }
}
