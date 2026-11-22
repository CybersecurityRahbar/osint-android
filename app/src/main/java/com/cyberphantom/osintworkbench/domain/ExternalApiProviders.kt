package com.cyberphantom.osintworkbench.domain

import android.content.Context
import com.cyberphantom.osintworkbench.data.ApiKeyCandidate
import com.cyberphantom.osintworkbench.data.ApiProviderGroup
import com.cyberphantom.osintworkbench.data.ApiProviderId
import com.cyberphantom.osintworkbench.data.ApiProviderSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class ExternalSearchOutcome(
    val results: List<PublicProfileResult> = emptyList(),
    val warnings: List<String> = emptyList(),
    val providersAttempted: Int = 0,
    val providersResponded: Int = 0
)

data class ExternalNewsSourceOutcome(
    val provider: String,
    val articles: List<NewsArticle>,
    val error: String? = null
)

data class ExternalNewsOutcome(
    val sources: List<ExternalNewsSourceOutcome> = emptyList(),
    val warnings: List<String> = emptyList(),
    val providersAttempted: Int = 0,
    val providersResponded: Int = 0
)

/**
 * REST adapters for the five user-configured general-search APIs.
 * A provider is called only when it is enabled and has at least one decryptable key.
 */
class ExternalApiSearchClient(private val settings: ApiProviderSettingsStore) {
    suspend fun search(
        rawQuery: String,
        kind: IdentifierKind,
        maxCalls: Int,
        maxResults: Int,
        includeImages: Boolean,
        onProgress: (String) -> Unit = {}
    ): ExternalSearchOutcome {
        val query = buildQuery(rawQuery, kind)
        val enabled = SEARCH_ORDER.filter(settings::isEnabled)
        val withoutKeys = enabled.filter { settings.availableKeys(it).isEmpty() }
        val selected = enabled
            .filter { settings.availableKeys(it).isNotEmpty() }
            .take(maxCalls.coerceIn(1, SEARCH_ORDER.size))

        if (selected.isEmpty()) {
            val warnings = if (enabled.isEmpty()) emptyList() else
                listOf("المصادر الخارجية المفعّلة لا تحتوي على مفاتيح قابلة للاستخدام؛ راجع إعدادات المفاتيح.")
            return ExternalSearchOutcome(warnings = warnings)
        }

        onProgress("استعلام محركات البحث الخارجية: " + selected.joinToString("، ") { it.name })
        val calls = coroutineScope {
            selected.map { provider ->
                async {
                    val result = callWithKeyFallback(provider, settings) { key ->
                        queryProvider(provider, key, query, maxResults.coerceIn(1, 20), includeImages)
                    }
                    Pair(provider, result)
                }
            }.awaitAll()
        }

        val results = mutableListOf<PublicProfileResult>()
        val warnings = mutableListOf<String>()
        withoutKeys.forEach { warnings += it.name + ": المصدر مفعّل لكن لم يُحفظ له مفتاح قابل للقراءة." }
        var responded = 0
        calls.forEach { (provider, result) ->
            when (result) {
                is ProviderCall.Success -> {
                    responded++
                    results += result.value
                }
                is ProviderCall.Failure -> warnings += provider.name + ": " + result.message
            }
        }
        return ExternalSearchOutcome(
            results = results.distinctBy { canonicalUrl(it.url) }.take(maxResults.coerceAtLeast(1)),
            warnings = warnings.distinct(),
            providersAttempted = selected.size,
            providersResponded = responded
        )
    }

