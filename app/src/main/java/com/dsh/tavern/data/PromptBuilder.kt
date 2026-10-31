package com.dsh.tavern.data

object PromptBuilder {

    data class ApiMessage(val role: String, val content: String)

    data class Built(
        val messages: List<ApiMessage>,
        val worldTokens: Int,
        val activatedCount: Int,
        val totalTokens: Int,
        /** P0-2：本次组装的调试快照（段落预算 + 世界书命中 + 最终消息）。 */
        val debug: PromptDebug
    )

    fun build(
        card: CharacterCard,
        entries: List<WorldEntry>,
        settings: AppSettings,
        history: List<Message>,
        /** 预设里的「后置指令」，追加在作者注释之后。 */
        presetPostHistory: String = "",
        /** 长期记忆：更早剧情的压缩摘要（P1-5）。 */
        memorySummary: String = "",
        /** 摘要覆盖到哪条消息为止；之后的历史才随请求发送。 */
        memoryUptoId: Long = 0,
        /** P1-6：互动面板输出格式说明（卡片配置了模板时传入）。 */
        panelInstruction: String = "",
        /** P1-6：当前面板状态 JSON（让模型知道上一轮的状态）。 */
        panelStateJson: String = "",
        /** E1：跨卡共享的全局世界书条目。 */
        globalEntries: List<WorldEntry> = emptyList(),
        /** E2：会话变量（注入系统提示并替换 {{变量名}} 宏）。 */
        variables: Map<String, String> = emptyMap(),
        /** E3：多角色同台时绑定的卡（2~4 张；空 = 普通单卡会话）。 */
        groupCards: List<CharacterCard> = emptyList(),
        /** 最低回复字数（中文字符，0 = 不限制）。 */
        minReplyLength: Int = 0
    ): Built {
        val contextLimit = settings.contextLimit.coerceAtLeast(1024)
        val budgetPercent = settings.worldbookBudgetPercent.coerceIn(5, 60)
        val worldBudget = (contextLimit.toLong() * budgetPercent / 100L).toInt().coerceAtLeast(256)
        val scanDepth = settings.worldbookScanDepth.coerceIn(1, 50)
        val effectiveHistory =
            if (memorySummary.isNotBlank() && memoryUptoId > 0) {
                history.filter { it.id > memoryUptoId }
            } else {
                history
            }
        val recentText = effectiveHistory.takeLast(scanDepth).joinToString("\n") { it.content }
        val seed = effectiveHistory.lastOrNull()?.id ?: 0L

        // Tavern 宏替换：{{char}} / {{user}} 在系统提示、角色卡、世界书、历史里统一生效
        val charName = card.name.ifBlank { "角色" }
        val userName = settings.personaName.ifBlank { "你" }
        fun macro(text: String): String {
            if (text.isEmpty() || !text.contains("{{") && !text.contains("<USER>") && !text.contains("<BOT>")) {
                return text
            }
            return text
                .replace("{{char}}", charName)
                .replace("{{Char}}", charName)
                .replace("<BOT>", charName)
                .replace("{{user}}", userName)
                .replace("{{User}}", userName)
                .replace("{{persona}}", userName)
                .replace("<USER>", userName)
        }

        // ---- 世界书：逐条诊断 + 激活（角色卡条目 + 全局条目） ----
        val hits = mutableListOf<WorldHit>()
        val activated = mutableListOf<WorldEntry>()
        var worldTokens = 0

        val allEntries: List<Pair<WorldEntry, String>> =
            entries.map { it to "" } + globalEntries.map { it to "全局" }

        allEntries.filter { !it.first.enabled }.forEach { (entry, source) ->
            hits.add(worldHit(entry, "", "disabled", "条目已停用", 0, source))
        }
        val sorted = allEntries
            .filter { it.first.enabled }
            .sortedWith(
                compareBy(
                    { it.first.insertionOrder },
                    { it.first.priority },
                    { it.first.id }
                )
            )

        for ((entry, source) in sorted) {
            if (!entry.constant && entry.probability < 100 && !probabilityHit(entry, seed)) {
                hits.add(worldHit(entry, "", "inactive", "条件概率未命中（${entry.probability}%）", 0, source))
                continue
            }
            if (!entry.constant &&
                !matches(entry.keys, entry.caseSensitive, entry.selectiveLogic, recentText, entry.regex)
            ) {
                hits.add(
                    worldHit(
                        entry, "", "inactive",
                        if (entry.keys.isEmpty()) "没有触发词且未开启常驻" else "关键词未命中", 0, source
                    )
                )
                continue
            }
            if (entry.secondaryKeys.isNotEmpty() &&
                !matches(entry.secondaryKeys, entry.caseSensitive, 0, recentText, false)
            ) {
                hits.add(worldHit(entry, "", "inactive", "辅助关键词未命中", 0, source))
                continue
            }
            if (entry.content.isBlank()) {
                hits.add(worldHit(entry, matchedKeys(entry, recentText), "empty", "条目内容为空", 0, source))
                continue
            }
            val tokens = TokenEstimator.estimate(entry.content) + 8
            if (worldTokens + tokens > worldBudget) {
                hits.add(
                    worldHit(
                        entry, matchedKeys(entry, recentText), "budget",
                        "命中了但世界书预算不足（$budgetPercent% / $worldBudget tokens）", tokens, source
                    )
                )
                continue
            }
            activated.add(entry)
            worldTokens += tokens
            hits.add(
                worldHit(
                    entry,
                    if (entry.constant) "（常驻）" else matchedKeys(entry, recentText),
                    "inserted",
                    (if (entry.constant) "常驻条目，始终注入" else "关键词命中") +
                        (if (source.isNotBlank()) " · $source" else "") +
                        if (entry.position.isNotBlank()) " · ${positionLabel(entry.position)}" else "",
                    tokens,
                    source
                )
            )
        }

        val activatedSub = activated.map { it.copy(content = macro(it.content)) }
        fun at(position: String): List<WorldEntry> = activatedSub.filter { it.position == position }

        // ---- 组装系统提示（按 position 插入世界书） ----
        val sb = StringBuilder()
        appendEntries(sb, at("before_system"))
        sb.append(macro(settings.systemPreset))
        if (groupCards.size > 1) {
            sb.append("\n\n【多人场景】你同时扮演以下角色：")
            sb.append(groupCards.joinToString("、") { it.name })
            sb.append("。回复时用【角色名】标记每一段动作/台词；角色之间可以互相对话、行动；")
            sb.append("不要替用户行动或说话，也不要用“作为AI”之类跳出角色。")
            groupCards.forEach { member ->
                sb.append("\n\n【角色：").append(member.name).append("】")
                if (member.description.isNotBlank()) {
                    sb.append("\n").append(macro(member.description).take(1200))
                }
                if (member.personality.isNotBlank()) {
                    sb.append("\n性格：").append(macro(member.personality).take(600))
                }
                if (member.scenario.isNotBlank()) {
                    sb.append("\n初始场景：").append(macro(member.scenario).take(400))
                }
                if (member.firstMes.isNotBlank()) {
                    sb.append("\n开场参考：").append(macro(member.firstMes).take(300))
                }
            }
        } else {
            sb.append("\n\n【角色名（作品标题）】").append(card.name)
            sb.append("\n（注：角色名是作品标题，角色真名以角色设定为准，不要用标题当自称。）")
            appendEntries(sb, at("before_char"))
            if (card.description.isNotBlank()) sb.append("\n\n【角色设定】\n").append(macro(card.description))
            appendEntries(sb, at("after_char"))
            if (card.personality.isNotBlank()) sb.append("\n\n【性格】\n").append(macro(card.personality))
            if (card.scenario.isNotBlank()) sb.append("\n\n【场景】\n").append(macro(card.scenario))
        }
        appendEntries(sb, at("after_system"))
        if (card.systemPrompt.isNotBlank()) {
            sb.append("\n【角色专属系统指令】\n").append(macro(card.systemPrompt))
        }
        sb.append("\n\n【用户】").append(userName)
        if (settings.personaDescription.isNotBlank()) {
            sb.append("：").append(macro(settings.personaDescription))
        }
        if (!settings.nsfwEnabled) {
            sb.append("\n\n【内容限制】本对话不包含成人内容，亲密场景点到为止。")
        }
        appendEntries(sb, at("example_start"))
        if (card.mesExample.isNotBlank()) {
            sb.append("\n\n【对话示例】\n").append(macro(card.mesExample))
        }
        appendEntries(sb, at("example_end"))
        if (card.postHistoryInstructions.isNotBlank()) {
            sb.append("\n\n【作者注释】\n").append(macro(card.postHistoryInstructions))
        }
        if (presetPostHistory.isNotBlank()) {
            sb.append("\n\n【预设指令】\n").append(macro(presetPostHistory))
        }
        if (memorySummary.isNotBlank()) {
            sb.append("\n\n【前情提要（更早剧情，已压缩）】\n").append(macro(memorySummary.trim()))
        }
        if (panelInstruction.isNotBlank()) {
            sb.append("\n\n").append(macro(panelInstruction.trim()))
        }
        if (minReplyLength > 0) {
            sb.append("\n\n【回复长度要求】每次回复正文不少于约 ")
                .append(minReplyLength)
                .append(" 个中文字符；用对话、动作、心理与环境描写把场景展开，")
                .append("不要一两句就结束，也不要复述设定或总结剧情。")
        }
        if (panelStateJson.isNotBlank()) {
            sb.append("\n\n【当前互动面板】\n").append(panelStateJson.trim())
        }
        if (variables.isNotEmpty()) {
            sb.append("\n\n【变量】\n")
            variables.forEach { (name, value) ->
                sb.append("- ").append(name).append(" = ").append(value).append('\n')
            }
            sb.append("如需修改变量，在回复末尾输出 <setvar name=\"变量名\">新值</setvar>，不要解释。")
        }
        appendEntries(sb, at("greeting_end"))
        // 未知 position 兜底
        val known = setOf(
            "before_system", "before_char", "after_char", "after_system",
            "in_history", "example_start", "example_end", "greeting_end"
        )
        activatedSub.filter { it.position !in known }.forEach { appendEntry(sb, it) }

        var system = sb.toString()
        variables.forEach { (name, value) -> system = system.replace("{{$name}}", value) }
        val systemTokens = TokenEstimator.estimate(system)
        var totalTokens = systemTokens + 8
        val limit = contextLimit - settings.maxTokens - 64
        val kept = mutableListOf<ApiMessage>()
        var historyTokens = 0
        for (m in effectiveHistory.asReversed()) {
            if (m.role != "user" && m.role != "assistant") continue
            val t = TokenEstimator.estimate(m.content) + 8
            if (totalTokens + t > limit && kept.isNotEmpty()) break
            kept.add(0, ApiMessage(m.role, macro(m.content)))
            totalTokens += t
            historyTokens += t
        }

        val messages = ArrayList<ApiMessage>(kept.size + 1)
        messages.add(ApiMessage("system", system))
        messages.addAll(kept)

        // position = in_history：按 depth 插入为独立的 system 消息（离对话末尾 depth 层）
        at("in_history")
            .sortedByDescending { it.depth }
            .forEach { entry ->
                val block = "【世界书】\n- ${macro(entry.content).trim()}"
                val index = (messages.size - entry.depth.coerceAtLeast(0)).coerceIn(1, messages.size)
                messages.add(index, ApiMessage("system", block))
            }

        val debug = PromptDebug(
            contextLimit = contextLimit,
            outputReserve = settings.maxTokens,
            systemTokens = (systemTokens - worldTokens).coerceAtLeast(0),
            worldTokens = worldTokens,
            historyTokens = historyTokens,
            totalTokens = totalTokens,
            worldBudget = worldBudget,
            activated = activated.size,
            model = settings.provider.model,
            createdAt = System.currentTimeMillis(),
            world = hits,
            messages = messages.map { DebugMessage(it.role, it.content) }
        )
        return Built(messages, worldTokens, activated.size, totalTokens, debug)
    }

