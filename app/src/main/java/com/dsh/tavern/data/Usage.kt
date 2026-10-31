package com.dsh.tavern.data

/** 用量统计（token）。 */
data class UsageStats(
    val todayPrompt: Long = 0,
    val todayCompletion: Long = 0,
    val monthPrompt: Long = 0,
    val monthCompletion: Long = 0
) {
    val todayTokens: Long get() = todayPrompt + todayCompletion
    val monthTokens: Long get() = monthPrompt + monthCompletion
}

/** 按设置里的「每百万 token 单价」估算花费（元）。 */
object CostCalculator {
    fun cost(settings: AppSettings, promptTokens: Long, completionTokens: Long): Double =
        promptTokens / 1_000_000.0 * settings.priceInputPerMillion +
            completionTokens / 1_000_000.0 * settings.priceOutputPerMillion

    fun cost(settings: AppSettings, promptTokens: Int, completionTokens: Int): Double =
        cost(settings, promptTokens.toLong(), completionTokens.toLong())
}
