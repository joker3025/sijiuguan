package com.dsh.tavern.data

/** 角色卡工坊的输入参数（对齐「梗概 + 选项 + 自填」的创作面板）。 */
data class WorkshopInput(
    /** 故事梗概，可只写一段。 */
    val idea: String = "",
    val name: String = "",
    val gender: String = "自动（按梗概判断）",
    val scale: String = "默认（慢热推进，不设限）",
    val world: String = "自动 / 随机",
    val archetype: String = "自动 / 随机",
    val plot: String = "自动 / 随机",
    val priorityBase: Int = 0,
    val placeholder: String = "{{user}} / {{char}}（酒馆标准）",
    val genre: String = "",
    val tone: String = "",
    /** 外貌要点（供卡面与立绘提示词）。 */
    val appearance: String = "",
    val nsfw: Boolean = true,
    val worldbookLevel: String = "中（12~20 条）",
    val titleCount: Int = 3,
    val synopsisLength: String = "中（200~400 字）",
    val extra: String = ""
)

/** 工坊下拉选项与候选池（作为 API 随机的约束）。 */
object WorkshopOptions {

    val genders = listOf("自动（按梗概判断）", "女性", "男性", "随机")
    val scales = listOf(
        "默认（慢热推进，不设限）",
        "成人向直接展开",
        "全年龄"
    )
    val worlds = listOf("自动 / 随机", "现代都市", "古代", "玄幻修仙", "校园", "家庭")
    val archetypes = listOf(
        "自动 / 随机",
        // 女性向
        "冷艳仙子", "傲娇千金", "病娇学姐", "害羞画家", "元气学妹", "寡妇老板娘",
        // 男性向
        "冷面校草", "痞帅学长", "腹黑上司", "病弱公子", "忠犬护卫", "黑道少爷",
        // 通用
        "温柔医生", "冷酷教官", "心机秘书", "落魄贵族", "暗卫", "青梅竹马"
    )
    val plots = listOf(
        "自动 / 随机", "穿越", "契约", "追妻火葬场", "先婚后爱",
        "宿敌变情人", "暗恋成真", "救赎", "身份互换", "系统",
        "失忆", "复仇", "生存"
    )
    val placeholders = listOf(
        "{{user}} / {{char}}（酒馆标准）",
        "{user} / {char}"
    )
    val worldbookLevels = listOf("小（6~10 条）", "中（12~20 条）", "大（20~30 条）")
    val titleCounts = listOf("1 个", "3 个", "5 个")
    val synopsisLengths = listOf(
        "短（100~200 字）",
        "中（200~400 字）",
        "长（400~600 字）"
    )

    fun isUnset(value: String): Boolean =
        value.isBlank() || value == "自动 / 随机" || value == "自动（按梗概判断）" ||
            value == "自动（按梗概/人设决定）"
}

/** 可以由 API 随机生成的字段；options 非空表示必须从候选里选。 */
enum class WorkshopFieldRule(val label: String, val options: List<String>?) {
    GENDER("性别", listOf("女性", "男性")),
    SCALE("尺度", listOf("默认（慢热推进，不设限）", "成人向直接展开", "全年龄")),
    WORLD("世界观", listOf("现代都市", "古代", "玄幻修仙", "校园", "家庭")),
    ARCHETYPE("人设", listOf(
        "冷艳仙子", "傲娇千金", "病娇学姐", "害羞画家", "元气学妹", "寡妇老板娘",
        "冷面校草", "痞帅学长", "腹黑上司", "病弱公子", "忠犬护卫", "黑道少爷",
        "温柔医生", "冷酷教官", "心机秘书", "落魄贵族", "暗卫", "青梅竹马"
    )),
    PLOT("主线", listOf(
        "穿越", "契约", "追妻火葬场", "先婚后爱", "敌变情人", "暗恋成真",
        "救赎", "身份互换", "系统", "失忆", "复仇", "生存"
    )),
    GENRE("题材", null),
    TONE("基调", null),
    APPEARANCE("外貌要点", null),
    NAME("角色名", null),
    PRIORITY("触发词优先级起始", null),
    WORLDBOOK("世界书规模", listOf("小（6~10 条）", "中（12~20 条）", "大（20~30 条）")),
    TITLE_COUNT("候选标题数量", listOf("1 个", "3 个", "5 个"))
}

/**
 * 角色卡工坊的提示词模板：
 * 1) 给任意 AI 的「构思/润色」提示词；
 * 2) 生成 Character Card V3 JSON 的提示词（含分级世界书、标题套路）；
 * 3) 只生成「故事梗概」的提示词（字数可选）；
 * 4) 「API 随机一个字段」的提示词（以已确定的设定为约束）；
 * 5) 立绘/头像的绘图提示词生成器；
 * 6) 导入说明。
 *
 * 所有随机都由 API 生成，不使用内置固定设定池。
 */
object CardPromptTemplates {

    /** 用户提供的 18+ 标题风格参考（仅本地使用，随提示词一起复制）。 */
    val titleStyleExamples = """
1. 不好!做到一半催眠失效，被我压在身下的清冷仙子怒瞪着我!
2. 白天骂我穷酸的清冷校花，晚上却在我妈开的鸡店里等客
3. 兄弟天天在我面前抱怨女友不够骚，真肏了她之后发现这不是挺骚的吗
4. 兄弟的汉服社校花女友来找我拍露点写真套图
5. 那黄色废料入脑的娇气师尊总觉得我想把她肏成哦齁母猪，于是对我处处防备？！
6. 逆徒，既然泄完了，就赶紧滚出为师的卧房……等等！齁哦！？❤️
7. 你根本不想听我讲原生家庭你也是想操我对比不?
8. 霸凌过你的傲慢千金在小巷里被人堵了，混混让你滚去后面排队捡漏
""".trim()

