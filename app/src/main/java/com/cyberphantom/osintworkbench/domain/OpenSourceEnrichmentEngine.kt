package com.cyberphantom.osintworkbench.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Additional open-knowledge and community indexes.
 *
 * These are independent, best-effort collectors. They only read public search
 * endpoints; they do not sign in, bypass access controls, or attempt to identify
 * a person from a weak name match.
 */
internal class OpenSourceEnrichmentEngine {
    internal data class Outcome(
        val results: List<PublicProfileResult>,
        val warnings: List<String>,
        val attempted: Int
    )

    private data class SourceOutcome(
        val source: String,
        val results: List<PublicProfileResult>,
        val error: String?
    )

    suspend fun search(
        query: String,
        kind: IdentifierKind,
        limit: Int = 60
    ): Outcome = coroutineScope {
        val clean = query.trim()
        if (clean.isBlank()) return@coroutineScope Outcome(emptyList(), listOf("قيمة البحث فارغة."), 0)
        val cap = limit.coerceIn(5, 60)
        val tasks = mutableListOf<kotlinx.coroutines.Deferred<SourceOutcome>>()

        tasks += async { runSource("Wikipedia · English") { wikipedia(clean, "en", minOf(12, cap)) } }
        tasks += async { runSource("Wikipedia · Arabic") { wikipedia(clean, "ar", minOf(12, cap)) } }
        tasks += async { runSource("Lemmy · lemmy.world") { lemmy(clean, "lemmy.world", minOf(15, cap)) } }
        tasks += async { runSource("Lemmy · lemmy.ml") { lemmy(clean, "lemmy.ml", minOf(10, cap)) } }
        tasks += async { runSource("Hacker News / Algolia") { hackerNews(clean, minOf(12, cap)) } }
        tasks += async { runSource("Stack Exchange") { stackExchange(clean, minOf(10, cap)) } }

        if (kind == IdentifierKind.NAME || kind == IdentifierKind.USERNAME) {
            tasks += async { runSource("OpenAlex") { openAlex(clean, minOf(10, cap)) } }
            tasks += async { runSource("Crossref") { crossref(clean, minOf(10, cap)) } }
            tasks += async { runSource("GitHub public users") { PublicResearchEngine().searchGitHubUsers(clean, minOf(10, cap)) } }
            tasks += async { runSource("Wikidata") { PublicResearchEngine().searchWikidata(clean, minOf(10, cap)) } }
        }

        val outcomes = tasks.awaitAll()
        val merged = outcomes.flatMap { it.results }
            .filter { it.url.startsWith("https://") && it.title.isNotBlank() }
            .distinctBy { canonical(it.url) }
        Outcome(
            results = merged,
            warnings = outcomes.filter { it.error != null }.map { "${it.source}: ${it.error}" },
            attempted = outcomes.size
        )
    }

    private suspend fun runSource(
        source: String,
        action: suspend () -> List<PublicProfileResult>
    ): SourceOutcome = try {
        SourceOutcome(source, action(), null)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        SourceOutcome(source, emptyList(), error.message ?: "تعذر الوصول إلى المصدر")
    }

