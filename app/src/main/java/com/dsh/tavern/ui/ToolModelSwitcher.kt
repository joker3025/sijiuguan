package com.dsh.tavern.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.dsh.tavern.data.ModelCatalog

/**
 * 通用「AI 工具模型」切换条：工坊、角色卡编辑器、世界书编辑/收藏库共用。
 * 只影响工坊随机/原文分析/世界书 AI/卡片字段改写，不影响聊天对话模型。
 */
@Composable
fun ToolModelBar(
    ui: UiState,
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    var showDialog by remember { mutableStateOf(false) }
    val providerIndex = ui.settings.toolProviderIndex
        .takeIf { it in ui.settings.providers.indices }
        ?: ui.settings.activeProvider.coerceIn(0, (ui.settings.providers.size - 1).coerceAtLeast(0))
    val provider = ui.settings.providers.getOrNull(providerIndex)
    val modelShown = ui.settings.toolModel.ifBlank { provider?.model.orEmpty() }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 2.dp else 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "AI 模型：${provider?.name.orEmpty()} / $modelShown",
                    style = MaterialTheme.typography.labelMedium
                )
                if (!compact) {
                    Text(
                        if (ui.settings.toolModel.isBlank()) {
                            "跟随聊天模型（点击可单独指定）"
                        } else {
                            "独立模型（只影响工坊/原文分析/世界书/卡片改写）"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = { showDialog = true }) {
                Text("切换 API 模型", style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    if (showDialog) {
        ToolModelDialog(
            ui = ui,
            vm = vm,
            initialProvider = providerIndex,
            initialModel = modelShown,
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun ToolModelDialog(
    ui: UiState,
    vm: MainViewModel,
    initialProvider: Int,
    initialModel: String,
    onDismiss: () -> Unit
) {
    var pickProvider by remember { mutableStateOf(initialProvider) }
    var pickModel by remember { mutableStateOf(initialModel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切换 AI 工具模型") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("供应商", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ui.settings.providers.forEachIndexed { index, provider ->
                        FilterChip(
                            selected = pickProvider == index,
                            onClick = {
                                pickProvider = index
                                pickModel = provider.model
                            },
                            label = { Text(provider.name.ifBlank { "供应商 ${index + 1}" }) }
                        )
                    }
                }
                OutlinedTextField(
                    value = pickModel,
                    onValueChange = { pickModel = it },
                    label = { Text("模型 ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ModelCatalog.knownIds.forEach { id ->
                        FilterChip(
                            selected = pickModel == id,
                            onClick = { pickModel = id },
                            label = { Text(id) }
                        )
                    }
                }
                ui.settings.providers.getOrNull(pickProvider)?.let { provider ->
                    OutlinedButton(
                        onClick = { vm.fetchModels(provider) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("拉取该供应商的模型列表") }
                }
                if (ui.modelOptions.isNotEmpty()) {
                    Text("供应商返回的模型（点选）：", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ui.modelOptions.take(30).forEach { id ->
                            FilterChip(
                                selected = pickModel == id,
                                onClick = { pickModel = id },
                                label = { Text(id) }
                            )
                        }
                    }
                }
                Text(
                    "只改工坊/原文分析/世界书/卡片改写用的模型，不影响聊天对话模型；Base URL / Key 用所选供应商。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveToolModel(pickProvider, pickModel)
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    vm.saveToolModel(-1, "", followChat = true)
                    onDismiss()
                }) { Text("跟随聊天") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
