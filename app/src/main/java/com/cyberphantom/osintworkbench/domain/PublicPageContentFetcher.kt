package com.cyberphantom.osintworkbench.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.Locale

/**
 * Loads text from a user-visible public HTTPS page only.
 * It does not sign in, defeat paywalls, or execute page scripts.
 */
class PublicPageContentFetcher {
    suspend fun fetch(rawUrl: String): String = withContext(Dispatchers.IO) {
        var current = validatePublicUrl(rawUrl)
        repeat(MAX_REDIRECTS + 1) { redirectIndex ->
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,text/plain;q=0.8")
                setRequestProperty("Accept-Language", "ar,en;q=0.8")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                val status = connection.responseCode
                if (status in REDIRECT_CODES) {
                    if (redirectIndex >= MAX_REDIRECTS) return@withContext ""
                    val location = connection.getHeaderField("Location") ?: return@withContext ""
                    current = validatePublicUrl(URL(URL(current), location).toString())
                    return@repeat
                }
                if (status !in 200..299) return@withContext ""
                val contentType = connection.contentType.orEmpty().lowercase(Locale.ROOT)
                if (!contentType.contains("html") && !contentType.contains("text/plain")) return@withContext ""
                val stream = connection.inputStream
                val bytes = stream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (total < MAX_BYTES) {
                        val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES - total))
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                        total += count
                    }
                    output.toByteArray()
                }
                return@withContext extractReadableText(String(bytes, Charsets.UTF_8))
            } finally {
                connection.disconnect()
            }
        }
        ""
    }

    internal fun extractReadableText(html: String): String {
        if (html.isBlank()) return ""
        val document = Jsoup.parse(html)
        document.select("script,style,noscript,svg,canvas,iframe,nav,footer,header,aside,form,button,[aria-hidden=true]").remove()
        val candidates = document.select("article,main,[role=main],.article-body,.article-content,.post-content,.entry-content")
        val best = candidates.maxByOrNull { it.text().length }
        val body = best ?: document.body()
        return body.text().replace(Regex("\\s+"), " ").trim().take(MAX_TEXT_CHARS)
    }

    private fun validatePublicUrl(raw: String): String {
        val url = URL(raw.trim())
        require(url.protocol.equals("https", true)) { "Only public HTTPS pages are supported." }
        val host = url.host.lowercase(Locale.ROOT).removePrefix("www.")
        require(host.isNotBlank() && host !in BLOCKED_HOSTS && !host.endsWith(".local")) {
            "This destination is not a public website."
        }
        val address = runCatching { InetAddress.getByName(host) }.getOrNull()
        if (address != null) {
            require(!address.isAnyLocalAddress && !address.isLoopbackAddress &&
                !address.isLinkLocalAddress && !address.isSiteLocalAddress &&
                !address.isMulticastAddress) {
                "Private or local network destinations are not allowed."
            }
        }
        return url.toString()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 7_000
        private const val READ_TIMEOUT_MS = 9_000
        private const val MAX_REDIRECTS = 3
        private const val MAX_BYTES = 1_500_000
        private const val MAX_TEXT_CHARS = 60_000
        private const val USER_AGENT = "OSINT-Workbench/0.10 (public-page text preview)"
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val BLOCKED_HOSTS = setOf("localhost", "127.0.0.1", "::1", "0.0.0.0")
    }
}
