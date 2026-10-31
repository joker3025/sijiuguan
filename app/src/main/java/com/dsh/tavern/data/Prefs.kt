package com.dsh.tavern.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("tavern_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val raw = sp.getString("settings", null) ?: return AppSettings()
        return try {
            val o = JSONObject(raw)
            val providers = mutableListOf<ProviderConfig>()
            val arr = o.optJSONArray("providers")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    providers.add(
                        ProviderConfig(
                            name = p.optString("name", "供应商"),
                            baseUrl = p.optString("baseUrl", "https://api.deepseek.com/v1"),
                            apiKey = Crypto.decrypt(p.optString("apiKey", "")),
                            model = p.optString("model", "deepseek-chat"),
                            extraHeaders = p.optString("extraHeaders", "")
                        )
                    )
                }
            }
            if (providers.isEmpty()) providers.add(ProviderConfig())
            AppSettings(
                providers = providers,
                activeProvider = o.optInt("activeProvider", 0).coerceIn(0, providers.size - 1),
                temperature = o.optDouble("temperature", 1.0).toFloat(),
                topP = o.optDouble("topP", 1.0).toFloat(),
                maxTokens = o.optInt("maxTokens", 4096).coerceIn(64, 65536),
                contextLimit = o.optInt("contextLimit", 65536).coerceIn(1024, 1_000_000),
                personaName = o.optString("personaName", "你"),
                personaDescription = o.optString("personaDescription", ""),
                systemPreset = o.optString("systemPreset", AppSettings.DEFAULT_PRESET),
                nsfwEnabled = o.optBoolean("nsfw", true),
                reasoningDisplay = o.optBoolean("reasoning", true),
                includeUsage = o.optBoolean("includeUsage", true),
                priceInputPerMillion = o.optDouble("priceIn", 0.0),
                priceOutputPerMillion = o.optDouble("priceOut", 0.0),
                worldbookScanDepth = o.optInt("wbScanDepth", 6).coerceIn(1, 50),
                worldbookBudgetPercent = o.optInt("wbBudgetPercent", 25).coerceIn(5, 60),
                themeMode = o.optString("themeMode", "dark"),
                autoSummaryEnabled = o.optBoolean("autoSummary", true),
                summaryThresholdMessages = o.optInt("summaryThreshold", 16).coerceIn(4, 200),
                dailyBudgetYuan = o.optDouble("dailyBudget", 0.0).coerceAtLeast(0.0),
                autoBackupEnabled = o.optBoolean("autoBackup", true),
                stopSequences = o.optString("stopSequences", ""),
                frequencyPenalty = o.optDouble("frequencyPenalty", 0.0).toFloat(),
                presencePenalty = o.optDouble("presencePenalty", 0.0).toFloat(),
                appLockEnabled = o.optBoolean("appLock", false),
                appLockPinHash = o.optString("appLockPin", ""),
                hideInRecents = o.optBoolean("hideInRecents", false),
                dynamicSuggestions = o.optBoolean("dynamicSuggestions", true),
                minReplyLength = o.optInt("minReplyLength", 0),
                toolModel = o.optString("toolModel", ""),
                toolProviderIndex = o.optInt("toolProviderIndex", -1)
            )
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun save(s: AppSettings) {
        val o = JSONObject()
        val arr = JSONArray()
        s.providers.forEach { p ->
            arr.put(
                JSONObject()
                    .put("name", p.name)
                    .put("baseUrl", p.baseUrl)
                    .put("apiKey", Crypto.encrypt(p.apiKey))
                    .put("model", p.model)
                    .put("extraHeaders", p.extraHeaders)
            )
        }
        o.put("providers", arr)
        o.put("activeProvider", s.activeProvider)
        o.put("temperature", s.temperature.toDouble())
        o.put("topP", s.topP.toDouble())
        o.put("maxTokens", s.maxTokens)
        o.put("contextLimit", s.contextLimit)
        o.put("personaName", s.personaName)
        o.put("personaDescription", s.personaDescription)
        o.put("systemPreset", s.systemPreset)
        o.put("nsfw", s.nsfwEnabled)
        o.put("reasoning", s.reasoningDisplay)
        o.put("includeUsage", s.includeUsage)
        o.put("priceIn", s.priceInputPerMillion)
        o.put("priceOut", s.priceOutputPerMillion)
        o.put("wbScanDepth", s.worldbookScanDepth)
        o.put("wbBudgetPercent", s.worldbookBudgetPercent)
        o.put("themeMode", s.themeMode)
        o.put("autoSummary", s.autoSummaryEnabled)
        o.put("summaryThreshold", s.summaryThresholdMessages)
        o.put("dailyBudget", s.dailyBudgetYuan)
        o.put("autoBackup", s.autoBackupEnabled)
        o.put("stopSequences", s.stopSequences)
        o.put("frequencyPenalty", s.frequencyPenalty.toDouble())
        o.put("presencePenalty", s.presencePenalty.toDouble())
        o.put("appLock", s.appLockEnabled)
        o.put("appLockPin", s.appLockPinHash)
        o.put("hideInRecents", s.hideInRecents)
        o.put("dynamicSuggestions", s.dynamicSuggestions)
        o.put("minReplyLength", s.minReplyLength)
        o.put("toolModel", s.toolModel)
        o.put("toolProviderIndex", s.toolProviderIndex)
        sp.edit().putString("settings", o.toString()).apply()
    }

    /** 最近一次自动备份的日期（yyyyMMdd）。 */
    fun autoBackupLastDate(): String = sp.getString("autoBackupDate", "") ?: ""

    fun saveAutoBackupDate(date: String) {
        sp.edit().putString("autoBackupDate", date).apply()
    }
}
