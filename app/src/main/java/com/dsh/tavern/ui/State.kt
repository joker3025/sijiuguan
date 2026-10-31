package com.dsh.tavern.ui

import com.dsh.tavern.data.AppSettings
import com.dsh.tavern.data.CharacterCard
import com.dsh.tavern.data.Chat
import com.dsh.tavern.data.ChatListItem
import com.dsh.tavern.data.ChatMemory
import com.dsh.tavern.data.Checkpoint
import com.dsh.tavern.data.FieldConflict
import com.dsh.tavern.data.LibraryItem
import com.dsh.tavern.data.Message
import com.dsh.tavern.data.MessageHit
import com.dsh.tavern.data.PanelTemplate
import com.dsh.tavern.data.Preset
import com.dsh.tavern.data.PromptDebug
import com.dsh.tavern.data.UsageStats
import com.dsh.tavern.data.WorldEntry
import com.dsh.tavern.data.WorldbookCollection
import com.dsh.tavern.data.WorkshopInput

sealed interface Screen {
    data object Library : Screen
    data class CardDetail(val cardId: Long) : Screen
    data class Chat(val chatId: Long) : Screen
    data object Settings : Screen
    data object Presets : Screen
    /** P1-9：卡片编辑器；cardId = 0 表示新建。 */
    data class CardEditor(val cardId: Long) : Screen
    /** 全局会话列表 + 消息搜索。 */
    data object ChatList : Screen
    /** E1：跨卡共享的全局世界书。 */
    data object GlobalWorldbook : Screen
    /** v1.6：世界书收藏库（分组，可导入/适配到当前卡）。 */
    data class WorldbookLibrary(val cardId: Long) : Screen
    /** v1.4：角色卡工坊（提示词模板 + 粘贴/API 生成导入）。 */
    data object Workshop : Screen
}

data class CardDetailData(
    val card: CharacterCard,
    val entries: List<WorldEntry>,
    val chats: List<Chat>
)

data class ChatData(
    val chat: Chat,
    val card: CharacterCard,
    val messages: List<Message>,
    val contextTokens: Int,
    val worldCount: Int,
    /** P0-4：本次组装的段落预算明细（角色卡/世界书/历史/输出预留）。 */
    val debug: PromptDebug = PromptDebug(),
    /** 是否还有更早的消息（分页加载）。 */
    val hasMore: Boolean = false,
    /** E3：多角色同台时该会话绑定多张卡（size<=1 表示普通单卡）。 */
    val groupCards: List<CharacterCard> = emptyList()
)

/** 本次流式生成是「新消息 / 重生成某条 / 继续某条」。 */
enum class StreamMode { NEW, REGENERATE, CONTINUE }

data class StreamState(
    val streaming: Boolean = false,
    val chatId: Long = 0,
    val content: String = "",
    val reasoning: String = "",
    val mode: StreamMode = StreamMode.NEW,
    /** REGENERATE / CONTINUE 时对应的消息 id。 */
    val targetMessageId: Long = 0,
    /** 真实用量（P0-4：来自接口 usage 字段）。 */
    val usagePrompt: Int = 0,
    val usageCompletion: Int = 0
)

/** 正在查看的 Prompt 调试数据（下一步请求预览，或某条历史消息的登记）。 */
data class PromptPreviewData(
    val title: String,
    val debug: PromptDebug,
    /** true = 下一次请求的实时预览；false = 从消息里读出的历史快照。 */
    val live: Boolean = true
)

