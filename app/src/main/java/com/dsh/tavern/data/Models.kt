package com.dsh.tavern.data

data class ProviderConfig(
    val name: String = "DeepSeek",
    val baseUrl: String = "https://api.deepseek.com/v1",
    val apiKey: String = "",
    val model: String = "deepseek-flash",
    /** 自定义请求头（每行 Name: value），给 OpenRouter/网关用。 */
    val extraHeaders: String = ""
)

data class AppSettings(
    val providers: List<ProviderConfig> = listOf(ProviderConfig()),
    val activeProvider: Int = 0,
    val temperature: Float = 1.0f,
    val topP: Float = 1.0f,
    val maxTokens: Int = 4096,
    val contextLimit: Int = 65536,
    val personaName: String = "你",
    val personaDescription: String = "",
    val systemPreset: String = DEFAULT_PRESET,
    val nsfwEnabled: Boolean = true,
    val reasoningDisplay: Boolean = true,
    /** 请求 body 是否带 stream_options.include_usage（部分中转站不支持时关掉）。 */
    val includeUsage: Boolean = true,
    /** 每百万 token 单价（用于估算花费；0 = 不显示金额）。 */
    val priceInputPerMillion: Double = 0.0,
    val priceOutputPerMillion: Double = 0.0,
    /** 世界书触发扫描窗口：最近 N 条消息。 */
    val worldbookScanDepth: Int = 6,
    /** 世界书 token 预算 = 上下文上限的百分比。 */
    val worldbookBudgetPercent: Int = 25,
    /** 主题：dark / light / system（跟随系统）。 */
    val themeMode: String = "dark",
    /** 历史变长后自动生成前情提要。 */
    val autoSummaryEnabled: Boolean = true,
    /** 触发自动摘要的消息条数阈值。 */
    val summaryThresholdMessages: Int = 16,
    /** 每日预算（元，0 = 不提醒）。 */
    val dailyBudgetYuan: Double = 0.0,
    /** 启动时自动备份。 */
    val autoBackupEnabled: Boolean = true,
    /** 停止序列（换行分隔，空 = 不设置）。 */
    val stopSequences: String = "",
    /** 频率惩罚（-2.0 ~ 2.0）。 */
    val frequencyPenalty: Float = 0f,
    /** 存在惩罚（-2.0 ~ 2.0）。 */
    val presencePenalty: Float = 0f,
    /** E4：应用锁（启动需要 PIN）。 */
    val appLockEnabled: Boolean = false,
    /** PIN 的 SHA-256（空 = 未设置）。 */
    val appLockPinHash: String = "",
    /** E4：隐藏最近任务预览（FLAG_SECURE，下次启动生效）。 */
    val hideInRecents: Boolean = false,
    /** 动态建议回复：每轮回复后额外调一次小请求，按当前剧情生成 3 条可选回复。 */
    val dynamicSuggestions: Boolean = true,
    /** 最低回复字数（中文字符，0 = 不限制），会写进系统提示要求模型展开。 */
    val minReplyLength: Int = 0,
    /**
     * AI 工具模型（工坊随机/原文分析/世界书 AI/卡片字段改写等）。
     * 留空 = 跟随当前对话模型；填写后这些功能单独用该模型（仍用当前供应商的 Base URL / Key）。
     */
    val toolModel: String = "",
    /** AI 工具用的供应商下标（-1 = 跟随当前供应商）。 */
    val toolProviderIndex: Int = -1
) {
    val provider: ProviderConfig
        get() = providers.getOrNull(activeProvider) ?: providers.firstOrNull() ?: ProviderConfig()

    companion object {
        val DEFAULT_PRESET = """
你是一名出色的角色扮演作者，正在与{{user}}进行沉浸式成人向角色扮演（仅限18岁以上的虚构创作）。

规则：
1. 始终以{{char}}的身份、第一人称（或角色惯用口吻）行动和说话，不代替{{user}}行动或说话，不跳出角色。
2. 严格保持角色的性格、说话方式、称呼、知识范围与人物关系，避免OOC和降智。
3. 回复要有画面感：动作、神态、环境、感官细节与对话结合；每次推进一小段剧情，给{{user}}留出回应空间。
4. 尊重世界书设定；世界书与角色设定冲突时，以世界书为准。
5. 涉及亲密场景时允许直白、细腻的成人向描写，但必须符合角色性格与情感发展，不写未成年内容。
6. 不要输出解释、旁白说明或“作为AI”之类的内容，直接进入角色。
""".trim()
    }
}

