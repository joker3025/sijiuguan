# 私酒馆 Android 开发进度（v1.1 → v1.3）

> 目标：把 DZMM 的「可观测提示词管线 / 消息时间旅行 / 结构化互动」搬进本地单机版，不做社区、不做付费、不做语音生图。
> 基准：v1.0.0 源码（原 zip 保留未动），本目录是工作副本。
> 注：本机没有 Android SDK/JDK/Gradle，改完需在 Android Studio 或构建机 `gradle assembleDebug` 验证；每项改动尽量保持自洽。

## v1.9.5 长截图切片（避免缩小后模糊）

- `SourceImporter.tileRanges(totalHeight, tileHeight, overlap, maxTiles)`：纵向切段（含重叠，避免切断文字行）。
- `ImageTextExtractor.loadTilesAsJpeg`：长截图保持原宽度、按 1600px 高切片压 JPEG（重叠 10%，最多 8 片）。
- `ApiClient.completeWithImages`：一条 user 消息里携带多张 image_url。
- `cardImageTranscribePrompt(tileCount)`：多片时明确「同一长截图的切片、按顺序、重叠不重复」。
- VM 视觉读图改用切片发送；普通截图仍是 1 张。
- 单测新增切片范围与多片提示词检查，共 **34/34 通过**。
- 版本：versionCode 43 / versionName 1.9.5。

## v1.9.4 视觉读图改两步（转录 → 文本提取）

- `CardPromptTemplates.cardImageTranscribePrompt`：只做图片文字转录，要求纯文本输出。
- VM `readCardImageWithApi`：视觉模型只转录 → 写入 `workshopImageText` → 自动调用 `extractFromCardImageText(false)` 用文本模型提取 JSON；失败时错误信息带模型实际返回片段。
- 工坊图片说明更新为两步流程。
- 单测新增转录提示词检查，共 **33/33 通过**。
- 版本：versionCode 42 / versionName 1.9.4。

## v1.9.3 精简版：移除本地 OCR（仅视觉 API）

- 移除依赖 `com.google.mlkit:text-recognition-chinese`；APK 从 ~51.7MB 回到 ~10MB。
- `ImageTextExtractor` 只保留图片解码/缩放/JPEG 压缩；删除 ML Kit 识别代码。
- 删除 VM `importCardImage`（本地 OCR）；分享图片不是卡文件时改为调用 `readCardImageWithApi(uri, openWorkshop=true)`。
- 工坊图片区只保留「选择截图（视觉 API 读图）」；说明文案更新为需要支持视觉的模型。
- 版本：versionCode 41 / versionName 1.9.3。

## v1.9.2 视觉 API 读图

- `ApiClient.completeWithImage`：OpenAI 兼容多模态请求（最后一条 user 消息为 text + image_url base64 data URL，detail=high）。
- `ImageTextExtractor.loadJpegBytes`：图片缩放后压 JPEG（默认 1600px / q82），控制 base64 体积。
- `CardPromptTemplates.cardImageVisionPrompt`：转录 transcript + 提取设定 + 润色 synopsis，复用重点保留 focus。
- VM `readCardImageWithApi(uri)`：读图 → API → 填草稿 + 设置 OCR 文字；失败弹窗提示换视觉模型或用本地 OCR。
- 工坊图片区新增「视觉 API 读图并提取」按钮，说明两种读图方式差异。
- 单测新增视觉提示词契约，共 **32/32 通过**。
- 版本：versionCode 40 / versionName 1.9.2。

## v1.9.1 bug 修复 / 体验完善

- 分享/打开图片到应用：先尝试角色卡导入，失败且是图片时自动 OCR → 写入 `workshopImageText` 并跳工坊（以前直接报“导入失败”）。
- Manifest SEND/VIEW 增加 `image/jpeg`、`image/webp`。
- `SourceImporter.isImageBytes`（PNG/JPEG/WebP 文件头）；`ImageTextExtractor` 缩放逻辑修正 + `Tasks.await(90s)` 超时。
- `excerptChapters`：范围写反（5→3）自动交换。
- `MainActivity.isSourceText`：.json/.png/.jpg/.jpeg/.webp 优先走角色卡导入，避免被 text/plain 误判成原文。
- 单测新增反向章节范围 + 图片文件头判断，共 **31/31 通过**。
- 版本：versionCode 39 / versionName 1.9.1。

## v1.9.0 从图片读卡（本地 OCR + API 提取/补全）

- 新增依赖 `com.google.mlkit:text-recognition-chinese:16.0.0`（本地中文 OCR，离线）。
- `data/ImageTextExtractor.kt`：按最长边缩放解码图片 → ML Kit 识别 → 文本；不上传图片。
- `CardPromptTemplates.cardIntroExtractPrompt`：修正 OCR 错字、提取设定（name/gender/world/archetype/plot/genre/tone/appearance/extra）+润色 synopsis/summary，沿用重点保留 focus。
- `UiState.workshopImageText/workshopImageBusy`；VM `importCardImage`（OCR）、`updateWorkshopImageText`、`extractFromCardImageText(autoGenerate)`（API 提取，auto 时串完整卡生成）。
- 工坊新增「从图片读卡」区：选择截图、识别文字可编辑、「提取设定并润色」、「提取并生成完整卡」。
- 单测新增 `cardIntroExtractPromptHasFields`，共 **30/30 通过**。
- 版本：versionCode 38 / versionName 1.9.0。

## v1.8.8 时间点说明更清楚

- 工坊时间点选择 label 改为「角色卡从原著的哪个进度开始（时间点 / 范围）」。
- 下方新增说明：这是原著时间线起点（之前为背景、之后不提前写进开场白；范围模式只按区间取材）。
- 版本：versionCode 37 / versionName 1.8.8。

## v1.8.7 原文分析失败可诊断 + 重试

- 事件类时间点（某个事件之后 / 事件范围）关键词为空时直接提示，不再发无意义请求。
- 快速压缩输入预算按 `contextLimit` 自适应：`min(20_000, max(6_000, contextLimit/6))`；片段部分 fast 模式只取 6k 字符。
- 首次 JSON 解析失败：缩短输入到 10k + 降温 0.2 + 明确「只输出 JSON」重试一次。
- 分析失败改用全局错误对话框，显示具体原因 + 排查步骤（换工具模型/拉取模型列表/换模式/降上下文）。
- 版本：versionCode 36 / versionName 1.8.7。

## v1.8.6 范围感知（大范围 1→100/150 章）

- 新增 VM `sourceRangeText(doc, timePoint)`：选「章节范围 / 事件范围」时返回区间原文。
- 快速压缩 / 逐段精读改为：有范围时只在范围内采样/切段（不再分析全书）；无范围仍分析全书。
- 工坊 UI：范围模式下显示提示「范围很大也没问题：区间内均匀采样/切段」。
- 版本：versionCode 35 / versionName 1.8.6。

## v1.8.5 章节范围 / 事件范围

