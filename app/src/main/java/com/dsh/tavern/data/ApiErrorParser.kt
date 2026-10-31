package com.dsh.tavern.data

import org.json.JSONObject

/** API 报错解析结果：友好提示 + 接口明确告知的可用模型。 */
data class ApiErrorInfo(
    val friendly: String,
    val httpCode: Int,
    val failedModel: String,
    val supportedModels: List<String>
)

object ApiErrorParser {

    /** 从报错里解析接口声明的最大上下文 token 数（如 "maximum context length is 65536 tokens"）。 */
    fun parseContextLimit(message: String): Int? {
        if (message.isBlank()) return null
        val patterns = listOf(
            Regex("(?i)maximum context length is\\s*(\\d+)"),
            Regex("(?i)max(?:imum)? context length[^0-9]{0,24}(\\d{3,})"),
            Regex("(?i)context length is\\s*(\\d+)"),
            Regex("(?i)context window[^0-9]{0,24}(\\d{3,})"),
            Regex("(?i)maximum context[^0-9]{0,24}(\\d{3,})")
        )
        patterns.forEach { pattern ->
            val value = pattern.find(message)?.groupValues?.get(1)?.toIntOrNull()
            if (value != null && value in 1_024..2_000_000) return value
        }
        return null
    }

    /**
     * 解析 OpenAI 兼容接口的错误体。
     * 典型：{"error":{"message":"The supported API model names are deepseek-flash, deepseek-v4-pro, but you passed deepseek-v4.1-flash",...}}
     */
    fun parse(httpCode: Int, raw: String, currentModel: String): ApiErrorInfo {
        val message = runCatching {
            val obj = JSONObject(raw)
            obj.optJSONObject("error")?.optString("message").orEmpty()
                .ifBlank { obj.optString("error").orEmpty() }
                .ifBlank { obj.optString("message").orEmpty() }
        }.getOrDefault("").ifBlank { raw.trim().take(400) }

        val supported = parseSupportedModels(message)
        val friendly = if (supported.isNotEmpty()) {
            buildString {
                append("当前模型「").append(currentModel).append("」不被这个接口支持。\n\n")
                append("该接口可用的模型：").append(supported.joinToString("、")).append("。\n\n")
                append("点下面的模型名可直接切换并重试；也可以到「设置 → 供应商」修改模型名。")
            }
        } else {
            buildString {
                append("请求失败（HTTP ").append(httpCode).append("）")
                if (message.isNotBlank()) append("：").append(message.take(400))
            }
        }
        return ApiErrorInfo(friendly, httpCode, currentModel, supported)
    }

    /** 从报错文案里提取 "supported API model names are X, Y" 里的模型名列表。 */
    fun parseSupportedModels(message: String): List<String> {
        if (message.isBlank()) return emptyList()
        val match = Regex(
            "(?i)(?:supported\\s+(?:api\\s+)?model\\s+names?\\s+(?:are|is)|supported\\s+models?\\s+(?:are|is))" +
                "\\s*[:：]?\\s*(.+)$"
        ).find(message) ?: return emptyList()
        return match.groupValues[1]
            .substringBefore(';')
            .substringBefore('.')
            .substringBefore('。')
            .split(',', '，', '、', '/', '|')
            .flatMap { it.split(Regex("(?i)\\s+or\\s+")) }
            .map { it.trim().trim('"', '\'', '`', ' ') }
            .filter { it.length in 2..64 && Regex("^[A-Za-z0-9._:\\-]+$").matches(it) }
            .distinct()
            .take(8)
    }
}
