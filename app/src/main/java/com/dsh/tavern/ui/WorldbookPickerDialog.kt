package com.dsh.tavern.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import com.dsh.tavern.data.WorldEntry
import com.dsh.tavern.data.WorldbookCollection

/**
 * 从世界书收藏库选择条目：
 * 先选分组（可整组全选），再勾选单条；确认后返回选中的条目列表。
 */
@Composable
fun WorldbookPickerDialog(
    collections: List<WorldbookCollection>,
    vm: MainViewModel,
    onConfirm: (List<WorldEntry>) -> Unit,
    onDismiss: () -> Unit
) {
    var currentCollection by remember { mutableStateOf<WorldbookCollection?>(null) }
    var entriesCache by remember { mutableStateOf<Map<Long, List<WorldEntry>>>(emptyMap()) }
    var checked by remember { mutableStateOf(setOf<Long>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (currentCollection == null) "从收藏库选择世界书"
                else "选择条目：${currentCollection!!.name}"
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (currentCollection == null) {
                    if (collections.isEmpty()) {
                        Text(
                            "还没有收藏分组：先到世界书条目里点「★ 收藏」并新建分组",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    collections.forEach { collection ->
                        TextButton(
                            onClick = {
                                currentCollection = collection
                                checked = emptySet()
                                if (!entriesCache.containsKey(collection.id)) {
                                    vm.loadWorldbookLibraryEntries(collection.id) { list ->
                                        entriesCache = entriesCache + (collection.id to list)
                                    }
                                }
                            }
                        ) { Text("${collection.name}（${collection.entryCount}）") }
                    }
                } else {
                    val collection = currentCollection!!
                    val list = entriesCache[collection.id].orEmpty()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            currentCollection = null
                            checked = emptySet()
                        }) { Text("← 分组") }
                        TextButton(onClick = {
                            checked = if (checked.size == list.size && list.isNotEmpty()) {
                                emptySet()
                            } else {
                                list.map { it.id }.toSet()
                            }
                        }) { Text(if (checked.size == list.size && list.isNotEmpty()) "取消全选" else "全选本组") }
                    }
                    if (list.isEmpty()) {
                        Text(
                            "加载中…（或该分组为空）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    list.forEach { entry ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked.contains(entry.id),
                                onCheckedChange = { isChecked ->
                                    checked = if (isChecked) {
                                        checked + entry.id
                                    } else {
                                        checked - entry.id
                                    }
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(entry.name.ifBlank { "条目" })
                                Text(
                                    entry.content.take(50),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (currentCollection != null) {
                val list = entriesCache[currentCollection!!.id].orEmpty()
                TextButton(
                    onClick = {
                        onConfirm(list.filter { checked.contains(it.id) })
                    },
                    enabled = checked.isNotEmpty()
                ) { Text("导入选中（${checked.size}）") }
            } else {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
