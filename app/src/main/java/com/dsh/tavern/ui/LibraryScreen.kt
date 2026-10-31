package com.dsh.tavern.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.tavern.data.LibraryItem

@Composable
fun LibraryScreen(ui: UiState, vm: MainViewModel) {
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFromUri(uri)
    }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("recent") }
    var showGroupChat by remember { mutableStateOf(false) }
    var groupSelection by remember { mutableStateOf(setOf<Long>()) }
    val visible = remember(ui.library, query, sort) {
        var list = ui.library
        if (query.isNotBlank()) {
            val keyword = query.trim()
            list = list.filter { item ->
                item.card.name.contains(keyword, ignoreCase = true) ||
                    item.card.tags.any { it.contains(keyword, ignoreCase = true) }
            }
        }
        when (sort) {
            "name" -> list.sortedBy { it.card.name }
            "chats" -> list.sortedByDescending { it.chatCount }
            else -> list.sortedByDescending { it.lastPlayed }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        TavernBar(
            title = "私酒馆",
            actions = {
                TextButton(onClick = { vm.openChatList() }) { Text("对话") }
                TextButton(onClick = { showGroupChat = true }) { Text("群聊") }
                TextButton(onClick = { vm.openWorkshop() }) { Text("工坊") }
                TextButton(onClick = { vm.openNewCard() }) { Text("新建") }
                TextButton(onClick = { vm.navigate(Screen.Settings) }) { Text("设置") }
            }
        )
        Text(
            "本地角色卡 · DeepSeek / OpenAI 兼容 API · 数据保存在本机",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("搜索角色名 / 标签") },
            singleLine = true
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "recent" to "最近",
                "name" to "名称",
                "chats" to "对话数"
            ).forEach { (value, label) ->
                FilterChip(
                    selected = sort == value,
                    onClick = { sort = value },
                    label = { Text(label) }
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f)
            ) {
                Text("＋ 导入角色卡")
            }
            OutlinedButton(onClick = { vm.navigate(Screen.Settings) }) {
                Text("API 设置")
            }
        }

        if (visible.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                if (ui.library.isEmpty()) {
                    EmptyView(
                        "还没有角色卡",
                        "支持 PNG 角色卡（chara 元数据）与 chara_card_v2/v3 JSON。\n点击「导入角色卡」，或在文件管理器里分享文件到本应用。"
                    )
                } else {
                    EmptyView("没有匹配的角色卡", "换个关键词，或把筛选改回「最近」")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visible, key = { it.card.id }) { item ->
                    LibraryRow(item, vm)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showGroupChat) {
        AlertDialog(
            onDismissRequest = { showGroupChat = false },
            title = { Text("创建群聊（选 2~4 张卡）") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (ui.library.isEmpty()) {
                        Text("还没有角色卡")
                    }
                    ui.library.forEach { item ->
                        val checked = groupSelection.contains(item.card.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    groupSelection = if (checked) {
                                        groupSelection - item.card.id
                                    } else {
                                        groupSelection + item.card.id
                                    }
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    groupSelection = if (checked) {
                                        groupSelection - item.card.id
                                    } else {
                                        groupSelection + item.card.id
                                    }
                                }
                            )
                            Text(
                                item.card.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text(
                        "已选 ${groupSelection.size} 张（2~4 张）。群聊里模型会同时扮演这些角色，用【角色名】分段。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = groupSelection.size in 2..4,
                    onClick = {
                        vm.createGroupChat(groupSelection.toList())
                        showGroupChat = false
                        groupSelection = emptySet()
                    }
                ) { Text("开始") }
            },
            dismissButton = {
                TextButton(onClick = { showGroupChat = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun LibraryRow(item: LibraryItem, vm: MainViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) vm.exportCard(item.card.id, uri)
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { vm.navigate(Screen.CardDetail(item.card.id)) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardAvatar(item.card.avatar, item.card.name, 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.card.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall
                )
                if (item.card.tags.isNotEmpty()) {
                    Text(
                        item.card.tags.take(4).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Text(
                    buildString {
                        append("${item.chatCount} 个对话")
                        if (item.lastPlayed > 0) append(" · 最后 ${formatTime(item.lastPlayed)}")
                        else append(" · 未开始")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                TextButton(onClick = { menuOpen = true }) {
                    Text("⋯", fontSize = 20.sp)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("导出 JSON") },
                        onClick = {
                            menuOpen = false
                            exportLauncher.launch(item.card.name.take(20) + ".json")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("复制角色卡") },
                        onClick = {
                            menuOpen = false
                            vm.duplicateCard(item.card.id)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除角色卡") },
                        onClick = {
                            menuOpen = false
                            confirmDelete = true
                        }
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除角色卡",
            text = "将同时删除该角色卡的全部对话记录，且不可恢复。",
            onConfirm = {
                confirmDelete = false
                vm.deleteCard(item.card.id)
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