- 时间点选项新增「章节范围」「事件范围」；UI 提供「从第几章 → 到第几章」「起始事件 → 结束事件」输入。
- `SourceImporter.excerptChapters/chapters` 与 `excerptBetween`：按范围截取，超长自动采样截断。
- `buildSourceExcerpt` 解析「章节范围：X 到 Y」「事件范围：A 到 B」，供「仅所选范围片段」模式使用；分析提示词会带上范围文字作为时间节点。
- 工坊 UI：「仅时间点片段」改名「仅所选范围片段」，说明随范围模式变化。
- 单测新增章节/事件范围截取，共 **29/29 通过**。
- 版本：versionCode 34 / versionName 1.8.5。

## v1.8.4 快速压缩模式 + 章节切分修复

- 排查用户 TXT：1.83MB / 655,324 字 / 301 章，标题为「第1 章 xxx」（数字与“章”之间有空格），旧正则只切出 5 章；逐段精读要 20+ 次调用，容易中途失败。
- `SourceImporter.splitChapters`：支持 `第1 章`/`第 1 章` 等间距写法，并在标题与正文同行时按行首切分。
- 新增 `SourceImporter.sampleForCompression(text, maxChars)`：全文顺序均匀采样（含开头/结尾），用于「快速压缩」。
- `analyzeWorkshopSource(timePoint, requirement, mode: "fast"|"full"|"excerpt", autoGenerateCard)`：
  - fast（默认）：采样约 2.4 万字 → 一次 `sourceAnalysisPrompt(includeDigest=true)` 输出 digest+梗概/选项；digest 存入 `workshopSourceDigest`。
  - full：保留原逐段精读 + 自适应分段/重试/回退。
  - excerpt：仅时间节点片段。
- 工坊 UI：三模式 chips（快速压缩（推荐）/逐段精读（慢/贵）/仅时间点片段），按钮文案随模式变化。
- 单测新增「第1 章」切章与采样覆盖测试，共 **28/28 通过**。
- 版本：versionCode 33 / versionName 1.8.4。

## v1.8.3 「重点保留」多选

- `UiState.workshopSourceFocus`（默认：人物关系/外貌外形/性格特点/重要经历事件/说话方式）；`updateWorkshopSourceFocus`。
- `CardPromptTemplates.focusBlock(focus)`：统一生成「重点保留」提示段；`fullTextExtractPrompt`、`sourceAnalysisPrompt`、`cardJsonPrompt`、`rewriteCardFieldPrompt`、`generateWorldbookEntryPrompt` 均新增 `focus` 参数并写入提示词。
- 工坊原文区新增 8 个 FilterChip（多选，含世界观规则/地点势力/称呼身份），切换即保存到 VM。
- 单测扩展重点保留断言，共 **27/27 通过**。
- 版本：versionCode 32 / versionName 1.8.3。

## v1.8.2 全文分析稳定性（分段自适应 / 重试 / 自动降上下文 / 回退片段）

- `ApiErrorParser.parseContextLimit`：从「maximum context length is X」解析真实上限。
- 全文分段大小按 `contextLimit` 自适应：≥500K→30K 字符、≥128K→12K、否则 6K（不再固定 60K）。
- 每段 `maxTokens` 900→2000；空返回时缩短本段到 2/3 再重试一次（2800 tokens）；段间 delay 降低限流风险。
- 全部段失败：若错误含上下文上限则自动 `saveSettings(contextLimit=X)`；并自动改用时间点片段完成分析，toast 显示真实原因。
- 单测新增 `apiErrorParserReadsContextLimit`，共 **27/27 通过**。
- 版本：versionCode 31 / versionName 1.8.2。

## v1.8.1 一键「原文+要求」直接出卡

- `analyzeWorkshopSource(timePoint, requirement, fullText, autoGenerateCard)`：分析成功后可自动调用 `generateCardFromWorkshop()`。
- 工坊「原作要求」下方新增「✨ 一键：按原文+要求生成梗概并做卡」：未导入原文时提示；导入后全文分析 → 填梗概/选项 → 自动生成卡预览。
- 时间节点拼接抽成 `buildTimePoint()`，一键与分开流程共用。
- 版本：versionCode 30 / versionName 1.8.1。

## v1.8.0 全文分析（不再只截取片段）

- `SourceImporter.splitForAnalysis(text, maxChars)`：按段落切分全文（超长段落硬切），供全文分段分析。
- `CardPromptTemplates.fullTextExtractPrompt`：从第 i/N 段提取人物/关系/世界观/事件要点；`cardJsonPrompt`、`rewriteCardFieldPrompt`、`generateWorldbookEntryPrompt` 新增 `sourceDigest`（全书参考）并写入提示词。
- VM `analyzeWorkshopSource(timePoint, requirement, fullText = true)`：全文切段（≤80 段，每段约 contextLimit/3 字符）→ 逐段调 API 提取 → 合并（过长再压缩一次）→ 结合时间节点附近原文生成梗概/选项；`UiState.workshopSourceDigest/Progress` 保存全书参考与「第 i/N 段」进度。
- 工坊 UI：新增「全文分析（推荐）/ 仅时间点片段」切换、进度显示、全书参考字数提示。
- 单测新增全文切分/提示词检查，共 **26/26 通过**。
- 版本：versionCode 29 / versionName 1.8.0。

## v1.7.8 模型切换覆盖所有 AI 编辑界面

- 新增通用 `ui/ToolModelSwitcher.kt`（`ToolModelBar` + `ToolModelDialog`），工坊改用该组件。
- 接入：角色卡编辑器（TavernBar 下）、世界书条目编辑弹窗（compact）、世界书收藏库、全局世界书页。
- 所有入口共享同一「工具模型」设置（`toolProviderIndex` / `toolModel`），不影响聊天模型。
- 版本：versionCode 28 / versionName 1.7.8。

## v1.7.7 工坊页直接切换 API 模型

- `AppSettings.toolProviderIndex`（-1 跟随当前供应商）+ Prefs 读写；`toolSettings()` 支持工具独立供应商 + 模型。
- VM 新增 `saveToolModel(providerIndex, model, followChat)`：保存工具供应商/模型（可恢复「跟随聊天」），不影响聊天。
- 工坊顶部新增「工坊 AI：供应商 / 模型」栏 +「切换 API 模型」弹窗（供应商 FilterChip、模型输入、常用模型/拉取到的模型点选、「跟随聊天」/保存）。
- 版本：versionCode 27 / versionName 1.7.7。

## v1.7.6 AI 工具模型独立设置

- `AppSettings.toolModel`（空=跟随对话模型）；`Prefs` 读写 `toolModel`。
- VM 新增 `toolSettings()`：把当前供应商的 model 替换为 toolModel，Base URL/Key 不变；工坊随机/原文分析/卡片生成/世界书 AI/字段改写共 11 处非流式调用改用 `toolSettings()`。
- 设置 → 供应商新增「AI 工具模型」输入框 + `ModelCatalog.knownIds` 快捷按钮；聊天仍用原对话模型。
- 版本：versionCode 26 / versionName 1.7.6。

## v1.7.5 设置页模型快捷建议更新

