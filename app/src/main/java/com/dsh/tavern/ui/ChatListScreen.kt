package com.dsh.tavern.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 全局会话列表 + 消息搜索（跨角色卡）。
 * 输入关键词时同时搜会话标题/角色名/最后一条消息，以及所有历史消息内容。
 */
@Composable
fun ChatListScreen(ui: UiState, vm: MainViewModel) {
    var query by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        TavernBar(title = "对话", onBack = { vm.back() })
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                vm.loadChatList(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            placeholder = { Text("搜索会话标题 / 角色 / 消息内容") },
            singleLine = true
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (ui.chatList.isEmpty()) {
                item {
                    EmptyView(
                        "没有会话",
                        if (query.isBlank()) "去角色卡里点「新对话」开始吧" else "换个关键词试试"
                    )
                }
            }
            items(ui.chatList, key = { it.chat.id }) { item ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.openChat(item.chat.id) }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.cardName.ifBlank { "角色" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                formatTime(item.chat.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            item.chat.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            item.chat.lastMessage.ifBlank { "（空）" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (ui.messageHits.isNotEmpty()) {
                item {
                    SectionTitle("消息命中（${ui.messageHits.size}）")
                }
                items(
                    ui.messageHits,
                    key = { "${it.chatId}-${it.createdAt}-${it.content.take(8)}" }
                ) { hit ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.openChat(hit.chatId) }
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                "${hit.cardName.ifBlank { "角色" }} · ${hit.chatTitle} · " +
                                    if (hit.role == "user") "你" else "角色",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                hit.content.replace("\n", " ").take(140),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