    fun ideationPrompt(input: WorkshopInput): String = buildString {
        append("你是一位资深角色卡策划与网文编辑，擅长把粗糙灵感打磨成可玩性高的角色扮演设定。\n\n")
        append("【我的初步想法】\n")
        append(ideaBlock(input))
        if (input.idea.isBlank()) {
            append("\n我没有写故事梗概：请你严格根据上面的世界观/人设/主线/尺度等选项，")
            append("自行设计一个完整、有钩子的故事背景，不要反问我，直接给方案。\n")
        }
        append("\n\n请先用追问的方式帮我把设定补完整（每轮最多问 5 个最关键的问题），然后输出一份结构化大纲，包含：\n")
        append("1. 一句话卖点（20 字内，制造信息差）；\n")
        append("2. 角色三层设定：表层人设 / 内心欲望与恐惧 / 不愿被发现的秘密；\n")
        append("3. 与「用户」的关系张力：初始关系、隐藏关系、可激化的冲突；\n")
        append("4. 剧情引擎：3 个可反复推进的事件钩子（不是一次性剧情）；\n")
        append("5. 开场白钩子：第一幕的冲突点与未解悬念；\n")
        append("6. 标题候选 ${input.titleCount} 个（8~20 字，不剧透、有反差或禁忌关系）；\n")
        append("7. 世界书分级大纲（T0 常驻规则 / T1 人物地点 / T2 剧情事件 / T3 氛围彩蛋），每级给出条目名称与触发词建议。\n\n")
        append("【标题套路参考（18+，来自用户素材）】\n")
        append(titleStyleExamples)
        append("\n\n标题写法：人物+处境+反转/禁忌，先给冲击画面再留悬念；不要写成剧情概括。\n")
        append("【边界】只做 18 岁以上虚构成人向内容；角色均为成年人；不写未成年性内容。")
    }

    fun cardJsonPrompt(
        input: WorkshopInput,
        sourceExcerpt: String = "",
        sourceSummary: String = "",
        sourceRequirement: String = "",
        sourceDigest: String = "",
        sourceFocus: List<String> = emptyList()
    ): String = buildString {
        append("你是顶级角色卡作者，请生成一张可以直接导入「酒馆/SillyTavern 类」应用的角色卡。\n")
        append("只输出一个 JSON 对象，不要 markdown 代码块、不要解释、不要前后缀。\n\n")
        append("【创作输入】\n")
        append(ideaBlock(input))
        if (input.idea.isBlank()) {
            append("\n注意：用户没有提供故事梗概，请根据上面的世界观/人设/主线/尺度等选项自行设计一个完整、")
            append("可直接开演的故事，不要反问、不要只写设定说明。\n")
        }
        if (sourceExcerpt.isNotBlank() || sourceSummary.isNotBlank() || sourceDigest.isNotBlank()) {
            append("\n【原作参考（同人/改编，必须参考，避免 OOC）】\n")
            if (sourceDigest.isNotBlank()) {
                append("全书参考（来自全文分段分析，人物/关系/世界观/事件）：\n")
                append(sourceDigest.take(9000)).append('\n')
            }
            if (sourceSummary.isNotBlank()) {
                append("原作要点：").append(sourceSummary.take(2000)).append('\n')
            }
            if (sourceExcerpt.isNotBlank()) {
                append("时间节点附近原文：\n").append(sourceExcerpt.take(8000)).append('\n')
            }
            if (sourceRequirement.isNotBlank()) {
                append("用户对原作的改编要求（角色/时间/地点等，必须遵守）：\n")
                append(sourceRequirement.take(1500)).append('\n')
            }
            append("要求：角色的外貌、性格、说话方式、世界观与关系必须与原文一致；")
            append("开场白只能从用户指定的时间节点开始，不要提前写后面的剧情；若与梗概冲突，以原文为准。\n")
            append("请在角色卡里把原作中与主角相关的重要配角关系写清楚（可在 description / 世界书里体现）。\n")
            append(focusBlock(sourceFocus))
        }
        append("\n\n【输出格式：Character Card V3】\n")
        append(
            """{
  "spec": "chara_card_v3",
  "spec_version": "3.0",
  "data": {
    "name": "角色名",
    "description": "详细描述：身份/外貌/背景故事/能力/关系网，300~800字",
    "personality": "性格：用具体行为与反应写，不要形容词堆砌，150~400字",
    "scenario": "场景：具体地点+时间+氛围+角色当前状态，100~300字",
    "first_mes": "开场白：第二人称或角色视角，200~450字，包含动作、环境、对话与钩子",
    "mes_example": "<START>\n{{user}}: 示例用户台词\n{{char}}: 示例角色回复（带动作描写）\n<START>\n{{user}}: 第二组\n{{char}}: 第二组回复",
    "creator_notes": "给用户看的角色卡介绍（AI 不会看到）：概览/背景/玩法建议/注意事项；第一行放候选标题",
    "system_prompt": "系统指令：语言风格、行为模式、互动规则、角色立场；不要替代用户行动",
    "post_history_instructions": "后置指令：写作风格与禁忌（可空字符串）",
    "alternate_greetings": ["备选开场白1（150~300字，不同情境）", "备选开场白2"],
    "tags": ["题材标签", "角色标签", "关系标签"],
    "creator": "AI 协作生成",
    "character_version": "1.0",
    "character_book": {
      "name": "世界书",
      "entries": [
        {
          "keys": ["触发词1", "触发词2", "别名"],
          "content": "条目内容 80~300 字",
          "enabled": true,
          "insertion_order": 0,
          "position": "before_char",
          "constant": true,
          "selective": true,
          "comment": "T0 核心规则"
        }
      ]
    }
  }
}"""
        )
        append("\n\n【世界观与人物必须落实】\n")
        append("- 世界观：").append(input.world).append("；\n")
        append("- 角色人设：").append(input.archetype).append("；\n")
        append("- 主线：").append(input.plot).append("；\n")
        append("- 性别：")
            .append(if (input.gender.contains("自动")) "按人设与世界观决定，全文保持一致" else input.gender)
            .append("；尺度：").append(input.scale).append("；\n")
        append("- 卡内正文使用占位符：").append(input.placeholder).append("（用户=第一位，角色=第二位）；\n")
        append("- 世界书条目的 insertion_order 从 ").append(input.priorityBase).append(" 开始递增。\n")
        append("\n【世界书分级（必须按层级写）】\n")
        append("- T0 核心规则：2~4 条，constant=true，position=before_char，写世界底线/能力体系/称呼规则，不写角色本人设定；\n")
        append("- T1 人物与地点：4~8 条，关键词触发，position=after_char，写配角、组织、关键地点；\n")
        append("- T2 剧情与事件：4~8 条，关键词触发，position=in_history（若支持则设置 depth 2~4），写支线、危机、秘密；\n")
        append("- T3 氛围与彩蛋：3~6 条，低概率/少触发，写气味、声音、传闻、小物件；\n")
        append("- 每条 80~300 字；keys 给 3~6 个（含别名/称呼）；禁止与角色主设定重复；总条数按「${input.worldbookLevel}」。\n\n")
        append("【标题要求】\n")
        append("在 creator_notes 第一行给出 ${input.titleCount} 个候选标题，每个 8~20 字，制造信息差/反差/禁忌关系，不要概括全部剧情。参考风格：\n")
        append(titleStyleExamples)
        append("\n\n【写作要求】\n")
        append("- 所有角色均为成年人；只写 18+ 虚构内容；")
        append(if (input.nsfw) "可包含成人向描写，但要有情境与情感铺垫。\n" else "不写成人内容。\n")
        append("- description 要写到能让人脑内成像：外貌细节、说话习惯、身体语言；\n")
        if (input.appearance.isNotBlank()) {
            append("- 请在 description 中自然融入以下外貌要点：").append(input.appearance).append("；\n")
        }
        append("- personality 必须能被行为验证（遇到 X 会做 Y），不要只写「温柔/高冷」；\n")
        append("- first_mes 要能直接开演：有环境、有动作、有对话、有一个未解问题；\n")
        append("- 避免完美人设，至少给一个缺点或矛盾；避免替用户做决定；\n")
        append("- JSON 字符串内换行用 \\n 转义；不要输出 JSON 注释。\n\n")
        append("【校验】输出前自检：JSON 可解析、字段齐全、世界观/人设/主线落实、世界书分级齐全、标题数量正确。")
    }