- 设置 → 供应商的模型 `AssistChip` 由硬编码 2 个（deepseek-chat/reasoner）改为 `ModelCatalog.knownIds` 全列表（横向滚动），并加说明「只是快捷填写，不是限制，可手填任意型号，以 /models 为准」。
- `ProviderConfig.model` 新安装默认值改为 `deepseek-flash`（V4 新命名，旧安装不受影响）。
- 版本：versionCode 25 / versionName 1.7.5。

## v1.7.4 模型名报错自动修复

- 新增 `data/ApiErrorParser.kt`：解析 OpenAI 兼容错误体中的 `error.message`，提取「supported API model names are X, Y」里的可用模型名（`ApiErrorInfo`）。
- 聊天流式请求失败时：UiState 增加 `modelFixFailed/modelFixModels`，弹窗列出可用模型，点一下 `applyModelFixAndRetry()` 会切换模型并自动重试。
- 非流式请求：`ApiClient.lastError` 记录友好错误，工坊/AI 改写/世界书等失败提示会优先显示（含可用模型），不再只报「没有返回内容」。
- `ModelCatalog`：常用模型加入 `deepseek-flash`、`deepseek-v4-pro`（1M 上下文 / 384K 输出）。
- 单测新增 `apiErrorParserExtractsSupportedModels`，共 **25/25 通过**。
- 版本：versionCode 24 / versionName 1.7.4。

## v1.7.3 文本直链导入 + TXT/EPUB 系统分享接入

- 工坊新增「TXT/EPUB 直链」输入框 +「从链接导入」：http(s) GET（UA、80MB 上限、拒绝 HTML 页面），解析后直接进入原文分析。
- `MainActivity.handleIntent` 按 MIME/文件名分流：TXT/EPUB 走 `importSourceFromUri`（解析原文并跳工坊），PNG/JSON 仍走角色卡导入。
- Manifest 增加 SEND/VIEW 的 `application/epub+zip` 与 VIEW `text/plain`。
- 明确不内置番茄/起点抓取（平台条款 + 版权 + 反爬）；外部下载器导出 TXT 后可直接分享/打开到本应用。
- 版本：versionCode 23 / versionName 1.7.3。

## 已完成

- [x] **P0-1 消息多版本 + 继续生成 + 编辑后重生成**
  - 数据层：`messages` 新增 `variants`（全部版本 JSON）、`active_variant`、`debug`；DB_VERSION 1 → 2 带迁移；旧数据自动按 `content` 合成 1 个版本；备份/恢复同步兼容。
  - 逻辑层：`regenerateMessage(id)`（任意 AI 回复保留旧版另存新版）、`continueMessage(id)`（接着写并合并进同一条）、`switchVariant(id, ±1)`、`editMessage(id, text, regenerateAfter)`（编辑 AI 回复存为新版本；编辑用户消息可选丢弃后文并重新生成）。
  - UI：消息气泡显示 `‹ 1/2 ›` 版本切换；长按菜单 = 复制 / 编辑 / 重新生成 / 继续写 / 删除；编辑对话框带「保存并重新生成后面的回复」勾选项。
  - 涉及文件：`data/Models.kt`、`data/DbHelper.kt`、`ui/State.kt`、`ui/MainViewModel.kt`、`ui/Components.kt`、`ui/ChatScreen.kt`
  - 构建注意：装过 v1.0.0 的设备升级安装后首次启动会自动 `ALTER TABLE`，旧对话不丢。

- [x] **P0-2 Prompt 预览 + 世界书诊断**
  - 数据层：新增 `data/PromptDebug.kt`（`PromptDebug` / `WorldHit` / `DebugMessage` + JSON 编解码）；`PromptBuilder.Built` 多返回一个 `debug` 快照（段落 token、世界书逐条命中、最终 messages）。
  - 逻辑层：`PromptBuilder` 为每个世界书条目产出诊断：`已注入 / 未触发 / 已停用 / 内容为空 / 预算不足`，并记录配置触发词、实际命中词、position、insertion order、token 占用、原因；每次生成（新消息 / 重生成 / 继续）把 `debug` JSON 写进对应消息的 `debug` 列。
  - UI：聊天页顶部新增 `Prompt` 按钮 = 「下一次请求会发什么」实时预览；长按任意角色消息 →「查看本回合 Prompt」看历史快照。弹窗三个页签：`最终输入`（真正发给模型的 messages）/ `世界书`（逐条命中诊断）/ `预算`（上下文上限、输出预留、系统段、世界书、历史、合计、剩余）。
  - 涉及文件：`data/PromptDebug.kt`（新）、`data/PromptBuilder.kt`、`ui/MainViewModel.kt`、`ui/State.kt`、`ui/Components.kt`、`ui/ChatScreen.kt`、`ui/PromptDebugDialog.kt`（新）
  - 构建注意：`debug` 列存整段 Prompt，长对话下每条消息可能多占几 KB；如果嫌数据库大，后续可加「只存最近 N 条」的清理策略。

- [x] **P0-3 预设（Prompt Preset）系统**
  - 数据层：`data/Models.kt` 新增 `Preset`（名称 / 系统提示 / 后置指令 / temperature / top_p / maxTokens / 风格 / 内置标记）；DB_VERSION 2 → 3：新建 `presets` 表、`chats` 加 `preset_id`，首次建库/升级时自动种入 4 套内置预设（标准 / 创意 / 发散 / 抗复读）；备份/恢复包含预设（按名称回填会话绑定）。
  - 逻辑层：`MainViewModel.resolveSettings(presetId)`——会话选了预设就用预设的系统提示 + 温度/top_p/最大输出 + 后置指令，否则用设置页的全局提示词；生成、重生成、继续、Prompt 预览、上下文 token 估算全部走这套解析；新增 `loadPresets / openPresets / savePreset / deletePreset / setChatPreset`。
  - UI：新增 `ui/PresetScreen.kt`（预设列表 / 新建 / 编辑 / 复制 / 删除，编辑含风格 chips、参数、系统提示、后置指令）；聊天页底部输入框上方新增 `预设：xxx` 按钮，可给单个会话切换（含「跟随全局设置」）；设置页「系统预设」下新增「管理预设」入口。
  - 涉及文件：`data/Models.kt`、`data/DbHelper.kt`、`data/PromptBuilder.kt`（+`presetPostHistory` 参数）、`ui/State.kt`、`ui/MainViewModel.kt`、`ui/AppRoot.kt`、`ui/ChatScreen.kt`、`ui/SettingsScreen.kt`、`ui/PresetScreen.kt`（新）
  - 构建注意：DB v3 迁移会新增 `presets` 表和 `chats.preset_id`；升级安装后旧会话默认「跟随全局」。