    private suspend fun queryProvider(
        provider: ApiProviderId,
        key: String,
        query: String,
        limit: Int,
        includeImages: Boolean
    ): List<PublicProfileResult> = withContext(Dispatchers.IO) {
        val root = when (provider) {
            ApiProviderId.SERP_API -> {
                val url = queryUrl(
                    "https://serpapi.com/search.json",
                    linkedMapOf(
                        "engine" to "google",
                        "q" to query,
                        "num" to limit.toString(),
                        "api_key" to key
                    )
                )
                requestJson(url)
            }
            ApiProviderId.TAVILY -> requestJson(
                "https://api.tavily.com/search",
                method = "POST",
                headers = mapOf("Authorization" to "Bearer " + key),
                body = JSONObject()
                    .put("query", query)
                    .put("topic", "general")
                    .put("search_depth", "basic")
                    .put("max_results", limit)
                    .put("include_answer", false)
                    .put("include_raw_content", false)
            )
            ApiProviderId.TINYFISH -> requestJson(
                queryUrl(
                    "https://api.search.tinyfish.ai",
                    linkedMapOf(
                        "query" to query,
                        "domain_type" to "web",
                        "language" to if (containsArabic(query)) "ar" else "en"
                    )
                ),
                headers = mapOf("X-API-Key" to key)
            )
            ApiProviderId.FIRECRAWL -> requestJson(
                "https://api.firecrawl.dev/v2/search",
                method = "POST",
                headers = mapOf("Authorization" to "Bearer " + key),
                body = JSONObject()
                    .put("query", query)
                    .put("limit", limit)
                    .put("sources", JSONArray().put(JSONObject().put("type", "web")))
            )
            ApiProviderId.EXA -> requestJson(
                "https://api.exa.ai/search",
                method = "POST",
                headers = mapOf("x-api-key" to key),
                body = JSONObject()
                    .put("query", query)
                    .put("type", "auto")
                    .put("numResults", limit)
                    .put("contents", JSONObject().put("highlights", JSONObject().put("numSentences", 2)))
            )
            else -> error("Provider is not a general-search source.")
        }
        detectLogicalError(provider, root)
        return parseSearchResults(provider, root, includeImages)
    }

    private fun parseSearchResults(
        provider: ApiProviderId,
        root: JSONObject,
        includeImages: Boolean
    ): List<PublicProfileResult> {
        val items = when (provider) {
            ApiProviderId.SERP_API -> root.optJSONArray("organic_results") ?: JSONArray()
            ApiProviderId.TAVILY -> root.optJSONArray("results") ?: JSONArray()
            ApiProviderId.TINYFISH -> root.optJSONArray("results") ?: JSONArray()
            ApiProviderId.FIRECRAWL -> root.optJSONObject("data")?.optJSONArray("web") ?: JSONArray()
            ApiProviderId.EXA -> root.optJSONArray("results") ?: JSONArray()
            else -> JSONArray()
        }
        val label = providerLabel(provider)
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val url = when (provider) {
                    ApiProviderId.SERP_API -> item.optString("link")
                    ApiProviderId.TINYFISH -> item.optString("url")
                    ApiProviderId.FIRECRAWL -> item.optString("url")
                    else -> item.optString("url")
                }.takeIf(::isHttpsUrl) ?: continue
                val title = item.optString("title").trim().takeIf(String::isNotBlank) ?: continue
                val description = when (provider) {
                    ApiProviderId.SERP_API -> item.optString("snippet")
                    ApiProviderId.TAVILY -> item.optString("content")
                    ApiProviderId.TINYFISH -> item.optString("snippet")
                    ApiProviderId.FIRECRAWL -> item.optString("description")
                        .ifBlank { item.optJSONObject("metadata")?.optString("description").orEmpty() }
                    ApiProviderId.EXA -> {
                        val highlights = item.optJSONArray("highlights")
                        if (highlights == null) item.optString("text")
                        else (0 until highlights.length()).mapNotNull { highlights.optString(it).takeIf(String::isNotBlank) }.joinToString(" ")
                    }
                    else -> ""
                }.trim().take(700)
                val image = when (provider) {
                    ApiProviderId.SERP_API -> item.optString("thumbnail")
                    ApiProviderId.EXA -> item.optString("image")
                    else -> ""
                }.takeIf { includeImages && isHttpsUrl(it) }
                add(
                    PublicProfileResult(
                        provider = label,
                        title = title.take(240),
                        description = description,
                        url = url,
                        confidence = when (provider) {
                            ApiProviderId.SERP_API -> 76
                            ApiProviderId.TAVILY -> 74
                            ApiProviderId.TINYFISH -> 75
                            ApiProviderId.FIRECRAWL -> 72
                            ApiProviderId.EXA -> 76
                            else -> 65
                        },
                        imageUrl = image
                    )
                )
            }
        }
    }

    companion object {
        val SEARCH_ORDER = listOf(
            ApiProviderId.SERP_API,
            ApiProviderId.TAVILY,
            ApiProviderId.TINYFISH,
            ApiProviderId.FIRECRAWL,
            ApiProviderId.EXA
        )
    }
}

