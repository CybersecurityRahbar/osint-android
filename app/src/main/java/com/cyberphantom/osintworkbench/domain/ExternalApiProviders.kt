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
    val mediaResults: List<SearchMediaResult> = emptyList(),
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

private data class SearchProviderBatch(
    val results: List<PublicProfileResult> = emptyList(),
    val media: List<SearchMediaResult> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * Public search adapters. Each selected provider returns results from its supported
 * pages and media indexes. Key fallback improves resilience; it does not multiply quotas.
 */
class ExternalApiSearchClient(private val settings: ApiProviderSettingsStore) {
    suspend fun search(
        rawQuery: String,
        kind: IdentifierKind,
        maxCalls: Int,
        maxResults: Int,
        includeImages: Boolean,
        includeVideos: Boolean,
        pageLimit: Int,
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

        val resultLimit = maxResults.coerceIn(10, 500)
        val pages = pageLimit.coerceIn(1, 5)
        onProgress("تجميع نتائج " + selected.size + " محركات خارجية؛ حتى " + pages + " صفحات للمحركات الداعمة…")
        val calls = coroutineScope {
            selected.map { provider ->
                async {
                    val result = callWithKeyFallback(provider, settings) { key ->
                        queryProvider(provider, key, query, resultLimit, includeImages, includeVideos, pages)
                    }
                    Pair(provider, result)
                }
            }.awaitAll()
        }

        val results = mutableListOf<PublicProfileResult>()
        val media = mutableListOf<SearchMediaResult>()
        val warnings = mutableListOf<String>()
        withoutKeys.forEach { warnings += it.name + ": المصدر مفعّل لكن لا يوجد مفتاح محفوظ قابل للاستخدام." }
        var responded = 0
        calls.forEach { (provider, result) ->
            when (result) {
                is ProviderCall.Success -> {
                    responded++
                    results += result.value.results
                    media += result.value.media
                    warnings += result.value.warnings
                }
                is ProviderCall.Failure -> warnings += provider.name + ": " + result.message
            }
        }
        return ExternalSearchOutcome(
            results = results.distinctBy { canonicalUrl(it.url) }.take(resultLimit * selected.size),
            mediaResults = media.distinctBy {
                it.mediaId.ifBlank { canonicalUrl(it.pageUrl + "|" + it.thumbnailUrl.orEmpty()) }
            },
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
        includeImages: Boolean,
        includeVideos: Boolean,
        pageLimit: Int
    ): SearchProviderBatch = withContext(Dispatchers.IO) {
        val results = mutableListOf<PublicProfileResult>()
        val media = mutableListOf<SearchMediaResult>()
        val warnings = mutableListOf<String>()
        when (provider) {
            ApiProviderId.SERP_API -> {
                // SerpApi Search Index has its own multi-billion-page index and supports
                // up to 100 requested records per response plus deep-recall mode.
                // It is currently preview, so gracefully fall back to Google Light if unavailable.
                var indexedPages = 0
                for (page in 0 until pageLimit) {
                    try {
                        val root = requestJson(
                            queryUrl(
                                "https://serpapi.com/search.json",
                                linkedMapOf(
                                    "engine" to "search_index",
                                    "q" to query,
                                    "type" to "web",
                                    "mode" to "deep",
                                    "num" to "100",
                                    "start" to (page * 100).toString(),
                                    "api_key" to key
                                )
                            )
                        )
                        detectLogicalError(provider, root)
                        val pageResults = parseSearchResults(provider, root, includeImages)
                        results += pageResults
                        indexedPages++
                        val next = root.optJSONObject("serpapi_pagination")?.optString("next").orEmpty()
                        if (pageResults.isEmpty() || next.isBlank() || results.size >= limit) break
                    } catch (error: Exception) {
                        if (indexedPages == 0) {
                            warnings += "SerpApi Search Index غير متاح لهذا الحساب أو تعذر الوصول إليه؛ يجري الرجوع إلى Google Light: " + safeErrorMessage(error)
                        } else {
                            warnings += "SerpApi Search Index: توقف ترقيم النتائج بعد " + indexedPages +
                                " صفحات: " + safeErrorMessage(error)
                        }
                        break
                    }
                }

                if (indexedPages == 0 || results.isEmpty()) {
                    var fallbackPages = 0
                    for (page in 0 until pageLimit) {
                        try {
                            val root = requestJson(
                                queryUrl(
                                    "https://serpapi.com/search.json",
                                    linkedMapOf(
                                        "engine" to "google_light",
                                        "q" to query,
                                        "start" to (page * 10).toString(),
                                        "api_key" to key
                                    )
                                )
                            )
                            detectLogicalError(provider, root)
                            val pageResults = parseSearchResults(provider, root, includeImages)
                            results += pageResults
                            fallbackPages++
                            val next = root.optJSONObject("serpapi_pagination")?.optString("next").orEmpty()
                            if (pageResults.isEmpty() || next.isBlank() || results.size >= limit) break
                        } catch (error: Exception) {
                            if (fallbackPages == 0) throw error
                            warnings += "Google Light: توقف الترقيم بعد " + fallbackPages +
                                " صفحات: " + safeErrorMessage(error)
                            break
                        }
                    }
                }

                if (includeImages) {
                    runCatching {
                        val root = requestJson(
                            queryUrl(
                                "https://serpapi.com/search.json",
                                linkedMapOf("engine" to "google_images", "q" to query, "ijn" to "0", "api_key" to key)
                            )
                        )
                        detectLogicalError(provider, root)
                        media += parseSerpImages(root)
                    }.onFailure { warnings += "SerpApi Images: " + safeErrorMessage(it as? Exception ?: Exception()) }
                }

                if (includeVideos) {
                    val beforeVideos = media.size
                    runCatching {
                        val root = requestJson(
                            queryUrl(
                                "https://serpapi.com/search.json",
                                linkedMapOf(
                                    "engine" to "search_index",
                                    "q" to query,
                                    "type" to "video",
                                    "mode" to "deep",
                                    "num" to "100",
                                    "start" to "0",
                                    "api_key" to key
                                )
                            )
                        )
                        detectLogicalError(provider, root)
                        media += parseSerpVideos(root, "SerpApi Search Index Videos")
                    }.onFailure { warnings += "Search Index Videos: " + safeErrorMessage(it as? Exception ?: Exception()) }

                    // Search Index video corpus may be unavailable while the preview is evolving.
                    // Use dedicated YouTube and Google Video indexes only as a fallback.
                    if (media.size == beforeVideos) {
                        runCatching {
                            val root = requestJson(
                                queryUrl(
                                    "https://serpapi.com/search.json",
                                    linkedMapOf("engine" to "youtube", "search_query" to rawMediaQuery(query), "api_key" to key)
                                )
                            )
                            detectLogicalError(provider, root)
                            media += parseSerpVideos(root, "YouTube")
                        }.onFailure { warnings += "YouTube index: " + safeErrorMessage(it as? Exception ?: Exception()) }
                        runCatching {
                            val root = requestJson(
                                queryUrl(
                                    "https://serpapi.com/search.json",
                                    linkedMapOf("engine" to "google_videos", "q" to query, "api_key" to key)
                                )
                            )
                            detectLogicalError(provider, root)
                            media += parseSerpVideos(root, "Google Videos")
                        }.onFailure { warnings += "Google Videos: " + safeErrorMessage(it as? Exception ?: Exception()) }
                    }
                }
            }
            ApiProviderId.TAVILY -> {
                val root = requestJson(
                    "https://api.tavily.com/search",
                    method = "POST",
                    headers = mapOf("Authorization" to "Bearer " + key),
                    body = JSONObject()
                        .put("query", query)
                        .put("topic", "general")
                        .put("search_depth", "basic")
                        .put("max_results", minOf(20, limit))
                        .put("include_answer", false)
                        .put("include_raw_content", true)
                        .put("include_images", includeImages)
                )
                detectLogicalError(provider, root)
                results += parseSearchResults(provider, root, includeImages)
                if (includeImages) media += parseTavilyImages(root)
            }
            ApiProviderId.TINYFISH -> {
                var completedPages = 0
                for (page in 0 until pageLimit) {
                    try {
                        val root = requestJson(
                            queryUrl(
                                "https://api.search.tinyfish.ai",
                                linkedMapOf(
                                    "query" to query,
                                    "domain_type" to "web",
                                    "language" to if (containsArabic(query)) "ar" else "en",
                                    "page" to page.toString()
                                )
                            ),
                            headers = mapOf("X-API-Key" to key)
                        )
                        detectLogicalError(provider, root)
                        results += parseSearchResults(provider, root, includeImages)
                        completedPages++
                        if ((root.optJSONArray("results")?.length() ?: 0) == 0) break
                    } catch (error: Exception) {
                        if (completedPages == 0) throw error
                        warnings += "TinyFish: توقف الترقيم بعد " + completedPages +
                            " صفحة: " + safeErrorMessage(error)
                        break
                    }
                }
            }
            ApiProviderId.FIRECRAWL -> {
                val sourceTypes = JSONArray()
                    .put(JSONObject().put("type", "web"))
                    .put(JSONObject().put("type", "news"))
                if (includeImages) sourceTypes.put(JSONObject().put("type", "images"))
                val root = requestJson(
                    "https://api.firecrawl.dev/v2/search",
                    method = "POST",
                    headers = mapOf("Authorization" to "Bearer " + key),
                    body = JSONObject()
                        .put("query", query)
                        .put("limit", minOf(20, limit))
                        .put("sources", sourceTypes)
                )
                detectLogicalError(provider, root)
                results += parseSearchResults(provider, root, includeImages)
                if (includeImages) media += parseFirecrawlImages(root)
            }
            ApiProviderId.EXA -> {
                val root = requestJson(
                    "https://api.exa.ai/search",
                    method = "POST",
                    headers = mapOf("x-api-key" to key),
                    body = JSONObject()
                        .put("query", query)
                        .put("type", "auto")
                        .put("numResults", minOf(100, limit))
                        .put("contents", JSONObject()
                            .put("text", JSONObject().put("maxCharacters", 12_000))
                            .put("highlights", JSONObject().put("numSentences", 4)))
                )
                detectLogicalError(provider, root)
                results += parseSearchResults(provider, root, includeImages)
                if (includeImages) media += parseExaImages(root)
            }
            else -> error("Provider is not a general-search source.")
        }
        SearchProviderBatch(
            results = results.distinctBy { canonicalUrl(it.url) },
            media = media.distinctBy { it.mediaId.ifBlank { canonicalUrl(it.pageUrl + "|" + it.thumbnailUrl.orEmpty()) } },
            warnings = warnings
        )
    }

    private fun parseSearchResults(
        provider: ApiProviderId,
        root: JSONObject,
        includeImages: Boolean
    ): List<PublicProfileResult> {
        val data = root.optJSONObject("data")
        val items = when (provider) {
            ApiProviderId.SERP_API -> root.optJSONArray("organic_results") ?: JSONArray()
            ApiProviderId.TAVILY -> root.optJSONArray("results") ?: JSONArray()
            ApiProviderId.TINYFISH -> root.optJSONArray("results") ?: JSONArray()
            ApiProviderId.FIRECRAWL -> JSONArray().apply {
                val web = data?.optJSONArray("web") ?: JSONArray()
                val news = data?.optJSONArray("news") ?: JSONArray()
                for (i in 0 until web.length()) {
                    val item = web.optJSONObject(i)
                    if (item != null) put(item)
                }
                for (i in 0 until news.length()) {
                    val item = news.optJSONObject(i)
                    if (item != null) put(item)
                }
            }
            ApiProviderId.EXA -> root.optJSONArray("results") ?: JSONArray()
            else -> JSONArray()
        }
        val label = providerLabel(provider)
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val url = when (provider) {
                    ApiProviderId.SERP_API -> item.optString("link")
                    else -> item.optString("url")
                }.takeIf(::isHttpsUrl) ?: continue
                val title = item.optString("title").trim().takeIf(String::isNotBlank) ?: continue
                val providerText = when (provider) {
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
                }.trim()
                val rawText = when (provider) {
                    ApiProviderId.TAVILY -> item.optString("raw_content")
                    ApiProviderId.EXA -> item.optString("text")
                    ApiProviderId.FIRECRAWL -> item.optString("markdown").ifBlank { item.optString("content") }
                    else -> ""
                }.trim()
                val image = when (provider) {
                    ApiProviderId.SERP_API -> item.optString("image_url").ifBlank { item.optString("thumbnail") }
                    ApiProviderId.EXA -> item.optString("image")
                    else -> ""
                }.takeIf { includeImages && isHttpsUrl(it) }
                add(
                    PublicProfileResult(
                        provider = label,
                        title = title.take(240),
                        description = providerText.take(900),
                        url = url,
                        confidence = when (provider) {
                            ApiProviderId.SERP_API -> 76
                            ApiProviderId.TAVILY -> 74
                            ApiProviderId.TINYFISH -> 75
                            ApiProviderId.FIRECRAWL -> 72
                            ApiProviderId.EXA -> 76
                            else -> 65
                        },
                        imageUrl = image,
                        fullText = rawText.take(60_000)
                    )
                )
            }
        }
    }

    private fun parseSerpImages(root: JSONObject): List<SearchMediaResult> {
        val items = root.optJSONArray("images_results") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val thumbnail = item.optString("thumbnail").ifBlank { item.optString("original") }
                val page = item.optString("link").ifBlank { item.optString("original") }
                if (!isHttpsUrl(thumbnail) || !isHttpsUrl(page)) continue
                add(SearchMediaResult(
                    provider = "SerpApi Images",
                    mediaType = "IMAGE",
                    title = item.optString("title").ifBlank { "صورة مفهرسة" }.take(200),
                    pageUrl = page,
                    thumbnailUrl = thumbnail,
                    description = item.optString("source").take(240)
                ))
            }
        }
    }

    private fun parseSerpVideos(root: JSONObject, providerName: String): List<SearchMediaResult> {
        val items = root.optJSONArray("video_results") ?: root.optJSONArray("videos_results") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val link = item.optString("link").ifBlank { item.optString("url") }
                val thumbnail = thumbnailValue(item.opt("thumbnail"))
                if (!isHttpsUrl(link)) continue
                add(SearchMediaResult(
                    provider = providerName,
                    mediaType = "VIDEO",
                    title = item.optString("title").ifBlank { "فيديو" }.take(240),
                    pageUrl = link,
                    thumbnailUrl = thumbnail?.takeIf(::isHttpsUrl),
                    description = item.optString("description").ifBlank { item.optString("snippet") }.take(500),
                    mediaId = item.optString("video_id")
                ))
            }
        }
    }

    private fun parseTavilyImages(root: JSONObject): List<SearchMediaResult> {
        val items = root.optJSONArray("images") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val url = item.optString("url")
                if (!isHttpsUrl(url)) continue
                add(SearchMediaResult(
                    provider = "Tavily Images",
                    mediaType = "IMAGE",
                    title = item.optString("description").ifBlank { "صورة مفهرسة" }.take(200),
                    pageUrl = url,
                    thumbnailUrl = url,
                    description = item.optString("description").take(400)
                ))
            }
        }
    }

    private fun parseFirecrawlImages(root: JSONObject): List<SearchMediaResult> {
        val items = root.optJSONObject("data")?.optJSONArray("images") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val url = item.optString("url").takeIf(::isHttpsUrl) ?: continue
                val sourceUrl = item.optString("sourceURL").ifBlank { item.optString("link") }.ifBlank { url }
                add(SearchMediaResult(
                    provider = "Firecrawl Images",
                    mediaType = "IMAGE",
                    title = item.optString("title").ifBlank { "صورة مفهرسة" }.take(200),
                    pageUrl = sourceUrl,
                    thumbnailUrl = url,
                    description = item.optString("description").take(400)
                ))
            }
        }
    }

    private fun parseExaImages(root: JSONObject): List<SearchMediaResult> {
        val items = root.optJSONArray("results") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val page = item.optString("url").takeIf(::isHttpsUrl) ?: continue
                val direct = item.optString("image").takeIf(::isHttpsUrl)
                if (direct != null) add(SearchMediaResult(
                    provider = "Exa",
                    mediaType = "IMAGE",
                    title = item.optString("title").ifBlank { "صورة مرتبطة بالصفحة" }.take(200),
                    pageUrl = page,
                    thumbnailUrl = direct
                ))
                val links = item.optJSONArray("imageLinks") ?: item.optJSONArray("richImageLinks") ?: JSONArray()
                for (i in 0 until links.length()) {
                    val imageUrl = links.optString(i).takeIf(::isHttpsUrl) ?: continue
                    add(SearchMediaResult(
                        provider = "Exa",
                        mediaType = "IMAGE",
                        title = item.optString("title").ifBlank { "صورة مرتبطة بالصفحة" }.take(200),
                        pageUrl = page,
                        thumbnailUrl = imageUrl
                    ))
                }
            }
        }
    }

    private fun thumbnailValue(value: Any?): String? = when (value) {
        is String -> value
        is JSONObject -> value.optString("static").ifBlank { value.optString("url") }.ifBlank { value.optString("link") }
        is JSONArray -> {
            val first = value.optJSONObject(0)
            first?.let { it.optString("url").ifBlank { it.optString("static") } }
        }
        else -> null
    }?.takeIf(String::isNotBlank)

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