    private fun appendEntries(sb: StringBuilder, entries: List<WorldEntry>) {
        if (entries.isEmpty()) return
        sb.append("\n\n【世界书】\n")
        entries.forEach { appendEntry(sb, it) }
    }

    private fun appendEntry(sb: StringBuilder, entry: WorldEntry) {
        sb.append("- ").append(entry.content.trim()).append('\n')
    }

    private fun worldHit(
        entry: WorldEntry,
        hitKeys: String,
        status: String,
        reason: String,
        tokens: Int,
        source: String = ""
    ): WorldHit = WorldHit(
        name = entry.name,
        keys = entry.keys.joinToString(", "),
        hitKeys = hitKeys,
        status = status,
        reason = reason,
        tokens = tokens,
        position = entry.position,
        order = entry.insertionOrder,
        source = source
    )

    /** 条件启用：按「条目 id + 本轮最后一条消息 id」确定性计算，重生成同一轮结果一致。 */
    private fun probabilityHit(entry: WorldEntry, seed: Long): Boolean {
        if (entry.probability >= 100) return true
        if (entry.probability <= 0) return false
        val mixed = (entry.id * 1_000_003L) xor (seed * 31L)
        val bucket = (mixed % 100L).let { if (it < 0) -it else it }.toInt()
        return bucket < entry.probability
    }

