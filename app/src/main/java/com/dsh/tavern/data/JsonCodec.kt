package com.dsh.tavern.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

object JsonCodec {

    fun parseStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (s.isNotBlank()) list.add(s)
        }
        return list
    }

    fun cardFromCardJson(root: JSONObject, raw: String): CharacterCard {
        val data = root.optJSONObject("data") ?: root
        val ext = data.optJSONObject("extensions") ?: JSONObject()
        return CharacterCard(
            name = data.optString("name").ifBlank { root.optString("name").ifBlank { "未命名角色" } },
            description = data.optString("description"),
            personality = data.optString("personality"),
            scenario = data.optString("scenario"),
            systemPrompt = data.optString("system_prompt"),
            postHistoryInstructions = data.optString("post_history_instructions"),
            creatorNotes = data.optString("creator_notes"),
            firstMes = data.optString("first_mes"),
            mesExample = data.optString("mes_example"),
            alternateGreetings = parseStringList(data.optJSONArray("alternate_greetings")),
            tags = parseStringList(data.optJSONArray("tags")),
            suggestedReplies = parseStringList(ext.optJSONArray("suggested_replies")),
            creator = data.optString("creator"),
            characterVersion = data.optString("character_version"),
            rawJson = raw
        )
    }

    fun extractEntries(data: JSONObject, root: JSONObject): List<WorldEntry> {
        val ext = data.optJSONObject("extensions")
        val arr = data.optJSONObject("character_book")?.optJSONArray("entries")
            ?: ext?.optJSONObject("world_book")?.optJSONArray("entries")
            ?: root.optJSONObject("character_book")?.optJSONArray("entries")
            ?: root.optJSONArray("world_entries")
        return entriesFromJson(arr)
    }

    fun entriesFromJson(arr: JSONArray?): List<WorldEntry> {
        if (arr == null) return emptyList()
        val list = mutableListOf<WorldEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(entryFromJson(o, i))
        }
        return list
    }

    fun entryFromJson(o: JSONObject, index: Int): WorldEntry {
        val keys = mutableListOf<String>()
        o.optJSONArray("keys")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i).trim()
                if (s.isNotEmpty()) keys.add(s)
            }
        }
        if (keys.isEmpty()) {
            o.optString("key").split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { keys.add(it) }
        }
        val ext = o.optJSONObject("extensions")
        return WorldEntry(
            name = o.optString("name").ifBlank { o.optString("comment").ifBlank { "条目 ${index + 1}" } },
            keys = keys,
            content = o.optString("content"),
            constant = o.optBoolean("constant", false),
            selective = o.optBoolean("selective", true),
            selectiveLogic = o.optInt("selectiveLogic", o.optInt("selective_logic", 0)),
            priority = o.optInt("priority", 0),
            insertionOrder = o.optInt("insertion_order", o.optInt("insertionOrder", index)),
            enabled = o.optBoolean("enabled", true),
            caseSensitive = o.optBoolean("case_sensitive", false),
            position = o.optString("position", "after_char"),
            probability = o.optInt("probability", ext?.optInt("probability", 100) ?: 100)
                .coerceIn(0, 100),
            depth = o.optInt("depth", ext?.optInt("depth", 4) ?: 4).coerceAtLeast(0),
            secondaryKeys = parseStringList(
                o.optJSONArray("secondary_keys") ?: o.optJSONArray("secondaryKeys")
            ),
            regex = o.optBoolean("regex", false)
        )
    }

    fun entryToJson(e: WorldEntry): JSONObject = JSONObject()
        .put("name", e.name)
        .put("keys", JSONArray(e.keys))
        .put("secondary_keys", JSONArray(e.secondaryKeys))
        .put("content", e.content)
        .put("constant", e.constant)
        .put("selective", e.selective)
        .put("selectiveLogic", e.selectiveLogic)
        .put("priority", e.priority)
        .put("insertion_order", e.insertionOrder)
        .put("enabled", e.enabled)
        .put("case_sensitive", e.caseSensitive)
        .put("position", e.position)
        .put("probability", e.probability)
        .put("depth", e.depth)
        .put("regex", e.regex)

    fun cardToJson(card: CharacterCard, withAvatar: Boolean = true): JSONObject = JSONObject()
        .put("name", card.name)
        .put("description", card.description)
        .put("personality", card.personality)
        .put("scenario", card.scenario)
        .put("system_prompt", card.systemPrompt)
        .put("post_history_instructions", card.postHistoryInstructions)
        .put("creator_notes", card.creatorNotes)
        .put("first_mes", card.firstMes)
        .put("mes_example", card.mesExample)
        .put("alternate_greetings", JSONArray(card.alternateGreetings))
        .put("tags", JSONArray(card.tags))
        .put(
            "extensions",
            JSONObject()
                .put("suggested_replies", JSONArray(card.suggestedReplies))
                .put("status_template", card.statusTemplate)
        )
        .put("status_template", card.statusTemplate)
        .put("creator", card.creator)
        .put("character_version", card.characterVersion)
        .put("default_preset_id", card.defaultPresetId)
        .put("regex_rules", card.regexRules)
        .put("variables_text", card.variablesText)
        .apply {
            if (withAvatar && card.avatar != null) {
                put("avatar_b64", Base64.encodeToString(card.avatar, Base64.NO_WRAP))
            }
        }

    fun cardFromBackup(o: JSONObject): CharacterCard = CharacterCard(
        name = o.optString("name"),
        description = o.optString("description"),
        personality = o.optString("personality"),
        scenario = o.optString("scenario"),
        systemPrompt = o.optString("system_prompt"),
        postHistoryInstructions = o.optString("post_history_instructions"),
        creatorNotes = o.optString("creator_notes"),
        firstMes = o.optString("first_mes"),
        mesExample = o.optString("mes_example"),
        alternateGreetings = parseStringList(o.optJSONArray("alternate_greetings")),
        tags = parseStringList(o.optJSONArray("tags")),
        suggestedReplies = parseStringList(o.optJSONObject("extensions")?.optJSONArray("suggested_replies")),
        creator = o.optString("creator"),
        characterVersion = o.optString("character_version"),
        defaultPresetId = o.optLong("default_preset_id", 0L),
        regexRules = o.optString("regex_rules", ""),
        variablesText = o.optString("variables_text", ""),
        statusTemplate = o.optString("status_template")
            .ifBlank { o.optJSONObject("extensions")?.optString("status_template").orEmpty() },
        avatar = o.optString("avatar_b64").takeIf { it.isNotBlank() }?.let {
            try {
                Base64.decode(it, Base64.NO_WRAP)
            } catch (e: Exception) {
                null
            }
        }
    )

    /** 导出为可被 SillyTavern / 酒馆AI 识别的 chara_card_v2 JSON。 */
    fun buildCharaCard(card: CharacterCard, entries: List<WorldEntry>): String {
        val data = cardToJson(card, withAvatar = false)
        val arr = JSONArray()
        entries.forEach { arr.put(entryToJson(it)) }
        data.put(
            "character_book",
            JSONObject()
                .put("name", card.name + " 世界书")
                .put("entries", arr)
        )
        return JSONObject()
            .put("spec", "chara_card_v2")
            .put("spec_version", "2.0")
            .put("data", data)
            .toString(2)
    }

    /** 导出为 Character Card V3（chara_card_v3 / 3.0）JSON。 */
    fun buildCharaCardV3(card: CharacterCard, entries: List<WorldEntry>): String {
        val data = cardToJson(card, withAvatar = false)
        val arr = JSONArray()
        entries.forEach { arr.put(entryToJson(it)) }
        data.put(
            "character_book",
            JSONObject()
                .put("name", card.name + " 世界书")
                .put("entries", arr)
        )
        return JSONObject()
            .put("spec", "chara_card_v3")
            .put("spec_version", "3.0")
            .put("data", data)
            .toString(2)
    }
}