private fun rawMediaQuery(query: String): String = query.trim().take(180)

/**
 * REST adapters for the four user-configured news APIs. Each enabled, configured provider
 * receives one query per collection run; key slots are retried only for key/quota/rate failures.
 */
class ExternalApiNewsClient(private val settings: ApiProviderSettingsStore) {
    suspend fun search(
        rawQuery: String,
        maxCalls: Int,
        maxResults: Int,
        includeImages: Boolean,
        onProgress: (String) -> Unit = {}
    ): ExternalNewsOutcome {
        val query = rawQuery.trim()
        val enabled = NEWS_ORDER.filter(settings::isEnabled)
        val withoutKeys = enabled.filter { settings.availableKeys(it).isEmpty() }
        val selected = enabled
            .filter { settings.availableKeys(it).isNotEmpty() }
            .take(maxCalls.coerceIn(1, NEWS_ORDER.size))
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
        parseNewsResults(provider, root, includeImages)
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
                        description = description,
                        fullText = when (provider) {
                            ApiProviderId.CURRENTS -> item.optString("content")
                            ApiProviderId.THE_NEWS_API -> item.optString("content").ifBlank { item.optString("full_content") }
                            ApiProviderId.NEWSDATA -> item.optString("content")
                            ApiProviderId.GNEWS -> item.optString("content")
                            else -> ""
                        }.take(60_000)
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
        while (total < 3_000_000) {
            val count = input.read(buffer, 0, minOf(buffer.size, 3_000_000 - total))
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
