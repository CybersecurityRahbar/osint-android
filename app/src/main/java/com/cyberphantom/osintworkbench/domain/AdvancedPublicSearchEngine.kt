package com.cyberphantom.osintworkbench.domain

import android.content.Context
import com.cyberphantom.osintworkbench.data.ApiProviderSettingsStore
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.Base64

data class PublicWebSearchResponse(
    val query: String,
    val results: List<PublicProfileResult>,
    val warnings: List<String>,
    val providersAttempted: Int,
    val elapsedMs: Long = 0,
    val mediaResults: List<SearchMediaResult> = emptyList()
) {
    val sourceCount: Int get() = results.map { it.provider }.distinct().size
}

/**
 * In-app, public-index search. It retrieves and parses result pages from DuckDuckGo, Bing,
 * Google, and Yahoo instead of returning only manually curated links. It does not authenticate to,
 * crawl, or bypass access controls on social networks. Public result availability depends on each provider.
 */
class AdvancedPublicSearchEngine(private val appContext: Context? = null) {
    fun buildSearchQueries(rawQuery: String, kind: IdentifierKind): List<String> {
        val query = rawQuery.trim()
        require(query.isNotBlank()) { "أدخل قيمة للبحث." }
        require(query.length <= 240) { "العبارة طويلة جداً." }

        val base = when (kind) {
            IdentifierKind.NAME -> buildList {
                add(query)
                add("\"$query\"")
                val parts = query.split(Regex("\\s+")).filter { it.length > 1 }
                if (parts.size >= 2) {
                    add("\"${parts.first()} ${parts.last()}\"")
                    parts.zipWithNext().forEach { (left, right) -> add("\"$left $right\"") }
                }
                add("\"$query\" site:reddit.com")
                add("\"$query\" (site:linkedin.com OR site:facebook.com OR site:instagram.com OR site:tiktok.com OR site:x.com OR site:threads.net)")
                add("\"$query\" (site:aljazeera.net OR site:bbc.com OR site:reuters.com OR site:apnews.com OR site:alarabiya.net)")
                add("\"$query\" (site:github.com OR site:gitlab.com OR site:stackoverflow.com OR site:medium.com)")
                add("\"$query\" (filetype:pdf OR filetype:doc OR filetype:docx)")
                add("\"$query\" (site:academia.edu OR site:researchgate.net OR site:scholar.google.com)")
                add("\"$query\" (site:gov OR site:edu OR site:org)")
            }
            IdentifierKind.USERNAME -> {
                val username = query.removePrefix("@")
                require(username.matches(Regex("[A-Za-z0-9._-]{1,100}"))) {
                    "اسم المستخدم يجب أن يكون معرفاً بسيطاً دون مسافات."
                }
                listOf(
                    "\"$username\"",
                    "$username site:github.com/$username",
                    "$username site:gitlab.com/$username",
                    "$username site:reddit.com/user/$username",
                    "\"@$username\"",
                    "\"$username\" site:reddit.com",
                    "\"$username\" (site:github.com OR site:gitlab.com OR site:gist.github.com)",
                    "\"$username\" (site:youtube.com OR site:x.com OR site:threads.net OR site:bsky.app)",
                    "\"$username\" (site:instagram.com OR site:tiktok.com OR site:facebook.com OR site:linkedin.com)",
                    "\"$username\" (site:medium.com OR site:dev.to OR site:stackoverflow.com OR site:quora.com)"
                )
            }
            IdentifierKind.PHONE -> {
                val digits = query.filter(Char::isDigit)
                require(digits.length in 7..15) { "أدخل رقماً يحتوي من 7 إلى 15 رقماً." }
                val normalized = normalizePhoneForSearch(query)
                buildList {
                    add("\"$query\"")
                    if (digits != query) add("\"$digits\"")
                    add("site:reddit.com \"$digits\"")
                    add("\"$digits\" (site:github.com OR site:gitlab.com OR site:stackoverflow.com)")
                    add("\"$digits\" (site:facebook.com OR site:instagram.com OR site:tiktok.com OR site:x.com OR site:youtube.com)")
                    add("\"$digits\" (site:pastebin.com OR site:medium.com OR site:quora.com)")
                    normalized.forEach { if (it != query && it != digits) add("\"$it\"") }
                }.distinct()
            }
            IdentifierKind.EMAIL -> {
                require(query.contains("@") && query.substringAfter("@").contains(".")) {
                    "أدخل عنوان بريد إلكتروني كاملاً."
                }
                listOf(
                    "\"$query\"",
                    "\"$query\" site:reddit.com",
                    "\"$query\" (site:github.com OR site:gitlab.com OR site:gist.github.com)",
                    "\"$query\" (site:pastebin.com OR site:stackoverflow.com OR site:medium.com)",
                    "\"$query\" (filetype:pdf OR filetype:csv OR filetype:json)"
                )
            }
        }

        val dorkCategories = when (kind) {
            IdentifierKind.NAME -> listOf(DorkCategory.PEOPLE, DorkCategory.SOCIAL, DorkCategory.DOCS, DorkCategory.FORUM)
            IdentifierKind.USERNAME -> listOf(DorkCategory.SOCIAL, DorkCategory.CODE, DorkCategory.FORUM)
            IdentifierKind.PHONE -> listOf(DorkCategory.FORUM, DorkCategory.SOCIAL)
            IdentifierKind.EMAIL -> listOf(DorkCategory.LEAKS, DorkCategory.DOCS, DorkCategory.CODE)
        }
        val dorks = DorkCatalogue.expand(query, dorkCategories, 12)
        return (base + dorks).distinct().take(26)
    }

