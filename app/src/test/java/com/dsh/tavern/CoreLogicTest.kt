package com.dsh.tavern

import com.dsh.tavern.data.AppSettings
import com.dsh.tavern.data.ApiErrorParser
import com.dsh.tavern.data.CardPromptTemplates
import com.dsh.tavern.data.CharacterCard
import com.dsh.tavern.data.JsonCodec
import com.dsh.tavern.data.Message
import com.dsh.tavern.data.ModelCatalog
import com.dsh.tavern.data.PanelOutputParser
import com.dsh.tavern.data.PngCardParser
import com.dsh.tavern.data.PromptBuilder
import com.dsh.tavern.data.SharePack
import com.dsh.tavern.data.SourceImporter
import com.dsh.tavern.data.TokenEstimator
import com.dsh.tavern.data.VariableParser
import com.dsh.tavern.data.WorkshopOptions
import com.dsh.tavern.data.WorldEntry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class CoreLogicTest {

    @Test
    fun fullTextAnalysisHelpersWork() {
        val text = (1..3).joinToString("\n") { "第${it}段内容".repeat(500) }
        val chunks = SourceImporter.splitForAnalysis(text, 2_000)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 2_100 })
        assertEquals(
            text.replace("\n", "").length,
            chunks.joinToString("").replace("\n", "").length
        )

        val extract = CardPromptTemplates.fullTextExtractPrompt(
            "本段原文内容", 2, 5, "章节：3", "保留配角关系"
        )
        assertTrue(extract.contains("2/5"))
        assertTrue(extract.contains("本段原文内容"))
        assertTrue(extract.contains("章节：3"))
        assertTrue(extract.contains("保留配角关系"))

        val cardPrompt = CardPromptTemplates.cardJsonPrompt(
            com.dsh.tavern.data.WorkshopInput(idea = "梗概"),
            "时间点原文", "原作要点", "改编要求", "全书参考：人物关系与世界观"
        )
        assertTrue(cardPrompt.contains("全书参考"))
        assertTrue(cardPrompt.contains("全书参考：人物关系与世界观"))

        val focusExtract = CardPromptTemplates.fullTextExtractPrompt(
            "段", 1, 3, "第一章开头", "", listOf("人物关系", "说话方式")
        )
        assertTrue(focusExtract.contains("重点保留"))
        assertTrue(focusExtract.contains("人物关系"))
        assertTrue(focusExtract.contains("说话方式"))
        val focusCard = CardPromptTemplates.cardJsonPrompt(
            com.dsh.tavern.data.WorkshopInput(idea = "梗概"),
            "原文", "要点", "要求", "全书参考", listOf("外貌外形", "重要经历事件")
        )
        assertTrue(focusCard.contains("重点保留"))
        assertTrue(focusCard.contains("外貌外形"))
        assertTrue(focusCard.contains("重要经历事件"))
    }

    @Test
    fun tokenEstimatorCountsCjk() {
        assertTrue(TokenEstimator.estimate("你好世界") >= 4)
        assertEquals(0, TokenEstimator.estimate(""))
    }

    @Test
    fun worldBookConstantAndTriggered() {
        val card = CharacterCard(
            name = "测试",
            description = "设定",
            personality = "性格",
            scenario = "场景",
            firstMes = "开场"
        )
        val entries = listOf(
            WorldEntry(id = 1, name = "常驻", content = "常驻内容", constant = true, insertionOrder = 0),
            WorldEntry(
                id = 2, name = "足部", content = "足部内容",
                keys = listOf("足", "脚"), constant = false, insertionOrder = 1
            ),
            WorldEntry(
                id = 3, name = "无关", content = "无关内容",
                keys = listOf("飞船"), constant = false, insertionOrder = 2
            )
        )
        val settings = AppSettings(maxTokens = 512, contextLimit = 8192)
        val history = listOf(Message(id = 1, chatId = 1, role = "user", content = "看看你的脚"))
        val built = PromptBuilder.build(card, entries, settings, history)
        val system = built.messages.first().content
        assertTrue(system.contains("常驻内容"))
        assertTrue(system.contains("足部内容"))
        assertFalse(system.contains("无关内容"))
    }

    @Test
    fun jsonEntriesParseAndOrder() {
        val json = JSONObject(
            """{"data":{"name":"卡","description":"d","character_book":{"entries":[
                {"keys":["a","b"],"content":"c","insertion_order":2,"constant":false},
                {"keys":["d"],"content":"e","insertion_order":0,"constant":true}
            ]}}}"""
        )
        val card = JsonCodec.cardFromCardJson(json, json.toString())
        assertEquals("卡", card.name)
        val entries = JsonCodec.extractEntries(json.getJSONObject("data"), json)
        assertEquals(2, entries.size)
        assertEquals(listOf("a", "b"), entries[0].keys)
        assertEquals(2, entries[0].insertionOrder)
        assertTrue(entries[1].constant)
    }

    @Test
    fun pngTextChunkParsing() {
        val json = "{\"spec\":\"chara_card_v2\",\"data\":{\"name\":\"PNGCard\"}}"
        val png = buildPngWithText("chara", json)
        assertTrue(PngCardParser.isPng(png))
        val meta = PngCardParser.extractMetadata(png)
        assertEquals(json, meta["chara"])
    }

    @Test
    fun panelOutputParserTagAndFence() {
        val (cleanTag, jsonTag) = PanelOutputParser.extract("正文\n<panel>{\"options\":[\"A\"]}</panel>")
        assertEquals("正文", cleanTag)
        assertEquals("{\"options\":[\"A\"]}", jsonTag)

        val (cleanFence, jsonFence) = PanelOutputParser.extract("正文\n```panel\n{\"options\":[\"B\"]}\n```")
        assertEquals("正文", cleanFence)
        assertTrue(jsonFence != null && jsonFence.contains("\"B\""))

        val (cleanPlain, jsonPlain) = PanelOutputParser.extract("普通文本，没有面板")
        assertEquals("普通文本，没有面板", cleanPlain)
        assertEquals(null, jsonPlain)

        // 普通代码块不应被当成面板
        val (cleanCode, jsonCode) = PanelOutputParser.extract("代码：\n```kotlin\nval a = 1\n```")
        assertEquals(null, jsonCode)
        assertTrue(cleanCode.contains("val a = 1"))
    }

    @Test
    fun worldbookPositionsAndProbability() {
        val card = CharacterCard(name = "卡", description = "角色描述", firstMes = "开场")
        val entries = listOf(
            WorldEntry(id = 1, name = "前", content = "前内容", constant = true, position = "before_char"),
            WorldEntry(id = 2, name = "后", content = "后内容", constant = true, position = "after_char"),
            WorldEntry(
                id = 3, name = "历史", content = "历史内容", constant = true,
                position = "in_history", depth = 2, insertionOrder = 3
            ),
            WorldEntry(
                id = 4, name = "概率0", content = "不该出现",
                keys = listOf("你好"), constant = false, probability = 0, insertionOrder = 4
            )
        )
        val settings = AppSettings(maxTokens = 256, contextLimit = 8192)
        val history = listOf(Message(id = 1, chatId = 1, role = "user", content = "你好"))
        val built = PromptBuilder.build(card, entries, settings, history)
        val system = built.messages.first().content
        assertTrue(system.contains("前内容"))
        assertTrue(system.contains("后内容"))
        assertTrue(system.indexOf("前内容") < system.indexOf("角色描述"))
        assertTrue(system.indexOf("后内容") > system.indexOf("角色描述"))
        assertFalse(system.contains("不该出现"))
        assertTrue(built.messages.any { it.role == "system" && it.content.contains("历史内容") })
        assertEquals(3, built.activatedCount)
    }

    @Test
    fun worldbookSecondaryKeys() {
        val card = CharacterCard(name = "卡", description = "描述")
        val entries = listOf(
            WorldEntry(
                id = 1, name = "双关键词", content = "双关键词内容",
                keys = listOf("你好"), secondaryKeys = listOf("下雨"),
                constant = false, insertionOrder = 0
            ),
            WorldEntry(
                id = 2, name = "命中", content = "命中的内容",
                keys = listOf("你好"), secondaryKeys = listOf("你好"),
                constant = false, insertionOrder = 1
            )
        )
        val settings = AppSettings(maxTokens = 256, contextLimit = 8192)
        val history = listOf(Message(id = 1, chatId = 1, role = "user", content = "你好"))
        val built = PromptBuilder.build(card, entries, settings, history)
        val system = built.messages.first().content
        assertFalse(system.contains("双关键词内容"))
        assertTrue(system.contains("命中的内容"))
    }

    @Test
    fun variableParserExtractsSetvar() {
        val (clean, updates) = VariableParser.extract(
            "天黑了。\n<setvar name=\"时间\">夜晚</setvar>\n<setvar name=\"好感度\">35</setvar>"
        )
        assertEquals("天黑了。", clean)
        assertEquals("夜晚", updates["时间"])
        assertEquals("35", updates["好感度"])

        val initial = VariableParser.parseInitial("时间=夜晚\n好感度：35\n#注释\n没有等号")
        assertEquals("夜晚", initial["时间"])
        assertEquals("35", initial["好感度"])
        assertEquals(2, initial.size)
    }

    @Test
    fun worldbookRegexTrigger() {
        val card = CharacterCard(name = "卡", description = "描述")
        val entries = listOf(
            WorldEntry(
                id = 1, name = "正则", content = "正则内容",
                keys = listOf("下(雨|雪)"), regex = true,
                constant = false, insertionOrder = 0
            )
        )
        val settings = AppSettings(maxTokens = 256, contextLimit = 8192)
        val hit = PromptBuilder.build(
            card, entries, settings,
            listOf(Message(id = 1, chatId = 1, role = "user", content = "下雨了"))
        )
        assertTrue(hit.messages.first().content.contains("正则内容"))

        val miss = PromptBuilder.build(
            card, entries, settings,
            listOf(Message(id = 1, chatId = 1, role = "user", content = "出门"))
        )
        assertFalse(miss.messages.first().content.contains("正则内容"))
    }

    @Test
    fun globalWorldbookInjectedAndMarked() {
        val card = CharacterCard(name = "卡", description = "描述")
        val global = listOf(WorldEntry(id = 99, name = "全局", content = "全局设定", constant = true))
        val settings = AppSettings(maxTokens = 256, contextLimit = 8192)
        val built = PromptBuilder.build(
            card, emptyList(), settings,
            listOf(Message(id = 1, chatId = 1, role = "user", content = "你好")),
            globalEntries = global
        )
        assertTrue(built.messages.first().content.contains("全局设定"))
        assertTrue(built.debug.world.any { it.name == "全局" && it.source == "全局" })
    }

    @Test
    fun macrosReplacedInCardAndHistory() {
        val card = CharacterCard(
            name = "小雪",
            description = "{{char}}是{{user}}的青梅竹马。",
            personality = "对{{user}}很温柔"
        )
        val settings = AppSettings(personaName = "阿明", maxTokens = 512, contextLimit = 8192)
        val built = PromptBuilder.build(
            card, emptyList(), settings,
            listOf(Message(id = 1, chatId = 1, role = "assistant", content = "{{char}}笑了。"))
        )
        val system = built.messages.first().content
        assertTrue(system.contains("小雪是阿明的青梅竹马"))
        assertTrue(system.contains("对阿明很温柔"))
        assertFalse(system.contains("{{user}}"))
        assertEquals("小雪笑了。", built.messages[1].content)
    }

    @Test
    fun workshopPromptsContainSchemaAndGrading() {
        val input = com.dsh.tavern.data.WorkshopInput(
            idea = "被退婚的女将军在雨夜敲开我的门",
            name = "沈砚",
            gender = "女性",
            world = "玄幻修仙",
            archetype = "冷艳仙子",
            plot = "复仇",
            genre = "古风",
            appearance = "黑长直、泪痣、深色风衣",
            worldbookLevel = "中（12~20 条）",
            titleCount = 3
        )
        val jsonPrompt = CardPromptTemplates.cardJsonPrompt(input)
        assertTrue(jsonPrompt.contains("chara_card_v3"))
        assertTrue(jsonPrompt.contains("character_book"))
        assertTrue(jsonPrompt.contains("T0 核心规则"))
        assertTrue(jsonPrompt.contains("沈砚"))
        assertTrue(jsonPrompt.contains("世界书规模"))
        assertTrue(jsonPrompt.contains("黑长直"))
        assertTrue(jsonPrompt.contains("玄幻修仙"))
        assertTrue(jsonPrompt.contains("冷艳仙子"))
        assertTrue(jsonPrompt.contains("复仇"))
        assertTrue(jsonPrompt.contains("女性"))
        assertTrue(CardPromptTemplates.ideationPrompt(input).contains("标题"))
        assertTrue(CardPromptTemplates.illustrationPrompt(input).contains("提示词"))
        assertTrue(CardPromptTemplates.importGuide().contains("PNG"))
    }

    @Test
    fun sharePackFormatDetection() {
        assertTrue(SharePack.isSharePack("""{"format":"dsh_tavern_share","version":1}"""))
        assertFalse(SharePack.isSharePack("""{"spec":"chara_card_v3","data":{}}"""))
        assertFalse(SharePack.isSharePack("<html>not json</html>"))
        assertEquals("dsh_tavern_share", SharePack.FORMAT)
    }

    @Test
    fun apiErrorParserExtractsSupportedModels() {
        val raw = """{"error":{"message":"The supported API model names are deepseek-flash, deepseek-v4-pro, but you passed deepseek-v4.1-flash. (request_id: a319769b)","type":"invalid_request_error","code":"invalid_request_error"}}"""
        val info = ApiErrorParser.parse(400, raw, "deepseek-v4.1-flash")
        assertEquals(400, info.httpCode)
        assertEquals(listOf("deepseek-flash", "deepseek-v4-pro"), info.supportedModels)
        assertTrue(info.friendly.contains("deepseek-flash"))
        assertTrue(info.friendly.contains("deepseek-v4-pro"))
        assertTrue(info.friendly.contains("deepseek-v4.1-flash"))
        assertEquals(1_000_000, ModelCatalog.specFor("deepseek-flash")?.contextLimit)
        assertEquals(1_000_000, ModelCatalog.specFor("deepseek-v4-pro")?.contextLimit)
        assertTrue(ApiErrorParser.parseSupportedModels("完全无关的报错").isEmpty())
    }

    @Test
    fun apiErrorParserReadsContextLimit() {
        assertEquals(
            65_536,
            ApiErrorParser.parseContextLimit(
                "This model's maximum context length is 65536 tokens. However, you requested 90000 tokens."
            )
        )
        assertEquals(
            1_000_000,
            ApiErrorParser.parseContextLimit("maximum context length is 1000000 tokens")
        )
        assertNull(ApiErrorParser.parseContextLimit("完全无关的报错"))
    }

    @Test
    fun cardImageTranscribePromptIsPlainText() {
        val prompt = CardPromptTemplates.cardImageTranscribePrompt()
        assertTrue(prompt.contains("转录"))
        assertTrue(prompt.contains("只输出转录的文字"))
        assertTrue(prompt.contains("没有识别到文字"))
    }

    @Test
    fun longScreenshotTilesAreSlicedWithOverlap() {
        val ranges = SourceImporter.tileRanges(2844, 1600, overlap = 160, maxTiles = 8)
        assertEquals(2, ranges.size)
        assertEquals(0, ranges[0].first)
        assertEquals(1599, ranges[0].last)
        assertEquals(2843, ranges[1].last)
        assertTrue(ranges[1].first < ranges[0].last) // 有重叠，避免切断文字行

        val tiledPrompt = CardPromptTemplates.cardImageTranscribePrompt(3)
        assertTrue(tiledPrompt.contains("3 张图片"))
        assertTrue(tiledPrompt.contains("切片"))
        assertTrue(tiledPrompt.contains("不要重复"))
    }

    @Test
    fun cardImageVisionPromptHasTranscriptContract() {
        val prompt = CardPromptTemplates.cardImageVisionPrompt(
            "保留黑丝禁欲感", listOf("人物关系", "外貌外形")
        )
        assertTrue(prompt.contains("transcript"))
        assertTrue(prompt.contains("synopsis"))
        assertTrue(prompt.contains("保留黑丝禁欲感"))
        assertTrue(prompt.contains("重点保留"))
        assertTrue(prompt.contains("图片"))
    }

    @Test
    fun reversedChapterRangeAndImageMagicWork() {
        val chapters = (1..10).map { index -> "第${index}章 标题\n" + "内容$index".repeat(100) }
        val reversed = SourceImporter.excerptChapters(chapters, 5, 3, 100_000)
        assertTrue(reversed.contains("第3章"))
        assertTrue(reversed.contains("第5章"))
        assertFalse(reversed.contains("第6章"))

        val png = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 1, 2)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2)
        val json = """{"a":1}""".toByteArray(Charsets.UTF_8)
        assertTrue(SourceImporter.isImageBytes(png))
        assertTrue(SourceImporter.isImageBytes(jpeg))
        assertFalse(SourceImporter.isImageBytes(json))
    }

    @Test
    fun cardIntroExtractPromptHasFields() {
        val prompt = CardPromptTemplates.cardIntroExtractPrompt(
            com.dsh.tavern.data.WorkshopInput(idea = ""),
            "重生七年后，我竟娶了死对头校花。苏沐雪穿着半透明黑丝，把离婚协议放在书房。",
            "保留黑丝禁欲感",
            listOf("人物关系", "外貌外形")
        )
        assertTrue(prompt.contains("苏沐雪"))
        assertTrue(prompt.contains("保留黑丝禁欲感"))
        assertTrue(prompt.contains("重点保留"))
        assertTrue(prompt.contains("synopsis"))
        assertTrue(prompt.contains("extra"))
        assertTrue(prompt.contains("OCR"))
    }

    @Test
    fun chapterAndEventRangeExcerptsWork() {
        val chapters = (1..10).map { index -> "第${index}章 标题\n" + "内容$index".repeat(300) }
        val chapterExcerpt = SourceImporter.excerptChapters(chapters, 2, 4, 100_000)
        assertTrue(chapterExcerpt.contains("第2章"))
        assertTrue(chapterExcerpt.contains("第4章"))
        assertFalse(chapterExcerpt.contains("第5章"))

        val full = chapters.joinToString("\n")
        val eventExcerpt = SourceImporter.excerptBetween(full, "内容3", "内容6", 100_000)
        assertTrue(eventExcerpt.contains("内容3"))
        assertTrue(eventExcerpt.contains("内容6"))
        assertFalse(eventExcerpt.contains("内容7"))

        val sampled = SourceImporter.excerptBetween(full, "内容3", "内容6", 2_000)
        assertTrue(sampled.length <= 3_000)
    }

    @Test
    fun sourceImporterHandlesSpacedChapterTitlesAndSampling() {
        val text = (1..30).joinToString("\n") { index ->
            "第$index 章 标题$index\n" + "正文$index".repeat(120)
        }
        val doc = SourceImporter.parseTxt(text.toByteArray(Charsets.UTF_8), "书.txt")
        assertEquals(30, doc.chapters.size)
        assertTrue(doc.chapters[0].startsWith("第1 章"))

        val longText = "A".repeat(100_000)
        val sample = SourceImporter.sampleForCompression(longText, 10_000)
        assertTrue(sample.length in 9_000..16_000)
        assertTrue(sample.contains("【开头】"))
        assertTrue(sample.contains("【结尾】"))
        assertTrue(sample.contains("采样"))
    }

    @Test
    fun sourceImporterSplitsTxtAndStripsHtml() {
        val text = "第一章 相遇\n" + "a".repeat(50) + "\n第二章 分别\n" + "b".repeat(50)
        val doc = SourceImporter.parseTxt(text.toByteArray(Charsets.UTF_8), "测试.txt")
        assertEquals(2, doc.chapters.size)
        assertTrue(doc.chapters[0].contains("第一章"))
        assertEquals("txt", doc.format)
        assertEquals("hello\nworld", SourceImporter.stripHtml("<p>hello</p><p>world</p>"))
        assertTrue(SourceImporter.stripHtml("<script>x()</script><div>正文</div>").contains("正文"))
        assertFalse(SourceImporter.stripHtml("<script>x()</script>").contains("x()"))
    }

    @Test
    fun sourceRequirementFlowsIntoCardAndAiPrompts() {
        val card = CharacterCard(name = "沈砚", description = "废太子。")
        val entries = listOf(WorldEntry(id = 1, name = "冷宫", content = "雨夜冷宫。"))
        val requirement = "以沈砚为主角，从第三章冷宫开始，梳理配角关系"
        val rewrite = CardPromptTemplates.rewriteCardFieldPrompt(
            card, entries, "description", "详细描述", "更阴沉",
            sourceExcerpt = "原文片段", sourceSummary = "原作要点", sourceRequirement = requirement
        )
        assertTrue(rewrite.contains("原作参考"))
        assertTrue(rewrite.contains(requirement))
        assertTrue(rewrite.contains("原文片段"))

        val worldbook = CardPromptTemplates.generateWorldbookEntryPrompt(
            card, entries, "写一条配角关系",
            sourceExcerpt = "原文片段", sourceSummary = "原作要点", sourceRequirement = requirement
        )
        assertTrue(worldbook.contains("原作参考"))
        assertTrue(worldbook.contains(requirement))
        assertTrue(worldbook.contains("配角"))

        val cardPrompt = CardPromptTemplates.cardJsonPrompt(
            com.dsh.tavern.data.WorkshopInput(idea = "梗概"), "原文片段", "原作要点", requirement
        )
        assertTrue(cardPrompt.contains("原作参考"))
        assertTrue(cardPrompt.contains(requirement))
    }

    @Test
    fun sourceAnalysisAndCardPromptUseExcerpt() {
        val input = com.dsh.tavern.data.WorkshopInput(idea = "梗概", name = "沈砚")
        val analysis = CardPromptTemplates.sourceAnalysisPrompt(
            input, "原文节选内容……", "章节：3", "外貌按原文"
        )
        assertTrue(analysis.contains("原文节选内容"))
        assertTrue(analysis.contains("章节：3"))
        assertTrue(analysis.contains("外貌按原文"))
        assertTrue(analysis.contains("synopsis"))
        assertTrue(analysis.contains("appearance"))

        val card = CardPromptTemplates.cardJsonPrompt(input, "原文节选内容……", "原作要点")
        assertTrue(card.contains("原作参考"))
        assertTrue(card.contains("原文节选内容"))
        assertTrue(card.contains("原作要点"))
        assertTrue(card.contains("避免 OOC"))
    }

    @Test
    fun cardAiPromptsHaveContextAndJsonContract() {
        val card = CharacterCard(
            name = "沈砚",
            description = "被废太子，左眉骨有旧伤。",
            personality = "克制、阴沉。",
            scenario = "雨夜冷宫。",
            firstMes = "门外传来叩门声。",
            systemPrompt = "保持古风。",
            tags = listOf("古风", "权谋")
        )
        val entries = listOf(
            WorldEntry(id = 1, name = "旧伤来历", content = "三年前替人挡箭。", constant = true)
        )
        val rewrite = CardPromptTemplates.rewriteCardFieldPrompt(
            card, entries, "scenario", "场景", "更压抑一点"
        )
        assertTrue(rewrite.contains("更压抑一点"))
        assertTrue(rewrite.contains("沈砚"))
        assertTrue(rewrite.contains("旧伤来历"))
        assertTrue(rewrite.contains("只输出改写后的字段内容"))

        val check = CardPromptTemplates.conflictCheckPrompt(card, entries, "scenario", "新的场景描写")
        assertTrue(check.contains("新的场景描写"))
        assertTrue(check.contains("\"conflicts\""))
        assertTrue(check.contains("suggestion"))

        val gen = CardPromptTemplates.generateWorldbookEntryPrompt(card, entries, "写一条关于亲吻的描写")
        assertTrue(gen.contains("写一条关于亲吻的描写"))
        assertTrue(gen.contains("\"content\""))
        assertTrue(gen.contains("\"keys\""))

        val adapt = CardPromptTemplates.adaptWorldbookEntriesPrompt(
            card, entries, "保留亲吻/舌吻描写，只改冲突部分"
        )
        assertTrue(adapt.contains("保留亲吻/舌吻描写"))
        assertTrue(adapt.contains("旧伤来历"))
        assertTrue(adapt.contains("JSON 数组"))
        assertTrue(adapt.contains("沈砚"))
    }

    @Test
    fun polishAndExtractPromptsKeepUserSynopsis() {
        val input = com.dsh.tavern.data.WorkshopInput(
            idea = "她在雨夜敲开我的门。",
            extra = "必须有猫",
            gender = "女性",
            world = "家庭"
        )
        val polish = CardPromptTemplates.polishSynopsisPrompt(input, "中（200~400 字）")
        assertTrue(polish.contains("她在雨夜敲开我的门"))
        assertTrue(polish.contains("必须有猫"))
        assertTrue(polish.contains("只输出润色后的梗概正文"))
        assertTrue(polish.contains("保留原有核心设定"))
        assertTrue(polish.contains("女性"))
        assertFalse(polish.contains("chara_card_v3"))

        val extract = CardPromptTemplates.extractOptionsPrompt(input)
        assertTrue(extract.contains("\"gender\""))
        assertTrue(extract.contains("玄幻修仙"))
        assertTrue(extract.contains("她在雨夜敲开我的门"))
        assertTrue(extract.contains("appearance"))
    }

    @Test
    fun randomFieldPromptsRespectLockedSettings() {
        val input = com.dsh.tavern.data.WorkshopInput(
            idea = "雨夜，她敲开了我的门",
            gender = "女性",
            world = "家庭",
            archetype = "寡妇老板娘",
            plot = "救赎"
        )
        // 随机「外貌」时，已确定的性别/人设必须作为约束出现在提示词里
        val appearancePrompt = CardPromptTemplates.randomFieldPrompt(
            com.dsh.tavern.data.WorkshopFieldRule.APPEARANCE,
            input
        )
        assertTrue(appearancePrompt.contains("女性"))
        assertTrue(appearancePrompt.contains("寡妇老板娘"))
        assertTrue(appearancePrompt.contains("只随机这一个字段"))
        assertFalse(appearancePrompt.contains("随机「世界观」"))

        // 随机「世界观」时不能把已确定的世界观当约束，但候选池要在
        val worldPrompt = CardPromptTemplates.randomFieldPrompt(
            com.dsh.tavern.data.WorkshopFieldRule.WORLD,
            input
        )
        assertTrue(worldPrompt.contains("现代都市"))
        assertTrue(worldPrompt.contains("玄幻修仙"))
        assertTrue(worldPrompt.contains("必须从以下候选中选一个"))

        // 整组随机：已确定项锁定，输出 JSON 补未设置项
        val bulk = CardPromptTemplates.randomOptionsPrompt(input)
        assertTrue(bulk.contains("已确定的设定"))
        assertTrue(bulk.contains("JSON"))
        assertTrue(bulk.contains("gender"))
        assertTrue(bulk.contains("appearance"))
        assertTrue(bulk.contains("女性"))
        assertTrue(bulk.contains("寡妇老板娘"))
    }

    @Test
    fun synopsisPromptHasLengthAndOptions() {
        val input = com.dsh.tavern.data.WorkshopInput(
            world = "校园",
            archetype = "病娇学姐",
            plot = "暗恋成真",
            nsfw = true
        )
        val prompt = CardPromptTemplates.synopsisPrompt(input, "长（400~600 字）")
        assertTrue(prompt.contains("400~600"))
        assertTrue(prompt.contains("校园"))
        assertTrue(prompt.contains("病娇学姐"))
        assertTrue(prompt.contains("暗恋成真"))
        assertTrue(prompt.contains("只输出梗概正文"))
        assertTrue(CardPromptTemplates.synopsisPrompt(input, "短（100~200 字）").contains("100~200"))
    }

    @Test
    fun workshopPromptHandlesBlankSynopsis() {
        val input = com.dsh.tavern.data.WorkshopInput(
            world = "玄幻修仙",
            archetype = "冷艳仙子",
            plot = "救赎",
            nsfw = true
        )
        val jsonPrompt = CardPromptTemplates.cardJsonPrompt(input)
        assertTrue(jsonPrompt.contains("没有提供故事梗概"))
        assertTrue(jsonPrompt.contains("玄幻修仙"))
        assertTrue(jsonPrompt.contains("冷艳仙子"))
        assertTrue(jsonPrompt.contains("救赎"))
        val ideation = CardPromptTemplates.ideationPrompt(input)
        assertTrue(ideation.contains("没有写故事梗概"))
    }

    @Test
    fun modelCatalogKnowsV41() {
        val spec = ModelCatalog.specFor("deepseek-v4.1-flash-1017")
        assertNotNull(spec)
        assertEquals(1_000_000, spec!!.contextLimit)
        assertEquals(393_216, spec.maxOutput)

        val alias = ModelCatalog.specFor("deepseek-flash")
        assertNotNull(alias)
        assertEquals(1_000_000, alias!!.contextLimit)

        val v3 = ModelCatalog.specFor("deepseek-chat")
        assertNotNull(v3)
        assertEquals(128_000, v3!!.contextLimit)

        assertNull(ModelCatalog.specFor("some-random-model"))
    }

    @Test
    fun minReplyLengthInstructionInjected() {
        val card = CharacterCard(name = "卡", description = "描述")
        val settings = AppSettings(maxTokens = 1024, contextLimit = 8192)
        val built = PromptBuilder.build(
            card, emptyList(), settings,
            listOf(Message(id = 1, chatId = 1, role = "user", content = "你好")),
            minReplyLength = 300
        )
        val system = built.messages.first().content
        assertTrue(system.contains("回复长度要求"))
        assertTrue(system.contains("300"))
    }

    @Test
    fun worldbookJsonRoundtrip() {        val entry = WorldEntry(
            name = "条目",
            keys = listOf("a", "b"),
            secondaryKeys = listOf("c"),
            content = "内容",
            constant = false,
            position = "in_history",
            probability = 30,
            depth = 2
        )
        val json = JsonCodec.entryToJson(entry)
        val back = JsonCodec.entryFromJson(json, 0)
        assertEquals(entry.keys, back.keys)
        assertEquals(entry.secondaryKeys, back.secondaryKeys)
        assertEquals(entry.position, back.position)
        assertEquals(entry.probability, back.probability)
        assertEquals(entry.depth, back.depth)
        assertEquals(entry.content, back.content)
    }

    private fun buildPngWithText(keyword: String, text: String): ByteArray {
        fun chunk(type: String, data: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            fun writeInt(v: Int) {
                out.write(
                    byteArrayOf(
                        (v ushr 24).toByte(),
                        ((v ushr 16) and 0xFF).toByte(),
                        ((v ushr 8) and 0xFF).toByte(),
                        (v and 0xFF).toByte()
                    )
                )
            }
            writeInt(data.size)
            out.write(type.toByteArray(Charsets.US_ASCII))
            out.write(data)
            writeInt(0) // 假 CRC，解析器不校验
            return out.toByteArray()
        }

        val ihdr = ByteArray(13)
        ihdr[0] = 1
        ihdr[1] = 1
        ihdr[2] = 8
        ihdr[3] = 6
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val textData = keyword.toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf(0) +
            text.toByteArray(Charsets.ISO_8859_1)

        val out = ByteArrayOutputStream()
        out.write(signature)
        out.write(chunk("IHDR", ihdr))
        out.write(chunk("tEXt", textData))
        out.write(chunk("IEND", ByteArray(0)))
        return out.toByteArray()
    }
}
