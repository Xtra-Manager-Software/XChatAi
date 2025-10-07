package id.xms.xcai.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import id.xms.xcai.data.local.ChatEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParsedMessage(
    val thinking: String?,
    val content: List<MessageContent>
)

sealed class MessageContent {
    data class Text(val text: String) : MessageContent()
    data class CodeBlock(val code: String, val language: String) : MessageContent()
}

// Parse message dengan line-by-line detection untuk handle literal ```
fun parseMessageContent(message: String): ParsedMessage {
    // Parse thinking section
    val thinkPattern = """<think>(.*?)</think>""".toRegex(RegexOption.DOT_MATCHES_ALL)
    val thinkMatch = thinkPattern.find(message)
    val thinking = thinkMatch?.groupValues?.getOrNull(1)?.trim()

    // Remove thinking part
    val cleanMessage = if (thinking != null) {
        message.replace(thinkPattern, "").trim()
    } else {
        message
    }

    val content = mutableListOf<MessageContent>()
    val lines = cleanMessage.lines()
    var i = 0
    val textBuffer = mutableListOf<String>()
    var inCodeBlock = false
    var codeLanguage = ""
    val codeBuffer = mutableListOf<String>()

    Log.d("MessageParse", "Parsing ${lines.size} lines")

    while (i < lines.size) {
        val line = lines[i]
        val trimmedLine = line.trim()

        // Check if line is code block delimiter (``` or ```
        if (trimmedLine.startsWith("```")) {
            if (!inCodeBlock) {
                // Start of code block
                Log.d("MessageParse", "Code block START at line $i")

                // Add accumulated text
                if (textBuffer.isNotEmpty()) {
                    val text = textBuffer.joinToString("\n").trim()
                    if (text.isNotEmpty()) {
                        content.addAll(parseTextWithInlineCode(text))
                    }
                    textBuffer.clear()
                }

                inCodeBlock = true
                // Extract language (everything after ```
                codeLanguage = trimmedLine.removePrefix("```").trim()
                if (codeLanguage.isEmpty()) {
                    codeLanguage = "plaintext"
                }
                Log.d("MessageParse", "Language: $codeLanguage")
            } else {
                // End of code block
                Log.d("MessageParse", "Code block END at line $i")
                inCodeBlock = false

                val code = codeBuffer.joinToString("\n").trim()
                if (code.isNotEmpty()) {
                    Log.d("MessageParse", "Adding code block: ${code.lines().size} lines")
                    content.add(MessageContent.CodeBlock(code, codeLanguage))
                }

                codeBuffer.clear()
                codeLanguage = ""
            }
        } else {
            // Regular line
            if (inCodeBlock) {
                codeBuffer.add(line)
            } else {
                textBuffer.add(line)
            }
        }

        i++
    }

    // Handle remaining content
    if (textBuffer.isNotEmpty()) {
        val text = textBuffer.joinToString("\n").trim()
        if (text.isNotEmpty()) {
            content.addAll(parseTextWithInlineCode(text))
        }
    }

    // Handle unclosed code block
    if (inCodeBlock && codeBuffer.isNotEmpty()) {
        val code = codeBuffer.joinToString("\n").trim()
        Log.d("MessageParse", "Unclosed code block, adding anyway")
        content.add(MessageContent.CodeBlock(code, codeLanguage))
    }

    // If no content parsed, treat entire message as text
    if (content.isEmpty()) {
        content.add(MessageContent.Text(cleanMessage))
    }

    Log.d("MessageParse", "Final content items: ${content.size}")
    content.forEachIndexed { index, item ->
        when (item) {
            is MessageContent.Text -> Log.d("MessageParse", "[$index] Text: ${item.text.take(50)}")
            is MessageContent.CodeBlock -> Log.d("MessageParse", "[$index] Code: ${item.language}, ${item.code.lines().size} lines")
        }
    }

    return ParsedMessage(thinking, content)
}

