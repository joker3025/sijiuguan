package com.dsh.tavern.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import kotlinx.coroutines.delay

@Composable
fun AppRoot(ui: UiState, vm: MainViewModel) {
    BackHandler(enabled = !ui.locked && ui.screen != Screen.Library) { vm.back() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (ui.locked) {
            LockScreen(ui, vm)
        } else {
            when (val screen = ui.screen) {
                Screen.Library -> LibraryScreen(ui, vm)
                is Screen.CardDetail -> CardDetailScreen(ui, vm, screen.cardId)
                is Screen.Chat -> ChatScreen(ui, vm, screen.chatId)
                Screen.Settings -> SettingsScreen(ui, vm)
                Screen.Presets -> PresetScreen(ui, vm)
                is Screen.CardEditor -> CardEditorScreen(ui, vm)
                Screen.ChatList -> ChatListScreen(ui, vm)
                Screen.GlobalWorldbook -> GlobalWorldbookScreen(ui, vm)
                Screen.Workshop -> CardWorkshopScreen(ui, vm)
                is Screen.WorldbookLibrary -> WorldbookLibraryScreen(ui, vm, screen.cardId)
            }
        }

        ui.toast?.let { message ->
            LaunchedEffect(message) {
                delay(2600)
                vm.clearToast()
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = 36.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 6.dp
            ) {
                Text(
                    message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        ui.error?.let { message ->
            val models = ui.modelFixModels
            AlertDialog(
                onDismissRequest = { vm.clearError() },
                title = { Text(if (models.isEmpty()) "出错了" else "模型名不被支持") },
                text = {
                    Column {
                        Text(message)
                        if (models.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "切换并重试：",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            models.take(4).forEach { model ->
                                OutlinedButton(
                                    onClick = { vm.applyModelFixAndRetry(model) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                ) { Text(model) }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { vm.clearError() }) { Text("关闭") }
                },
                dismissButton = if (models.isEmpty()) {
                    {
                        TextButton(onClick = {
                            vm.clearError()
                            vm.retryLast()
                        }) { Text("重试") }
                    }
                } else {
                    null
                }
            )
        }
    }
}
