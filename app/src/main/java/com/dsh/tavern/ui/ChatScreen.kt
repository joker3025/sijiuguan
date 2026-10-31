package com.dsh.tavern.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.dsh.tavern.data.AppSettings
import com.dsh.tavern.data.ChatMemory
import com.dsh.tavern.data.Checkpoint
import com.dsh.tavern.data.CostCalculator
import com.dsh.tavern.data.Message
import com.dsh.tavern.data.ModelCatalog
import com.dsh.tavern.data.Preset
import com.dsh.tavern.data.ProviderConfig
import org.json.JSONObject

private fun formatYuan(value: Double): String = String.format(java.util.Locale.US, "%.4f", value)

private fun variablesToJson(variables: Map<String, String>): String {
    val obj = JSONObject()
    variables.forEach { (name, value) -> obj.put(name, value) }
    return obj.toString(2)
}

@Composable
fun ChatScreen(ui: UiState, vm: MainViewModel, chatId: Long) {
    val data = ui.chat
    var input by remember(chatId) { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var deletingMessage by remember { mutableStateOf<Message?>(null) }
    var showPresetPicker by remember(chatId) { mutableStateOf(false) }
    var showModelPicker by remember(chatId) { mutableStateOf(false) }
    var showMemory by remember(chatId) { mutableStateOf(false) }
    var editingMemory by remember(chatId) { mutableStateOf(false) }
    var showTools by remember(chatId) { mutableStateOf(false) }
    var editingPanelState by remember(chatId) { mutableStateOf(false) }
    var editingVariables by remember(chatId) { mutableStateOf(false) }
    var checkpointTarget by remember { mutableStateOf<Message?>(null) }
    var restoringCheckpoint by remember { mutableStateOf<Checkpoint?>(null) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val exportChatLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri -> if (uri != null) vm.exportChatMarkdown(chatId, uri) }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
    ) {
        TavernBar(
            title = if (data != null && data.groupCards.size > 1) {
                "群聊：" + data.groupCards.take(3).joinToString("+") { it.name }
            } else {
                data?.card?.name ?: "对话"
            },
            onBack = { vm.back() },
            actions = {
                TextButton(
                    onClick = { vm.previewPrompt() },
                    enabled = data != null && !ui.stream.streaming
                ) { Text("Prompt") }
                TextButton(
                    onClick = { vm.regenerateLast() },
                    enabled = data != null && !ui.stream.streaming &&
                        data.messages.lastOrNull()?.role == "assistant"
                ) { Text("重生成") }
            }
        )

        if (data == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        val streaming = ui.stream.streaming && ui.stream.chatId == chatId
        val lastMessageId = data.messages.lastOrNull()?.id
        // 正在重生成/继续写某条消息时，先隐藏旧气泡，避免和流式气泡重复
        val visibleMessages =
            if (streaming && ui.stream.targetMessageId > 0) {
                data.messages.filterNot { it.id == ui.stream.targetMessageId }
            } else {
                data.messages
            }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (data.hasMore) {
                item(key = "load-earlier") {
                    TextButton(
                        onClick = { vm.loadEarlier(chatId) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("加载更早的消息") }
                }
            }
            items(visibleMessages, key = { it.id }) { message ->
                val isUser = message.role == "user"
                val messageCost = CostCalculator.cost(
                    ui.settings, message.promptTokens, message.completionTokens
                )
                MessageBubble(
                    message = message,
                    isUser = isUser,
                    showReasoning = ui.settings.reasoningDisplay,
                    canRegenerate = !streaming && !isUser,
                    canContinue = !streaming && !isUser && message.id == lastMessageId,
                    canShowPrompt = !isUser && message.debug.isNotBlank(),
                    canBranch = !streaming,
                    canCheckpoint = !streaming,
                    onRegenerate = { vm.regenerateMessage(message.id) },
                    onContinue = { vm.continueMessage(message.id) },
                    onShowPrompt = { vm.openMessageDebug(message.id) },
                    onBranch = { vm.branchFromMessage(message.id) },
                    onCheckpoint = { checkpointTarget = message },
                    onPrevVariant = { vm.switchVariant(message.id, -1) },
                    onNextVariant = { vm.switchVariant(message.id, 1) },
                    onEdit = { editingMessage = message },
                    onDelete = { deletingMessage = message },
                    onCopy = { clipboard.setText(AnnotatedString(message.displayContent)) },
                    charName = data.card.name,
                    userName = ui.settings.personaName,
                    costText = if (messageCost > 0) "≈ ¥${formatYuan(messageCost)}" else ""
                )
            }
            if (streaming) {
                item(key = "streaming") {
                    StreamingBubble(ui.stream, ui.settings.reasoningDisplay)
                }
            }
            item(key = "bottom-spacer") { Spacer(Modifier.height(8.dp)) }
        }

        LaunchedEffect(data.messages.size, ui.stream.content.length, ui.stream.reasoning.length) {
            val count = listState.layoutInfo.totalItemsCount
            if (count > 0) listState.animateScrollToItem(count - 1)
        }

        val debug = data.debug
        Column(Modifier.padding(horizontal = 14.dp)) {
            Text(
                "预算：系统 ${debug.systemTokens + debug.worldTokens}（世界书 ${debug.worldTokens}）· " +
                    "历史 ${debug.historyTokens} · 输出预留 ${debug.outputReserve} · 上限 ${debug.contextLimit}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val lastUsage = data.messages.lastOrNull { it.role == "assistant" && it.promptTokens > 0 }
            if (lastUsage != null) {
                val cost = CostCalculator.cost(
                    ui.settings, lastUsage.promptTokens, lastUsage.completionTokens
                )
                Text(
                    "本轮：输入 ${lastUsage.promptTokens} / 输出 ${lastUsage.completionTokens} tokens" +
                        if (cost > 0) " · ≈ ¥${formatYuan(cost)}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                Text(
                    "消息 ${data.messages.size} 条 · 世界书命中 ${data.worldCount} 条" +
                        if (ui.settings.includeUsage) "（本轮用量等待接口返回）" else "（已关闭 usage 统计）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val todayCost = CostCalculator.cost(
                ui.settings, ui.usageStats.todayPrompt, ui.usageStats.todayCompletion
            )
            val sessionCost = data.messages.sumOf { message ->
                CostCalculator.cost(
                    ui.settings, message.promptTokens, message.completionTokens
                )
            }
            if (sessionCost > 0) {
                Text(
                    "本会话：${data.messages.sumOf { (it.promptTokens + it.completionTokens).toLong() }} tokens" +
                        " · ≈ ¥${formatYuan(sessionCost)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (todayCost > 0) {
                Text(
                    "今日：${ui.usageStats.todayTokens} tokens · ≈ ¥${formatYuan(todayCost)}" +
                        if (ui.settings.dailyBudgetYuan > 0) {
                            " / 预算 ¥${formatYuan(ui.settings.dailyBudgetYuan)}"
                        } else {
                            ""
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ui.panelTemplate?.let { template ->
            PanelBar(
                template = template,
                stateJson = ui.panelStateJson,
                onOption = { option -> vm.sendPanelOption(option) },
                onReset = { vm.clearPanelState(chatId) },
                onEdit = { editingPanelState = true }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val presetName = ui.presets.firstOrNull { it.id == data.chat.presetId }?.name
            TextButton(
                onClick = { showPresetPicker = true },
                enabled = !streaming
            ) { Text("预设：${presetName ?: "跟随全局"}") }
            TextButton(
                onClick = { showMemory = true },
                enabled = !streaming
            ) {
                Text(if (ui.memory?.summary.isNullOrBlank()) "记忆：无" else "记忆：${ui.memory!!.summary.length} 字")
            }
            TextButton(
                onClick = { showTools = true },
                enabled = !streaming
            ) { Text("存档/分支") }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { showModelPicker = true },
                enabled = !streaming
            ) { Text("模型：${ui.settings.provider.model}") }
            Text(
                ui.settings.provider.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!streaming) {
            val replies = ui.suggestions.ifEmpty { data.card.suggestedReplies.take(3) }
            SuggestedReplies(replies) { input = it }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("说点什么…") },
                maxLines = 5
            )
            Spacer(Modifier.width(8.dp))
            if (streaming) {
                Button(onClick = { vm.stopGeneration() }) { Text("停止") }
            } else {
                Button(
                    onClick = {
                        val text = input
                        if (text.isNotBlank()) {
                            input = ""
                            vm.sendMessage(text)
                        }
                    },
                    enabled = input.isNotBlank()
                ) { Text("发送") }
            }
        }
    }

    editingMessage?.let { message ->
        val hasLater = data != null &&
            data.messages.indexOfFirst { it.id == message.id } in 0 until data.messages.lastIndex
        EditMessageDialog(
            message = message,
            canRegenerateAfter = message.role == "user" && hasLater,
            onConfirm = { text, regenerateAfter ->
                vm.editMessage(message.id, text, regenerateAfter)
                editingMessage = null
            },
            onDismiss = { editingMessage = null }
        )
    }
    deletingMessage?.let { message ->
        ConfirmDialog(
            title = "删除消息",
            text = "删除后不可恢复。",
            onConfirm = {
                vm.deleteMessage(message.id)
                deletingMessage = null
            },
            onDismiss = { deletingMessage = null }
        )
    }
    ui.promptPreview?.let { preview ->
        PromptDebugDialog(preview = preview, onClose = { vm.closePromptPreview() })
    }
    if (showPresetPicker) {
        PresetPickDialog(
            presets = ui.presets,
            currentId = data?.chat?.presetId ?: 0L,
            onSelect = { presetId ->
                vm.setChatPreset(chatId, presetId)
                showPresetPicker = false
            },
            onManage = {
                showPresetPicker = false
                vm.openPresets()
            },
            onDismiss = { showPresetPicker = false }
        )
    }
    if (showModelPicker) {
        ModelPickDialog(
            providers = ui.settings.providers,
            activeIndex = ui.settings.activeProvider,
            options = ui.modelOptions,
            onPick = { index, model ->
                vm.switchModel(index, model)
                showModelPicker = false
            },
            onFetch = { provider -> vm.fetchModels(provider) },
            onDismiss = { showModelPicker = false }
        )
    }
    if (showMemory) {
        MemoryDialog(
            memory = ui.memory,
            onRefresh = { vm.refreshMemory(chatId) },
            onEdit = {
                showMemory = false
                editingMemory = true
            },
            onClear = { vm.clearMemory(chatId) },
            onDismiss = { showMemory = false }
        )
    }
    if (editingMemory) {
        InputDialog(
            title = "编辑前情提要",
            initial = ui.memory?.summary.orEmpty(),
            label = "摘要内容（留空 = 清空）",
            minLines = 6,
            onConfirm = {
                vm.editMemory(chatId, it)
                editingMemory = false
            },
            onDismiss = { editingMemory = false }
        )
    }
    if (editingPanelState) {
        InputDialog(
            title = "编辑面板状态（JSON）",
            initial = ui.panelStateJson,
            label = "留空 = 清空；格式如 {\"状态\":{\"时间\":\"夜晚\"},\"options\":[\"继续\"]}",
            minLines = 5,
            onConfirm = {
                vm.editPanelState(chatId, it)
                editingPanelState = false
            },
            onDismiss = { editingPanelState = false }
        )
    }
    if (editingVariables) {
        InputDialog(
            title = "编辑变量（JSON）",
            initial = variablesToJson(ui.chatVariables),
            label = "格式如 {\"好感度\":\"35\",\"时间\":\"夜晚\"}；留空 = 清空",
            minLines = 6,
            onConfirm = { text ->
                if (text.isBlank()) {
                    vm.saveVariables(chatId, emptyMap())
                } else {
                    val parsed = runCatching {
                        val obj = JSONObject(text)
                        val map = mutableMapOf<String, String>()
                        obj.keys().forEach { key -> map[key] = obj.optString(key) }
                        map
                    }.getOrNull()
                    if (parsed != null) vm.saveVariables(chatId, parsed)
                }
                editingVariables = false
            },
            onDismiss = { editingVariables = false }
        )
    }
    if (showTools) {
        ChatToolsDialog(
            checkpoints = ui.checkpoints,
            onRestore = { checkpoint ->
                showTools = false
                restoringCheckpoint = checkpoint
            },
            onDelete = { checkpoint -> vm.deleteCheckpoint(checkpoint.id) },
            onRecap = {
                showTools = false
                vm.startNewChatWithRecap()
            },
            onExport = {
                showTools = false
                exportChatLauncher.launch("chat-${chatId}.md")
            },
            onVariables = {
                showTools = false
                editingVariables = true
            },
            onDismiss = { showTools = false }
        )
    }
    checkpointTarget?.let { message ->
        InputDialog(
            title = "保存检查点",
            initial = "检查点 ${ui.checkpoints.size + 1}",
            label = "名称",
            onConfirm = { name ->
                vm.saveCheckpointAt(message.id, name)
                checkpointTarget = null
            },
            onDismiss = { checkpointTarget = null }
        )
    }
    restoringCheckpoint?.let { checkpoint ->
        ConfirmDialog(
            title = "回到检查点",
            text = "会删除「${checkpoint.name}」之后的全部消息，且不可恢复。",
            confirmLabel = "回到这里",
            onConfirm = {
                vm.restoreCheckpoint(checkpoint.id)
                restoringCheckpoint = null
            },
            onDismiss = { restoringCheckpoint = null }
        )
    }
}

@Composable
private fun ModelPickDialog(
    providers: List<ProviderConfig>,
    activeIndex: Int,
    options: List<String>,
    onPick: (Int, String) -> Unit,
    onFetch: (ProviderConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var providerIndex by remember {
        mutableStateOf(activeIndex.coerceIn(0, (providers.size - 1).coerceAtLeast(0)))
    }
    var modelText by remember {
        mutableStateOf(providers.getOrNull(providerIndex)?.model.orEmpty())
    }
    val quickModels = (
        ModelCatalog.knownIds +
            listOf(providers.getOrNull(providerIndex)?.model.orEmpty()) +
            options
        )
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切换模型") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "供应商",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                providers.forEachIndexed { index, provider ->
                    TextButton(onClick = {
                        providerIndex = index
                        modelText = provider.model
                    }) {
                        Text(
                            (if (index == providerIndex) "✓ " else "") +
                                provider.name.ifBlank { "供应商 ${index + 1}" }
                        )
                    }
                }
                Text(
                    "常用模型 / 已拉取模型（V4.1 系列为 1,000,000 上下文）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                quickModels.forEach { id ->
                    val spec = ModelCatalog.specFor(id)
                    TextButton(onClick = { modelText = id }) {
                        Text(
                            (if (id == modelText) "✓ " else "") + id +
                                if (spec != null) {
                                    "（${if (spec.contextLimit >= 1_000_000) "${spec.contextLimit / 1_000_000}M" else "${spec.contextLimit / 1000}K"} 上下文）"
                                } else {
                                    ""
                                }
                        )
                    }
                }
                OutlinedTextField(
                    value = modelText,
                    onValueChange = { modelText = it },
                    label = { Text("模型 ID（可手填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val pickedSpec = ModelCatalog.specFor(modelText)
                if (pickedSpec != null) {
                    Text(
                        "该型号规格：上下文 ${pickedSpec.contextLimit} / 最大输出 ${pickedSpec.maxOutput}；" +
                            "切换后自动套用预算。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = {
                    providers.getOrNull(providerIndex)?.let(onFetch)
                }) { Text("拉取该供应商模型列表") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(providerIndex, modelText) }) { Text("切换") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ChatToolsDialog(
    checkpoints: List<Checkpoint>,
    onRestore: (Checkpoint) -> Unit,
    onDelete: (Checkpoint) -> Unit,
    onRecap: () -> Unit,
    onExport: () -> Unit,
    onVariables: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("存档 / 分支") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "检查点（长按任意消息可保存）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (checkpoints.isEmpty()) {
                    Text(
                        "还没有检查点",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                checkpoints.forEach { checkpoint ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            checkpoint.name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onRestore(checkpoint) }) { Text("回到") }
                        TextButton(onClick = { onDelete(checkpoint) }) {
                            Text("删", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Text(
                    "带前情开新档：把当前剧情压成前情提要，新开一个会话接着玩。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onVariables) { Text("变量") }
                TextButton(onClick = onExport) { Text("导出对话") }
                TextButton(onClick = onRecap) { Text("带前情开新档") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun MemoryDialog(
    memory: ChatMemory?,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("长期记忆（前情提要）") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (memory == null || memory.summary.isBlank()) {
                    Text(
                        "还没有前情提要。历史变长后会在每轮回复后自动生成；也可以点「立即生成」。",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        "覆盖到消息 #${memory.msgUptoId} · ${memory.summary.length} 字",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        memory.summary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Text(
                    "生成后会用它替代较早的历史消息，省 token；内容可以在「编辑」里手动改。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh) { Text("立即生成") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onClear) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

@Composable
private fun PresetPickDialog(
    presets: List<Preset>,
    currentId: Long,
    onSelect: (Long) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择预设") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                PresetOptionRow("跟随全局设置", currentId == 0L) { onSelect(0L) }
                presets.forEach { preset ->
                    PresetOptionRow(
                        "${preset.name}（${preset.style} · temp ${preset.temperature}）",
                        currentId == preset.id
                    ) { onSelect(preset.id) }
                }
                if (presets.isEmpty()) {
                    Text(
                        "还没有预设，去「管理预设」创建一套",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onManage) { Text("管理预设") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun PresetOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onClick() }
    ) {
        Text(
            (if (selected) "✓ " else "") + label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(10.dp)
        )
    }
}

@Composable
private fun EditMessageDialog(
    message: Message,
    canRegenerateAfter: Boolean,
    onConfirm: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(message.id) { mutableStateOf(message.displayContent) }
    var regenerateAfter by remember(message.id) { mutableStateOf(canRegenerateAfter) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (message.role == "user") "编辑用户消息" else "编辑角色回复") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 12
                )
                if (canRegenerateAfter) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = regenerateAfter, onCheckedChange = { regenerateAfter = it })
                        Text(
                            "保存并重新生成后面的回复",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else if (message.role != "user") {
                    Text(
                        "保存后作为新版本，可用气泡里的 ‹ › 切换",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text, regenerateAfter) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun StreamingBubble(stream: StreamState, showReasoning: Boolean) {
    var reasoningOpen by remember { mutableStateOf(false) }
    val label = when (stream.mode) {
        StreamMode.REGENERATE -> "角色（重生成中…）"
        StreamMode.CONTINUE -> "角色（继续写作中…）"
        StreamMode.NEW -> "角色"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (stream.reasoning.isNotBlank() && showReasoning) {
                Text(
                    text = if (reasoningOpen) {
                        "▾ 思考过程"
                    } else {
                        "▸ 思考过程（${stream.reasoning.length} 字）"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.clickable { reasoningOpen = !reasoningOpen }
                )
                if (reasoningOpen) {
                    Text(
                        stream.reasoning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .heightIn(max = 140.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
            RichText(
                text = (stream.content.ifBlank {
                    if (stream.reasoning.isNotBlank()) "（思考中…）" else "…"
                }) + "▍",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
            if (stream.usagePrompt > 0 || stream.usageCompletion > 0) {
                Text(
                    "输入 ${stream.usagePrompt} / 输出 ${stream.usageCompletion} tokens",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