/**
 * REST adapters for the four user-configured news APIs. Each enabled, configured provider
 * receives one query per collection run; key slots are retried only for key/quota/rate failures.
 */
class ExternalApiNewsClient(private val settings: ApiProviderSettingsStore) {
    suspend fun search(
        rawQuery: String,
        maxResults: Int,
        includeImages: Boolean,
        onProgress: (String) -> Unit = {}
    ): ExternalNewsOutcome {
        val query = rawQuery.trim()
        val enabled = NEWS_ORDER.filter(settings::isEnabled)
        val withoutKeys = enabled.filter { settings.availableKeys(it).isEmpty() }
        val selected = enabled.filter { settings.availableKeys(it).isNotEmpty() }
        if (selected.isEmpty()) {
            val warnings = if (enabled.isEmpty()) emptyList() else
                listOf("مصادر الأخبار المفعّلة لا تحتوي على مفاتيح قابلة للقراءة؛ راجع إعدادات المفاتيح.")
            return ExternalNewsOutcome(warnings = warnings)
        }

        onProgress("إضافة مصادر الأخبار ذات المفاتيح المحفوظة…")
        val calls = coroutineScope {
            selected.map { provider ->
                async {
                    val result = callWithKeyFallback(provider, settings) { key ->
                        queryProvider(provider, key, query, maxResults.coerceIn(1, 20), includeImages)
                    }
                    Pair(provider, result)
                }
            }.awaitAll()
        }

        val sources = mutableListOf<ExternalNewsSourceOutcome>()
        val warnings = mutableListOf<String>()
        withoutKeys.forEach { warnings += it.name + ": المصدر مفعّل لكن لم يُحفظ له مفتاح قابل للقراءة." }
        var responded = 0
        calls.forEach { (provider, result) ->
            when (result) {
                is ProviderCall.Success -> {
                    responded++
                    sources += ExternalNewsSourceOutcome(
                        provider = providerLabel(provider),
                        articles = result.value
                    )
                }
                is ProviderCall.Failure -> {
                    warnings += provider.name + ": " + result.message
                    sources += ExternalNewsSourceOutcome(
                        provider = providerLabel(provider),
                        articles = emptyList(),
                        error = result.message
                    )
                }
            }
        }
        return ExternalNewsOutcome(
            sources = sources,
            warnings = warnings.distinct(),
            providersAttempted = selected.size,
            providersResponded = responded
        )
    }

