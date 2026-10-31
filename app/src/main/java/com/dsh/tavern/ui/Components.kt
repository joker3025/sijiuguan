package com.dsh.tavern.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.tavern.data.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TavernBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                TextButton(onClick = onBack) {
                    Text("‹", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            actions()
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
    )
}

@Composable
fun EmptyView(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📖", fontSize = 44.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CardAvatar(
    avatar: ByteArray?,
    name: String,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(avatar) {
        avatar?.let {
            try {
                BitmapFactory.decodeByteArray(it, 0, it.size)
            } catch (e: Exception) {
                null
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4))
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).ifBlank { "卡" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun TagLine(tags: List<String>) {
    if (tags.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tags.forEach { tag ->
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun InputDialog(
    title: String,
    initial: String = "",
    label: String = "",
    minLines: Int = 1,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { if (label.isNotEmpty()) Text(label) },
                modifier = Modifier.fillMaxWidth(),
                minLines = minLines,
                maxLines = 10
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "删除",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun SectionCard(title: String, text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(6.dp))
            Text(
                text.ifBlank { "（无）" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isUser: Boolean,
    showReasoning: Boolean,
    canRegenerate: Boolean,
    canContinue: Boolean,
    canShowPrompt: Boolean,
    canBranch: Boolean,
    canCheckpoint: Boolean,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit,
    onShowPrompt: () -> Unit,
    onBranch: () -> Unit,
    onCheckpoint: () -> Unit,
    onPrevVariant: () -> Unit,
    onNextVariant: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    /** 显示时替换 {{char}} / {{user}} 宏（旧消息里可能存的是原始宏）。 */
    charName: String = "",
    userName: String = "",
    /** 本条消息的估算花费文本（价格未设置时为空）。 */
    costText: String = ""
) {
    var menuOpen by remember { mutableStateOf(false) }
    var reasoningOpen by remember { mutableStateOf(false) }
    val bg = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val content = substituteMacros(stripNullNoise(message.displayContent), charName, userName)
    val reasoning = substituteMacros(stripNullNoise(message.displayReasoning), charName, userName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (!isUser && reasoning.isNotBlank() && showReasoning) {
                    Text(
                        text = if (reasoningOpen) "▾ 思考过程" else "▸ 思考过程",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.clickable { reasoningOpen = !reasoningOpen }
                    )
                    if (reasoningOpen) {
                        Text(
                            reasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(top = 4.dp)
                        )
                    }
                }
                RichText(
                    text = content.ifBlank { "…" },
                    color = fg,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "你" else "角色",
                        style = MaterialTheme.typography.labelSmall,
                        color = fg.copy(alpha = 0.6f)
                    )
                    if (!isUser && message.isMultiVariant) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "‹",
                            style = MaterialTheme.typography.labelMedium,
                            color = fg.copy(alpha = 0.8f),
                            modifier = Modifier
                                .clickable { onPrevVariant() }
                                .padding(horizontal = 4.dp)
                        )
                        Text(
                            "${message.activeVariant + 1}/${message.variantCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = fg.copy(alpha = 0.8f)
                        )
                        Text(
                            "›",
                            style = MaterialTheme.typography.labelMedium,
                            color = fg.copy(alpha = 0.8f),
                            modifier = Modifier
                                .clickable { onNextVariant() }
                                .padding(horizontal = 4.dp)
                        )
                    }
                    if (costText.isNotBlank()) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            costText,
                            style = MaterialTheme.typography.labelSmall,
                            color = fg.copy(alpha = 0.55f)
                        )
                    }
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("复制") },
                    onClick = { menuOpen = false; onCopy() }
                )
                DropdownMenuItem(
                    text = { Text(if (isUser) "编辑并重生成" else "编辑（存为新版本）") },
                    onClick = { menuOpen = false; onEdit() }
                )
                if (!isUser && canRegenerate) {
                    DropdownMenuItem(
                        text = { Text("重新生成") },
                        onClick = { menuOpen = false; onRegenerate() }
                    )
                }
                if (!isUser && canContinue) {
                    DropdownMenuItem(
                        text = { Text("继续写") },
                        onClick = { menuOpen = false; onContinue() }
                    )
                }
                if (!isUser && canShowPrompt) {
                    DropdownMenuItem(
                        text = { Text("查看本回合 Prompt") },
                        onClick = { menuOpen = false; onShowPrompt() }
                    )
                }
                if (canBranch) {
                    DropdownMenuItem(
                        text = { Text("从这里分支") },
                        onClick = { menuOpen = false; onBranch() }
                    )
                }
                if (canCheckpoint) {
                    DropdownMenuItem(
                        text = { Text("在此保存检查点") },
                        onClick = { menuOpen = false; onCheckpoint() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("删除") },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

@Composable
fun SuggestedReplies(replies: List<String>, onClick: (String) -> Unit) {
    if (replies.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        replies.take(6).forEach { reply ->
            AssistChip(
                onClick = { onClick(reply) },
                label = { Text(reply.take(28), maxLines = 1) }
            )
        }
    }
}

fun formatTime(ts: Long): String {
    if (ts <= 0) return ""
    return try {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
    } catch (e: Exception) {
        ""
    }
}

/** 旧版本 SSE 解析 bug 会把 JSON null 变成 "null" 字符串；显示时兜底清理连续 null。 */
private fun stripNullNoise(text: String): String {
    if (!text.contains("null", ignoreCase = true)) return text
    return text.replace(Regex("(?i)(null\\s*){2,}"), "").trim()
}

/** 显示层宏替换：{{char}} / {{user}}。 */
private fun substituteMacros(text: String, charName: String, userName: String): String {
    if (text.isBlank() || (!text.contains("{{") && !text.contains("<USER>") && !text.contains("<BOT>"))) {
        return text
    }
    val char = charName.ifBlank { "角色" }
    val user = userName.ifBlank { "你" }
    return text
        .replace("{{char}}", char)
        .replace("{{Char}}", char)
        .replace("<BOT>", char)
        .replace("{{user}}", user)
        .replace("{{User}}", user)
        .replace("{{persona}}", user)
        .replace("<USER>", user)
}