- [x] **P0-4 token 预算面板 + usage 真实用量 / 成本**
  - 数据层：`AppSettings` 新增 `includeUsage`、`priceInputPerMillion`、`priceOutputPerMillion`（Prefs 持久化）；`Message` 新增 `promptTokens / completionTokens`；DB_VERSION 3 → 4（`messages` 加两列，升级自动迁移，备份/恢复包含用量）。
  - 网络层：流式请求 body 自动加 `stream_options: {"include_usage": true}`（可在设置关闭，兼容不支持的中转站）；`generate()` 解析 SSE 里 `usage` 字段，生成中实时显示，收尾时写入消息。
  - UI：聊天页底部条升级为两行——`预算：系统(x，其中世界书 y)·历史·输出预留·上限` + `本轮：输入/输出 tokens · ≈ ¥金额`；流式气泡里也会显示实时用量；设置页新增「用量与成本」：usage 开关、输入/输出单价（每百万 token）。`ChatData` 增加 `debug` 供预算展示。
  - 涉及文件：`data/Models.kt`、`data/Prefs.kt`、`data/DbHelper.kt`、`net/ApiClient.kt`、`ui/State.kt`、`ui/MainViewModel.kt`、`ui/ChatScreen.kt`、`ui/SettingsScreen.kt`
  - 构建注意：若某中转站返回 400（不支持 `stream_options`），在设置里关闭「请求真实 token 用量」即可恢复；旧消息无用量数据，金额只对开启后的新回复生效。

- [x] **P1-5 长期记忆 / 自动摘要**
  - 数据层：新增 `ChatMemory` 模型与 `chat_memory` 表（chat_id 主键 / summary / msg_upto_id / updated_at），DB_VERSION 4 → 5；删卡、删会话同步清理记忆；备份/恢复包含摘要（恢复后按「摘要 + 全部原文」并存处理，不会丢）。
  - 逻辑层：`MainViewModel.summarizeMemory()`——每轮生成结束后自动检查未摘要的历史（≥16 条或 ≥ 上下文 10% token 时触发），用当前供应商模型（temperature 0.3 / max 900，非流式）把「旧摘要 + 新剧情」整合成前情提要入库；有摘要时 `PromptBuilder` 只发送摘要之后的原文，并在系统提示里注入 `【前情提要（更早剧情，已压缩）】`，长篇对话省 token 且不断片。新增 `refreshMemory / editMemory / clearMemory`。
  - UI：聊天页「预设」旁新增 `记忆：无 / 记忆：N 字` 按钮 → 记忆弹窗（摘要内容、覆盖到第几条、字数），可 **立即生成 / 编辑 / 清空**；摘要编辑用现成 `InputDialog`。
  - 涉及文件：`data/Models.kt`、`data/DbHelper.kt`、`data/PromptBuilder.kt`、`ui/State.kt`、`ui/MainViewModel.kt`、`ui/ChatScreen.kt`
  - 构建注意：自动摘要会额外调用一次非流式 API（少量 token）；不想花钱可只用手动「立即生成」，或后续加开关。

- [x] **P1-7 分支 / 检查点 / 带前情开新档**
  - 数据层：`Chat` 加 `branchedFrom`；新增 `Checkpoint` 模型与 `checkpoints` 表；DB_VERSION 5 → 6（`chats.branched_from`、`checkpoints` 表）；删卡/删会话同步清理检查点。
  - 逻辑层：`DbHelper.branchChat(sourceChatId, uptoMessageId)`——事务内复制该消息（含）之前的全部消息（含多版本、debug、用量、预设），并映射长期记忆覆盖点；`saveCheckpoint / listCheckpoints / deleteCheckpoint / restoreCheckpoint`（回到检查点=删除其后消息）；`MainViewModel` 新增 `branchFromMessage / saveCheckpointAt / deleteCheckpoint / restoreCheckpoint / startNewChatWithRecap`（后者强制生成摘要→新建「前情续档」会话→摘要挂到新档）。
  - UI：长按任意消息 →「从这里分支」/「在此保存检查点」；聊天页新增 `存档/分支` 按钮 → 弹窗列出检查点（回到/删除）并提供「带前情开新档」；回到检查点有二次确认。
  - 涉及文件：`data/Models.kt`、`data/DbHelper.kt`、`ui/State.kt`、`ui/MainViewModel.kt`、`ui/Components.kt`、`ui/ChatScreen.kt`
  - 构建注意：分支会话是**独立副本**（之后两边互不影响）；检查点「回到」会删除后续消息，不可恢复；检查点暂未纳入备份 JSON（下一轮或收尾时可补）。

- [x] **P1-8 世界书增强**
  - 数据层：`WorldEntry` 新增 `probability`（条件概率 0-100）、`depth`（in_history 插入深度）、`secondaryKeys`（辅助关键词）；DB_VERSION 6 → 7（`world_entries` 加 3 列）；导入/导出 JSON 兼容 `probability / depth / secondary_keys`（含 V3 卡 `extensions` 里的字段）。
  - 逻辑层：`PromptBuilder` 重写——`position` 真正生效（系统提示前/角色描述前/后/系统提示后/插入历史/示例开头/示例结尾/开场白结尾 8 档，未知位置兜底追加）；`in_history` 按 `depth` 作为独立 system 消息插入；`probability` 用「条目 id + 本轮最后消息 id」确定性计算（重生成同一轮结果一致）；辅助关键词不满足则跳过；`priority` 作为同 `insertion_order` 下的次级排序（不再只是标记）；扫描窗口与预算改为设置项。
  - 设置：新增「世界书」设置——触发扫描最近 N 条（默认 6）、预算占上下文百分比（默认 25%，可 5-60）。
  - UI：世界书条目编辑弹窗新增 8 个插入位置 chips、条件概率、插入深度、辅助关键词；`Prompt 调试` 的世界书页签会显示这些新状态与原因。
  - 涉及文件：`data/Models.kt`、`data/Prefs.kt`、`data/DbHelper.kt`、`data/JsonCodec.kt`、`data/PromptBuilder.kt`、`ui/CardDetailScreen.kt`、`ui/SettingsScreen.kt`
  - 构建注意：DB v7 迁移自动完成；旧条目默认 probability=100、depth=4、辅助关键词为空，行为与之前一致。

- [x] **P1-6 互动面板（状态 / 关系 / 背包 / 剧情选项）**
  - 数据层：`CharacterCard` 加 `statusTemplate`；新增 `PanelModels.kt`（`PanelTemplate / PanelDef / PanelField / PanelRuntime / PanelOutputParser`）；DB_VERSION 7 → 8（`cards.status_template`、`panel_state` 表）；删卡/删会话清理面板状态；导入导出/备份包含模板。
  - Prompt：卡片配置模板后，系统提示自动追加「互动面板输出格式说明 + 当前面板状态」，要求模型每轮末尾输出 `<panel>{...}</panel>`。
  - 生成：流式结束后自动抽取 `<panel>` 块 → 正文去掉该块再落库、JSON 写入 `panel_state`；面板解析失败则沿用上一轮状态（不会乱）。
  - UI：聊天页出现**互动面板条**——横向滚动显示各面板（状态/关系/背包/自定义）的字段值，下面是模型给出的 2~4 个**剧情选项 chip，点一下即作为玩家回复发送**；可「重置」面板状态。卡片详情页新增「互动面板」区块，用 JSON 编辑模板（填入示例 / 清空 / 校验）。
  - 涉及文件：`data/Models.kt`、`data/PanelModels.kt`（新）、`data/DbHelper.kt`、`data/JsonCodec.kt`、`data/PromptBuilder.kt`、`ui/State.kt`、`ui/MainViewModel.kt`、`ui/ChatScreen.kt`、`ui/PanelBar.kt`（新）、`ui/CardDetailScreen.kt`
  - 构建注意：模板是自由 JSON，格式不对会提示且不保存；模型不一定每轮严格输出 `<panel>`，缺失时保持上一轮状态（与 DZMM 相同的容错策略）。

