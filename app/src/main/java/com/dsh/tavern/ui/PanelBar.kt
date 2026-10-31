package com.dsh.tavern.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dsh.tavern.data.PanelRuntime
import com.dsh.tavern.data.PanelTemplate

/**
 * P1-6：聊天页的互动面板。
 * 上方是状态/关系/背包等面板（模型每轮更新），下方是模型给出的剧情选项（点一下即发送）。
 */
@Composable
fun PanelBar(
    template: PanelTemplate,
    stateJson: String,
    onOption: (String) -> Unit,
    onReset: () -> Unit,
    onEdit: () -> Unit
) {
    val runtime = remember(stateJson) { PanelRuntime.parse(stateJson) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "互动面板",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onEdit) { Text("编辑") }
            TextButton(onClick = onReset) { Text("重置") }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            template.panels.forEach { panel ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.widthIn(min = 120.dp, max = 230.dp)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text(
                            panel.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        panel.fields.forEach { field ->
                            val value = runtime.values[panel.title]?.get(field.name)
                                ?.takeIf { it.isNotBlank() }
                                ?: field.initial.takeIf { it.isNotBlank() }
                                ?: "—"
                            Text(
                                "${field.name}: $value",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        if (runtime.options.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                runtime.options.forEach { option ->
                    AssistChip(
                        onClick = { onOption(option) },
                        label = { Text(option.take(24), maxLines = 1) }
                    )
                }
            }
        }
    }
}
