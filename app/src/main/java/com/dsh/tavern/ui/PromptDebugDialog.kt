package com.dsh.tavern.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dsh.tavern.data.PromptDebug
import com.dsh.tavern.data.WorldHit

/**
 * P0-2：Prompt 调试面板。
 * 三个页签对应 DZMM 的「组装上下文」+「世界书诊断」：
 * - 最终输入：真正发给模型的 messages
 * - 世界书：每个条目的命中/未触发/预算不足 + 关键词 + 位置
 * - 预算：角色卡/世界书/历史/输出各占多少 token
 */
@Composable
fun PromptDebugDialog(preview: PromptPreviewData, onClose: () -> Unit) {
    val debug = preview.debug
    var tab by remember(preview.title, debug.createdAt) { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(preview.title) },
        text = {
            Column(Modifier.heightIn(max = 520.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        label = { Text("最终输入 ${debug.messages.size}") }
                    )
                    FilterChip(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        label = { Text("世界书 ${debug.activated}/${debug.world.size}") }
                    )
                    FilterChip(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        label = { Text("预算") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                when (tab) {
                    0 -> FinalInputTab(debug)
                    1 -> WorldBookTab(debug)
                    else -> BudgetTab(debug)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("关闭") }
        }
    )
}

@Composable
private fun FinalInputTab(debug: PromptDebug) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        if (debug.messages.isEmpty()) {
            Text("（没有消息）", style = MaterialTheme.typography.bodySmall)
        }
        debug.messages.forEachIndexed { index, message ->
            Text(
                "${index + 1}. ${roleLabel(message.role)} · ${message.content.length} 字",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 6.dp)
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            ) {
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun WorldBookTab(debug: PromptDebug) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        if (debug.world.isEmpty()) {
            Text(
                "这张卡没有世界书条目",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        Text(
            "命中 ${debug.activated} 条 · 世界书占用 ${debug.worldTokens} tokens（预算 ${debug.worldBudget}）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(6.dp))
        debug.world.forEach { hit ->
            WorldHitRow(hit)
        }
    }
}

@Composable
private fun WorldHitRow(hit: WorldHit) {
    val color = when (hit.status) {
        "inserted" -> MaterialTheme.colorScheme.primary
        "budget" -> MaterialTheme.colorScheme.error
        "disabled" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "【${hit.statusLabel}】",
                    style = MaterialTheme.typography.labelMedium,
                    color = color
                )
                Text(
                    hit.name.ifBlank { "（未命名条目）" },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                )
                Text(
                    "${hit.tokens} t",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val keysText = buildString {
                append("触发词: ").append(hit.keys.ifBlank { "（无）" })
                if (hit.hitKeys.isNotBlank()) append(" ｜ 命中: ").append(hit.hitKeys)
                append(" ｜ 位置: ").append(positionLabel(hit.position))
                append(" ｜ 顺序: ").append(hit.order)
            }
            Text(
                keysText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hit.reason.isNotBlank()) {
                Text(
                    hit.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun BudgetTab(debug: PromptDebug) {
    val remaining = (debug.contextLimit - debug.totalTokens - debug.outputReserve).coerceAtLeast(0)
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        BudgetRow("上下文上限", debug.contextLimit)
        BudgetRow("输出预留（max_tokens）", debug.outputReserve)
        BudgetRow("系统段（预设+角色卡+世界书）", debug.systemTokens + debug.worldTokens)
        BudgetRow("　其中世界书", debug.worldTokens)
        BudgetRow("历史消息", debug.historyTokens)
        BudgetRow("本次合计", debug.totalTokens)
        BudgetRow("剩余可用", remaining)
        Spacer(Modifier.height(8.dp))
        Text(
            "模型：${debug.model.ifBlank { "未知" }} · 世界书预算为上下文的 25%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BudgetRow(label: String, value: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            "$value tokens",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

private fun roleLabel(role: String): String = when (role) {
    "system" -> "系统"
    "user" -> "用户"
    "assistant" -> "角色"
    else -> role
}

private fun positionLabel(position: String): String = when (position) {
    "before_char" -> "角色描述前"
    "after_char" -> "角色描述后"
    "before_system" -> "系统提示前"
    "after_system" -> "系统提示后"
    "in_history" -> "插入历史"
    "example_start" -> "示例开头"
    "example_end" -> "示例结尾"
    "greeting_end" -> "开场白结尾"
    else -> position.ifBlank { "默认" }
}