- [x] **P1-9 卡片编辑器 + PNG 导出**
  - 数据层：`DbHelper.updateCard()`；`JsonCodec.buildCharaCardV3()`（chara_card_v3 / 3.0 导出）；新增 `PngCardWriter`（把 V2/V3 JSON base64 写进 PNG 的 `chara` / `ccv3` tEXt 块；无头像时生成渐变占位图；头像不是 PNG 时退回占位图）。
  - 编辑器：新增 `CardEditorScreen`——头像（相册选择）/角色名/作者/版本/标签/角色卡介绍/详细描述/性格/场景/系统指令/作者注释/开场白/备选开场白（每行一条）/对话示例/建议回复/互动面板模板（填入示例/清空）；新建与编辑共用，保存后回到详情页。
  - 入口：卡片库顶部「新建」；卡片详情页顶部「编辑」。
  - 导出：详情页「导出」下拉 = 导出 JSON（原有）/ **导出 PNG 角色卡**（标准酒馆卡，可被 SillyTavern/其他酒馆导入）。
  - 涉及文件：`data/DbHelper.kt`、`data/JsonCodec.kt`、`data/PngCardWriter.kt`（新）、`ui/State.kt`、`ui/MainViewModel.kt`、`ui/AppRoot.kt`、`ui/LibraryScreen.kt`、`ui/CardDetailScreen.kt`、`ui/CardEditorScreen.kt`（新）
  - 构建注意：PNG 导出会把整张头像作为底图（≤ 原图体积）；如果头像过大建议先压缩。编辑已有卡不会改世界书条目。

## 待完成（按顺序）

- [ ] （无）

## 收尾完成

- [x] 全部 9 项功能实现并记录于本文件
- [x] `README.md` 更新为 v1.1.0（新功能、玩法、构建、隐私、限制）
- [x] `构建说明.md`（环境、Android Studio/命令行构建、常见问题、自测清单、回滚）
- [x] `app/build.gradle.kts`：versionCode 2 / versionName 1.1.0
- [x] 打包 `私酒馆-源码-v1.1.0.zip` 放回桌面文件夹（原 v1.0.0 的 zip/APK 未改动）
- [x] 静态一致性检查：`db.*` 调用、`vm.*` 调用、`ui.*` 属性、`Screen.*` 引用、括号平衡均通过
- [x] **编译验证通过**（`assembleDebug` BUILD SUCCESSFUL）
- [x] **APK 已产出**：`私酒馆-v1.1.0-debug.apk`（9,639,382 字节）

## 构建验证记录

本机原本没有 JDK/SDK/Gradle，本次使用便携工具链完成构建：

| 组件 | 位置 |
|---|---|
| JDK | 环境变量 `JAVA_HOME`（JDK 17+） |
| Android SDK | 环境变量 `ANDROID_HOME`（platform-34、build-tools 34.0.0） |
| Gradle | 环境变量 `GRADLE_BIN` 指向 `gradle.bat`（Gradle 8.7） |
| 构建目录 | `%TEMP%\tavern-build`（ASCII 路径） |

- 首次失败：项目路径含中文 → AGP 报 `non-ASCII characters`（已用 ASCII 构建目录 + `android.overridePathCheck=true` 解决）。
- 唯一编译错误：`MainViewModel.summarizeMemory` 里 `memory.summary` 可空智能转换失败 → 改为 `previousSummary` 局部变量后通过。
- 产物校验：`aapt dump badging` = `com.dsh.tavern` versionCode 2 / versionName 1.1.0 / minSdk 26 / target 34 / label「私酒馆」/ launchable `MainActivity`；`apksigner verify` = Android Debug 证书。
- APK SHA256：`C4A28B4A73033EF18BD6B632203BF02095CEC734E52518CAF830362906C261B0`
- **未做真机/模拟器运行验证**（本机无设备/模拟器），建议按 `构建说明.md` 第 5 节自测。

## 已知限制

- 无法本地编译验证（无 SDK/JDK）；改动以 Kotlin/Compose 标准 API 为准，未引入新依赖。
- 「继续写」通过临时 user 指令实现（不落库），对模型兼容性最好；后续可在预设里自定义这句提示。
- 世界书条目 `position` 字段目前仍未真正参与注入位置（P1-8 处理）。

---

# v1.2 迭代记录（可改进项全量落地）

## A. 修复与基础体验
- **PNG 导出修复**：`PngCardWriter` 头像非 PNG 时先重编码为 PNG（兼容 v1.1 导入的 JPEG 头像）；导出前剥离已有 `chara`/`ccv3` 块。
- **明暗主题**：`Theme.kt` 增加浅色方案 + `TavernTheme(themeMode)`；`MainActivity` 跟随 `ui.settings.themeMode`；设置页「外观」深色/浅色/跟随系统。
- **Markdown 渲染**：新增 `ui/RichText.kt`（粗体/斜体/行内代码/引用/代码块/标题），消息气泡与流式气泡使用。

## B. 体验/成本/数据安全
- 卡片库搜索（名称/标签）+ 排序（最近/名称/对话数）。
- 全局会话列表 + 消息搜索：`Screen.ChatList`、`ChatListScreen`、`DbHelper.listAllChats/searchMessages`。
- 导出对话 Markdown：聊天页「存档/分支」→「导出对话」。
- 自动备份：每天首次启动备份到 `Android/data/com.dsh.tavern/files/backups/`，保留 7 份；设置里开关 + 立即备份。
- 备份增强：JSON 增加检查点（按消息 id 映射恢复）、面板状态、branched_from；DB v9 用量表。
- 用量统计与日预算：`usage_log` 表、今日/本月 token 与金额、聊天页底部今日花费、超预算 toast。
- 自动摘要开关 + 阈值可调（设置页「记忆与摘要」）。

