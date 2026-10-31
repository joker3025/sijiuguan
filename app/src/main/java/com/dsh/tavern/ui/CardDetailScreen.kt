package com.dsh.tavern.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.tavern.data.CharacterCard
import com.dsh.tavern.data.Chat
import com.dsh.tavern.data.PanelTemplate
import com.dsh.tavern.data.TokenEstimator
import com.dsh.tavern.data.WorldEntry
import com.dsh.tavern.data.WorldbookCollection

@Composable
fun CardDetailScreen(ui: UiState, vm: MainViewModel, cardId: Long) {
    val detail = ui.detail
    if (detail == null) {
        Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
            TavernBar(title = "角色卡", onBack = { vm.back() })
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    var showGreetings by remember { mutableStateOf(false) }
    var confirmDeleteCard by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<WorldEntry?>(null) }
    var showWbPicker by remember { mutableStateOf(false) }
    var editingPanel by remember { mutableStateOf(false) }
    var renamingChat by remember { mutableStateOf<Chat?>(null) }
    var deletingChat by remember { mutableStateOf<Chat?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) vm.exportCard(cardId, uri)
    }
    val pngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) vm.exportCardPng(cardId, uri)
    }
    var shareIncludeChats by remember { mutableStateOf(true) }
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) vm.exportSharePack(cardId, shareIncludeChats, uri)
    }
    val wbExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) vm.exportWorldbook(cardId, uri)
    }
    val wbImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.importWorldbook(cardId, uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        TavernBar(
            title = detail.card.name,
            onBack = { vm.back() },
            actions = {
                TextButton(onClick = { vm.openCardEditor(cardId) }) { Text("编辑") }
                var exportMenu by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { exportMenu = true }) { Text("导出") }
                    DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("导出 JSON") },
                            onClick = {
                                exportMenu = false
                                exportLauncher.launch(detail.card.name.take(20) + ".json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导出 PNG 角色卡") },
                            onClick = {
                                exportMenu = false
                                pngLauncher.launch(detail.card.name.take(20) + ".png")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导出分享包（卡 + 聊天记录）") },
                            onClick = {
                                exportMenu = false
                                shareIncludeChats = true
                                shareLauncher.launch(detail.card.name.take(20) + "-分享包.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导出分享包（仅角色卡）") },
                            onClick = {
                                exportMenu = false
                                shareIncludeChats = false
                                shareLauncher.launch(detail.card.name.take(20) + "-分享包.json")
                            }
                        )
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CardHeader(
                    detail = detail,
                    onNewChat = { showGreetings = true },
                    onDelete = { confirmDeleteCard = true }
                )
            }
            item { CardQualityCard(detail) }
            item { SectionCard("角色卡介绍", detail.card.creatorNotes) }
            item { SectionCard("详细描述", detail.card.description) }
            item { SectionCard("性格", detail.card.personality) }
            item { SectionCard("场景", detail.card.scenario) }
            item { SectionCard("系统指令", detail.card.systemPrompt) }
            item { SectionCard("开场白", detail.card.firstMes) }
            if (detail.card.alternateGreetings.isNotEmpty()) {
                item {
                    SectionCard(
                        "备选开场白",
                        detail.card.alternateGreetings.mapIndexed { index, s ->
                            "备选 ${index + 1}：$s"
                        }.joinToString("\n\n")
                    )
                }
            }
            item {
                WorldBookSection(
                    detail = detail,
                    vm = vm,
                    onEdit = { entry -> editingEntry = entry },
                    onImport = { wbImportLauncher.launch(arrayOf("application/json", "*/*")) },
                    onExport = { wbExportLauncher.launch(detail.card.name.take(20) + "-worldbook.json") },
                    onLibrary = { vm.openWorldbookLibrary(cardId) },
                    onPick = { showWbPicker = true }
                )
            }
            item {
                PanelTemplateSection(
                    templateJson = detail.card.statusTemplate,
                    onEdit = { editingPanel = true }
                )
            }
            item {
                ChatSection(
                    detail = detail,
                    vm = vm,
                    onNewChat = { showGreetings = true },
                    onRename = { renamingChat = it },
                    onDelete = { deletingChat = it }
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showGreetings) {
        GreetingDialog(
            card = detail.card,
            onSelect = { index ->
                showGreetings = false
                vm.createChat(detail.card.id, index)
            },
            onDismiss = { showGreetings = false }
        )
    }
    editingEntry?.let { entry ->
        WorldEntryDialog(
            entry = entry,
            onDismiss = { editingEntry = null },
            onSave = {
                vm.saveWorldEntry(it)
                editingEntry = null
            },
            vm = vm,
            card = detail.card,
            cardEntries = detail.entries,
            collections = ui.worldbookCollections,
            onSaveToLibrary = { savedEntry, collectionId ->
                vm.saveWorldEntryToLibrary(savedEntry, collectionId)
            },
            onSaveToNewCollection = { savedEntry, name ->
                vm.saveWorldEntryToNewCollection(savedEntry, name)
            },
            uiState = ui
        )
    }
    if (showWbPicker) {
        WorldbookPickerDialog(
            collections = ui.worldbookCollections,
            vm = vm,
            onConfirm = { picked ->
                vm.importWorldbookEntriesToCard(cardId, picked)
                showWbPicker = false
            },
            onDismiss = { showWbPicker = false }
        )
    }
    if (editingPanel) {
        PanelTemplateDialog(
            initial = detail.card.statusTemplate,
            onSave = { json ->
                vm.savePanelTemplate(cardId, json)
                editingPanel = false
            },
            onDismiss = { editingPanel = false }
        )
    }
    renamingChat?.let { chat ->
        InputDialog(
            title = "重命名对话",
            initial = chat.title,
            label = "对话名称",
            onConfirm = {
                vm.renameChat(chat.id, it.ifBlank { chat.title })
                renamingChat = null
            },
            onDismiss = { renamingChat = null }
        )
    }
    deletingChat?.let { chat ->
        ConfirmDialog(
            title = "删除对话",
            text = "将删除该对话的全部消息，且不可恢复。",
            onConfirm = {
                vm.deleteChat(chat.id, cardId)
                deletingChat = null
            },
            onDismiss = { deletingChat = null }
        )
    }
    if (confirmDeleteCard) {
        ConfirmDialog(
            title = "删除角色卡",
            text = "将删除角色卡、世界书和全部对话记录，且不可恢复。",
            onConfirm = {
                confirmDeleteCard = false
                vm.deleteCard(cardId)
            },
            onDismiss = { confirmDeleteCard = false }
        )
    }
}