    /** 只生成「故事梗概」的提示词（给工坊的「API 随机梗概」用，字数可选）。 */
    fun synopsisPrompt(input: WorkshopInput, lengthLabel: String): String = buildString {
        append("请为一张角色扮演角色卡设计「故事梗概」。只输出梗概正文：不要标题、不要解释、不要 JSON、不要分点。\n\n")
        append("【创作条件】\n")
        append(ideaBlock(input))
        if (input.idea.isNotBlank()) {
            append("- 用户已写的梗概（可在此基础上改写或扩写）：").append(input.idea).append('\n')
        }
        append("\n【要求】\n")
        append("1. 字数：").append(lengthLabel).append("（按这个范围写，宁可略长不要过短）；\n")
        append("2. 用 2~4 段：先给有画面感的场景，再交代角色处境与隐藏矛盾，最后留一个钩子；\n")
        append("3. 必须体现上面的世界观、人设、主线、尺度；角色均为成年人；\n")
        append("4. 不要写角色卡字段、不要写开场白、不要写「以下是梗概」这类话；\n")
        append("5. 梗概用第三人称或第二人称叙述均可，不要替用户行动。")
    }

    /** 单独随机一个字段：已确定的其他字段作为约束，不允许推翻。 */
    fun randomFieldPrompt(field: WorkshopFieldRule, input: WorkshopInput): String = buildString {
        append("你在为一张角色卡做设定。请只随机「").append(field.label).append("」这一个字段。\n\n")
        append("【已确定的设定（必须保持不变，作为约束）】\n")
        append(randomContext(input, exclude = field))
        append("\n【只随机这一个字段】\n")
        when (field) {
            WorkshopFieldRule.NAME ->
                append("请起一个 2~4 字的中文角色名（可复姓），与世界观/人设/性别协调，不要与上下文重名。只输出名字本身，不要解释。")
            WorkshopFieldRule.GENRE ->
                append("请给一个「A/B」两段式的题材标签（如 都市/悬疑、古风/权谋、仙侠/复仇），必须与已定世界观/基调协调。只输出标签本身。")
            WorkshopFieldRule.TONE ->
                append("请用 2~4 个中文词概括基调，词之间用顿号分隔（如 冷峻、克制、暗流），必须与世界观/人设协调。只输出词组。")
            WorkshopFieldRule.APPEARANCE ->
                append("请写 20~40 字的外貌要点（发型发色/面部特征/服装/标志性细节），必须符合已定性别与人设。只输出描述本身。")
            WorkshopFieldRule.PRIORITY ->
                append("请给一个 0~20 之间的整数，用于世界书触发词优先级。只输出数字。")
            else -> {
                val options = field.options.orEmpty()
                append("必须从以下候选中选一个（原样输出，不要解释）：\n")
                append(options.joinToString("\n") { "- $it" })
            }
        }
        append("\n\n如果和已确定设定冲突，以已确定设定为准。")
    }

