package com.dsh.tavern.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsh.tavern.data.AppSettings
import com.dsh.tavern.data.ApiErrorParser
import com.dsh.tavern.data.CardImporter
import com.dsh.tavern.data.CardPromptTemplates
import com.dsh.tavern.data.CharacterCard
import com.dsh.tavern.data.CostCalculator
import com.dsh.tavern.data.Crypto
import com.dsh.tavern.data.DbHelper
import com.dsh.tavern.data.DebugMessage
import com.dsh.tavern.data.FieldConflict
import com.dsh.tavern.data.ImageTextExtractor
import com.dsh.tavern.data.JsonCodec
import com.dsh.tavern.data.Message
import com.dsh.tavern.data.MessageVariant
import com.dsh.tavern.data.ModelCatalog
import com.dsh.tavern.data.ModelSpec
import com.dsh.tavern.data.PanelOutputParser
import com.dsh.tavern.data.PanelTemplate
import com.dsh.tavern.data.PngCardWriter
import com.dsh.tavern.data.Prefs
import com.dsh.tavern.data.Preset
import com.dsh.tavern.data.PromptBuilder
import com.dsh.tavern.data.PromptDebug
import com.dsh.tavern.data.ProviderConfig
import com.dsh.tavern.data.SharePack
import com.dsh.tavern.data.SourceDocument
import com.dsh.tavern.data.SourceImporter
import com.dsh.tavern.data.TokenEstimator
import com.dsh.tavern.data.UsageStats
import com.dsh.tavern.data.VariableParser
import com.dsh.tavern.data.WorldEntry
import com.dsh.tavern.data.WorldbookCollection
import com.dsh.tavern.data.WorkshopFieldRule
import com.dsh.tavern.data.WorkshopInput
import com.dsh.tavern.net.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        /** 「继续写」时追加给模型的临时指令（不落库）。 */
        const val CONTINUE_PROMPT =
            "（请直接从上一段结尾自然接着写下去，不要重复或总结已经写过的内容，不要加任何说明。）"

        /** 生成前情提要用的系统提示。 */
        const val SUMMARY_SYSTEM =
            "你是一名剧情摘要助手，负责把角色扮演对话压缩成前情提要。只做事实性总结，不续写、不评价，使用第三人称。"
    }

    /** 正在生成摘要的会话，避免并发重复请求。 */
    private val summarizing = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    private val prefs = Prefs(app)
    private val db = DbHelper(app)
    private val _ui = MutableStateFlow(UiState(settings = prefs.load()))
    val ui = _ui.asStateFlow()

    private val backStack = ArrayDeque<Screen>()
    private var currentCall: Call? = null

    /** 工坊导入的原文（TXT/EPUB），只在内存里，不上传整本。 */
    private var sourceDocument: SourceDocument? = null

    @Volatile
    private var stopRequested = false

    init {
        val initial = _ui.value.settings
        // 老用户默认 64K：如果当前模型是已知 1M/128K 规格，自动把上下文预算升上去
        val initialSpec = ModelCatalog.specFor(initial.provider.model)
        if (initialSpec != null && initial.contextLimit == 65_536) {
            val upgraded = initial.copy(contextLimit = initialSpec.contextLimit)
            prefs.save(upgraded)
            setState { it.copy(settings = upgraded) }
        }
        if (initial.appLockEnabled && initial.appLockPinHash.isNotBlank()) {
            setState { it.copy(locked = true) }
        }
        refreshLibrary()
        loadPresets()
        refreshUsageStats()
        loadGlobalEntries()
        loadWorldbookCollections()
        maybeAutoBackup()
    }

    // ---------------- app lock ----------------

    fun unlock(pin: String) {
        var ok = false
        val settings = _ui.value.settings
        if (settings.appLockPinHash.isNotBlank() && Crypto.sha256(pin) == settings.appLockPinHash) {
            ok = true
            setState { it.copy(locked = false) }
        }
        if (ok) toast("已解锁") else toast("PIN 错误")
    }

    fun enableAppLock(pin: String) {
        val trimmed = pin.trim()
        if (trimmed.length < 4) {
            toast("PIN 至少 4 位数字")
            return
        }
        saveSettings(
            _ui.value.settings.copy(
                appLockEnabled = true,
                appLockPinHash = Crypto.sha256(trimmed)
            )
        )
        toast("应用锁已开启（下次启动需要 PIN）")
    }

    fun disableAppLock() {
        saveSettings(_ui.value.settings.copy(appLockEnabled = false, appLockPinHash = ""))
        toast("应用锁已关闭")
    }

    fun setHideInRecents(value: Boolean) {
        saveSettings(_ui.value.settings.copy(hideInRecents = value))
        toast(if (value) "已开启隐藏最近任务（下次启动生效）" else "已关闭隐藏最近任务")
    }

    fun loadGlobalEntries() {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = db.getGlobalEntries()
            setState { it.copy(globalEntries = entries) }
        }
    }

    /** E1：全局世界书条目：id=0 新增，否则更新。 */
    fun saveGlobalEntry(entry: WorldEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            if (entry.id == 0L) db.insertGlobalEntry(entry) else db.updateGlobalEntry(entry)
            val entries = db.getGlobalEntries()
            setState { it.copy(globalEntries = entries) }
            toast("全局世界书已保存")
        }
    }

    fun toggleGlobalEntry(entry: WorldEntry, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updateGlobalEntry(entry.copy(enabled = enabled))
            val entries = db.getGlobalEntries()
            setState { it.copy(globalEntries = entries) }
        }
    }

    fun deleteGlobalEntry(entry: WorldEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteGlobalEntry(entry.id)
            val entries = db.getGlobalEntries()
            setState { it.copy(globalEntries = entries) }
            toast("已删除全局条目")
        }
    }

    /** 会话绑定了预设就用预设的系统提示/参数，否则用全局设置。 */
    private fun resolveSettings(presetId: Long): Pair<AppSettings, String> {
        val base = _ui.value.settings
        if (presetId <= 0L) return base to ""
        val preset = db.getPreset(presetId) ?: return base to ""
        val resolved = base.copy(
            systemPreset = preset.systemPrompt.ifBlank { base.systemPreset },
            temperature = preset.temperature,
            topP = preset.topP,
            maxTokens = preset.maxTokens,
            stopSequences = preset.stopSequences,
            frequencyPenalty = preset.frequencyPenalty,
            presencePenalty = preset.presencePenalty
        )
        return resolved to preset.postHistory
    }

    private fun setState(block: (UiState) -> UiState) = _ui.update(block)

    private fun toast(message: String) = setState { it.copy(toast = message) }

    fun clearToast() = setState { it.copy(toast = null) }

    fun clearError() = setState {
        it.copy(error = null, modelFixFailed = "", modelFixModels = emptyList())
    }

    /** 非流式请求失败时优先显示解析后的友好错误（含可用模型列表）。 */
    private fun apiErrorOr(fallback: String): String =
        ApiClient.lastError?.takeIf { it.isNotBlank() } ?: fallback

    /**
     * AI 工具（工坊随机 / 原文分析 / 世界书 / 卡片字段改写）使用的设置：
     * 设置了「工具模型」就单独用该模型，否则跟随当前对话模型。
     * Base URL / Key 仍用当前供应商。
     */
    private fun toolSettings(): AppSettings {
        val s = _ui.value.settings
        val providerIndex = if (s.toolProviderIndex in s.providers.indices) {
            s.toolProviderIndex
        } else {
            s.activeProvider.coerceIn(0, (s.providers.size - 1).coerceAtLeast(0))
        }
        val base = s.copy(activeProvider = providerIndex)
        val model = s.toolModel.trim()
        if (model.isEmpty()) return base
        val providers = base.providers.mapIndexed { i, p ->
            if (i == providerIndex) p.copy(model = model) else p
        }
        return base.copy(providers = providers)
    }

    /** 工坊页直接切换 AI 工具用的供应商 + 模型（不影响聊天的对话模型）。 */
    fun saveToolModel(providerIndex: Int, model: String, followChat: Boolean = false) {
        val s = _ui.value.settings
        val index = if (providerIndex in s.providers.indices) providerIndex else s.activeProvider
        val updated = if (followChat) {
            s.copy(toolProviderIndex = -1, toolModel = "")
        } else {
            s.copy(toolProviderIndex = index, toolModel = model.trim())
        }
        saveSettings(updated)
        val provider = s.providers.getOrNull(index)
        val shown = if (followChat || model.isBlank()) {
            provider?.model.orEmpty()
        } else {
            model.trim()
        }
        toast(
            if (followChat) "工坊 AI 已跟随聊天的对话模型"
            else "工坊 AI 模型：${provider?.name.orEmpty()} / $shown"
        )
    }

    /** 接口告知可用模型时：一键切换模型并重试上一次生成。 */
    fun applyModelFixAndRetry(model: String) {
        val settings = _ui.value.settings
        val index = settings.activeProvider.coerceIn(0, (settings.providers.size - 1).coerceAtLeast(0))
        switchModel(index, model)
        setState { it.copy(error = null, modelFixFailed = "", modelFixModels = emptyList()) }
        retryLast()
    }

    fun navigate(screen: Screen) {
        val current = _ui.value.screen
        if (current != screen) backStack.addLast(current)
        setState { it.copy(screen = screen) }
        when (screen) {
            is Screen.CardDetail -> loadCard(screen.cardId)
            is Screen.Chat -> loadChat(screen.chatId)
            is Screen.CardEditor -> loadEditor(screen.cardId)
            else -> Unit
        }
    }

    fun back() {
        val previous = if (backStack.isNotEmpty()) backStack.removeLast() else Screen.Library
        setState { it.copy(screen = previous) }
        when (previous) {
            is Screen.CardDetail -> loadCard(previous.cardId)
            is Screen.Chat -> loadChat(previous.chatId)
            is Screen.CardEditor -> loadEditor(previous.cardId)
            else -> Unit
        }
    }

    // ---------------- library ----------------

    fun refreshLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = db.listCards()
            setState { it.copy(library = items) }
        }
    }

    private fun loadCard(cardId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val card = db.getCard(cardId) ?: return@launch
            val entries = db.getEntries(cardId)
            val chats = db.listChats(cardId)
            setState { it.copy(detail = CardDetailData(card, entries, chats)) }
        }
    }

    fun deleteCard(cardId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteCard(cardId)
            backStack.clear()
            setState { it.copy(screen = Screen.Library, detail = null, chat = null) }
            refreshLibrary()
            toast("已删除角色卡")
        }
    }

    // ---------------- card editor ----------------

    private fun loadEditor(cardId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val card = if (cardId > 0) db.getCard(cardId) else CharacterCard(
                name = "",
                createdAt = System.currentTimeMillis()
            )
            val entries = if (cardId > 0) db.getEntries(cardId) else emptyList()
            setState { it.copy(editorCard = card, editorEntries = entries) }
        }
    }

    fun openNewCard() {
        navigate(Screen.CardEditor(0))
    }

    fun openCardEditor(cardId: Long) {
        navigate(Screen.CardEditor(cardId))
    }

    /** 新建或保存角色卡（包含世界书条目）。 */
    fun saveCard(card: CharacterCard, entries: List<WorldEntry> = emptyList()) {
        viewModelScope.launch(Dispatchers.IO) {
            if (card.id == 0L) {
                val id = db.insertCard(card.copy(id = 0), entries)
                refreshLibrary()
                setState { it.copy(screen = Screen.CardDetail(id), editorCard = null, editorEntries = emptyList()) }
                loadCard(id)
                toast("角色卡已创建")
            } else {
                db.updateCard(card)
                db.replaceEntries(card.id, entries)
                refreshLibrary()
                setState { it.copy(screen = Screen.CardDetail(card.id), editorCard = null, editorEntries = emptyList()) }
                loadCard(card.id)
                toast("角色卡已保存")
            }
        }
    }

    // ---------------- import / export ----------------

    fun importFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = readBytes(uri) ?: throw IOException("无法读取文件")
                val text = String(bytes, Charsets.UTF_8).trimStart()
                if (text.startsWith("{") && SharePack.isSharePack(text)) {
                    val share = SharePack.import(db, text)
                    refreshLibrary()
                    toast(
                        "已导入《${share.cardName}》：${share.chatCount} 个聊天 / " +
                            "${share.messageCount} 条消息"
                    )
                    setState { it.copy(screen = Screen.CardDetail(share.cardId)) }
                    loadCard(share.cardId)
                    return@launch
                }
                try {
                    val result = CardImporter.importBytes(bytes)
                    val id = db.insertCard(result.card, result.entries)
                    refreshLibrary()
                    toast("已导入《${result.card.name}》")
                    setState { it.copy(screen = Screen.CardDetail(id)) }
                    loadCard(id)
                } catch (cardError: Exception) {
                    // 分享进来的可能只是「角色卡介绍截图」，不是角色卡文件：改用视觉模型读图
                    if (SourceImporter.isImageBytes(bytes)) {
                        setState { it.copy(screen = Screen.Workshop) }
                        readCardImageWithApi(uri, openWorkshop = true)
                        toast("这张图不是角色卡文件，正在用视觉模型读图…")
                        return@launch
                    }
                    throw cardError
                }
            } catch (e: Exception) {
                toast("导入失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** 导出「卡 + 聊天记录」分享包；includeChats=false 时只导卡。 */
    fun exportSharePack(cardId: Long, includeChats: Boolean, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = SharePack.build(db, cardId, includeChats)
                writeText(uri, text)
                toast(
                    if (includeChats) "分享包已导出（角色卡+聊天记录+图片）"
                    else "分享包已导出（仅角色卡+图片）"
                )
            } catch (e: Exception) {
                toast("导出失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun exportCard(cardId: Long, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = db.exportCard(cardId) ?: throw IOException("角色卡不存在")
                writeText(uri, text)
                toast("角色卡 JSON 已导出")
            } catch (e: Exception) {
                toast("导出失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** P1-9：导出为内嵌 chara / ccv3 元数据的 PNG 角色卡。 */    fun exportCardPng(cardId: Long, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val card = db.getCard(cardId) ?: throw IOException("角色卡不存在")
                val entries = db.getEntries(cardId)
                val jsonV2 = JsonCodec.buildCharaCard(card, entries)
                val jsonV3 = JsonCodec.buildCharaCardV3(card, entries)
                val png = PngCardWriter.buildCardPng(card.avatar, jsonV2, jsonV3)
                writeBytes(uri, png)
                toast("PNG 角色卡已导出")
            } catch (e: Exception) {
                toast("导出失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** 把单条对话导出为 Markdown（含多版本的当前版本内容）。 */
    fun exportChatMarkdown(chatId: Long, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chat = db.getChat(chatId) ?: throw IOException("会话不存在")
                val card = db.getCard(chat.cardId)
                val messages = db.getMessages(chatId)
                val timeFormat = java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm", java.util.Locale.getDefault()
                )
                val sb = StringBuilder()
                sb.append("# ").append(chat.title).append("\n\n")
                sb.append("- 角色卡：").append(card?.name ?: "未知").append("\n")
                sb.append("- 开始时间：")
                    .append(timeFormat.format(java.util.Date(chat.createdAt))).append("\n")
                sb.append("- 消息数：").append(messages.size).append("\n\n---\n\n")
                messages.forEach { message ->
                    sb.append(if (message.role == "user") "**你**" else "**${card?.name ?: "角色"}**")
                        .append("：\n\n")
                        .append(message.displayContent)
                        .append("\n\n")
                }
                writeText(uri, sb.toString())
                toast("对话已导出为 Markdown")
            } catch (e: Exception) {
                toast("导出失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** 导出某张卡的世界书为 JSON 数组。 */
    fun exportWorldbook(cardId: Long, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = db.getEntries(cardId)
                val arr = JSONArray()
                entries.forEach { arr.put(JsonCodec.entryToJson(it)) }
                writeText(uri, arr.toString(2))
                toast("世界书已导出（${entries.size} 条）")
            } catch (e: Exception) {
                toast("导出失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** 导入世界书 JSON（数组，或含 entries 的对象），追加到当前卡。 */
    fun importWorldbook(cardId: Long, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = readBytes(uri)?.toString(Charsets.UTF_8)
                    ?: throw IOException("无法读取文件")
                val trimmed = text.trim()
                val arr = if (trimmed.startsWith("[")) {
                    JSONArray(trimmed)
                } else {
                    val obj = JSONObject(trimmed)
                    obj.optJSONArray("entries")
                        ?: obj.optJSONArray("world_entries")
                        ?: obj.optJSONObject("character_book")?.optJSONArray("entries")
                        ?: throw IllegalArgumentException("没有找到世界书条目")
                }
                val entries = JsonCodec.entriesFromJson(arr)
                if (entries.isEmpty()) throw IllegalArgumentException("没有找到世界书条目")
                db.insertEntries(cardId, entries)
                loadCard(cardId)
                toast("已导入 ${entries.size} 条世界书")
            } catch (e: Exception) {
                toast("导入失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                writeText(uri, db.dumpJson())
                toast("备份已导出")
            } catch (e: Exception) {
                toast("导出失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = readBytes(uri)?.toString(Charsets.UTF_8) ?: throw IOException("无法读取文件")
                if (SharePack.isSharePack(text)) {
                    val share = SharePack.import(db, text)
                    refreshLibrary()
                    toast(
                        "已导入分享包《${share.cardName}》：${share.chatCount} 个聊天 / " +
                            "${share.messageCount} 条消息"
                    )
                    setState { it.copy(screen = Screen.CardDetail(share.cardId)) }
                    loadCard(share.cardId)
                    return@launch
                }
                val count = db.restoreJson(text)
                refreshLibrary()
                toast("已恢复 $count 张角色卡")
            } catch (e: Exception) {
                toast("恢复失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun readBytes(uri: Uri): ByteArray? =
        getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }

    private fun writeText(uri: Uri, text: String) {
        getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
            it.write(text.toByteArray(Charsets.UTF_8))
        } ?: throw IOException("无法写入文件")
    }

    private fun writeBytes(uri: Uri, bytes: ByteArray) {
        getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
            it.write(bytes)
        } ?: throw IOException("无法写入文件")
    }

    // ---------------- presets ----------------

    fun loadPresets() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = db.listPresets()
            setState { it.copy(presets = list) }
        }
    }

    fun openPresets() {
        navigate(Screen.Presets)
        loadPresets()
    }

    fun savePreset(preset: Preset) {
        viewModelScope.launch(Dispatchers.IO) {
            val isNew = preset.id == 0L
            if (isNew) db.insertPreset(preset) else db.updatePreset(preset)
            val list = db.listPresets()
            setState { it.copy(presets = list) }
            toast(if (isNew) "预设已创建" else "预设已保存")
        }
    }

    fun deletePreset(preset: Preset) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deletePreset(preset.id)
            val list = db.listPresets()
            setState { it.copy(presets = list) }
            toast("预设已删除")
        }
    }

    /** 导出全部预设为 JSON 文件。 */
    fun exportPresets(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                writeText(uri, db.exportPresetsJson())
                toast("预设已导出")
            } catch (e: Exception) {
                toast("导出失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** 从 JSON 文件导入预设（追加）。 */
    fun importPresets(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = readBytes(uri)?.toString(Charsets.UTF_8)
                    ?: throw IOException("无法读取文件")
                val count = db.importPresetsJson(text)
                val list = db.listPresets()
                setState { it.copy(presets = list) }
                toast("已导入 $count 个预设")
            } catch (e: Exception) {
                toast("导入失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** 为当前会话选择预设；presetId = 0 表示跟随全局设置。 */
    fun setChatPreset(chatId: Long, presetId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updateChatPreset(chatId, presetId)
            loadChat(chatId)
            toast(if (presetId == 0L) "已跟随全局设置" else "已切换预设")
        }
    }

    // ---------------- chats ----------------

    fun loadChat(chatId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val chat = db.getChat(chatId) ?: return@launch
            val card = db.getCard(chat.cardId) ?: return@launch
            val groupCards = groupCardsFor(chatId)
            val entries = entriesFor(card, groupCards)
            val messages = db.getMessages(chatId, limit = 200)
            val hasMore = messages.isNotEmpty() &&
                db.getMessages(chatId, limit = 1, beforeId = messages.first().id).isNotEmpty()
            val (settings, presetPost) = resolveSettings(chat.presetId)
            val memory = db.getMemory(chatId)
            val panelTemplate = PanelTemplate.parse(card.statusTemplate)
            val panelStateJson = db.getPanelState(chatId).orEmpty()
            val globalEntries = db.getGlobalEntries()
            val variables = db.getVariables(chatId)
            val built = PromptBuilder.build(
                card, entries, settings, messages, presetPost,
                memory?.summary.orEmpty(), memory?.msgUptoId ?: 0,
                panelInstruction = panelTemplate?.instruction() ?: "",
                panelStateJson = panelStateJson,
                globalEntries = globalEntries,
                variables = variables,
                groupCards = groupCards,
                minReplyLength = settings.minReplyLength
            )
            val presets = db.listPresets()
            setState {
                it.copy(
                    chat = ChatData(
                        chat,
                        card,
                        messages,
                        built.totalTokens,
                        built.activatedCount,
                        built.debug,
                        hasMore,
                        groupCards
                    ),
                    presets = presets,
                    memory = memory,
                    checkpoints = db.listCheckpoints(chatId),
                    panelTemplate = panelTemplate,
                    panelStateJson = panelStateJson,
                    globalEntries = globalEntries,
                    chatVariables = variables,
                    suggestions = emptyList(),
                    stream = StreamState()
                )
            }
        }
    }

    /** 加载更早的一页消息（长对话分页）。 */
    fun loadEarlier(chatId: Long) {
        val data = _ui.value.chat ?: return
        if (data.messages.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val older = db.getMessages(chatId, limit = 200, beforeId = data.messages.first().id)
            if (older.isEmpty()) {
                setState { it.copy(chat = data.copy(hasMore = false)) }
                return@launch
            }
            setState {
                it.copy(chat = data.copy(messages = older + data.messages, hasMore = true))
            }
        }
    }

    fun createChat(cardId: Long, greetingIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val card = db.getCard(cardId) ?: return@launch
            val chats = db.listChats(cardId)
            val greeting = if (greetingIndex <= 0) {
                card.firstMes
            } else {
                card.alternateGreetings.getOrNull(greetingIndex - 1) ?: card.firstMes
            }
            val chatId = db.createChat(
                cardId,
                "对话 ${chats.size + 1}",
                substituteMacros(greeting, card.name),
                _ui.value.settings.provider.model
            )
            if (card.defaultPresetId > 0L) {
                db.updateChatPreset(chatId, card.defaultPresetId)
            }
            val initialVariables = VariableParser.parseInitial(card.variablesText)
            if (initialVariables.isNotEmpty()) {
                db.setVariables(chatId, initialVariables)
            }
            navigate(Screen.Chat(chatId))
        }
    }

    // ---------------- chat list / search ----------------

    fun openChatList() {
        navigate(Screen.ChatList)
        loadChatList("")
    }

    fun loadChatList(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = db.listAllChats()
            val keyword = query.trim()
            val filtered = if (keyword.isBlank()) {
                all
            } else {
                all.filter {
                    it.chat.title.contains(keyword, ignoreCase = true) ||
                        it.cardName.contains(keyword, ignoreCase = true) ||
                        it.chat.lastMessage.contains(keyword, ignoreCase = true)
                }
            }
            val hits = if (keyword.isBlank()) emptyList() else db.searchMessages(keyword)
            setState { it.copy(chatList = filtered, messageHits = hits) }
        }
    }

    fun openChat(chatId: Long) {
        navigate(Screen.Chat(chatId))
    }

    fun openGlobalWorldbook() {
        navigate(Screen.GlobalWorldbook)
        loadGlobalEntries()
    }

    // ---------------- AI 改写 / 冲突检查 / 世界书收藏库 ----------------

    /** 用 API 改写角色卡的某一个字段；返回改写后的纯文本。 */
    fun rewriteCardField(
        card: CharacterCard,
        entries: List<WorldEntry>,
        fieldKey: String,
        fieldLabel: String,
        requirement: String,
        onResult: (String) -> Unit
    ) {
        if (_ui.value.aiBusy) return
        setState { it.copy(aiBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = toolSettings()
                val raw = ApiClient.complete(
                    settings.copy(temperature = 0.85f, topP = 0.95f),
                    listOf(
                        PromptBuilder.ApiMessage(
                            "system",
                            "你是角色卡编辑，只输出改写后的字段内容，不要解释。"
                        ),
                        PromptBuilder.ApiMessage(
                            "user",
                            CardPromptTemplates.rewriteCardFieldPrompt(
                                card, entries, fieldKey, fieldLabel, requirement,
                                _ui.value.workshopSourceExcerpt,
                                _ui.value.workshopSourceSummary,
                                _ui.value.workshopSourceRequirement,
                                _ui.value.workshopSourceDigest,
                                _ui.value.workshopSourceFocus
                            )
                        )
                    ),
                    maxTokens = 2000
                ).orEmpty()
                val text = raw.removePrefix("```").removeSuffix("```").trim()
                if (text.isBlank()) toast(apiErrorOr("改写失败：接口没有返回内容"))
                else withContext(Dispatchers.Main) { onResult(text) }
            } catch (e: Exception) {
                toast("改写失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                setState { it.copy(aiBusy = false) }
            }
        }
    }

    /** 检查字段改写后是否与其他设定冲突。 */
    fun checkCardConflicts(
        card: CharacterCard,
        entries: List<WorldEntry>,
        changedFieldKey: String,
        changedValue: String,
        onResult: (List<FieldConflict>) -> Unit
    ) {
        setState { it.copy(aiBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = toolSettings()
                val raw = ApiClient.complete(
                    settings.copy(temperature = 0.2f, topP = 1.0f),
                    listOf(
                        PromptBuilder.ApiMessage(
                            "system",
                            "你是角色卡一致性检查器，只输出 JSON，不要解释。"
                        ),
                        PromptBuilder.ApiMessage(
                            "user",
                            CardPromptTemplates.conflictCheckPrompt(card, entries, changedFieldKey, changedValue)
                        )
                    ),
                    maxTokens = 1000
                ).orEmpty()
                val json = extractJsonObject(raw)
                val list = mutableListOf<FieldConflict>()
                if (json != null) {
                    val arr = JSONObject(json).optJSONArray("conflicts")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            list.add(
                                FieldConflict(
                                    field = o.optString("field"),
                                    label = o.optString("label").ifBlank {
                                        CardPromptTemplates.fieldLabelOf(o.optString("field"))
                                    },
                                    issue = o.optString("issue"),
                                    suggestion = o.optString("suggestion")
                                )
                            )
                        }
                    }
                }
                withContext(Dispatchers.Main) { onResult(list) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(emptyList()) }
            } finally {
                setState { it.copy(aiBusy = false) }
            }
        }
    }

    /** 按用户要求用 API 生成一条世界书条目。 */
    fun generateWorldbookEntryAI(
        card: CharacterCard,
        entries: List<WorldEntry>,
        requirement: String,
        onResult: (WorldEntry) -> Unit
    ) {
        if (_ui.value.aiBusy) return
        if (requirement.isBlank()) {
            toast("先写一条要求，例如：写一条关于亲吻/舌吻的描写")
            return
        }
        setState { it.copy(aiBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = toolSettings()
                val raw = ApiClient.complete(
                    settings.copy(temperature = 0.9f, topP = 0.95f),
                    listOf(
                        PromptBuilder.ApiMessage(
                            "system",
                            "你是角色卡世界书作者，只输出一个 JSON 对象，不要解释。"
                        ),
                        PromptBuilder.ApiMessage(
                            "user",
                            CardPromptTemplates.generateWorldbookEntryPrompt(
                                card, entries, requirement,
                                _ui.value.workshopSourceExcerpt,
                                _ui.value.workshopSourceSummary,
                                _ui.value.workshopSourceRequirement,
                                _ui.value.workshopSourceDigest,
                                _ui.value.workshopSourceFocus
                            )
                        )
                    ),
                    maxTokens = 1400
                ).orEmpty()
                val json = extractJsonObject(raw)
                    ?: throw IllegalArgumentException(ApiClient.lastError ?: "模型没有返回可解析的 JSON")
                val entry = JsonCodec.entryFromJson(JSONObject(json), 0)
                if (entry.content.isBlank()) throw IllegalArgumentException("条目内容为空")
                withContext(Dispatchers.Main) { onResult(entry) }
            } catch (e: Exception) {
                toast("生成失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                setState { it.copy(aiBusy = false) }
            }
        }
    }

    /** 把收藏的世界书条目适配到目标卡（保留用户点名要保留的描写）。 */
    fun adaptWorldbookEntriesAI(
        card: CharacterCard,
        sourceEntries: List<WorldEntry>,
        requirement: String,
        onResult: (List<WorldEntry>) -> Unit
    ) {
        if (_ui.value.aiBusy) return
        if (sourceEntries.isEmpty()) {
            toast("这个分组没有条目")
            return
        }
        setState { it.copy(aiBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = toolSettings()
                val raw = ApiClient.complete(
                    settings.copy(temperature = 0.7f, topP = 0.95f),
                    listOf(
                        PromptBuilder.ApiMessage(
                            "system",
                            "你是世界书改编作者，只输出 JSON 数组，不要解释。"
                        ),
                        PromptBuilder.ApiMessage(
                            "user",
                            CardPromptTemplates.adaptWorldbookEntriesPrompt(card, sourceEntries, requirement)
                        )
                    ),
                    maxTokens = 8000
                ).orEmpty()
                val start = raw.indexOf('[')
                val end = raw.lastIndexOf(']')
                if (start < 0 || end <= start) throw IllegalArgumentException("模型没有返回 JSON 数组")
                val arr = org.json.JSONArray(raw.substring(start, end + 1))
                val adapted = JsonCodec.entriesFromJson(arr)
                if (adapted.isEmpty()) throw IllegalArgumentException("适配结果为空")
                withContext(Dispatchers.Main) { onResult(adapted) }
            } catch (e: Exception) {
                toast("适配失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                setState { it.copy(aiBusy = false) }
            }
        }
    }

    // ---- 世界书收藏库 ----

    fun openWorldbookLibrary(cardId: Long) {
        setState { it.copy(screen = Screen.WorldbookLibrary(cardId), worldbookLibraryCardId = cardId) }
        loadWorldbookCollections()
    }

    fun loadWorldbookCollections() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = db.listWorldbookCollections()
            setState { it.copy(worldbookCollections = list) }
        }
    }

    fun loadWorldbookLibraryEntries(collectionId: Long, onResult: (List<WorldEntry>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = db.listWorldbookLibraryEntries(collectionId)
            withContext(Dispatchers.Main) { onResult(list) }
        }
    }

    fun createWorldbookCollection(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.createWorldbookCollection(name)
            loadWorldbookCollections()
            toast("已新建分组")
        }
    }

    fun deleteWorldbookCollection(collection: WorldbookCollection) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteWorldbookCollection(collection.id)
            loadWorldbookCollections()
            toast("已删除分组「${collection.name}」")
        }
    }

    fun saveWorldEntryToLibrary(entry: WorldEntry, collectionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.saveWorldEntryToLibrary(entry, collectionId)
            loadWorldbookCollections()
            toast("已收藏到世界书库")
        }
    }

    /** 收藏条目并顺手新建分组（分组名重名则复用已有分组）。 */
    fun saveWorldEntryToNewCollection(entry: WorldEntry, collectionName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val name = collectionName.trim().ifBlank { "未命名分组" }
            val existing = db.listWorldbookCollections().firstOrNull { it.name == name }
            val collectionId = existing?.id ?: db.createWorldbookCollection(name)
            db.saveWorldEntryToLibrary(entry, collectionId)
            loadWorldbookCollections()
            toast("已收藏到「$name」")
        }
    }

    /** 从收藏库挑选任意条目导入当前卡。 */
    fun importWorldbookEntriesToCard(cardId: Long, entries: List<WorldEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            db.insertEntries(cardId, entries)
            loadCard(cardId)
            toast("已导入 ${entries.size} 条世界书")
        }
    }

    fun deleteWorldbookLibraryEntry(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteWorldbookLibraryEntry(id)
            loadWorldbookCollections()
            toast("已删除收藏条目")
        }
    }

    fun importWorldbookCollectionToCard(cardId: Long, collection: WorldbookCollection) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = db.importWorldbookCollectionToCard(cardId, collection.id)
            loadCard(cardId)
            toast(if (count > 0) "已导入 $count 条世界书" else "该分组没有条目")
        }
    }

    /** 收藏分组 → AI 适配到当前卡并导入。 */
    fun adaptWorldbookCollectionToCard(
        cardId: Long,
        collection: WorldbookCollection,
        requirement: String
    ) {
        val card = db.getCard(cardId) ?: return
        val source = db.listWorldbookLibraryEntries(collection.id)
        adaptWorldbookEntriesAI(card, source, requirement) { adapted ->
            viewModelScope.launch(Dispatchers.IO) {
                db.insertEntries(cardId, adapted)
                loadCard(cardId)
                setState { it.copy(screen = Screen.CardDetail(cardId)) }
                toast("已适配并导入 ${adapted.size} 条世界书")
            }
        }
    }

    fun duplicateCard(cardId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val newId = db.duplicateCard(cardId)
            if (newId == null) {
                toast("复制失败：角色卡不存在")
                return@launch
            }
            refreshLibrary()
            toast("已复制角色卡")
        }
    }

    fun renameChat(chatId: Long, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.renameChat(chatId, title)
            _ui.value.detail?.card?.id?.let { loadCard(it) }
            toast("已重命名")
        }
    }

    fun deleteChat(chatId: Long, cardId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteChat(chatId)
            loadCard(cardId)
            toast("已删除对话")
        }
    }

    fun saveWorldEntry(entry: WorldEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updateEntry(entry)
            loadCard(entry.cardId)
            toast("世界书条目已保存")
        }
    }

    fun toggleWorldEntry(entry: WorldEntry, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updateEntry(entry.copy(enabled = enabled))
            loadCard(entry.cardId)
        }
    }

    // ---------------- generation ----------------

    fun sendMessage(text: String) {
        val data = _ui.value.chat ?: return
        if (_ui.value.stream.streaming) return
        val content = text.trim()
        if (content.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val chatId = data.chat.id
            db.insertMessage(chatId, "user", content, "")
            db.touchChat(chatId, content, _ui.value.settings.provider.model)

            val chat = db.getChat(chatId) ?: return@launch
            if (chat.title.startsWith("对话 ")) {
                val userCount = db.getMessages(chatId).count { it.role == "user" }
                if (userCount == 1) {
                    db.renameChat(chatId, content.replace("\n", " ").take(24))
                }
            }
            val card = db.getCard(chat.cardId) ?: return@launch
            val groupCards = groupCardsFor(chatId)
            val entries = entriesFor(card, groupCards)
            val messages = db.getMessages(chatId)
            val (settings, presetPost) = resolveSettings(chat.presetId)
            val memory = db.getMemory(chatId)
            val panelTemplate = PanelTemplate.parse(card.statusTemplate)
            val panelStateJson = db.getPanelState(chatId).orEmpty()
            val variables = db.getVariables(chatId)
            val built = PromptBuilder.build(
                card, entries, settings, messages, presetPost,
                memory?.summary.orEmpty(), memory?.msgUptoId ?: 0,
                panelInstruction = panelTemplate?.instruction() ?: "",
                panelStateJson = panelStateJson,
                globalEntries = db.getGlobalEntries(),
                variables = variables,
                groupCards = groupCards,
                minReplyLength = settings.minReplyLength
            )

            setState {
                it.copy(
                    chat = ChatData(
                        db.getChat(chatId) ?: chat,
                        card,
                        messages,
                        built.totalTokens,
                        built.activatedCount,
                        built.debug,
                        data.hasMore,
                        groupCards
                    ),
                    stream = StreamState(streaming = true, chatId = chatId)
                )
            }
            generate(card, entries, messages, chatId, StreamMode.NEW, 0)
        }
    }

    fun stopGeneration() {
        stopRequested = true
        currentCall?.cancel()
    }

    fun regenerateLast() {
        val last = _ui.value.chat?.messages?.lastOrNull() ?: return
        if (last.role != "assistant") return
        regenerateMessage(last.id)
    }

    /** 重新生成任意一条 AI 回复：旧内容保留为一个版本，新内容作为新版本；对用户消息则截断其后内容再生成。 */
    fun regenerateMessage(messageId: Long) {
        val data = _ui.value.chat ?: return
        if (_ui.value.stream.streaming) return
        viewModelScope.launch(Dispatchers.IO) {
            val all = db.getMessages(data.chat.id)
            val pos = all.indexOfFirst { it.id == messageId }
            if (pos < 0) return@launch
            val target = all[pos]
            val card = db.getCard(data.card.id) ?: data.card
            val entries = db.getEntries(card.id)
            if (target.role != "assistant") {
                // 编辑用户消息后的「重新生成」= 丢弃这条之后的内容
                all.drop(pos + 1).forEach { db.deleteMessage(it.id) }
                val remaining = all.take(pos + 1)
                setState {
                    it.copy(
                        chat = data.copy(messages = remaining),
                        stream = StreamState(streaming = true, chatId = data.chat.id)
                    )
                }
                generate(card, entries, remaining, data.chat.id, StreamMode.NEW, 0)
            } else {
                val before = all.take(pos)
                setState {
                    it.copy(
                        stream = StreamState(
                            streaming = true,
                            chatId = data.chat.id,
                            mode = StreamMode.REGENERATE,
                            targetMessageId = target.id
                        )
                    )
                }
                generate(card, entries, before, data.chat.id, StreamMode.REGENERATE, target.id)
            }
        }
    }

    /** 继续写：从这条 AI 回复的结尾接着生成，结果追加到同一条消息。 */
    fun continueMessage(messageId: Long) {
        val data = _ui.value.chat ?: return
        if (_ui.value.stream.streaming) return
        viewModelScope.launch(Dispatchers.IO) {
            val all = db.getMessages(data.chat.id)
            val pos = all.indexOfFirst { it.id == messageId }
            if (pos < 0) return@launch
            val target = all[pos]
            if (target.role != "assistant") return@launch
            val card = db.getCard(data.card.id) ?: data.card
            val entries = db.getEntries(card.id)
            setState {
                it.copy(
                    stream = StreamState(
                        streaming = true,
                        chatId = data.chat.id,
                        mode = StreamMode.CONTINUE,
                        targetMessageId = target.id
                    )
                )
            }
            generate(card, entries, all.take(pos + 1), data.chat.id, StreamMode.CONTINUE, target.id)
        }
    }

    /** 切换某条消息的显示版本（delta = ±1，循环）。 */
    fun switchVariant(messageId: Long, delta: Int) {
        val chatId = _ui.value.chat?.chat?.id ?: return
        if (_ui.value.stream.streaming) return
        viewModelScope.launch(Dispatchers.IO) {
            val msg = db.getMessage(messageId) ?: return@launch
            if (msg.variantCount <= 1) return@launch
            var idx = msg.activeVariant + delta
            if (idx < 0) idx = msg.variantCount - 1
            if (idx >= msg.variantCount) idx = 0
            db.updateMessageVariants(messageId, msg.allVariants, idx)
            loadChat(chatId)
        }
    }

    fun retryLast() {
        val data = _ui.value.chat ?: return
        if (_ui.value.stream.streaming) return
        viewModelScope.launch(Dispatchers.IO) {
            val last = data.messages.lastOrNull()
            if (last != null && last.role == "assistant") {
                db.deleteMessage(last.id)
            }
            val card = db.getCard(data.card.id) ?: data.card
            val entries = db.getEntries(card.id)
            val messages = db.getMessages(data.chat.id)
            setState { it.copy(stream = StreamState(streaming = true, chatId = data.chat.id)) }
            generate(card, entries, messages, data.chat.id, StreamMode.NEW, 0)
        }
    }

    private suspend fun generate(
        card: CharacterCard,
        entries: List<WorldEntry>,
        history: List<Message>,
        chatId: Long,
        mode: StreamMode = StreamMode.NEW,
        targetMessageId: Long = 0
    ) {
        val currentChat = db.getChat(chatId)
        val (settings, presetPost) = resolveSettings(currentChat?.presetId ?: 0L)
        val memory = db.getMemory(chatId)
        val panelTemplate = PanelTemplate.parse((db.getCard(card.id) ?: card).statusTemplate)
        val panelStateJson = db.getPanelState(chatId).orEmpty()
        val variables = db.getVariables(chatId)
        val groupCards = groupCardsFor(chatId)
        val effectiveEntries = entriesFor(card, groupCards)
        val built = PromptBuilder.build(
            card, effectiveEntries, settings, history, presetPost,
            memory?.summary.orEmpty(), memory?.msgUptoId ?: 0,
            panelInstruction = panelTemplate?.instruction() ?: "",
            panelStateJson = panelStateJson,
            globalEntries = db.getGlobalEntries(),
            variables = variables,
            groupCards = groupCards,
            minReplyLength = settings.minReplyLength
        )
        val apiMessages = if (mode == StreamMode.CONTINUE) {
            built.messages + PromptBuilder.ApiMessage("user", CONTINUE_PROMPT)
        } else {
            built.messages
        }
        // 记录「真正发给模型的内容」：继续写时把追加的临时指令也算进去
        val promptDebug = if (mode == StreamMode.CONTINUE) {
            built.debug.copy(messages = apiMessages.map { DebugMessage(it.role, it.content) })
        } else {
            built.debug
        }
        val debugJson = promptDebug.toJson()
        setState {
            it.copy(
                chat = it.chat?.copy(contextTokens = built.totalTokens, worldCount = built.activatedCount),
                stream = StreamState(
                    streaming = true,
                    chatId = chatId,
                    mode = mode,
                    targetMessageId = targetMessageId
                )
            )
        }

        val request = ApiClient.buildChatRequest(
            if (settings.minReplyLength > 0) {
                settings.copy(maxTokens = maxOf(settings.maxTokens, settings.minReplyLength + 512))
            } else {
                settings
            },
            apiMessages,
            stream = true
        )
        val call = ApiClient.http.newCall(request)
        currentCall = call
        stopRequested = false

        val content = StringBuilder()
        val reasoning = StringBuilder()
        var usagePrompt = 0
        var usageCompletion = 0

        setState { it.copy(suggestions = emptyList()) }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val raw = response.body?.string().orEmpty()
                    val info = ApiErrorParser.parse(
                        response.code, raw, settings.provider.model
                    )
                    setState {
                        it.copy(
                            error = info.friendly,
                            modelFixFailed = info.failedModel,
                            modelFixModels = info.supportedModels
                        )
                    }
                    return
                }
                val source = response.body?.source()
                if (source == null) {
                    setState { it.copy(error = "响应为空") }
                    return
                }
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.substring(5).trim()
                    if (payload.isEmpty()) continue
                    if (payload == "[DONE]") break
                    try {
                        val obj = JSONObject(payload)
                        val usage = obj.optJSONObject("usage")
                        if (usage != null) {
                            usagePrompt = usage.optInt("prompt_tokens", usagePrompt)
                            usageCompletion = usage.optInt("completion_tokens", usageCompletion)
                            setState {
                                it.copy(
                                    stream = it.stream.copy(
                                        usagePrompt = usagePrompt,
                                        usageCompletion = usageCompletion
                                    )
                                )
                            }
                        }
                        val delta = obj.optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("delta")
                        val r = delta?.optTextOrEmpty("reasoning_content").orEmpty()
                        val c = delta?.optTextOrEmpty("content").orEmpty()
                        if (r.isNotEmpty()) {
                            reasoning.append(r)
                            setState {
                                it.copy(
                                    stream = it.stream.copy(
                                        streaming = true,
                                        chatId = chatId,
                                        content = content.toString(),
                                        reasoning = reasoning.toString()
                                    )
                                )
                            }
                        }
                        if (c.isNotEmpty()) {
                            content.append(c)
                            setState {
                                it.copy(
                                    stream = it.stream.copy(
                                        streaming = true,
                                        chatId = chatId,
                                        content = content.toString(),
                                        reasoning = reasoning.toString()
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略非 JSON 的心跳行
                    }
                }
            }
        } catch (e: Exception) {
            if (!stopRequested) {
                setState { it.copy(error = "网络错误：${e.message ?: e.javaClass.simpleName}") }
            }
        } finally {
            currentCall = null
            val rawContent = stripNullNoise(content.toString())
            val finalReasoning = stripNullNoise(reasoning.toString())
            // P1-6：抽出模型输出的 <panel> 块，正文与状态分开存
            val panelParsed = if (panelTemplate != null) {
                PanelOutputParser.extract(rawContent)
            } else {
                rawContent to null
            }
            panelParsed.second?.takeIf { it.isNotBlank() }?.let { db.savePanelState(chatId, it) }
            // E2：抽 <setvar> 更新变量；再按卡片规则做输出后处理
            val variableParsed = VariableParser.extract(panelParsed.first)
            if (variableParsed.second.isNotEmpty()) {
                db.updateVariables(chatId, variableParsed.second)
                if (_ui.value.chat?.chat?.id == chatId) {
                    setState { it.copy(chatVariables = db.getVariables(chatId)) }
                }
            }
            val rulesCard = db.getCard(card.id) ?: card
            val finalContent = applyOutputRegex(variableParsed.first, rulesCard.regexRules)
            val hasOutput = rawContent.isNotBlank() || finalReasoning.isNotBlank()
            when (mode) {
                StreamMode.NEW -> {
                    if (hasOutput) {
                        val newId = db.insertMessage(
                            chatId,
                            "assistant",
                            finalContent,
                            finalReasoning,
                            usagePrompt,
                            usageCompletion
                        )
                        db.updateMessageDebug(newId, debugJson)
                        db.touchChat(chatId, finalContent, _ui.value.settings.provider.model)
                    }
                }
                StreamMode.REGENERATE -> {
                    val target = if (targetMessageId > 0) db.getMessage(targetMessageId) else null
                    if (target != null && hasOutput) {
                        val variants = target.allVariants +
                            MessageVariant(finalContent, finalReasoning, System.currentTimeMillis())
                        db.updateMessageVariants(target.id, variants, variants.lastIndex)
                        db.updateMessageDebug(target.id, debugJson)
                        if (usagePrompt > 0 || usageCompletion > 0) {
                            db.updateMessageUsage(target.id, usagePrompt, usageCompletion)
                        }
                        db.touchChat(chatId, finalContent, _ui.value.settings.provider.model)
                    }
                }
                StreamMode.CONTINUE -> {
                    val target = if (targetMessageId > 0) db.getMessage(targetMessageId) else null
                    if (target != null && finalContent.isNotBlank()) {
                        val variants = target.allVariants.toMutableList()
                        val idx = target.activeVariant.coerceIn(0, variants.size - 1)
                        val old = variants[idx]
                        val mergedContent =
                            if (old.content.isBlank()) finalContent else old.content.trimEnd() + finalContent
                        val mergedReasoning = when {
                            finalReasoning.isBlank() -> old.reasoning
                            old.reasoning.isBlank() -> finalReasoning
                            else -> old.reasoning + "\n" + finalReasoning
                        }
                        variants[idx] = old.copy(content = mergedContent, reasoning = mergedReasoning)
                        db.updateMessageVariants(target.id, variants, idx)
                        db.updateMessageDebug(target.id, debugJson)
                        if (usagePrompt > 0 || usageCompletion > 0) {
                            db.updateMessageUsage(target.id, usagePrompt, usageCompletion)
                        }
                        db.touchChat(chatId, mergedContent, _ui.value.settings.provider.model)
                    }
                }
            }
            val messages = db.getMessages(chatId)
            val chat = db.getChat(chatId)
            val latestCard = chat?.let { db.getCard(it.cardId) } ?: card
            val latestGroupCards = groupCardsFor(chatId)
            val latestEntries = entriesFor(latestCard, latestGroupCards)
            val latestPanelTemplate = PanelTemplate.parse(latestCard.statusTemplate)
            val latestPanelState = db.getPanelState(chatId).orEmpty()
            val rebuilt = PromptBuilder.build(
                latestCard, latestEntries, settings, messages, presetPost,
                db.getMemory(chatId)?.summary.orEmpty(),
                db.getMemory(chatId)?.msgUptoId ?: 0,
                panelInstruction = latestPanelTemplate?.instruction() ?: "",
                panelStateJson = latestPanelState,
                globalEntries = db.getGlobalEntries(),
                variables = db.getVariables(chatId),
                groupCards = latestGroupCards,
                minReplyLength = settings.minReplyLength
            )
            setState {
                it.copy(
                    chat = if (chat != null) {
                        ChatData(
                            chat,
                            latestCard,
                            messages,
                            rebuilt.totalTokens,
                            rebuilt.activatedCount,
                            rebuilt.debug,
                            it.chat?.hasMore ?: false,
                            latestGroupCards
                        )
                    } else {
                        it.chat
                    },
                    memory = db.getMemory(chatId),
                    panelTemplate = latestPanelTemplate,
                    panelStateJson = latestPanelState,
                    chatVariables = db.getVariables(chatId),
                    stream = StreamState()
                )
            }
            if (usagePrompt > 0 || usageCompletion > 0) {
                db.insertUsage(usagePrompt, usageCompletion, settings.provider.model)
                checkDailyBudget(settings)
                refreshUsageStats()
            }
            db.pruneMessageDebug(chatId)
            if (settings.autoSummaryEnabled) {
                viewModelScope.launch(Dispatchers.IO) {
                    summarizeMemory(chatId, settings, force = false)
                }
            }
            if (settings.dynamicSuggestions) {
                viewModelScope.launch(Dispatchers.IO) {
                    generateSuggestions(chatId, settings, card)
                }
            }
        }
    }

    /**
     * 动态建议回复：按最近剧情让模型生成 3 条「用户可发送」的回复。
     * 失败时静默（不影响主流程），下次回复会再试。
     */
    private suspend fun generateSuggestions(chatId: Long, settings: AppSettings, card: CharacterCard) {
        if (settings.provider.apiKey.isBlank()) return
        val messages = db.getMessages(chatId).takeLast(8)
        if (messages.isEmpty()) return
        val transcript = messages.joinToString("\n") { m ->
            (if (m.role == "user") "用户" else "角色") + "：" + m.content.take(600)
        }.take(6000)
        val system = buildString {
            append("你在帮一个角色扮演应用生成「用户可选回复」。根据最近剧情，站在用户（玩家）视角给出 3 条可以直接发送的回复。")
            append("要求：每条 15~50 个中文字符；符合用户当前处境与说话风格；能推进剧情或制造张力；")
            append("不要编号、不要引号、不要任何解释；每行一条，只输出 3 行。")
            if (card.name.isNotBlank()) append("当前角色：").append(card.name).append("。")
        }
        val userPrompt = "最近剧情：\n$transcript\n\n请输出 3 条用户可选回复。"
        val result = ApiClient.complete(
            settings.copy(temperature = 0.9f, topP = 0.95f),
            listOf(
                PromptBuilder.ApiMessage("system", system),
                PromptBuilder.ApiMessage("user", userPrompt)
            ),
            maxTokens = 240
        )
        if (result.isNullOrBlank()) return
        val lines = result.lines()
            .map { line ->
                line.trim()
                    .replace(Regex("^\\d+[.、)）]\\s*"), "")
                    .trimStart('-', '*', '•', '·')
                    .trim()
            }
            .filter { it.length in 4..120 }
            .take(3)
        if (lines.isEmpty()) return
        if (_ui.value.chat?.chat?.id == chatId) {
            setState { it.copy(suggestions = lines) }
        }
    }

    /**
     * 编辑消息：
     * - 编辑 AI 回复：旧内容保留，编辑后的内容作为新版本；
     * - 编辑用户消息：可选「保存并重新生成」——丢弃其后的消息后重新生成。
     */
    fun editMessage(id: Long, text: String, regenerateAfter: Boolean = false) {
        val data = _ui.value.chat ?: return
        if (_ui.value.stream.streaming) return
        viewModelScope.launch(Dispatchers.IO) {
            val target = db.getMessage(id) ?: return@launch
            if (target.role == "assistant") {
                val variants = target.allVariants +
                    MessageVariant(text, target.reasoning, System.currentTimeMillis())
                db.updateMessageVariants(id, variants, variants.lastIndex)
                loadChat(data.chat.id)
                return@launch
            }
            val variants = target.allVariants.toMutableList()
            val idx = target.activeVariant.coerceIn(0, variants.size - 1)
            variants[idx] = variants[idx].copy(content = text)
            db.updateMessageVariants(id, variants, idx)
            val all = db.getMessages(data.chat.id)
            val pos = all.indexOfFirst { it.id == id }
            if (!regenerateAfter || pos < 0) {
                loadChat(data.chat.id)
                return@launch
            }
            all.drop(pos + 1).forEach { db.deleteMessage(it.id) }
            val remaining = all.take(pos + 1)
            val card = db.getCard(data.card.id) ?: data.card
            val entries = db.getEntries(card.id)
            setState {
                it.copy(
                    chat = data.copy(messages = remaining),
                    stream = StreamState(streaming = true, chatId = data.chat.id)
                )
            }
            generate(card, entries, remaining, data.chat.id, StreamMode.NEW, 0)
        }
    }

    fun deleteMessage(id: Long) {
        val chatId = _ui.value.chat?.chat?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteMessage(id)
            loadChat(chatId)
        }
    }

    // ---------------- prompt debug ----------------

    /** 预览「下一次请求」会发给模型的完整内容 + 世界书命中情况。 */
    fun previewPrompt() {
        val data = _ui.value.chat ?: return
        if (_ui.value.stream.streaming) return
        viewModelScope.launch(Dispatchers.IO) {
            val card = db.getCard(data.card.id) ?: data.card
            val groupCards = groupCardsFor(data.chat.id)
            val entries = entriesFor(card, groupCards)
            val messages = db.getMessages(data.chat.id)
            val (settings, presetPost) = resolveSettings(data.chat.presetId)
            val memory = db.getMemory(data.chat.id)
            val panelTemplate = PanelTemplate.parse(card.statusTemplate)
            val built = PromptBuilder.build(
                card, entries, settings, messages, presetPost,
                memory?.summary.orEmpty(), memory?.msgUptoId ?: 0,
                panelInstruction = panelTemplate?.instruction() ?: "",
                panelStateJson = db.getPanelState(data.chat.id).orEmpty(),
                globalEntries = db.getGlobalEntries(),
                variables = db.getVariables(data.chat.id),
                groupCards = groupCards,
                minReplyLength = settings.minReplyLength
            )
            setState {
                it.copy(
                    promptPreview = PromptPreviewData(
                        title = "下一次请求的 Prompt",
                        debug = built.debug,
                        live = true
                    )
                )
            }
        }
    }

    /** 查看某条历史消息生成时登记的 Prompt 快照。 */
    fun openMessageDebug(messageId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = db.getMessage(messageId) ?: return@launch
            if (msg.debug.isBlank()) {
                toast("这条消息没有 Prompt 记录（升级前的旧消息）")
                return@launch
            }
            setState {
                it.copy(
                    promptPreview = PromptPreviewData(
                        title = "消息 #${msg.id} 的 Prompt 快照",
                        debug = PromptDebug.fromJson(msg.debug),
                        live = false
                    )
                )
            }
        }
    }

    fun closePromptPreview() = setState { it.copy(promptPreview = null) }

    // ---------------- branch / checkpoints / recap ----------------

    /** 从某条消息创建分支会话（复制该消息及之前的历史）。 */
    fun branchFromMessage(messageId: Long) {
        val data = _ui.value.chat ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val newChatId = db.branchChat(data.chat.id, messageId)
            if (newChatId == null) {
                toast("分支失败：消息不存在")
                return@launch
            }
            navigate(Screen.Chat(newChatId))
            toast("已创建分支会话")
        }
    }

    fun saveCheckpointAt(messageId: Long, name: String) {
        val chatId = _ui.value.chat?.chat?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            db.saveCheckpoint(chatId, messageId, name)
            setState { it.copy(checkpoints = db.listCheckpoints(chatId)) }
            toast("检查点已保存")
        }
    }

    fun deleteCheckpoint(id: Long) {
        val chatId = _ui.value.chat?.chat?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteCheckpoint(id)
            setState { it.copy(checkpoints = db.listCheckpoints(chatId)) }
            toast("检查点已删除")
        }
    }

    fun restoreCheckpoint(id: Long) {
        val chatId = _ui.value.chat?.chat?.id ?: return
        if (_ui.value.stream.streaming) return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = db.restoreCheckpoint(id)
            setState { it.copy(checkpoints = db.listCheckpoints(chatId)) }
            if (ok) {
                loadChat(chatId)
                toast("已回到检查点")
            } else {
                toast("检查点不存在")
            }
        }
    }

    /** 带前情开新档：生成/复用前情提要 → 新建会话 → 摘要挂到新档。 */
    fun startNewChatWithRecap() {
        val data = _ui.value.chat ?: return
        if (_ui.value.stream.streaming) return
        viewModelScope.launch(Dispatchers.IO) {
            val source = db.getChat(data.chat.id) ?: return@launch
            val card = db.getCard(source.cardId) ?: return@launch
            val settings = resolveSettings(source.presetId).first
            val summarized = summarizeMemory(source.id, settings, force = true)
            val memory = db.getMemory(source.id)
            val summary = memory?.summary.orEmpty()
            if (!summarized && summary.isBlank()) {
                toast("前情生成失败：没有可总结的内容或请求失败")
                return@launch
            }
            val chatId = db.createChat(
                card.id,
                "前情续档 · ${card.name}",
                card.firstMes,
                settings.provider.model
            )
            db.updateChatPreset(chatId, source.presetId)
            val greetingId = db.getMessages(chatId).firstOrNull()?.id ?: 0L
            if (summary.isNotBlank()) db.saveMemory(chatId, summary, greetingId)
            navigate(Screen.Chat(chatId))
            toast("已用前情提要开新档")
        }
    }

    // ---------------- panel ----------------

    /** 保存/清空卡片互动面板模板，并在当前会话里立即生效。 */
    fun savePanelTemplate(cardId: Long, templateJson: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updateCardStatusTemplate(cardId, templateJson)
            loadCard(cardId)
            _ui.value.chat?.let { chatData ->
                if (chatData.card.id == cardId) loadChat(chatData.chat.id)
            }
            toast(if (templateJson.isBlank()) "已清空互动面板" else "互动面板已保存")
        }
    }

    /** 清空当前会话的面板状态（下一轮会按模板重新填）。 */
    fun clearPanelState(chatId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.clearPanelState(chatId)
            setState { it.copy(panelStateJson = "") }
            toast("面板状态已清空")
        }
    }

    /** 手动编辑面板状态 JSON（模型输出不对时救急）。 */
    fun editPanelState(chatId: Long, json: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (json.isBlank()) {
                db.clearPanelState(chatId)
                setState { it.copy(panelStateJson = "") }
            } else {
                db.savePanelState(chatId, json)
                setState { it.copy(panelStateJson = json) }
            }
            toast("面板状态已保存")
        }
    }

    /** 点击剧情选项时，把它当作玩家回复发出去。 */
    fun sendPanelOption(text: String) {
        sendMessage(text)
    }

    // ---------------- variables ----------------

    /** 保存会话变量（聊天页「变量」对话框）。 */
    fun saveVariables(chatId: Long, variables: Map<String, String>) {
        viewModelScope.launch(Dispatchers.IO) {
            db.setVariables(chatId, variables)
            setState { it.copy(chatVariables = variables) }
            toast("变量已保存（${variables.size} 个）")
        }
    }

    /** 按卡片「输出后处理」规则做正则替换（每行 pattern => replacement）。 */
    private fun applyOutputRegex(text: String, rulesText: String): String {
        if (rulesText.isBlank() || text.isBlank()) return text
        var result = text
        rulesText.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach
            val index = trimmed.indexOf("=>")
            if (index <= 0) return@forEach
            val pattern = trimmed.substring(0, index).trim()
            val replacement = trimmed.substring(index + 2).trim()
            if (pattern.isNotEmpty()) {
                result = runCatching { Regex(pattern).replace(result, replacement) }
                    .getOrDefault(result)
            }
        }
        return result
    }

    // ---------------- group chat ----------------

    /** E3：多角色同台：把 2~4 张卡绑到同一个会话，模型分饰所有角色。 */
    fun createGroupChat(cardIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            val cards = cardIds.distinct().mapNotNull { db.getCard(it) }
            if (cards.size < 2) {
                toast("至少选择 2 张角色卡")
                return@launch
            }
            val first = cards.first()
            val names = cards.joinToString("+") { it.name.take(8) }
            val chatId = db.createChat(
                first.id,
                "群聊：$names",
                substituteMacros(first.firstMes, first.name),
                _ui.value.settings.provider.model
            )
            db.setGroupCards(chatId, cards.map { it.id })
            val variables = cards.flatMap {
                VariableParser.parseInitial(it.variablesText).entries
            }.associate { it.key to it.value }
            if (variables.isNotEmpty()) db.setVariables(chatId, variables)
            navigate(Screen.Chat(chatId))
            toast("群聊已创建（${cards.size} 个角色）")
        }
    }

    // ---------------- card workshop ----------------

    fun openWorkshop() = navigate(Screen.Workshop)

    fun notify(message: String) = toast(message)

    // ---- 工坊草稿：存在 ViewModel，生成失败/切页都不丢 ----

    fun updateWorkshopDraft(input: WorkshopInput) = setState { it.copy(workshopDraft = input) }

    private fun currentDraft(): WorkshopInput = _ui.value.workshopDraft

    private fun modifyDraft(block: (WorkshopInput) -> WorkshopInput) {
        setState { it.copy(workshopDraft = block(it.workshopDraft)) }
    }

    /** 梗概变更前压栈（供撤消），同时清空恢复栈。 */
    private fun pushSynopsisHistory(previous: String) {
        setState {
            it.copy(
                workshopUndo = it.workshopUndo + previous,
                workshopRedo = emptyList()
            )
        }
    }

    fun undoWorkshopPolish() {
        val undo = _ui.value.workshopUndo
        if (undo.isEmpty()) return
        val target = undo.last()
        setState {
            it.copy(
                workshopDraft = it.workshopDraft.copy(idea = target),
                workshopUndo = it.workshopUndo.dropLast(1),
                workshopRedo = it.workshopRedo + it.workshopDraft.idea
            )
        }
        toast("已撤回上一步")
    }

    fun redoWorkshopPolish() {
        val redo = _ui.value.workshopRedo
        if (redo.isEmpty()) return
        val target = redo.last()
        setState {
            it.copy(
                workshopDraft = it.workshopDraft.copy(idea = target),
                workshopRedo = it.workshopRedo.dropLast(1),
                workshopUndo = it.workshopUndo + it.workshopDraft.idea
            )
        }
        toast("已恢复下一步")
    }

    // ---- 同人/小说原文导入与分析 ----

    /** 从文件选择器/系统分享接收 TXT/EPUB，解析成工坊原文。 */
    fun importSourceFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = readBytes(uri) ?: throw IOException("无法读取文件")
                if (bytes.size > 80 * 1024 * 1024) throw IOException("文件超过 80MB")
                val name = uri.lastPathSegment ?: "原文.txt"
                val doc = SourceImporter.parse(bytes, name)
                storeSource(doc)
                setState { it.copy(screen = Screen.Workshop) }
                toast("已导入原文：《${doc.title}》${doc.chapters.size} 章 · ${doc.charCount} 字")
            } catch (e: Exception) {
                toast("原文导入失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun storeSource(doc: SourceDocument) {
        sourceDocument = doc
        setState {
            it.copy(
                workshopSourceTitle = doc.title,
                workshopSourceFormat = doc.format,
                workshopSourceChapterCount = doc.chapters.size,
                workshopSourceCharCount = doc.charCount,
                workshopSourceExcerpt = "",
                workshopSourceSummary = "",
                workshopSourceTimePoint = ""
            )
        }
    }

    fun importWorkshopSource(uri: Uri) = importSourceFromUri(uri)

    /** 从 http(s) 直链导入 TXT/EPUB（不支持需要登录/反爬的网页）。 */
    fun importWorkshopSourceFromUrl(url: String) {
        val normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            toast("请输入 http/https 直链")
            return
        }
        if (_ui.value.workshopSourceBusy) return
        setState { it.copy(workshopSourceBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(normalized)
                    .header("User-Agent", "Mozilla/5.0 (Android) TavernImporter")
                    .get()
                    .build()
                val bytes = ApiClient.http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("响应为空")
                    if (body.contentLength() > 80L * 1024 * 1024) throw IOException("文件超过 80MB")
                    val data = body.bytes()
                    if (data.size > 80 * 1024 * 1024) throw IOException("文件超过 80MB")
                    data
                }
                if (bytes.size >= 2 && bytes[0] == '<'.code.toByte()) {
                    throw IllegalArgumentException("这是网页不是 TXT/EPUB 直链；番茄/起点等网站不支持直接抓取")
                }
                val name = normalized.substringBefore('?').substringAfterLast('/')
                    .ifBlank { "原文.txt" }
                val doc = SourceImporter.parse(bytes, name)
                storeSource(doc)
                toast("已从链接导入：《${doc.title}》${doc.chapters.size} 章")
            } catch (e: Exception) {
                toast("链接导入失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                setState { it.copy(workshopSourceBusy = false) }
            }
        }
    }

    fun clearWorkshopSource() {
        sourceDocument = null
        setState {
            it.copy(
                workshopSourceTitle = "",
                workshopSourceFormat = "",
                workshopSourceChapterCount = 0,
                workshopSourceCharCount = 0,
                workshopSourceExcerpt = "",
                workshopSourceSummary = "",
                workshopSourceTimePoint = "",
                workshopSourceRequirement = "",
                workshopSourceDigest = "",
                workshopSourceProgress = ""
            )
        }
        toast("已移除原文")
    }

    fun updateWorkshopSourceRequirement(text: String) {
        setState { it.copy(workshopSourceRequirement = text) }
    }

    fun updateWorkshopSourceFocus(focus: List<String>) {
        setState { it.copy(workshopSourceFocus = focus) }
    }

    // ---- 图片读卡（视觉 API 读图 + API 提取/补全） ----

    fun updateWorkshopImageText(text: String) {
        setState { it.copy(workshopImageText = text) }
    }

    /** 用视觉模型直接读图（图片会发给当前 API 供应商）：转录文字 + 提取设定。 */
    fun readCardImageWithApi(uri: Uri, openWorkshop: Boolean = false) {
        if (_ui.value.workshopImageBusy) return
        setState { it.copy(workshopImageBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 长截图按纵向切片（保持原宽度），再一起发给视觉模型，避免整体缩小后文字发糊
                val tiles = ImageTextExtractor.loadTilesAsJpeg(getApplication(), uri)
                if (tiles.isEmpty()) throw IllegalArgumentException("读不到图片")
                val dataUrls = tiles.map {
                    "data:image/jpeg;base64," +
                        android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)
                }
                val settings = toolSettings()
                // 第一步：只让视觉模型转录文字（不要求 JSON，成功率最高）
                val raw = ApiClient.completeWithImages(
                    settings.copy(temperature = 0.2f, topP = 0.9f),
                    "你是图片文字转录助手，只输出图片里的文字，不要解释。",
                    CardPromptTemplates.cardImageTranscribePrompt(tiles.size),
                    dataUrls,
                    maxTokens = minOf(4_000, 1_500 + 700 * tiles.size)
                ).orEmpty().trim()
                val transcript = raw.removePrefix("```").removeSuffix("```").trim()
                val looksEmpty = transcript.isBlank() ||
                    transcript.length < 10 ||
                    transcript.contains("没有识别到文字")
                if (looksEmpty) {
                    throw IllegalArgumentException(
                        (ApiClient.lastError ?: "模型没有返回文字内容") +
                            "（返回片段：${raw.take(120)}）"
                    )
                }
                setState {
                    it.copy(
                        workshopImageText = transcript,
                        workshopImageBusy = false,
                        screen = if (openWorkshop) Screen.Workshop else it.screen
                    )
                }
                toast("已读图转录 ${transcript.length} 字，正在提取设定…")
                // 第二步：用文本提取流程把转录结果变成设定/梗概（JSON 交给普通文本模型，更稳）
                withContext(Dispatchers.Main) { extractFromCardImageText(autoGenerate = false) }
            } catch (e: Exception) {
                setState {
                    it.copy(
                        workshopImageBusy = false,
                        error = "视觉读图失败：${e.message ?: e.javaClass.simpleName}\n\n" +
                            "常见原因：\n" +
                            "1) 当前「AI 工具模型」看不到图片（DeepSeek 官方对话模型通常不支持图片输入，需要供应商提供视觉/VL 型号）；\n" +
                            "2) 中转站把图片参数忽略/过滤了；\n" +
                            "3) 请求体太大或图片格式不支持。\n\n" +
                            "处理：工坊顶部「切换 API 模型」→「拉取该供应商的模型列表」，选支持图片输入的型号后再试；或换一个多模态中转站。"
                    )
                }
            }
        }
    }

    /** 把截图介绍交给 API：修正 OCR、提取可用设定、润色梗概；autoGenerate 时继续生成完整卡。 */
    fun extractFromCardImageText(autoGenerate: Boolean = false) {
        val intro = _ui.value.workshopImageText.trim()
        if (intro.isBlank()) {
            toast("先选一张角色卡介绍截图，或把介绍文字粘进去")
            return
        }
        if (_ui.value.workshopImageBusy) return
        setState { it.copy(workshopImageBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = toolSettings()
                val draft = currentDraft()
                val raw = ApiClient.complete(
                    settings.copy(temperature = 0.5f, topP = 0.95f),
                    listOf(
                        PromptBuilder.ApiMessage(
                            "system",
                            "你是角色卡策划，只输出一个 JSON 对象，不要 markdown、不要解释。"
                        ),
                        PromptBuilder.ApiMessage(
                            "user",
                            CardPromptTemplates.cardIntroExtractPrompt(
                                draft,
                                intro,
                                _ui.value.workshopSourceRequirement,
                                _ui.value.workshopSourceFocus
                            )
                        )
                    ),
                    maxTokens = 2_600
                ).orEmpty()
                val json = extractJsonObject(raw)
                    ?: throw IllegalArgumentException(ApiClient.lastError ?: "模型没有返回可解析的 JSON")
                val obj = JSONObject(json)
                var updated = applyOptionsJson(draft, json)
                obj.optString("synopsis").takeIf { it.isNotBlank() }?.let {
                    updated = updated.copy(idea = it)
                }
                obj.optString("name").takeIf { it.isNotBlank() }?.let {
                    updated = updated.copy(name = it.take(20))
                }
                obj.optString("extra").takeIf { it.isNotBlank() }?.let { extra ->
                    updated = updated.copy(extra = (draft.extra + "\n" + extra).trim())
                }
                setState { it.copy(workshopDraft = updated, workshopImageBusy = false) }
                toast("已提取设定并润色梗概")
                if (autoGenerate) {
                    withContext(Dispatchers.Main) { generateCardFromWorkshop() }
                }
            } catch (e: Exception) {
                setState {
                    it.copy(
                        workshopImageBusy = false,
                        error = "图片设定提取失败：${e.message ?: e.javaClass.simpleName}\n\n" +
                            "可检查工坊顶部「AI 模型」是否可用（切换 API 模型 → 拉取模型列表）。"
                    )
                }
            }
        }
    }

    /**
     * 分析原文并填梗概/选项。
     * mode = fast：快速压缩（整本均匀采样 + 一次调用，推荐）;
     * mode = full：逐段精读（全本分段多次调用，最全面但也最慢最贵）;
     * mode = excerpt：仅时间节点附近片段。
     */
    fun analyzeWorkshopSource(
        timePoint: String,
        requirement: String,
        mode: String = "fast",
        autoGenerateCard: Boolean = false
    ) {
        val doc = sourceDocument
        if (doc == null) {
            toast("先导入 TXT / EPUB 原文")
            return
        }
        // 选了事件类时间点但没填关键词，直接提示，避免发一个无意义的请求
        if (timePoint.startsWith("事件")) {
            val body = timePoint.substringAfter("：", "")
                .ifBlank { timePoint.substringAfter(":", "").trim() }
            val from = body.substringBefore("到").trim()
            val to = body.substringAfter("到", "").trim()
            if (from.isBlank() || (timePoint.startsWith("事件范围") && to.isBlank())) {
                setState {
                    it.copy(
                        error = "你选了「$timePoint」，但没有填事件关键词。\n\n" +
                            "请在「事件关键词 / 起始事件 / 结束事件」里填一个书中出现过的词（如：婚礼 / 决裂 / 葬礼），再点分析。"
                    )
                }
                return
            }
        }
        if (_ui.value.workshopSourceBusy) return
        setState {
            it.copy(
                workshopSourceBusy = true,
                workshopSourceRequirement = requirement,
                workshopSourceProgress = when (mode) {
                    "full" -> "准备全文分段…"
                    "fast" -> "快速压缩全文…"
                    else -> "分析中…"
                }
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            var fullTextFallbackReason: String? = null
            val fullMode = mode == "full"
            val fastMode = mode == "fast"
            try {
                val settings = toolSettings()
                val timePointExcerpt = buildSourceExcerpt(doc, timePoint, settings.contextLimit)
                var digest = ""
                var fastSample = ""
                // 若用户选了「章节范围 / 事件范围」，快速压缩与逐段精读都只在该范围内进行
                val rangeText = sourceRangeText(doc, timePoint)
                if (fullMode) {
                    val allText = rangeText ?: doc.chapters.joinToString("\n")
                    // 按上下文预算选择每段字符数：宁可多切几段，也别一次撞上限
                    val chunkChars = when {
                        settings.contextLimit >= 500_000 -> 30_000
                        settings.contextLimit >= 128_000 -> 12_000
                        else -> 6_000
                    }
                    val segments = SourceImporter.splitForAnalysis(allText, chunkChars)
                        .filter { it.isNotBlank() }
                        .take(120)
                    if (segments.size > 1) {
                        val parts = mutableListOf<String>()
                        var firstError: String? = null
                        var failedCount = 0
                        segments.forEachIndexed { index, segment ->
                            setState {
                                it.copy(
                                    workshopSourceProgress =
                                        "全文分析：第 ${index + 1}/${segments.size} 段…"
                                )
                            }
                            var raw = ApiClient.complete(
                                settings.copy(temperature = 0.3f, topP = 0.9f),
                                listOf(
                                    PromptBuilder.ApiMessage(
                                        "system",
                                        "你是小说资料整理助手。直接输出要点，不要长思考、不要续写、不要编造。"
                                    ),
                                    PromptBuilder.ApiMessage(
                                        "user",
                                        CardPromptTemplates.fullTextExtractPrompt(
                                            segment, index + 1, segments.size, timePoint, requirement,
                                            _ui.value.workshopSourceFocus
                                        )
                                    )
                                ),
                                maxTokens = 2_000
                            ).orEmpty()
                            if (raw.isBlank()) {
                                if (firstError == null) {
                                    firstError = ApiClient.lastError?.takeIf { it.isNotBlank() }
                                        ?: "接口返回空内容（可能 max_tokens 被思考占用、触发过滤或超出上下文）"
                                }
                                // 重试一次：缩短本段再试，给输出更多 token
                                kotlinx.coroutines.delay(400)
                                raw = ApiClient.complete(
                                    settings.copy(temperature = 0.3f, topP = 0.9f),
                                    listOf(
                                        PromptBuilder.ApiMessage(
                                            "system",
                                            "你是小说资料整理助手。直接输出要点，不要长思考。"
                                        ),
                                        PromptBuilder.ApiMessage(
                                            "user",
                                            CardPromptTemplates.fullTextExtractPrompt(
                                                segment.take(segment.length * 2 / 3),
                                                index + 1, segments.size, timePoint, requirement,
                                                _ui.value.workshopSourceFocus
                                            )
                                        )
                                    ),
                                    maxTokens = 2_800
                                ).orEmpty()
                            }
                            if (raw.isNotBlank()) parts.add(raw.trim()) else failedCount++
                            kotlinx.coroutines.delay(250)
                        }
                        if (parts.isEmpty()) {
                            fullTextFallbackReason = firstError
                                ?: "全文分段分析没有返回内容（可能是模型上下文不足或额度不足）"
                            // 接口若明确说了上下文上限，自动把设置调低，避免以后继续撞墙
                            val limit = ApiErrorParser.parseContextLimit(firstError.orEmpty())
                            if (limit != null && _ui.value.settings.contextLimit > limit) {
                                saveSettings(_ui.value.settings.copy(contextLimit = limit))
                                toast("接口上下文上限 $limit，已自动调整上下文设置")
                            }
                        } else {
                            if (failedCount > 0) {
                                toast("有 $failedCount 段分析失败，全书参考可能不完整")
                            }
                            val merged = parts.joinToString("\n")
                            digest = if (merged.length > 16_000) {
                                setState { it.copy(workshopSourceProgress = "汇总全书参考…") }
                                ApiClient.complete(
                                    settings.copy(temperature = 0.3f, topP = 0.9f),
                                    listOf(
                                        PromptBuilder.ApiMessage(
                                            "system",
                                            "你是资料汇总助手，只输出结构化要点，不要解释。"
                                        ),
                                        PromptBuilder.ApiMessage(
                                            "user",
                                            "把下面的分章资料压缩合并成「全书参考」：" +
                                                "人物及关系 / 外貌性格 / 世界观规则 / 地点势力 / 关键事件 / 称呼。" +
                                                "不超过 2500 字；保留与时间节点「$timePoint」和用户要求相关的细节。\n\n" +
                                                merged.take(140_000)
                                        )
                                    ),
                                    maxTokens = 3200
                                ).orEmpty().ifBlank { merged.take(16_000) }
                            } else {
                                merged
                            }
                        }
                    }
                }
                if (fastMode) {
                    val allText = rangeText ?: doc.chapters.joinToString("\n")
                    // 按上下文预算控制单次输入：宁可少采一点，也别撞上下文上限
                    val fastBudget = minOf(20_000, maxOf(6_000, settings.contextLimit / 6))
                    fastSample = SourceImporter.sampleForCompression(allText, fastBudget)
                    setState { it.copy(workshopSourceProgress = "快速压缩：整理重点资料…") }
                }
                setState { it.copy(workshopSourceProgress = "生成梗概与选项…") }
                val analysisInput = buildString {
                    if (digest.isNotBlank()) {
                        append("【全书参考（来自逐段精读，覆盖全文）】\n")
                        append(digest.take(20_000)).append("\n\n")
                    } else if (fastSample.isNotBlank()) {
                        append("【全书均匀采样（按顺序覆盖开头→结尾，共约 ${fastSample.length} 字）】\n")
                        append(fastSample.take(22_000)).append("\n\n")
                    }
                    append("【时间节点附近原文】\n")
                    append(timePointExcerpt.take(if (fastMode) 6_000 else 12_000))
                }
                val draft = currentDraft()
                val raw = ApiClient.complete(
                    settings.copy(temperature = 0.6f, topP = 0.95f),
                    listOf(
                        PromptBuilder.ApiMessage(
                            "system",
                            "你是小说改编策划，只输出一个 JSON 对象，不要 markdown、不要解释。"
                        ),
                        PromptBuilder.ApiMessage(
                            "user",
                            CardPromptTemplates.sourceAnalysisPrompt(
                                draft, analysisInput, timePoint, requirement,
                                _ui.value.workshopSourceFocus,
                                includeDigest = fastMode
                            )
                        )
                    ),
                    maxTokens = if (fastMode) 3_500 else 2_200
                ).orEmpty()
                var json = extractJsonObject(raw)
                if (json == null) {
                    // 第一次失败：缩短输入 + 降温 + 明确“只要 JSON”再试一次
                    setState { it.copy(workshopSourceProgress = "JSON 解析失败，缩短输入重试…") }
                    val retryRaw = ApiClient.complete(
                        settings.copy(temperature = 0.2f, topP = 0.9f),
                        listOf(
                            PromptBuilder.ApiMessage(
                                "system",
                                "你是小说改编策划。只输出一个 JSON 对象，不要任何解释、不要 markdown。"
                            ),
                            PromptBuilder.ApiMessage(
                                "user",
                                CardPromptTemplates.sourceAnalysisPrompt(
                                    draft, analysisInput.take(10_000), timePoint, requirement,
                                    _ui.value.workshopSourceFocus,
                                    includeDigest = fastMode
                                )
                            )
                        ),
                        maxTokens = 2_500
                    ).orEmpty()
                    json = extractJsonObject(retryRaw)
                    if (json == null) {
                        throw IllegalArgumentException(
                            ApiClient.lastError?.takeIf { it.isNotBlank() }
                                ?: "模型输出不是有效 JSON（返回片段：${retryRaw.take(120)}）"
                        )
                    }
                }
                val obj = JSONObject(json)
                var updated = applyOptionsJson(draft, json)
                obj.optString("synopsis").takeIf { it.isNotBlank() }?.let { updated = updated.copy(idea = it) }
                obj.optString("name").takeIf { it.isNotBlank() }?.let { updated = updated.copy(name = it.take(20)) }
                val summary = obj.optString("summary").takeIf { it.isNotBlank() } ?: timePointExcerpt.take(1500)
                if (fastMode && digest.isBlank()) {
                    digest = obj.optString("digest").takeIf { it.isNotBlank() }
                        ?: summary.ifBlank { fastSample.take(12_000) }
                }
                setState {
                    it.copy(
                        workshopDraft = updated,
                        workshopSourceExcerpt = timePointExcerpt.take(20_000),
                        workshopSourceDigest = digest.take(24_000),
                        workshopSourceSummary = summary,
                        workshopSourceTimePoint = timePoint,
                        workshopSourceBusy = false,
                        workshopSourceProgress = ""
                    )
                }
                toast(
                    when {
                        fullTextFallbackReason != null ->
                            "全文分段分析失败：$fullTextFallbackReason\n已改用时间点片段分析并填好设定（可检查后再生成）"
                        fastMode && digest.isNotBlank() ->
                            "已快速压缩全文（重点资料 ${digest.length} 字）并填入设定"
                        digest.isNotBlank() -> "已分析全文（全书参考 ${digest.length} 字）并填入设定"
                        else -> "已按原文提取设定：梗概/人设/外貌已填入，可先修改再生成卡"
                    }
                )
                if (autoGenerateCard) {
                    withContext(Dispatchers.Main) { generateCardFromWorkshop() }
                }
            } catch (e: Exception) {
                setState {
                    it.copy(
                        workshopSourceBusy = false,
                        workshopSourceProgress = "",
                        error = "原文分析失败：${e.message ?: e.javaClass.simpleName}\n\n" +
                            "可以这样排查：\n" +
                            "1) 工坊顶部「AI 模型」是否可用：点「切换 API 模型」→「拉取该供应商的模型列表」，选真实存在的型号；\n" +
                            "2) 事件类时间点（某个事件之后 / 事件范围）必须填书中出现过的事件关键词；\n" +
                            "3) 先切「快速压缩」或「仅所选范围片段」再试；\n" +
                            "4) 上下文/额度不足时，把设置里的「上下文上限」调低，或换便宜的模型。"
                    )
                }
            }
        }
    }

    /** 用户选了「章节范围 / 事件范围」时返回该范围的原文；否则返回 null（用全书）。 */
    private fun sourceRangeText(doc: SourceDocument, timePoint: String): String? {
        val chapters = doc.chapters
        if (chapters.isEmpty()) return null
        return when {
            timePoint.startsWith("章节范围") -> {
                val numbers = Regex("\\d+").findAll(timePoint).map { it.value.toInt() }.toList()
                val from = numbers.getOrNull(0) ?: 1
                val to = numbers.getOrNull(1) ?: from
                SourceImporter.excerptChapters(chapters, from, to, 20_000_000)
            }
            timePoint.startsWith("事件范围") -> {
                val body = timePoint.substringAfter("：", "")
                    .ifBlank { timePoint.substringAfter(":", "").trim() }
                val from = body.substringBefore("到").trim()
                val to = body.substringAfter("到", "").trim()
                val full = chapters.joinToString("\n")
                SourceImporter.excerptBetween(full, from, to, 20_000_000)
            }
            else -> null
        }
    }

    private fun buildSourceExcerpt(
        doc: SourceDocument,
        timePoint: String,
        contextLimit: Int
    ): String {
        val maxChars = minOf(80_000, maxOf(8_000, contextLimit / 2))
        val chapters = doc.chapters
        if (chapters.isEmpty()) return ""
        val text = when {
            timePoint.contains("最后") -> chapters.takeLast(2).joinToString("\n")
            timePoint.contains("第一章") || timePoint.contains("开头") ->
                chapters.take(2).joinToString("\n")
            timePoint.startsWith("章节范围") -> {
                val numbers = Regex("\\d+").findAll(timePoint).map { it.value.toInt() }.toList()
                val from = numbers.getOrNull(0) ?: 1
                val to = numbers.getOrNull(1) ?: from
                SourceImporter.excerptChapters(chapters, from, to, maxChars)
            }
            timePoint.startsWith("事件范围") -> {
                val body = timePoint.substringAfter("：", "")
                    .ifBlank { timePoint.substringAfter(":", "").trim() }
                val from = body.substringBefore("到").trim()
                val to = body.substringAfter("到", "").trim()
                val full = chapters.joinToString("\n")
                SourceImporter.excerptBetween(full, from, to, maxChars)
            }
            timePoint.startsWith("章节") -> {
                val number = Regex("\\d+").find(timePoint)?.value?.toIntOrNull() ?: 1
                val start = (number - 2).coerceAtLeast(0)
                chapters.drop(start).take(2).joinToString("\n")
            }
            timePoint.startsWith("事件") -> {
                val keyword = timePoint.substringAfter("：", "")
                    .ifBlank { timePoint.substringAfter(":", "").trim() }
                val full = chapters.joinToString("\n")
                val index = if (keyword.isBlank()) -1 else full.indexOf(keyword)
                if (index < 0) {
                    full.take(maxChars)
                } else {
                    full.substring(
                        (index - maxChars * 2 / 3).coerceAtLeast(0),
                        minOf(full.length, index + maxChars / 6)
                    )
                }
            }
            else -> chapters.joinToString("\n")
        }
        return text.take(maxChars)
    }

    /** 粘贴 AI 生成的 JSON（V3/V2/裸卡均可）→ 解析为待确认预览。 */
    fun importCardFromJson(text: String) {
        if (text.isBlank()) {
            toast("先粘贴角色卡 JSON")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val imported = CardImporter.importBytes(text.trim().toByteArray(Charsets.UTF_8))
                setState {
                    it.copy(
                        workshopCard = imported.card,
                        workshopEntries = imported.entries,
                        workshopJson = text.trim()
                    )
                }
                toast("解析成功：${imported.card.name.ifBlank { "未命名" }}，检查后点「导入卡片」")
            } catch (e: Exception) {
                toast("解析失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** 确认导入工坊预览的卡片。 */
    fun confirmWorkshopImport() {
        val card = _ui.value.workshopCard ?: return
        val entries = _ui.value.workshopEntries
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val id = db.insertCard(card.copy(id = 0), entries)
                refreshLibrary()
                setState {
                    it.copy(
                        screen = Screen.CardDetail(id),
                        workshopCard = null,
                        workshopEntries = emptyList(),
                        workshopJson = null
                    )
                }
                loadCard(id)
                toast("已导入：${card.name.ifBlank { "角色卡" }}")
            } catch (e: Exception) {
                toast("导入失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun clearWorkshopPreview() {
        setState { it.copy(workshopCard = null, workshopEntries = emptyList(), workshopJson = null) }
    }

    /** 把工坊的原始 JSON 导出成文件（方便备份/发给别的 AI 继续改）。 */
    fun exportWorkshopJson(uri: android.net.Uri) {
        val json = _ui.value.workshopJson ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                writeText(uri, json)
                toast("JSON 已导出")
            } catch (e: Exception) {
                toast("导出失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** 工坊：用 API 随机「一个字段」，已确定的其他设定作为约束。 */
    fun randomWorkshopField(field: WorkshopFieldRule) {
        if (_ui.value.workshopFieldBusy) return
        val input = currentDraft()
        setState { it.copy(workshopFieldBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = toolSettings()
                val raw = ApiClient.complete(
                    settings.copy(temperature = 1.1f, topP = 0.95f),
                    listOf(
                        PromptBuilder.ApiMessage(
                            "system",
                            "你是角色卡策划，只输出被要求的内容本身：不要解释、不要引号、不要编号。"
                        ),
                        PromptBuilder.ApiMessage(
                            "user",
                            CardPromptTemplates.randomFieldPrompt(field, input)
                        )
                    ),
                    maxTokens = 300
                ).orEmpty()
                val value = raw
                    .replace("```", "")
                    .trim()
                    .lines()
                    .firstOrNull()
                    .orEmpty()
                    .trim()
                    .trim('"', '“', '”', '\'', '*', '-', ' ', '　')
                if (value.isBlank()) {
                    toast(apiErrorOr("随机失败：接口没有返回内容"))
                } else {
                    modifyDraft { applyField(it, field, value) }
                    toast("已随机${field.label}：${value.take(20)}")
                }
            } catch (e: Exception) {
                toast("随机失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                setState { it.copy(workshopFieldBusy = false) }
            }
        }
    }

    /** 把 API 随机出的单个值写进草稿对应字段（候选字段做模糊匹配）。 */
    private fun applyField(draft: WorkshopInput, field: WorkshopFieldRule, value: String): WorkshopInput {
        fun match(options: List<String>, current: String): String =
            options.firstOrNull { it == value || it.contains(value) || value.contains(it) } ?: current
        return when (field) {
            WorkshopFieldRule.NAME -> draft.copy(name = value.take(20))
            WorkshopFieldRule.GENDER -> draft.copy(
                gender = when {
                    value.contains("男") -> "男性"
                    value.contains("女") -> "女性"
                    else -> draft.gender
                }
            )
            WorkshopFieldRule.SCALE ->
                draft.copy(scale = match(WorkshopFieldRule.SCALE.options.orEmpty(), draft.scale))
            WorkshopFieldRule.WORLD ->
                draft.copy(world = match(WorkshopFieldRule.WORLD.options.orEmpty(), draft.world))
            WorkshopFieldRule.ARCHETYPE ->
                draft.copy(archetype = match(WorkshopFieldRule.ARCHETYPE.options.orEmpty(), draft.archetype))
            WorkshopFieldRule.PLOT ->
                draft.copy(plot = match(WorkshopFieldRule.PLOT.options.orEmpty(), draft.plot))
            WorkshopFieldRule.GENRE -> draft.copy(genre = value.take(30))
            WorkshopFieldRule.TONE -> draft.copy(tone = value.take(40))
            WorkshopFieldRule.APPEARANCE -> draft.copy(appearance = value.take(150))
            WorkshopFieldRule.PRIORITY -> draft.copy(
                priorityBase = value.filter { it.isDigit() }.take(3).toIntOrNull()
                    ?.coerceIn(0, 999) ?: draft.priorityBase
            )
            WorkshopFieldRule.WORLDBOOK -> draft.copy(
                worldbookLevel = match(WorkshopFieldRule.WORLDBOOK.options.orEmpty(), draft.worldbookLevel)
            )
            WorkshopFieldRule.TITLE_COUNT -> draft.copy(
                titleCount = value.filter { it.isDigit() }.take(2).toIntOrNull()
                    ?.coerceIn(1, 10) ?: draft.titleCount
            )
        }
    }

    /** 工坊：用 API 整组随机——只补当前未设置的字段，已设置的保持不变。 */
    fun randomWorkshopOptions() {
        if (_ui.value.workshopFieldBusy) return
        val input = currentDraft()
        setState { it.copy(workshopFieldBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = toolSettings()
                val raw = ApiClient.complete(
                    settings.copy(temperature = 1.1f, topP = 0.95f),
                    listOf(
                        PromptBuilder.ApiMessage(
                            "system",
                            "你是角色卡策划，只输出一个 JSON 对象，不要 markdown、不要解释。"
                        ),
                        PromptBuilder.ApiMessage(
                            "user",
                            CardPromptTemplates.randomOptionsPrompt(input)
                        )
                    ),
                    maxTokens = 800
                ).orEmpty()
                val json = extractJsonObject(raw)
                    ?: throw IllegalArgumentException(ApiClient.lastError ?: "模型没有返回可解析的 JSON")
                val updated = applyOptionsJson(input, json)
                setState { it.copy(workshopDraft = updated) }
                toast("已按 API 随机补齐未设置项")
            } catch (e: Exception) {
                toast("随机失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                setState { it.copy(workshopFieldBusy = false) }
            }
        }
    }

    /** 工坊：润色草稿里的梗概（保留设定，只改文笔/节奏；可多次；压栈可撤回/恢复）。 */
    fun polishWorkshopDraft(lengthLabel: String) {
        val input = currentDraft()
        if (input.idea.isBlank()) {
            toast("先写一段梗概再润色")
            return
        }
        if (_ui.value.workshopPolishBusy) return
        setState { it.copy(workshopPolishBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = toolSettings()
                val raw = ApiClient.complete(
                    settings.copy(temperature = 0.9f, topP = 0.95f),
                    listOf(
                        PromptBuilder.ApiMessage(
                            "system",
                            "你是中文小说编辑，只输出润色后的故事梗概正文，不要解释、不要标题。"
                        ),
                        PromptBuilder.ApiMessage(
                            "user",
                            CardPromptTemplates.polishSynopsisPrompt(input, lengthLabel)
                        )
                    ),
                    maxTokens = 1800
                ).orEmpty()
                val text = raw
                    .removePrefix("```").removeSuffix("```")
                    .trim()
                    .lines()
                    .dropWhile { it.isBlank() }
                    .joinToString("\n")
                    .trim()
                    .trim('"', '“', '”')
                if (text.isBlank()) {
                    toast(apiErrorOr("润色失败：接口没有返回内容"))
                } else {
                    pushSynopsisHistory(input.idea)
                    modifyDraft { it.copy(idea = text) }
                    toast("已润色（${text.length} 字），可继续润色、撤回或恢复")
                }
            } catch (e: Exception) {
                toast("润色失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                setState { it.copy(workshopPolishBusy = false) }
            }
        }
    }

    /** 工坊：按当前梗概提取并填写选项（只改选项，不生成角色卡；梗概不动）。 */
    fun fillOptionsFromSynopsis() {
        val input = currentDraft()
        if (input.idea.isBlank()) {
            toast("先写/生成一段梗概")
            return
        }
        if (_ui.value.workshopFieldBusy) return
        setState { it.copy(workshopFieldBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = toolSettings()
                val raw = ApiClient.complete(
                    settings.copy(temperature = 0.3f, topP = 1.0f),
                    listOf(
                        PromptBuilder.ApiMessage(
                            "system",
                            "你是角色卡设定提取器，只输出一个 JSON 对象，不要 markdown、不要解释。"
                        ),
                        PromptBuilder.ApiMessage(
                            "user",
                            CardPromptTemplates.extractOptionsPrompt(input)
                        )
                    ),
                    maxTokens = 600
                ).orEmpty()
                val json = extractJsonObject(raw)
                    ?: throw IllegalArgumentException(ApiClient.lastError ?: "模型没有返回可解析的 JSON")
                val updated = applyOptionsJson(input, json)
                setState { it.copy(workshopDraft = updated) }
                toast("已按梗概填写选项（梗概未改，可继续修改）")
            } catch (e: Exception) {
                toast("填写失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                setState { it.copy(workshopFieldBusy = false) }
            }
        }
    }

    /** 把模型返回的选项 JSON 合并进 WorkshopInput（候选字段做模糊匹配）。 */
    private fun applyOptionsJson(input: WorkshopInput, json: String): WorkshopInput {
        val obj = JSONObject(json)
        var updated = input
        fun read(key: String): String? =
            if (obj.has(key) && !obj.isNull(key)) obj.optString(key).trim().takeIf { it.isNotBlank() } else null
        read("gender")?.let { v ->
            updated = updated.copy(
                gender = when {
                    v.contains("男") -> "男性"
                    v.contains("女") -> "女性"
                    else -> updated.gender
                }
            )
        }
        fun matchCandidates(key: String, options: List<String>, apply: (String) -> Unit) {
            read(key)?.let { v ->
                options.firstOrNull { it == v || it.contains(v) || v.contains(it) }?.let(apply)
            }
        }
        matchCandidates("world", WorkshopFieldRule.WORLD.options.orEmpty()) { updated = updated.copy(world = it) }
        matchCandidates("archetype", WorkshopFieldRule.ARCHETYPE.options.orEmpty()) { updated = updated.copy(archetype = it) }
        matchCandidates("plot", WorkshopFieldRule.PLOT.options.orEmpty()) { updated = updated.copy(plot = it) }
        matchCandidates("scale", WorkshopFieldRule.SCALE.options.orEmpty()) { updated = updated.copy(scale = it) }
        read("genre")?.let { updated = updated.copy(genre = it.take(30)) }
        read("tone")?.let { updated = updated.copy(tone = it.take(40)) }
        read("appearance")?.let { updated = updated.copy(appearance = it.take(150)) }
        read("name")?.let { if (input.name.isBlank()) updated = updated.copy(name = it.take(20)) }
        return updated
    }

    /** 工坊：按当前选项随机生成梗概，写入草稿并压撤销栈。 */
    fun generateWorkshopSynopsis(lengthLabel: String) {
        val input = currentDraft()
        if (_ui.value.workshopSynopsisBusy) return
        setState { it.copy(workshopSynopsisBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = toolSettings()
                val raw = ApiClient.complete(
                    settings.copy(temperature = 1.0f, topP = 0.95f),
                    listOf(
                        PromptBuilder.ApiMessage(
                            "system",
                            "你是角色扮演故事策划，只输出故事梗概正文，不要标题、不要解释、不要 JSON。"
                        ),
                        PromptBuilder.ApiMessage(
                            "user",
                            CardPromptTemplates.synopsisPrompt(input, lengthLabel)
                        )
                    ),
                    maxTokens = 1400
                ).orEmpty().trim()
                val text = raw
                    .removePrefix("```").removeSuffix("```")
                    .trim()
                    .trim('"')
                    .trim()
                if (text.isBlank()) {
                    toast(apiErrorOr("梗概生成失败：接口没有返回内容"))
                } else {
                    pushSynopsisHistory(input.idea)
                    modifyDraft { it.copy(idea = text) }
                    toast("梗概已生成（${text.length} 字），可撤回或继续改")
                }
            } catch (e: Exception) {
                toast("梗概生成失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                setState { it.copy(workshopSynopsisBusy = false) }
            }
        }
    }

    /** 工坊：用当前 API 生成整张卡并预览；失败只提示，草稿（梗概/选项）一律不动。 */
    fun generateCardFromWorkshop() {
        val input = currentDraft()
        if (_ui.value.workshopBusy) return
        setState { it.copy(workshopBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = toolSettings()
                val result = ApiClient.complete(
                    settings.copy(temperature = 0.9f, topP = 0.95f),
                    listOf(
                        PromptBuilder.ApiMessage(
                            "system",
                            "你是角色卡生成器，只输出一个 JSON 对象，不要 markdown 代码块。"
                        ),
                        PromptBuilder.ApiMessage("user", CardPromptTemplates.cardJsonPrompt(
                            input,
                            _ui.value.workshopSourceExcerpt,
                            _ui.value.workshopSourceSummary,
                            _ui.value.workshopSourceRequirement,
                            _ui.value.workshopSourceDigest,
                            _ui.value.workshopSourceFocus
                        ))
                    ),
                    maxTokens = 12_288
                )
                val json = extractJsonObject(result.orEmpty())
                    ?: throw IllegalArgumentException(
                        ApiClient.lastError ?: "模型没有返回可解析的 JSON"
                    )
                val imported = CardImporter.importBytes(json.toByteArray(Charsets.UTF_8))
                setState {
                    it.copy(
                        workshopBusy = false,
                        workshopCard = imported.card,
                        workshopEntries = imported.entries,
                        workshopJson = json
                    )
                }
                toast("已生成：${imported.card.name.ifBlank { "未命名" }}，检查后点「导入卡片」")
            } catch (e: Exception) {
                // 只关闭 busy，不改 workshopDraft：梗概与选项全部保留
                setState { it.copy(workshopBusy = false) }
                toast("生成失败（梗概与选项已保留）：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** 从模型回复里截取第一个完整 JSON 对象（容忍 ```json 包裹与前后废话）。 */
    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun groupCardsFor(chatId: Long): List<CharacterCard> {
        val ids = db.getGroupCardIds(chatId)
        if (ids.size < 2) return emptyList()
        return ids.mapNotNull { db.getCard(it) }
    }

    private fun entriesFor(card: CharacterCard, groupCards: List<CharacterCard>): List<WorldEntry> =
        if (groupCards.size > 1) {
            groupCards.flatMap { db.getEntries(it.id) }
        } else {
            db.getEntries(card.id)
        }

    /** 欢迎语/开场白里的 {{user}}、{{char}} 在创建会话时就落成实际名字。 */
    private fun substituteMacros(text: String, charName: String): String {
        if (text.isBlank()) return text
        val user = _ui.value.settings.personaName.ifBlank { "你" }
        return text
            .replace("{{char}}", charName)
            .replace("{{Char}}", charName)
            .replace("<BOT>", charName)
            .replace("{{user}}", user)
            .replace("{{User}}", user)
            .replace("{{persona}}", user)
            .replace("<USER>", user)
    }

    // ---------------- memory ----------------

    /** 手动重新生成前情提要（用当前预设/供应商的模型，非流式）。 */
    fun refreshMemory(chatId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = resolveSettings(_ui.value.chat?.chat?.presetId ?: 0L).first
            val ok = summarizeMemory(chatId, settings, force = true)
            toast(if (ok) "前情提要已更新" else "生成失败：没有可总结的内容或请求失败")
        }
    }

    fun editMemory(chatId: Long, summary: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (summary.isBlank()) {
                db.clearMemory(chatId)
            } else {
                val upto = db.getMessages(chatId).lastOrNull()?.id ?: 0L
                db.saveMemory(chatId, summary.trim(), upto)
            }
            setState { it.copy(memory = db.getMemory(chatId)) }
            toast("前情提要已保存")
        }
    }

    fun clearMemory(chatId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.clearMemory(chatId)
            setState { it.copy(memory = null) }
            toast("前情提要已清空")
        }
    }

    /**
     * 把「尚未被摘要覆盖」的历史压成前情提要。
     * force = true 时忽略阈值（手动触发）；历史太短时也会摘要，便于手动整理。
     */
    private suspend fun summarizeMemory(chatId: Long, settings: AppSettings, force: Boolean): Boolean {
        if (!summarizing.add(chatId)) return false
        try {
            val messages = db.getMessages(chatId).filter { it.role == "user" || it.role == "assistant" }
            if (messages.isEmpty()) return false
            val memory = db.getMemory(chatId)
            val coveredUpTo = memory?.msgUptoId ?: 0L
            val fresh = messages.filter { it.id > coveredUpTo }
            if (fresh.isEmpty()) return false
            val freshTokens = fresh.sumOf { TokenEstimator.estimate(it.content) }
            val thresholdTokens = (settings.contextLimit / 10).coerceAtLeast(1200)
            val thresholdMessages = settings.summaryThresholdMessages.coerceIn(4, 200)
            if (!force && fresh.size < thresholdMessages && freshTokens < thresholdTokens) return false
            val transcript = fresh.takeLast(80).joinToString("\n") { m ->
                (if (m.role == "user") "用户" else "角色") + "：" + m.content
            }.take(14000)
            val previousSummary = memory?.summary.orEmpty()
            val userPrompt = buildString {
                if (previousSummary.isNotBlank()) {
                    append("【已有前情提要】\n").append(previousSummary).append("\n\n")
                }
                append("【新增剧情】\n").append(transcript).append("\n\n")
                append("请把以上内容整合成一份前情提要，用于后续角色扮演时注入上下文。要求：")
                append("1) 保留人物关系、关键事件、重要设定与未解决的悬念；")
                append("2) 按时间顺序用简洁段落写，300~600 字；")
                append("3) 只做摘要，不续写、不评价。")
            }
            val request = ApiClient.buildChatRequest(
                settings.copy(temperature = 0.3f, topP = 1.0f, maxTokens = 900),
                listOf(
                    PromptBuilder.ApiMessage("system", SUMMARY_SYSTEM),
                    PromptBuilder.ApiMessage("user", userPrompt)
                ),
                stream = false
            )
            val result = ApiClient.http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body?.string().orEmpty()
                val summary = runCatching {
                    JSONObject(body).optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        .orEmpty()
                }.getOrDefault("").trim()
                if (summary.isBlank()) return@use false
                db.saveMemory(chatId, summary, fresh.last().id)
                if (_ui.value.chat?.chat?.id == chatId) {
                    setState { it.copy(memory = db.getMemory(chatId)) }
                }
                true
            }
            return result
        } catch (e: Exception) {
            return false
        } finally {
            summarizing.remove(chatId)
        }
    }

    // ---------------- usage / budget ----------------

    private fun startOfDay(): Long = java.time.LocalDate.now()
        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun startOfMonth(): Long = java.time.LocalDate.now().withDayOfMonth(1)
        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun refreshUsageStats() {
        viewModelScope.launch(Dispatchers.IO) {
            db.pruneUsage(System.currentTimeMillis() - 180L * 24 * 3600 * 1000)
            val (todayPrompt, todayCompletion) = db.usageSince(startOfDay())
            val (monthPrompt, monthCompletion) = db.usageSince(startOfMonth())
            setState {
                it.copy(
                    usageStats = UsageStats(
                        todayPrompt = todayPrompt,
                        todayCompletion = todayCompletion,
                        monthPrompt = monthPrompt,
                        monthCompletion = monthCompletion
                    )
                )
            }
        }
    }

    private fun checkDailyBudget(settings: AppSettings) {
        if (settings.dailyBudgetYuan <= 0.0) return
        val (todayPrompt, todayCompletion) = db.usageSince(startOfDay())
        val cost = CostCalculator.cost(settings, todayPrompt, todayCompletion)
        if (cost > settings.dailyBudgetYuan) {
            toast(
                String.format(
                    java.util.Locale.US,
                    "今日已花费 ≈ ¥%.4f，超过预算 ¥%.2f",
                    cost,
                    settings.dailyBudgetYuan
                )
            )
        }
    }

    // ---------------- auto backup ----------------

    private fun maybeAutoBackup() {
        if (!_ui.value.settings.autoBackupEnabled) return
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            .format(java.util.Date())
        if (prefs.autoBackupLastDate() == today) return
        viewModelScope.launch(Dispatchers.IO) { doAutoBackup(showToast = false) }
    }

    fun runAutoBackupNow() {
        viewModelScope.launch(Dispatchers.IO) { doAutoBackup(showToast = true) }
    }

    private fun doAutoBackup(showToast: Boolean) {
        try {
            val base = getApplication<Application>().getExternalFilesDir(null) ?: return
            val dir = java.io.File(base, "backups")
            if (!dir.exists() && !dir.mkdirs()) return
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                .format(java.util.Date())
            val file = java.io.File(dir, "tavern-auto-$stamp.json")
            file.writeText(db.dumpJson())
            prefs.saveAutoBackupDate(stamp.substring(0, 8))
            val files = dir.listFiles { f -> f.name.startsWith("tavern-auto-") }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
            files.drop(7).forEach { runCatching { it.delete() } }
            if (showToast) toast("已备份：${file.absolutePath}")
        } catch (e: Exception) {
            if (showToast) toast("备份失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ---------------- model list ----------------

    fun clearModelOptions() = setState { it.copy(modelOptions = emptyList()) }

    /** 从供应商的 /models 接口拉取模型列表（OpenAI 兼容）。 */
    fun fetchModels(provider: ProviderConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val base = provider.baseUrl.trim().trimEnd('/')
                val root = if (base.endsWith("/chat/completions")) {
                    base.removeSuffix("/chat/completions")
                } else {
                    base
                }
                val request = okhttp3.Request.Builder()
                    .url("$root/models")
                    .get()
                    .apply {
                        if (provider.apiKey.isNotBlank()) {
                            header("Authorization", "Bearer ${provider.apiKey.trim()}")
                        }
                        ApiClient.parseExtraHeaders(provider).forEach { (name, value) ->
                            header(name, value)
                        }
                    }
                    .build()
                ApiClient.http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        toast("拉取模型失败 ${response.code}")
                        return@use
                    }
                    val body = response.body?.string().orEmpty()
                    val arr = JSONObject(body).optJSONArray("data")
                    val ids = mutableListOf<String>()
                    val contexts = mutableMapOf<String, Int>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            val id = item.optString("id").orEmpty()
                            if (id.isBlank()) continue
                            ids.add(id)
                            val ctx = item.optInt("context_length", 0).takeIf { it > 0 }
                                ?: item.optInt("context_window", 0).takeIf { it > 0 }
                                ?: item.optJSONObject("top_provider")
                                    ?.optInt("context_length", 0)
                                    ?.takeIf { it > 0 }
                            if (ctx != null) contexts[id] = ctx
                        }
                    }
                    setState { it.copy(modelOptions = ids, modelContexts = contexts) }
                    toast(
                        if (ids.isEmpty()) {
                            "没有可用模型"
                        } else {
                            "已获取 ${ids.size} 个模型" +
                                if (contexts.isNotEmpty()) "（${contexts.size} 个带上下文信息）" else ""
                        }
                    )
                }
            } catch (e: Exception) {
                toast("拉取失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    // ---------------- settings ----------------

    fun saveSettings(settings: AppSettings) {
        prefs.save(settings)
        setState { it.copy(settings = settings) }
        toast("设置已保存")
    }

    fun testConnection(settings: AppSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val started = System.currentTimeMillis()
                val request = ApiClient.buildChatRequest(
                    settings,
                    listOf(PromptBuilder.ApiMessage("user", "ping")),
                    stream = false,
                    maxTokensOverride = 1
                )
                ApiClient.http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        toast("连接成功（${settings.provider.model}，${System.currentTimeMillis() - started}ms）")
                    } else {
                        toast("连接失败 ${response.code}：${body.take(200)}")
                    }
                }
            } catch (e: Exception) {
                toast("连接失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun estimateTokens(text: String): Int = TokenEstimator.estimate(text)

    /** 聊天页快速切换模型：更新指定供应商的 model 并设为当前供应商；已知型号自动套用上下文/输出规格。 */
    fun switchModel(providerIndex: Int, model: String) {
        val trimmed = model.trim()
        if (trimmed.isBlank()) {
            toast("模型 ID 不能为空")
            return
        }
        val s = _ui.value.settings
        if (s.providers.isEmpty()) return
        val index = providerIndex.coerceIn(0, s.providers.lastIndex)
        val updated = s.providers.mapIndexed { i, p ->
            if (i == index) p.copy(model = trimmed) else p
        }
        var newSettings = s.copy(activeProvider = index, providers = updated)
        val spec: ModelSpec? = ModelCatalog.specFor(trimmed)
            ?: _ui.value.modelContexts[trimmed]?.let { ModelSpec(it, 0) }
        if (spec != null && spec.contextLimit > 0) {
            val newMax = if (spec.maxOutput > 0) {
                minOf(maxOf(newSettings.maxTokens, 8192), spec.maxOutput)
            } else {
                newSettings.maxTokens
            }
            newSettings = newSettings.copy(
                contextLimit = spec.contextLimit,
                maxTokens = newMax
            )
            saveSettings(newSettings)
            toast("已切换：$trimmed · 上下文 ${spec.contextLimit} · 输出上限 $newMax")
        } else {
            saveSettings(newSettings)
            toast("已切换模型：$trimmed（未识别规格，可在设置里手动调上下文）")
        }
    }
}

/** org.json 的 null 不能直接 optString（会得到字符串 "null"，此前导致回复里出现 nullnull…）。 */
private fun JSONObject.optTextOrEmpty(name: String): String =
    if (isNull(name)) "" else optString(name, "")

/** 清理旧版本 SSE 解析 bug 留下的连续 "null" 污染（仅显示/落库前兜底，不误删正常单词）。 */
private fun stripNullNoise(text: String): String {
    if (!text.contains("null", ignoreCase = true)) return text
    return text.replace(Regex("(?i)(null\\s*){2,}"), "").trim()
}