    private suspend fun queryProvider(
        provider: ApiProviderId,
        key: String,
        query: String,
        limit: Int,
        includeImages: Boolean
    ): List<NewsArticle> = withContext(Dispatchers.IO) {
        val language = if (containsArabic(query)) "ar" else "en"
        val root = when (provider) {
            ApiProviderId.CURRENTS -> requestJson(
                queryUrl(
                    "https://api.currentsapi.services/v1/search",
                    linkedMapOf(
                        "keywords" to query,
                        "language" to language,
                        "page_size" to minOf(limit, 20).toString()
                    )
                ),
                headers = mapOf("Authorization" to "Bearer " + key)
            )
            ApiProviderId.THE_NEWS_API -> requestJson(
                queryUrl(
                    "https://api.thenewsapi.com/v1/news/all",
                    linkedMapOf(
                        "api_token" to key,
                        "search" to query,
                        "limit" to minOf(limit, 3).toString(),
                        "language" to language
                    )
                )
            )
            ApiProviderId.NEWSDATA -> requestJson(
                queryUrl(
                    "https://newsdata.io/api/1/latest",
                    linkedMapOf(
                        "q" to query,
                        "language" to language
                    )
                ),
                headers = mapOf("X-ACCESS-KEY" to key)
            )
            ApiProviderId.GNEWS -> requestJson(
                queryUrl(
                    "https://gnews.io/api/v4/search",
                    linkedMapOf(
                        "q" to query,
                        "lang" to language,
                        "max" to minOf(limit, 10).toString()
                    )
                ),
                headers = mapOf("X-Api-Key" to key)
            )
            else -> error("Provider is not a news source.")
        }
        detectLogicalError(provider, root)
        return parseNewsResults(provider, root, includeImages)
    }

    private fun parseNewsResults(
        provider: ApiProviderId,
        root: JSONObject,
        includeImages: Boolean
    ): List<NewsArticle> {
        val items = when (provider) {
            ApiProviderId.CURRENTS -> root.optJSONArray("news") ?: JSONArray()
            ApiProviderId.THE_NEWS_API -> root.optJSONArray("data") ?: JSONArray()
            ApiProviderId.NEWSDATA -> root.optJSONArray("results") ?: JSONArray()
            ApiProviderId.GNEWS -> root.optJSONArray("articles") ?: JSONArray()
            else -> JSONArray()
        }
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val url = when (provider) {
                    ApiProviderId.NEWSDATA -> item.optString("link")
                    else -> item.optString("url")
                }.takeIf(::isHttpsUrl) ?: continue
                val title = item.optString("title").trim().takeIf(String::isNotBlank) ?: continue
                val source = when (provider) {
                    ApiProviderId.CURRENTS -> item.optString("author").ifBlank { item.optString("source") }
                    ApiProviderId.THE_NEWS_API -> item.optString("source")
                    ApiProviderId.NEWSDATA -> item.optString("source_id")
                    ApiProviderId.GNEWS -> item.optJSONObject("source")?.optString("name").orEmpty()
                    else -> ""
                }
                val domain = extractDomain(url)
                val published = when (provider) {
                    ApiProviderId.CURRENTS -> item.optString("published")
                    ApiProviderId.THE_NEWS_API -> item.optString("published_at")
                    ApiProviderId.NEWSDATA -> item.optString("pubDate")
                    ApiProviderId.GNEWS -> item.optString("publishedAt")
                    else -> ""
                }
                val description = when (provider) {
                    ApiProviderId.CURRENTS -> item.optString("description")
                    ApiProviderId.THE_NEWS_API -> item.optString("description").ifBlank { item.optString("snippet") }
                    ApiProviderId.NEWSDATA -> item.optString("description")
                    ApiProviderId.GNEWS -> item.optString("description").ifBlank { item.optString("content") }
                    else -> ""
                }.trim().take(800)
                val image = when (provider) {
                    ApiProviderId.CURRENTS -> item.optString("image")
                    ApiProviderId.THE_NEWS_API -> item.optString("image_url")
                    ApiProviderId.NEWSDATA -> item.optString("image_url")
                    ApiProviderId.GNEWS -> item.optString("image")
                    else -> ""
                }.takeIf { includeImages && isHttpsUrl(it) }
                val language = item.optString("language").ifBlank {
                    if (containsArabic(title + " " + description)) "ar" else ""
                }
                add(
                    NewsArticle(
                        title = title.take(300),
                        url = url,
                        domain = domain,
                        publishedAt = published,
                        language = language,
                        publisherName = source.ifBlank { domain },
                        imageUrl = image,
                        provider = providerLabel(provider),
                        description = description
                    )
                )
            }
        }.distinctBy { canonicalUrl(it.url) }
    }

    companion object {
        val NEWS_ORDER = listOf(
            ApiProviderId.CURRENTS,
            ApiProviderId.THE_NEWS_API,
            ApiProviderId.NEWSDATA,
            ApiProviderId.GNEWS
        )
    }
}

