package com.dsh.tavern.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dsh.tavern.data.CardPromptTemplates
import com.dsh.tavern.data.CharacterCard
import com.dsh.tavern.data.FieldConflict
import com.dsh.tavern.data.PanelTemplate
import com.dsh.tavern.data.WorldEntry

/**
 * P1-9：角色卡编辑器（新建 / 编辑）。
 * 字段按 Character Card V3 组织；世界书条目在保存后到卡片详情页维护。
 */
@Composable
fun CardEditorScreen(ui: UiState, vm: MainViewModel) {
    val initial = ui.editorCard
    if (initial == null) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            TavernBar(title = "角色卡编辑器", onBack = { vm.back() })
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    var draft by remember(initial.id, initial.createdAt) { mutableStateOf(initial) }
    var tagsText by remember(initial.id, initial.createdAt) {
        mutableStateOf(initial.tags.joinToString("，"))
    }
    var greetingsText by remember(initial.id, initial.createdAt) {
        mutableStateOf(initial.alternateGreetings.joinToString("\n"))
    }
    var repliesText by remember(initial.id, initial.createdAt) {
        mutableStateOf(initial.suggestedReplies.joinToString("\n"))
    }
    var entries by remember(initial.id) { mutableStateOf(ui.editorEntries) }
    var editingEntry by remember { mutableStateOf<WorldEntry?>(null) }
    var showWbPicker by remember { mutableStateOf(false) }
    var undoStack by remember(initial.id) { mutableStateOf(listOf<EditorSnapshot>()) }
    var redoStack by remember(initial.id) { mutableStateOf(listOf<EditorSnapshot>()) }
    var conflicts by remember(initial.id) { mutableStateOf(listOf<FieldConflict>()) }
    var aiDialogField by remember { mutableStateOf<String?>(null) }
    var aiRequirement by remember { mutableStateOf("") }
    var aiResult by remember { mutableStateOf("") }
    val context = LocalContext.current
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes != null) draft = draft.copy(avatar = bytes)
        }
    }

    fun save() {
        val card = draft.copy(
            name = draft.name.ifBlank { "未命名角色" },
            tags = splitList(tagsText),
            alternateGreetings = greetingsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
            suggestedReplies = repliesText.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
            updatedAt = System.currentTimeMillis()
        )
        vm.saveCard(card, entries)
    }

    fun snapshot(): EditorSnapshot = EditorSnapshot(draft, tagsText, greetingsText, repliesText)

    fun restore(snapshot: EditorSnapshot) {
        draft = snapshot.card
        tagsText = snapshot.tags
        greetingsText = snapshot.greetings
        repliesText = snapshot.replies
    }

    fun pushUndo() {
        undoStack = undoStack + snapshot()
        redoStack = emptyList()
    }

    fun applyFieldValue(key: String, value: String) {
        when (key) {
            "name" -> draft = draft.copy(name = value)
            "description" -> draft = draft.copy(description = value)
            "personality" -> draft = draft.copy(personality = value)
            "scenario" -> draft = draft.copy(scenario = value)
            "first_mes" -> draft = draft.copy(firstMes = value)
            "system_prompt" -> draft = draft.copy(systemPrompt = value)
            "post_history_instructions" -> draft = draft.copy(postHistoryInstructions = value)
            "mes_example" -> draft = draft.copy(mesExample = value)
            "creator_notes" -> draft = draft.copy(creatorNotes = value)
            "tags" -> {
                tagsText = value
                draft = draft.copy(tags = splitList(value))
            }
            "alternate_greetings" -> {
                greetingsText = value
                draft = draft.copy(
                    alternateGreetings = value.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                )
            }
            else -> Unit
        }
    }

    fun applyConflictSuggestions() {
        val applicable = conflicts.filter { it.suggestion.isNotBlank() }
        if (applicable.isEmpty()) {
            conflicts = emptyList()
            return
        }
        pushUndo()
        applicable.forEach { applyFieldValue(it.field, it.suggestion) }
        conflicts = emptyList()
        vm.notify("已按建议一并修改 ${applicable.size} 处")
    }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        TavernBar(
            title = if (initial.id == 0L) "新建角色卡" else "编辑角色卡",
            onBack = { vm.back() },
            actions = {
                TextButton(onClick = { save() }) { Text("保存") }
            }
        )
        ToolModelBar(
            ui = ui,
            vm = vm,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardAvatar(avatar = draft.avatar, name = draft.name, size = 72.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("头像", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "保存在本地；导出 PNG 角色卡时会作为卡面",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { avatarPicker.launch("image/*") }) { Text("选择图片") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        aiDialogField = "description"
                        aiRequirement = ""
                        aiResult = ""
                    },
                    enabled = !ui.aiBusy,
                    modifier = Modifier.weight(1f)
                ) { Text("✨ AI 改写字段") }
                OutlinedButton(
                    onClick = {
                        if (undoStack.isNotEmpty()) {
                            val previous = undoStack.last()
                            undoStack = undoStack.dropLast(1)
                            redoStack = redoStack + snapshot()
                            restore(previous)
                            conflicts = emptyList()
                        }
                    },
                    enabled = undoStack.isNotEmpty()
                ) { Text("↩") }
                OutlinedButton(
                    onClick = {
                        if (redoStack.isNotEmpty()) {
                            val next = redoStack.last()
                            redoStack = redoStack.dropLast(1)
                            undoStack = undoStack + snapshot()
                            restore(next)
                        }
                    },
                    enabled = redoStack.isNotEmpty()
                ) { Text("↪") }
            }
            Text(
                "AI 改写只改你选的字段；改完会自动检查与其它设定是否冲突。↩/↪ 可撤回或恢复（包括 AI 改写与「一并修改」）。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (conflicts.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "⚠ 检测到设定冲突",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFFFFC107)
                        )
                        conflicts.forEach { conflict ->
                            Text(
                                "${conflict.label}：${conflict.issue}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFC107)
                            )
                        }
                        Row {
                            TextButton(onClick = { conflicts = emptyList() }) { Text("保留冲突") }
                            TextButton(
                                onClick = { applyConflictSuggestions() },
                                enabled = conflicts.any { it.suggestion.isNotBlank() }
                            ) { Text("一并修改") }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text("角色名 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.creator,
                onValueChange = { draft = draft.copy(creator = it) },
                label = { Text("作者") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.characterVersion,
                onValueChange = { draft = draft.copy(characterVersion = it) },
                label = { Text("版本") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
                label = { Text("标签（逗号/顿号分隔）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("设定")
            OutlinedTextField(
                value = draft.creatorNotes,
                onValueChange = { draft = draft.copy(creatorNotes = it) },
                label = { Text("角色卡介绍（给玩家看，AI 不读）") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.description,
                onValueChange = { draft = draft.copy(description = it) },
                label = { Text("详细描述（背景故事/外貌/能力）") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.personality,
                onValueChange = { draft = draft.copy(personality = it) },
                label = { Text("性格 / 说话方式") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.scenario,
                onValueChange = { draft = draft.copy(scenario = it) },
                label = { Text("场景") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.systemPrompt,
                onValueChange = { draft = draft.copy(systemPrompt = it) },
                label = { Text("角色专属系统指令") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.postHistoryInstructions,
                onValueChange = { draft = draft.copy(postHistoryInstructions = it) },
                label = { Text("作者注释（后置指令，可空）") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("开场与示例")
            OutlinedTextField(
                value = draft.firstMes,
                onValueChange = { draft = draft.copy(firstMes = it) },
                label = { Text("开场白") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = greetingsText,
                onValueChange = { greetingsText = it },
                label = { Text("备选开场白（每行一条）") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.mesExample,
                onValueChange = { draft = draft.copy(mesExample = it) },
                label = { Text("对话示例 / 示例对话") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = repliesText,
                onValueChange = { repliesText = it },
                label = { Text("建议回复（每行一条）") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("默认预设（新对话用）")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = draft.defaultPresetId == 0L,
                    onClick = { draft = draft.copy(defaultPresetId = 0L) },
                    label = { Text("跟随全局") }
                )
                ui.presets.forEach { preset ->
                    FilterChip(
                        selected = draft.defaultPresetId == preset.id,
                        onClick = { draft = draft.copy(defaultPresetId = preset.id) },
                        label = { Text(preset.name) }
                    )
                }
            }

            SectionTitle("世界书（${entries.size} 条）")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showWbPicker = true }) { Text("从收藏库选择") }
                TextButton(onClick = {
                    editingEntry = WorldEntry(
                        cardId = initial.id,
                        name = "条目 ${entries.size + 1}"
                    )
                }) { Text("＋ 添加条目") }
            }
            if (entries.isEmpty()) {
                Text(
                    "还没有条目。可以在这里添加，或从收藏库批量选择。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            entries.forEachIndexed { index, entry ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.name.ifBlank { "条目 ${index + 1}" },
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                            Text(
                                if (entry.constant) "常驻" else entry.keys.joinToString("、").take(40),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        TextButton(onClick = { editingEntry = entry }) { Text("编辑") }
                        TextButton(onClick = {
                            entries = entries.toMutableList().also { it.removeAt(index) }
                        }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                        Switch(
                            checked = entry.enabled,
                            onCheckedChange = { enabled ->
                                entries = entries.toMutableList().also { list ->
                                    list[index] = entry.copy(enabled = enabled)
                                }
                            }
                        )
                    }
                }
            }

            SectionTitle("输出后处理 / 初始变量")
            OutlinedTextField(
                value = draft.regexRules,
                onValueChange = { draft = draft.copy(regexRules = it) },
                label = { Text("输出后处理正则（每行 pattern => replacement）") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("对 AI 回复正文做清洗，例如：\\\\*\\\\* => *，或 去掉 <think> 标签") }
            )
            OutlinedTextField(
                value = draft.variablesText,
                onValueChange = { draft = draft.copy(variablesText = it) },
                label = { Text("初始变量（每行 名字=值）") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("新对话会写入会话变量，可在聊天页「存档/分支 → 变量」里改") }
            )

            SectionTitle("互动面板模板（JSON，可空）")
            OutlinedTextField(
                value = draft.statusTemplate,
                onValueChange = { draft = draft.copy(statusTemplate = it) },
                label = { Text("模板 JSON") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { draft = draft.copy(statusTemplate = PanelTemplate.EXAMPLE) }) {
                    Text("填入示例")
                }
                TextButton(onClick = { draft = draft.copy(statusTemplate = "") }) {
                    Text("清空")
                }
            }
            Text(
                "世界书条目请在保存后到卡片详情页编辑；导入的卡会自动带过来。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    aiDialogField?.let { key ->
        val fieldLabel = CardPromptTemplates.fieldLabelOf(key)
        var fieldMenuOpen by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { aiDialogField = null },
            title = { Text("✨ AI 改写：$fieldLabel") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val fieldOptions = listOf(
                        "name" to "角色名",
                        "description" to "详细描述",
                        "personality" to "性格",
                        "scenario" to "场景",
                        "first_mes" to "开场白",
                        "system_prompt" to "系统指令",
                        "post_history_instructions" to "作者注释",
                        "mes_example" to "示例对话",
                        "creator_notes" to "角色卡介绍",
                        "tags" to "标签",
                        "alternate_greetings" to "备选开场白"
                    )
                    Box {
                        OutlinedButton(
                            onClick = { fieldMenuOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("字段：$fieldLabel") }
                        DropdownMenu(
                            expanded = fieldMenuOpen,
                            onDismissRequest = { fieldMenuOpen = false }
                        ) {
                            fieldOptions.forEach { (k, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        aiDialogField = k
                                        aiResult = ""
                                        fieldMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = aiRequirement,
                        onValueChange = { aiRequirement = it },
                        label = { Text("你的要求（可空，例如：更病娇一点 / 加入亲吻描写 / 语气更冷）") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (aiResult.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "改写结果（可继续编辑）",
                            style = MaterialTheme.typography.labelSmall
                        )
                        OutlinedTextField(
                            value = aiResult,
                            onValueChange = { aiResult = it },
                            minLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (aiResult.isBlank()) {
                    TextButton(
                        onClick = {
                            vm.rewriteCardField(draft, entries, key, fieldLabel, aiRequirement) { text ->
                                aiResult = text
                            }
                        },
                        enabled = !ui.aiBusy
                    ) { Text(if (ui.aiBusy) "生成中…" else "让 AI 改写") }
                } else {
                    TextButton(onClick = {
                        val value = aiResult
                        pushUndo()
                        applyFieldValue(key, value)
                        conflicts = emptyList()
                        aiDialogField = null
                        vm.checkCardConflicts(draft, entries, key, value) { list ->
                            conflicts = list
                        }
                        vm.notify("已应用改写")
                    }) { Text("应用") }
                }
            },
            dismissButton = {
                TextButton(onClick = { aiDialogField = null }) { Text("取消") }
            }
        )
    }

    if (showWbPicker) {
        WorldbookPickerDialog(
            collections = ui.worldbookCollections,
            vm = vm,
            onConfirm = { picked ->
                entries = entries + picked
                showWbPicker = false
            },
            onDismiss = { showWbPicker = false }
        )
    }

    editingEntry?.let { entry ->
        WorldEntryDialog(
            entry = entry,
            onDismiss = { editingEntry = null },
            onSave = { saved ->
                val list = entries.toMutableList()
                val index = list.indexOfFirst { it.id != 0L && it.id == saved.id }
                if (index >= 0) list[index] = saved else list.add(saved)
                entries = list
                editingEntry = null
            },
            vm = vm,
            card = draft,
            cardEntries = entries,
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
}

private data class EditorSnapshot(
    val card: CharacterCard,
    val tags: String,
    val greetings: String,
    val replies: String
)

private fun splitList(text: String): List<String> =
    text.split("，", ",", "、", "\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
