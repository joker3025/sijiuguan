package com.dsh.tavern.net

import com.dsh.tavern.data.ApiErrorParser
import com.dsh.tavern.data.AppSettings
import com.dsh.tavern.data.PromptBuilder.ApiMessage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiClient {
    /** 最近一次非流式请求的友好错误（供 UI 提示「模型不被支持」等）。 */
    @Volatile
    var lastError: String? = null
        private set

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun endpoint(settings: AppSettings): String {
        val base = settings.provider.baseUrl.trim().trimEnd('/')
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }

    /** 解析「每行 Name: value」的自定义请求头。 */
    fun parseExtraHeaders(provider: com.dsh.tavern.data.ProviderConfig): List<Pair<String, String>> =
        provider.extraHeaders.split('\n')
            .mapNotNull { line ->
                val index = line.indexOf(':')
                if (index <= 0) return@mapNotNull null
                val name = line.substring(0, index).trim()
                val value = line.substring(index + 1).trim()
                if (name.isEmpty()) null else name to value
            }

    /** 非流式一次性补全（建议回复/摘要等小请求）。返回 null 表示失败。 */
    fun complete(
        settings: AppSettings,
        messages: List<ApiMessage>,
        maxTokens: Int = 256
    ): String? {
        lastError = null
        return try {
            val request = buildChatRequest(settings, messages, stream = false, maxTokensOverride = maxTokens)
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val raw = response.body?.string().orEmpty()
                    lastError = ApiErrorParser.parse(
                        response.code, raw, settings.provider.model
                    ).friendly
                    return null
                }
                val body = response.body?.string().orEmpty()
                val message = JSONObject(body).optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                if (message == null || message.isNull("content")) {
                    null
                } else {
                    message.optString("content").takeIf { it.isNotBlank() }
                }
            }
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            null
        }
    }

    /** 视觉模型：把图片以 data URL 作为 user 消息的 image_url 发送（OpenAI 兼容格式）。 */
    fun completeWithImage(
        settings: AppSettings,
        systemPrompt: String,
        userPrompt: String,
        imageDataUrl: String,
        maxTokens: Int = 2_000
    ): String? = completeWithImages(settings, systemPrompt, userPrompt, listOf(imageDataUrl), maxTokens)

    /** 视觉模型：多张图片（长截图切片）放在同一条 user 消息里发送。 */
    fun completeWithImages(
        settings: AppSettings,
        systemPrompt: String,
        userPrompt: String,
        imageDataUrls: List<String>,
        maxTokens: Int = 2_000
    ): String? {
        lastError = null
        if (imageDataUrls.isEmpty()) {
            lastError = "没有可发送的图片"
            return null
        }
        return try {
            val content = JSONArray()
                .put(JSONObject().put("type", "text").put("text", userPrompt))
            imageDataUrls.forEach { url ->
                content.put(
                    JSONObject().put("type", "image_url").put(
                        "image_url",
                        JSONObject().put("url", url).put("detail", "high")
                    )
                )
            }
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", content))
            val body = JSONObject()
                .put("model", settings.provider.model)
                .put("messages", messages)
                .put("stream", false)
                .put("max_tokens", maxTokens)
            if (!settings.provider.model.contains("reasoner", ignoreCase = true)) {
                body.put("temperature", settings.temperature.toDouble())
            }
            val builder = Request.Builder()
                .url(endpoint(settings))
                .post(body.toString().toRequestBody("application/json".toMediaType()))
            if (settings.provider.apiKey.isNotBlank()) {
                builder.addHeader("Authorization", "Bearer ${settings.provider.apiKey.trim()}")
            }
            parseExtraHeaders(settings.provider).forEach { (name, value) -> builder.addHeader(name, value) }
            builder.addHeader("Accept", "application/json")
            http.newCall(builder.build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    lastError = ApiErrorParser.parse(response.code, raw, settings.provider.model).friendly
                    return null
                }
                val message = JSONObject(raw).optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                if (message == null || message.isNull("content")) return null
                message.optString("content").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            null
        }
    }

    fun buildChatRequest(
        settings: AppSettings,
        messages: List<ApiMessage>,
        stream: Boolean,
        maxTokensOverride: Int? = null
    ): Request {
        val provider = settings.provider
        val arr = JSONArray()
        messages.forEach { m ->
            arr.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        val body = JSONObject()
            .put("model", provider.model)
            .put("messages", arr)
            .put("stream", stream)
            .put("max_tokens", maxTokensOverride ?: settings.maxTokens)

        // 流式时请求真实 usage（DeepSeek / OpenAI 兼容；个别中转站不支持可在设置里关闭）
        if (stream && settings.includeUsage) {
            body.put("stream_options", JSONObject().put("include_usage", true))
        }

        // DeepSeek reasoner 不接受 temperature / top_p / 惩罚 / stop
        if (!provider.model.contains("reasoner", ignoreCase = true)) {
            body.put("temperature", settings.temperature.toDouble())
            body.put("top_p", settings.topP.toDouble())
            if (settings.frequencyPenalty != 0f) {
                body.put("frequency_penalty", settings.frequencyPenalty.toDouble())
            }
            if (settings.presencePenalty != 0f) {
                body.put("presence_penalty", settings.presencePenalty.toDouble())
            }
            val stop = settings.stopSequences
                .split('\n', ',', '，')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (stop.isNotEmpty()) body.put("stop", JSONArray(stop))
        }

        val builder = Request.Builder()
            .url(endpoint(settings))
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        if (provider.apiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer ${provider.apiKey.trim()}")
        }
        parseExtraHeaders(provider).forEach { (name, value) -> builder.addHeader(name, value) }
        builder.addHeader("Accept", if (stream) "text/event-stream" else "application/json")
        return builder.build()
    }
}