private sealed class ProviderCall<out T> {
    data class Success<T>(val value: T) : ProviderCall<T>()
    data class Failure(val message: String) : ProviderCall<Nothing>()
}

private class ApiProviderHttpException(val status: Int) : Exception("HTTP " + status)

private suspend fun <T> callWithKeyFallback(
    provider: ApiProviderId,
    settings: ApiProviderSettingsStore,
    call: suspend (String) -> T
): ProviderCall<T> {
    val candidates: List<ApiKeyCandidate> = settings.availableKeys(provider)
    if (candidates.isEmpty()) return ProviderCall.Failure("لا يوجد مفتاح صالح محفوظ لهذا المصدر.")
    var lastStatus = 0
    for (candidate in candidates) {
        try {
            val result = call(candidate.value)
            settings.recordSuccessfulKey(provider, candidate.slot)
            return ProviderCall.Success(result)
        } catch (error: ApiProviderHttpException) {
            lastStatus = error.status
            if (error.status !in setOf(401, 402, 403, 429, 432)) {
                return ProviderCall.Failure("رفض المزود الطلب (HTTP " + error.status + "). راجع إعدادات المصدر والاستعلام.")
            }
        } catch (error: Exception) {
            return ProviderCall.Failure("تعذر الاتصال بالمزود: " + safeErrorMessage(error))
        }
    }
    settings.rotatePreferredKey(provider)
    return ProviderCall.Failure(
        when (lastStatus) {
            401 -> "كل المفاتيح المحفوظة رُفضت كمفاتيح غير صالحة (401)."
            402, 429 -> "وصلت المفاتيح المحفوظة إلى حد الرصيد أو معدل الطلبات؛ جُرّبت المفاتيح البديلة أيضاً."
            403 -> "رفض المزود المفاتيح أو صلاحية الحساب (403) بعد تجربة البدائل."
            else -> "فشل استخدام مفاتيح المصدر."
        }
    )
}

private fun requestJson(
    endpoint: String,
    method: String = "GET",
    headers: Map<String, String> = emptyMap(),
    body: JSONObject? = null
): JSONObject = run {
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 9_000
        readTimeout = 9_000
        instanceFollowRedirects = true
        useCaches = false
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "OSINT-Workbench/0.8 (user-configured public data providers)")
        headers.forEach { (name, value) -> setRequestProperty(name, value) }
        if (body != null) {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
    }
    try {
        if (body != null) {
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val raw = readBounded(stream)
        if (status !in 200..299) throw ApiProviderHttpException(status)
        if (raw.isBlank()) throw IllegalStateException("Empty JSON response.")
        JSONObject(raw)
    } finally {
        connection.disconnect()
    }
}

private fun readBounded(stream: java.io.InputStream?): String {
    if (stream == null) return ""
    return stream.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0
        while (total < 1_000_000) {
            val count = input.read(buffer, 0, minOf(buffer.size, 1_000_000 - total))
            if (count <= 0) break
            output.write(buffer, 0, count)
            total += count
        }
        output.toString(StandardCharsets.UTF_8.name())
    }
}

