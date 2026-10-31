package com.dsh.tavern.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dsh.tavern.data.AppSettings
import com.dsh.tavern.data.CostCalculator
import com.dsh.tavern.data.ModelCatalog
import com.dsh.tavern.data.ProviderConfig

@Composable
fun SettingsScreen(ui: UiState, vm: MainViewModel) {
    var draft by remember { mutableStateOf(ui.settings) }
    var contextText by remember { mutableStateOf(ui.settings.contextLimit.toString()) }
    var maxTokensText by remember { mutableStateOf(ui.settings.maxTokens.toString()) }
    var temperatureText by remember { mutableStateOf(ui.settings.temperature.toString()) }
    var topPText by remember { mutableStateOf(ui.settings.topP.toString()) }
    var priceInText by remember { mutableStateOf(ui.settings.priceInputPerMillion.toString()) }
    var priceOutText by remember { mutableStateOf(ui.settings.priceOutputPerMillion.toString()) }
    var wbScanText by remember { mutableStateOf(ui.settings.worldbookScanDepth.toString()) }
    var wbBudgetText by remember { mutableStateOf(ui.settings.worldbookBudgetPercent.toString()) }
    var summaryText by remember { mutableStateOf(ui.settings.summaryThresholdMessages.toString()) }
    var budgetText by remember { mutableStateOf(ui.settings.dailyBudgetYuan.toString()) }
    var minReplyText by remember { mutableStateOf(ui.settings.minReplyLength.toString()) }
    var showKey by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.importBackup(uri)
    }

    fun updateProvider(block: (ProviderConfig) -> ProviderConfig) {
        val list = draft.providers.toMutableList()
        val index = draft.activeProvider.coerceIn(0, list.size - 1)
        list[index] = block(list[index])
        draft = draft.copy(providers = list)
    }

    fun save() {
        val newSettings = draft.copy(
            contextLimit = contextText.toIntOrNull()?.coerceIn(1024, 2_000_000) ?: draft.contextLimit,
            maxTokens = maxTokensText.toIntOrNull()?.coerceIn(64, 393_216) ?: draft.maxTokens,
            temperature = temperatureText.toFloatOrNull()?.coerceIn(0f, 2f) ?: draft.temperature,
            topP = topPText.toFloatOrNull()?.coerceIn(0.01f, 1f) ?: draft.topP,
            priceInputPerMillion = priceInText.toDoubleOrNull()?.coerceAtLeast(0.0)
                ?: draft.priceInputPerMillion,
            priceOutputPerMillion = priceOutText.toDoubleOrNull()?.coerceAtLeast(0.0)
                ?: draft.priceOutputPerMillion,
            worldbookScanDepth = wbScanText.toIntOrNull()?.coerceIn(1, 50)
                ?: draft.worldbookScanDepth,
            worldbookBudgetPercent = wbBudgetText.toIntOrNull()?.coerceIn(5, 60)
                ?: draft.worldbookBudgetPercent,
            summaryThresholdMessages = summaryText.toIntOrNull()?.coerceIn(4, 200)
                ?: draft.summaryThresholdMessages,
            dailyBudgetYuan = budgetText.toDoubleOrNull()?.coerceAtLeast(0.0)
                ?: draft.dailyBudgetYuan,
            minReplyLength = minReplyText.toIntOrNull()?.coerceIn(0, 5000)
                ?: draft.minReplyLength
        )
        vm.saveSettings(newSettings)
        draft = newSettings
        contextText = newSettings.contextLimit.toString()
        maxTokensText = newSettings.maxTokens.toString()
        temperatureText = newSettings.temperature.toString()
        topPText = newSettings.topP.toString()
        priceInText = newSettings.priceInputPerMillion.toString()
        priceOutText = newSettings.priceOutputPerMillion.toString()
        wbScanText = newSettings.worldbookScanDepth.toString()
        wbBudgetText = newSettings.worldbookBudgetPercent.toString()
        summaryText = newSettings.summaryThresholdMessages.toString()
        budgetText = newSettings.dailyBudgetYuan.toString()
        minReplyText = newSettings.minReplyLength.toString()
    }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        TavernBar(
            title = "设置",
            onBack = { vm.back() },
            actions = {
                TextButton(onClick = { save() }) { Text("保存") }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionTitle("外观")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "dark" to "深色",
                    "light" to "浅色",
                    "system" to "跟随系统"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = draft.themeMode == value,
                        onClick = { draft = draft.copy(themeMode = value) },
                        label = { Text(label) }
                    )
                }
            }
            Text(
                "保存后立即生效；跟随系统会随手机的深色模式切换。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionTitle("隐私与应用锁")
            if (ui.settings.appLockEnabled) {
                Text(
                    "应用锁已开启：每次启动需要输入 PIN。",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showPinDialog = true }) { Text("修改 PIN") }
                    OutlinedButton(onClick = { vm.disableAppLock() }) { Text("关闭应用锁") }
                }
            } else {
                OutlinedButton(
                    onClick = { showPinDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("开启应用锁（设置 PIN）") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("隐藏最近任务预览", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "开启后本应用无法截屏/录屏、最近任务里显示空白，下次启动生效。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = ui.settings.hideInRecents,
                    onCheckedChange = {
                        draft = draft.copy(hideInRecents = it)
                        vm.setHideInRecents(it)
                    }
                )
            }

            SectionTitle("API 供应商（可多套切换）")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                draft.providers.forEachIndexed { index, provider ->
                    FilterChip(
                        selected = index == draft.activeProvider,
                        onClick = { draft = draft.copy(activeProvider = index) },
                        label = { Text(provider.name.ifBlank { "供应商" }) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val list = draft.providers + ProviderConfig(name = "供应商 ${draft.providers.size + 1}")
                    draft = draft.copy(providers = list, activeProvider = list.size - 1)
                }) { Text("＋ 新增供应商") }
                TextButton(
                    onClick = {
                        if (draft.providers.size > 1) {
                            val list = draft.providers.toMutableList()
                            list.removeAt(draft.activeProvider.coerceIn(0, list.size - 1))
                            draft = draft.copy(providers = list, activeProvider = 0)
                        }
                    },
                    enabled = draft.providers.size > 1
                ) { Text("删除当前") }
            }

            OutlinedTextField(
                value = draft.provider.name,
                onValueChange = { value -> updateProvider { it.copy(name = value) } },
                label = { Text("供应商名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.provider.baseUrl,
                onValueChange = { value -> updateProvider { it.copy(baseUrl = value) } },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("DeepSeek: https://api.deepseek.com/v1（自动追加 /chat/completions；中转站可自定义）") }
            )
            OutlinedTextField(
                value = draft.provider.apiKey,
                onValueChange = { value -> updateProvider { it.copy(apiKey = value) } },
                label = { Text("API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showKey = !showKey }) {
                        Text(if (showKey) "隐藏" else "显示")
                    }
                }
            )
            OutlinedTextField(
                value = draft.provider.model,
                onValueChange = { value -> updateProvider { it.copy(model = value) } },
                label = { Text("模型 ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModelCatalog.knownIds.forEach { id ->
                    AssistChip(
                        onClick = { updateProvider { it.copy(model = id) } },
                        label = { Text(id) }
                    )
                }
            }
            Text(
                "上面只是快捷填写，不是限制；模型 ID 可以手动输入任意型号。实际可用型号以供应商的 /models 为准（可点下方「拉取模型列表」），报错时应用也会提示可用模型。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = draft.toolModel,
                onValueChange = { value -> draft = draft.copy(toolModel = value) },
                label = { Text("AI 工具模型（工坊随机 / 原文分析 / 世界书 / 卡片改写）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("留空 = 跟随上面的对话模型；填写后这些功能单独用该模型（Base URL / Key 仍用本供应商）。例如对话用 deepseek-flash，工具用 deepseek-v4.1-flash。")
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModelCatalog.knownIds.forEach { id ->
                    AssistChip(
                        onClick = { draft = draft.copy(toolModel = id) },
                        label = { Text(id) }
                    )
                }
            }
            OutlinedTextField(
                value = draft.provider.extraHeaders,
                onValueChange = { value -> updateProvider { it.copy(extraHeaders = value) } },
                label = { Text("自定义请求头（每行 Name: value，可空）") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("给 OpenRouter / 兼容网关等需要额外 Header 的供应商用") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        vm.fetchModels(draft.provider)
                        showModels = true
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("拉取模型列表") }
                if (ui.modelOptions.isNotEmpty()) {
                    OutlinedButton(onClick = { showModels = true }) { Text("选择模型") }
                }
            }
            Button(
                onClick = {
                    save()
                    vm.testConnection(draft)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存并测试连接") }

            SectionTitle("生成参数")
            OutlinedTextField(
                value = contextText,
                onValueChange = { contextText = it.filter { c -> c.isDigit() } },
                label = { Text("上下文上限（tokens）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text(
                        "只用于提示组装与预算显示，不改模型硬上限：DeepSeek 官方多为 64K，" +
                            "若你的中转/模型支持 128K 可改 128000。世界书预算自动取 25%。"
                    )
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(65_536, 131_072, 262_144, 524_288, 1_000_000).forEach { value ->
                    AssistChip(
                        onClick = {
                            contextText = value.toString()
                            draft = draft.copy(contextLimit = value)
                        },
                        label = {
                            Text(
                                when {
                                    value >= 1_000_000 -> "${value / 1_000_000}M"
                                    else -> "${value / 1000}K"
                                }
                            )
                        }
                    )
                }
            }
            OutlinedTextField(
                value = maxTokensText,
                onValueChange = { maxTokensText = it.filter { c -> c.isDigit() } },
                label = { Text("单次最大输出（tokens）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(4096, 8192, 16384, 65536, 393_216).forEach { value ->
                    AssistChip(
                        onClick = {
                            maxTokensText = value.toString()
                            draft = draft.copy(maxTokens = value)
                        },
                        label = {
                            Text(
                                when {
                                    value >= 1_000_000 -> "${value / 1_000_000}M"
                                    value >= 1000 -> "${value / 1000}K"
                                    else -> value.toString()
                                }
                            )
                        }
                    )
                }
            }
            Text(
                "DeepSeek-V4.1-Flash 规格：上下文 1,000,000 / 最大输出 393,216；" +
                    "切到该模型时应用会自动套用。输出越长费用越高，按需调低。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = minReplyText,
                onValueChange = { minReplyText = it.filter { c -> c.isDigit() } },
                label = { Text("最低回复字数（0 = 不限制）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("会写进系统提示要求模型展开；建议 200~600，同时把单次最大输出给到 1024+，否则会被截断")
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 200, 400, 600).forEach { value ->
                    AssistChip(
                        onClick = {
                            draft = draft.copy(minReplyLength = value)
                            minReplyText = value.toString()
                        },
                        label = { Text(if (value == 0) "不限制" else "$value 字") }
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
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("动态建议回复", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "每轮回复后额外调一次小请求，按当前剧情生成 3 条可选回复（会产生少量 token）；关闭则用角色卡的固定建议。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = draft.dynamicSuggestions,
                    onCheckedChange = { draft = draft.copy(dynamicSuggestions = it) }
                )
            }

            SectionTitle("世界书")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = wbScanText,
                    onValueChange = { wbScanText = it.filter { c -> c.isDigit() } },
                    label = { Text("触发扫描最近 N 条") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = wbBudgetText,
                    onValueChange = { wbBudgetText = it.filter { c -> c.isDigit() } },
                    label = { Text("预算占上下文 %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "世界书预算 = 上下文上限 × 百分比；超出时按插入顺序跳过靠后条目。条目的插入位置/概率/深度在卡片详情页逐条编辑。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { vm.openGlobalWorldbook() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("全局世界书（跨卡共享）") }

            SectionTitle("记忆与摘要")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自动生成前情提要")
                    Text(
                        "历史变长后自动调用一次摘要 API（消耗少量 token）；关闭后只能手动生成",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = draft.autoSummaryEnabled,
                    onCheckedChange = { draft = draft.copy(autoSummaryEnabled = it) }
                )
            }
            OutlinedTextField(
                value = summaryText,
                onValueChange = { summaryText = it.filter { c -> c.isDigit() } },
                label = { Text("触发阈值（未摘要消息条数）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("达到该条数或 token 超上下文 10% 时自动摘要") }
            )

            SectionTitle("用量与成本")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("请求真实 token 用量")
                    Text(
                        "流式请求会带 stream_options.include_usage；个别中转站不支持时可关闭",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = draft.includeUsage,
                    onCheckedChange = { draft = draft.copy(includeUsage = it) }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = priceInText,
                    onValueChange = { priceInText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("输入价 / 百万 token") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = priceOutText,
                    onValueChange = { priceOutText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("输出价 / 百万 token") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "填 0 不显示金额；价格只用于本地估算，不影响实际计费。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val todayCost = CostCalculator.cost(
                ui.settings, ui.usageStats.todayPrompt, ui.usageStats.todayCompletion
            )
            val monthCost = CostCalculator.cost(
                ui.settings, ui.usageStats.monthPrompt, ui.usageStats.monthCompletion
            )
            Text(
                "今日：${ui.usageStats.todayTokens} tokens · ≈ ¥%.4f\n本月：${ui.usageStats.monthTokens} tokens · ≈ ¥%.4f"
                    .format(todayCost, monthCost),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            OutlinedTextField(
                value = budgetText,
                onValueChange = { budgetText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("每日预算（元，0 = 不提醒）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("超过预算时每轮回复后会提示一次") }
            )

            SectionTitle("用户人设（{{user}}）")
            OutlinedTextField(
                value = draft.personaName,
                onValueChange = { draft = draft.copy(personaName = it) },
                label = { Text("你的名字") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = draft.personaDescription,
                onValueChange = { draft = draft.copy(personaDescription = it) },
                label = { Text("你的身份/设定（可空）") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("系统预设（可用 {{char}} / {{user}}）")
            OutlinedTextField(
                value = draft.systemPreset,
                onValueChange = { draft = draft.copy(systemPreset = it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
            Row {
                TextButton(onClick = {
                    draft = draft.copy(systemPreset = AppSettings.DEFAULT_PRESET)
                }) { Text("恢复默认") }
                Spacer(Modifier.width(8.dp))
                Text(
                    "成人向预设默认开启；如切换为全年龄可自行修改",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
            OutlinedButton(
                onClick = { vm.openPresets() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("管理预设（多套提示词 / 参数，聊天时可单独切换）") }

            SectionTitle("内容")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("允许成人内容")
                    Text(
                        "仅限 18 岁以上虚构创作；API 端是否放行取决于供应商",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = draft.nsfwEnabled,
                    onCheckedChange = { draft = draft.copy(nsfwEnabled = it) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("显示思考过程")
                    Text(
                        "deepseek-reasoner 的 reasoning_content 会折叠显示",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = draft.reasoningDisplay,
                    onCheckedChange = { draft = draft.copy(reasoningDisplay = it) }
                )
            }

            SectionTitle("数据")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { exportLauncher.launch("tavern-backup.json") },
                    modifier = Modifier.weight(1f)
                ) { Text("导出备份") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.weight(1f)
                ) { Text("导入备份") }
            }
            Text(
                "备份包含角色卡、世界书、全部对话记录；不包含 API Key。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("每日自动备份")
                    Text(
                        "每天启动时备份一次到应用目录，保留最近 7 份",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = draft.autoBackupEnabled,
                    onCheckedChange = { draft = draft.copy(autoBackupEnabled = it) }
                )
            }
            OutlinedButton(
                onClick = { vm.runAutoBackupNow() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("立即备份") }

            Spacer(Modifier.height(8.dp))
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) {
                Text("保存设置")
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "私酒馆 v1.0.0 · 本地优先 · 无广告、无云端账号",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showModels) {
        AlertDialog(
            onDismissRequest = { showModels = false },
            title = { Text("选择模型") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (ui.modelOptions.isEmpty()) {
                        Text(
                            "还没有模型列表：点「拉取模型列表」从供应商的 /models 接口获取。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ui.modelOptions.forEach { id ->
                        TextButton(onClick = {
                            updateProvider { it.copy(model = id) }
                            showModels = false
                        }) { Text(id) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModels = false }) { Text("关闭") }
            }
        )
    }

    if (showPinDialog) {
        InputDialog(
            title = "设置应用锁 PIN",
            initial = "",
            label = "4-8 位数字（忘记后只能清除应用数据）",
            onConfirm = { pin ->
                vm.enableAppLock(pin)
                showPinDialog = false
            },
            onDismiss = { showPinDialog = false }
        )
    }
}
