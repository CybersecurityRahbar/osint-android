package com.cyberphantom.osintworkbench.domain

/**
 * Dork catalogue derived from the ALKASER reference engine, re-scoped to operators and indexes
 * that are reachable without authentication. Every template receives a quoted query.
 */
enum class DorkCategory(val label: String) {
    SOCIAL("منصات"),
    CODE("شيفرة"),
    PASTE("لصق"),
    DOCS("مستندات"),
    LEAKS("تسريبات"),
    CONFIGS("إعدادات"),
    ARCHIVE("أرشيف"),
    PEOPLE("أشخاص"),
    FORUM("منتديات")
}

object DorkCatalogue {
    private val templates: Map<DorkCategory, List<String>> = mapOf(
        DorkCategory.SOCIAL to listOf(
            "site:reddit.com \"{q}\"",
            "site:x.com \"{q}\"",
            "site:twitter.com \"{q}\"",
            "site:facebook.com \"{q}\"",
            "site:instagram.com \"{q}\"",
            "site:tiktok.com \"{q}\"",
            "site:linkedin.com \"{q}\"",
            "site:youtube.com \"{q}\"",
            "site:medium.com \"{q}\"",
            "site:t.me \"{q}\"",
            "site:threads.net \"{q}\"",
            "site:bsky.app \"{q}\""
        ),
        DorkCategory.CODE to listOf(
            "site:github.com \"{q}\"",
            "site:gitlab.com \"{q}\"",
            "site:gist.github.com \"{q}\"",
            "site:stackoverflow.com \"{q}\"",
            "site:codepen.io \"{q}\"",
            "site:replit.com \"{q}\"",
            "site:huggingface.co \"{q}\"",
            "site:dev.to \"{q}\""
        ),
        DorkCategory.PASTE to listOf(
            "site:pastebin.com \"{q}\"",
            "site:rentry.co \"{q}\"",
            "site:justpaste.it \"{q}\"",
            "site:controlc.com \"{q}\"",
            "site:ghostbin.site \"{q}\"",
            "site:hastebin.com \"{q}\""
        ),
        DorkCategory.DOCS to listOf(
            "\"{q}\" filetype:pdf",
            "\"{q}\" (filetype:doc OR filetype:docx)",
            "\"{q}\" (filetype:xls OR filetype:xlsx)",
            "\"{q}\" (filetype:ppt OR filetype:pptx)",
            "\"{q}\" filetype:csv",
            "\"{q}\" filetype:json",
            "\"{q}\" filetype:sql"
        ),
        DorkCategory.LEAKS to listOf(
            "\"{q}\" intext:password",
            "\"{q}\" intext:passwd",
            "\"{q}\" intext:breach",
            "\"{q}\" intext:credentials",
            "\"{q}\" intext:\"api_key\"",
            "\"{q}\" \"تسريب\"",
            "\"{q}\" \"اختراق\""
        ),
        DorkCategory.CONFIGS to listOf(
            "\"{q}\" intitle:\"index of\"",
            "\"{q}\" inurl:wp-config.php",
            "\"{q}\" inurl:.env",
            "\"{q}\" filetype:env",
            "\"{q}\" filetype:yml",
            "\"{q}\" filetype:ini"
        ),
        DorkCategory.ARCHIVE to listOf(
            "site:web.archive.org \"{q}\"",
            "site:archive.org \"{q}\"",
            "site:archive.ph \"{q}\""
        ),
        DorkCategory.PEOPLE to listOf(
            "\"{q}\" (\"cv\" OR \"resume\")",
            "\"{q}\" (\"email\" OR \"contact\")",
            "\"{q}\" \"profile\"",
            "\"{q}\" \"bio\"",
            "\"{q}\" site:academia.edu"
        ),
        DorkCategory.FORUM to listOf(
            "site:quora.com \"{q}\"",
            "site:stackexchange.com \"{q}\"",
            "site:news.ycombinator.com \"{q}\"",
            "site:disqus.com \"{q}\""
        )
    )

    fun categories(): List<DorkCategory> = DorkCategory.entries

    fun templatesFor(categories: List<DorkCategory> = DorkCategory.entries): List<String> =
        categories.flatMap { templates[it].orEmpty() }

    /** Expands the catalogue into concrete queries, de-duplicated and bounded. */
    fun expand(
        query: String,
        categories: List<DorkCategory> = DorkCategory.entries,
        max: Int = 12
    ): List<String> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val quoted = if (q.startsWith("\"") && q.endsWith("\"")) q else "\"$q\""
        return templatesFor(categories)
            .map { it.replace("{q}", quoted.removeSurrounding("\"")) }
            .distinct()
            .take(max.coerceIn(1, 40))
    }

    /** Curated set that fits an identifier type without over-querying a single search round. */
    fun forKind(kind: IdentifierKind, max: Int = 3): List<String> = when (kind) {
        IdentifierKind.NAME -> expand("placeholder", listOf(DorkCategory.PEOPLE), max)
        IdentifierKind.USERNAME -> expand("placeholder", listOf(DorkCategory.SOCIAL, DorkCategory.CODE), max)
        IdentifierKind.PHONE -> expand("placeholder", listOf(DorkCategory.SOCIAL, DorkCategory.FORUM), max)
        IdentifierKind.EMAIL -> expand("placeholder", listOf(DorkCategory.LEAKS, DorkCategory.DOCS), max)
    }
}