private fun detectLogicalError(provider: ApiProviderId, root: JSONObject) {
    val error = when (provider) {
        ApiProviderId.SERP_API -> root.optString("error")
        ApiProviderId.TAVILY -> root.optString("error")
        ApiProviderId.TINYFISH -> root.optString("error")
        ApiProviderId.FIRECRAWL -> root.optString("error").ifBlank {
            if (root.has("success") && !root.optBoolean("success", true)) "Request failed" else ""
        }
        ApiProviderId.EXA -> root.optString("error")
        ApiProviderId.CURRENTS -> {
            if (root.optString("status").equals("error", true)) {
                root.optString("msg").ifBlank { root.optString("message") }
            } else ""
        }
        ApiProviderId.THE_NEWS_API -> root.optString("error")
        ApiProviderId.NEWSDATA -> {
            if (root.optString("status").equals("error", true)) {
                root.optJSONArray("results")?.optString(0).orEmpty().ifBlank { root.optString("message") }
            } else ""
        }
        ApiProviderId.GNEWS -> root.optJSONArray("errors")?.let { errors ->
            (0 until errors.length()).joinToString("; ") { errors.optString(it) }
        }.orEmpty()
    }.trim()
    if (error.isBlank()) return
    val lower = error.lowercase(Locale.ROOT)
    val status = when {
        lower.contains("quota") || lower.contains("limit") || lower.contains("credit") || lower.contains("rate") || lower.contains("allowance") || lower.contains("exceed") || lower.contains("run out") -> 429
        lower.contains("api key") || lower.contains("token") || lower.contains("unauthorized") || lower.contains("authentication") -> 401
        else -> 502
    }
    throw ApiProviderHttpException(status)
}

private fun queryUrl(base: String, values: Map<String, String>): String {
    val query = values.entries.joinToString("&") { entry ->
        enc(entry.key) + "=" + enc(entry.value)
    }
    return base + "?" + query
}

private fun buildQuery(rawQuery: String, kind: IdentifierKind): String {
    val value = rawQuery.trim()
    return when (kind) {
        IdentifierKind.USERNAME -> "\"" + value.removePrefix("@") + "\""
        IdentifierKind.PHONE -> {
            val compact = value.filter { it.isDigit() || it == '+' }
            "\"" + compact + "\" OR \"" + compact.filter(Char::isDigit) + "\""
        }
        IdentifierKind.EMAIL -> "\"" + value + "\""
        IdentifierKind.NAME -> "\"" + value + "\""
    }
}

private fun providerLabel(provider: ApiProviderId): String = when (provider) {
    ApiProviderId.SERP_API -> "SerpApi"
    ApiProviderId.TAVILY -> "Tavily"
    ApiProviderId.TINYFISH -> "TinyFish"
    ApiProviderId.FIRECRAWL -> "Firecrawl"
    ApiProviderId.EXA -> "Exa"
    ApiProviderId.CURRENTS -> "Currents API"
    ApiProviderId.THE_NEWS_API -> "TheNewsAPI"
    ApiProviderId.NEWSDATA -> "NewsData.io"
    ApiProviderId.GNEWS -> "GNews"
}

private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

private fun containsArabic(value: String): Boolean =
    value.any { it in '\u0600'..'\u06ff' }

private fun isHttpsUrl(value: String): Boolean =
    value.startsWith("https://", ignoreCase = true) && runCatching { URL(value).host.isNotBlank() }.getOrDefault(false)

private fun extractDomain(value: String): String =
    runCatching { URL(value).host.lowercase(Locale.ROOT).removePrefix("www.") }.getOrDefault("")

private fun canonicalUrl(value: String): String = value
    .substringBefore("#")
    .removeSuffix("/")
    .lowercase(Locale.ROOT)

private fun safeErrorMessage(error: Exception): String {
    val raw = when (error) {
        is ApiProviderHttpException -> error.message.orEmpty()
        else -> error.message?.take(160)?.takeIf(String::isNotBlank) ?: "خطأ شبكة غير معروف"
    }
    return raw.replace(
        Regex("(?i)(api_key|api_token|apikey)=([^&\\s]+)"),
        "$1=[redacted]"
    )
}