    /** 整组随机：只补当前未设置的字段，已设置的字段保持不变。输出 JSON。 */
    fun randomOptionsPrompt(input: WorkshopInput): String = buildString {
        append("你在为一张角色扮演角色卡做设定。请随机补齐下面「未确定」的字段，已确定的字段必须保持不变。\n\n")
        append("【已确定的设定（不可改动）】\n")
        val locked = buildList {
            if (input.idea.isNotBlank()) add("故事梗概：${input.idea.take(300)}")
            if (input.name.isNotBlank()) add("角色名：${input.name}")
            if (!WorkshopOptions.isUnset(input.gender)) add("性别：${input.gender}")
            if (!WorkshopOptions.isUnset(input.world)) add("世界观：${input.world}")
            if (!WorkshopOptions.isUnset(input.archetype)) add("人设：${input.archetype}")
            if (!WorkshopOptions.isUnset(input.plot)) add("主线：${input.plot}")
            if (input.scale.isNotBlank()) add("尺度：${input.scale}")
            if (input.genre.isNotBlank()) add("题材：${input.genre}")
            if (input.tone.isNotBlank()) add("基调：${input.tone}")
            if (input.appearance.isNotBlank()) add("外貌要点：${input.appearance}")
        }
        if (locked.isEmpty()) append("（无，全部可自由设计）\n") else locked.forEach { append("- ").append(it).append('\n') }
        append("\n【需要你随机的字段与候选】\n")
        append("- gender：女性 或 男性\n")
        append("- world：").append(WorkshopFieldRule.WORLD.options.orEmpty().joinToString(" / ")).append('\n')
        append("- archetype：").append(WorkshopFieldRule.ARCHETYPE.options.orEmpty().joinToString(" / ")).append('\n')
        append("- plot：").append(WorkshopFieldRule.PLOT.options.orEmpty().joinToString(" / ")).append('\n')
        append("- scale：").append(WorkshopFieldRule.SCALE.options.orEmpty().joinToString(" / ")).append('\n')
        append("- genre：A/B 两段式题材标签（自由生成，与世界观协调）\n")
        append("- tone：2~4 个词概括基调（自由生成）\n")
        append("- appearance：20~40 字外貌要点（自由生成，符合性别与人设）\n")
        append("- name：2~4 字中文角色名（自由生成，若角色名已确定则不要输出）\n")
        append("\n【输出格式】只输出一个 JSON 对象，不要 markdown。键名：gender/world/archetype/plot/scale/genre/tone/appearance/name。")
        append("\n只输出你随机出的字段；已确定字段不要出现在 JSON 里。所有角色均为成年人。")
    }

    /** 润色用户自己写的梗概：保留设定，只改文笔/节奏/钩子；可反复调用。 */
    fun polishSynopsisPrompt(input: WorkshopInput, lengthLabel: String): String = buildString {
        append("请润色下面这段「用户自己写的」故事梗概。只输出润色后的梗概正文：")
        append("不要标题、不要解释、不要 JSON、不要分点。\n\n")
        append("【用户原梗概】\n").append(input.idea.trim()).append("\n\n")
        append("【已确定的设定（必须保留，不要改动设定）】\n")
        val context = randomContext(input, exclude = null)
        append(if (context.isBlank()) "（无）\n" else context)
        if (input.extra.isNotBlank()) {
            append("- 用户特殊要求：").append(input.extra).append('\n')
        }
        append("\n【润色要求】\n")
        append("1. 保留原有核心设定、人物关系、主线与结局倾向，不要另起炉灶；\n")
        append("2. 增强画面感与钩子：场景具体、动作与对话自然、结尾留悬念；\n")
        append("3. 修正逻辑与重复，统一人称与语气；\n")
        append("4. 目标字数：").append(lengthLabel).append("（可扩写或精简，不要偏离太多）；\n")
        append("5. 角色均为成年人；遵守用户特殊要求与内容分级；\n")
        append("6. 不要写角色卡字段、不要写开场白、不要出现「以下是润色」之类的话。")
    }

    /** 从梗概里提取/推断选项（只填选项，不生成角色卡）。 */
    fun extractOptionsPrompt(input: WorkshopInput): String = buildString {
        append("请阅读下面这段故事梗概，提取/推断角色卡选项。只输出一个 JSON 对象，不要 markdown、不要解释。\n\n")
        append("【故事梗概】\n").append(input.idea.trim()).append("\n\n")
        if (input.extra.isNotBlank()) {
            append("【用户特殊要求】\n").append(input.extra).append("\n\n")
        }
        append("【当前已选（仅作参考；若与梗概冲突，以梗概为准）】\n")
        append("- 性别：").append(input.gender).append('\n')
        append("- 世界观：").append(input.world).append('\n')
        append("- 人设：").append(input.archetype).append('\n')
        append("- 主线：").append(input.plot).append('\n')
        append("- 尺度：").append(input.scale).append('\n')
        append("\n【字段与候选】\n")
        append("- gender：女性 或 男性\n")
        append("- world：").append(WorkshopFieldRule.WORLD.options.orEmpty().joinToString(" / ")).append('\n')
        append("- archetype：").append(WorkshopFieldRule.ARCHETYPE.options.orEmpty().joinToString(" / ")).append('\n')
        append("- plot：").append(WorkshopFieldRule.PLOT.options.orEmpty().joinToString(" / ")).append('\n')
        append("- scale：").append(WorkshopFieldRule.SCALE.options.orEmpty().joinToString(" / ")).append('\n')
        append("- genre：A/B 两段式题材标签（如 都市/悬疑）\n")
        append("- tone：2~4 个中文词概括基调，用顿号分隔\n")
        append("- appearance：20~40 字外貌要点\n")
        append("\n【输出格式】只输出 JSON：{\"gender\":\"女性\",\"world\":\"...\",\"archetype\":\"...\",")
        append("\"plot\":\"...\",\"scale\":\"...\",\"genre\":\"...\",\"tone\":\"...\",\"appearance\":\"...\"}")
    }

    // ---------------- 卡片字段 AI 改写 / 冲突检查 ----------------

