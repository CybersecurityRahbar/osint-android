package com.cyberphantom.osintworkbench.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import org.w3c.dom.Element

/**
 * Multi-source news collector. GDELT and Google News RSS are queried independently, with
 * publisher-scoped RSS feeds added for major international and Arabic-language outlets.
 * Each story retains its publisher label, source URL, publication time, and image URL when supplied.
 */
class MultiSourceNewsEngine {
    private data class DirectFeed(
        val name: String,
        val url: String,
        val language: String,
        val country: String,
        val host: String
    )

    private val directFeeds = listOf(
        DirectFeed("BBC World", "https://feeds.bbci.co.uk/news/world/rss.xml", "en", "GB", "bbc.co.uk"),
        DirectFeed("Al Jazeera English", "https://www.aljazeera.com/xml/rss/all.xml", "en", "QA", "aljazeera.com"),
        DirectFeed("Al Jazeera Arabic", "https://www.aljazeera.net/rss", "ar", "QA", "aljazeera.net"),
        DirectFeed("The Guardian World", "https://www.theguardian.com/world/rss", "en", "GB", "theguardian.com"),
        DirectFeed("DW English", "https://rss.dw.com/rdf/rss-en-all", "en", "DE", "dw.com"),
        DirectFeed("France 24 English", "https://www.france24.com/en/rss", "en", "FR", "france24.com"),
        DirectFeed("France 24 Arabic", "https://www.france24.com/ar/rss", "ar", "FR", "france24.com"),
        DirectFeed("Sky News Arabia", "https://www.skynewsarabia.com/rss.xml", "ar", "AE", "skynewsarabia.com")
    )

    private val publishers = listOf(
        Publisher("Reuters", "reuters.com", "en-US", "US", "US:en"),
        Publisher("Associated Press", "apnews.com", "en-US", "US", "US:en"),
        Publisher("BBC News", "bbc.com", "en-US", "GB", "GB:en"),
        Publisher("Al Jazeera", "aljazeera.com", "en-US", "US", "US:en"),
        Publisher("الجزيرة", "aljazeera.net", "ar", "SA", "SA:ar"),
        Publisher("DW", "dw.com", "en-US", "DE", "DE:en"),
        Publisher("France 24", "france24.com", "en-US", "FR", "FR:en"),
        Publisher("The Guardian", "theguardian.com", "en-US", "GB", "GB:en"),
        Publisher("CNN", "cnn.com", "en-US", "US", "US:en"),
        Publisher("العربية", "alarabiya.net", "ar", "SA", "SA:ar"),
        Publisher("سكاي نيوز عربية", "skynewsarabia.com", "ar", "AE", "AE:ar"),
        Publisher("الحرة", "alhurra.com", "ar", "US", "US:ar"),
        Publisher("الشرق الأوسط", "aawsat.com", "ar", "SA", "SA:ar"),
        Publisher("الميادين", "almayadeen.net", "ar", "LB", "LB:ar"),
        Publisher("The New York Times", "nytimes.com", "en-US", "US", "US:en"),
        Publisher("NPR", "npr.org", "en-US", "US", "US:en"),
        Publisher("BBC Arabic", "bbc.com", "ar", "GB", "GB:ar"),
        Publisher("CNN Arabic", "arabic.cnn.com", "ar", "US", "US:ar"),
        Publisher("RT Arabic", "arabic.rt.com", "ar", "RU", "RU:ar"),
        Publisher("France 24 Arabic", "france24.com", "ar", "FR", "FR:ar"),
        Publisher("DW Arabic", "dw.com", "ar", "DE", "DE:ar"),
        Publisher("Independent Arabia", "independentarabia.com", "ar", "GB", "GB:ar"),
        Publisher("AFP", "afp.com", "en-US", "FR", "FR:en"),
        Publisher("Asharq News", "asharq.com", "ar", "SA", "SA:ar"),
        Publisher("The National", "thenationalnews.com", "en-US", "AE", "AE:en"),
        Publisher("Gulf News", "gulfnews.com", "en-US", "AE", "AE:en"),
        Publisher("Erem News", "eremnews.com", "ar", "AE", "AE:ar"),
        Publisher("Al Masry Al Youm", "almasryalyoum.com", "ar", "EG", "EG:ar"),
        Publisher("Youm7", "youm7.com", "ar", "EG", "EG:ar")
    )