data class CharacterCard(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val creatorNotes: String = "",
    val firstMes: String = "",
    val mesExample: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val suggestedReplies: List<String> = emptyList(),
    val creator: String = "",
    val characterVersion: String = "",
    val rawJson: String = "",
    /** P1-6：互动面板模板 JSON（空 = 未配置）。 */
    val statusTemplate: String = "",
    /** 新对话默认使用的预设；0 = 跟随全局。 */
    val defaultPresetId: Long = 0,
    /** E2：输出后处理规则（每行「正则 => 替换」）。 */
    val regexRules: String = "",
    /** E2：初始变量（每行「名字=值」），新对话时写入会话变量。 */
    val variablesText: String = "",
    val avatar: ByteArray? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

data class WorldEntry(
    val id: Long = 0,
    val cardId: Long = 0,
    val name: String = "",
    val keys: List<String> = emptyList(),
    val content: String = "",
    val constant: Boolean = false,
    val selective: Boolean = true,
    val selectiveLogic: Int = 0,
    val priority: Int = 0,
    val insertionOrder: Int = 0,
    val enabled: Boolean = true,
    val caseSensitive: Boolean = false,
    val position: String = "after_char",
    /** 条件启用概率（0-100，按本轮对话确定性计算，同一条目同轮结果固定）。 */
    val probability: Int = 100,
    /** position = in_history 时的插入深度（距离对话末尾的层数）。 */
    val depth: Int = 4,
    /** 辅助关键词：非空时至少要命中一个，否则条目不触发。 */
    val secondaryKeys: List<String> = emptyList(),
    /** 触发词按正则表达式匹配（keys 里每项是一条正则）。 */
    val regex: Boolean = false
)

data class Chat(
    val id: Long = 0,
    val cardId: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessage: String = "",
    val model: String = "",
    /** 本会话使用的预设；0 = 跟随全局设置。 */
    val presetId: Long = 0,
    /** 从哪个会话分支出来的；0 = 原会话。 */
    val branchedFrom: Long = 0
)

/**
 * 一套可切换的「提示词 + 参数」预设。
 * 对应 DZMM 预设中心的简化版：系统提示、后置指令、温度/top_p/最大输出、风格名。
 * 内置 4 套（标准/创意/发散/抗复读），可复制、编辑、删除。
 */
data class Preset(
    val id: Long = 0,
    val name: String,
    val systemPrompt: String = "",
    val postHistory: String = "",
    val temperature: Float = 1.0f,
    val topP: Float = 1.0f,
    val maxTokens: Int = 4096,
    val style: String = "标准",
    val builtIn: Boolean = false,
    /** 停止序列（换行分隔）。 */
    val stopSequences: String = "",
    val frequencyPenalty: Float = 0f,
    val presencePenalty: Float = 0f,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    companion object {
        fun builtIns(): List<Preset> = listOf(
            Preset(
                name = "标准",
                systemPrompt = AppSettings.DEFAULT_PRESET,
                postHistory = "",
                temperature = 1.0f,
                topP = 1.0f,
                style = "标准",
                builtIn = true
            ),
            Preset(
                name = "创意",
                systemPrompt = AppSettings.DEFAULT_PRESET,
                postHistory = "【风格要求】更大胆地展开场景与情感，允许更主动、更细腻的描写；每轮可以多推进一点情节。",
                temperature = 1.15f,
                topP = 1.0f,
                style = "创意",
                builtIn = true
            ),
            Preset(
                name = "发散",
                systemPrompt = AppSettings.DEFAULT_PRESET,
                postHistory = "【风格要求】剧情推进更跳跃、更有想象力，主动引入意外发展、新角色或新环境，避免平铺直叙。",
                temperature = 1.3f,
                topP = 1.0f,
                style = "发散",
                builtIn = true
            ),
            Preset(
                name = "抗复读",
                systemPrompt = AppSettings.DEFAULT_PRESET,
                postHistory = "【风格要求】避免重复上一轮的句式、动作、意象和情节套路；每轮换一种开场与描写角度，保持新鲜感。",
                temperature = 0.95f,
                topP = 0.9f,
                style = "抗复读",
                builtIn = true
            )
        )
    }
}

/** 一条回复的某个版本（类似 DZMM 的「上一版本 / 下一版本」多版本回复）。 */
data class MessageVariant(
    val content: String,
    val reasoning: String = "",
    val createdAt: Long = 0
)

data class Message(
    val id: Long = 0,
    val chatId: Long,
    val role: String,
    val content: String,
    val reasoning: String = "",
    val createdAt: Long = 0,
    /** 该条消息保存的全部版本；老数据为空时由 content 合成 1 个版本。 */
    val variants: List<MessageVariant> = emptyList(),
    /** 当前显示的版本下标。 */
    val activeVariant: Int = 0,
    /** 本回合的 Prompt 调试信息（JSON，P0-2 使用）。 */
    val debug: String = "",
    /** 本回合真实用量（来自接口 usage，P0-4；旧消息为 0）。 */
    val promptTokens: Int = 0,
    val completionTokens: Int = 0
) {
    val allVariants: List<MessageVariant>
        get() = variants.ifEmpty { listOf(MessageVariant(content, reasoning, createdAt)) }

    val variantCount: Int get() = allVariants.size

    val isMultiVariant: Boolean get() = variantCount > 1

    /** 当前版本内容（content 列始终与当前版本保持一致）。 */
    val displayContent: String
        get() = allVariants.getOrNull(activeVariant)?.content ?: content

    val displayReasoning: String
        get() = allVariants.getOrNull(activeVariant)?.reasoning ?: reasoning
}

data class LibraryItem(
    val card: CharacterCard,
    val chatCount: Int,
    val lastPlayed: Long
)

/**
 * 会话级「长期记忆」：把较早的历史压缩成前情提要。
 * msgUptoId 之前的消息视为已被摘要覆盖，后续请求只用摘要 + 之后的原文。
 */
data class ChatMemory(
    val chatId: Long = 0,
    val summary: String = "",
    val msgUptoId: Long = 0,
    val updatedAt: Long = 0
)

/** 会话检查点：记录「玩到第几条消息」的快照点，可回到该点继续。 */
data class Checkpoint(
    val id: Long = 0,
    val chatId: Long = 0,
    val name: String = "",
    val messageId: Long = 0,
    val createdAt: Long = 0
)

/** 全局会话列表的一条（跨角色卡）。 */
data class ChatListItem(
    val chat: Chat,
    val cardName: String
)

/** 消息搜索结果。 */
data class MessageHit(
    val chatId: Long,
    val chatTitle: String,
    val cardName: String,
    val role: String,
    val content: String,
    val createdAt: Long
)

/** 世界书收藏分组（id=0 表示「未分组」）。 */
data class WorldbookCollection(
    val id: Long = 0,
    val name: String = "",
    val entryCount: Int = 0,
    val createdAt: Long = 0
)

/** 角色卡字段被 AI 改写后检测到的设定冲突。 */
data class FieldConflict(
    val field: String,
    val label: String,
    val issue: String,
    val suggestion: String = ""
)
