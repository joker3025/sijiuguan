package com.dsh.tavern.data

/** 模型回复里的变量更新：<setvar name="好感度">35</setvar>。 */
object VariableParser {
    private val setVarRegex = Regex(
        "<setvar\\s+name=[\"']([^\"']+)[\"']\\s*>(.*?)</setvar>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** 返回（去掉 setvar 标签后的正文, 变量更新表）。 */
    fun extract(text: String): Pair<String, Map<String, String>> {
        val updates = mutableMapOf<String, String>()
        val cleaned = setVarRegex.replace(text) { match ->
            val name = match.groupValues.getOrNull(1)?.trim().orEmpty()
            val value = match.groupValues.getOrNull(2)?.trim().orEmpty()
            if (name.isNotEmpty()) updates[name] = value
            ""
        }.trim()
        return cleaned to updates
    }

    /** 解析卡片的初始变量（每行「名字=值」或「名字: 值」）。 */
    fun parseInitial(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        text.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach
            val eq = trimmed.indexOf('=')
            val colon = trimmed.indexOf('：').takeIf { it >= 0 } ?: trimmed.indexOf(':')
            val index = when {
                eq > 0 && (colon <= 0 || eq < colon) -> eq
                colon > 0 -> colon
                else -> -1
            }
            if (index > 0) {
                val name = trimmed.substring(0, index).trim()
                val value = trimmed.substring(index + 1).trim()
                if (name.isNotEmpty()) result[name] = value
            }
        }
        return result
    }
}
