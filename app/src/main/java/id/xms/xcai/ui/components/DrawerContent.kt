package id.xms.xcai.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseUser
import id.xms.xcai.data.local.ConversationEntity
import id.xms.xcai.data.repository.PremiumStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerContent(
    user: FirebaseUser?,
    conversations: List<ConversationEntity>,
    currentConversationId: Long?,
    premiumStatus: PremiumStatus,
    isLoading: Boolean,
    onConversationClick: (Long) -> Unit,
    onNewChat: () -> Unit,
    onDeleteConversation: (ConversationEntity) -> Unit,
    onRenameConversation: (ConversationEntity, String) -> Unit,
    onSettingsClick: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current

    var conversationToDelete by remember { mutableStateOf<ConversationEntity?>(null) }
    var conversationToRename by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showPremiumDialog by remember { mutableStateOf(false) }

    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations
        else conversations.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    // Gradient background matching ChatScreen
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(Color(0xFF1A1A1A), Color(0xFF0D0D0D))
                    } else {
                        listOf(Color(0xFFFAFAFA), Color(0xFFEEEEEE))
                    }
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            // Profile Section (Glass effect)
            ProfileSection(
                user = user,
                premiumStatus = premiumStatus,
                isDark = isDark,
                onSettingsClick = onSettingsClick,
                onLogout = onLogout,
                onUpgradeClick = { showPremiumDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar
            SearchBar(
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                onClear = { searchQuery = "" },
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(16.dp))

            // New Chat Button
            NewChatButton(
                isPremium = premiumStatus.isPremium,
                isDark = isDark,
                onClick = onNewChat
            )

            Spacer(modifier = Modifier.height(16.dp))

            // History Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color.White else Color.Black
                )
                Text(
                    text = "${filteredConversations.size} chats",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Chat List
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF4285F4))
                }
            } else if (filteredConversations.isEmpty()) {
                EmptyState(hasSearch = searchQuery.isNotEmpty(), isDark = isDark)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = filteredConversations, key = { it.id }) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            isSelected = conversation.id == currentConversationId,
                            isDark = isDark,
                            onClick = { onConversationClick(conversation.id) },
                            onLongClick = {
                                conversationToRename = conversation
                                renameText = conversation.title
                            },
                            onDelete = { conversationToDelete = conversation }
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showPremiumDialog) {
        PremiumDialog(
            onDismiss = { showPremiumDialog = false },
            onContactTelegram = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/GustyxPower"))
                context.startActivity(intent)
                showPremiumDialog = false
            }
        )
    }

    conversationToDelete?.let { conversation ->
        DeleteDialog(
            title = conversation.title,
            onConfirm = {
                onDeleteConversation(conversation)
                conversationToDelete = null
            },
            onDismiss = { conversationToDelete = null }
        )
    }

    conversationToRename?.let { conversation ->
        RenameDialog(
            currentName = renameText,
            onNameChange = { renameText = it },
            onConfirm = {
                if (renameText.isNotBlank() && renameText != conversation.title) {
                    onRenameConversation(conversation, renameText)
                }
                conversationToRename = null
                renameText = ""
            },
            onDismiss = {
                conversationToRename = null
                renameText = ""
            }
        )
    }
}

@Composable
private fun ProfileSection(
    user: FirebaseUser?,
    premiumStatus: PremiumStatus,
    isDark: Boolean,
    onSettingsClick: () -> Unit,
    onLogout: () -> Unit,
    onUpgradeClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) Color(0xFF2D2D2D).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)),
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Profile Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar with premium border
                Surface(
                    shape = CircleShape,
                    border = BorderStroke(
                        width = 2.dp,
                        color = if (premiumStatus.isPremium) Color(0xFFFFD700) else Color(0xFF4285F4)
                    )
                ) {
                    if (user?.photoUrl != null) {
                        AsyncImage(
                            model = user.photoUrl,
                            contentDescription = "Profile",
                            modifier = Modifier.size(56.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(56.dp).background(Color(0xFF4285F4)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user?.displayName?.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Name & Email
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user?.displayName ?: "User",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = user?.email ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Premium Badge
            if (premiumStatus.isPremium) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when (premiumStatus.tier) {
                        "premium_plus" -> Color(0xFFFFD700).copy(alpha = 0.2f)
                        else -> Color(0xFF4285F4).copy(alpha = 0.2f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = if (premiumStatus.tier == "premium_plus") "💎" else "⭐", fontSize = 16.sp)
                        Text(
                            text = if (premiumStatus.tier == "premium_plus") "Premium Plus" else "Premium",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (premiumStatus.tier == "premium_plus") Color(0xFFFFD700) else Color(0xFF4285F4)
                        )
                        Text(
                            text = "✨ Unlimited",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                // Upgrade Button
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    onClick = onUpgradeClick,
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFD700)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Diamond, null, modifier = Modifier.size(18.dp), tint = Color(0xFF1A1A1A))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Upgrade to Premium",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1A1A)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))

            // Actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    isDark = isDark,
                    onClick = onSettingsClick,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    icon = Icons.Default.ExitToApp,
                    label = "Logout",
                    isDark = isDark,
                    isError = true,
                    onClick = onLogout,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isDark: Boolean,
    isError: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isError) {
            Color(0xFFEA4335).copy(alpha = 0.1f)
        } else {
            if (isDark) Color(0xFF1A1A1A).copy(alpha = 0.5f) else Color(0xFFF1F3F4).copy(alpha = 0.8f)
        },
        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(18.dp),
                tint = if (isError) Color(0xFFEA4335) else if (isDark) Color.White else Color.Black
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isError) Color(0xFFEA4335) else if (isDark) Color.White else Color.Black
            )
        }
    }
}

