package com.cyberphantom.osintworkbench.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Email authentication posture from public DNS only: SPF, DMARC and a small DKIM selector probe.
 * A missing record may mean the domain does not use that mechanism, or that the resolver is
 * filtered; both are reported separately from a negative security conclusion.
 */
class EmailPostureEngine {
    data class Posture(
        val domain: String,
        val spf: SpfInfo?,
        val dmarc: DmarcInfo?,
        val dkimSelectors: List<String>,
        val records: List<ReconRecord>,
        val warnings: List<String>
    )

    data class SpfInfo(
        val raw: String,
        val qualifier: String,
        val lookupCount: Int,
        val includes: List<String>
    )

    data class DmarcInfo(
        val raw: String,
        val policy: String,
        val subdomainPolicy: String?,
        val reporting: Boolean,
        val percentage: Int?
    )

    suspend fun inspect(rawDomain: String): Posture = withContext(Dispatchers.IO) {
        val domain = DomainTools.normalizeHost(rawDomain)
        require(DomainTools.isValidDomain(domain)) { "أدخل نطاقاً صالحاً لفحص البريد." }

        val warnings = mutableListOf<String>()
        val records = mutableListOf<ReconRecord>()

        val spfTxt = runCatching { txt(domain) }
            .onFailure { warnings += "SPF: ${it.message ?: "تعذر جلب سجلات TXT"}" }
            .getOrDefault(emptyList())
        val spf = spfTxt.firstOrNull { it.startsWith("v=spf1", ignoreCase = true) }?.let(::parseSpf)
        if (spf != null) {
            records += ReconRecord(
                kind = "EMAIL_SPF",
                value = "all:${spf.qualifier} · lookups:${spf.lookupCount}",
                source = "Public DNS TXT",
                sourceUrl = "https://dns.google/resolve?name=${encode(domain)}&type=TXT",
                confidence = 90,
                detail = "سياسة SPF معلنة: ${spf.raw.take(240)}"
            )
            if (spf.lookupCount > 10) {
                records += ReconRecord(
                    kind = "EMAIL_SPF_RISK",
                    value = "عدد استعلامات SPF يتجاوز 10 (${spf.lookupCount})",
                    source = "SPF analysis",
                    sourceUrl = "https://dns.google/resolve?name=${encode(domain)}&type=TXT",
                    confidence = 72,
                    detail = "قد يُرفض البريد أو يُصنّف كغير موثوق لأن SPF يتجاوز حدّ الاستعلامات الموصى به."
                )
            }
            if (spf.qualifier == "+") {
                records += ReconRecord(
                    kind = "EMAIL_SPF_PERMISSIVE",
                    value = "+all يسمح بأي مرسل",
                    source = "SPF analysis",
                    sourceUrl = "https://dns.google/resolve?name=${encode(domain)}&type=TXT",
                    confidence = 70,
                    detail = "سياسة SPF تسمح لأي خادم بالإرسال نيابة عن النطاق؛ راجعها إن لم يكن ذلك مقصوداً."
                )
            }
        } else {
            records += ReconRecord(
                kind = "EMAIL_SPF_ABSENT",
                value = "لم يُعلن سجل SPF في TXT",
                source = "Public DNS TXT",
                sourceUrl = "https://dns.google/resolve?name=${encode(domain)}&type=TXT",
                confidence = 45,
                detail = "غياب SPF من هذه الاستجابة لا يثبت أن النطاق لا يرسل بريداً؛ قد تُستخدم سياسة أعلى نطاق أو مُصفّى DNS."
            )
        }

        val dmarcTxt = runCatching { txt("_dmarc.$domain") }
            .onFailure { warnings += "DMARC: ${it.message ?: "تعذر جلب سجل DMARC"}" }
            .getOrDefault(emptyList())
        val dmarc = dmarcTxt.firstOrNull { it.startsWith("v=DMARC1", ignoreCase = true) }?.let(::parseDmarc)
        if (dmarc != null) {
            records += ReconRecord(
                kind = "EMAIL_DMARC",
                value = "p=${dmarc.policy} · rua=${if (dmarc.reporting) "yes" else "no"}",
                source = "Public DNS TXT",
                sourceUrl = "https://dns.google/resolve?name=${encode("_dmarc.$domain")}&type=TXT",
                confidence = 92,
                detail = "سياسة DMARC معلنة: ${dmarc.raw.take(240)}"
            )
            if (dmarc.policy == "none") {
                records += ReconRecord(
                    kind = "EMAIL_DMARC_MONITOR_ONLY",
                    value = "p=none (مراقبة فقط)",
                    source = "DMARC analysis",
                    sourceUrl = "https://dns.google/resolve?name=${encode("_dmarc.$domain")}&type=TXT",
                    confidence = 75,
                    detail = "السياسة لا تمنع انتحال النطاق؛ قد يكون ذلك مرحلة انتقالية مقصودة."
                )
            }
        } else {
            records += ReconRecord(
                kind = "EMAIL_DMARC_ABSENT",
                value = "لم يُعلن سجل DMARC",
                source = "Public DNS TXT",
                sourceUrl = "https://dns.google/resolve?name=${encode("_dmarc.$domain")}&type=TXT",
                confidence = 45,
                detail = "غياب DMARC يعني عادةً عدم وجود حماية معلنة من انتحال النطاق، مع احتمال سياسة عند نطاق أعلى."
            )
        }

        val selectors = listOf("default", "google", "selector1", "selector2", "k1", "s1", "mail", "dkim")
        val found = mutableListOf<String>()
        for (selector in selectors) {
            val name = "$selector._domainkey.$domain"
            val txt = runCatching { txt(name) }.getOrDefault(emptyList())
            if (txt.any { it.contains("v=DKIM1", ignoreCase = true) || it.contains("k=rsa", ignoreCase = true) }) {
                found += selector
                records += ReconRecord(
                    kind = "EMAIL_DKIM_SELECTOR",
                    value = selector,
                    source = "Public DNS TXT",
                    sourceUrl = "https://dns.google/resolve?name=${encode(name)}&type=TXT",
                    confidence = 88,
                    detail = "محدد DKIM عام موجود. لا يُستخرج المفتاح الخاص ولا يثبت أن كل الرسائل موقّعة."
                )
            }
        }
        if (found.isEmpty()) {
            records += ReconRecord(
                kind = "EMAIL_DKIM_NOT_OBSERVED",
                value = "لم يُرصد أي من المحددات الشائعة الثمانية",
                source = "DKIM selector probe",
                sourceUrl = "https://dns.google/resolve?name=${encode(domain)}&type=TXT",
                confidence = 40,
                detail = "النطاق قد يستخدم محدداً مخصصاً؛ عدم رصد المحددات الشائعة لا يثبت غياب DKIM."
            )
        }

        records += ReconRecord(
            kind = "EMAIL_POSTURE_SUMMARY",
            value = "SPF=${if (spf != null) "yes" else "no"} · DMARC=${if (dmarc != null) dmarc.policy else "no"} · DKIM=${found.size}",
            source = "Email posture engine",
            sourceUrl = "https://dns.google/resolve?name=${encode(domain)}&type=TXT",
            confidence = 86,
            detail = "ملخص سياسات البريد العامة${if (spfTxt.isEmpty()) "؛ تعذر جزء من استعلامات DNS" else ""}. راجع التحذيرات قبل أي استنتاج."
        )

        Posture(domain, spf, dmarc, found, records, warnings.distinct())
    }