## C. 玩法/工具增强
- 面板容错：`PanelOutputParser` 支持 `<panel>` 与 ```panel 代码块；面板「编辑」可手改 JSON。
- 预设增强：停止序列、frequency/presence penalty；预设导入/导出 JSON（DB v10）。
- 世界书独立导入/导出（卡片详情页）。
- 卡片编辑器内世界书增删改（`editorEntries` + `replaceEntries`）。
- 模型列表拉取（`/models`）+ 自定义请求头（ProviderConfig.extraHeaders）。
- 长对话分页：`getMessages(limit,beforeId)`、`ChatData.hasMore`、「加载更早的消息」。
- Prompt 调试数据清理：`pruneMessageDebug(keep 30)`。
- 卡片检查：详情页 token 估算 + 缺失字段提示。

## D. 工程项
- `build.ps1` 一键构建（定位 JDK/SDK/Gradle、ASCII 构建目录、输出 APK + SHA256）。
- 单元测试扩充到 8 个（PromptBuilder 位置/概率/辅助关键词、PanelOutputParser、世界书 JSON roundtrip）。

## v1.2.0 构建与验证
- 构建：JDK 21 + Gradle 8.7（通过 `GRADLE_BIN` 指定）+ Android SDK 34，`assembleDebug` BUILD SUCCESSFUL。
- 单元测试：`testDebugUnitTest` 的 Gradle worker 因用户目录含中文启动失败 → 改用 JUnitCore 直接执行，**8/8 通过**。
- APK：`私酒馆-v1.2.0-debug.apk`，9,718,770 字节，versionCode 3 / versionName 1.2.0 / minSdk 26 / target 34，debug 签名（与 v1.0/v1.1 同一证书，可覆盖升级）。
- APK SHA256：`B111D52279B09789781F30C9313DA1AFB08913EE9800D665FB5D41A197B2864F`
- 未做真机运行验证；自测清单见 `构建说明.md` 第 6 节。

---

# v1.3 Tavern 进阶能力记录

## E1 世界书与卡片进阶
- **卡片复制**：`DbHelper.duplicateCard`（连同世界书），卡片库菜单「复制角色卡」。
- **卡片默认预设绑定**：`cards.default_preset_id`；卡片编辑器可选默认预设，新建对话自动套用。
- **跨卡全局世界书**：`global_world_entries` 表 + 设置页入口 + 独立管理页；与卡片世界书合并参与触发/预算，Prompt 诊断标「全局」。
- **世界书正则触发**：`WorldEntry.regex`；触发词按正则匹配（非法正则安全忽略），编辑器可开关。

## E2 变量与输出后处理
- **变量系统**：`chat_variables` 表；卡片可定义初始变量（每行 名字=值）；PromptBuilder 注入【变量】并替换 `{{变量名}}` 宏；模型可用 `<setvar name="x">值</setvar>` 更新，客户端自动剥离并落库；聊天页「存档/分支 → 变量」用 JSON 编辑。
- **输出正则后处理**：卡片级「正则 => 替换」规则（每行一条），对 AI 回复正文生效；编辑器入口在卡片编辑器。

## E3 多角色同台
- `chat_cards` 表：一个会话绑定 2~4 张卡；首页「群聊」多选创建。
- PromptBuilder 群聊模式：同时列出各角色设定 + 指示模型用【角色名】分段、不替用户行动。
- 世界书合并所有成员条目；群聊标题与提示词生效；记忆/预设/变量/面板仍复用。

## E4 应用锁
- PIN 应用锁（SHA-256 存储，启动锁定页）；设置 → 隐私开启/修改/关闭。
- 「隐藏最近任务预览」：FLAG_SECURE（截屏/录屏黑屏，下次启动生效）。
- 忘记 PIN 只能清除应用数据（文档已注明）。

## v1.3.0 构建与验证
- 数据库版本 13（v11 默认预设/正则/全局世界书、v12 变量/后处理、v13 群聊）。
- 单元测试：在 v1.2 的 8 个基础上新增「变量解析、正则世界书、全局世界书注入」共 11 个。
- APK / 源码包校验值见桌面 `私酒馆-v1.3.0-文件校验.txt`。
- 未做真机运行验证。

---

# v1.3.1 真机反馈修复

- **null 污染修复**：SSE delta 里 `reasoning_content`/`content` 为 JSON null 时 `optString` 会得到字符串 "null"，导致回复/思考过程开头出现大量 null；改为 `isNull` 判断 + `optTextOrEmpty`，并在落库与显示前做连续 null 清理兜底。
- **聊天页模型显示与切换**：输入框上方「模型：xxx + 供应商」按钮，弹窗可切供应商、选/手填模型、拉取 /models 列表；`switchModel` 写回设置。
- **动态建议回复**：新增设置项（默认开）；每轮回复后调用轻量非流式请求按最近 8 条剧情生成 3 条用户可选回复；失败静默，关闭时回退角色卡固定建议。
- **最低回复字数**：设置项（0~5000），写进 PromptBuilder 系统提示；生成时自动把 max_tokens 提升到至少 minReplyLength+512。
- **{{user}}/{{char}} 宏全面替换**：系统提示、角色卡各字段、世界书、历史消息、预设后置、记忆、面板指令统一 macro()；开场白创建会话时替换；旧消息显示层替换。
- **上下文上限**：设置页新增 32K/64K/128K/200K 快捷档位与说明（只影响提示组装/预算，不改模型硬上限）。
- 单元测试新增「宏替换、最低字数提示」2 个，共 **13/13 通过**。
- 版本：versionCode 5 / versionName 1.3.1。

---

# v1.3.2 模型规格适配

- 新增 `data/ModelCatalog.kt`：DeepSeek V4.1/V4（含 flash 别名）→ 1,000,000 上下文 / 393,216 输出；V3.x/chat/reasoner → 128,000 / 65,536。
- 聊天页切换模型时自动套用该规格到设置；启动时若上下文仍是旧默认 65536 且当前模型可识别，自动升级。
- `/models` 拉取时解析 `context_length` / `context_window` / `top_provider.context_length`，存入 `modelContexts`，切换时用于自动设置。
- 设置页：上下文上限上限 2,000,000（档位 64K/128K/256K/512K/1M），单次最大输出上限 393,216（档位 4K/8K/16K/64K/384K）。
- 单元测试新增 `modelCatalogKnowsV41`，共 **14/14 通过**。
- 版本：versionCode 6 / versionName 1.3.2。

---

# v1.4.0 角色卡工坊与花费可视化

- **花费可见**：MessageBubble 增加单条 `costText`（真实 token × 单价）；聊天页底部新增「本会话 ≈ ¥」；`CostCalculator` 复用。
- **角色卡工坊**：新增 `data/CardPromptTemplates.kt`（构思/润色、V3 JSON 生成、立绘提示词、导入说明、标题套路参考、分级世界书规范）与 `ui/CardWorkshopScreen.kt`。
- **粘贴导入**：`CardImporter.importBytes` 解析 V3/V2/裸卡 JSON，VM `importCardFromJson` 入库并跳转详情。
- **API 直接生成**：VM `generateCardFromWorkshop` 调 `ApiClient.complete`（max 12K）+ `extractJsonObject` 容错截取 → 自动导入；`workshopBusy` 状态。
- 首页入口「工坊」；`Screen.Workshop` 路由。
- 单元测试新增 `workshopPromptsContainSchemaAndGrading`，共 **15/15 通过**。
- 版本：versionCode 7 / versionName 1.4.0。

---

# v1.4.1 工坊可用性增强

- 工坊 6 个示例模板（古风权谋/都市禁忌/校园反差/西幻/悬疑/末日）一键填充输入。
- `WorkshopInput.appearance` 外貌要点字段：写入卡面 description 与立绘提示词。
- 生成/粘贴改为**预览→确认导入**：`UiState.workshopCard/workshopEntries/workshopJson`；新增 `confirmWorkshopImport/clearWorkshopPreview/exportWorkshopJson`。
- 预览含质检：缺角色名、描述<200、开场白<80、无标签、无世界书、无 T0 常驻、非触发条目无 keys。
- 预览可复制/导出 JSON 文件（SAF）。
- 单元测试覆盖外貌要点传递，共 **15/15 通过**。
- 版本：versionCode 8 / versionName 1.4.1。

---

# v1.5.0 离线分享包（角色卡 + 聊天记录）

- 新增 `data/SharePack.kt`：单 JSON 分享包 `format=dsh_tavern_share`，内含 `card`（V3 字段 + avatar_b64）、`world_entries`、`chats`（title/preset_name/messages[role,content,reasoning,created_at,tokens,variants]/memory/variables/panel_state）、`meta`。
- 导出：卡片详情「导出」菜单新增「导出分享包（卡+聊天记录）」「导出分享包（仅角色卡）」；VM `exportSharePack`。
- 导入：`importFromUri` 与 `importBackup` 先检测 `SharePack.isSharePack`，命中则走 `SharePack.import`（建卡、写世界书、按顺序重建聊天、恢复记忆/变量/面板/多版本），完成后跳转卡片详情。
- 单测新增 `sharePackFormatDetection`，共 **16/16 通过**。
- 版本：versionCode 9 / versionName 1.5.0。

---

# v1.5.1 工坊创作面板重做

- `WorkshopInput` 扩展：gender/scale/world/archetype/plot/priorityBase/placeholder（保留 idea/name/genre/tone/appearance/worldbookLevel/titleCount/extra）。
- 新增 `WorkshopOptions`：性别/尺度/世界观/人设/主线/占位符选项、五个精选题材模板（现代都市/古代/玄幻修仙/校园/家庭，2~3 句梗概）、randomAll/randomWorld/randomArchetype/randomPlot/randomGender。
- `CardPromptTemplates` 把选项写入构思/生成/立绘提示词；占位符与 priorityBase 一并传给 AI。
- `CardWorkshopScreen` 重做输入区：梗概 + 一键随机/清空 + 五个模板 + 选项下拉（每个可随机）+ 自填字段；保留提示词/API 生成/预览质检/粘贴/导出。
- 单测覆盖世界观/人设/主线/性别写入，共 **16/16 通过**。
- 版本：versionCode 10 / versionName 1.5.1。

---

# v1.5.2 工坊「全部可随机/可自编」与说明

- 删除五个内置题材模板（按用户意见），`WorkshopOptions` 移除 templates/QualityTemplate。
- 新增随机素材池：男女名、genres、tones、appearances、优先级；`randomName/randomGenre/randomTone/randomAppearance/randomPriority/randomWorldbookLevel/randomTitleCount/randomSynopsis/randomAll`。
- `OptionSelector` 支持下拉 + 「随机」+ 「自定义…」弹窗输入；世界观/人设/主线/性别/尺度/占位符/世界书规模/标题数量全部适用。
- 角色名/题材/基调/外貌/优先级/梗概均可点随机；世界书规模与标题数量加说明文案（含义与条数范围）。
- 版本：versionCode 11 / versionName 1.5.2。

---

# v1.5.3 随机只随机选项、梗概可空

- `WorkshopOptions.randomAll(idea)` 只随机选项，不再生成/覆盖故事梗概与角色名；删除 `randomSynopsis`。
- 删除「随机一段梗概」按钮；「🎲 一键随机」改名「🎲 随机选项」并加说明。
- 梗概可空：`cardJsonPrompt`/`ideationPrompt` 在 idea 为空时追加「没有提供梗概，请按世界观/人设/主线/尺度自行设计，不要反问」。
- API 生成不再要求梗概/角色名非空，按钮始终可用；按钮文案改「生成并预览」。
- 单测新增 `workshopPromptHandlesBlankSynopsis`，共 **17/17 通过**。
- 版本：versionCode 12 / versionName 1.5.3。

---

# v1.5.4 工坊三步生成（选项 → 梗概 → 卡片）

- `WorkshopOptions.synopsisLengths`：短/中/长三档 + 自定义。
- `CardPromptTemplates.synopsisPrompt(input, lengthLabel)`：只输出梗概正文、按字数范围、体现所选选项。
- `UiState.workshopSynopsisBusy`；VM `generateWorkshopSynopsis(input, length, onResult)`（非流式 complete，回调交回 UI 编辑）。
- UI：随机选项 / API 随机梗概 两个按钮并排 + 梗概字数选择 + 清空 / 🎲 全自动生成；全自动 = 随机选项→API 梗概→API 卡片，中途结果可改。
- 单测新增 `synopsisPromptHasLengthAndOptions`，共 **18/18 通过**。
- 版本：versionCode 13 / versionName 1.5.4。

---

# v1.5.5 随机一致性修复（尊重用户已设 + 性别/人设/外貌统一）

- 移除「🎲 全自动生成」按钮与 `autoGenerateAll`：API 随机梗概只填梗概，绝不再自动出卡；三步互不串联。
- `WorkshopOptions` 重构随机逻辑：
  - `randomizeAll(existing)` 只随机未设置（自动/随机/空）的字段，用户已选的性别/人设/主线/世界观/题材/基调/外貌/梗概/角色名原样保留；
  - 性别推断：女性人设→女性、男性人设→男性；性别固定时人设从对应池+通用池抽取；
  - 人设池按性别拆分（新增冷面校草/痞帅学长/腹黑上司/病弱公子/忠犬护卫/黑道少爷）；
  - 外貌池按性别拆分；题材/基调按世界观约束映射；
  - `randomGender()` 只返回女性/男性，不再返回「随机」。
- 单测新增 `randomizeRespectsUserSettingsAndGenderCoherence`，共 **19/19 通过**。
- 版本：versionCode 14 / versionName 1.5.5。

---

# v1.5.6 工坊随机全部改走 API（尊重已确定设定）

- 删除内置随机池/`randomizeAll`，改为 API 生成：
  - `WorkshopFieldRule` 枚举（12 个字段，含候选约束）；
  - `CardPromptTemplates.randomFieldPrompt(field, input)`：已确定的其他字段作为约束，只随机目标字段；
  - `CardPromptTemplates.randomOptionsPrompt(input)`：整组随机，只补未设置字段，输出 JSON；
- VM 新增 `randomWorkshopField` / `randomWorkshopOptions`（temp 1.1，maxTokens 300/800，结果回调 UI）；`UiState.workshopFieldBusy`。
- UI：每个字段「随机」按钮和「🎲 随机选项」均走 API；提示词说明会消耗 token；「API 随机梗概」仍只生成梗概、不出卡。
- 单测替换为 `randomFieldPromptsRespectLockedSettings`（约束上下文/候选池/JSON 格式），共 **19/19 通过**。
- 版本：versionCode 15 / versionName 1.5.6。

---

# v1.5.7 梗概润色（可多次/可撤回/可回填选项）

- `CardPromptTemplates.polishSynopsisPrompt`：保留原梗概与已定设定，按目标字数润色文笔/节奏/钩子，只输出梗概正文。
- `CardPromptTemplates.extractOptionsPrompt`：从梗概提取选项 JSON（gender/world/archetype/plot/scale/genre/tone/appearance）。
- VM：`polishWorkshopSynopsis`（temp 0.9，max 1800）、`fillOptionsFromSynopsis`（temp 0.3）、`applyOptionsJson` 合并助手（随机/回填共用）；`UiState.workshopPolishBusy`。
- UI：✨ 润色梗概 / ↩ 撤回润色（本地历史栈，逐步回退）/ 按当前梗概填写下面的选项；只改选项、不出卡；润色与选项仍可手改。
- 单测新增 `polishAndExtractPromptsKeepUserSynopsis`，共 **20/20 通过**。
- 版本：versionCode 16 / versionName 1.5.7。

---

# v1.5.8 草稿持久化 + 润色重做（redo）

- 工坊全部输入迁入 ViewModel：`UiState.workshopDraft: WorkshopInput`（含 synopsisLength），`workshopUndo/workshopRedo` 历史栈；屏幕改为无状态读 `ui.workshopDraft`。
- 新增 `updateWorkshopDraft`；随机/润色/梗概/回填选项/生成卡都直接读写草稿；**生成失败只提示，不清任何字段**；切页/旋转也不丢。
- 润色撤回升级为双向：`undoWorkshopPolish` / `redoWorkshopPolish`，UI 三按钮「✨润色 / ↩撤回 / ↪恢复」。
- 移除带回调的旧 VM 工坊函数（`randomWorkshopField(input,onResult)` 等），改为无参草稿版。
- 版本：versionCode 17 / versionName 1.5.8。

---

# v1.6.0 卡片字段 AI 改写 + 世界书收藏库

- 新增 `FieldConflict` / `WorldbookCollection` 模型；DB v14：`worldbook_collections`、`worldbook_library`（条目字段与 world_entries 一致，含 collection_id）。
- `CardPromptTemplates` 新增：`rewriteCardFieldPrompt`、`conflictCheckPrompt`、`generateWorldbookEntryPrompt`、`adaptWorldbookEntriesPrompt`、`fieldLabelOf/fieldValue`。
- VM：`rewriteCardField`、`checkCardConflicts`、`generateWorldbookEntryAI`、`adaptWorldbookEntriesAI`、世界书库 CRUD（收藏/分组/导入/适配导入）；`UiState.aiBusy/worldbookCollections`；`Screen.WorldbookLibrary(cardId)`。
- 编辑器：✨AI 改写字段（字段下拉+要求+结果可编辑）、冲突黄字提示 +「保留冲突/一并修改」、↩/↪ 撤回恢复（含 AI 改写与一并修改）。
- 世界书条目对话框：AI 生成/改写内容、★ 收藏到分组。
- 新页面 `WorldbookLibraryScreen`：分组列表、展开条目、导入本卡、API 适配导入（保留指定描写）、新建/删除分组。
- 单测新增 `cardAiPromptsHaveContextAndJsonContract`，共 **21/21 通过**。
- 版本：versionCode 18 / versionName 1.6.0。

---

# v1.6.1 世界书开关 / 收藏即建组 / 收藏库多选导入

- 角色卡编辑器世界书条目加**启用/停用 Switch**（与详情页一致）。
- `WorldEntryDialog` ★收藏不再因无分组禁用：下拉列出已建分组 + 「＋ 新建分组并收藏…」（内嵌输入框）；新增回调 `onSaveToNewCollection`；VM `saveWorldEntryToNewCollection`（重名复用分组）。
- 新增 `WorldbookPickerDialog`：先选分组 → 展示条目 → 单条勾选 / 全选本组 → 导入选中。
- 入口：卡片详情「世界书 → 从收藏选择」、角色卡编辑器「世界书 → 从收藏库选择」；VM 新增 `importWorldbookEntriesToCard`（详情页导入当前卡）。
- 版本：versionCode 19 / versionName 1.6.1。

---

# v1.7.0 同人/小说原文导入 + 世界书结构校验

- 新增 `data/SourceImporter.kt`：TXT（UTF-8/GBK 自动、章节切分）、EPUB（container/opf/spine、HTML 去标签）解析为 `SourceDocument`；本地处理不联网。
- `CardPromptTemplates.sourceAnalysisPrompt`：按时间节点从原文提取 synopsis/name/gender/world/archetype/plot/genre/tone/appearance/summary；`cardJsonPrompt(input, sourceExcerpt, sourceSummary)` 增加「原作参考」段落与防 OOC 要求。
- VM：`importWorkshopSource`（Uri→解析）、`clearWorkshopSource`、`analyzeWorkshopSource`（按章节/事件截取原文→API→填草稿）、`buildSourceExcerpt`；`UiState.workshopSource*`；生成卡时把原文节选+原作要点带入提示词。
- 工坊 UI：TXT/EPUB 导入、时间点选择（第一章/最后一章/指定章节/事件之后）、分析要求、提取按钮、原作参考摘要。
- 世界书：AI 生成/改写后本地结构校验（位置/触发词/常驻/概率/深度/正则冲突），黄字提示 +「保留/一键修正」。
- 单测新增 TXT 切章/HTML 清理、原文分析与卡片提示词引用，共 **23/23 通过**。
- 版本：versionCode 20 / versionName 1.7.0。

---

# v1.7.1 原作要求贯穿全流程

- `UiState.workshopSourceRequirement`；工坊在「选择 TXT/EPUB」旁新增原作要求输入框（角色/时间/地点/其他），变更即写入 VM。
- `sourceAnalysisPrompt` 强化时间/地点/角色约束与「summary 里梳理主要人物关系（含配角/组织/地点）」。
- `cardJsonPrompt(..., sourceRequirement)`：卡片生成参考原文+原作要点+改编要求，并要求写清配角关系。
- `rewriteCardFieldPrompt(..., sourceExcerpt, sourceSummary, sourceRequirement)`：卡片编辑器 AI 改写也参考原作与要求，防 OOC。
- `generateWorldbookEntryPrompt(..., sourceExcerpt, sourceSummary, sourceRequirement)`：世界书 AI 生成/修改参考原作，可梳理配角关系。
- VM 全部调用点传入 `workshopSource*`。
- 单测新增 `sourceRequirementFlowsIntoCardAndAiPrompts`，共 **24/24 通过**。
- 版本：versionCode 21 / versionName 1.7.1。

---

# v1.7.2 模型切换列表补全

- `ModelCatalog.knownIds`：deepseek-v4.1-flash / deepseek-v4.1-flash-1017 / deepseek-v4.1 / deepseek-chat / deepseek-reasoner。
- `ModelPickDialog` 常用列表 = knownIds + 当前模型 + /models 拉取结果，去重；V4.1 标注 1M 上下文；标题改为「常用模型 / 已拉取模型（V4.1 系列为 1,000,000 上下文）」。
- 版本：versionCode 22 / versionName 1.7.2。