    /** 当前命中的触发词（用于诊断展示）。 */
    private fun matchedKeys(entry: WorldEntry, text: String): String {
        if (entry.keys.isEmpty()) return ""
        return entry.keys.filter { key ->
            key.isNotBlank() && keyMatched(key, entry.regex, entry.caseSensitive, text)
        }.joinToString(", ")
    }

    private fun keyMatched(key: String, regex: Boolean, caseSensitive: Boolean, text: String): Boolean {
        if (regex) {
            val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            return runCatching { Regex(key, options).containsMatchIn(text) }.getOrDefault(false)
        }
        return if (caseSensitive) text.contains(key) else text.lowercase().contains(key.lowercase())
    }

    private fun matches(
        keys: List<String>,
        caseSensitive: Boolean,
        logic: Int,
        text: String,
        regex: Boolean = false
    ): Boolean {
        if (keys.isEmpty()) return false
        var hits = 0
        for (key in keys) {
            if (key.isBlank()) continue
            if (keyMatched(key, regex, caseSensitive, text)) hits++
        }
        return when (logic) {
            1 -> hits > 0 && hits < keys.size      // NOT ALL
            2 -> hits == 0                         // NOT ANY
            3 -> hits == keys.size                 // AND ALL
            else -> hits > 0                       // AND ANY (0)
        }
    }

    private fun positionLabel(position: String): String = when (position) {
        "before_char" -> "角色描述前"
        "after_char" -> "角色描述后"
        "before_system" -> "系统提示前"
        "after_system" -> "系统提示后"
        "in_history" -> "插入历史"
        "example_start" -> "示例开头"
        "example_end" -> "示例结尾"
        "greeting_end" -> "开场白结尾"
        else -> position.ifBlank { "默认" }
    }
}
