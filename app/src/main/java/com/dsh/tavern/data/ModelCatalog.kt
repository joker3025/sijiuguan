package com.dsh.tavern.data

/** 已知模型的上下文 / 最大输出规格（来自供应商文档）。 */
data class ModelSpec(
    val contextLimit: Int,
    val maxOutput: Int
)

/**
 * 模型能力表：切换模型时据此自动设置「上下文上限 / 单次最大输出」。
 * 只列确认过官方文档的型号；未知型号不猜，交给 /models 返回的 context_length 或用户手填。
 */
object ModelCatalog {
    private const val ONE_MILLION = 1_000_000
    private const val OUTPUT_384K = 393_216

    /** 切换模型对话框里的常用预设（新模型在前）。 */
    val knownIds = listOf(
        "deepseek-flash",
        "deepseek-v4-pro",
        "deepseek-v4.1-flash",
        "deepseek-v4.1-flash-1017",
        "deepseek-v4.1",
        "deepseek-chat",
        "deepseek-reasoner"
    )

    fun specFor(model: String): ModelSpec? {
        val m = model.trim().lowercase()
        if (m.isEmpty()) return null
        return when {
            // DeepSeek V4 系列（含 flash / v4-pro）：1M 上下文，384K 最大输出
            m.contains("deepseek-v4") ||
                m.contains("deepseek v4") ||
                m.contains("deepseek-v4.1") ||
                m == "deepseek-flash" ||
                m.contains("v4.1-flash") -> ModelSpec(ONE_MILLION, OUTPUT_384K)

            // DeepSeek V3.x / 官方 chat、reasoner：128K 上下文，64K 输出
            m.contains("deepseek-v3") ||
                m == "deepseek-chat" ||
                m == "deepseek-reasoner" -> ModelSpec(128_000, 65_536)

            else -> null
        }
    }
}