    private suspend fun wikipedia(query: String, language: String, limit: Int): List<PublicProfileResult> =
        withContext(Dispatchers.IO) {
            val endpoint = "https://${language}.wikipedia.org/w/api.php?action=query&list=search&srsearch=${enc(query)}&utf8=1&format=json&srlimit=${limit.coerceIn(1, 20)}"
            val root = JSONObject(getText(endpoint, 7_000, "application/json"))
            val rows = root.optJSONObject("query")?.optJSONArray("search") ?: JSONArray()
            buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val title = row.optString("title").takeIf { it.isNotBlank() } ?: continue
                    val pageId = row.optLong("pageid", -1L)
                    val url = if (pageId > 0) {
                        "https://${language}.wikipedia.org/?curid=$pageId"
                    } else {
                        "https://${language}.wikipedia.org/wiki/${enc(title.replace(' ', '_'))}"
                    }
                    add(
                        PublicProfileResult(
                            provider = "Wikipedia · ${language.uppercase(Locale.ROOT)}",
                            title = title,
                            description = stripHtml(row.optString("snippet")).take(480),
                            url = url,
                            confidence = 72
                        )
                    )
                }
            }
        }

    private suspend fun lemmy(query: String, instance: String, limit: Int): List<PublicProfileResult> =
        withContext(Dispatchers.IO) {
            val endpoint = "https://$instance/api/v3/search?q=${enc(query)}&type_=Posts&sort=TopAll&limit=${limit.coerceIn(1, 20)}"
            val root = JSONObject(getText(endpoint, 7_000, "application/json"))
            parseLemmy(root, instance, limit)
        }

    internal fun parseLemmy(root: JSONObject, instance: String, limit: Int = 20): List<PublicProfileResult> {
        val posts = root.optJSONArray("posts") ?: JSONArray()
        return buildList {
            for (i in 0 until minOf(posts.length(), limit.coerceIn(1, 60))) {
                val wrapper = posts.optJSONObject(i) ?: continue
                val post = wrapper.optJSONObject("post") ?: wrapper
                val id = post.optLong("id", -1L)
                val title = post.optString("name").ifBlank { post.optString("title") }
                if (title.isBlank() || id <= 0L) continue
                val body = post.optString("body")
                val creator = wrapper.optJSONObject("creator")?.optString("name").orEmpty()
                val community = wrapper.optJSONObject("community")?.optString("name").orEmpty()
                val description = buildString {
                    if (creator.isNotBlank()) append("الكاتب: ").append(creator)
                    if (community.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append("المجتمع: ").append(community)
                    }
                    if (body.isNotBlank()) {
                        if (isNotEmpty()) append("\n")
                        append(stripHtml(body))
                    }
                }
                add(
                    PublicProfileResult(
                        provider = "Lemmy · $instance",
                        title = title.take(240),
                        description = description.take(500),
                        url = "https://$instance/post/$id",
                        confidence = 68
                    )
                )
            }
        }
    }

    private suspend fun hackerNews(query: String, limit: Int): List<PublicProfileResult> =
        withContext(Dispatchers.IO) {
            val endpoint = "https://hn.algolia.com/api/v1/search?query=${enc(query)}&tags=story&hitsPerPage=${limit.coerceIn(1, 20)}"
            val root = JSONObject(getText(endpoint, 7_000, "application/json"))
            val hits = root.optJSONArray("hits") ?: JSONArray()
            buildList {
                for (i in 0 until hits.length()) {
                    val item = hits.optJSONObject(i) ?: continue
                    val title = item.optString("title").ifBlank { item.optString("story_title") }
                    if (title.isBlank()) continue
                    val id = item.optString("objectID")
                    val url = item.optString("url").takeIf { it.startsWith("https://") }
                        ?: "https://news.ycombinator.com/item?id=$id"
                    add(
                        PublicProfileResult(
                            provider = "Hacker News · Algolia",
                            title = title.take(240),
                            description = item.optString("author").let { author ->
                                val created = item.optString("created_at")
                                listOfNotNull(author.takeIf { it.isNotBlank() }?.let { "الكاتب: $it" },
                                    created.takeIf { it.isNotBlank() }?.let { "التاريخ: $it" }).joinToString(" · ")
                            },
                            url = url,
                            confidence = 70
                        )
                    )
                }
            }
        }

    private suspend fun stackExchange(query: String, limit: Int): List<PublicProfileResult> =
        withContext(Dispatchers.IO) {
            val endpoint = "https://api.stackexchange.com/2.3/search/advanced?order=desc&sort=relevance&q=${enc(query)}&site=stackoverflow&pagesize=${limit.coerceIn(1, 20)}"
            val root = JSONObject(getText(endpoint, 7_000, "application/json"))
            val rows = root.optJSONArray("items") ?: JSONArray()
            buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val title = stripHtml(row.optString("title"))
                    val url = row.optString("link").takeIf { it.startsWith("https://") } ?: continue
                    if (title.isBlank()) continue
                    add(
                        PublicProfileResult(
                            provider = "Stack Exchange API",
                            title = title.take(240),
                            description = "الإجابات: ${row.optInt("answer_count")} · المشاهدات: ${row.optInt("view_count")}",
                            url = url,
                            confidence = 66
                        )
                    )
                }
            }
        }

    private suspend fun openAlex(query: String, limit: Int): List<PublicProfileResult> =
        withContext(Dispatchers.IO) {
            val endpoint = "https://api.openalex.org/authors?search=${enc(query)}&per-page=${limit.coerceIn(1, 20)}"
            val root = JSONObject(getText(endpoint, 8_000, "application/json"))
            val rows = root.optJSONArray("results") ?: JSONArray()
            buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val name = row.optString("display_name").takeIf { it.isNotBlank() } ?: continue
                    val url = row.optString("id").takeIf { it.startsWith("https://") } ?: continue
                    val institution = row.optJSONArray("last_known_institutions")
                        ?.optJSONObject(0)?.optString("display_name").orEmpty()
                    add(
                        PublicProfileResult(
                            provider = "OpenAlex",
                            title = name,
                            description = buildString {
                                if (institution.isNotBlank()) append(institution).append(" · ")
                                append("الأعمال العلمية: ").append(row.optInt("works_count"))
                            }.take(480),
                            url = url,
                            confidence = 67
                        )
                    )
                }
            }
        }

    private suspend fun crossref(query: String, limit: Int): List<PublicProfileResult> =
        withContext(Dispatchers.IO) {
            val endpoint = "https://api.crossref.org/works?query.author=${enc(query)}&rows=${limit.coerceIn(1, 20)}&select=DOI,title,author,published-print,published-online,URL"
            val root = JSONObject(getText(endpoint, 8_000, "application/json"))
            val rows = root.optJSONObject("message")?.optJSONArray("items") ?: JSONArray()
            buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val title = row.optJSONArray("title")?.optString(0).orEmpty().takeIf { it.isNotBlank() } ?: continue
                    val url = row.optString("URL").takeIf { it.startsWith("https://") }
                        ?: row.optString("DOI").takeIf { it.isNotBlank() }?.let { "https://doi.org/$it" }
                        ?: continue
                    val authors = row.optJSONArray("author") ?: JSONArray()
                    val authorNames = buildList {
                        for (authorIndex in 0 until minOf(authors.length(), 3)) {
                            val author = authors.optJSONObject(authorIndex) ?: continue
                            add(listOf(author.optString("given"), author.optString("family")).filter { it.isNotBlank() }.joinToString(" "))
                        }
                    }.joinToString(", ")
                    add(
                        PublicProfileResult(
                            provider = "Crossref",
                            title = title.take(240),
                            description = authorNames.ifBlank { "سجل منشور في فهرس Crossref" }.take(480),
                            url = url,
                            confidence = 64
                        )
                    )
                }
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
            setRequestProperty("User-Agent", "OSINTWorkbench/0.7.0 (public-source discovery)")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                var total = 0
                while (total < 850_000) {
                    val count = input.read(buffer, 0, minOf(buffer.size, 850_000 - total))
                    if (count <= 0) break
                    output.write(buffer, 0, count)
                    total += count
                }
                output.toString(StandardCharsets.UTF_8.name())
            }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("HTTP $status من ${URL(endpoint).host}")
            }
            if (body.isBlank()) throw IllegalStateException("استجابة فارغة من ${URL(endpoint).host}")
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun stripHtml(value: String): String = value
        .replace(Regex("(?is)<[^>]+>"), " ")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun canonical(raw: String): String = runCatching {
        val url = URL(raw)
        url.protocol.lowercase(Locale.ROOT) + "://" + url.host.lowercase(Locale.ROOT) +
            url.path.trimEnd('/') + if (url.query.isNullOrBlank()) "" else "?" + url.query
    }.getOrDefault(raw.trim())

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
