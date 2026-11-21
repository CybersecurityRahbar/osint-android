package com.cyberphantom.osintworkbench.domain

import java.util.Locale

data class IdentifierHit(val value: String, val count: Int)

data class FusionAlert(
    val severity: String,
    val keyword: String,
    val title: String,
    val url: String,
    val provider: String
)

/**
 * Deterministic offline fusion over one result set. It extracts entity-grade identifiers,
 * surfaces watchlist keywords (leak/breach/password in any language we index) and produces a
 * transparent confidence score. It never asserts identity or ownership: every note says so.
 */
data class FusionProfile(
    val resultCount: Int,
    val providerCount: Int,
    val hostCount: Int,
    val exactMatches: Int,
    val confidence: Int,
    val verdict: String,
    val emails: List<IdentifierHit>,
    val phones: List<IdentifierHit>,
    val handles: List<IdentifierHit>,
    val domains: List<IdentifierHit>,
    val ips: List<IdentifierHit>,
    val wallets: List<IdentifierHit>,
    val alerts: List<FusionAlert>,
    val topCountries: List<Pair<String, Int>>,
    val notes: List<String>
) {
    val identifierTotal: Int
        get() = emails.size + phones.size + handles.size + domains.size + ips.size + wallets.size
}

object IntelFusion {
    private val emailRe = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
    private val phoneRe = Regex("\\+?\\d[\\d\\s().\\-]{6,}\\d")
    private val handleRe = Regex("(?<![A-Za-z0-9_])@([A-Za-z0-9_]{2,32})")
    private val domainRe = Regex("\\b([a-z0-9][a-z0-9\\-]{0,62}\\.(?:[a-z]{2,12}))\\b", RegexOption.IGNORE_CASE)
    private val ipRe = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
    private val btcRe = Regex("\\b(?:bc1[a-z0-9]{20,80}|[13][a-km-zA-HJ-NP-Z1-9]{25,34})\\b")
    private val ethRe = Regex("\\b0x[a-fA-F0-9]{40}\\b")

    private val blockedHosts = setOf(
        "duckduckgo.com", "bing.com", "google.com", "brave.com", "mojeek.com", "yahoo.com",
        "startpage.com", "ecosia.org", "yandex.com", "qwant.com", "searx.be", "disroot.org",
        "w3.org", "schema.org", "googleapis.com", "gstatic.com", "example.com", "example.org",
        "example.net", "facebook.com", "instagram.com", "tiktok.com", "x.com", "twitter.com",
        "linkedin.com", "youtube.com", "reddit.com", "github.com", "gitlab.com"
    )

    private val leakKeywords = listOf(
        "leak", "breach", "dump", "password", "passwd", "credential", "combolist",
        "تسريب", "اختراق", "كلمة المرور", "بيانات مسربة"
    )

    private val countryHints: List<Pair<String, List<String>>> = listOf(
        "اليمن" to listOf("yemen", "sanaa", "aden", "اليمن", "صنعاء", "عدن", "تعز", "حضرموت"),
        "السعودية" to listOf("saudi", "riyadh", "jeddah", "السعودية", "الرياض", "جدة", "مكة", "الدمام"),
        "الإمارات" to listOf("uae", "dubai", "abu dhabi", "الإمارات", "دبي", "أبوظبي", "الشارقة"),
        "مصر" to listOf("egypt", "cairo", "alexandria", "مصر", "القاهرة", "الإسكندرية"),
        "العراق" to listOf("iraq", "baghdad", "basra", "العراق", "بغداد", "البصرة", "الموصل"),
        "الأردن" to listOf("jordan", "amman", "الأردن", "عمّان", "عمان"),
        "الكويت" to listOf("kuwait", "الكويت"),
        "قطر" to listOf("qatar", "doha", "قطر", "الدوحة"),
        "البحرين" to listOf("bahrain", "manama", "البحرين", "المنامة"),
        "عُمان" to listOf("oman", "muscat", "عمان", "مسقط"),
        "سوريا" to listOf("syria", "damascus", "aleppo", "سوريا", "دمشق", "حلب"),
        "لبنان" to listOf("lebanon", "beirut", "لبنان", "بيروت"),
        "فلسطين" to listOf("palestine", "gaza", "ramallah", "فلسطين", "غزة", "رام الله"),
        "السودان" to listOf("sudan", "khartoum", "السودان", "الخرطوم"),
        "ليبيا" to listOf("libya", "tripoli", "ليبيا", "طرابلس"),
        "المغرب" to listOf("morocco", "rabat", "casablanca", "المغرب", "الرباط", "الدار البيضاء"),
        "الجزائر" to listOf("algeria", "algiers", "الجزائر"),
        "تونس" to listOf("tunisia", "tunis", "تونس"),
        "تركيا" to listOf("turkey", "istanbul", "ankara", "تركيا", "اسطنبول", "أنقرة"),
        "الولايات المتحدة" to listOf("united states", "usa", "new york", "california", "texas"),
        "بريطانيا" to listOf("united kingdom", "london", "england", "britain"),
        "ألمانيا" to listOf("germany", "berlin", "ألمانيا"),
        "فرنسا" to listOf("france", "paris", "فرنسا"),
        "روسيا" to listOf("russia", "moscow", "روسيا"),
        "الهند" to listOf("india", "mumbai", "delhi"),
        "باكستان" to listOf("pakistan", "karachi", "lahore")
    )

