package id.xms.xcai.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
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
    data class Heading(val text: String, val level: Int) : MessageContent()
    data class BulletList(val items: List<String>) : MessageContent()
}

fun parseMessageContent(message: String): ParsedMessage {
    val thinkPattern = """<think>(.*?)</think>""".toRegex(RegexOption.DOT_MATCHES_ALL)
    val thinkMatch = thinkPattern.find(message)
    val thinking = thinkMatch?.groupValues?.getOrNull(1)?.trim()

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

    while (i < lines.size) {
        val line = lines[i]
        val trimmedLine = line.trim()

        when {
            trimmedLine.startsWith("```") -> {
                if (!inCodeBlock) {
                    if (textBuffer.isNotEmpty()) {
                        content.addAll(parseTextWithMarkdown(textBuffer.joinToString("\n")))
                        textBuffer.clear()
                    }
                    inCodeBlock = true
                    codeLanguage = trimmedLine.removePrefix("```").trim()
                    if (codeLanguage.isEmpty()) {
                        codeLanguage = "plaintext"
                    }
                } else {
                    inCodeBlock = false
                    val code = codeBuffer.joinToString("\n").trim()
                    if (code.isNotEmpty()) {
                        content.add(MessageContent.CodeBlock(code, codeLanguage))
                    }
                    codeBuffer.clear()
                    codeLanguage = ""
                }
            }
            else -> {
                if (inCodeBlock) {
                    codeBuffer.add(line)
                } else {
                    textBuffer.add(line)
                }
            }
        }
        i++
    }

    if (textBuffer.isNotEmpty()) {
        content.addAll(parseTextWithMarkdown(textBuffer.joinToString("\n")))
    }

    if (inCodeBlock && codeBuffer.isNotEmpty()) {
        content.add(MessageContent.CodeBlock(codeBuffer.joinToString("\n").trim(), codeLanguage))
    }

    if (content.isEmpty()) {
        content.add(MessageContent.Text(cleanMessage))
    }

    return ParsedMessage(thinking, content)
}

fun parseTextWithMarkdown(text: String): List<MessageContent> {
    val result = mutableListOf<MessageContent>()
    val lines = text.lines()
    var i = 0
    val textBuffer = mutableListOf<String>()
    val listBuffer = mutableListOf<String>()

    while (i < lines.size) {
        val line = lines[i]
        val trimmedLine = line.trim()

        when {
            trimmedLine.matches("""^#{1,3}\s+.+$""".toRegex()) -> {
                if (textBuffer.isNotEmpty()) {
                    val bufferedText = textBuffer.joinToString("\n").trim()
                    if (bufferedText.isNotEmpty()) {
                        result.addAll(parseInlineFormatting(bufferedText))
                    }
                    textBuffer.clear()
                }
                if (listBuffer.isNotEmpty()) {
                    result.add(MessageContent.BulletList(listBuffer.toList()))
                    listBuffer.clear()
                }
                val level = trimmedLine.takeWhile { it == '#' }.length
                val headingText = trimmedLine.dropWhile { it == '#' }.trim()
                result.add(MessageContent.Heading(headingText, level))
            }

            trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") -> {
                if (textBuffer.isNotEmpty()) {
                    val bufferedText = textBuffer.joinToString("\n").trim()
                    if (bufferedText.isNotEmpty()) {
                        result.addAll(parseInlineFormatting(bufferedText))
                    }
                    textBuffer.clear()
                }
                val itemText = trimmedLine.removePrefix("- ").removePrefix("* ")
                listBuffer.add(itemText)
            }

            trimmedLine.matches("""^\d+\.\s+.+""".toRegex()) -> {
                if (textBuffer.isNotEmpty()) {
                    val bufferedText = textBuffer.joinToString("\n").trim()
                    if (bufferedText.isNotEmpty()) {
                        result.addAll(parseInlineFormatting(bufferedText))
                    }
                    textBuffer.clear()
                }
                val itemText = trimmedLine.replaceFirst("""^\d+\.\s+""".toRegex(), "")
                listBuffer.add(itemText)
            }

            trimmedLine.isEmpty() -> {
                if (listBuffer.isNotEmpty()) {
                    result.add(MessageContent.BulletList(listBuffer.toList()))
                    listBuffer.clear()
                }
                if (textBuffer.isNotEmpty() && textBuffer.last().isNotEmpty()) {
                    textBuffer.add("")
                }
            }

            else -> {
                if (listBuffer.isNotEmpty()) {
                    result.add(MessageContent.BulletList(listBuffer.toList()))
                    listBuffer.clear()
                }
                textBuffer.add(line)
            }
        }
        i++
    }

    if (listBuffer.isNotEmpty()) {
        result.add(MessageContent.BulletList(listBuffer.toList()))
    }

    if (textBuffer.isNotEmpty()) {
        val bufferedText = textBuffer.joinToString("\n").trim()
        if (bufferedText.isNotEmpty()) {
            result.addAll(parseInlineFormatting(bufferedText))
        }
    }

    return result
}

