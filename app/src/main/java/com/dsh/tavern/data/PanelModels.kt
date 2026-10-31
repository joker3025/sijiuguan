package com.dsh.tavern.data

import org.json.JSONArray
import org.json.JSONObject

/** 互动面板的一个字段（如「好感度」「时间」）。 */
data class PanelField(
    val name: String,
    val type: String = "text", // text / number / list
    val initial: String = "",
    val max: Int = 100,
    val rule: String = ""
)

/** 一个面板（如「状态」「关系」「背包」）。 */
data class PanelDef(
    val title: String,
    val fields: List<PanelField>
)

/**
 * 角色卡上的互动面板模板（P1-6，对应 DZMM 的「互动模板」）。
 * 存在卡片 `status_template` 字段里（JSON），运行时模型每轮输出 <panel>{...}</panel>。
 */
data class PanelTemplate(val panels: List<PanelDef>) {

    val isConfigured: Boolean get() = panels.isNotEmpty()

    fun fieldCount(): Int = panels.sumOf { it.fields.size }

    /** 给模型的输出格式说明。 */
    fun instruction(): String {
        val builder = StringBuilder()
        builder.append("【互动面板】每轮回复的最后，另起一行输出一个 <panel>...</panel> 块，内容是 JSON（不要用代码块包裹）：\n")
        builder.append("{\"状态\":{\"时间\":\"...\",\"地点\":\"...\"},\"关系\":{\"好感度\":35},\"背包\":[\"物品\"],\"options\":[\"下一步选项1\",\"下一步选项2\"]}\n")
        builder.append("面板模板：\n")
        panels.forEach { panel ->
            builder.append("- ").append(panel.title).append("：")
            builder.append(
                panel.fields.joinToString("、") { field ->
                    val typeLabel = when (field.type) {
                        "number" -> "数值"
                        "list" -> "列表"
                        else -> "文本"
                    }
                    "${field.name}($typeLabel)"
                }
            )
            builder.append('\n')
        }
        builder.append("规则：values 只写本轮发生变化的字段（没变化可省略）；options 给 2~4 条简短的下一步建议；<panel> 块之外不要解释。")
        return builder.toString()
    }

    fun toJson(): String {
        val root = JSONObject()
        val panelsArr = JSONArray()
        panels.forEach { panel ->
            val fieldsArr = JSONArray()
            panel.fields.forEach { field ->
                fieldsArr.put(
                    JSONObject()
                        .put("name", field.name)
                        .put("type", field.type)
                        .put("initial", field.initial)
                        .put("max", field.max)
                        .put("rule", field.rule)
                )
            }
            panelsArr.put(JSONObject().put("title", panel.title).put("fields", fieldsArr))
        }
        root.put("panels", panelsArr)
        return root.toString()
    }

    companion object {
        /** 新建模板时的示例。 */
        val EXAMPLE = """
{
  "panels": [
    {
      "title": "状态",
      "fields": [
        {"name": "时间", "type": "text", "initial": "夜晚"},
        {"name": "地点", "type": "text", "initial": "客厅"},
        {"name": "心情", "type": "text", "initial": "紧张"}
      ]
    },
    {
      "title": "关系",
      "fields": [
        {"name": "好感度", "type": "number", "initial": "20", "max": 100, "rule": "对方示好时每轮最多+10，被冒犯时下降"},
        {"name": "关系阶段", "type": "text", "initial": "陌生"}
      ]
    },
    {
      "title": "背包",
      "fields": [
        {"name": "物品", "type": "list", "initial": ""}
      ]
    }
  ]
}
        """.trimIndent()

        fun parse(json: String?): PanelTemplate? {
            if (json.isNullOrBlank()) return null
            val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
            val arr = root.optJSONArray("panels") ?: return null
            val panels = mutableListOf<PanelDef>()
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val title = p.optString("title", "").ifBlank { "面板 ${i + 1}" }
                val fieldsArr = p.optJSONArray("fields") ?: JSONArray()
                val fields = mutableListOf<PanelField>()
                for (j in 0 until fieldsArr.length()) {
                    val f = fieldsArr.optJSONObject(j) ?: continue
                    val name = f.optString("name", "")
                    if (name.isBlank()) continue
                    fields.add(
                        PanelField(
                            name = name,
                            type = f.optString("type", "text"),
                            initial = f.optString("initial", ""),
                            max = f.optInt("max", 100),
                            rule = f.optString("rule", "")
                        )
                    )
                }
                if (fields.isNotEmpty()) panels.add(PanelDef(title, fields))
            }
            return if (panels.isEmpty()) null else PanelTemplate(panels)
        }
    }
}

/** 面板的运行时状态（从模型输出的 JSON 解析而来）。 */
data class PanelRuntime(
    val values: Map<String, Map<String, String>>,
    val options: List<String>
) {
    companion object {
        fun parse(json: String?): PanelRuntime {
            if (json.isNullOrBlank()) return PanelRuntime(emptyMap(), emptyList())
            val root = runCatching { JSONObject(json) }.getOrNull()
                ?: return PanelRuntime(emptyMap(), emptyList())
            val values = mutableMapOf<String, Map<String, String>>()
            root.keys().forEach { panelKey ->
                if (panelKey == "options") return@forEach
                val obj = root.optJSONObject(panelKey) ?: return@forEach
                val panelValues = mutableMapOf<String, String>()
                obj.keys().forEach { fieldKey ->
                    val value = obj.opt(fieldKey)
                    panelValues[fieldKey] = when (value) {
                        is JSONArray -> (0 until value.length()).joinToString("、") { value.optString(it) }
                        null -> ""
                        else -> value.toString()
                    }
                }
                values[panelKey] = panelValues
            }
            val options = mutableListOf<String>()
            root.optJSONArray("options")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i).trim()
                    if (s.isNotEmpty()) options.add(s)
                }
            }
            return PanelRuntime(values, options)
        }
    }
}

/** 从模型回复里抽出面板 JSON（支持 <panel> 标签或 ```panel 代码块），返回（去掉块的正文, 面板 JSON）。 */
object PanelOutputParser {
    private val tagRegex = Regex("<panel>([\\s\\S]*?)</panel>", RegexOption.IGNORE_CASE)
    private val fenceRegex = Regex("```(?:panel|json)?\\s*\\n([\\s\\S]*?)```", RegexOption.IGNORE_CASE)

    fun extract(text: String): Pair<String, String?> {
        tagRegex.find(text)?.let { match ->
            val json = match.groupValues.getOrNull(1)?.trim().orEmpty()
            val cleaned = text.removeRange(match.range).trim()
            return cleaned to json.ifBlank { null }
        }
        // 有些模型会输出 ```panel {...}``` 或 ```json {...}```；只在内容像面板 JSON 时才剥掉
        fenceRegex.findAll(text).toList().asReversed().forEach { match ->
            val body = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (body.startsWith("{") && body.contains("\"options\"")) {
                val cleaned = text.removeRange(match.range).trim()
                return cleaned to body
            }
        }
        return text to null
    }
}