    /**
     * Number metadata is derived offline from numbering-plan metadata only. It cannot establish
     * whether a number is currently assigned, reachable, or who owns it.
     */
    fun phoneMetadata(rawPhone: String): String? {
        val clean = rawPhone.trim()
        if (!clean.startsWith("+")) return null
        return runCatching {
            val util = PhoneNumberUtil.getInstance()
            val parsed: Phonenumber.PhoneNumber = util.parse(clean, "ZZ")
            val possible = util.isPossibleNumber(parsed)
            val validRange = util.isValidNumber(parsed)
            val region = util.getRegionCodeForNumber(parsed).orEmpty().ifBlank { "غير معروف" }
            val type = util.getNumberType(parsed).name
                .lowercase(Locale.ROOT)
                .replace('_', ' ')
            val e164 = util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            "تنسيق دولي: $e164 · رمز المنطقة: $region · النوع التقريبي: $type · " +
                when {
                    validRange -> "صيغة متوافقة مع خطة الترقيم الحالية؛ لا تثبت أن الرقم نشط أو تعرّف مالكه."
                    possible -> "الطول ممكن لكن النطاق غير مؤكد؛ لا يثبت أن الرقم نشط أو تعرّف مالكه."
                    else -> "الصيغة لا تطابق بيانات خطة الترقيم الحالية؛ راجع الرقم ورمز الدولة."
                }
        }.getOrNull()
    }