fun parseInlineFormatting(text: String): List<MessageContent> {
    val result = mutableListOf<MessageContent>()
    val inlineCodePattern = """`([^`\n]+)`""".toRegex()
    var lastIndex = 0

    val matches = inlineCodePattern.findAll(text).toList()

    matches.forEach { match ->
        if (match.range.first > lastIndex) {
            val textBefore = text.substring(lastIndex, match.range.first)
            if (textBefore.isNotEmpty()) {
                result.add(MessageContent.Text(textBefore))
            }
        }
        result.add(MessageContent.CodeBlock(match.groupValues[1], "inline"))
        lastIndex = match.range.last + 1
    }

    if (lastIndex < text.length) {
        val remaining = text.substring(lastIndex)
        if (remaining.isNotEmpty()) {
            result.add(MessageContent.Text(remaining))
        }
    }

    if (result.isEmpty() && text.isNotEmpty()) {
        result.add(MessageContent.Text(text))
    }

    return result
}

@Composable
fun MessageItem(
    message: ChatEntity,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    streamingText: String = ""
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
        val messageToShow = if (isStreaming) streamingText else message.message
        val parsed = remember(messageToShow) {
            parseMessageContent(messageToShow)
        }
        AIMessageWithContent(
            thinking = parsed.thinking,
            content = parsed.content,
            time = timeString,
            modifier = modifier,
            isStreaming = isStreaming
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
                shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 1.dp
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
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
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    var isThinkingExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
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

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "XChatAi",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.size(8.dp))

            if (thinking != null && !isStreaming) {
                ThinkingSection(
                    thinking = thinking,
                    isExpanded = isThinkingExpanded,
                    onToggle = { isThinkingExpanded = !isThinkingExpanded }
                )
                Spacer(modifier = Modifier.size(12.dp))
            }

            content.forEach { item ->
                when (item) {
                    is MessageContent.Text -> {
                        Text(
                            text = parseStyledText(item.text),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight.times(1.5f),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    is MessageContent.Heading -> {
                        Spacer(modifier = Modifier.size(4.dp))
                        HeadingText(text = item.text, level = item.level)
                    }
                    is MessageContent.BulletList -> {
                        BulletListText(items = item.items)
                    }
                    is MessageContent.CodeBlock -> {
                        if (item.language == "inline") {
                            InlineCodeText(code = item.code)
                        } else {
                            Spacer(modifier = Modifier.size(8.dp))
                            CodeBlockCard(
                                isStreaming = isStreaming,
                                code = item.code,
                                language = item.language
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                        }
                    }
                }
            }

            if (!isStreaming) {
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun HeadingText(
    text: String,
    level: Int,
    modifier: Modifier = Modifier
) {
    val style = when (level) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.titleMedium
    }

    Text(
        text = parseStyledText(text),
        style = style,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun BulletListText(
    items: List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        items.forEach { item ->
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "•  ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = parseStyledText(item),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight.times(1.5f),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun parseStyledText(text: String): AnnotatedString {
    return buildAnnotatedString {
        val boldRegex = """\*\*(.+?)\*\*""".toRegex()
        var lastEnd = 0

        boldRegex.findAll(text).forEach { match ->
            if (match.range.first > lastEnd) {
                append(text.substring(lastEnd, match.range.first))
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
            lastEnd = match.range.last + 1
        }

        if (lastEnd < text.length) {
            append(text.substring(lastEnd))
        }
    }
}

@Composable
private fun CodeBlockCard(
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier,
    code: String,
    language: String
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📄",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = language,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "(${code.lines().size} lines)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                if (!isStreaming) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Code", code))
                            Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.size(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
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
                SpanStyle(
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🧠",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "Thought process",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "(${thinking.split("\\s+".toRegex()).size} words)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Thinking", thinking))
                            Toast.makeText(context, "Thinking copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.size(12.dp))

                    Text(
                        text = thinking,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StreamingMessageItem(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
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

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "XChatAi",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.size(8.dp))

            // Typewriter text dengan cursor
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight.times(1.5f)
                )

                // Blinking cursor
                BlinkingCursor()
            }
        }
    }
}

@Composable
private fun BlinkingCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 530),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    Text(
        text = "▊",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        modifier = Modifier.padding(start = 2.dp)
    )
}



@Composable
fun AITypingIndicator(
    modifier: Modifier = Modifier
) {
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

        Column {
            Text(
                text = "XChatAi",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.size(8.dp))

            // Typing Bubble
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    TypingDot(delay = 0)
                    TypingDot(delay = 150)
                    TypingDot(delay = 300)
                }
            }

            Spacer(modifier = Modifier.size(4.dp))

            Text(
                text = "typing...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun AIThinkingIndicator(
    modifier: Modifier = Modifier
) {
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

        Column {
            Text(
                text = "XChatAi",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.size(8.dp))

            // Thinking Bubble
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "AI is thinking...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingDot(
    modifier: Modifier = Modifier,
    delay: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dot")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 600,
                delayMillis = delay
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Box(
        modifier = modifier
            .size(10.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
    )
}
