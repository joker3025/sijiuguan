package com.dsh.tavern.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.dsh.tavern.data.Preset

/**
 * P0-3：预设管理页。
 * 预设 = 系统提示 + 后置指令 + temperature / top_p / max_tokens / 风格。
 * 会话里可单独选一套；不选则跟随设置页的全局提示词。
 */
@Composable
fun PresetScreen(ui: UiState, vm: MainViewModel) {
    var editing by remember { mutableStateOf<Preset?>(null) }
    var deleting by remember { mutableStateOf<Preset?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) vm.exportPresets(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importPresets(uri) }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        TavernBar(
            title = "预设",
            onBack = { vm.back() },
            actions = {
                TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                    Text("导入")
                }
                TextButton(onClick = { exportLauncher.launch("tavern-presets.json") }) {
                    Text("导出")
                }
                TextButton(
                    onClick = {
                        editing = Preset(
                            name = "新预设",
                            systemPrompt = ui.settings.systemPreset,
                            style = "标准"
                        )
                    }
                ) { Text("新建") }
            }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "预设 = 系统提示 + 后置指令 + 温度 / top_p / 最大输出。聊天页顶部「预设」可给单个会话切换；不选则跟随设置页的全局提示词。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (ui.presets.isEmpty()) {
                EmptyView("还没有预设", "点右上角「新建」创建一套")
            }
            ui.presets.forEach { preset ->
                PresetCard(
                    preset = preset,
                    onEdit = { editing = preset },
                    onCopy = {
                        vm.savePreset(
                            preset.copy(
                                id = 0,
                                name = preset.name + " 副本",
                                builtIn = false
                            )
                        )
                    },
                    onDelete = { deleting = preset }
                )
            }
        }
    }

    editing?.let { preset ->
        PresetEditorDialog(
            initial = preset,
            onSave = {
                vm.savePreset(it)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
    deleting?.let { preset ->
        ConfirmDialog(
            title = "删除预设",
            text = "删除「${preset.name}」？用它开始的会话会回退到全局设置。",
            onConfirm = {
                vm.deletePreset(preset)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun PresetCard(
    preset: Preset,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preset.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (preset.builtIn) {
                    Text(
                        "内置",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "风格 ${preset.style}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "temp ${preset.temperature}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "top_p ${preset.topP}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "max ${preset.maxTokens}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                preset.systemPrompt.replace("\n", " ").take(140) +
                    if (preset.systemPrompt.length > 140) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            if (preset.postHistory.isNotBlank()) {
                Text(
                    "后置指令：" + preset.postHistory.replace("\n", " ").take(80),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onCopy) { Text("复制") }
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PresetEditorDialog(
    initial: Preset,
    onSave: (Preset) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var systemPrompt by remember(initial.id) { mutableStateOf(initial.systemPrompt) }
    var postHistory by remember(initial.id) { mutableStateOf(initial.postHistory) }
    var style by remember(initial.id) { mutableStateOf(initial.style) }
    var temperatureText by remember(initial.id) { mutableStateOf(initial.temperature.toString()) }
    var topPText by remember(initial.id) { mutableStateOf(initial.topP.toString()) }
    var maxTokensText by remember(initial.id) { mutableStateOf(initial.maxTokens.toString()) }
    var stopText by remember(initial.id) { mutableStateOf(initial.stopSequences) }
    var frequencyText by remember(initial.id) { mutableStateOf(initial.frequencyPenalty.toString()) }
    var presenceText by remember(initial.id) { mutableStateOf(initial.presencePenalty.toString()) }
    val styles = listOf("标准", "创意", "发散", "抗复读")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "新建预设" else "编辑预设") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("风格", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    styles.forEach { option ->
                        FilterChip(
                            selected = style == option,
                            onClick = { style = option },
                            label = { Text(option) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = temperatureText,
                        onValueChange = { temperatureText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("temperature") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = topPText,
                        onValueChange = { topPText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("top_p") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxTokensText,
                        onValueChange = { maxTokensText = it.filter { c -> c.isDigit() } },
                        label = { Text("max") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = frequencyText,
                        onValueChange = { frequencyText = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
                        label = { Text("frequency_penalty") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = presenceText,
                        onValueChange = { presenceText = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
                        label = { Text("presence_penalty") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = stopText,
                    onValueChange = { stopText = it },
                    label = { Text("停止序列（换行/逗号分隔，可空）") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("系统提示（可用 {{char}} / {{user}}）") },
                    minLines = 5,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = postHistory,
                    onValueChange = { postHistory = it },
                    label = { Text("后置指令（可选，追加在作者注释后）") },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        name = name.ifBlank { "未命名预设" },
                        systemPrompt = systemPrompt,
                        postHistory = postHistory,
                        style = style,
                        temperature = temperatureText.toFloatOrNull()?.coerceIn(0f, 2f)
                            ?: initial.temperature,
                        topP = topPText.toFloatOrNull()?.coerceIn(0.01f, 1f) ?: initial.topP,
                        maxTokens = maxTokensText.toIntOrNull()?.coerceIn(64, 65536)
                            ?: initial.maxTokens,
                        stopSequences = stopText,
                        frequencyPenalty = frequencyText.toFloatOrNull()?.coerceIn(-2f, 2f)
                            ?: initial.frequencyPenalty,
                        presencePenalty = presenceText.toFloatOrNull()?.coerceIn(-2f, 2f)
                            ?: initial.presencePenalty
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