    /** 只改写角色卡的某一个字段；其他字段作为一致性约束；可带原作参考。 */
    fun rewriteCardFieldPrompt(
        card: CharacterCard,
        entries: List<WorldEntry>,
        fieldKey: String,
        fieldLabel: String,
        requirement: String,
        sourceExcerpt: String = "",
        sourceSummary: String = "",
        sourceRequirement: String = "",
        sourceDigest: String = "",
        sourceFocus: List<String> = emptyList()
    ): String = buildString {
        append("你在编辑一张角色卡，请只改写「").append(fieldLabel).append("」这个字段。\n\n")
        append("【当前完整卡片（作为一致性约束）】\n")
        append(cardSummary(card, entries))
        append("\n【该字段原内容】\n")
        append(fieldValue(card, fieldKey).ifBlank { "（空）" }).append("\n\n")
        append("【用户要求】\n")
        append(requirement.ifBlank { "在保持设定一致的前提下润色并加强这个字段" }).append("\n\n")
        if (sourceSummary.isNotBlank() || sourceExcerpt.isNotBlank() ||
            sourceRequirement.isNotBlank() || sourceDigest.isNotBlank()
        ) {
            append("【原作参考（同人/改编，必须保持一致）】\n")
            if (sourceDigest.isNotBlank()) append("全书参考：\n").append(sourceDigest.take(6000)).append('\n')
            if (sourceSummary.isNotBlank()) append("原作要点：").append(sourceSummary.take(1500)).append('\n')
            if (sourceExcerpt.isNotBlank()) append("时间节点附近原文：\n").append(sourceExcerpt.take(4000)).append('\n')
            if (sourceRequirement.isNotBlank()) {
                append("用户对原作的改编要求：").append(sourceRequirement.take(800)).append('\n')
            }
            append(focusBlock(sourceFocus))
            append("改写必须符合原文的人物性格、说话方式与关系，不要 OOC。\n\n")
        }
        append("【输出要求】只输出改写后的字段内容本身：纯文本，不要 JSON、不要标题、不要解释。\n")
        append("必须与其他字段（世界观、人设、主线、开场白、世界书）保持一致；如用户要求与设定冲突，以用户要求为准，但要让改写结果内部自洽。\n")
        append("角色均为成年人；只写 18+ 虚构成人向内容允许范围内的表达。")
    }

    /** 检查刚改写的字段是否与卡片其他部分冲突，返回冲突列表 JSON。 */
    fun conflictCheckPrompt(
        card: CharacterCard,
        entries: List<WorldEntry>,
        changedFieldKey: String,
        changedValue: String
    ): String = buildString {
        append("下面是一张角色卡。刚刚改写了「").append(fieldLabelOf(changedFieldKey)).append("」为：\n")
        append(changedValue.take(2000)).append("\n\n")
        append("【整张卡其他内容（作为对照）】\n")
        append(cardSummary(card, entries))
        append("\n请检查这次改写是否与其他字段或世界书产生设定冲突")
        append("（身份、时间线、能力、年龄、称呼、关系、语气、世界观规则等）。\n")
        append("只输出 JSON：{\"conflicts\":[{\"field\":\"字段键\",\"label\":\"字段名\",")
        append("\"issue\":\"冲突说明\",\"suggestion\":\"该字段修正后的完整新内容\"}]}\n")
        append("字段键可用：name/description/personality/scenario/first_mes/system_prompt/")
        append("post_history_instructions/mes_example/tags。没有冲突就输出 {\"conflicts\":[]}。不要解释。")
    }

    /** 按用户要求生成一条世界书条目，输出 JSON；可带原作参考。 */
    fun generateWorldbookEntryPrompt(
        card: CharacterCard,
        entries: List<WorldEntry>,
        requirement: String,
        sourceExcerpt: String = "",
        sourceSummary: String = "",
        sourceRequirement: String = "",
        sourceDigest: String = "",
        sourceFocus: List<String> = emptyList()
    ): String = buildString {
        append("你在为一张角色卡写「世界书」条目。\n\n")
        append("【角色卡设定】\n").append(cardSummary(card, entries)).append("\n\n")
        append("【用户要求】\n").append(requirement).append("\n\n")
        if (sourceSummary.isNotBlank() || sourceExcerpt.isNotBlank() ||
            sourceRequirement.isNotBlank() || sourceDigest.isNotBlank()
        ) {
            append("【原作参考（同人/改编，必须保持一致）】\n")
            if (sourceDigest.isNotBlank()) append("全书参考：\n").append(sourceDigest.take(6000)).append('\n')
            if (sourceSummary.isNotBlank()) append("原作要点：").append(sourceSummary.take(1500)).append('\n')
            if (sourceExcerpt.isNotBlank()) append("时间节点附近原文：\n").append(sourceExcerpt.take(4000)).append('\n')
            if (sourceRequirement.isNotBlank()) {
                append("用户对原作的改编要求：").append(sourceRequirement.take(800)).append('\n')
            }
            append(focusBlock(sourceFocus))
            append("世界书内容必须符合原文设定；可以写原作里的配角、组织、地点与关系，帮助沉浸式游玩。\n\n")
        }
        append("请只输出一个 JSON 对象，不要 markdown、不要解释：\n")
        append("{\"name\":\"条目标题\",\"keys\":[\"触发词1\",\"触发词2\",\"别名\"],")
        append("\"content\":\"条目内容，80~300 字，具体、可执行、不与角色主设定重复\",")
        append("\"constant\":false,\"position\":\"after_char\",\"probability\":100}\n")
        append("要求：keys 3~6 个（含用户指定的触发词/称呼）；position 只能是 ")
        append("before_char / after_char / after_system / in_history / example_end；")
        append("常驻核心规则用 constant=true、position=before_char；角色均为成年人。")
    }

    /** 把收藏的世界书条目适配到另一张卡；保留用户明确要求保留的描写。 */
    fun adaptWorldbookEntriesPrompt(
        card: CharacterCard,
        sourceEntries: List<WorldEntry>,
        requirement: String
    ): String = buildString {
        append("你在把一套世界书条目适配到一张新的角色卡上。\n\n")
        append("【目标角色卡】\n").append(cardSummary(card, emptyList())).append("\n\n")
        append("【待适配的世界书条目（JSON）】\n")
        val arr = org.json.JSONArray()
        sourceEntries.forEach { arr.put(JsonCodec.entryToJson(it)) }
        append(arr.toString())
        append("\n\n【用户要求（必须尽量保留）】\n")
        append(requirement.ifBlank { "保留原有描写风格与细节，只修正与目标角色卡设定冲突的部分" })
        append("\n\n【要求】\n")
        append("1. 保留原条目的 keys、结构与描写风格（用户点名要保留的亲密/具体描写必须保留）；\n")
        append("2. 只调整与目标卡冲突的身份、称呼、关系、世界观规则；\n")
        append("3. content 每条 80~300 字；不要与目标卡主设定重复；\n")
        append("4. 只输出一个 JSON 数组，元素格式与输入条目相同（name/keys/content/constant/")
        append("position/probability 必填，enabled 默认 true）；不要 markdown、不要解释。\n")
        append("角色均为成年人。")
    }

