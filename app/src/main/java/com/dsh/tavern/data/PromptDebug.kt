package com.dsh.tavern.data

import org.json.JSONArray
import org.json.JSONObject

/** 真正发给模型的一条消息（用于 Prompt 预览/归档）。 */
data class DebugMessage(val role: String, val content: String)

/** 本次请求中某个世界书条目的命中情况。 */
data class WorldHit(
    val name: String,
    val keys: String,
    val hitKeys: String,
    val status: String,
    val reason: String,
    val tokens: Int,
    val position: String,
    val order: Int,
    /** P1-8：条目的来源（空 = 角色卡；"全局" = 全局世界书）。 */
    val source: String = ""
) {
    val statusLabel: String
        get() = when (status) {
            "inserted" -> "已注入"
            "inactive" -> "未触发"
            "disabled" -> "已停用"
            "empty" -> "内容为空"
            "budget" -> "预算不足"
            else -> status
        }
}

/** 一次生成的 Prompt 组装快照（对应 DZMM 的「组装上下文」+「世界书诊断」）。 */
data class PromptDebug(
    val contextLimit: Int = 0,
    val outputReserve: Int = 0,
    val systemTokens: Int = 0,
    val worldTokens: Int = 0,
    val historyTokens: Int = 0,
    val totalTokens: Int = 0,
    val worldBudget: Int = 0,
    val activated: Int = 0,
    val model: String = "",
    val createdAt: Long = 0,
    val world: List<WorldHit> = emptyList(),
    val messages: List<DebugMessage> = emptyList()
) {
    fun toJson(): String {
        val root = JSONObject()
            .put("contextLimit", contextLimit)
            .put("outputReserve", outputReserve)
            .put("systemTokens", systemTokens)
            .put("worldTokens", worldTokens)
            .put("historyTokens", historyTokens)
            .put("totalTokens", totalTokens)
            .put("worldBudget", worldBudget)
            .put("activated", activated)
            .put("model", model)
            .put("createdAt", createdAt)
        val wor = JSONArray()
        world.forEach { w ->
            wor.put(
                JSONObject()
                    .put("name", w.name)
                    .put("keys", w.keys)
                    .put("hitKeys", w.hitKeys)
                    .put("status", w.status)
                    .put("reason", w.reason)
                    .put("tokens", w.tokens)
                    .put("position", w.position)
                    .put("order", w.order)
                    .put("source", w.source)
            )
        }
        root.put("world", wor)
        val msgs = JSONArray()
        messages.forEach { m ->
            msgs.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        root.put("messages", msgs)
        return root.toString()
    }

    companion object {
        fun fromJson(text: String): PromptDebug {
            val root = runCatching { JSONObject(text) }.getOrNull()
                ?: return PromptDebug()
            val world = mutableListOf<WorldHit>()
            root.optJSONArray("world")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    world.add(
                        WorldHit(
                            name = o.optString("name", ""),
                            keys = o.optString("keys", ""),
                            hitKeys = o.optString("hitKeys", ""),
                            status = o.optString("status", ""),
                            reason = o.optString("reason", ""),
                            tokens = o.optInt("tokens", 0),
                            position = o.optString("position", ""),
                            order = o.optInt("order", 0),
                            source = o.optString("source", "")
                        )
                    )
                }
            }
            val messages = mutableListOf<DebugMessage>()
            root.optJSONArray("messages")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    messages.add(DebugMessage(o.optString("role", ""), o.optString("content", "")))
                }
            }
            return PromptDebug(
                contextLimit = root.optInt("contextLimit", 0),
                outputReserve = root.optInt("outputReserve", 0),
                systemTokens = root.optInt("systemTokens", 0),
                worldTokens = root.optInt("worldTokens", 0),
                historyTokens = root.optInt("historyTokens", 0),
                totalTokens = root.optInt("totalTokens", 0),
                worldBudget = root.optInt("worldBudget", 0),
                activated = root.optInt("activated", 0),
                model = root.optString("model", ""),
                createdAt = root.optLong("createdAt", 0),
                world = world,
                messages = messages
            )
        }
    }
}
