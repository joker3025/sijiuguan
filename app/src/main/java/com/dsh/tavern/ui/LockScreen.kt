package com.dsh.tavern.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** E4：应用锁解锁页。 */
@Composable
fun LockScreen(ui: UiState, vm: MainViewModel) {
    var pin by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("私酒馆已锁定", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "输入 PIN 解锁",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { input -> pin = input.filter { it.isDigit() }.take(8) },
            label = { Text("PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.widthIn(max = 280.dp)
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.unlock(pin) },
            enabled = pin.length >= 4,
            modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth()
        ) { Text("解锁") }
        Spacer(Modifier.height(8.dp))
        Text(
            "忘记 PIN 只能清除应用数据（对话会丢，建议平时导出备份）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