    fun fieldLabelOf(key: String): String = when (key) {
        "name" -> "角色名"
        "description" -> "详细描述"
        "personality" -> "性格"
        "scenario" -> "场景"
        "first_mes" -> "开场白"
        "system_prompt" -> "系统指令"
        "post_history_instructions" -> "作者注释"
        "mes_example" -> "示例对话"
        "tags" -> "标签"
        else -> key
    }

    fun fieldValue(card: CharacterCard, key: String): String = when (key) {
        "name" -> card.name
        "description" -> card.description
        "personality" -> card.personality
        "scenario" -> card.scenario
        "first_mes" -> card.firstMes
        "system_prompt" -> card.systemPrompt
        "post_history_instructions" -> card.postHistoryInstructions
        "mes_example" -> card.mesExample
        "tags" -> card.tags.joinToString("、")
        "alternate_greetings" -> card.alternateGreetings.joinToString("\n---\n")
        "creator_notes" -> card.creatorNotes
        "suggested_replies" -> card.suggestedReplies.joinToString("\n")
        else -> ""
    }

    private fun cardSummary(card: CharacterCard, entries: List<WorldEntry>): String = buildString {
        append("- 角色名：").append(card.name).append('\n')
        append("- 详细描述：").append(card.description.take(1200)).append('\n')
        append("- 性格：").append(card.personality.take(600)).append('\n')
        append("- 场景：").append(card.scenario.take(400)).append('\n')
        append("- 开场白：").append(card.firstMes.take(400)).append('\n')
        append("- 系统指令：").append(card.systemPrompt.take(500)).append('\n')
        if (card.tags.isNotEmpty()) append("- 标签：").append(card.tags.joinToString("、")).append('\n')
        if (entries.isNotEmpty()) {
            append("- 世界书摘要：\n")
            entries.take(20).forEach { entry ->
                append("  · ").append(entry.name.ifBlank { "条目" }).append("：")
                    .append(entry.content.take(120)).append('\n')
            }
        }
    }

    /** 重点保留项 → 提示词段落。 */
    fun focusBlock(focus: List<String>): String {
        val cleaned = focus.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return ""
        return buildString {
            append("【重点保留（用户勾选，必须优先提取/体现）】\n")
            append("· ").append(cleaned.joinToString("、")).append('\n')
            append(
                "请优先、具体地提取这些方面的内容（人物关系写清关系与称呼；外貌写具体特征；" +
                    "性格写行为反应；重要经历写关键事件与结果；说话方式写语气/口头禅/称呼习惯）。\n\n"
            )
        }
    }

    /** 全文分段分析：从第 i/N 段原文提取资料要点（不续写、不编造）。 */
    fun fullTextExtractPrompt(
        chunk: String,
        index: Int,
        total: Int,
        timePoint: String,
        requirement: String,
        focus: List<String> = emptyList()
    ): String = buildString {
        append("你在为小说/同人改编整理资料。下面是原文第 ").append(index).append("/").append(total)
        append(" 段（全文分段分析）。请提取这一段里与角色设定相关的资料。\n\n")
        append("【目标时间节点】").append(timePoint).append('\n')
        if (requirement.isNotBlank()) append("【用户要求】").append(requirement.take(600)).append('\n')
        append(focusBlock(focus))
        append("\n【本段原文】\n").append(chunk.take(80_000)).append("\n\n")
        append("只输出简洁要点（不要续写、不要编造、不要评价），尽量覆盖：\n")
        append("1. 出场人物：名字/称呼、外貌、性格、说话方式、身份；\n")
        append("2. 人物关系与称呼（含配角、组织、家族）；\n")
        append("3. 世界观规则、地点、势力、时间线关键事件；\n")
        append("4. 与用户要求/时间节点相关的细节。\n")
        append("格式：每行一条，最多 30 行；原文没有的不要写。")
    }

    /** 从同人/小说原文里提取设定：按时间节点生成梗概 + 人设 + 外貌等。 */
    fun sourceAnalysisPrompt(
        input: WorkshopInput,
        sourceExcerpt: String,
        timePoint: String,
        requirement: String,
        focus: List<String> = emptyList(),
        includeDigest: Boolean = false
    ): String = buildString {
        append("你在把一段小说/同人原文改编成角色扮演角色卡。请**只依据下面的原文节选**提取设定，不要编造原文没有的事实。\n\n")
        append("【时间节点（角色卡从这个进度开始）】\n").append(timePoint).append("\n\n")
        append(focusBlock(focus))
        append("【原文节选（可能被截断）】\n").append(sourceExcerpt.take(40000)).append("\n\n")
        if (requirement.isNotBlank()) {
            append("【用户要求（角色 / 时间 / 地点 / 改编要求，必须遵守）】\n").append(requirement).append("\n\n")
        }
        append("【输出】只输出一个 JSON 对象，不要 markdown、不要解释：\n")
        append("{\"synopsis\":\"在这个时间点的故事梗概（200~400字，承接原文前情，不剧透后面剧情）\",")
        append("\"name\":\"角色名（原文有明确名字就用原名；用户指定了角色就用指定角色）\",\"gender\":\"女性/男性\",")
        append("\"world\":\"现代都市/古代/玄幻修仙/校园/家庭 之一（按原文判断）\",")
        append("\"archetype\":\"冷艳仙子/傲娇千金/病娇学姐/害羞画家/元气学妹/寡妇老板娘/冷面校草/痞帅学长/腹黑上司/病弱公子/忠犬护卫/黑道少爷/温柔医生/冷酷教官/心机秘书/落魄贵族/暗卫/青梅竹马 之一（最接近原文的）\",")
        append("\"plot\":\"穿越/契约/追妻火葬场/先婚后爱/宿敌变情人/暗恋成真/救赎/身份互换/系统/失忆/复仇/生存 之一（按原文走向）\",")
        append("\"genre\":\"A/B 两段式题材标签\",\"tone\":\"2~4 个词概括基调\",")
        append("\"appearance\":\"20~40 字外貌要点，必须与原文描写一致\",")
        if (includeDigest) {
            append("\"digest\":\"全书重点压缩（800~1500字）：人物关系与称呼、外貌外形、性格特点、重要经历事件、说话方式、世界观规则与地点势力；只写原文有依据的内容\",")
        }
        append("\"summary\":\"120~240 字原作要点：当前时间点的主要人物关系（含配角、组织、地点）、关键事件与局势，供后续生成角色卡与世界书参考\"}\n\n")
        append("要求：外貌、性格、说话方式以原文为准；synopsis 从指定时间节点开始；")
        append("如果用户指定了角色/时间/地点，严格按用户指定来；如果用户要求改变某些设定，以用户要求为准但保持原文人物内核。角色均为成年人。")
        if (includeDigest) {
            append("节选是「均匀采样」的，可能不连续；digest 要基于采样内容归纳，不确定的不要编造。")
        }
    }

