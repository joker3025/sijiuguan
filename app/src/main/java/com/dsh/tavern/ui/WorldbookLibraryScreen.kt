package com.dsh.tavern.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.dsh.tavern.data.WorldEntry
import com.dsh.tavern.data.WorldbookCollection

/**
 * 世界书收藏库：条目分组收藏、导入到当前卡、让 API 适配到当前卡（保留用户想要的描写）。
 */
@Composable
fun WorldbookLibraryScreen(ui: UiState, vm: MainViewModel, cardId: Long) {
    var showNewGroup by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    var entriesCache by remember { mutableStateOf<Map<Long, List<WorldEntry>>>(emptyMap()) }
    var adaptTarget by remember { mutableStateOf<WorldbookCollection?>(null) }
    var adaptRequirement by remember {
        mutableStateOf(
            "保留原有描写风格与细节，特别是亲吻、舌吻等亲密描写；只修正与目标角色卡冲突的身份、称呼、关系、世界观。"
        )
    }
    var deleting by remember { mutableStateOf<WorldbookCollection?>(null) }

    LaunchedEffect(Unit) { vm.loadWorldbookCollections() }

    fun toggle(collection: WorldbookCollection) {
        if (expanded.contains(collection.id)) {
            expanded = expanded - collection.id
        } else {
            expanded = expanded + collection.id
            vm.loadWorldbookLibraryEntries(collection.id) { list ->
                entriesCache = entriesCache + (collection.id to list)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        TavernBar(
            title = "世界书收藏库",
            onBack = { vm.back() },
            actions = {
                TextButton(onClick = { showNewGroup = true }) { Text("新建分组") }
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "在这里把满意的世界书条目分组收藏。下次做新卡时可以整组导入；" +
                    "新卡人物设定不同时，用「API 适配」按你的要求改写（例如保留亲吻/舌吻描写，只改冲突部分）。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (ui.worldbookCollections.isEmpty()) {
                EmptyView(
                    "还没有收藏分组",
                    "先在角色卡的世界书条目里点「★ 收藏」，或点右上角「新建分组」"
                )
            }
            ui.worldbookCollections.forEach { collection ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(collection.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${collection.entryCount} 条",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { toggle(collection) }) {
                                Text(if (expanded.contains(collection.id)) "收起" else "展开")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { vm.importWorldbookCollectionToCard(cardId, collection) }) {
                                Text("导入到本卡")
                            }
                            TextButton(
                                onClick = {
                                    adaptTarget = collection
                                    adaptRequirement =
                                        "保留原有描写风格与细节，特别是亲吻、舌吻等亲密描写；只修正与目标角色卡冲突的身份、称呼、关系、世界观。"
                                }
                            ) { Text("API 适配导入") }
                            TextButton(onClick = { deleting = collection }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (expanded.contains(collection.id)) {
                            val entries = entriesCache[collection.id].orEmpty()
                            if (entries.isEmpty()) {
                                Text(
                                    "（空）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            entries.forEach { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            entry.name.ifBlank { "条目" },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            entry.content.take(60),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                    TextButton(onClick = {
                                        vm.deleteWorldbookLibraryEntry(entry.id)
                                        expanded = expanded - collection.id
                                        vm.loadWorldbookCollections()
                                    }) { Text("删", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showNewGroup) {
        InputDialog(
            title = "新建收藏分组",
            initial = "",
            label = "分组名称（如：通用亲密描写 / 世界观规则）",
            onConfirm = {
                vm.createWorldbookCollection(it)
                showNewGroup = false
            },
            onDismiss = { showNewGroup = false }
        )
    }

    deleting?.let { collection ->
        ConfirmDialog(
            title = "删除分组",
            text = "将删除「${collection.name}」及其中 ${collection.entryCount} 条收藏，不可恢复。",
            onConfirm = {
                vm.deleteWorldbookCollection(collection)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }

    adaptTarget?.let { collection ->
        AlertDialog(
            onDismissRequest = { adaptTarget = null },
            title = { Text("API 适配到本卡") },
            text = {
                Column {
                    Text(
                        "把「${collection.name}」的条目按本卡设定改写，你点名保留的描写会尽量保留。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = adaptRequirement,
                        onValueChange = { adaptRequirement = it },
                        label = { Text("保留/修改要求") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.adaptWorldbookCollectionToCard(cardId, collection, adaptRequirement)
                        adaptTarget = null
                    },
                    enabled = !ui.aiBusy
                ) { Text(if (ui.aiBusy) "适配中…" else "开始适配并导入") }
            },
            dismissButton = {
                TextButton(onClick = { adaptTarget = null }) { Text("取消") }
            }
        )
    }
}