@Composable
private fun SearchBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onClear: () -> Unit,
    isDark: Boolean
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) Color(0xFF2D2D2D).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Search, "Search", tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(20.dp))

            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = if (isDark) Color.White else Color.Black),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            "Search conversations...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            )

            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, "Clear", modifier = Modifier.size(16.dp), tint = if (isDark) Color.White else Color.Black)
                }
            }
        }
    }
}

@Composable
private fun NewChatButton(isPremium: Boolean, isDark: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF4285F4),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.2f)) {
                Icon(Icons.Default.Add, "New Chat", modifier = Modifier.padding(10.dp).size(24.dp), tint = Color.White)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text("New Chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    if (isPremium) "✨ Premium access" else "Start a conversation",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            Icon(Icons.Default.ArrowForward, "Go", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    conversation: ConversationEntity,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
    val dateString = dateFormat.format(Date(conversation.updatedAt))

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            Color(0xFF4285F4).copy(alpha = 0.2f)
        } else {
            if (isDark) Color(0xFF2D2D2D).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.8f)
        },
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF4285F4).copy(alpha = 0.5f)
            else if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Chat,
                null,
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) Color(0xFF4285F4) else if (isDark) Color.White else Color.Black
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isDark) Color.White else Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(18.dp), tint = Color(0xFFEA4335))
            }
        }
    }
}

@Composable
private fun EmptyState(hasSearch: Boolean, isDark: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                if (hasSearch) Icons.Default.Search else Icons.Default.Chat,
                null,
                modifier = Modifier.size(56.dp),
                tint = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
            )
            Text(
                if (hasSearch) "No results found" else "No conversations yet",
                style = MaterialTheme.typography.titleMedium,
                color = if (isDark) Color.White else Color.Black
            )
            Text(
                if (hasSearch) "Try a different search" else "Start a new chat to begin",
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun DeleteDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFEA4335)) },
        title = { Text("Delete Conversation?", fontWeight = FontWeight.Bold) },
        text = { Text("This will permanently delete \"$title\" and all its messages.") },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA4335))) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RenameDialog(currentName: String, onNameChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, null, tint = Color(0xFF4285F4)) },
        title = { Text("Rename Conversation", fontWeight = FontWeight.Bold) },
        text = {
            TextField(
                value = currentName,
                onValueChange = onNameChange,
                label = { Text("New name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { Button(onClick = onConfirm, enabled = currentName.isNotBlank()) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PremiumDialog(onDismiss: () -> Unit, onContactTelegram: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Diamond, null, tint = Color(0xFFFFD700), modifier = Modifier.size(48.dp)) },
        title = { Text("Upgrade to Premium", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Choose your premium plan:", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)

                // Premium Plan
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4285F4).copy(alpha = 0.2f))) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFF4285F4), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Premium", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("100 Requests / 30 min", style = MaterialTheme.typography.bodySmall, color = Color.Black.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$1 / month", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                    }
                }

                // Premium Plus
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD700).copy(alpha = 0.2f))) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Diamond, null, tint = Color(0xFFFFD700), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Premium Plus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Unlimited Requests", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$4 Lifetime", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                    }
                }

                HorizontalDivider()
                Text("Contact via Telegram for payment:", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                Text("t.me/GustyxPower", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF0088CC))
            }
        },
        confirmButton = { Button(onClick = onContactTelegram, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088CC))) { Text("Open Telegram") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
