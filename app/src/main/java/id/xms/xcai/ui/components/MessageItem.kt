package id.xms.xcai.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xms.xcai.data.local.ChatEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed class MessageContent {
    data class Text(val content: String) : MessageContent()
    data class Code(val code: String, val language: String = "") : MessageContent()
    data class InlineCode(val code: String) : MessageContent()
    data class Bold(val text: String) : MessageContent()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MessageContent()
}

@Composable
fun MessageItem(
    message: ChatEntity,
    modifier: Modifier = Modifier,
    isLastUserMessage: Boolean = false,
    onEditMessage: ((String, String) -> Unit)? = null,
    onRegenerateResponse: ((String) -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val time = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    if (message.isUser) {
        UserMessage(
            message = message.message,
            time = time,
            isDark = isDark,
            modifier = modifier,
            showActions = isLastUserMessage,
            onEdit = { newText ->
                onEditMessage?.invoke(message.id.toString(), newText)
            }
        )
    } else {
        val (thinking, content) = remember(message.message) {
            parseMessageContent(message.message)
        }

        AIMessageWithContent(
            thinking = thinking,
            content = content,
            time = time,
            isDark = isDark,
            modifier = modifier,
            fullMessageText = message.message,
            showRegenerate = isLastUserMessage,
            onRegenerate = {
                onRegenerateResponse?.invoke(message.id.toString())
            }
        )
    }
}

fun parseMessageContent(text: String): Pair<String?, List<MessageContent>> {
    val thinkingRegex = """<think>(.*?)</think>""".toRegex(RegexOption.DOT_MATCHES_ALL)
    val thinkingMatch = thinkingRegex.find(text)
    val thinking = thinkingMatch?.groupValues?.get(1)?.trim()
    val contentText = text.replace(thinkingRegex, "").trim()
    val content = parseContent(contentText)
    return Pair(thinking, content)
}

private fun parseContent(text: String): List<MessageContent> {
    val result = mutableListOf<MessageContent>()
    var remainingText = text
    val tripleBacktick = "```"

    while (remainingText.isNotEmpty()) {
        when {
            remainingText.startsWith(tripleBacktick) -> {
                val endIndex = remainingText.indexOf(tripleBacktick, 3)
                if (endIndex != -1 && endIndex > 3) {
                    val codeBlock = remainingText.substring(3, endIndex)
                    val lines = codeBlock.lines()
                    val language = lines.firstOrNull()?.trim() ?: ""
                    val code = if (language.isNotEmpty() && language.all { it.isLetterOrDigit() }) {
                        lines.drop(1).joinToString("\n")
                    } else {
                        codeBlock
                    }
                    result.add(MessageContent.Code(code.trim(), language))
                    remainingText = remainingText.substring(endIndex + 3).trimStart()
                } else {
                    result.add(MessageContent.Text(remainingText))
                    remainingText = ""
                }
            }

            remainingText.startsWith("`") && !remainingText.startsWith(tripleBacktick) -> {
                val endIndex = remainingText.indexOf("`", 1)
                if (endIndex != -1 && endIndex > 1) {
                    result.add(MessageContent.InlineCode(remainingText.substring(1, endIndex)))
                    remainingText = remainingText.substring(endIndex + 1)
                } else {
                    result.add(MessageContent.Text(remainingText))
                    remainingText = ""
                }
            }

            remainingText.startsWith("**") -> {
                val endIndex = remainingText.indexOf("**", 2)
                if (endIndex != -1 && endIndex > 2) {
                    result.add(MessageContent.Bold(remainingText.substring(2, endIndex)))
                    remainingText = remainingText.substring(endIndex + 2)
                } else {
                    result.add(MessageContent.Text(remainingText))
                    remainingText = ""
                }
            }

            remainingText.contains("|") && remainingText.contains("\n") -> {
                val lines = remainingText.lines()
                val tableLines = mutableListOf<String>()

                for (line in lines) {
                    val trimmedLine = line.trim()
                    if (trimmedLine.startsWith("|") && trimmedLine.endsWith("|")) {
                        tableLines.add(line)
                    } else if (tableLines.size >= 3) {
                        break
                    } else if (tableLines.isNotEmpty()) {
                        tableLines.clear()
                    }
                }

                if (tableLines.size >= 3) {
                    val headers = tableLines[0]
                        .split("|")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    val rows = tableLines.drop(2)
                        .map { line ->
                            line.split("|")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                        }
                        .filter { it.isNotEmpty() }

                    result.add(MessageContent.Table(headers, rows))

                    val tableText = tableLines.joinToString("\n")
                    remainingText = remainingText.substringAfter(tableText).trimStart()
                    continue
                }

                val codeBlockIndex = remainingText.indexOf(tripleBacktick)
                val inlineCodeIndex = remainingText.indexOf("`")
                val boldIndex = remainingText.indexOf("**")

                val nextSpecialChar = listOf(
                    codeBlockIndex,
                    inlineCodeIndex,
                    boldIndex
                ).filter { it != -1 }.minOrNull()

                if (nextSpecialChar != null && nextSpecialChar > 0) {
                    result.add(MessageContent.Text(remainingText.substring(0, nextSpecialChar)))
                    remainingText = remainingText.substring(nextSpecialChar)
                } else {
                    result.add(MessageContent.Text(remainingText))
                    remainingText = ""
                }
            }

            else -> {
                val codeBlockIndex = remainingText.indexOf(tripleBacktick)
                val inlineCodeIndex = remainingText.indexOf("`")
                val boldIndex = remainingText.indexOf("**")

                val nextSpecialChar = listOf(
                    codeBlockIndex,
                    inlineCodeIndex,
                    boldIndex
                ).filter { it != -1 }.minOrNull()

                if (nextSpecialChar != null && nextSpecialChar > 0) {
                    result.add(MessageContent.Text(remainingText.substring(0, nextSpecialChar)))
                    remainingText = remainingText.substring(nextSpecialChar)
                } else {
                    result.add(MessageContent.Text(remainingText))
                    remainingText = ""
                }
            }
        }
    }
    return result
}


@Composable
private fun UserMessage(
    message: String,
    time: String,
    isDark: Boolean,
    modifier: Modifier,
    showActions: Boolean = false,
    onEdit: ((String) -> Unit)? = null
) {
    var isEditing by remember { mutableStateOf(false) }
    var editedText by remember { mutableStateOf(message) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.End
    ) {
        if (isEditing) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF2D2D2D) else Color(0xFFF5F5F5),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    OutlinedTextField(
                        value = editedText,
                        onValueChange = { editedText = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if (isDark) Color.White else Color.Black,
                            unfocusedTextColor = if (isDark) Color.White else Color.Black,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = Color(0xFF4285F4),
                            unfocusedBorderColor = if (isDark) {
                                Color.White.copy(alpha = 0.2f)
                            } else {
                                Color.Black.copy(alpha = 0.2f)
                            }
                        ),
                        maxLines = 8
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = {
                                editedText = message
                                isEditing = false
                            },
                            isDark = isDark,
                            text = "Cancel"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                if (editedText.isNotBlank()) {
                                    onEdit?.invoke(editedText.trim())
                                    isEditing = false
                                }
                            },
                            isDark = isDark,
                            text = "Save & Resend",
                            isPrimary = true
                        )
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                color = Color(0xFF4285F4),
                shadowElevation = 2.dp,
                modifier = Modifier.padding(start = 60.dp, end = 16.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) {
                        Color.White.copy(alpha = 0.5f)
                    } else {
                        Color.Black.copy(alpha = 0.5f)
                    }
                )

                if (showActions) {
                    IconButton(
                        onClick = { isEditing = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = if (isDark) {
                                Color.White.copy(alpha = 0.6f)
                            } else {
                                Color.Black.copy(alpha = 0.6f)
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AIMessageWithContent(
    thinking: String?,
    content: List<MessageContent>,
    time: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    fullMessageText: String = "",
    showRegenerate: Boolean = false,
    onRegenerate: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = if (isDark) {
                                listOf(Color(0xFF8AB4F8), Color(0xFF4285F4))
                            } else {
                                listOf(Color(0xFF4285F4), Color(0xFF1A73E8))
                            }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✨", fontSize = 20.sp)
            }

            Column {
                Text(
                    text = "XChatAi",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFF8AB4F8) else Color(0xFF1A73E8)
                )
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) {
                        Color.White.copy(alpha = 0.5f)
                    } else {
                        Color.Black.copy(alpha = 0.5f)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (thinking != null && thinking.isNotBlank()) {
            ThinkingCard(thinking = thinking)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 60.dp, end = 16.dp)
        ) {
            content.forEach { item ->
                when (item) {
                    is MessageContent.Text -> {
                        if (item.content.isNotBlank()) {
                            SelectionContainer {
                                Text(
                                    text = item.content,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        lineHeight = 24.sp
                                    ),
                                    color = if (isDark) {
                                        Color.White.copy(alpha = 0.9f)
                                    } else {
                                        Color(0xFF202124).copy(alpha = 0.9f)
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    is MessageContent.Code -> {
                        CodeBlock(code = item.code, language = item.language, isDark = isDark)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    is MessageContent.InlineCode -> {
                        SelectionContainer {
                            InlineCodeText(code = item.code, isDark = isDark)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    is MessageContent.Bold -> {
                        SelectionContainer {
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    is MessageContent.Table -> {
                        TableContent(headers = item.headers, rows = item.rows, isDark = isDark)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                CopyButton(
                    text = fullMessageText,
                    context = context,
                    isDark = isDark
                )

                if (showRegenerate) {
                    RegenerateButton(
                        onClick = { onRegenerate?.invoke() },
                        isDark = isDark
                    )
                }
            }
        }
    }
}

@Composable
private fun TableContent(
    headers: List<String>,
    rows: List<List<String>>,
    isDark: Boolean
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) Color(0xFF1E1E1E) else Color(0xFFF6F8FA))
            .border(
                width = 1.dp,
                color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isDark) Color(0xFF2D2D2D) else Color(0xFFE1E4E8))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📊",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "Table",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${rows.size} rows",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
                )
            }

            IconButton(
                onClick = {
                    val csv = buildString {
                        append(headers.joinToString(","))
                        append("\n")
                        rows.forEach { row ->
                            append(row.joinToString(","))
                            append("\n")
                        }
                    }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("table", csv))
                    copied = true
                    Toast.makeText(context, "Table copied as CSV!", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        delay(2000)
                        copied = false
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy table",
                    tint = if (copied) Color(0xFF34A853) else {
                        if (isDark) Color(0xFF8AB4F8) else Color(0xFF4285F4)
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Column {
                Row {
                    headers.forEach { header ->
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .background(if (isDark) Color(0xFF2D2D2D) else Color(0xFFE1E4E8))
                                .padding(8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = header,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF8AB4F8) else Color(0xFF1A73E8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f)
                )

                rows.forEach { row ->
                    Row {
                        row.forEach { cell ->
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .padding(8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = cell,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) Color(0xFFE3E3E3) else Color(0xFF24292E),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegenerateButton(
    onClick: () -> Unit,
    isDark: Boolean
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) {
                Color.White.copy(alpha = 0.2f)
            } else {
                Color.Black.copy(alpha = 0.2f)
            }
        ),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Regenerate",
                tint = if (isDark) Color(0xFF8AB4F8) else Color(0xFF4285F4),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Regenerate",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) {
                    Color.White.copy(alpha = 0.8f)
                } else {
                    Color.Black.copy(alpha = 0.8f)
                }
            )
        }
    }
}

@Composable
private fun TextButton(
    onClick: () -> Unit,
    isDark: Boolean,
    text: String,
    isPrimary: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isPrimary) Color(0xFF4285F4) else Color.Transparent,
        border = if (!isPrimary) BorderStroke(
            width = 1.dp,
            color = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f)
        ) else null
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isPrimary) Color.White else {
                if (isDark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.8f)
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun CodeBlock(code: String, language: String, isDark: Boolean) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) Color(0xFF1E1E1E) else Color(0xFFF6F8FA))
            .border(
                width = 1.dp,
                color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isDark) Color(0xFF2D2D2D) else Color(0xFFE1E4E8))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (language.isNotEmpty()) language else "code",
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("code", code)
                    clipboard.setPrimaryClip(clip)
                    copied = true
                    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        delay(2000)
                        copied = false
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    tint = if (copied) Color(0xFF34A853) else {
                        if (isDark) Color(0xFF8AB4F8) else Color(0xFF4285F4)
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        SelectionContainer {
            Text(
                text = code,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp
                ),
                color = if (isDark) Color(0xFFE3E3E3) else Color(0xFF24292E),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            )
        }
    }
}

@Composable
private fun InlineCodeText(code: String, isDark: Boolean) {
    Text(
        text = code,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        color = if (isDark) Color(0xFFE3E3E3) else Color(0xFF24292E),
        modifier = Modifier
            .background(
                color = if (isDark) Color(0xFF2D2D2D) else Color(0xFFF6F8FA),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun CopyButton(text: String, context: Context, isDark: Boolean) {
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("message", text)
            clipboard.setPrimaryClip(clip)
            copied = true
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            scope.launch {
                delay(2000)
                copied = false
            }
        },
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f)
        ),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = "Copy",
                tint = if (copied) Color(0xFF34A853) else {
                    if (isDark) Color(0xFF8AB4F8) else Color(0xFF4285F4)
                },
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (copied) "Copied!" else "Copy",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun StreamingMessageItem(text: String, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val (thinking, content) = remember(text) { parseMessageContent(text) }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = if (isDark) listOf(Color(0xFF8AB4F8), Color(0xFF4285F4))
                            else listOf(Color(0xFF4285F4), Color(0xFF1A73E8))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✨", fontSize = 20.sp)
            }
            Column {
                Text(
                    text = "XChatAi",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFF8AB4F8) else Color(0xFF1A73E8)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (thinking != null && thinking.isNotBlank()) {
            ThinkingCard(thinking = thinking)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Column(modifier = Modifier.fillMaxWidth().padding(start = 60.dp, end = 16.dp)) {
            content.forEach { item ->
                when (item) {
                    is MessageContent.Text -> {
                        if (item.content.isNotBlank()) {
                            SelectionContainer {
                                Text(
                                    text = item.content,
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                                    color = if (isDark) Color.White.copy(alpha = 0.9f)
                                    else Color(0xFF202124).copy(alpha = 0.9f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    is MessageContent.Code -> {
                        CodeBlock(code = item.code, language = item.language, isDark = isDark)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    is MessageContent.InlineCode -> {
                        SelectionContainer { InlineCodeText(code = item.code, isDark = isDark) }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    is MessageContent.Bold -> {
                        SelectionContainer {
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    is MessageContent.Table -> {
                        TableContent(headers = item.headers, rows = item.rows, isDark = isDark)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingCard(thinking: String) {
    val isDark = isSystemInDarkTheme()
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) Color(0xFF2D2D2D).copy(alpha = 0.8f) else Color(0xFFF1F3F4),
        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🤔",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Thinking...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFF8AB4F8) else Color(0xFF1A73E8)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = thinking,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.8f),
                lineHeight = 20.sp
            )
        }
    }
}