    suspend fun search(
        rawQuery: String,
        kind: IdentifierKind,
        maxResults: Int = 60,
        includeImages: Boolean = true,
        includeVideos: Boolean = true,
        pageLimit: Int = 2,
        onProgress: (String) -> Unit = {}
    ): PublicWebSearchResponse {
        val started = System.nanoTime()
        val query = rawQuery.trim()
        val allQueries = buildSearchQueries(query, kind)
        val dorkCategories = when (kind) {
            IdentifierKind.NAME -> listOf(DorkCategory.PEOPLE, DorkCategory.SOCIAL, DorkCategory.DOCS, DorkCategory.FORUM, DorkCategory.PASTE, DorkCategory.ARCHIVE)
            IdentifierKind.USERNAME -> listOf(DorkCategory.SOCIAL, DorkCategory.CODE, DorkCategory.FORUM, DorkCategory.PASTE)
            IdentifierKind.PHONE -> listOf(DorkCategory.FORUM, DorkCategory.SOCIAL, DorkCategory.PASTE)
            IdentifierKind.EMAIL -> listOf(DorkCategory.LEAKS, DorkCategory.DOCS, DorkCategory.CODE, DorkCategory.PASTE)
        }
        val targetedDorks = DorkCatalogue.expand(query, dorkCategories, 10)
        val baseQueries = allQueries.filterNot { it in DorkCatalogue.expand(query, dorkCategories, 12) }
        val baseBudget = when (kind) {
            IdentifierKind.NAME -> 5
            IdentifierKind.USERNAME -> 4
            IdentifierKind.PHONE -> 5
            IdentifierKind.EMAIL -> 4
        }
        // Interleave independent public-index queries with source-specific dorks so the
        // bounded adapter still reaches Reddit, forums, public profiles and document indexes.
        val redditQuery = when (kind) {
            IdentifierKind.USERNAME -> "\"" + query.removePrefix("@") + "\" site:reddit.com"
            IdentifierKind.PHONE -> "\"" + query.filter(Char::isDigit) + "\" site:reddit.com"
            IdentifierKind.EMAIL -> "\"" + query + "\" site:reddit.com"
            IdentifierKind.NAME -> "\"" + query + "\" site:reddit.com"
        }
        val queries = buildList {
            addAll(baseQueries.take(2))
            addAll(targetedDorks.take(2))
            addAll(baseQueries.drop(2).take(1))
            addAll(targetedDorks.drop(2).take(1))
            add(redditQuery)
        }.distinct().take(5)
        val limit = maxResults.coerceIn(10, 200)
        onProgress("توسيع العبارة إلى ${queries.size} مسارات بحث متخصصة…")

        val (broadOutcome, enrichment) = coroutineScope {
            val web = async {
                try {
                    BroadPublicSearchAdapter().search(queries, limit, includeImages)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    BroadPublicSearchAdapter.Outcome(
                        emptyList(),
                        listOf("فهارس الويب: ${error.message ?: "تعذر إكمال البحث"}"),
                        0
                    )
                }
            }
            val open = async {
                try {
                    onProgress("جمع نتائج قواعد المعرفة والمجتمعات المفتوحة…")
                    OpenSourceEnrichmentEngine().search(query, kind, minOf(limit, 60))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    OpenSourceEnrichmentEngine.Outcome(
                        emptyList(),
                        listOf("مصادر المعرفة المفتوحة: ${error.message ?: "تعذر الوصول"}"),
                        0
                    )
                }
            }
            web.await() to open.await()
        }

        val shouldTryInstantAnswer = broadOutcome.results.count { it.imageUrl.isNullOrBlank() } < minOf(6, limit)
        onProgress("تحليل التكرارات وترتيب النتائج حسب صلة العبارة…")
        val instantResults = if (shouldTryInstantAnswer) {
            runCatching { searchInstantAnswer(query, minOf(10, limit)) }
                .getOrDefault(emptyList())
        } else emptyList()

        val externalOutcome = appContext?.let { app ->
            val providerSettings = ApiProviderSettingsStore(app)
            ExternalApiSearchClient(providerSettings).search(
                rawQuery = query,
                kind = kind,
                maxCalls = providerSettings.externalProviderLimit(),
                maxResults = limit,
                includeImages = includeImages,
                includeVideos = includeVideos,
                pageLimit = pageLimit,
                onProgress = onProgress
            )
        } ?: ExternalSearchOutcome()

        val collectedResults = (broadOutcome.results + enrichment.results + instantResults + externalOutcome.results)
            .filter { it.url.startsWith("https://") }
            .map { result ->
                if (!result.imageUrl.isNullOrBlank()) {
                    result.copy(relevanceScore = maxOf(result.relevanceScore, calculateRelevance(query, kind, result, 0)))
                } else {
                    result.copy(relevanceScore = calculateRelevance(query, kind, result, 0))
                }
            }
            .distinctBy {
                if (!it.imageUrl.isNullOrBlank()) "image:" + canonicalUrl(it.imageUrl)
                else canonicalUrl(it.url)
            }

        val usernameNeedle = query.trim().removePrefix("@")
        val allResults = if (kind == IdentifierKind.USERNAME) {
            val exactPages = collectedResults.filter {
                it.imageUrl.isNullOrBlank() && containsExactUsername(usernameNeedle, it)
            }.map { it.copy(matchType = "EXACT_PROFILE") }
            val relatedPages = collectedResults.filter {
                it.imageUrl.isNullOrBlank() && !containsExactUsername(usernameNeedle, it)
            }.map {
                it.copy(
                    confidence = minOf(it.confidence, 48),
                    matchType = "RELATED_MENTION",
                    description = (it.description + " · ذكر مفهرس لاسم المستخدم داخل صفحة عامة؛ لا يثبت ملكية الحساب.").take(900)
                )
            }
            val indexedImages = collectedResults.filter { !it.imageUrl.isNullOrBlank() }.map {
                it.copy(
                    matchType = "RESULT",
                    description = (it.description + " · صورة مفهرسة لعبارة البحث؛ ليست إثباتاً لوجود الحساب.").take(900)
                )
            }
            exactPages + relatedPages + indexedImages
        } else collectedResults

        val images = allResults.filter { !it.imageUrl.isNullOrBlank() }
            .sortedWith(compareByDescending<PublicProfileResult> { it.relevanceScore }.thenByDescending { it.confidence })
        val pages = allResults.filter { it.imageUrl.isNullOrBlank() }
            .filter { kind != IdentifierKind.NAME || query.trim().split(Regex("\\s+")).size < 2 || it.relevanceScore >= 30 }
            .sortedWith(compareByDescending<PublicProfileResult> { it.relevanceScore }.thenByDescending { it.confidence })
        val imageSlots = minOf(images.size, if (includeImages) maxOf(4, limit / 3) else 0)
        val merged = (pages.take(limit - imageSlots) + images.take(imageSlots))
            .sortedWith(compareByDescending<PublicProfileResult> { it.relevanceScore }.thenByDescending { it.confidence })
            .take(limit)

        val warnings = buildList {
            addAll(broadOutcome.warnings)
            addAll(enrichment.warnings)
            addAll(externalOutcome.warnings)
            if (shouldTryInstantAnswer && instantResults.isEmpty()) {
                add("لم يُرجع مصدر الإجابة الفورية نتائج إضافية؛ لا يؤثر ذلك في بقية الفهارس.")
            }
            if (kind == IdentifierKind.USERNAME && collectedResults.any { it.imageUrl.isNullOrBlank() } &&
                collectedResults.none { it.imageUrl.isNullOrBlank() && containsExactUsername(usernameNeedle, it) }) {
                add("لم يظهر ملف عام يطابق اسم المستخدم بدقة. تعرض النتائج الأخرى بوسم «ذكر مرتبط» ولا تُعد إثباتاً لملكية الحساب.")
            }
            if (merged.isEmpty()) {
                add("لم تُستخرج نتائج من المصادر التي أمكن الوصول إليها. قد تكون الفهارس محدودة أو محجوبة أو لا تحتوي العبارة؛ النتيجة الصفرية لا تثبت غياب المعلومات.")
            }
            if (kind == IdentifierKind.NAME) {
                add("نتائج الأسماء قرائن بحث وليست إثباتاً لهوية شخص بعينه. طابق الاسم والسياق والمصدر قبل الربط.")
            }
        }.distinct()

        onProgress("اكتمل الجمع: ${merged.size} سجل من مصادر متعددة.")
        return PublicWebSearchResponse(
            query = query,
            results = merged,
            warnings = warnings,
            providersAttempted = broadOutcome.attempted + enrichment.attempted + externalOutcome.providersAttempted + if (shouldTryInstantAnswer) 1 else 0,
            elapsedMs = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0L),
            mediaResults = externalOutcome.mediaResults
        )
    }

    private fun endpointsForQuery(query: String, primary: Boolean): List<SearchEndpoint> {
        val encoded = enc(query)
        return if (primary) {
            listOf(
                SearchEndpoint("DuckDuckGo", "https://html.duckduckgo.com/html/?q=$encoded&kl=wt-wt"),
                SearchEndpoint("Bing Search", "https://www.bing.com/search?q=$encoded&count=10"),
                SearchEndpoint("Google Search", "https://www.google.com/search?q=$encoded&num=10&hl=en"),
                SearchEndpoint("Yahoo Search", "https://search.yahoo.com/search?p=$encoded")
            )
        } else {
            listOf(
                SearchEndpoint("DuckDuckGo Lite", "https://lite.duckduckgo.com/lite/?q=$encoded"),
                SearchEndpoint("Bing Search", "https://www.bing.com/search?q=$encoded&count=10")
            )
        }
    }

    private suspend fun searchProvider(
        endpoint: SearchEndpoint,
        limit: Int
    ): List<PublicProfileResult> = withContext(Dispatchers.IO) {
        val html = fetchHtml(endpoint.url, timeoutMs = 6_500)
        val parsed = parseResults(html, limit, endpoint.provider)
        if (parsed.isEmpty()) {
            throw IllegalStateException(
                "HTTP 200، لكن لم يتعرف المحلل على نتائج خارجية من صفحة المزود؛ قد تكون بنية HTML تغيرت أو ظهرت صفحة تحقق أو لا توجد نتائج مفهرسة لهذه العبارة."
            )
        }
        parsed
    }

    private fun fetchHtml(endpoint: String, timeoutMs: Int): String {
        val connection = (URL(endpoint).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
            setRequestProperty("Accept-Language", "en-US,en;q=0.8,ar;q=0.6")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36 OSINTWorkbench/0.4")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                var total = 0
                while (total < 1_500_000) {
                    val count = input.read(buffer, 0, minOf(buffer.size, 1_500_000 - total))
                    if (count <= 0) break
                    out.write(buffer, 0, count)
                    total += count
                }
                out.toString(StandardCharsets.UTF_8.name())
            }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("HTTP $status from ${URL(endpoint).host}")
            if (body.isBlank()) throw IllegalStateException("استجابة فارغة من ${URL(endpoint).host}")
            val accessChallenge = body.contains("anomaly-modal", ignoreCase = true) ||
                body.contains("bot challenge", ignoreCase = true) ||
                body.contains("unusual traffic", ignoreCase = true) ||
                body.contains("consent.google.com", ignoreCase = true) ||
                body.contains("verify you are human", ignoreCase = true) ||
                (body.contains("g-recaptcha", ignoreCase = true) && body.length < 250_000)
            if (accessChallenge) {
                throw IllegalStateException("أعاد مزود البحث صفحة تحقق/موافقة بدلاً من قائمة النتائج")
            }
            return body
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseResults(html: String, limit: Int, provider: String = "Public Web Search"): List<PublicProfileResult> {
        val candidates = mutableListOf<Triple<String, String, String>>()

        // DuckDuckGo HTML/Lite templates.
        val ddgAnchors = Regex(
            """(?is)<a\b(?=[^>]*\bclass\s*=\s*["'][^"']*(?:result__a|result-link)[^"']*["'])[^>]*>.*?</a>"""
        ).findAll(html).toList()
        val snippets = Regex(
            """(?is)<(?:a|div|td|p)\b(?=[^>]*\bclass\s*=\s*["'][^"']*(?:result__snippet|result-snippet|b_caption)[^"']*["'])[^>]*>(.*?)</(?:a|div|td|p)>"""
        ).findAll(html).map { cleanHtml(it.groupValues[1]) }.toList()
        for ((index, match) in ddgAnchors.withIndex()) {
            val anchor = match.value
            val attrs = anchor.substringBefore('>')
            val href = Regex("""(?is)\bhref\s*=\s*["']([^"']+)["']""")
                .find(attrs)?.groupValues?.getOrNull(1) ?: continue
            val title = cleanHtml(anchor.substringAfter('>').substringBeforeLast("</a>"))
            if (title.isNotBlank()) candidates += Triple(href, title, snippets.getOrNull(index).orEmpty())
        }

        // Bing's result containers are more stable than page-wide generic anchors.
        if (candidates.isEmpty()) {
            val blocks = Regex(
                """(?is)<li\b(?=[^>]*\bclass\s*=\s*["'][^"']*\bb_algo\b[^"']*["'])[^>]*>(.*?)</li>"""
            ).findAll(html)
            for (block in blocks) {
                val content = block.groupValues[1]
                val anchor = Regex("""(?is)<h2\b[^>]*>.*?<a\b([^>]*)>(.*?)</a>.*?</h2>""").find(content)
                    ?: Regex("""(?is)<a\b([^>]*)>(.*?)</a>""").find(content)
                    ?: continue
                val href = Regex("""(?is)\bhref\s*=\s*["']([^"']+)["']""")
                    .find(anchor.groupValues[1])?.groupValues?.getOrNull(1) ?: continue
                val title = cleanHtml(anchor.groupValues[2])
                val snippet = Regex("""(?is)<p\b[^>]*>(.*?)</p>""").find(content)?.groupValues?.getOrNull(1)?.let(::cleanHtml).orEmpty()
                if (title.isNotBlank()) candidates += Triple(href, title, snippet)
                if (candidates.size >= limit) break
            }
        }

        // Last-resort parser for result pages that keep titles inside an h2 anchor.
        if (candidates.isEmpty()) {
            val headings = Regex("""(?is)<h2\b[^>]*>.*?<a\b([^>]*)>(.*?)</a>.*?</h2>""").findAll(html)
            for (heading in headings) {
                val href = Regex("""(?is)\bhref\s*=\s*["']([^"']+)["']""")
                    .find(heading.groupValues[1])?.groupValues?.getOrNull(1) ?: continue
                val title = cleanHtml(heading.groupValues[2])
                if (title.isNotBlank()) candidates += Triple(href, title, "")
                if (candidates.size >= limit) break
            }
        }

        // Yahoo and some regional search frontends put an anchor inside an h3.
        if (candidates.isEmpty()) {
            val h3Links = Regex("""(?is)<h3\b[^>]*>.*?<a\b([^>]*)>(.*?)</a>.*?</h3>""").findAll(html)
            for (heading in h3Links) {
                val href = Regex("""(?is)\bhref\s*=\s*["']([^"']+)["']""")
                    .find(heading.groupValues[1])?.groupValues?.getOrNull(1) ?: continue
                val title = cleanHtml(heading.groupValues[2])
                if (title.isNotBlank()) candidates += Triple(href, title, "")
                if (candidates.size >= limit) break
            }
        }

        // Google results wrap external links around h3 headings instead of h2 elements.
        if (candidates.isEmpty()) {
            val headingAnchors = Regex("""(?is)<a\b([^>]*)>(.*?)</a>""").findAll(html)
            for (anchor in headingAnchors) {
                val inner = anchor.groupValues[2]
                if (!inner.contains("<h3", ignoreCase = true)) continue
                val href = Regex("""(?is)\bhref\s*=\s*["']([^"']+)["']""")
                    .find(anchor.groupValues[1])?.groupValues?.getOrNull(1) ?: continue
                val title = cleanHtml(inner)
                if (title.isNotBlank()) candidates += Triple(href, title, "")
                if (candidates.size >= limit) break
            }
        }

        val blockedHosts = setOf("duckduckgo.com", "bing.com", "search.brave.com", "google.com", "search.yahoo.com", "r.search.yahoo.com", "yahoo.com", "microsoft.com")
        return candidates.mapNotNull { (href, title, snippet) ->
            val url = unwrapSearchUrl(href) ?: return@mapNotNull null
            val host = sourceHost(url)
            if (host.isBlank() || blockedHosts.any { host == it || host.endsWith(".$it") }) return@mapNotNull null
            if (title.length < 3 || title.equals("cached", true) || title.equals("translate", true)) return@mapNotNull null
            PublicProfileResult(
                provider = "$provider · ${displayHost(host)}",
                title = title.take(240),
                description = snippet.ifBlank { "نتيجة ويب عامة مفهرسة · افتح المصدر للتحقق من النص والسياق." }.take(500),
                url = url,
                confidence = if (snippet.isBlank()) 62 else 73
            )
        }.distinctBy { canonicalUrl(it.url) }.take(limit)
    }

    private suspend fun searchWikimediaImages(query: String, limit: Int): List<PublicProfileResult> = withContext(Dispatchers.IO) {
        val endpoint = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=${enc(query)}&gsrnamespace=6&gsrlimit=${limit.coerceIn(1, 12)}&prop=info%7Cimageinfo&inprop=url&iiprop=url&iiurlwidth=560&format=json"
        val root = JSONObject(fetchPublicApi(endpoint, "application/json", 8_000))
        val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: JSONObject()
        val keys = pages.keys()
        buildList {
            while (keys.hasNext() && size < limit) {
                val page = pages.optJSONObject(keys.next()) ?: continue
                val title = page.optString("title").removePrefix("File:")
                if (title.isBlank()) continue
                val pageUrl = page.optString("fullurl").takeIf { it.startsWith("https://") }
                    ?: "https://commons.wikimedia.org/wiki/${enc("File:$title")}"
                val imageInfo = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val imageUrl = imageInfo.optString("thumburl").ifBlank { imageInfo.optString("url") }
                    .takeIf { it.startsWith("https://") } ?: continue
                add(
                    PublicProfileResult(
                        provider = "Wikimedia Commons images",
                        title = title,
                        description = "صورة مفهرسة عامة من Wikimedia Commons. راجع صفحة المصدر والترخيص قبل إعادة الاستخدام.",
                        url = pageUrl,
                        confidence = 78,
                        relevanceScore = 55,
                        imageUrl = imageUrl
                    )
                )
            }
        }
    }

    private fun fetchPublicApi(endpoint: String, accept: String, timeoutMs: Int): String {
        val connection = (URL(endpoint).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "OSINTWorkbench/0.4 (public-source research; see project README)")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { it.bufferedReader(StandardCharsets.UTF_8).readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("HTTP $status from ${URL(endpoint).host}")
            if (body.isBlank()) throw IllegalStateException("استجابة فارغة من ${URL(endpoint).host}")
            return body
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Deterministic lexical ranking: exact identifier hits rank above vague token overlap.
     * The score is a relevance heuristic, not a probability or proof that the result belongs
     * to the searched person/number.
     */
    internal fun calculateRelevance(
        query: String,
        kind: IdentifierKind,
        result: PublicProfileResult,
        queryExpansionIndex: Int = 0
    ): Int {
        val title = normalizeForRanking(result.title)
        val description = normalizeForRanking(result.description)
        val destination = normalizeForRanking(result.url)
        val combined = "$title $description $destination"
        var score = 18

        when (kind) {
            IdentifierKind.PHONE -> {
                val digits = query.filter(Char::isDigit)
                val resultDigits = (result.title + " " + result.description + " " + result.url).filter(Char::isDigit)
                if (digits.length >= 7 && resultDigits.contains(digits)) score += 58
                else if (digits.length >= 7 && resultDigits.contains(digits.takeLast(7))) score += 28
                if (result.provider.contains("Tellows", true) ||
                    result.provider.contains("Sync.ME", true) ||
                    result.provider.contains("Should I Answer", true)
                ) score += 5
            }
            IdentifierKind.EMAIL -> {
                val email = query.trim().lowercase(Locale.ROOT)
                if (email.isNotBlank() && combined.contains(email)) score += 60
                else {
                    val parts = email.split('@')
                    if (parts.firstOrNull().orEmpty().length >= 3 && combined.contains(parts.first())) score += 18
                    if (parts.getOrNull(1).orEmpty().contains('.') && combined.contains(parts[1])) score += 12
                }
            }
            IdentifierKind.USERNAME -> {
                val username = query.trim().removePrefix("@").lowercase(Locale.ROOT)
                if (username.length >= 2 && containsExactIdentifierToken(title, username)) score += 48
                else if (username.length >= 2 && containsExactIdentifierToken(destination, username)) score += 48
                else if (username.length >= 2 && containsExactIdentifierToken(description, username)) score += 30
            }
            IdentifierKind.NAME -> {
                val phrase = normalizeForRanking(query)
                if (phrase.length >= 3 && title.contains(phrase)) score += 36
                if (phrase.length >= 3 && description.contains(phrase)) score += 16
                val tokens = phrase.split(' ').filter { it.length >= 2 }.distinct()
                if (tokens.isNotEmpty()) {
                    val titleMatches = tokens.count { title.contains(it) }
                    val descriptionMatches = tokens.count { description.contains(it) }
                    score += (24 * titleMatches / tokens.size)
                    score += (12 * descriptionMatches / tokens.size)
                }
            }
        }

        score -= queryExpansionIndex.coerceAtLeast(0) * 3
        return score.coerceIn(0, 100)
    }

    internal fun containsExactUsername(username: String, result: PublicProfileResult): Boolean {
        val needle = username.trim().removePrefix("@")
        if (needle.length < 2) return false
        val decodedUrl = runCatching {
            URLDecoder.decode(result.url, StandardCharsets.UTF_8.name())
        }.getOrDefault(result.url)
        // A username in a paragraph may be a casual mention, not the account.
        // Accept a token in the result title or destination URL only.
        return containsExactIdentifierToken(result.title, needle) ||
            containsExactIdentifierToken(decodedUrl, needle)
    }

    private fun containsExactIdentifierToken(value: String, identifier: String): Boolean {
        if (identifier.isBlank()) return false
        val pattern = Regex(
            "(?i)(^|[^A-Za-z0-9._-])" + Regex.escape(identifier) + "($|[^A-Za-z0-9._-])"
        )
        return pattern.containsMatchIn(value)
    }

    private fun normalizeForRanking(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}@.+_-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private suspend fun searchInstantAnswer(query: String, limit: Int): List<PublicProfileResult> = withContext(Dispatchers.IO) {
        val endpoint = "https://api.duckduckgo.com/?q=${URLEncoder.encode(query, StandardCharsets.UTF_8.name())}&format=json&no_html=1&skip_disambig=1"
        val connection = (URL(endpoint).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 7_000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "OSINT-Workbench/0.3 (public research app)")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299 || body.isBlank()) return@withContext emptyList()
            val root = JSONObject(body)
            val results = mutableListOf<PublicProfileResult>()

            val abstractUrl = root.optString("AbstractURL")
            val abstractText = root.optString("AbstractText").trim()
            if (abstractText.isNotBlank() && abstractUrl.startsWith("https://")) {
                results += PublicProfileResult(
                    provider = "DuckDuckGo Instant Answer",
                    title = root.optString("Heading").ifBlank { query },
                    description = abstractText.take(600),
                    url = abstractUrl,
                    confidence = 70
                )
            }

            fun appendTopics(items: JSONArray, depth: Int = 0) {
                if (depth > 2 || results.size >= limit) return
                for (index in 0 until items.length()) {
                    if (results.size >= limit) break
                    val item = items.optJSONObject(index) ?: continue
                    val nested = item.optJSONArray("Topics")
                    if (nested != null) {
                        appendTopics(nested, depth + 1)
                        continue
                    }
                    val url = item.optString("FirstURL")
                    val text = item.optString("Text").trim()
                    if (text.isNotBlank() && url.startsWith("https://")) {
                        results += PublicProfileResult(
                            provider = "DuckDuckGo Instant Answer",
                            title = text.substringBefore(" - ").take(200).ifBlank { query },
                            description = text.take(600),
                            url = url,
                            confidence = 62
                        )
                    }
                }
            }

            appendTopics(root.optJSONArray("Results") ?: JSONArray())
            appendTopics(root.optJSONArray("RelatedTopics") ?: JSONArray())
            results.distinctBy { canonicalUrl(it.url) }.take(limit)
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun unwrapSearchUrl(rawHref: String): String? = runCatching {
        val absolute = when {
            rawHref.startsWith("//") -> "https:$rawHref"
            rawHref.startsWith("/url?") -> "https://www.google.com$rawHref"
            rawHref.startsWith("/") -> "https://duckduckgo.com$rawHref"
            else -> rawHref
        }
        val uri = URI(absolute)
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)
        val queryParams = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
            val pieces = part.split('=', limit = 2)
            if (pieces.size == 2) pieces[0] to pieces[1] else null
        }.toMap()
        val isDuckDuckGo = host == "duckduckgo.com" || host.endsWith(".duckduckgo.com")
        val isGoogleRedirect = host.startsWith("google.") || host.contains(".google.") && uri.path == "/url"
        val isYahooRedirect = host == "r.search.yahoo.com" || host == "search.yahoo.com"
        val isBingRedirect = host == "bing.com" || host.endsWith(".bing.com")
        val queryDestination = when {
            isDuckDuckGo -> queryParams["uddg"]
            isGoogleRedirect -> queryParams["url"] ?: queryParams["q"]
            isYahooRedirect -> queryParams["url"] ?: queryParams["u"] ?: queryParams["RU"]
            else -> null
        }
        val yahooPathDestination = if (isYahooRedirect) {
            Regex("""(?:^|/)RU=([^/]+)""").find(uri.rawPath.orEmpty())?.groupValues?.getOrNull(1)
        } else null
        val bingDestination = if (isBingRedirect) {
            queryParams["u"]?.let { raw ->
                val decodedParameter = URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
                val payload = decodedParameter.removePrefix("a1")
                val base64Decoded = runCatching {
                    String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8)
                }.getOrNull()
                base64Decoded?.takeIf { it.startsWith("https://") }
                    ?: decodedParameter.takeIf { it.startsWith("https://") }
            }
        } else null
        val encodedDestination = queryDestination ?: yahooPathDestination
        val destination = bingDestination
            ?: encodedDestination?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
        if (!destination.isNullOrBlank() && destination.startsWith("https://")) destination else absolute
    }.getOrNull()?.takeIf {
        val host = runCatching { URL(it).host.lowercase(Locale.ROOT) }.getOrDefault("")
        it.startsWith("https://") && it.length <= 2_048 &&
            !host.startsWith("google.") && !host.contains(".google.") &&
            host != "duckduckgo.com" && !host.endsWith(".duckduckgo.com") &&
            !host.contains("bing.com") && !host.endsWith("search.yahoo.com") &&
            host != "r.search.yahoo.com"
    }

    private fun cleanHtml(raw: String): String = raw
        .replace(Regex("(?is)<script\\b.*?</script>"), " ")
        .replace(Regex("(?is)<style\\b.*?</style>"), " ")
        .replace(Regex("(?s)<[^>]+>"), " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&#x27;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun canonicalUrl(raw: String): String = runCatching {
        val uri = URI(raw)
        URI(uri.scheme, uri.authority, uri.path, null, null).toString().trimEnd('/')
    }.getOrDefault(raw.substringBefore('#').substringBefore('?').trimEnd('/')).lowercase(Locale.ROOT)

    private fun sourceHost(raw: String): String = runCatching { URL(raw).host.lowercase(Locale.ROOT).removePrefix("www.") }.getOrDefault("")

    private fun displayHost(host: String): String = when {
        host == "reddit.com" || host.endsWith(".reddit.com") -> "Reddit"
        host == "github.com" || host.endsWith(".github.com") -> "GitHub"
        host == "tellows.com" || host.endsWith(".tellows.com") -> "Tellows"
        host == "sync.me" || host.endsWith(".sync.me") -> "Sync.ME"
        host == "shouldianswer.com" || host.endsWith(".shouldianswer.com") -> "Should I Answer"
        host.contains("youtube.com") -> "YouTube"
        host == "x.com" || host.endsWith(".x.com") -> "X"
        else -> host
    }

    private fun normalizePhoneForSearch(raw: String): List<String> {
        val clean = raw.trim()
        val digits = clean.filter(Char::isDigit)
        val values = linkedSetOf<String>()
        values += clean
        values += digits
        if (clean.startsWith("+")) {
            runCatching {
                val util = PhoneNumberUtil.getInstance()
                val parsed = util.parse(clean, "ZZ")
                if (util.isPossibleNumber(parsed)) {
                    values += util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
                    values += util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
                }
            }
        }
        return values.filter(String::isNotBlank)
    }

    private data class SearchEndpoint(val provider: String, val url: String)

    private data class SearchAttempt(
        val index: Int,
        val query: String,
        val provider: String,
        val results: List<PublicProfileResult>,
        val error: String?
    )

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
