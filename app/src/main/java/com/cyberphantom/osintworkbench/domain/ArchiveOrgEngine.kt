package com.cyberphantom.osintworkbench.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Public Archive.org catalogue search (texts, audio, video, images, collections).
 * This is the Archive.org item index, not the Wayback web-snapshot index.
 */
class ArchiveOrgEngine {
    suspend fun search(rawQuery: String, rows: Int = 20): List<ReconRecord> = withContext(Dispatchers.IO) {
        val query = rawQuery.trim()
        require(query.isNotBlank()) { "أدخل قيمة للبحث في كتالوغ الأرشيف." }
        val limit = rows.coerceIn(1, 40)
        val q = URLEncoder.encode("($query)", StandardCharsets.UTF_8.name())
        val endpoint = "https://archive.org/advancedsearch.php?q=$q" +
            "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=mediatype&fl%5B%5D=date" +
            "&rows=$limit&page=1&output=json"
        val root = JSONObject(getText(endpoint))
        val docs = root.optJSONObject("response")?.optJSONArray("docs") ?: return@withContext emptyList()
        buildList {
            for (index in 0 until docs.length()) {
                val doc = docs.optJSONObject(index) ?: continue
                val identifier = doc.optString("identifier").takeIf(String::isNotBlank) ?: continue
                val title = doc.optString("title").ifBlank { identifier }
                val mediaType = doc.optString("mediatype").ifBlank { "unknown" }
                val date = doc.optString("date").take(10)
                add(
                    ReconRecord(
                        kind = "ARCHIVE_ORG_ITEM",
                        value = title.take(220),
                        source = "Archive.org catalog",
                        sourceUrl = "https://archive.org/details/${URLEncoder.encode(identifier, StandardCharsets.UTF_8.name())}",
                        confidence = 74,
                        detail = "نوع المادة: $mediaType · التاريخ: ${date.ifBlank { "غير مذكور" }} · المعرّف: $identifier. " +
                            "عنصر كتالوغ عام؛ راجع الصفحة والترخيص قبل الاعتماد أو إعادة الاستخدام."
                    )
                )
            }
        }
    }

    private fun getText(endpoint: String): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 9_000
            readTimeout = 12_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "OSINTWorkbench/0.6 (public archive catalogue)")
        }
        try {
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("HTTP $status من archive.org")
            if (body.isBlank()) throw IllegalStateException("استجابة فارغة من archive.org")
            return body
        } finally {
            connection.disconnect()
        }
    }
}