// Parse text dengan inline code support
fun parseTextWithInlineCode(text: String): List<MessageContent> {
    val result = mutableListOf<MessageContent>()
    val inlineCodePattern = """`([^`\n]+)`""".toRegex()
    var lastIndex = 0

    inlineCodePattern.findAll(text).forEach { match ->
        // Add text before inline code
        if (match.range.first > lastIndex) {
            val textBefore = text.substring(lastIndex, match.range.first)
            if (textBefore.isNotEmpty()) {
                result.add(MessageContent.Text(textBefore))
            }
        }

        // Add inline code
        val inlineCode = match.groupValues[1]
        result.add(MessageContent.CodeBlock(inlineCode, "inline"))

        lastIndex = match.range.last + 1
    }

    // Add remaining text
    if (lastIndex < text.length) {
        val remaining = text.substring(lastIndex)
        if (remaining.isNotEmpty()) {
            result.add(MessageContent.Text(remaining))
        }
    }

    // If no inline code found, return as text
    if (result.isEmpty() && text.isNotEmpty()) {
        result.add(MessageContent.Text(text))
    }

    return result
}

@Composable
fun MessageItem(
    message: ChatEntity,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = timeFormat.format(Date(message.timestamp))

    if (message.isUser) {
        UserMessageBubble(
            message = message.message,
            time = timeString,
            modifier = modifier
        )
    } else {
        val parsed = remember(message.message) {
            parseMessageContent(message.message)
        }

        AIMessageWithContent(
            thinking = parsed.thinking,
            content = parsed.content,
            time = timeString,
            modifier = modifier
        )
    }
}

@Composable
private fun UserMessageBubble(
    message: String,
    time: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 4.dp
                ),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 1.dp
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 10.dp
                    )
                )
            }

            Spacer(modifier = Modifier.size(4.dp))

            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun AIMessageWithContent(
    thinking: String?,
    content: List<MessageContent>,
    time: String,
    modifier: Modifier = Modifier
) {
    var isThinkingExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // AI Avatar
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = "AI",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.size(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "XChatAi",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.size(8.dp))

            // Thinking Section
            if (thinking != null) {
                ThinkingSection(
                    thinking = thinking,
                    isExpanded = isThinkingExpanded,
                    onToggle = { isThinkingExpanded = !isThinkingExpanded }
                )

                Spacer(modifier = Modifier.size(12.dp))
            }

            // Render content
            content.forEach { item ->
                when (item) {
                    is MessageContent.Text -> {
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight.times(1.5f),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    is MessageContent.CodeBlock -> {
                        if (item.language == "inline") {
                            // Inline code
                            InlineCodeText(code = item.code)
                        } else {
                            // Code block - always visible
                            Spacer(modifier = Modifier.size(8.dp))
                            CodeBlockCard(
                                code = item.code,
                                language = item.language
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.size(4.dp))

            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun CodeBlockCard(
    code: String,
    language: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "📄",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = language,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    val lineCount = code.lines().size
                    Text(
                        text = "($lineCount lines)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                IconButton(
                    onClick = {
                        copyToClipboard(context, code)
                        Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.size(8.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.size(12.dp))

            // Code content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight.times(1.6f)
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}

@Composable
private fun InlineCodeText(
    code: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = buildAnnotatedString {
            withStyle(
                style = SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    color = MaterialTheme.colorScheme.primary
                )
            ) {
                append(" $code ")
            }
        },
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier
    )
}

@Composable
private fun ThinkingSection(
    thinking: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "🧠",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "Thought process",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val wordCount = thinking.split("\\s+".toRegex()).size
                    Text(
                        text = "($wordCount words)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            copyToClipboard(context, thinking)
                            Toast.makeText(context, "Thinking copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy thinking",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.size(8.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.size(12.dp))

                    Text(
                        text = thinking,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight.times(1.5f)
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Copied Text", text)
    clipboard.setPrimaryClip(clip)
}