@Composable
private fun CardHeader(
    detail: CardDetailData,
    onNewChat: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardAvatar(detail.card.avatar, detail.card.name, 76.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(detail.card.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    val meta = listOf(detail.card.creator, detail.card.characterVersion)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (detail.card.tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                TagLine(detail.card.tags)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNewChat, modifier = Modifier.weight(1f)) {
                    Text("＋ 新对话")
                }
                OutlinedButton(onClick = onDelete) {
                    Text("删除")
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "世界书 ${detail.entries.size} 条 · 对话 ${detail.chats.size} 个",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CardQualityCard(detail: CardDetailData) {
    val card = detail.card
    val cardTokens = TokenEstimator.estimate(
        listOf(
            card.description,
            card.personality,
            card.scenario,
            card.systemPrompt,
            card.firstMes,
            card.mesExample,
            card.postHistoryInstructions
        ).joinToString("")
    )
    val worldTokens = detail.entries.sumOf { TokenEstimator.estimate(it.content) }
    val warnings = buildList {
        if (card.firstMes.isBlank()) add("缺少开场白")
        if (card.description.isBlank()) add("缺少详细描述")
        if (card.personality.isBlank()) add("缺少性格描述")
        if (detail.entries.isEmpty()) add("没有世界书条目（可选）")
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "卡片检查",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                "角色卡字段 ≈ $cardTokens tokens + 世界书 ≈ $worldTokens tokens（不含历史与输出预留）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (warnings.isEmpty()) {
                Text(
                    "字段完整，可以直接开聊。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                warnings.forEach { warning ->
                    Text(
                        "· $warning",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WorldBookSection(
    detail: CardDetailData,
    vm: MainViewModel,
    onEdit: (WorldEntry) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onLibrary: () -> Unit,
    onPick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "世界书",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${detail.entries.count { it.enabled }}/${detail.entries.size} 启用",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onLibrary) { Text("世界书库") }
                TextButton(onClick = onPick) { Text("从收藏选择") }
                TextButton(onClick = onImport) { Text("导入") }
                TextButton(onClick = onExport) { Text("导出") }
            }
            Spacer(Modifier.height(4.dp))
            if (detail.entries.isEmpty()) {
                Text(
                    "（未导入世界书）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            detail.entries.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onEdit(entry) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                entry.name.ifBlank { "条目" },
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (entry.constant) "常驻" else "触发",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.constant) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                }
                            )
                        }
                        if (!entry.constant && entry.keys.isNotEmpty()) {
                            Text(
                                entry.keys.joinToString("、").take(60),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Switch(
                        checked = entry.enabled,
                        onCheckedChange = { vm.toggleWorldEntry(entry, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatSection(
    detail: CardDetailData,
    vm: MainViewModel,
    onNewChat: () -> Unit,
    onRename: (Chat) -> Unit,
    onDelete: (Chat) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "对话记录",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onNewChat) { Text("＋ 新对话") }
            }
            if (detail.chats.isEmpty()) {
                Text(
                    "还没有对话，点「新对话」开始。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            detail.chats.forEach { chat ->
                ChatRow(chat, vm, onRename, onDelete)
            }
        }
    }
}

@Composable
private fun ChatRow(
    chat: Chat,
    vm: MainViewModel,
    onRename: (Chat) -> Unit,
    onDelete: (Chat) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { vm.navigate(Screen.Chat(chat.id)) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                chat.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                chat.lastMessage.ifBlank { "（空对话）" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            formatTime(chat.updatedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box {
            TextButton(onClick = { menu = true }) { Text("⋯", fontSize = 18.sp) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    onClick = { menu = false; onRename(chat) }
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    onClick = { menu = false; onDelete(chat) }
                )
            }
        }
    }
}

@Composable
private fun GreetingDialog(
    card: CharacterCard,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = mutableListOf("默认开场白" to card.firstMes)
    card.alternateGreetings.forEachIndexed { index, text ->
        options.add("备选 ${index + 1}" to text)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择开场白") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                options.forEachIndexed { index, option ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                option.first,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                option.second.take(120).ifBlank { "（空）" },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun WorldEntryDialog(
    entry: WorldEntry,
    onDismiss: () -> Unit,
    onSave: (WorldEntry) -> Unit,
    vm: MainViewModel? = null,
    card: CharacterCard? = null,
    cardEntries: List<WorldEntry> = emptyList(),
    collections: List<WorldbookCollection> = emptyList(),
    onSaveToLibrary: ((WorldEntry, Long) -> Unit)? = null,
    onSaveToNewCollection: ((WorldEntry, String) -> Unit)? = null,
    uiState: UiState? = null
) {
    var name by remember { mutableStateOf(entry.name) }
    var keys by remember { mutableStateOf(entry.keys.joinToString("，")) }
    var secondaryKeys by remember { mutableStateOf(entry.secondaryKeys.joinToString("，")) }
    var content by remember { mutableStateOf(entry.content) }
    var constant by remember { mutableStateOf(entry.constant) }
    var regex by remember { mutableStateOf(entry.regex) }
    var priority by remember { mutableStateOf(entry.priority.toString()) }
    var insertion by remember { mutableStateOf(entry.insertionOrder.toString()) }
    var position by remember { mutableStateOf(entry.position) }
    var probability by remember { mutableStateOf(entry.probability.toString()) }
    var depth by remember { mutableStateOf(entry.depth.toString()) }
    var aiRequirement by remember { mutableStateOf("") }
    var aiLoading by remember { mutableStateOf(false) }
    var showNewCollection by remember { mutableStateOf(false) }
    var structureWarnings by remember { mutableStateOf(emptyList<String>()) }

    fun validateStructure(): List<String> {
        val warnings = mutableListOf<String>()
        val validPositions = setOf(
            "before_system", "before_char", "after_char", "after_system",
            "in_history", "example_start", "example_end", "greeting_end"
        )
        if (position !in validPositions) {
            warnings.add("插入位置「$position」无效，建议改为「角色描述后」")
        }
        val keyList = keys.split("，", ",", "、").map { it.trim() }.filter { it.isNotEmpty() }
        if (!constant && keyList.isEmpty()) {
            warnings.add("非从常驻条目但没有触发词，将永远不会命中（可改为常驻或补触发词）")
        }
        if (!constant && keyList.size > 8) {
            warnings.add("触发词 ${keyList.size} 个偏多，建议 3~6 个")
        }
        if (constant && regex) {
            warnings.add("常驻条目不需要正则触发，建议关闭正则")
        }
        val p = probability.toIntOrNull()
        if (p == null || p !in 0..100) warnings.add("条件概率应在 0~100")
        val d = depth.toIntOrNull()
        if (d == null || d < 0) warnings.add("插入深度应 ≥ 0")
        return warnings
    }

    fun autoFixStructure() {
        val validPositions = setOf(
            "before_system", "before_char", "after_char", "after_system",
            "in_history", "example_start", "example_end", "greeting_end"
        )
        if (position !in validPositions) position = "after_char"
        val keyList = keys.split("，", ",", "、").map { it.trim() }.filter { it.isNotEmpty() }
        if (!constant && keyList.isEmpty()) constant = true
        if (constant) regex = false
        probability = (probability.toIntOrNull() ?: 100).coerceIn(0, 100).toString()
        depth = (depth.toIntOrNull() ?: 4).coerceAtLeast(0).toString()
        structureWarnings = emptyList()
    }

    fun currentEntry(): WorldEntry {
        val newKeys = keys.split("，", ",", "、")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val newSecondary = secondaryKeys.split("，", ",", "、")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return entry.copy(
            name = name.ifBlank { "条目" },
            keys = newKeys,
            secondaryKeys = newSecondary,
            content = content,
            constant = constant,
            selective = !constant,
            priority = priority.toIntOrNull() ?: entry.priority,
            insertionOrder = insertion.toIntOrNull() ?: entry.insertionOrder,
            position = position,
            probability = (probability.toIntOrNull() ?: entry.probability).coerceIn(0, 100),
            depth = (depth.toIntOrNull() ?: entry.depth).coerceAtLeast(0),
            regex = regex
        )
    }

    val positions = listOf(
        "before_system" to "系统提示前",
        "before_char" to "角色描述前",
        "after_char" to "角色描述后",
        "after_system" to "系统提示后",
        "in_history" to "插入历史",
        "example_start" to "示例开头",
        "example_end" to "示例结尾",
        "greeting_end" to "开场白结尾"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑世界书条目") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (vm != null && uiState != null) {
                    ToolModelBar(ui = uiState, vm = vm, compact = true)
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("条目名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keys,
                    onValueChange = { keys = it },
                    label = { Text("触发词（逗号/顿号分隔）") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = secondaryKeys,
                    onValueChange = { secondaryKeys = it },
                    label = { Text("辅助关键词（可选，至少命中一个才触发）") },
                    minLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("条目内容") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )
                if (vm != null && card != null) {
                    Text(
                        "AI 生成 / 改写本条（会消耗 token）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    OutlinedTextField(
                        value = aiRequirement,
                        onValueChange = { aiRequirement = it },
                        label = { Text("你的要求（如：写亲吻/舌吻的描写；或：保留原意改写得更露骨）") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            aiLoading = true
                            val requirement = buildString {
                                if (content.isNotBlank()) {
                                    append("在保留现有条目设定的基础上改写/扩写下面这条世界书内容：\n")
                                    append(content)
                                    append("\n")
                                }
                                append("用户要求：")
                                append(aiRequirement.ifBlank { "加强描写，保持与角色卡设定一致" })
                            }
                            vm.generateWorldbookEntryAI(card, cardEntries, requirement) { generated ->
                                if (generated.name.isNotBlank()) name = generated.name
                                if (generated.keys.isNotEmpty()) keys = generated.keys.joinToString("，")
                                if (generated.content.isNotBlank()) content = generated.content
                                if (generated.position.isNotBlank()) position = generated.position
                                constant = generated.constant
                                probability = generated.probability.toString()
                                aiLoading = false
                                aiRequirement = ""
                                structureWarnings = validateStructure()
                            }
                        },
                        enabled = !aiLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (aiLoading) "生成中…" else "✨ 让 AI 生成/改写条目内容") }
                    if (structureWarnings.isNotEmpty()) {
                        Text(
                            "⚠ AI 改写后的结构检查",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFC107)
                        )
                        structureWarnings.forEach { warning ->
                            Text(
                                "· $warning",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFC107)
                            )
                        }
                        Row {
                            TextButton(onClick = { structureWarnings = emptyList() }) { Text("保留") }
                            TextButton(onClick = { autoFixStructure() }) { Text("一键修正") }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "常驻模式（无需触发词，始终注入）",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Switch(checked = constant, onCheckedChange = { constant = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "触发词按正则匹配（每行一条正则，适合复杂触发）",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Switch(checked = regex, onCheckedChange = { regex = it })
                }
                Text("插入位置", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    positions.forEach { (value, label) ->
                        FilterChip(
                            selected = position == value,
                            onClick = { position = value },
                            label = { Text(label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priority,
                        onValueChange = { priority = it.filter { c -> c.isDigit() } },
                        label = { Text("priority（同顺序下越小越靠前）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = insertion,
                        onValueChange = { insertion = it.filter { c -> c.isDigit() } },
                        label = { Text("插入顺序") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = probability,
                        onValueChange = { probability = it.filter { c -> c.isDigit() } },
                        label = { Text("条件概率 %") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = depth,
                        onValueChange = { depth = it.filter { c -> c.isDigit() } },
                        label = { Text("插入深度（in_history 用）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(currentEntry()) }) { Text("保存") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onSaveToLibrary != null) {
                    var collectionMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(
                            onClick = { collectionMenu = true }
                        ) { Text("★ 收藏") }
                        DropdownMenu(
                            expanded = collectionMenu,
                            onDismissRequest = { collectionMenu = false }
                        ) {
                            collections.forEach { collection ->
                                DropdownMenuItem(
                                    text = { Text("${collection.name}（${collection.entryCount}）") },
                                    onClick = {
                                        collectionMenu = false
                                        onSaveToLibrary(currentEntry(), collection.id)
                                    }
                                )
                            }
                            if (onSaveToNewCollection != null) {
                                DropdownMenuItem(
                                    text = { Text("＋ 新建分组并收藏…") },
                                    onClick = {
                                        collectionMenu = false
                                        showNewCollection = true
                                    }
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )

    if (showNewCollection && onSaveToNewCollection != null) {
        InputDialog(
            title = "新建分组并收藏",
            initial = "",
            label = "分组名称（如：通用亲密描写 / 世界观规则）",
            onConfirm = { name ->
                showNewCollection = false
                onSaveToNewCollection(currentEntry(), name)
            },
            onDismiss = { showNewCollection = false }
        )
    }
}

@Composable
private fun PanelTemplateSection(templateJson: String, onEdit: () -> Unit) {
    val template = remember(templateJson) { PanelTemplate.parse(templateJson) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "互动面板",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onEdit) {
                    Text(if (template == null) "配置" else "编辑")
                }
            }
            if (template == null) {
                Text(
                    "未配置。配置后模型每轮会更新状态 / 关系 / 背包，并给出可点的剧情选项。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "已配置 ${template.panels.size} 个面板 · ${template.fieldCount()} 个字段",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                template.panels.forEach { panel ->
                    Text(
                        "· ${panel.title}：${panel.fields.joinToString("、") { it.name }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelTemplateDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("互动面板模板（JSON）") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    minLines = 8,
                    maxLines = 16,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        text = PanelTemplate.EXAMPLE
                        error = null
                    }) { Text("填入示例") }
                    TextButton(onClick = {
                        text = ""
                        error = null
                    }) { Text("清空") }
                    TextButton(onClick = {
                        error = if (text.isBlank() || PanelTemplate.parse(text) != null) {
                            null
                        } else {
                            "解析失败：需要 {\"panels\":[{\"title\":\"...\",\"fields\":[{\"name\":\"...\"}]}]}"
                        }
                    }) { Text("校验") }
                }
                Text(
                    "模型每轮会在回复末尾输出 <panel>{...}</panel>，客户端解析后显示在聊天页；正文里不会保留这个块。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isBlank()) {
                    onSave("")
                } else if (PanelTemplate.parse(text) == null) {
                    error = "JSON 格式不正确，未保存"
                } else {
                    onSave(text)
                }
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