    /** 从「别人角色卡介绍 / 截图 OCR 文字」提取可用设定并润色，输出 JSON。 */
    fun cardIntroExtractPrompt(
        input: WorkshopInput,
        introText: String,
        requirement: String = "",
        focus: List<String> = emptyList()
    ): String = buildString {
        append("下面是一段别人写的角色卡介绍（可能是截图 OCR 出来的文字，可能有错别字/断行）。\n\n")
        append("【介绍原文】\n").append(introText.take(12_000)).append("\n\n")
        if (requirement.isNotBlank()) {
            append("【用户要求】\n").append(requirement.take(800)).append("\n\n")
        }
        append(focusBlock(focus))
        append("【任务】\n")
        append("1. 先修正 OCR 错别字、补全断句；\n")
        append("2. 提取能直接用的设定：角色名、性别、世界观、人设原型、主线、题材、基调、外貌、性格、与{{user}}的关系、开局处境；\n")
        append("3. 把介绍里的剧情润色成一段 200~400 字的「故事梗概」（作为角色卡开场前的局势，不要写成小说章节）；\n")
        append("4. 删掉广告/求关注/平台信息等无关内容；介绍没写的设定不要编造，留给后续生成补全。\n\n")
        append("【输出】只输出一个 JSON 对象，不要 markdown、不要解释：\n")
        append("{\"synopsis\":\"润色后的故事梗概（200~400字）\",")
        append("\"name\":\"角色名（介绍里的角色；没有就用主角名）\",\"gender\":\"女性/男性\",")
        append("\"world\":\"现代都市/古代/玄幻修仙/校园/家庭 之一\",")
        append("\"archetype\":\"冷艳仙子/傲娇千金/病娇学姐/害羞画家/元气学妹/寡妇老板娘/冷面校草/痞帅学长/腹黑上司/病弱公子/忠犬护卫/黑道少爷/温柔医生/冷酷教官/心机秘书/落魄贵族/暗卫/青梅竹马 之一\",")
        append("\"plot\":\"穿越/契约/追妻火葬场/先婚后爱/宿敌变情人/暗恋成真/救赎/身份互换/系统/失忆/复仇/生存 之一\",")
        append("\"genre\":\"A/B 两段式题材标签\",\"tone\":\"2~4 个词概括基调\",")
        append("\"appearance\":\"20~40 字外貌要点（按介绍里的描写）\",")
        append("\"extra\":\"补充说明：介绍里提到但上面字段装不下的设定（用户关系/开局/关键道具/禁忌等）\",")
        append("\"summary\":\"80~160 字：这份介绍的关键信息与可能缺的设定\"}\n")
        append("角色均为成年人。")
    }

    /** 视觉模型第一步：只做文字转录（不要求 JSON，最稳）；tileCount>1 表示长截图切片。 */
    fun cardImageTranscribePrompt(tileCount: Int = 1): String = buildString {
        if (tileCount > 1) {
            append("下面有 ").append(tileCount).append(" 张图片，它们是同一张长截图从上到下的切片，")
            append("相邻图片底部和顶部有一小段重叠。请按顺序转录所有文字，重叠的部分不要重复。\n\n")
        } else {
            append("请把这张图片里所有可见文字按从上到下的顺序转录出来。\n\n")
        }
        append("要求：\n")
        append("1. 保留原有段落与换行；\n")
        append("2. 删掉顶部的手机状态栏（时间/电量/信号）、广告、求关注、平台按钮、点赞数等无关文字；\n")
        append("3. 只输出转录的文字，不要解释、不要总结、不要 markdown 代码块；\n")
        append("4. 如果图片里根本没有可读文字，只输出：没有识别到文字。")
    }