    fun analyze(
        query: String,
        kind: IdentifierKind,
        results: List<PublicProfileResult>
    ): FusionProfile {
        val emails = mutableMapOf<String, Int>()
        val phones = mutableMapOf<String, Int>()
        val handles = mutableMapOf<String, Int>()
        val domains = mutableMapOf<String, Int>()
        val ips = mutableMapOf<String, Int>()
        val wallets = mutableMapOf<String, Int>()
        val alerts = mutableListOf<FusionAlert>()
        val countries = mutableMapOf<String, Int>()

        results.forEach { result ->
            val text = listOf(result.title, result.description, result.url).joinToString(" \n ")
            emailRe.findAll(text).forEach { match ->
                emails[match.value.lowercase(Locale.ROOT)] = (emails[match.value.lowercase(Locale.ROOT)] ?: 0) + 1
            }
            phoneRe.findAll(text).forEach { match ->
                val digits = match.value.filter(Char::isDigit)
                if (digits.length in 7..15) phones["+$digits"] = (phones["+$digits"] ?: 0) + 1
            }
            handleRe.findAll(text).forEach { match ->
                val handle = match.groupValues[1].lowercase(Locale.ROOT)
                handles[handle] = (handles[handle] ?: 0) + 1
            }
            domainRe.findAll(text).forEach { match ->
                val host = match.value.lowercase(Locale.ROOT)
                if (blockedHosts.none { host == it || host.endsWith(".$it") } && !host.endsWith(".jpg")) {
                    domains[host] = (domains[host] ?: 0) + 1
                }
            }
            ipRe.findAll(text).forEach { match ->
                if (isPlausibleIpv4(match.value)) ips[match.value] = (ips[match.value] ?: 0) + 1
            }
            btcRe.findAll(text).forEach { match ->
                wallets["btc:${match.value}"] = (wallets["btc:${match.value}"] ?: 0) + 1
            }
            ethRe.findAll(text).forEach { match ->
                val value = match.value.lowercase(Locale.ROOT)
                wallets["eth:$value"] = (wallets["eth:$value"] ?: 0) + 1
            }

            val lower = text.lowercase(Locale.ROOT)
            val hitKeywords = leakKeywords.filter { lower.contains(it) }
            if (hitKeywords.isNotEmpty()) {
                alerts += FusionAlert(
                    severity = if (hitKeywords.size >= 2) "high" else "medium",
                    keyword = hitKeywords.joinToString(", "),
                    title = result.title.take(160),
                    url = result.url,
                    provider = result.provider
                )
            }
            countryHints.forEach { (country, keywords) ->
                if (keywords.any { lower.contains(it) }) {
                    countries[country] = (countries[country] ?: 0) + 1
                }
            }
        }

        val providers = results.map { it.provider.substringBefore(" · ") }.distinct().size
        val hosts = results.mapNotNull { hostOf(it.url) }.distinct().size
        val exact = results.count { it.relevanceScore >= 55 }
        val identifierCount = emails.size + phones.size + handles.size

        val base = minOf(38, results.size * 2)
        val diversity = minOf(24, providers * 4)
        val exactBoost = minOf(18, exact * 3)
        val identifierBoost = minOf(14, identifierCount * 2)
        val score = (base + diversity + exactBoost + identifierBoost).coerceIn(0, 96)
        val verdict = when {
            score >= 70 -> "HIGH"
            score >= 45 -> "MEDIUM"
            else -> "LOW"
        }

        val notes = buildList {
            if (results.isEmpty()) add("لا توجد نتائج مباشرة؛ الدرجة صفر لا تعني غياب المعلومات، بل تعذر الوصول إلى الفهارس.")
            if (providers <= 1) add("النتائج من مصدر واحد أو أقل؛ لا يمكن اعتبارها متقاطعة.")
            if (alerts.isNotEmpty()) add("كلمات التسريب/الاختراق ظاهرة في نص النتيجة فقط، وليست إثباتاً لاختراق أو ملكية.")
            if (kind == IdentifierKind.PHONE) add("الرقم قد يظهر في صفحات عامة لا علاقة لها بمالكه؛ تحقق يدوياً قبل أي استنتاج.")
            add("الدرجة إرشادية مبنية على عدد النتائج وتنوع المصادر والتطابق النصي، وليست احتمالاً إحصائياً.")
        }

        return FusionProfile(
            resultCount = results.size,
            providerCount = providers,
            hostCount = hosts,
            exactMatches = exact,
            confidence = score,
            verdict = verdict,
            emails = top(emails),
            phones = top(phones),
            handles = top(handles),
            domains = top(domains),
            ips = top(ips),
            wallets = top(wallets),
            alerts = alerts.take(12),
            topCountries = countries.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value },
            notes = notes
        )
    }

    private fun top(values: Map<String, Int>): List<IdentifierHit> =
        values.entries.sortedByDescending { it.value }.take(12).map { IdentifierHit(it.key, it.value) }

    private fun hostOf(url: String): String? = runCatching {
        java.net.URL(url).host.lowercase(Locale.ROOT).removePrefix("www.")
    }.getOrNull()

    internal fun isPlausibleIpv4(raw: String): Boolean {
        val parts = raw.split('.')
        if (parts.size != 4) return false
        if (parts.any { it.length > 3 || it.isEmpty() }) return false
        return parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
    }
}
