package com.dsh.tavern.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

private const val DB_VERSION = 14

class DbHelper(context: Context) : SQLiteOpenHelper(context, "tavern.db", null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE cards(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                personality TEXT NOT NULL DEFAULT '',
                scenario TEXT NOT NULL DEFAULT '',
                system_prompt TEXT NOT NULL DEFAULT '',
                post_history TEXT NOT NULL DEFAULT '',
                creator_notes TEXT NOT NULL DEFAULT '',
                first_mes TEXT NOT NULL DEFAULT '',
                mes_example TEXT NOT NULL DEFAULT '',
                alternate_greetings TEXT NOT NULL DEFAULT '[]',
                tags TEXT NOT NULL DEFAULT '[]',
                suggested_replies TEXT NOT NULL DEFAULT '[]',
                creator TEXT NOT NULL DEFAULT '',
                character_version TEXT NOT NULL DEFAULT '',
                raw_json TEXT NOT NULL DEFAULT '',
                status_template TEXT NOT NULL DEFAULT '',
                default_preset_id INTEGER NOT NULL DEFAULT 0,
                regex_rules TEXT NOT NULL DEFAULT '',
                variables_text TEXT NOT NULL DEFAULT '',
                avatar BLOB,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE world_entries(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                card_id INTEGER NOT NULL,
                name TEXT NOT NULL DEFAULT '',
                keys TEXT NOT NULL DEFAULT '[]',
                content TEXT NOT NULL DEFAULT '',
                constant INTEGER NOT NULL DEFAULT 0,
                selective INTEGER NOT NULL DEFAULT 1,
                selective_logic INTEGER NOT NULL DEFAULT 0,
                priority INTEGER NOT NULL DEFAULT 0,
                insertion_order INTEGER NOT NULL DEFAULT 0,
                enabled INTEGER NOT NULL DEFAULT 1,
                case_sensitive INTEGER NOT NULL DEFAULT 0,
                position TEXT NOT NULL DEFAULT 'after_char',
                probability INTEGER NOT NULL DEFAULT 100,
                depth INTEGER NOT NULL DEFAULT 4,
                secondary_keys TEXT NOT NULL DEFAULT '[]',
                regex INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL(
            """CREATE TABLE chats(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                card_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                last_message TEXT NOT NULL DEFAULT '',
                model TEXT NOT NULL DEFAULT '',
                preset_id INTEGER NOT NULL DEFAULT 0,
                branched_from INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL(
            """CREATE TABLE messages(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                chat_id INTEGER NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL DEFAULT '',
                reasoning TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                variants TEXT NOT NULL DEFAULT '[]',
                active_variant INTEGER NOT NULL DEFAULT 0,
                debug TEXT NOT NULL DEFAULT '',
                prompt_tokens INTEGER NOT NULL DEFAULT 0,
                completion_tokens INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL("CREATE INDEX idx_world_card ON world_entries(card_id)")
        db.execSQL("CREATE INDEX idx_chat_card ON chats(card_id)")
        db.execSQL("CREATE INDEX idx_msg_chat ON messages(chat_id)")
        db.execSQL(
            """CREATE TABLE presets(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                system_prompt TEXT NOT NULL DEFAULT '',
                post_history TEXT NOT NULL DEFAULT '',
                temperature REAL NOT NULL DEFAULT 1.0,
                top_p REAL NOT NULL DEFAULT 1.0,
                max_tokens INTEGER NOT NULL DEFAULT 4096,
                style TEXT NOT NULL DEFAULT '标准',
                built_in INTEGER NOT NULL DEFAULT 0,
                stop_sequences TEXT NOT NULL DEFAULT '',
                frequency_penalty REAL NOT NULL DEFAULT 0,
                presence_penalty REAL NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE chat_memory(
                chat_id INTEGER PRIMARY KEY,
                summary TEXT NOT NULL DEFAULT '',
                msg_upto_id INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE checkpoints(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                chat_id INTEGER NOT NULL,
                name TEXT NOT NULL DEFAULT '',
                message_id INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX idx_checkpoint_chat ON checkpoints(chat_id)")
        db.execSQL(
            """CREATE TABLE panel_state(
                chat_id INTEGER PRIMARY KEY,
                json TEXT NOT NULL DEFAULT '',
                updated_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE usage_log(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at INTEGER NOT NULL,
                model TEXT NOT NULL DEFAULT '',
                prompt_tokens INTEGER NOT NULL DEFAULT 0,
                completion_tokens INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL("CREATE INDEX idx_usage_created ON usage_log(created_at)")
        db.execSQL(
            """CREATE TABLE chat_variables(
                chat_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                value TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(chat_id, name)
            )"""
        )
        db.execSQL(
            """CREATE TABLE chat_cards(
                chat_id INTEGER NOT NULL,
                card_id INTEGER NOT NULL,
                sort INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(chat_id, card_id)
            )"""
        )
        db.execSQL(
            """CREATE TABLE worldbook_collections(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE worldbook_library(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                collection_id INTEGER NOT NULL DEFAULT 0,
                card_id INTEGER NOT NULL DEFAULT 0,
                name TEXT NOT NULL DEFAULT '',
                keys TEXT NOT NULL DEFAULT '[]',
                content TEXT NOT NULL DEFAULT '',
                constant INTEGER NOT NULL DEFAULT 0,
                selective INTEGER NOT NULL DEFAULT 1,
                selective_logic INTEGER NOT NULL DEFAULT 0,
                priority INTEGER NOT NULL DEFAULT 0,
                insertion_order INTEGER NOT NULL DEFAULT 0,
                enabled INTEGER NOT NULL DEFAULT 1,
                case_sensitive INTEGER NOT NULL DEFAULT 0,
                position TEXT NOT NULL DEFAULT 'after_char',
                probability INTEGER NOT NULL DEFAULT 100,
                depth INTEGER NOT NULL DEFAULT 4,
                secondary_keys TEXT NOT NULL DEFAULT '[]',
                regex INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX idx_wb_library_collection ON worldbook_library(collection_id)")
        db.execSQL(
            """CREATE TABLE global_world_entries(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                card_id INTEGER NOT NULL DEFAULT 0,
                name TEXT NOT NULL DEFAULT '',
                keys TEXT NOT NULL DEFAULT '[]',
                content TEXT NOT NULL DEFAULT '',
                constant INTEGER NOT NULL DEFAULT 0,
                selective INTEGER NOT NULL DEFAULT 1,
                selective_logic INTEGER NOT NULL DEFAULT 0,
                priority INTEGER NOT NULL DEFAULT 0,
                insertion_order INTEGER NOT NULL DEFAULT 0,
                enabled INTEGER NOT NULL DEFAULT 1,
                case_sensitive INTEGER NOT NULL DEFAULT 0,
                position TEXT NOT NULL DEFAULT 'after_char',
                probability INTEGER NOT NULL DEFAULT 100,
                depth INTEGER NOT NULL DEFAULT 4,
                secondary_keys TEXT NOT NULL DEFAULT '[]',
                regex INTEGER NOT NULL DEFAULT 0
            )"""
        )
        seedPresets(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v2：消息多版本 + Prompt 调试信息。老消息的 variants 为空，读取时自动按 content 合成 1 个版本。
            db.execSQL("ALTER TABLE messages ADD COLUMN variants TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE messages ADD COLUMN active_variant INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE messages ADD COLUMN debug TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 3) {
            // v3：预设系统（预设表 + 会话绑定预设）
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS presets(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    system_prompt TEXT NOT NULL DEFAULT '',
                    post_history TEXT NOT NULL DEFAULT '',
                    temperature REAL NOT NULL DEFAULT 1.0,
                    top_p REAL NOT NULL DEFAULT 1.0,
                    max_tokens INTEGER NOT NULL DEFAULT 4096,
                    style TEXT NOT NULL DEFAULT '标准',
                    built_in INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )"""
            )
            db.execSQL("ALTER TABLE chats ADD COLUMN preset_id INTEGER NOT NULL DEFAULT 0")
            seedPresets(db)
        }
        if (oldVersion < 4) {
            // v4：记录每轮真实 token 用量（来自接口 usage）
            db.execSQL("ALTER TABLE messages ADD COLUMN prompt_tokens INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE messages ADD COLUMN completion_tokens INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 5) {
            // v5：会话长期记忆（前情提要）
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS chat_memory(
                    chat_id INTEGER PRIMARY KEY,
                    summary TEXT NOT NULL DEFAULT '',
                    msg_upto_id INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )"""
            )
        }
        if (oldVersion < 6) {
            // v6：会话分支 + 检查点
            db.execSQL("ALTER TABLE chats ADD COLUMN branched_from INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS checkpoints(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    chat_id INTEGER NOT NULL,
                    name TEXT NOT NULL DEFAULT '',
                    message_id INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                )"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_checkpoint_chat ON checkpoints(chat_id)")
        }
        if (oldVersion < 7) {
            // v7：世界书增强（条件概率 / 插入深度 / 辅助关键词）
            db.execSQL("ALTER TABLE world_entries ADD COLUMN probability INTEGER NOT NULL DEFAULT 100")
            db.execSQL("ALTER TABLE world_entries ADD COLUMN depth INTEGER NOT NULL DEFAULT 4")
            db.execSQL("ALTER TABLE world_entries ADD COLUMN secondary_keys TEXT NOT NULL DEFAULT '[]'")
        }
        if (oldVersion < 8) {
            // v8：互动面板（卡片模板 + 会话面板状态）
            db.execSQL("ALTER TABLE cards ADD COLUMN status_template TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS panel_state(
                    chat_id INTEGER PRIMARY KEY,
                    json TEXT NOT NULL DEFAULT '',
                    updated_at INTEGER NOT NULL
                )"""
            )
        }
        if (oldVersion < 9) {
            // v9：用量统计（token 花费累计 / 每日预算）
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS usage_log(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    created_at INTEGER NOT NULL,
                    model TEXT NOT NULL DEFAULT '',
                    prompt_tokens INTEGER NOT NULL DEFAULT 0,
                    completion_tokens INTEGER NOT NULL DEFAULT 0
                )"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_usage_created ON usage_log(created_at)")
        }
        if (oldVersion < 10) {
            // v10：预设增加停止序列与频率/存在惩罚
            db.execSQL("ALTER TABLE presets ADD COLUMN stop_sequences TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE presets ADD COLUMN frequency_penalty REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE presets ADD COLUMN presence_penalty REAL NOT NULL DEFAULT 0")
        }
        if (oldVersion < 11) {
            // v11：卡片默认预设、世界书正则触发、跨卡全局世界书
            db.execSQL("ALTER TABLE cards ADD COLUMN default_preset_id INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE world_entries ADD COLUMN regex INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS global_world_entries(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    card_id INTEGER NOT NULL DEFAULT 0,
                    name TEXT NOT NULL DEFAULT '',
                    keys TEXT NOT NULL DEFAULT '[]',
                    content TEXT NOT NULL DEFAULT '',
                    constant INTEGER NOT NULL DEFAULT 0,
                    selective INTEGER NOT NULL DEFAULT 1,
                    selective_logic INTEGER NOT NULL DEFAULT 0,
                    priority INTEGER NOT NULL DEFAULT 0,
                    insertion_order INTEGER NOT NULL DEFAULT 0,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    case_sensitive INTEGER NOT NULL DEFAULT 0,
                    position TEXT NOT NULL DEFAULT 'after_char',
                    probability INTEGER NOT NULL DEFAULT 100,
                    depth INTEGER NOT NULL DEFAULT 4,
                    secondary_keys TEXT NOT NULL DEFAULT '[]',
                    regex INTEGER NOT NULL DEFAULT 0
                )"""
            )
        }
        if (oldVersion < 12) {
            // v12：变量系统 + 输出正则后处理
            db.execSQL("ALTER TABLE cards ADD COLUMN regex_rules TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE cards ADD COLUMN variables_text TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS chat_variables(
                    chat_id INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    value TEXT NOT NULL DEFAULT '',
                    PRIMARY KEY(chat_id, name)
                )"""
            )
        }
        if (oldVersion < 13) {
            // v13：多角色同台（一个会话可绑定多张卡）
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS chat_cards(
                    chat_id INTEGER NOT NULL,
                    card_id INTEGER NOT NULL,
                    sort INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(chat_id, card_id)
                )"""
            )
        }
        if (oldVersion < 14) {
            // v14：世界书收藏库（收藏条目 + 分组），用于跨卡复用
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS worldbook_collections(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )"""
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS worldbook_library(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    collection_id INTEGER NOT NULL DEFAULT 0,
                    card_id INTEGER NOT NULL DEFAULT 0,
                    name TEXT NOT NULL DEFAULT '',
                    keys TEXT NOT NULL DEFAULT '[]',
                    content TEXT NOT NULL DEFAULT '',
                    constant INTEGER NOT NULL DEFAULT 0,
                    selective INTEGER NOT NULL DEFAULT 1,
                    selective_logic INTEGER NOT NULL DEFAULT 0,
                    priority INTEGER NOT NULL DEFAULT 0,
                    insertion_order INTEGER NOT NULL DEFAULT 0,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    case_sensitive INTEGER NOT NULL DEFAULT 0,
                    position TEXT NOT NULL DEFAULT 'after_char',
                    probability INTEGER NOT NULL DEFAULT 100,
                    depth INTEGER NOT NULL DEFAULT 4,
                    secondary_keys TEXT NOT NULL DEFAULT '[]',
                    regex INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                )"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_wb_library_collection ON worldbook_library(collection_id)")
        }
    }

    private fun seedPresets(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        Preset.builtIns().forEach { preset ->
            db.insert("presets", null, presetValues(preset, now))
        }
    }

    // ---------------- cards ----------------

    fun insertCard(card: CharacterCard, entries: List<WorldEntry>): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            val id = db.insertOrThrow("cards", null, cardValues(card, now))
            entries.forEach { e ->
                db.insert("world_entries", null, entryValues(e.copy(cardId = id)))
            }
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    fun listCards(): List<LibraryItem> {
        val result = mutableListOf<LibraryItem>()
        readableDatabase.rawQuery(
            """SELECT c.*, 
               (SELECT COUNT(*) FROM chats ch WHERE ch.card_id = c.id) AS chat_count,
               (SELECT MAX(ch.updated_at) FROM chats ch WHERE ch.card_id = c.id) AS last_played
               FROM cards c
               ORDER BY last_played DESC, c.updated_at DESC""",
            null
        ).use { c ->
            while (c.moveToNext()) {
                result.add(
                    LibraryItem(
                        card = readCard(c),
                        chatCount = c.getInt(c.getColumnIndexOrThrow("chat_count")),
                        lastPlayed = c.getLong(c.getColumnIndexOrThrow("last_played"))
                    )
                )
            }
        }
        return result
    }

    fun getCard(id: Long): CharacterCard? {
        readableDatabase.query("cards", null, "id=?", arrayOf(id.toString()), null, null, null).use { c ->
            return if (c.moveToFirst()) readCard(c) else null
        }
    }

    /** P1-9：编辑已有角色卡（世界书条目保持不动）。 */
    fun updateCard(card: CharacterCard) {
        writableDatabase.update(
            "cards",
            cardValues(card, System.currentTimeMillis()),
            "id=?",
            arrayOf(card.id.toString())
        )
    }

    /** 复制角色卡（连同世界书条目），返回新卡 id。 */
    fun duplicateCard(cardId: Long): Long? {
        val card = getCard(cardId) ?: return null
        val entries = getEntries(cardId)
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val newId = db.insertOrThrow(
                "cards", null,
                cardValues(card.copy(id = 0, name = card.name + " 副本", createdAt = 0), now)
            )
            entries.forEach { entry ->
                db.insert("world_entries", null, entryValues(entry.copy(id = 0, cardId = newId)))
            }
            db.setTransactionSuccessful()
            return newId
        } finally {
            db.endTransaction()
        }
    }

    fun deleteCard(cardId: Long) {        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(
                "chat_memory",
                "chat_id IN (SELECT id FROM chats WHERE card_id=?)",
                arrayOf(cardId.toString())
            )
            db.delete(
                "checkpoints",
                "chat_id IN (SELECT id FROM chats WHERE card_id=?)",
                arrayOf(cardId.toString())
            )
            db.delete(
                "panel_state",
                "chat_id IN (SELECT id FROM chats WHERE card_id=?)",
                arrayOf(cardId.toString())
            )
            db.delete(
                "chat_variables",
                "chat_id IN (SELECT id FROM chats WHERE card_id=?)",
                arrayOf(cardId.toString())
            )
            db.delete("chat_cards", "card_id=?", arrayOf(cardId.toString()))
            db.delete("messages", "chat_id IN (SELECT id FROM chats WHERE card_id=?)", arrayOf(cardId.toString()))
            db.delete("chats", "card_id=?", arrayOf(cardId.toString()))
            db.delete("world_entries", "card_id=?", arrayOf(cardId.toString()))
            db.delete("cards", "id=?", arrayOf(cardId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------------- world book ----------------

    fun getEntries(cardId: Long): List<WorldEntry> {
        val result = mutableListOf<WorldEntry>()
        readableDatabase.query(
            "world_entries", null, "card_id=?",
            arrayOf(cardId.toString()), null, null,
            "insertion_order ASC, priority ASC, id ASC"
        ).use { c ->
            while (c.moveToNext()) result.add(readEntry(c))
        }
        return result
    }

    /** 覆盖某张卡的全部世界书条目（卡片编辑器保存时用）。 */
    fun replaceEntries(cardId: Long, entries: List<WorldEntry>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("world_entries", "card_id=?", arrayOf(cardId.toString()))
            entries.forEach { entry ->
                db.insert("world_entries", null, entryValues(entry.copy(cardId = cardId, id = 0)))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 追加导入世界书条目。 */
    fun insertEntries(cardId: Long, entries: List<WorldEntry>) {
        if (entries.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            entries.forEach { entry ->
                db.insert("world_entries", null, entryValues(entry.copy(cardId = cardId)))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 清理旧消息里的 Prompt 调试数据，避免数据库无限膨胀。 */
    fun pruneMessageDebug(chatId: Long, keepRecent: Int = 30) {
        writableDatabase.execSQL(
            """UPDATE messages SET debug='' WHERE chat_id=? AND debug!=''
               AND id NOT IN (
                   SELECT id FROM messages WHERE chat_id=? ORDER BY id DESC LIMIT ?
               )""",
            arrayOf(chatId.toString(), chatId.toString(), keepRecent.toString())
        )
    }

    fun updateEntry(entry: WorldEntry) {
        writableDatabase.update("world_entries", entryValues(entry), "id=?", arrayOf(entry.id.toString()))
    }

    // ---------------- global world book ----------------

    fun getGlobalEntries(): List<WorldEntry> {
        val result = mutableListOf<WorldEntry>()
        readableDatabase.query(
            "global_world_entries", null, null, null, null, null,
            "insertion_order ASC, priority ASC, id ASC"
        ).use { c ->
            while (c.moveToNext()) result.add(readEntry(c))
        }
        return result
    }

    fun insertGlobalEntry(entry: WorldEntry): Long =
        writableDatabase.insertOrThrow(
            "global_world_entries", null,
            entryValues(entry.copy(id = 0, cardId = 0))
        )

    fun updateGlobalEntry(entry: WorldEntry) {
        writableDatabase.update(
            "global_world_entries", entryValues(entry.copy(cardId = 0)),
            "id=?", arrayOf(entry.id.toString())
        )
    }

    fun deleteGlobalEntry(id: Long) {
        writableDatabase.delete("global_world_entries", "id=?", arrayOf(id.toString()))
    }

    // ---------------- 世界书收藏库（分组 + 条目） ----------------

    fun listWorldbookCollections(): List<WorldbookCollection> {
        val result = mutableListOf<WorldbookCollection>()
        readableDatabase.rawQuery(
            """SELECT c.*, (SELECT COUNT(*) FROM worldbook_library e WHERE e.collection_id = c.id) AS entry_count
               FROM worldbook_collections c ORDER BY c.created_at ASC""",
            null
        ).use { c ->
            while (c.moveToNext()) {
                result.add(
                    WorldbookCollection(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")) ?: "",
                        entryCount = c.getInt(c.getColumnIndexOrThrow("entry_count")),
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        return result
    }

    fun createWorldbookCollection(name: String): Long =
        writableDatabase.insertOrThrow(
            "worldbook_collections", null,
            ContentValues().apply {
                put("name", name.trim().ifBlank { "新分组" })
                put("created_at", System.currentTimeMillis())
            }
        )

    fun renameWorldbookCollection(id: Long, name: String) {
        writableDatabase.update(
            "worldbook_collections",
            ContentValues().apply { put("name", name.trim().ifBlank { "未命名分组" }) },
            "id=?", arrayOf(id.toString())
        )
    }

    fun deleteWorldbookCollection(id: Long) {
        writableDatabase.delete("worldbook_library", "collection_id=?", arrayOf(id.toString()))
        writableDatabase.delete("worldbook_collections", "id=?", arrayOf(id.toString()))
    }

    fun listWorldbookLibraryEntries(collectionId: Long): List<WorldEntry> {
        val result = mutableListOf<WorldEntry>()
        readableDatabase.query(
            "worldbook_library", null, "collection_id=?",
            arrayOf(collectionId.toString()), null, null, "insertion_order ASC, id ASC"
        ).use { c ->
            while (c.moveToNext()) result.add(readEntry(c))
        }
        return result
    }

    /** 把某张卡的条目收藏到世界书库（collectionId = 0 表示未分组）。 */
    fun saveWorldEntryToLibrary(entry: WorldEntry, collectionId: Long): Long {
        val values = entryValues(entry.copy(id = 0, cardId = 0))
        values.put("collection_id", collectionId)
        values.put("created_at", System.currentTimeMillis())
        return writableDatabase.insertOrThrow("worldbook_library", null, values)
    }

    fun deleteWorldbookLibraryEntry(id: Long) {
        writableDatabase.delete("worldbook_library", "id=?", arrayOf(id.toString()))
    }

    /** 把收藏分组整组复制到角色卡的世界书。返回导入条数。 */
    fun importWorldbookCollectionToCard(cardId: Long, collectionId: Long): Int {
        val entries = listWorldbookLibraryEntries(collectionId)
        if (entries.isEmpty()) return 0
        insertEntries(cardId, entries)
        return entries.size
    }

    // ---------------- presets ----------------

    fun listPresets(): List<Preset> {
        val result = mutableListOf<Preset>()
        readableDatabase.query("presets", null, null, null, null, null, "built_in DESC, id ASC").use { c ->
            while (c.moveToNext()) result.add(readPreset(c))
        }
        return result
    }

    fun getPreset(id: Long): Preset? {
        readableDatabase.query("presets", null, "id=?", arrayOf(id.toString()), null, null, null).use { c ->
            return if (c.moveToFirst()) readPreset(c) else null
        }
    }

    fun insertPreset(preset: Preset): Long =
        writableDatabase.insertOrThrow("presets", null, presetValues(preset, System.currentTimeMillis()))

    fun updatePreset(preset: Preset) {
        writableDatabase.update(
            "presets",
            presetValues(preset, System.currentTimeMillis()),
            "id=?",
            arrayOf(preset.id.toString())
        )
    }

    fun deletePreset(id: Long) {
        writableDatabase.delete("presets", "id=?", arrayOf(id.toString()))
    }

    /** 导出全部预设为 JSON（数组）。 */
    fun exportPresetsJson(): String {
        val arr = JSONArray()
        listPresets().forEach { arr.put(presetToJson(it)) }
        return arr.toString(2)
    }

    /** 从 JSON 数组导入预设（追加，builtIn 会改成 false）。 */
    fun importPresetsJson(text: String): Int {
        val parsed = runCatching { JSONArray(text) }.getOrNull()
            ?: throw IllegalArgumentException("不是预设 JSON 数组")
        var count = 0
        for (i in 0 until parsed.length()) {
            val obj = parsed.optJSONObject(i) ?: continue
            val preset = presetFromJson(obj)
            if (preset.name.isBlank()) continue
            val exists = listPresets().any { it.name == preset.name }
            insertPreset(
                preset.copy(
                    id = 0,
                    name = if (exists) preset.name + "（导入）" else preset.name,
                    builtIn = false
                )
            )
            count++
        }
        return count
    }

    /** P1-6：保存/清空卡片互动面板模板。 */
    fun updateCardStatusTemplate(cardId: Long, templateJson: String) {
        writableDatabase.update(
            "cards",
            ContentValues().apply { put("status_template", templateJson) },
            "id=?",
            arrayOf(cardId.toString())
        )
    }

    // ---------------- panel state ----------------

    fun getPanelState(chatId: Long): String? {
        readableDatabase.query(
            "panel_state", null, "chat_id=?", arrayOf(chatId.toString()), null, null, null
        ).use { c ->
            if (!c.moveToFirst()) return null
            return c.getString(c.getColumnIndexOrThrow("json"))
        }
    }

    fun savePanelState(chatId: Long, json: String) {
        writableDatabase.insertWithOnConflict(
            "panel_state", null,
            ContentValues().apply {
                put("chat_id", chatId)
                put("json", json)
                put("updated_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun clearPanelState(chatId: Long) {
        writableDatabase.delete("panel_state", "chat_id=?", arrayOf(chatId.toString()))
    }

    // ---------------- chat variables ----------------

    fun getVariables(chatId: Long): Map<String, String> {
        val result = mutableMapOf<String, String>()
        readableDatabase.query(
            "chat_variables", null, "chat_id=?", arrayOf(chatId.toString()), null, null, "rowid ASC"
        ).use { c ->
            while (c.moveToNext()) {
                result[c.getString(c.getColumnIndexOrThrow("name"))] =
                    c.getString(c.getColumnIndexOrThrow("value")) ?: ""
            }
        }
        return result
    }

    fun setVariables(chatId: Long, variables: Map<String, String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("chat_variables", "chat_id=?", arrayOf(chatId.toString()))
            variables.forEach { (name, value) ->
                if (name.isNotBlank()) {
                    db.insertWithOnConflict(
                        "chat_variables", null,
                        ContentValues().apply {
                            put("chat_id", chatId)
                            put("name", name)
                            put("value", value)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateVariables(chatId: Long, updates: Map<String, String>) {
        if (updates.isEmpty()) return
        updates.forEach { (name, value) ->
            if (name.isNotBlank()) {
                writableDatabase.insertWithOnConflict(
                    "chat_variables", null,
                    ContentValues().apply {
                        put("chat_id", chatId)
                        put("name", name)
                        put("value", value)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }
    }

    // ---------------- usage ----------------

    fun insertUsage(promptTokens: Int, completionTokens: Int, model: String) {
        if (promptTokens <= 0 && completionTokens <= 0) return
        writableDatabase.insert(
            "usage_log", null,
            ContentValues().apply {
                put("created_at", System.currentTimeMillis())
                put("model", model)
                put("prompt_tokens", promptTokens)
                put("completion_tokens", completionTokens)
            }
        )
    }

    /** 返回 [起始时间, 现在] 内的 (prompt tokens, completion tokens)。 */
    fun usageSince(sinceMillis: Long): Pair<Long, Long> {
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(prompt_tokens),0), COALESCE(SUM(completion_tokens),0) " +
                "FROM usage_log WHERE created_at>=?",
            arrayOf(sinceMillis.toString())
        ).use { c ->
            if (!c.moveToFirst()) return 0L to 0L
            return c.getLong(0) to c.getLong(1)
        }
    }

    fun pruneUsage(beforeMillis: Long) {
        writableDatabase.delete("usage_log", "created_at<?", arrayOf(beforeMillis.toString()))
    }

    fun updateChatPreset(chatId: Long, presetId: Long) {
        writableDatabase.update(
            "chats",
            ContentValues().apply { put("preset_id", presetId) },
            "id=?",
            arrayOf(chatId.toString())
        )
    }

    // ---------------- chats ----------------

    fun createChat(cardId: Long, title: String, greeting: String, model: String): Long {
        val now = System.currentTimeMillis()
        val chatId = writableDatabase.insertOrThrow(
            "chats", null,
            ContentValues().apply {
                put("card_id", cardId)
                put("title", title)
                put("created_at", now)
                put("updated_at", now)
                put("last_message", greeting.replace("\n", " ").take(120))
                put("model", model)
            }
        )
        if (greeting.isNotBlank()) {
            insertMessage(chatId, "assistant", greeting, "")
        }
        return chatId
    }

    fun listChats(cardId: Long): List<Chat> {
        val result = mutableListOf<Chat>()
        readableDatabase.query(
            "chats", null, "card_id=?", arrayOf(cardId.toString()),
            null, null, "updated_at DESC"
        ).use { c ->
            while (c.moveToNext()) result.add(readChat(c))
        }
        return result
    }

    /** E3：多角色会话绑定的卡 id（按顺序）。 */
    fun getGroupCardIds(chatId: Long): List<Long> {
        val result = mutableListOf<Long>()
        readableDatabase.query(
            "chat_cards", arrayOf("card_id"), "chat_id=?",
            arrayOf(chatId.toString()), null, null, "sort ASC"
        ).use { c ->
            while (c.moveToNext()) result.add(c.getLong(0))
        }
        return result
    }

    /** E3：设置多角色会话的卡（2~4 张）。 */
    fun setGroupCards(chatId: Long, cardIds: List<Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("chat_cards", "chat_id=?", arrayOf(chatId.toString()))
            cardIds.forEachIndexed { index, cardId ->
                db.insertWithOnConflict(
                    "chat_cards", null,
                    ContentValues().apply {
                        put("chat_id", chatId)
                        put("card_id", cardId)
                        put("sort", index)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 全局会话列表（跨所有角色卡）。 */    fun listAllChats(): List<ChatListItem> {
        val result = mutableListOf<ChatListItem>()
        readableDatabase.rawQuery(
            """SELECT ch.*, c.name AS card_name FROM chats ch
               LEFT JOIN cards c ON c.id = ch.card_id
               ORDER BY ch.updated_at DESC""",
            null
        ).use { c ->
            while (c.moveToNext()) {
                result.add(ChatListItem(readChat(c), c.getString(c.getColumnIndexOrThrow("card_name")) ?: ""))
            }
        }
        return result
    }

    /** 跨会话搜索消息内容。 */
    fun searchMessages(query: String, limit: Int = 100): List<MessageHit> {
        val result = mutableListOf<MessageHit>()
        readableDatabase.rawQuery(
            """SELECT m.chat_id, m.role, m.content, m.created_at,
                      ch.title AS chat_title, c.name AS card_name
               FROM messages m
               JOIN chats ch ON ch.id = m.chat_id
               LEFT JOIN cards c ON c.id = ch.card_id
               WHERE m.content LIKE ?
               ORDER BY m.created_at DESC LIMIT ?""",
            arrayOf("%$query%", limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                result.add(
                    MessageHit(
                        chatId = c.getLong(c.getColumnIndexOrThrow("chat_id")),
                        chatTitle = c.getString(c.getColumnIndexOrThrow("chat_title")) ?: "",
                        cardName = c.getString(c.getColumnIndexOrThrow("card_name")) ?: "",
                        role = c.getString(c.getColumnIndexOrThrow("role")) ?: "",
                        content = c.getString(c.getColumnIndexOrThrow("content")) ?: "",
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        return result
    }

    fun getChat(id: Long): Chat? {
        readableDatabase.query("chats", null, "id=?", arrayOf(id.toString()), null, null, null).use { c ->
            return if (c.moveToFirst()) readChat(c) else null
        }
    }

    fun renameChat(id: Long, title: String) {
        writableDatabase.update(
            "chats",
            ContentValues().apply { put("title", title) },
            "id=?",
            arrayOf(id.toString())
        )
    }

    fun deleteChat(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("chat_memory", "chat_id=?", arrayOf(id.toString()))
            db.delete("checkpoints", "chat_id=?", arrayOf(id.toString()))
            db.delete("panel_state", "chat_id=?", arrayOf(id.toString()))
            db.delete("chat_variables", "chat_id=?", arrayOf(id.toString()))
            db.delete("chat_cards", "chat_id=?", arrayOf(id.toString()))
            db.delete("messages", "chat_id=?", arrayOf(id.toString()))
            db.delete("chats", "id=?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun touchChat(chatId: Long, lastMessage: String, model: String) {
        writableDatabase.update(
            "chats",
            ContentValues().apply {
                put("updated_at", System.currentTimeMillis())
                put("last_message", lastMessage.replace("\n", " ").take(120))
                put("model", model)
            },
            "id=?",
            arrayOf(chatId.toString())
        )
    }

    // ---------------- messages ----------------

    fun getMessages(chatId: Long): List<Message> {
        val result = mutableListOf<Message>()
        readableDatabase.query(
            "messages", null, "chat_id=?", arrayOf(chatId.toString()),
            null, null, "id ASC"
        ).use { c ->
            while (c.moveToNext()) result.add(readMessage(c))
        }
        return result
    }

    /**
     * 分页读取消息：limit>0 时取「beforeId 之前最近的 limit 条」，返回按 id 升序。
     * limit=0 时取全部（老行为）。
     */
    fun getMessages(chatId: Long, limit: Int, beforeId: Long = 0L): List<Message> {
        if (limit <= 0) return getMessages(chatId)
        val result = mutableListOf<Message>()
        val selection = if (beforeId > 0) "chat_id=? AND id<?" else "chat_id=?"
        val args = if (beforeId > 0) {
            arrayOf(chatId.toString(), beforeId.toString())
        } else {
            arrayOf(chatId.toString())
        }
        readableDatabase.query(
            "messages", null, selection, args, null, null, "id DESC LIMIT $limit"
        ).use { c ->
            while (c.moveToNext()) result.add(readMessage(c))
        }
        result.reverse()
        return result
    }

    fun insertMessage(
        chatId: Long,
        role: String,
        content: String,
        reasoning: String,
        promptTokens: Int = 0,
        completionTokens: Int = 0
    ): Long {
        val now = System.currentTimeMillis()
        val variants = listOf(MessageVariant(content, reasoning, now))
        return writableDatabase.insertOrThrow(
            "messages", null,
            ContentValues().apply {
                put("chat_id", chatId)
                put("role", role)
                put("content", content)
                put("reasoning", reasoning)
                put("created_at", now)
                put("variants", variantsToJson(variants))
                put("active_variant", 0)
                put("prompt_tokens", promptTokens)
                put("completion_tokens", completionTokens)
            }
        )
    }

    fun getMessage(id: Long): Message? {
        readableDatabase.query("messages", null, "id=?", arrayOf(id.toString()), null, null, null).use { c ->
            return if (c.moveToFirst()) readMessage(c) else null
        }
    }

    fun updateMessage(id: Long, content: String) {
        writableDatabase.update(
            "messages",
            ContentValues().apply { put("content", content) },
            "id=?",
            arrayOf(id.toString())
        )
    }

    /** 写入某条消息的全部版本，并把 content / reasoning 同步成当前版本。 */
    fun updateMessageVariants(id: Long, variants: List<MessageVariant>, active: Int) {
        if (variants.isEmpty()) return
        val safeActive = active.coerceIn(0, variants.size - 1)
        val current = variants[safeActive]
        writableDatabase.update(
            "messages",
            ContentValues().apply {
                put("variants", variantsToJson(variants))
                put("active_variant", safeActive)
                put("content", current.content)
                put("reasoning", current.reasoning)
            },
            "id=?",
            arrayOf(id.toString())
        )
    }

    fun updateMessageDebug(id: Long, debug: String) {
        writableDatabase.update(
            "messages",
            ContentValues().apply { put("debug", debug) },
            "id=?",
            arrayOf(id.toString())
        )
    }

    fun updateMessageUsage(id: Long, promptTokens: Int, completionTokens: Int) {
        writableDatabase.update(
            "messages",
            ContentValues().apply {
                put("prompt_tokens", promptTokens)
                put("completion_tokens", completionTokens)
            },
            "id=?",
            arrayOf(id.toString())
        )
    }

    fun deleteMessage(id: Long) {
        writableDatabase.delete("messages", "id=?", arrayOf(id.toString()))
    }

    // ---------------- memory ----------------

    fun getMemory(chatId: Long): ChatMemory? {
        readableDatabase.query(
            "chat_memory", null, "chat_id=?", arrayOf(chatId.toString()), null, null, null
        ).use { c ->
            if (!c.moveToFirst()) return null
            return ChatMemory(
                chatId = c.getLong(c.getColumnIndexOrThrow("chat_id")),
                summary = c.getString(c.getColumnIndexOrThrow("summary")) ?: "",
                msgUptoId = c.getLong(c.getColumnIndexOrThrow("msg_upto_id")),
                updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
            )
        }
    }

    fun saveMemory(chatId: Long, summary: String, msgUptoId: Long) {
        writableDatabase.insertWithOnConflict(
            "chat_memory", null,
            ContentValues().apply {
                put("chat_id", chatId)
                put("summary", summary)
                put("msg_upto_id", msgUptoId)
                put("updated_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun clearMemory(chatId: Long) {
        writableDatabase.delete("chat_memory", "chat_id=?", arrayOf(chatId.toString()))
    }

    // ---------------- branch / checkpoints ----------------

    /** 从某条消息分支：把该消息（含）之前的历史复制成一个新会话，原会话保留。 */
    fun branchChat(sourceChatId: Long, uptoMessageId: Long): Long? {
        val source = getChat(sourceChatId) ?: return null
        val messages = getMessages(sourceChatId)
        val index = messages.indexOfFirst { it.id == uptoMessageId }
        if (index < 0) return null
        val subset = messages.take(index + 1)
        val memory = getMemory(sourceChatId)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            val newChatId = db.insertOrThrow(
                "chats", null,
                ContentValues().apply {
                    put("card_id", source.cardId)
                    put("title", source.title + " · 分支")
                    put("created_at", now)
                    put("updated_at", now)
                    put("last_message", subset.lastOrNull()?.content?.replace("\n", " ")?.take(120) ?: "")
                    put("model", source.model)
                    put("preset_id", source.presetId)
                    put("branched_from", source.id)
                }
            )
            val idMap = mutableMapOf<Long, Long>()
            subset.forEach { message ->
                val newId = db.insertOrThrow(
                    "messages", null,
                    ContentValues().apply {
                        put("chat_id", newChatId)
                        put("role", message.role)
                        put("content", message.content)
                        put("reasoning", message.reasoning)
                        put("created_at", message.createdAt)
                        put("variants", variantsToJson(message.allVariants))
                        put("active_variant", message.activeVariant)
                        put("debug", message.debug)
                        put("prompt_tokens", message.promptTokens)
                        put("completion_tokens", message.completionTokens)
                    }
                )
                idMap[message.id] = newId
            }
            if (memory != null && memory.summary.isNotBlank()) {
                val mappedUpto = if (memory.msgUptoId > 0) idMap[memory.msgUptoId] ?: 0L else 0L
                db.insertWithOnConflict(
                    "chat_memory", null,
                    ContentValues().apply {
                        put("chat_id", newChatId)
                        put("summary", memory.summary)
                        put("msg_upto_id", mappedUpto)
                        put("updated_at", now)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
            return newChatId
        } finally {
            db.endTransaction()
        }
    }

    fun saveCheckpoint(chatId: Long, messageId: Long, name: String): Long =
        writableDatabase.insertOrThrow(
            "checkpoints", null,
            ContentValues().apply {
                put("chat_id", chatId)
                put("message_id", messageId)
                put("name", name.ifBlank { "检查点" })
                put("created_at", System.currentTimeMillis())
            }
        )

    fun listCheckpoints(chatId: Long): List<Checkpoint> {
        val result = mutableListOf<Checkpoint>()
        readableDatabase.query(
            "checkpoints", null, "chat_id=?", arrayOf(chatId.toString()),
            null, null, "id DESC"
        ).use { c ->
            while (c.moveToNext()) result.add(readCheckpoint(c))
        }
        return result
    }

    fun getCheckpoint(id: Long): Checkpoint? {
        readableDatabase.query("checkpoints", null, "id=?", arrayOf(id.toString()), null, null, null).use { c ->
            return if (c.moveToFirst()) readCheckpoint(c) else null
        }
    }

    fun deleteCheckpoint(id: Long) {
        writableDatabase.delete("checkpoints", "id=?", arrayOf(id.toString()))
    }

    /** 回到检查点：删除该检查点消息之后的内容（不可恢复，UI 里要二次确认）。 */
    fun restoreCheckpoint(checkpointId: Long): Boolean {
        val checkpoint = getCheckpoint(checkpointId) ?: return false
        writableDatabase.delete(
            "messages",
            "chat_id=? AND id>?",
            arrayOf(checkpoint.chatId.toString(), checkpoint.messageId.toString())
        )
        val last = getMessages(checkpoint.chatId).lastOrNull()
        writableDatabase.update(
            "chats",
            ContentValues().apply {
                put("updated_at", System.currentTimeMillis())
                put("last_message", (last?.content ?: "").replace("\n", " ").take(120))
            },
            "id=?",
            arrayOf(checkpoint.chatId.toString())
        )
        return true
    }

    // ---------------- export / import ----------------

    fun exportCard(cardId: Long): String? {
        val card = getCard(cardId) ?: return null
        return JsonCodec.buildCharaCard(card, getEntries(cardId))
    }

    fun dumpJson(): String {
        val root = JSONObject()
        root.put("app", "dsh-tavern")
        root.put("version", 3)
        root.put("exported_at", System.currentTimeMillis())
        val presets = listPresets()
        val presetNameById = presets.associate { it.id to it.name }
        val presetsArr = JSONArray()
        presets.forEach { presetsArr.put(presetToJson(it)) }
        root.put("presets", presetsArr)
        val cardsArr = JSONArray()
        listCards().forEach { item ->
            val card = item.card
            val cj = JsonCodec.cardToJson(card, withAvatar = true)
            val entriesArr = JSONArray()
            getEntries(card.id).forEach { entriesArr.put(JsonCodec.entryToJson(it)) }
            cj.put("world_entries", entriesArr)

            val chatsArr = JSONArray()
            listChats(card.id).forEach { chat ->
                val chj = JSONObject()
                    .put("title", chat.title)
                    .put("created_at", chat.createdAt)
                    .put("updated_at", chat.updatedAt)
                    .put("last_message", chat.lastMessage)
                    .put("model", chat.model)
                    .put("preset_id", chat.presetId)
                    .put("preset_name", presetNameById[chat.presetId] ?: "")
                    .put("branched_from", chat.branchedFrom)
                getMemory(chat.id)?.let { mem ->
                    if (mem.summary.isNotBlank()) {
                        chj.put(
                            "memory",
                            JSONObject()
                                .put("summary", mem.summary)
                                .put("msg_upto_id", mem.msgUptoId)
                                .put("updated_at", mem.updatedAt)
                        )
                    }
                }
                getPanelState(chat.id)?.takeIf { it.isNotBlank() }?.let { chj.put("panel_state", it) }
                val checkpointsArr = JSONArray()
                listCheckpoints(chat.id).forEach { cp ->
                    checkpointsArr.put(
                        JSONObject()
                            .put("name", cp.name)
                            .put("message_id", cp.messageId)
                            .put("created_at", cp.createdAt)
                    )
                }
                chj.put("checkpoints", checkpointsArr)
                val msgs = JSONArray()
                getMessages(chat.id).forEach { m ->
                    msgs.put(
                        JSONObject()
                            .put("id", m.id)
                            .put("role", m.role)
                            .put("content", m.content)
                            .put("reasoning", m.reasoning)
                            .put("created_at", m.createdAt)
                            .put("variants", variantsToJson(m.allVariants))
                            .put("active_variant", m.activeVariant)
                            .put("debug", m.debug)
                            .put("prompt_tokens", m.promptTokens)
                            .put("completion_tokens", m.completionTokens)
                    )
                }
                chj.put("messages", msgs)
                chatsArr.put(chj)
            }
            cj.put("chats", chatsArr)
            cardsArr.put(cj)
        }
        root.put("cards", cardsArr)
        return root.toString()
    }

    fun restoreJson(text: String): Int {
        val root = JSONObject(text)
        if (root.optString("app") != "dsh-tavern") {
            throw IllegalArgumentException("不是私酒馆的备份文件")
        }
        val arr = root.optJSONArray("cards") ?: return 0
        var count = 0
        val db = writableDatabase
        val now = System.currentTimeMillis()
        val presetNameToId = mutableMapOf<String, Long>()
        db.beginTransaction()
        try {
            root.optJSONArray("presets")?.let { presetsArr ->
                for (p in 0 until presetsArr.length()) {
                    val pj = presetsArr.optJSONObject(p) ?: continue
                    val preset = presetFromJson(pj)
                    if (preset.name.isBlank()) continue
                    val newId = db.insert("presets", null, presetValues(preset, now))
                    presetNameToId[preset.name] = newId
                }
            }
            for (i in 0 until arr.length()) {
                val cj = arr.optJSONObject(i) ?: continue
                val card = JsonCodec.cardFromBackup(cj)
                if (card.name.isBlank()) continue
                val now = System.currentTimeMillis()
                val cardId = db.insertOrThrow("cards", null, cardValues(card, now))
                JsonCodec.entriesFromJson(cj.optJSONArray("world_entries")).forEach { e ->
                    db.insert("world_entries", null, entryValues(e.copy(cardId = cardId)))
                }
                val chats = cj.optJSONArray("chats")
                if (chats != null) {
                    for (j in 0 until chats.length()) {
                        val chj = chats.optJSONObject(j) ?: continue
                        val chatId = db.insertOrThrow(
                            "chats", null,
                            ContentValues().apply {
                                put("card_id", cardId)
                                put("title", chj.optString("title", "对话"))
                                put("created_at", chj.optLong("created_at", now))
                                put("updated_at", chj.optLong("updated_at", now))
                                put("last_message", chj.optString("last_message", ""))
                                put("model", chj.optString("model", ""))
                                put(
                                    "preset_id",
                                    presetNameToId[chj.optString("preset_name", "")] ?: 0L
                                )
                                put("branched_from", 0L)
                            }
                        )
                        val idMap = mutableMapOf<Long, Long>()
                        val msgs = chj.optJSONArray("messages")
                        if (msgs != null) {
                            for (k in 0 until msgs.length()) {
                                val mj = msgs.optJSONObject(k) ?: continue
                                val mContent = mj.optString("content", "")
                                val mCreated = mj.optLong("created_at", now)
                                val mVariants = parseVariants(mj.optJSONArray("variants")?.toString())
                                    .ifEmpty {
                                        listOf(
                                            MessageVariant(mContent, mj.optString("reasoning", ""), mCreated)
                                        )
                                    }
                                val mActive = mj.optInt("active_variant", 0)
                                    .coerceIn(0, mVariants.size - 1)
                                val newMsgId = db.insert(
                                    "messages", null,
                                    ContentValues().apply {
                                        put("chat_id", chatId)
                                        put("role", mj.optString("role", "assistant"))
                                        put("content", mVariants[mActive].content)
                                        put("reasoning", mVariants[mActive].reasoning)
                                        put("created_at", mCreated)
                                        put("variants", variantsToJson(mVariants))
                                        put("active_variant", mActive)
                                        put("debug", mj.optString("debug", ""))
                                        put("prompt_tokens", mj.optInt("prompt_tokens", 0))
                                        put("completion_tokens", mj.optInt("completion_tokens", 0))
                                    }
                                )
                                val oldId = mj.optLong("id", -1L)
                                if (oldId > 0 && newMsgId > 0) idMap[oldId] = newMsgId
                            }
                        }
                        // 检查点（消息 id 映射到新 id）
                        chj.optJSONArray("checkpoints")?.let { cps ->
                            for (c in 0 until cps.length()) {
                                val cpj = cps.optJSONObject(c) ?: continue
                                val oldMessageId = cpj.optLong("message_id", 0L)
                                val newMessageId = idMap[oldMessageId] ?: continue
                                db.insert(
                                    "checkpoints", null,
                                    ContentValues().apply {
                                        put("chat_id", chatId)
                                        put("name", cpj.optString("name", "检查点"))
                                        put("message_id", newMessageId)
                                        put("created_at", cpj.optLong("created_at", now))
                                    }
                                )
                            }
                        }
                        chj.optString("panel_state", "").takeIf { it.isNotBlank() }?.let { panelJson ->
                            db.insertWithOnConflict(
                                "panel_state", null,
                                ContentValues().apply {
                                    put("chat_id", chatId)
                                    put("json", panelJson)
                                    put("updated_at", now)
                                },
                                SQLiteDatabase.CONFLICT_REPLACE
                            )
                        }
                        val memj = chj.optJSONObject("memory")
                        if (memj != null && memj.optString("summary", "").isNotBlank()) {
                            db.insertWithOnConflict(
                                "chat_memory", null,
                                ContentValues().apply {
                                    put("chat_id", chatId)
                                    put("summary", memj.optString("summary", ""))
                                    // 恢复后消息 id 会重排，摘要不再按 id 截断历史（摘要 + 全部原文同时存在）
                                    put("msg_upto_id", 0L)
                                    put("updated_at", memj.optLong("updated_at", now))
                                },
                                SQLiteDatabase.CONFLICT_REPLACE
                            )
                        }
                    }
                }
                count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    // ---------------- row helpers ----------------

    private fun presetToJson(p: Preset): JSONObject = JSONObject()
        .put("name", p.name)
        .put("system_prompt", p.systemPrompt)
        .put("post_history", p.postHistory)
        .put("temperature", p.temperature.toDouble())
        .put("top_p", p.topP.toDouble())
        .put("max_tokens", p.maxTokens)
        .put("style", p.style)
        .put("built_in", p.builtIn)
        .put("stop_sequences", p.stopSequences)
        .put("frequency_penalty", p.frequencyPenalty.toDouble())
        .put("presence_penalty", p.presencePenalty.toDouble())

    private fun presetFromJson(o: JSONObject): Preset = Preset(
        name = o.optString("name", ""),
        systemPrompt = o.optString("system_prompt", ""),
        postHistory = o.optString("post_history", ""),
        temperature = o.optDouble("temperature", 1.0).toFloat(),
        topP = o.optDouble("top_p", 1.0).toFloat(),
        maxTokens = o.optInt("max_tokens", 4096),
        style = o.optString("style", "标准"),
        builtIn = o.optBoolean("built_in", false),
        stopSequences = o.optString("stop_sequences", ""),
        frequencyPenalty = o.optDouble("frequency_penalty", 0.0).toFloat(),
        presencePenalty = o.optDouble("presence_penalty", 0.0).toFloat()
    )

    private fun cardValues(card: CharacterCard, now: Long): ContentValues = ContentValues().apply {
        put("name", card.name)
        put("description", card.description)
        put("personality", card.personality)
        put("scenario", card.scenario)
        put("system_prompt", card.systemPrompt)
        put("post_history", card.postHistoryInstructions)
        put("creator_notes", card.creatorNotes)
        put("first_mes", card.firstMes)
        put("mes_example", card.mesExample)
        put("alternate_greetings", JSONArray(card.alternateGreetings).toString())
        put("tags", JSONArray(card.tags).toString())
        put("suggested_replies", JSONArray(card.suggestedReplies).toString())
        put("creator", card.creator)
        put("character_version", card.characterVersion)
        put("raw_json", card.rawJson)
        put("status_template", card.statusTemplate)
        put("default_preset_id", card.defaultPresetId)
        put("regex_rules", card.regexRules)
        put("variables_text", card.variablesText)
        if (card.avatar != null) put("avatar", card.avatar) else putNull("avatar")
        put("created_at", if (card.createdAt > 0) card.createdAt else now)
        put("updated_at", now)
    }

    private fun entryValues(e: WorldEntry): ContentValues = ContentValues().apply {
        put("card_id", e.cardId)
        put("name", e.name)
        put("keys", JSONArray(e.keys).toString())
        put("content", e.content)
        put("constant", if (e.constant) 1 else 0)
        put("selective", if (e.selective) 1 else 0)
        put("selective_logic", e.selectiveLogic)
        put("priority", e.priority)
        put("insertion_order", e.insertionOrder)
        put("enabled", if (e.enabled) 1 else 0)
        put("case_sensitive", if (e.caseSensitive) 1 else 0)
        put("position", e.position)
        put("probability", e.probability.coerceIn(0, 100))
        put("depth", e.depth.coerceAtLeast(0))
        put("secondary_keys", JSONArray(e.secondaryKeys).toString())
        put("regex", if (e.regex) 1 else 0)
    }

    private fun readCard(c: Cursor): CharacterCard = CharacterCard(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        name = c.getString(c.getColumnIndexOrThrow("name")) ?: "",
        description = c.getString(c.getColumnIndexOrThrow("description")) ?: "",
        personality = c.getString(c.getColumnIndexOrThrow("personality")) ?: "",
        scenario = c.getString(c.getColumnIndexOrThrow("scenario")) ?: "",
        systemPrompt = c.getString(c.getColumnIndexOrThrow("system_prompt")) ?: "",
        postHistoryInstructions = c.getString(c.getColumnIndexOrThrow("post_history")) ?: "",
        creatorNotes = c.getString(c.getColumnIndexOrThrow("creator_notes")) ?: "",
        firstMes = c.getString(c.getColumnIndexOrThrow("first_mes")) ?: "",
        mesExample = c.getString(c.getColumnIndexOrThrow("mes_example")) ?: "",
        alternateGreetings = JsonCodec.parseStringList(
            runCatching { JSONArray(c.getString(c.getColumnIndexOrThrow("alternate_greetings")) ?: "[]") }.getOrNull()
        ),
        tags = JsonCodec.parseStringList(
            runCatching { JSONArray(c.getString(c.getColumnIndexOrThrow("tags")) ?: "[]") }.getOrNull()
        ),
        suggestedReplies = JsonCodec.parseStringList(
            runCatching { JSONArray(c.getString(c.getColumnIndexOrThrow("suggested_replies")) ?: "[]") }.getOrNull()
        ),
        creator = c.getString(c.getColumnIndexOrThrow("creator")) ?: "",
        characterVersion = c.getString(c.getColumnIndexOrThrow("character_version")) ?: "",
        rawJson = c.getString(c.getColumnIndexOrThrow("raw_json")) ?: "",
        statusTemplate = c.getString(c.getColumnIndexOrThrow("status_template")) ?: "",
        defaultPresetId = c.getLong(c.getColumnIndexOrThrow("default_preset_id")),
        regexRules = c.getString(c.getColumnIndexOrThrow("regex_rules")) ?: "",
        variablesText = c.getString(c.getColumnIndexOrThrow("variables_text")) ?: "",
        avatar = c.getBlob(c.getColumnIndexOrThrow("avatar")),
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
    )

    private fun readEntry(c: Cursor): WorldEntry = WorldEntry(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        cardId = c.getLong(c.getColumnIndexOrThrow("card_id")),
        name = c.getString(c.getColumnIndexOrThrow("name")) ?: "",
        keys = JsonCodec.parseStringList(
            runCatching { JSONArray(c.getString(c.getColumnIndexOrThrow("keys")) ?: "[]") }.getOrNull()
        ),
        content = c.getString(c.getColumnIndexOrThrow("content")) ?: "",
        constant = c.getInt(c.getColumnIndexOrThrow("constant")) == 1,
        selective = c.getInt(c.getColumnIndexOrThrow("selective")) == 1,
        selectiveLogic = c.getInt(c.getColumnIndexOrThrow("selective_logic")),
        priority = c.getInt(c.getColumnIndexOrThrow("priority")),
        insertionOrder = c.getInt(c.getColumnIndexOrThrow("insertion_order")),
        enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) == 1,
        caseSensitive = c.getInt(c.getColumnIndexOrThrow("case_sensitive")) == 1,
        position = c.getString(c.getColumnIndexOrThrow("position")) ?: "after_char",
        probability = c.getInt(c.getColumnIndexOrThrow("probability")).coerceIn(0, 100),
        depth = c.getInt(c.getColumnIndexOrThrow("depth")).coerceAtLeast(0),
        secondaryKeys = JsonCodec.parseStringList(
            runCatching { JSONArray(c.getString(c.getColumnIndexOrThrow("secondary_keys")) ?: "[]") }.getOrNull()
        ),
        regex = c.getInt(c.getColumnIndexOrThrow("regex")) == 1
    )

    private fun readChat(c: Cursor): Chat = Chat(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        cardId = c.getLong(c.getColumnIndexOrThrow("card_id")),
        title = c.getString(c.getColumnIndexOrThrow("title")) ?: "",
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
        lastMessage = c.getString(c.getColumnIndexOrThrow("last_message")) ?: "",
        model = c.getString(c.getColumnIndexOrThrow("model")) ?: "",
        presetId = c.getLong(c.getColumnIndexOrThrow("preset_id")),
        branchedFrom = c.getLong(c.getColumnIndexOrThrow("branched_from"))
    )

    private fun readCheckpoint(c: Cursor): Checkpoint = Checkpoint(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        chatId = c.getLong(c.getColumnIndexOrThrow("chat_id")),
        name = c.getString(c.getColumnIndexOrThrow("name")) ?: "",
        messageId = c.getLong(c.getColumnIndexOrThrow("message_id")),
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
    )

    private fun readPreset(c: Cursor): Preset = Preset(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        name = c.getString(c.getColumnIndexOrThrow("name")) ?: "",
        systemPrompt = c.getString(c.getColumnIndexOrThrow("system_prompt")) ?: "",
        postHistory = c.getString(c.getColumnIndexOrThrow("post_history")) ?: "",
        temperature = c.getFloat(c.getColumnIndexOrThrow("temperature")),
        topP = c.getFloat(c.getColumnIndexOrThrow("top_p")),
        maxTokens = c.getInt(c.getColumnIndexOrThrow("max_tokens")),
        style = c.getString(c.getColumnIndexOrThrow("style")) ?: "标准",
        builtIn = c.getInt(c.getColumnIndexOrThrow("built_in")) == 1,
        stopSequences = c.getString(c.getColumnIndexOrThrow("stop_sequences")) ?: "",
        frequencyPenalty = c.getFloat(c.getColumnIndexOrThrow("frequency_penalty")),
        presencePenalty = c.getFloat(c.getColumnIndexOrThrow("presence_penalty")),
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
    )

    private fun presetValues(p: Preset, now: Long): ContentValues = ContentValues().apply {
        put("name", p.name)
        put("system_prompt", p.systemPrompt)
        put("post_history", p.postHistory)
        put("temperature", p.temperature)
        put("top_p", p.topP)
        put("max_tokens", p.maxTokens)
        put("style", p.style)
        put("built_in", if (p.builtIn) 1 else 0)
        put("stop_sequences", p.stopSequences)
        put("frequency_penalty", p.frequencyPenalty)
        put("presence_penalty", p.presencePenalty)
        put("created_at", if (p.createdAt > 0) p.createdAt else now)
        put("updated_at", now)
    }

    private fun readMessage(c: Cursor): Message {
        val content = c.getString(c.getColumnIndexOrThrow("content")) ?: ""
        val reasoning = c.getString(c.getColumnIndexOrThrow("reasoning")) ?: ""
        val createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
        val variants = parseVariants(c.getString(c.getColumnIndexOrThrow("variants")))
            .ifEmpty { listOf(MessageVariant(content, reasoning, createdAt)) }
        val active = c.getInt(c.getColumnIndexOrThrow("active_variant")).coerceIn(0, variants.size - 1)
        return Message(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            chatId = c.getLong(c.getColumnIndexOrThrow("chat_id")),
            role = c.getString(c.getColumnIndexOrThrow("role")) ?: "assistant",
            content = variants[active].content,
            reasoning = variants[active].reasoning,
            createdAt = createdAt,
            variants = variants,
            activeVariant = active,
            debug = c.getString(c.getColumnIndexOrThrow("debug")) ?: "",
            promptTokens = c.getInt(c.getColumnIndexOrThrow("prompt_tokens")),
            completionTokens = c.getInt(c.getColumnIndexOrThrow("completion_tokens"))
        )
    }

    private fun variantsToJson(variants: List<MessageVariant>): String {
        val arr = JSONArray()
        variants.forEach { v ->
            arr.put(
                JSONObject()
                    .put("content", v.content)
                    .put("reasoning", v.reasoning)
                    .put("created_at", v.createdAt)
            )
        }
        return arr.toString()
    }

    private fun parseVariants(json: String?): List<MessageVariant> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                MessageVariant(
                    content = o.optString("content", ""),
                    reasoning = o.optString("reasoning", ""),
                    createdAt = o.optLong("created_at", 0)
                )
            }
        }.getOrDefault(emptyList())
    }
}
