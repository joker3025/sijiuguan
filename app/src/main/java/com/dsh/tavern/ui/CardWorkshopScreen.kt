package com.dsh.tavern.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dsh.tavern.data.CardPromptTemplates
import com.dsh.tavern.data.ModelCatalog
import com.dsh.tavern.data.WorkshopFieldRule
import com.dsh.tavern.data.WorkshopInput
import com.dsh.tavern.data.WorkshopOptions

/**
 * 角色卡工坊：梗概/选项/自填 → 提示词模板 → 粘贴或 API 生成 → 预览质检 → 导入。
 * 所有输入都存在 ViewModel 的 workshopDraft 里：生成失败、切页、旋转都不会丢。
 */
@Composable
fun CardWorkshopScreen(ui: UiState, vm: MainViewModel) {
    val clipboard = LocalClipboardManager.current
    val draft = ui.workshopDraft
    var pasted by remember { mutableStateOf("") }
    var sourceTimeMode by remember { mutableStateOf("第一章开头") }
    var sourceChapter by remember { mutableStateOf("1") }
    var sourceChapterTo by remember { mutableStateOf("5") }
    var sourceEvent by remember { mutableStateOf("") }
    var sourceEventTo by remember { mutableStateOf("") }
    var sourceRequirement by remember { mutableStateOf(ui.workshopSourceRequirement) }
    var sourceUrl by remember { mutableStateOf("") }
    var sourceMode by remember { mutableStateOf("fast") }
    var sourceFocus by remember { mutableStateOf(ui.workshopSourceFocus) }
    val focusOptions = listOf(
        "人物关系", "外貌外形", "性格特点", "重要经历事件", "说话方式",
        "世界观规则", "地点势力", "称呼身份"
    )

    fun buildTimePoint(): String = when (sourceTimeMode) {
        "第一章开头" -> "第一章开头"
        "最后一章" -> "最后一章"
        "指定章节" -> "章节：${sourceChapter.ifBlank { "1" }}"
        "章节范围" ->
            "章节范围：${sourceChapter.ifBlank { "1" }} 到 ${sourceChapterTo.ifBlank { sourceChapter.ifBlank { "1" } }}"
        "事件范围" -> "事件范围：${sourceEvent.trim()} 到 ${sourceEventTo.trim()}"
        else -> "事件之后：${sourceEvent.trim()}"
    }
    fun setDraft(new: WorkshopInput) = vm.updateWorkshopDraft(new)

    val sourceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.importWorkshopSource(uri)
    }

    val visionImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) vm.readCardImageWithApi(uri)
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) vm.exportWorkshopJson(uri)
    }

    val anyBusy = ui.workshopBusy || ui.workshopSynopsisBusy ||
        ui.workshopFieldBusy || ui.workshopPolishBusy || ui.workshopSourceBusy ||
        ui.workshopImageBusy

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        TavernBar(title = "角色卡工坊", onBack = { vm.back() })
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "三步互不自动串联：①「🎲 随机选项」只改选项（API 随机，已确定项保留）→ " +
                    "②「API 随机梗概」只生成梗概 → ③「用当前 API 生成并预览」才出整张卡。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "所有「随机」都调用一次 API，并以你已确定的设定为约束；润色可多次，支持撤回与恢复。会消耗少量 token。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ToolModelBar(ui, vm)

            OutlinedTextField(
                value = draft.idea,
                onValueChange = { setDraft(draft.copy(idea = it)) },
                label = { Text("故事梗概（可只写一段）") },
                placeholder = { Text("例：林晚是临江大学图书馆管理员，白天整理旧书，晚上替地下钱庄做账……") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.randomWorkshopOptions() },
                    enabled = !anyBusy,
                    modifier = Modifier.weight(1f)
                ) { Text(if (ui.workshopFieldBusy) "随机中…" else "🎲 随机选项") }
                OutlinedButton(
                    onClick = { vm.generateWorkshopSynopsis(draft.synopsisLength) },
                    enabled = !anyBusy,
                    modifier = Modifier.weight(1f)
                ) { Text(if (ui.workshopSynopsisBusy) "生成梗概中…" else "API 随机梗概") }
            }
            OptionSelector(
                label = "梗概字数",
                value = draft.synopsisLength,
                options = WorkshopOptions.synopsisLengths,
                onSelect = { setDraft(draft.copy(synopsisLength = it)) },
                onRandom = {
                    setDraft(draft.copy(synopsisLength = WorkshopOptions.synopsisLengths.random()))
                },
                helperText = "「API 随机梗概」与「✨ 润色梗概」都按这个目标字数工作。",
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.polishWorkshopDraft(draft.synopsisLength) },
                    enabled = draft.idea.isNotBlank() && !anyBusy,
                    modifier = Modifier.weight(1f)
                ) { Text(if (ui.workshopPolishBusy) "润色中…" else "✨ 润色梗概") }
                OutlinedButton(
                    onClick = { vm.undoWorkshopPolish() },
                    enabled = ui.workshopUndo.isNotEmpty() && !anyBusy,
                    modifier = Modifier.weight(1f)
                ) { Text("↩ 撤回") }
                OutlinedButton(
                    onClick = { vm.redoWorkshopPolish() },
                    enabled = ui.workshopRedo.isNotEmpty() && !anyBusy,
                    modifier = Modifier.weight(1f)
                ) { Text("↪ 恢复") }
            }
            OutlinedButton(
                onClick = { vm.fillOptionsFromSynopsis() },
                enabled = draft.idea.isNotBlank() && !anyBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (ui.workshopFieldBusy) "按梗概填写中…" else "按当前梗概填写下面的选项")
            }
            Text(
                "润色会按已确定设定与「额外要求」改文笔/节奏/钩子，保留主线；可多次润色，撤回多了可以「↪ 恢复」。" +
                    "「按当前梗概填写选项」只改选项，不改梗概、不生成角色卡。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        setDraft(WorkshopInput(nsfw = ui.settings.nsfwEnabled))
                    },
                    enabled = !anyBusy,
                    modifier = Modifier.weight(1f)
                ) { Text("清空") }
            }

            Text("从图片读卡（角色卡介绍截图）", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { visionImageLauncher.launch("image/*") },
                    enabled = !ui.workshopImageBusy
                ) {
                    Text(if (ui.workshopImageBusy) "读图中…" else "选择截图（视觉 API 读图）")
                }
                if (ui.workshopImageText.isNotBlank()) {
                    TextButton(onClick = { vm.updateWorkshopImageText("") }) { Text("清空") }
                }
            }
            if (ui.workshopImageText.isNotBlank()) {
                OutlinedTextField(
                    value = ui.workshopImageText,
                    onValueChange = { vm.updateWorkshopImageText(it) },
                    label = { Text("识别出的介绍文字（可手改）") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.extractFromCardImageText(false) },
                        enabled = !anyBusy,
                        modifier = Modifier.weight(1f)
                    ) { Text("✨ 提取设定并润色") }
                    Button(
                        onClick = { vm.extractFromCardImageText(true) },
                        enabled = !anyBusy,
                        modifier = Modifier.weight(1f)
                    ) { Text("✨ 提取并生成完整卡") }
                }
                Text(
                    "读图方式：先让视觉模型把截图「转录成文字」，再用文本模型提取设定并润色梗概（两步走更稳）。" +
                        "需要「AI 工具模型」支持图片输入；转录结果会填在下方框里，可手改后再生成完整卡。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "把别人角色卡的介绍/对话截图导入：应用会压缩图片并发给当前供应商的视觉模型，" +
                        "由它转录文字、提取可用设定、润色梗概；介绍里缺的部分再由 API 补全成完整角色卡。\n" +
                        "需要「AI 工具模型」支持视觉输入。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("原作导入（同人 / 小说 TXT / EPUB）", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        sourceLauncher.launch(arrayOf("text/plain", "application/epub+zip", "*/*"))
                    },
                    enabled = !anyBusy
                ) { Text("选择 TXT / EPUB") }
                if (ui.workshopSourceTitle.isNotBlank()) {
                    TextButton(onClick = { vm.clearWorkshopSource() }) { Text("移除原文") }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = sourceUrl,
                    onValueChange = { sourceUrl = it },
                    label = { Text("TXT / EPUB 直链（http/https）") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { vm.importWorkshopSourceFromUrl(sourceUrl) },
                    enabled = sourceUrl.isNotBlank() && !anyBusy
                ) { Text("从链接导入") }
            }
            Text(
                "只支持直接指向文件的直链；番茄/起点等网页需要登录或反爬，应用内不抓取。可先用外部下载器（如 Fanqie-novel-Downloader）导出 TXT，再用「选择 TXT」或把文件分享到本应用。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = sourceRequirement,
                onValueChange = {
                    sourceRequirement = it
                    vm.updateWorkshopSourceRequirement(it)
                },
                label = { Text("原作要求：哪个角色 / 哪个时间 / 哪个地点 / 其他") },
                placeholder = {
                    Text("例：以沈砚为主角，从第三章雨夜冷宫开始，地点在冷宫；保留原文克制的语气，把其他配角（周砚、锦澜集团）的关系梳理进世界书")
                },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "这段要求会同时用于：① 原文分析填梗概/选项 ② 生成角色卡 ③ 卡片编辑器里的 AI 改写 ④ 世界书 AI 生成/修改。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    if (ui.workshopSourceTitle.isBlank()) {
                        vm.notify("先导入 TXT / EPUB 原文（上方「选择 TXT / EPUB」）")
                    } else {
                        vm.analyzeWorkshopSource(
                            buildTimePoint(),
                            sourceRequirement,
                            sourceMode,
                            autoGenerateCard = true
                        )
                    }
                },
                enabled = !anyBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (anyBusy) "处理中…" else "✨ 一键：按原文+要求生成梗概并做卡")
            }
            Text(
                "会先做全文分析、自动填好梗概与选项，再自动生成角色卡预览；预览里可以继续改，满意再导入。消耗 token 较多。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (ui.workshopSourceTitle.isNotBlank()) {
                Text(
                    "《${ui.workshopSourceTitle}》· ${ui.workshopSourceFormat.uppercase()} · " +
                        "${ui.workshopSourceChapterCount} 章 · ${ui.workshopSourceCharCount} 字",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text("重点保留（可多选，会写进分析/生成卡/世界书/改写的提示词）", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    focusOptions.forEach { option ->
                        FilterChip(
                            selected = sourceFocus.contains(option),
                            onClick = {
                                sourceFocus = if (sourceFocus.contains(option)) {
                                    sourceFocus - option
                                } else {
                                    sourceFocus + option
                                }
                                vm.updateWorkshopSourceFocus(sourceFocus)
                            },
                            label = { Text(option) }
                        )
                    }
                }
                OptionSelector(
                    label = "角色卡从原著的哪个进度开始（时间点 / 范围）",
                    value = sourceTimeMode,
                    options = listOf(
                        "第一章开头", "最后一章", "指定章节", "章节范围",
                        "某个事件之后", "事件范围"
                    ),
                    onSelect = { sourceTimeMode = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "这是「原著时间线的起点」：选「第一章开头」= 从原著最开头做卡（之前无前情）；" +
                        "选后面的章节/事件 = 更早剧情变成角色卡的背景，开场白和场景只写这个时间点，" +
                        "之后的剧情不会提前写进去；选「范围」则只按这个区间的原文取材。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (sourceTimeMode == "指定章节") {
                    OutlinedTextField(
                        value = sourceChapter,
                        onValueChange = { sourceChapter = it.filter { c -> c.isDigit() } },
                        label = { Text("第几章（数字）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (sourceTimeMode == "章节范围") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = sourceChapter,
                            onValueChange = { sourceChapter = it.filter { c -> c.isDigit() } },
                            label = { Text("从第几章") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = sourceChapterTo,
                            onValueChange = { sourceChapterTo = it.filter { c -> c.isDigit() } },
                            label = { Text("到第几章") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (sourceTimeMode == "某个事件之后") {
                    OutlinedTextField(
                        value = sourceEvent,
                        onValueChange = { sourceEvent = it },
                        label = { Text("事件关键词（如：婚礼 / 决裂 / 葬礼）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (sourceTimeMode == "事件范围") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = sourceEvent,
                            onValueChange = { sourceEvent = it },
                            label = { Text("起始事件关键词") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = sourceEventTo,
                            onValueChange = { sourceEventTo = it },
                            label = { Text("结束事件关键词") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "fast" to "快速压缩（推荐）",
                        "full" to "逐段精读（慢/贵）",
                        "excerpt" to "仅所选范围片段"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = sourceMode == value,
                            onClick = { sourceMode = value },
                            label = { Text(label) }
                        )
                    }
                }
                Text(
                    when (sourceMode) {
                        "fast" ->
                            "快速压缩：在全书里按顺序均匀采样约 2.4 万字（覆盖开头到结尾），只调一次 API 就压缩出重点资料（人物关系/外貌/性格/事件/说话方式）。不逐字读全文，快且省 token。"
                        "full" ->
                            "逐段精读：把整本书按段依次发给 API（每段一次调用），最全面，但长篇可能二十多段、很慢很贵，也更容易中途失败。"
                        else ->
                            "只发送你选中的范围：可指定「第 X 章到第 Y 章」或「从事件 A 到事件 B」的原文；选「第一章开头/最后一章/指定章节/某个事件之后」时按该时间点附近片段。最省 token。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (sourceTimeMode == "章节范围" || sourceTimeMode == "事件范围") {
                    Text(
                        "范围很大（比如 1 到 100 / 150 章）也没问题：快速压缩只在这个范围内均匀采样约 2.4 万字；" +
                            "逐段精读只切这个范围；「仅所选范围片段」也会在区间内采样，不会把整段全塞给模型。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                OutlinedButton(
                    onClick = {
                        vm.analyzeWorkshopSource(buildTimePoint(), sourceRequirement, sourceMode)
                    },
                    enabled = !anyBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            ui.workshopSourceBusy && ui.workshopSourceProgress.isNotBlank() ->
                                ui.workshopSourceProgress
                            ui.workshopSourceBusy -> "分析中…"
                            sourceMode == "fast" -> "✨ 快速压缩全文并提取梗概 / 人设"
                            sourceMode == "full" -> "✨ 逐段精读全文并提取梗概 / 人设"
                            else -> "✨ 从原文提取梗概 / 人设 / 外貌"
                        }
                    )
                }
                if (ui.workshopSourceDigest.isNotBlank()) {
                    Text(
                        "已建立重点资料（${ui.workshopSourceDigest.length} 字）：生成角色卡、世界书 AI、卡片改写都会参考它。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (ui.workshopSourceSummary.isNotBlank()) {
                    Text(
                        "已提取原作参考：${ui.workshopSourceSummary.take(160)}…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "只用于本次工坊分析的上传；生成角色卡时会参考全文参考 + 时间节点附近原文 + 梗概 + 你的要求。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { setDraft(draft.copy(name = it)) },
                    label = { Text("角色名（可选）") },
                    placeholder = { Text("留空自动取名") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { vm.randomWorkshopField(WorkshopFieldRule.NAME) },
                    enabled = !anyBusy,
                    modifier = Modifier.padding(top = 24.dp)
                ) { Text("随机") }
            }
            OptionSelector(
                label = "性别",
                value = draft.gender,
                options = WorkshopOptions.genders,
                onSelect = { setDraft(draft.copy(gender = it)) },
                onRandom = { vm.randomWorkshopField(WorkshopFieldRule.GENDER) },
                modifier = Modifier.fillMaxWidth()
            )
            OptionSelector(
                label = "尺度",
                value = draft.scale,
                options = WorkshopOptions.scales,
                onSelect = { setDraft(draft.copy(scale = it)) },
                onRandom = { vm.randomWorkshopField(WorkshopFieldRule.SCALE) },
                modifier = Modifier.fillMaxWidth()
            )
            OptionSelector(
                label = "世界观",
                value = draft.world,
                options = WorkshopOptions.worlds,
                onSelect = { setDraft(draft.copy(world = it)) },
                onRandom = { vm.randomWorkshopField(WorkshopFieldRule.WORLD) },
                modifier = Modifier.fillMaxWidth()
            )
            OptionSelector(
                label = "人设",
                value = draft.archetype,
                options = WorkshopOptions.archetypes,
                onSelect = { setDraft(draft.copy(archetype = it)) },
                onRandom = { vm.randomWorkshopField(WorkshopFieldRule.ARCHETYPE) },
                modifier = Modifier.fillMaxWidth()
            )
            OptionSelector(
                label = "主线",
                value = draft.plot,
                options = WorkshopOptions.plots,
                onSelect = { setDraft(draft.copy(plot = it)) },
                onRandom = { vm.randomWorkshopField(WorkshopFieldRule.PLOT) },
                modifier = Modifier.fillMaxWidth()
            )
            OptionSelector(
                label = "玩家 ID 占位符",
                value = draft.placeholder,
                options = WorkshopOptions.placeholders,
                onSelect = { setDraft(draft.copy(placeholder = it)) },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.genre,
                    onValueChange = { setDraft(draft.copy(genre = it)) },
                    label = { Text("题材（可空）") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { vm.randomWorkshopField(WorkshopFieldRule.GENRE) },
                    enabled = !anyBusy,
                    modifier = Modifier.padding(top = 24.dp)
                ) { Text("随机") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.tone,
                    onValueChange = { setDraft(draft.copy(tone = it)) },
                    label = { Text("基调（可空）") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { vm.randomWorkshopField(WorkshopFieldRule.TONE) },
                    enabled = !anyBusy,
                    modifier = Modifier.padding(top = 24.dp)
                ) { Text("随机") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.appearance,
                    onValueChange = { setDraft(draft.copy(appearance = it)) },
                    label = { Text("外貌要点（可空）") },
                    minLines = 2,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { vm.randomWorkshopField(WorkshopFieldRule.APPEARANCE) },
                    enabled = !anyBusy,
                    modifier = Modifier.padding(top = 24.dp)
                ) { Text("随机") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.priorityBase.toString(),
                    onValueChange = { text ->
                        setDraft(draft.copy(priorityBase = text.filter { it.isDigit() }.take(3).toIntOrNull() ?: 0))
                    },
                    label = { Text("触发词优先级起始") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { vm.randomWorkshopField(WorkshopFieldRule.PRIORITY) },
                    enabled = !anyBusy,
                    modifier = Modifier.padding(top = 24.dp)
                ) { Text("随机") }
            }
            OutlinedTextField(
                value = draft.extra,
                onValueChange = { setDraft(draft.copy(extra = it)) },
                label = { Text("额外要求 / 自填设定（可空）") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("例：病娇、双人格、民国背景、必须出现雨夜、禁写某些内容") }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("18+ 成人向虚构", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "打开后提示词允许成人向描写（角色均为成年人）；关闭则全年龄。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = draft.nsfw,
                    onCheckedChange = { setDraft(draft.copy(nsfw = it)) }
                )
            }

            OptionSelector(
                label = "世界书规模",
                value = draft.worldbookLevel,
                options = WorkshopOptions.worldbookLevels,
                onSelect = { setDraft(draft.copy(worldbookLevel = it)) },
                onRandom = { vm.randomWorkshopField(WorkshopFieldRule.WORLDBOOK) },
                helperText = "小 = 让 AI 写 6~10 条世界书条目；中 = 12~20 条；大 = 20~30 条。也可以自定义条数。",
                modifier = Modifier.fillMaxWidth()
            )
            OptionSelector(
                label = "候选标题数量",
                value = "${draft.titleCount} 个",
                options = WorkshopOptions.titleCounts,
                onSelect = { option ->
                    val count = option.filter { it.isDigit() }.toIntOrNull()
                    if (count != null) setDraft(draft.copy(titleCount = count.coerceIn(1, 10)))
                },
                onRandom = { vm.randomWorkshopField(WorkshopFieldRule.TITLE_COUNT) },
                helperText = "让 AI 在卡片介绍里给你几个备选标题（1/3/5 个，也可自定义 1~10 个）。",
                modifier = Modifier.fillMaxWidth()
            )

            Text("提示词模板（点复制，按顺序用）", style = MaterialTheme.typography.titleSmall)
            PromptTemplateCard(
                title = "① 构思 / 润色（发给任意 AI 聊设定）",
                preview = "让 AI 追问并输出：卖点、三层人设、关系张力、剧情钩子、开场钩子、标题、世界书大纲",
                onCopy = { copyText(clipboard, vm, CardPromptTemplates.ideationPrompt(draft), "构思提示词") }
            )
            PromptTemplateCard(
                title = "② 生成角色卡（输出 V3 JSON）",
                preview = "完整字段规范 + 分级世界书 + 标题套路 + 写作要求；让 AI 只输出 JSON",
                onCopy = { copyText(clipboard, vm, CardPromptTemplates.cardJsonPrompt(draft), "生成角色卡提示词") }
            )
            PromptTemplateCard(
                title = "③ 立绘 / 头像提示词",
                preview = "让 AI 根据卡面写中英文生图提示词；生成后保存图片，在卡编辑器里选作头像，导出 PNG 会嵌入",
                onCopy = { copyText(clipboard, vm, CardPromptTemplates.illustrationPrompt(draft), "立绘提示词") }
            )

            Text("直接生成（消耗当前 API token）", style = MaterialTheme.typography.titleSmall)
            Button(
                onClick = { vm.generateCardFromWorkshop() },
                enabled = !anyBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (ui.workshopBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(16.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                    Text("生成中…（约 10~30 秒）")
                } else {
                    Text("用当前 API 生成并预览")
                }
            }
            Text(
                "生成失败只会提示，梗概和所有选项都会保留（存在草稿里）。费用按实际 token 计。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ui.workshopCard?.let { card ->
                val warnings = buildList {
                    if (card.name.isBlank()) add("缺少角色名")
                    if (card.description.length < 200) add("描述偏短（建议 300~800 字）")
                    if (card.firstMes.length < 80) add("开场白偏短（建议 200 字以上）")
                    if (card.tags.isEmpty()) add("没有标签")
                    if (ui.workshopEntries.isEmpty()) add("没有世界书条目")
                    if (ui.workshopEntries.isNotEmpty() && ui.workshopEntries.none { it.constant }) {
                        add("没有常驻（T0）条目，核心规则可能不稳")
                    }
                    if (ui.workshopEntries.any { !it.constant && it.keys.isEmpty() }) {
                        add("有非触发条目没写 keys，将永远不会命中")
                    }
                }
                val t0 = ui.workshopEntries.count { it.constant }
                val t1 = ui.workshopEntries.count { it.position == "after_char" && !it.constant }
                val t2 = ui.workshopEntries.count { it.position == "in_history" }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("预览与质检", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "名称：${card.name.ifBlank { "（空）" }} · 标签：${card.tags.joinToString("、").ifBlank { "无" }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "描述 ${card.description.length} 字 · 性格 ${card.personality.length} 字 · " +
                                "开场白 ${card.firstMes.length} 字 · 备选开场 ${card.alternateGreetings.size} 个",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "世界书 ${ui.workshopEntries.size} 条：常驻 $t0 / 人物地点 $t1 / 剧情 $t2 / 其他 ${ui.workshopEntries.size - t0 - t1 - t2}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (warnings.isEmpty()) {
                            Text(
                                "质检通过，可以直接导入。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        } else {
                            warnings.forEach { warning ->
                                Text(
                                    "· $warning",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { vm.confirmWorkshopImport() }) { Text("导入卡片") }
                            TextButton(onClick = { vm.clearWorkshopPreview() }) { Text("放弃") }
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(ui.workshopJson.orEmpty()))
                                vm.notify("已复制 JSON")
                            }) { Text("复制 JSON") }
                            TextButton(onClick = {
                                exportJsonLauncher.launch("card-${card.name.ifBlank { "tavern" }.take(12)}.json")
                            }) { Text("导出 JSON") }
                        }
                    }
                }
            }

            Text("粘贴 AI 生成的 JSON", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it },
                label = { Text("粘贴 V3 / V2 / 裸卡 JSON（不要 PNG）") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.importCardFromJson(pasted) },
                    enabled = pasted.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("解析预览") }
                TextButton(onClick = { pasted = "" }) { Text("清空") }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    CardPromptTemplates.importGuide(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun copyText(
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    vm: MainViewModel,
    text: String,
    label: String
) {
    clipboard.setText(AnnotatedString(text))
    vm.notify("已复制：$label")
}

/**
 * 通用选择器：下拉选项 + 随机（API）+ 自定义。
 */
@Composable
private fun OptionSelector(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRandom: (() -> Unit)? = null,
    helperText: String? = null
) {
    var open by remember { mutableStateOf(false) }
    var customOpen by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (onRandom != null) {
                TextButton(onClick = { onRandom.invoke() }) { Text("随机") }
            }
        }
        Box {
            OutlinedButton(
                onClick = { open = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(option)
                            open = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("自定义…") },
                    onClick = {
                        customText = value
                        open = false
                        customOpen = true
                    }
                )
            }
        }
        if (helperText != null) {
            Text(
                helperText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (customOpen) {
        AlertDialog(
            onDismissRequest = { customOpen = false },
            title = { Text("自定义$label") },
            text = {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (customText.isNotBlank()) onSelect(customText.trim())
                    customOpen = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { customOpen = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PromptTemplateCard(
    title: String,
    preview: String,
    onCopy: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                preview,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onCopy) { Text("复制提示词") }
        }
    }
}