    /** 视觉模型读图：转录截图文案 + 提取设定/润色梗概。 */
    fun cardImageVisionPrompt(
        requirement: String = "",
        focus: List<String> = emptyList()
    ): String = buildString {
        append("这张图片是别人写的角色卡介绍（可能带一点对话截图）。请先看图，再完成任务。\n\n")
        append(focusBlock(focus))
        if (requirement.isNotBlank()) {
            append("【用户要求】\n").append(requirement.take(800)).append("\n\n")
        }
        append("【任务】\n")
        append("1. 逐字转录图片里所有可见文字（保留段落，删掉广告/求关注/平台按钮等无关内容），放进 transcript；\n")
        append("2. 提取能直接用的设定：角色名、性别、世界观、人设原型、主线、题材、基调、外貌、关系、开局处境；\n")
        append("3. 把介绍剧情润色成 200~400 字梗概；图片里没写的设定不要编造；\n\n")
        append("【输出】只输出一个 JSON 对象，不要 markdown、不要解释：\n")
        append("{\"transcript\":\"转录的原文\",\"synopsis\":\"润色后的故事梗概\",")
        append("\"name\":\"角色名\",\"gender\":\"女性/男性\",")
        append("\"world\":\"现代都市/古代/玄幻修仙/校园/家庭 之一\",")
        append("\"archetype\":\"冷艳仙子/傲娇千金/病娇学姐/害羞画家/元气学妹/寡妇老板娘/冷面校草/痞帅学长/腹黑上司/病弱公子/忠犬护卫/黑道少爷/温柔医生/冷酷教官/心机秘书/落魄贵族/暗卫/青梅竹马 之一\",")
        append("\"plot\":\"穿越/契约/追妻火葬场/先婚后爱/宿敌变情人/暗恋成真/救赎/身份互换/系统/失忆/复仇/生存 之一\",")
        append("\"genre\":\"A/B 两段式题材标签\",\"tone\":\"2~4 个词概括基调\",")
        append("\"appearance\":\"20~40 字外貌要点\",")
        append("\"extra\":\"介绍里提到但字段装不下的设定\",")
        append("\"summary\":\"80~160 字：介绍的关键信息与可能缺的设定\"}\n")
        append("角色均为成年人。如果图片不是文字/角色卡介绍，synopsis 写“图片里没有可识别的角色卡介绍”。")
    }

    fun illustrationPrompt(input: WorkshopInput): String = buildString {
        append("请根据下面这张角色卡，写 2 段「给 AI 生图」的提示词（一段中文、一段英文），用于生成角色立绘/头像。\n\n")
        append("【角色信息】\n")
        append(ideaBlock(input))
        append("\n\n要求：\n")
        append("1. 外貌描述具体到：年龄感、脸型、发型发色、瞳色、身材、服装材质与配色、标志性配饰、姿态与表情；\n")
        if (input.appearance.isNotBlank()) {
            append("   以「外貌要点」为准并补全细节：").append(input.appearance).append("；\n")
        }
        append("2. 构图：竖版 3:4 半身像，视线看向镜头，背景简洁但有氛围光；\n")
        append("3. 风格：可给 2 个方向（写实/厚涂二次元）；\n")
        append("4. 英文提示词按「subject, appearance, outfit, pose, lighting, background, style, quality」组织；\n")
        append("5. 附负面提示词（lowres, bad hands, extra fingers, watermark, text 等）；\n")
        append("6. 只写提示词，不要解释。\n")
        append("生成图片后：保存到相册 → 本应用「新建/编辑角色卡」→ 头像 → 选择该图 → 导出 PNG 角色卡（头像会嵌入 PNG）。")
    }

    fun importGuide(): String = """
拿到 AI 的 JSON 后，有三种用法：
1) 直接粘贴到本页下方「粘贴 JSON」→ 解析预览 → 导入；
2) 保存成 .json 文件，用首页「导入角色卡」选择它；
3) 想发给别人/别的酒馆：导入后打开卡片详情 → 导出 → PNG 角色卡（会嵌入 chara/ccv3 元数据和头像）。

为什么推荐 V3 JSON 作为 AI 输出格式？
- 纯文本，任何模型（GPT/豆包/Grok/Claude/DeepSeek）都能稳定生成和修改；
- 字段齐全（含世界书 character_book），导入时不需要你手动搬内容；
- PNG 是分发格式——图片不能直接被 AI「写」出来，所以流程是：AI 产 JSON → 应用导入 → 应用导出 PNG。
""".trim()

    /** 随机单字段时使用的上下文：排除目标字段；只列已确定/已填写的项。 */
    private fun randomContext(input: WorkshopInput, exclude: WorkshopFieldRule?): String = buildString {
        fun add(label: String, value: String, field: WorkshopFieldRule? = null) {
            if (field == exclude) return
            if (value.isBlank() || WorkshopOptions.isUnset(value)) return
            append("- ").append(label).append("：").append(value).append('\n')
        }
        if (input.idea.isNotBlank()) {
            append("- 故事梗概（参考，不要复述）：").append(input.idea.take(500)).append('\n')
        }
        add("角色名", input.name, WorkshopFieldRule.NAME)
        add("性别", input.gender, WorkshopFieldRule.GENDER)
        add("尺度", input.scale, WorkshopFieldRule.SCALE)
        add("世界观", input.world, WorkshopFieldRule.WORLD)
        add("人设", input.archetype, WorkshopFieldRule.ARCHETYPE)
        add("主线", input.plot, WorkshopFieldRule.PLOT)
        add("题材", input.genre, WorkshopFieldRule.GENRE)
        add("基调", input.tone, WorkshopFieldRule.TONE)
        add("外貌要点", input.appearance, WorkshopFieldRule.APPEARANCE)
    }

    private fun ideaBlock(input: WorkshopInput): String = buildString {
        if (input.idea.isNotBlank()) append("- 故事梗概：").append(input.idea).append('\n')
        if (input.name.isNotBlank()) append("- 角色名：").append(input.name).append('\n')
        append("- 性别：").append(
            if (input.gender.contains("自动")) "自动（按梗概/人设决定，全文保持一致）" else input.gender
        ).append('\n')
        append("- 尺度：").append(input.scale).append('\n')
        append("- 世界观：").append(input.world).append('\n')
        append("- 人设：").append(input.archetype).append('\n')
        append("- 主线：").append(input.plot).append('\n')
        append("- 玩家 ID 占位符：").append(input.placeholder).append('\n')
        append("- 触发词优先级起始：").append(input.priorityBase).append('\n')
        if (input.genre.isNotBlank()) append("- 题材/类型：").append(input.genre).append('\n')
        if (input.tone.isNotBlank()) append("- 基调/风格：").append(input.tone).append('\n')
        if (input.appearance.isNotBlank()) append("- 外貌要点：").append(input.appearance).append('\n')
        append("- 内容分级：").append(if (input.nsfw) "18+ 成人向虚构" else "全年龄向").append('\n')
        append("- 世界书规模：").append(input.worldbookLevel).append('\n')
        if (input.extra.isNotBlank()) append("- 额外要求：").append(input.extra).append('\n')
    }
}