    suspend fun search(
        query: String,
        maxRecords: Int = 40,
        includeImages: Boolean = true,
        startTimeMillis: Long? = null,
        endTimeMillis: Long? = null,
        onProgress: (String) -> Unit = {}
    ): NewsSearchResult = withContext(Dispatchers.IO) {
        val clean = query.trim()
        require(clean.isNotBlank()) { "أدخل كلمات البحث عن الأخبار." }
        require(clean.length <= 180) { "عبارة البحث طويلة جداً." }
        require((startTimeMillis == null) == (endTimeMillis == null)) {
            "حدد بداية الفترة ونهايتها معاً."
        }
        if (startTimeMillis != null && endTimeMillis != null) {
            require(startTimeMillis <= endTimeMillis) { "تاريخ البداية يجب أن يسبق تاريخ النهاية." }
        }
        val started = System.nanoTime()
        val limit = maxRecords.coerceIn(1, 80)
        val hasRange = startTimeMillis != null && endTimeMillis != null
        val apiWindowSupported = !hasRange ||
            startTimeMillis!! >= System.currentTimeMillis() - 89L * 24L * 60L * 60L * 1000L
        val datedQuery = if (hasRange) {
            val startDate = Instant.ofEpochMilli(startTimeMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
            val endDateExclusive = Instant.ofEpochMilli(endTimeMillis!!).atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)
            "$clean after:$startDate before:$endDateExclusive"
        } else clean

        onProgress(if (hasRange) "تطبيق النطاق الزمني المحدد…" else "تجميع تغطية الأخبار الحديثة…")
        var outcomes = coroutineScope {
            val semaphore = Semaphore(6)
            val tasks = mutableListOf<kotlinx.coroutines.Deferred<SourceOutcome>>()
            if (apiWindowSupported) {
                tasks += async {
                    runSource(semaphore, "GDELT DOC API") {
                        queryGdelt(clean, limit, includeImages, startTimeMillis, endTimeMillis)
                    }
                }
            }
            tasks += async {
                runSource(semaphore, "Google News RSS · Arabic / Yemen") {
                    queryGoogleNews(datedQuery, limit, null, includeImages, "ar", "YE", "YE:ar", "Google News RSS · Arabic / Yemen")
                }
            }
            tasks += async {
                runSource(semaphore, "Google News RSS · Arabic / Saudi Arabia") {
                    queryGoogleNews(datedQuery, limit, null, includeImages, "ar", "SA", "SA:ar", "Google News RSS · Arabic / Saudi Arabia")
                }
            }
            tasks += async {
                runSource(semaphore, "Google News RSS · English / US") {
                    queryGoogleNews(datedQuery, limit, null, includeImages, "en-US", "US", "US:en", "Google News RSS · English / US")
                }
            }
            tasks += async {
                runSource(semaphore, "Google News RSS · English / UK") {
                    queryGoogleNews(datedQuery, limit, null, includeImages, "en-GB", "GB", "GB:en", "Google News RSS · English / UK")
                }
            }
            tasks += async { runSource(semaphore, "Bing News RSS") { queryBingNews(datedQuery, limit, includeImages) } }
            tasks += async { runSource(semaphore, "SearXNG News") { querySearxNews(datedQuery, limit, includeImages) } }
            tasks.awaitAll()
        }

        onProgress("قراءة خلاصات الناشرين المباشرة…")
        val directOutcomes = coroutineScope {
            val semaphore = Semaphore(6)
            directFeeds.map { feed ->
                async {
                    runSource(semaphore, "Direct RSS · ${feed.name}") {
                        queryDirectFeed(feed, clean, limit, includeImages)
                    }
                }
            }.awaitAll()
        }

        onProgress("توسيع البحث عبر فهارس الناشرين…")
        val publisherOutcomes = coroutineScope {
            val semaphore = Semaphore(6)
            publishers.map { publisher ->
                async {
                    runSource(semaphore, "Google News RSS · ${publisher.name}") {
                        queryGoogleNews(datedQuery, maxOf(5, limit / 3), publisher, includeImages)
                    }
                }
            }.awaitAll()
        }
        outcomes = outcomes + directOutcomes + publisherOutcomes

        onProgress("فلترة التواريخ وإزالة المقالات المكررة…")
        val rawArticles = outcomes.flatMap { it.articles }
            .filter { it.title.isNotBlank() && it.url.startsWith("https://") }
            .distinctBy { normalizeNewsUrl(it.url).lowercase(Locale.ROOT) }

        var excludedUnknownDates = 0
        var excludedOutsideRange = 0
        val articles = if (hasRange) {
            rawArticles.filter { article ->
                val published = parsePublishedMillis(article.publishedAt)
                when {
                    published == null -> {
                        excludedUnknownDates++
                        false
                    }
                    published < startTimeMillis!! || published > endTimeMillis!! -> {
                        excludedOutsideRange++
                        false
                    }
                    else -> true
                }
            }
        } else rawArticles
        val sortedArticles = articles
            .sortedByDescending { parsePublishedMillis(it.publishedAt) ?: Long.MIN_VALUE }
            .take(limit)

        val failed = outcomes.filter { it.error != null }
        val emptySources = outcomes.filter { it.error == null && it.articles.isEmpty() }.map { it.source }
        val failureText = failed.take(6).joinToString(" · ") { it.source + ": " + it.error }
        val warnings = buildList {
            if (hasRange && !apiWindowSupported) {
                add("نطاق GDELT للأخبار الحديثة محدود بآخر ثلاثة أشهر؛ جرى تخطي هذا المزود للفترة القديمة مع استمرار بقية المصادر.")
            }
            if (hasRange && excludedUnknownDates > 0) {
                add("استُبعدت $excludedUnknownDates مقالات لم يتوفر لها تاريخ نشر موثوق حتى لا تتجاوز الفترة الزمنية المطلوبة.")
            }
            if (hasRange && excludedOutsideRange > 0) {
                add("استُبعدت $excludedOutsideRange مقالات خارج النطاق الزمني المحدد.")
            }
            if (sortedArticles.isEmpty()) {
                add(
                    "لم تُرجع مصادر الأخبار مقالات ضمن الفترة المحددة. " +
                        (if (failureText.isBlank()) "تحققت المصادر دون أخطاء معلنة." else "أخطاء بعض المصادر: $failureText") +
                        (if (emptySources.isEmpty()) "" else " مصادر استجابت دون مقالات: ${emptySources.take(6).joinToString("، ")}")
                )
            } else if (failed.isNotEmpty()) {
                add("نتائج جزئية: نجح ${outcomes.count { it.articles.isNotEmpty() }} مزود وتعذر الوصول إلى بعض المصادر: $failureText")
            }
        }.distinct()

        NewsSearchResult(
            query = clean,
            articles = sortedArticles,
            warning = warnings.takeIf { it.isNotEmpty() }?.joinToString("\n"),
            elapsedMs = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0L),
            sourcesAttempted = outcomes.size
        )
    }

    private fun parsePublishedMillis(raw: String): Long? {
        val value = raw.trim()
        if (value.isBlank()) return null
        runCatching { return Instant.parse(value).toEpochMilli() }
        runCatching { return java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        runCatching { return java.time.ZonedDateTime.parse(value, RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        runCatching {
            val format = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX", Locale.US)
            return format.parse(value, Instant::from).toEpochMilli()
        }
        runCatching {
            val format = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.US).withZone(ZoneOffset.UTC)
            return format.parse(value, Instant::from).toEpochMilli()
        }
        runCatching {
            return LocalDate.parse(value.take(10)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        return null
    }

    private suspend fun runSource(
        semaphore: Semaphore,
        source: String,
        fetch: suspend () -> List<NewsArticle>
    ): SourceOutcome {
        semaphore.acquire()
        try {
            return try {
                SourceOutcome(source, fetch(), null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                SourceOutcome(source, emptyList(), error.message ?: "تعذر الوصول")
            }
        } finally {
            semaphore.release()
        }
    }

    private suspend fun queryGdelt(
        query: String,
        maxRecords: Int,
        includeImages: Boolean,
        startTimeMillis: Long? = null,
        endTimeMillis: Long? = null
    ): List<NewsArticle> = withContext(Dispatchers.IO) {
        val limit = maxRecords.coerceIn(1, 75)
        val cleanQuery = query.trim().replace(Regex("\\s+"), " ")
        val apiQuery = if (Regex("(?i)\\bOR\\b").containsMatchIn(cleanQuery) &&
            !cleanQuery.trim().startsWith("(")) "($cleanQuery)" else cleanQuery
        val endpoints = if (startTimeMillis != null && endTimeMillis != null) {
            val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC)
            val start = formatter.format(Instant.ofEpochMilli(startTimeMillis))
            val end = formatter.format(Instant.ofEpochMilli(endTimeMillis))
            listOf(
                "https://api.gdeltproject.org/api/v2/doc/doc?query=${enc(apiQuery)}&mode=artlist&format=json&sort=datedesc&maxrecords=$limit&STARTDATETIME=$start&ENDDATETIME=$end",
                "https://api.gdeltproject.org/api/v2/doc/doc?query=${enc(apiQuery)}&mode=artlist&format=json&sort=datedesc&maxrecords=${minOf(limit, 35)}&STARTDATETIME=$start&ENDDATETIME=$end"
            )
        } else {
            listOf(
                "https://api.gdeltproject.org/api/v2/doc/doc?query=${enc(apiQuery)}&mode=artlist&format=json&sort=datedesc&maxrecords=$limit&timespan=1week",
                "https://api.gdeltproject.org/api/v2/doc/doc?query=${enc(apiQuery)}&mode=artlist&format=json&maxrecords=${minOf(limit, 35)}"
            )
        }
        var lastFailure: Exception? = null
        for ((endpointIndex, endpoint) in endpoints.withIndex()) {
            val maxAttempts = if (endpointIndex == 0) 2 else 1
            for (attempt in 0 until maxAttempts) {
                try {
                    val raw = getText(endpoint, 9_000, "application/json, text/plain, */*")
                    val root = try {
                        JSONObject(raw)
                    } catch (error: Exception) {
                        val preview = raw.replace(Regex("\\s+"), " ").take(180)
                        throw IllegalStateException("GDELT returned non-JSON data: $preview", error)
                    }
                    lastFailure = null
                    val articles = parseGdeltResponse(root, includeImages)
                    if (articles.isNotEmpty()) return@withContext articles
                    break
                } catch (error: Exception) {
                    lastFailure = error
                    val message = error.message.orEmpty()
                    val retryable = listOf("HTTP 429", "HTTP 500", "HTTP 502", "HTTP 503", "HTTP 504", "timed out", "timeout")
                        .any { message.contains(it, ignoreCase = true) }
                    if (!retryable || attempt == maxAttempts - 1) break
                    kotlinx.coroutines.delay(5_200L)
                }
            }
            if (endpointIndex == endpoints.lastIndex && lastFailure != null) {
                throw IllegalStateException("GDELT DOC API failed after retries: ${lastFailure?.message ?: "unknown error"}", lastFailure)
            }
        }
        emptyList()
    }

    internal fun parseGdeltResponse(root: JSONObject, includeImages: Boolean = true): List<NewsArticle> {
        val items = root.optJSONArray("articles") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val url = item.optString("url").takeIf { it.startsWith("https://") } ?: continue
                val title = item.optString("title").trim().takeIf(String::isNotBlank) ?: continue
                val domain = item.optString("domain").removePrefix("www.")
                add(
                    NewsArticle(
                        title = title,
                        url = url,
                        domain = domain,
                        publishedAt = item.optString("seendate"),
                        language = item.optString("language"),
                        sourceCountry = item.optString("sourcecountry"),
                        publisherName = publisherLabel(domain),
                        imageUrl = item.optString("socialimage").takeIf { includeImages && it.startsWith("https://") },
                        provider = "GDELT DOC API"
                    )
                )
            }
        }.distinctBy { it.url }
    }

    private suspend fun queryDirectFeed(
        feed: DirectFeed,
        query: String,
        maxRecords: Int,
        includeImages: Boolean
    ): List<NewsArticle> = withContext(Dispatchers.IO) {
        val xml = getText(feed.url, 8_000, "application/rss+xml, application/atom+xml, application/xml, text/xml")
        val tokens = query.lowercase(Locale.ROOT)
            .split(Regex("\\s+"))
            .map { it.trim { ch -> ch in "\"'(),.:;" } }
            .filter { it.length >= 3 }
        parseRss(
            xml = xml,
            requestedPublisher = null,
            language = feed.language,
            country = feed.country,
            includeImages = includeImages,
            providerLabelOverride = "Direct RSS · ${feed.name}",
            publisherNameOverride = feed.name,
            publisherDomainOverride = feed.host
        ).asSequence()
            .filter { article ->
                if (tokens.isEmpty()) true
                else {
                    val searchable = (article.title + " " + article.description).lowercase(Locale.ROOT)
                    val matched = tokens.count(searchable::contains)
                    matched >= ((tokens.size + 1) / 2).coerceAtLeast(1) ||
                        searchable.contains(query.lowercase(Locale.ROOT))
                }
            }
            .take(maxRecords.coerceIn(1, 80))
            .toList()
    }

    private suspend fun queryGoogleNews(
        query: String,
        maxRecords: Int,
        publisher: Publisher?,
        includeImages: Boolean,
        languageOverride: String? = null,
        countryOverride: String? = null,
        editionOverride: String? = null,
        providerOverride: String? = null
    ): List<NewsArticle> = withContext(Dispatchers.IO) {
        val queryText = if (publisher == null) query else query + " site:" + publisher.domain
        val language = publisher?.language ?: languageOverride ?: "ar"
        val country = publisher?.country ?: countryOverride ?: "YE"
        val edition = publisher?.edition ?: editionOverride ?: "YE:ar"
        val q = enc(queryText)
        val hl = enc(language)
        val gl = enc(country)
        val ceid = enc(edition)
        val endpoint = "https://news.google.com/rss/search?q=$q&hl=$hl&gl=$gl&ceid=$ceid"
        val xml = getText(endpoint, 8_500, "application/rss+xml, application/xml, text/xml")
        val label = providerOverride ?: if (publisher == null) "Google News RSS" else "Google News RSS · " + publisher.name
        parseRss(xml, publisher, language, country, includeImages, label)
            .take(maxRecords.coerceIn(1, 60))
    }

    private suspend fun queryBingNews(query: String, maxRecords: Int, includeImages: Boolean): List<NewsArticle> = withContext(Dispatchers.IO) {
        val q = enc(query)
        val endpoint = "https://www.bing.com/news/search?q=$q&format=rss&setmkt=en-US"
        val xml = getText(endpoint, 8_000, "application/rss+xml, application/xml, text/xml")
        parseRss(xml, null, "en", "US", includeImages, "Bing News RSS")
            .take(maxRecords.coerceIn(1, 60))
    }

    private suspend fun querySearxNews(query: String, maxRecords: Int, includeImages: Boolean): List<NewsArticle> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (instance in listOf("https://searx.be", "https://search.disroot.org")) {
            try {
                val q = enc(query)
                val endpoint = instance + "/search?q=$q&categories=news&format=json"
                val root = JSONObject(getText(endpoint, 4_500, "application/json"))
                val rows = root.optJSONArray("results") ?: JSONArray()
                val articles = buildList {
                    for (i in 0 until minOf(rows.length(), maxRecords * 3)) {
                        val row = rows.optJSONObject(i) ?: continue
                        val url = row.optString("url").takeIf { it.startsWith("https://") } ?: continue
                        val title = row.optString("title").trim().takeIf(String::isNotBlank) ?: continue
                        val domain = runCatching { URL(url).host.removePrefix("www.") }.getOrDefault("")
                        val image = sequenceOf("thumbnail_src", "img_src", "thumbnail")
                            .map { key -> row.optString(key).takeIf { it.startsWith("https://") } }
                            .firstOrNull { it != null }
                        add(
                            NewsArticle(
                                title = title,
                                url = url,
                                domain = domain,
                                publishedAt = row.optString("publishedDate").ifBlank { row.optString("published_date") },
                                language = "",
                                sourceCountry = "",
                                publisherName = row.optString("source").ifBlank { publisherLabel(domain) },
                                imageUrl = image.takeIf { includeImages },
                                provider = "SearXNG News"
                            )
                        )
                        if (size >= maxRecords) break
                    }
                }
                if (articles.isNotEmpty()) return@withContext articles.distinctBy { it.url }
                lastError = IllegalStateException("أعاد SearXNG استجابة JSON دون مقالات")
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("تعذر الوصول إلى مصادر SearXNG News")
    }

    internal fun parseRss(
        xml: String,
        requestedPublisher: Publisher?,
        language: String,
        country: String,
        includeImages: Boolean = true,
        providerLabelOverride: String? = null,
        publisherNameOverride: String? = null,
        publisherDomainOverride: String? = null
    ): List<NewsArticle> {
        require(xml.length <= 2_000_000) { "RSS response exceeds the parser size limit." }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isXIncludeAware = false
            isExpandEntityReferences = false
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val rssItems = document.getElementsByTagName("item")
        val items = if (rssItems.length == 0) document.getElementsByTagName("entry") else rssItems
        return buildList {
            for (index in 0 until items.length) {
                val item = items.item(index) as? Element ?: continue
                var title = ""
                var link = ""
                var publishedAt = ""
                var description = ""
                var sourceName = ""
                var sourceUrl = ""
                var imageUrl = ""
                val children = item.childNodes
                for (childIndex in 0 until children.length) {
                    val child = children.item(childIndex) as? Element ?: continue
                    when (child.tagName.substringAfter(':').lowercase(Locale.ROOT)) {
                        "title" -> title = child.textContent.orEmpty().trim()
                        "description", "summary", "subtitle", "encoded" -> {
                            if (description.isBlank()) description = child.textContent.orEmpty().trim()
                        }
                        "link" -> {
                            val childLink = child.textContent.orEmpty().trim()
                            val href = child.getAttribute("href").trim()
                            if (link.isBlank() || child.getAttribute("rel").equals("alternate", true)) {
                                link = normalizeNewsUrl(href.ifBlank { childLink })
                            }
                        }
                        "pubdate", "published", "updated", "date" -> {
                            if (publishedAt.isBlank()) publishedAt = child.textContent.orEmpty().trim()
                        }
                        "source" -> {
                            sourceUrl = child.getAttribute("url").orEmpty()
                            sourceName = child.textContent.orEmpty().trim()
                            if (sourceName.isBlank()) sourceName = child.getAttribute("title").orEmpty()
                        }
                        "author" -> {
                            if (sourceName.isBlank()) {
                                sourceName = child.getElementsByTagName("name").item(0)?.textContent.orEmpty().trim()
                            }
                        }
                        "thumbnail", "content", "enclosure" -> {
                            val candidate = child.getAttribute("url").ifBlank { child.getAttribute("src") }
                            if (imageUrl.isBlank() && candidate.startsWith("https://")) imageUrl = candidate
                        }
                    }
                }
                if (title.isBlank() || !link.startsWith("https://")) continue
                val sourceDomain = runCatching { URL(sourceUrl).host.removePrefix("www.") }.getOrDefault("")
                    .ifBlank { requestedPublisher?.domain.orEmpty() }
                val fallbackDomain = runCatching { URL(link).host.removePrefix("www.") }.getOrDefault("")
                val actualDomain = sourceDomain.ifBlank { fallbackDomain }
                val inferredPublisher = inferPublisherFromTitle(title)
                val actualPublisher = sourceName.ifBlank {
                    publisherNameOverride ?: requestedPublisher?.name ?: inferredPublisher ?: publisherLabel(actualDomain)
                }
                val attributedDomain = sourceDomain.ifBlank {
                    publisherDomainOverride ?: requestedPublisher?.domain ?: knownPublisherDomain(actualPublisher) ?: actualDomain
                }
                add(
                    NewsArticle(
                        title = stripPublisherSuffix(title, actualPublisher),
                        url = link,
                        domain = attributedDomain,
                        publishedAt = publishedAt,
                        language = language,
                        sourceCountry = country,
                        publisherName = actualPublisher,
                        imageUrl = imageUrl.takeIf { includeImages && it.startsWith("https://") },
                        provider = providerLabelOverride ?: if (requestedPublisher == null) "News RSS" else "News RSS · " + requestedPublisher.name,
                        description = stripMarkup(description).take(900)
                    )
                )
            }
        }.distinctBy { "${it.title.lowercase(Locale.ROOT)}|${it.publisherName.lowercase(Locale.ROOT)}" }
    }

    private fun stripMarkup(value: String): String =
        value.replace(Regex("(?is)<[^>]+>"), " ")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace(Regex("\\s+"), " ").trim()

    private fun normalizeNewsUrl(raw: String): String {
        if (!raw.startsWith("https://")) return raw
        return runCatching {
            val uri = URL(raw)
            if (!uri.host.endsWith("bing.com") || !uri.path.contains("apiclick")) return@runCatching raw
            val params = uri.query.orEmpty().split('&').mapNotNull { part ->
                val pair = part.split('=', limit = 2)
                if (pair.size == 2) pair[0] to pair[1] else null
            }.toMap()
            val encoded = params["url"] ?: params["u"]
            val decoded = encoded?.let { java.net.URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
            decoded?.takeIf { it.startsWith("https://") } ?: raw
        }.getOrDefault(raw)
    }

    private fun stripPublisherSuffix(title: String, publisher: String): String =
        title.removeSuffix(" - $publisher").removeSuffix(" – $publisher").trim().ifBlank { title }

    private fun inferPublisherFromTitle(title: String): String? {
        val suffix = title.substringAfterLast(" - ", "").substringAfterLast(" – ", "").trim()
        val known = setOf(
            "Reuters", "Associated Press", "AP News", "BBC", "BBC News", "Al Jazeera",
            "الجزيرة", "DW", "France 24", "The Guardian", "CNN", "العربية", "Sky News"
        )
        return suffix.takeIf { it in known }?.let { if (it == "AP News") "Associated Press" else it }
    }

    private fun knownPublisherDomain(name: String): String? = when (name) {
        "Reuters" -> "reuters.com"
        "Associated Press", "AP News" -> "apnews.com"
        "BBC", "BBC News" -> "bbc.com"
        "Al Jazeera" -> "aljazeera.com"
        "الجزيرة" -> "aljazeera.net"
        "DW" -> "dw.com"
        "France 24" -> "france24.com"
        "The Guardian" -> "theguardian.com"
        "CNN" -> "cnn.com"
        "العربية" -> "alarabiya.net"
        "Sky News" -> "news.sky.com"
        else -> null
    }

    private fun publisherLabel(domain: String): String {
        val host = domain.lowercase(Locale.ROOT).removePrefix("www.")
        return when {
            host == "reuters.com" || host.endsWith(".reuters.com") -> "Reuters"
            host == "apnews.com" || host.endsWith(".apnews.com") -> "Associated Press"
            host == "bbc.com" || host.endsWith(".bbc.com") || host == "bbc.co.uk" -> "BBC News"
            host == "aljazeera.com" || host.endsWith(".aljazeera.com") -> "Al Jazeera"
            host == "aljazeera.net" || host.endsWith(".aljazeera.net") -> "الجزيرة"
            host == "dw.com" || host.endsWith(".dw.com") -> "DW"
            host == "france24.com" || host.endsWith(".france24.com") -> "France 24"
            host == "theguardian.com" || host.endsWith(".theguardian.com") -> "The Guardian"
            host == "cnn.com" || host.endsWith(".cnn.com") -> "CNN"
            host == "alarabiya.net" || host.endsWith(".alarabiya.net") -> "العربية"
            else -> host.ifBlank { "مصدر غير معروف" }
        }
    }

    private fun getText(endpoint: String, timeoutMs: Int, accept: String): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
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
            val body = stream?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                var total = 0
                while (total < 512_000) {
                    val count = input.read(buffer, 0, minOf(buffer.size, 512_000 - total))
                    if (count <= 0) break
                    out.write(buffer, 0, count)
                    total += count
                }
                out.toString(StandardCharsets.UTF_8.name())
            }.orEmpty()
            if (status !in 200..299) {
                val preview = body.replace(Regex("\\s+"), " ").take(160)
                throw IllegalStateException("HTTP $status from ${URL(endpoint).host}" + if (preview.isBlank()) "" else " · $preview")
            }
            if (body.isBlank()) throw IllegalStateException("Empty response from ${URL(endpoint).host}")
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    internal data class Publisher(
        val name: String,
        val domain: String,
        val language: String,
        val country: String,
        val edition: String
    )

    private data class SourceOutcome(val source: String, val articles: List<NewsArticle>, val error: String?)
}