    internal fun parseSpf(raw: String): SpfInfo {
        val tokens = raw.split(Regex("\\s+")).filter(String::isNotBlank)
        val all = tokens.firstOrNull { it.endsWith("all", ignoreCase = true) }
        val qualifier = when {
            all == null -> "?"
            all.startsWith("-") -> "-"
            all.startsWith("~") -> "~"
            all.startsWith("?") -> "?"
            all.startsWith("+") -> "+"
            else -> "?"
        }
        val lookupPrefixes = listOf("include:", "a", "mx", "ptr", "exists:", "redirect=")
        val lookups = tokens.count { token -> lookupPrefixes.any { token.startsWith(it, ignoreCase = true) } }
        val includes = tokens.filter { it.startsWith("include:", ignoreCase = true) }
            .map { it.substringAfter(':').trim() }
            .filter(String::isNotBlank)
            .take(12)
        return SpfInfo(raw, qualifier, lookups, includes)
    }

    internal fun parseDmarc(raw: String): DmarcInfo {
        val tags = raw.split(';')
            .mapNotNull { part ->
                val pieces = part.trim().split('=', limit = 2)
                if (pieces.size == 2) pieces[0].trim().lowercase() to pieces[1].trim() else null
            }
            .toMap()
        return DmarcInfo(
            raw = raw,
            policy = tags["p"]?.lowercase() ?: "none",
            subdomainPolicy = tags["sp"]?.lowercase(),
            reporting = !tags["rua"].isNullOrBlank() || !tags["ruf"].isNullOrBlank(),
            percentage = tags["pct"]?.toIntOrNull()
        )
    }

    private fun txt(name: String): List<String> {
        val endpoint = "https://cloudflare-dns.com/dns-query?name=${encode(name)}&type=TXT"
        val body = getJson(endpoint)
        val root = JSONObject(body)
        val answers = root.optJSONArray("Answer") ?: return emptyList()
        return buildList {
            for (index in 0 until answers.length()) {
                val answer = answers.optJSONObject(index) ?: continue
                val data = answer.optString("data").trim().trim('"').replace("\" \"", "")
                if (data.isNotBlank()) add(data)
            }
        }
    }

    private fun getJson(endpoint: String): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 7_000
            useCaches = false
            setRequestProperty("Accept", "application/dns-json")
            setRequestProperty("User-Agent", "OSINTWorkbench/0.6 (public email posture)")
        }
        try {
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("HTTP $status من مزود DNS")
            if (body.isBlank()) throw IllegalStateException("استجابة DNS فارغة")
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
