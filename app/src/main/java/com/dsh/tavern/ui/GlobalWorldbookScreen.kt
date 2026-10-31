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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dsh.tavern.data.WorldEntry

/**
 * E1：跨卡共享的全局世界书。
 * 这些条目对所有角色卡生效，与卡片世界书一起参与触发、预算与诊断。
 */
@Composable
fun GlobalWorldbookScreen(ui: UiState, vm: MainViewModel) {
    var editing by remember { mutableStateOf<WorldEntry?>(null) }
    var deleting by remember { mutableStateOf<WorldEntry?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        TavernBar(
            title = "全局世界书",
            onBack = { vm.back() },
            actions = {
                TextButton(onClick = {
                    editing = WorldEntry(name = "全局条目 ${ui.globalEntries.size + 1}")
                }) { Text("添加") }
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "全局条目对所有角色卡生效，和卡片自己的世界书一起参与关键词触发与 token 预算；在聊天页 Prompt 调试里会标「全局」。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (ui.globalEntries.isEmpty()) {
                EmptyView("还没有全局条目", "点右上角「添加」，例如世界观、通用规则、称呼习惯")
            }
            ui.globalEntries.forEach { entry ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        entry.name.ifBlank { "未命名条目" },
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (entry.constant) "常驻" else if (entry.regex) "正则触发" else "触发",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
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
                                onCheckedChange = { vm.toggleGlobalEntry(entry, it) }
                            )
                        }
                        Row {
                            TextButton(onClick = { editing = entry }) { Text("编辑") }
                            TextButton(onClick = { deleting = entry }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editing?.let { entry ->
        WorldEntryDialog(
            entry = entry,
            onDismiss = { editing = null },
            onSave = {
                vm.saveGlobalEntry(it)
                editing = null
            },
            vm = vm,
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
    deleting?.let { entry ->
        ConfirmDialog(
            title = "删除全局条目",
            text = "确定删除「${entry.name}」？所有卡片将不再使用它。",
            onConfirm = {
                vm.deleteGlobalEntry(entry)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}