data class UiState(
    val screen: Screen = Screen.Library,
    val library: List<LibraryItem> = emptyList(),
    val detail: CardDetailData? = null,
    val chat: ChatData? = null,
    val settings: AppSettings = AppSettings(),
    val presets: List<Preset> = emptyList(),
    val memory: ChatMemory? = null,
    val checkpoints: List<Checkpoint> = emptyList(),
    /** P1-6：当前会话卡片的互动面板模板与运行时状态。 */
    val panelTemplate: PanelTemplate? = null,
    val panelStateJson: String = "",
    /** E2：当前会话变量（name -> value）。 */
    val chatVariables: Map<String, String> = emptyMap(),
    val editorCard: CharacterCard? = null,
    val editorEntries: List<WorldEntry> = emptyList(),
    val globalEntries: List<WorldEntry> = emptyList(),
    /** E4：应用锁是否已锁定。 */
    val locked: Boolean = false,
    /** v1.4：工坊是否正在用 API 生成角色卡。 */
    val workshopBusy: Boolean = false,
    /** v1.5.4：工坊是否正在用 API 生成故事梗概。 */
    val workshopSynopsisBusy: Boolean = false,
    /** v1.5.6：工坊是否正在用 API 随机字段/整组选项。 */
    val workshopFieldBusy: Boolean = false,
    /** v1.5.8：工坊草稿（放 ViewModel，生成失败/切页都不丢）。 */
    val workshopDraft: WorkshopInput = WorkshopInput(),
    /** 润色撤回栈（旧梗概）与恢复栈（被撤回的新梗概）。 */
    val workshopUndo: List<String> = emptyList(),
    val workshopRedo: List<String> = emptyList(),
    /** v1.6：AI 改写/冲突检查/世界书生成是否进行中。 */
    val aiBusy: Boolean = false,
    /** v1.7.4：接口报错时解析出的「可用模型」，用于一键切换重试。 */
    val modelFixFailed: String = "",
    val modelFixModels: List<String> = emptyList(),
    /** v1.6：世界书收藏库分组。 */
    val worldbookCollections: List<WorldbookCollection> = emptyList(),
    val worldbookLibraryCardId: Long = 0,
    /** v1.9：视觉模型从截图转录出的角色卡介绍文字（可手改）。 */
    val workshopImageText: String = "",
    val workshopImageBusy: Boolean = false,
    /** v1.7：工坊导入的原文（TXT/EPUB）与 AI 提取结果。 */
    val workshopSourceTitle: String = "",
    val workshopSourceFormat: String = "",
    val workshopSourceChapterCount: Int = 0,
    val workshopSourceCharCount: Int = 0,
    val workshopSourceExcerpt: String = "",
    val workshopSourceSummary: String = "",
    val workshopSourceTimePoint: String = "",
    val workshopSourceRequirement: String = "",
    /** v1.8.3：原文分析重点保留的方面（人物关系/外貌/性格/事件/说话方式等）。 */
    val workshopSourceFocus: List<String> = listOf(
        "人物关系", "外貌外形", "性格特点", "重要经历事件", "说话方式"
    ),
    val workshopSourceBusy: Boolean = false,
    /** v1.8：全文分段分析得到的「全书参考」，用于生成卡/世界书/改写。 */
    val workshopSourceDigest: String = "",
    /** 全文分析进度文字（第 i/N 段）。 */
    val workshopSourceProgress: String = "",
    /** v1.5.7：工坊是否正在润色梗概。 */
    val workshopPolishBusy: Boolean = false,
    /** v1.4：工坊待确认的卡片（生成/粘贴后先预览，确认再导入）。 */
    val workshopCard: CharacterCard? = null,
    val workshopEntries: List<WorldEntry> = emptyList(),
    val workshopJson: String? = null,
    val chatList: List<ChatListItem> = emptyList(),
    val messageHits: List<MessageHit> = emptyList(),
    val modelOptions: List<String> = emptyList(),
    /** /models 返回的模型上下文长度（model -> tokens，拿不到则为空）。 */
    val modelContexts: Map<String, Int> = emptyMap(),
    /** 动态建议回复：按最近剧情生成的 3 条可选回复。 */
    val suggestions: List<String> = emptyList(),
    val usageStats: UsageStats = UsageStats(),
    val stream: StreamState = StreamState(),
    val promptPreview: PromptPreviewData? = null,
    val toast: String? = null,
    val error: String? = null
)
