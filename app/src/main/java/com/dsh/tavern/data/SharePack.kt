package com.dsh.tavern.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 「角色卡 + 聊天记录」离线分享包（单 JSON 文件，头像以 base64 内嵌）。
 *
 * 设计理由：没有服务器时，通过微信/QQ/网盘发一个 JSON 文件最稳；
 * PNG 只能装角色卡（且文本 AI 无法直接产出），聊天记录必须走 JSON。
 * 兼容性：外层是私酒馆格式，内层 card 仍按 Character Card V3 字段存储，便于以后拆包/转存。
 */
object SharePack {
    const val FORMAT = "dsh_tavern_share"
    const val VERSION = 1

    fun isSharePack(text: String): Boolean {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("{")) return false
        return runCatching { JSONObject(trimmed).optString("format") == FORMAT }.getOrDefault(false)
    }

    /** 导出：includeChats = 是否带上该卡的全部聊天记录。 */
    fun build(db: DbHelper, cardId: Long, includeChats: Boolean): String {
        val card = db.getCard(cardId) ?: throw IllegalArgumentException("角色卡不存在")
        val entries = db.getEntries(cardId)
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("app", "私酒馆")
            .put("exported_at", System.currentTimeMillis())
            .put("card", JsonCodec.cardToJson(card, withAvatar = true))
        val entriesArr = JSONArray()
        entries.forEach { entriesArr.put(JsonCodec.entryToJson(it)) }
        root.put("world_entries", entriesArr)

        var messageCount = 0
        val chatsArr = JSONArray()
        if (includeChats) {
            val presets = db.listPresets()
            db.listChats(cardId).forEach { chat ->
                val messages = db.getMessages(chat.id)
                    .filter { it.role == "user" || it.role == "assistant" }
                if (messages.isEmpty()) return@forEach
                val chatJson = JSONObject()
                    .put("title", chat.title)
                    .put("created_at", chat.createdAt)
                    .put(
                        "preset_name",
                        presets.firstOrNull { it.id == chat.presetId }?.name.orEmpty()
                    )
                val msgArr = JSONArray()
                messages.forEach { message ->
                    val msgJson = JSONObject()
                        .put("role", message.role)
                        .put("content", message.displayContent)
                        .put("reasoning", message.displayReasoning)
                        .put("created_at", message.createdAt)
                        .put("prompt_tokens", message.promptTokens)
                        .put("completion_tokens", message.completionTokens)
                    if (message.isMultiVariant) {
                        val variants = JSONArray()
                        message.allVariants.forEach { variant ->
                            variants.put(
                                JSONObject()
                                    .put("content", variant.content)
                                    .put("reasoning", variant.reasoning)
                                    .put("created_at", variant.createdAt)
                            )
                        }
                        msgJson.put("variants", variants)
                    }
                    msgArr.put(msgJson)
                    messageCount++
                }
                chatJson.put("messages", msgArr)
                db.getMemory(chat.id)?.let { memory ->
                    chatJson.put(
                        "memory",
                        JSONObject()
                            .put("summary", memory.summary)
                            .put("msg_upto_id", memory.msgUptoId)
                    )
                }
                val variables = db.getVariables(chat.id)
                if (variables.isNotEmpty()) {
                    val varsObj = JSONObject()
                    variables.forEach { (name, value) -> varsObj.put(name, value) }
                    chatJson.put("variables", varsObj)
                }
                db.getPanelState(chat.id)?.takeIf { it.isNotBlank() }?.let { state ->
                    chatJson.put("panel_state", state)
                }
                chatsArr.put(chatJson)
            }
        }
        root.put("chats", chatsArr)
        root.put(
            "meta",
            JSONObject()
                .put("card_name", card.name)
                .put("has_avatar", card.avatar != null)
                .put("chat_count", chatsArr.length())
                .put("message_count", messageCount)
        )
        return root.toString(2)
    }

    data class ImportResult(
        val cardId: Long,
        val cardName: String,
        val chatCount: Int,
        val messageCount: Int
    )

    /** 导入：写入角色卡 + 世界书 + 聊天记录（消息保留时间/用量/多版本）。 */
    fun import(db: DbHelper, text: String): ImportResult {
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT) { "不是私酒馆分享包" }
        val cardObj = root.optJSONObject("card") ?: throw IllegalArgumentException("分享包缺少角色卡")
        val card = JsonCodec.cardFromBackup(cardObj)
        val entries = JsonCodec.entriesFromJson(root.optJSONArray("world_entries"))
        val cardId = db.insertCard(card.copy(id = 0, createdAt = 0), entries)

        var chatCount = 0
        var messageCount = 0
        val chatsArr = root.optJSONArray("chats")
        if (chatsArr != null) {
            for (i in 0 until chatsArr.length()) {
                val chatJson = chatsArr.optJSONObject(i) ?: continue
                val msgArr = chatJson.optJSONArray("messages") ?: continue
                if (msgArr.length() == 0) continue
                val chatId = db.createChat(
                    cardId,
                    chatJson.optString("title").ifBlank { "分享记录 ${i + 1}" },
                    "",
                    "imported"
                )
                var lastId = 0L
                for (j in 0 until msgArr.length()) {
                    val msgJson = msgArr.optJSONObject(j) ?: continue
                    val role = if (msgJson.optString("role") == "assistant") "assistant" else "user"
                    lastId = db.insertMessage(
                        chatId,
                        role,
                        msgJson.optString("content"),
                        msgJson.optString("reasoning"),
                        msgJson.optInt("prompt_tokens"),
                        msgJson.optInt("completion_tokens")
                    )
                    val variants = msgJson.optJSONArray("variants")
                    if (variants != null && variants.length() > 1) {
                        val list = mutableListOf<MessageVariant>()
                        for (k in 0 until variants.length()) {
                            val variantJson = variants.optJSONObject(k) ?: continue
                            list.add(
                                MessageVariant(
                                    variantJson.optString("content"),
                                    variantJson.optString("reasoning"),
                                    variantJson.optLong("created_at")
                                )
                            )
                        }
                        if (list.isNotEmpty()) {
                            db.updateMessageVariants(lastId, list, list.lastIndex)
                        }
                    }
                    messageCount++
                }
                chatJson.optJSONObject("memory")?.let { memory ->
                    val summary = memory.optString("summary")
                    if (summary.isNotBlank()) db.saveMemory(chatId, summary, lastId)
                }
                chatJson.optJSONObject("variables")?.let { variables ->
                    val map = mutableMapOf<String, String>()
                    variables.keys().forEach { key -> map[key] = variables.optString(key) }
                    db.setVariables(chatId, map)
                }
                chatJson.optString("panel_state").takeIf { it.isNotBlank() }?.let { state ->
                    db.savePanelState(chatId, state)
                }
                chatCount++
            }
        }
        return ImportResult(cardId, card.name, chatCount, messageCount)
    }
}
