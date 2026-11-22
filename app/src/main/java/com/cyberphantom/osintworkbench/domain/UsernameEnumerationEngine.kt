package com.cyberphantom.osintworkbench.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

enum class ProbeKind { GITHUB, GITLAB, CODEBERG, REDDIT, TELEGRAM, HACKERNEWS, NPM, DOCKERHUB, KEYBASE, MASTODON, BLUESKY, STEAM, HTML }

enum class ProbeStatus(val label: String) {
    FOUND("موجود"),
    NOT_FOUND("غير موجود"),
    BLOCKED("محجوب/محدود"),
    UNKNOWN("غير مؤكد"),
    ERROR("خطأ")
}

data class PlatformProbe(
    val name: String,
    val category: String,
    val urlTemplate: String,
    val kind: ProbeKind,
    val positiveMarker: String = "",
    val negativeMarkers: List<String> = emptyList()
)

data class UsernameHit(
    val platform: String,
    val category: String,
    val url: String,
    val status: ProbeStatus,
    val confidence: Int,
    val detail: String
) {
    val found: Boolean get() = status == ProbeStatus.FOUND
}

/**
 * Existence check for one username across public platform endpoints, using explicit
 * positive/negative signatures instead of guessing from HTTP 200 alone. Blocked or
 * ambiguous responses are reported as such and never counted as a confirmed account.
 */
class UsernameEnumerationEngine {
    val platforms: List<PlatformProbe> = listOf(
        PlatformProbe("GitHub", "شيفرة", "https://api.github.com/users/%s", ProbeKind.GITHUB),
        PlatformProbe("GitLab", "شيفرة", "https://gitlab.com/api/v4/users?username=%s", ProbeKind.GITLAB),
        PlatformProbe("Codeberg", "شيفرة", "https://codeberg.org/api/v1/users/%s", ProbeKind.CODEBERG),
        PlatformProbe("Reddit", "مجتمع", "https://www.reddit.com/user/%s/about.json", ProbeKind.REDDIT),
        PlatformProbe("Telegram", "مراسلة", "https://t.me/%s", ProbeKind.TELEGRAM),
        PlatformProbe("Hacker News", "مجتمع", "https://hacker-news.firebaseio.com/v0/user/%s.json", ProbeKind.HACKERNEWS),
        PlatformProbe("npm", "حزم", "https://registry.npmjs.org/-/user/org.couchdb.user:%s", ProbeKind.NPM),
        PlatformProbe("Docker Hub", "حزم", "https://hub.docker.com/v2/users/%s/", ProbeKind.DOCKERHUB),
        PlatformProbe("Keybase", "هوية", "https://keybase.io/_/api/1.0/user/lookup.json?username=%s", ProbeKind.KEYBASE),
        PlatformProbe("Mastodon", "مجتمع", "https://mastodon.social/api/v1/accounts/lookup?acct=%s", ProbeKind.MASTODON),
        PlatformProbe("Bluesky", "مجتمع", "https://public.api.bsky.app/xrpc/com.atproto.identity.resolveHandle?handle=%s", ProbeKind.BLUESKY),
        PlatformProbe("Steam", "ألعاب", "https://steamcommunity.com/id/%s/?xml=1", ProbeKind.STEAM),
        PlatformProbe("YouTube", "فيديو", "https://www.youtube.com/@%s", ProbeKind.HTML,
            positiveMarker = "\"channelId\"",
            negativeMarkers = listOf("This page isn&#39;t available", "This page isn't available")),
        PlatformProbe("Twitch", "بث", "https://www.twitch.tv/%s", ProbeKind.HTML,
            negativeMarkers = listOf("Sorry. Unless you")),
        PlatformProbe("SoundCloud", "صوت", "https://soundcloud.com/%s", ProbeKind.HTML,
            negativeMarkers = listOf("We can&#39;t find that user", "We can't find that user")),
        PlatformProbe("Spotify", "صوت", "https://open.spotify.com/user/%s", ProbeKind.HTML,
            negativeMarkers = listOf("Page not found", "Couldn&#39;t find that")),
        PlatformProbe("Behance", "تصميم", "https://www.behance.net/%s", ProbeKind.HTML,
            negativeMarkers = listOf("Page Not Found", "Oops! We can&#39;t find that page")),
        PlatformProbe("Dribbble", "تصميم", "https://dribbble.com/%s", ProbeKind.HTML,
            negativeMarkers = listOf("Page Not Found")),
        PlatformProbe("Flickr", "صور", "https://www.flickr.com/people/%s", ProbeKind.HTML,
            negativeMarkers = listOf("Page Not Found")),
        PlatformProbe("Kaggle", "بيانات", "https://www.kaggle.com/%s", ProbeKind.HTML,
            negativeMarkers = listOf("Page Not Found", "404")),
        PlatformProbe("CodePen", "شيفرة", "https://codepen.io/%s", ProbeKind.HTML,
            negativeMarkers = listOf("Page not found")),
        PlatformProbe("Replit", "شيفرة", "https://replit.com/@%s", ProbeKind.HTML,
            negativeMarkers = listOf("Page not found", "404")),
        PlatformProbe("Pinterest", "صور", "https://www.pinterest.com/%s/", ProbeKind.HTML,
            negativeMarkers = listOf("Page not found", "Couldn&#39;t find")),
        PlatformProbe("VK", "مجتمع", "https://vk.com/%s", ProbeKind.HTML,
            negativeMarkers = listOf("Page not found", "404")),
        PlatformProbe("About.me", "هوية", "https://about.me/%s", ProbeKind.HTML,
            negativeMarkers = listOf("Page not found", "404")),
        PlatformProbe("Linktree", "روابط", "https://linktr.ee/%s", ProbeKind.HTML,
            negativeMarkers = listOf("Page not found", "404")),
        PlatformProbe("Twitter / X", "مجتمع", "https://x.com/%s", ProbeKind.HTML,
            negativeMarkers = listOf("This account doesn’t exist", "This account doesn&#39;t exist")),
        PlatformProbe("Instagram", "مجتمع", "https://www.instagram.com/%s/", ProbeKind.HTML,
            negativeMarkers = listOf("Sorry, this page isn&#39;t available")),
        PlatformProbe("TikTok", "فيديو", "https://www.tiktok.com/@%s", ProbeKind.HTML,
            negativeMarkers = listOf("Couldn&#39;t find this account", "Couldn't find this account"))
    )

    suspend fun scan(
        rawUsername: String,
        maxPlatforms: Int = platforms.size
    ): List<UsernameHit> {
        val username = rawUsername.trim().removePrefix("@")
        require(username.matches(Regex("[A-Za-z0-9._-]{2,64}"))) {
            "اسم المستخدم يجب أن يكون معرفاً بسيطاً (حروف إنجليزية وأرقام ونقطة/شرطة)."
        }
        val selected = platforms.take(maxPlatforms.coerceIn(1, platforms.size))
        val semaphore = Semaphore(5)
        return coroutineScope {
            selected.map { platform ->
                async(Dispatchers.IO) {
                    semaphore.withPermit { probe(platform, username) }
                }
            }.awaitAll()
        }
    }

    internal fun buildProbeUrl(platform: PlatformProbe, rawUsername: String): String {
        val username = rawUsername.trim().removePrefix("@")
        val lookupValue = if (platform.kind == ProbeKind.BLUESKY && !username.contains('.')) "$username.bsky.social" else username
        val encoded = URLEncoder.encode(lookupValue, StandardCharsets.UTF_8.name())
        return platform.urlTemplate.replace("%s", encoded)
    }

    private fun probe(platform: PlatformProbe, username: String): UsernameHit {
        val url = buildProbeUrl(platform, username)
        val outcome = runCatching { fetch(url, platform.kind) }
        if (outcome.isFailure) {
            val error = outcome.exceptionOrNull()
            if (error is CancellationException) throw error
            return UsernameHit(
                platform.name, platform.category, url, ProbeStatus.ERROR, 0,
                error?.message?.take(140) ?: "تعذر الاتصال"
            )
        }
        val (statusCode, body) = outcome.getOrThrow()
        return classify(platform, url, statusCode, body)
    }

    internal fun classify(
        platform: PlatformProbe,
        url: String,
        statusCode: Int,
        body: String
    ): UsernameHit {
        fun hit(status: ProbeStatus, confidence: Int, detail: String) =
            UsernameHit(platform.name, platform.category, url, status, confidence, detail)

        if (statusCode == 404 || statusCode == 410) {
            return hit(ProbeStatus.NOT_FOUND, 85, "المصدر أعاد 404/410 لهذا الاسم.")
        }
        if (statusCode == 429) {
            return hit(ProbeStatus.BLOCKED, 0, "المصدر يحدّ الطلبات (HTTP 429).")
        }
        if (statusCode == 401 || statusCode == 403) {
            return hit(ProbeStatus.BLOCKED, 0, "المصدر رفض الوصول (HTTP $statusCode).")
        }
        if (statusCode !in 200..299) {
            return hit(ProbeStatus.UNKNOWN, 0, "استجابة غير حاسمة: HTTP $statusCode.")
        }

        val lower = body.lowercase(Locale.ROOT)
        val ambiguous = when (platform.kind) {
            ProbeKind.GITHUB -> !lower.contains("\"login\"")
            ProbeKind.GITLAB -> !(body.trimStart().startsWith("[") && lower.contains("\"username\""))
            ProbeKind.CODEBERG -> !lower.contains("\"login\"")
            ProbeKind.REDDIT -> !lower.contains("\"name\"") || lower.contains("\"error\"")
            ProbeKind.TELEGRAM -> !lower.contains("tgme_page_title")
            ProbeKind.HACKERNEWS -> body.trim() == "null" || !lower.contains("\"id\"")
            ProbeKind.NPM -> !lower.contains("\"name\"")
            ProbeKind.DOCKERHUB -> !lower.contains("\"username\"")
            ProbeKind.KEYBASE -> !lower.contains("\"status\"") || lower.contains("\"name\":null")
            ProbeKind.MASTODON -> !lower.contains("\"username\"") || lower.contains("\"error\"")
            ProbeKind.BLUESKY -> !lower.contains("\"did\"")
            ProbeKind.STEAM -> !lower.contains("<steamid>")
            ProbeKind.HTML -> {
                val negative = platform.negativeMarkers.any { marker ->
                    marker.isNotBlank() && body.contains(marker, ignoreCase = true)
                }
                if (negative) {
                    return hit(ProbeStatus.NOT_FOUND, 78, "ظهرت عبارة صريحة تفيد بأن الصفحة أو الحساب غير متاح.")
                }

                val expectedUsername = extractProfileUsername(url)
                val bodyHasExactUsername = expectedUsername != null && containsExactProfileToken(body, expectedUsername)
                val explicitSignature = platform.positiveMarker.isNotBlank() &&
                    body.contains(platform.positiveMarker, ignoreCase = true)
                val openGraphSignature = body.contains("property=\"og:title\"", ignoreCase = true) ||
                    body.contains("name=\"twitter:title\"", ignoreCase = true) ||
                    body.contains("profilepage", ignoreCase = true) ||
                    body.contains("\"profile\":", ignoreCase = true)

                val explicitHandleSignature = containsExplicitHandleSignature(body, expectedUsername.orEmpty())
                if (bodyHasExactUsername && (explicitSignature || (openGraphSignature && explicitHandleSignature))) {
                    return hit(ProbeStatus.FOUND, if (explicitSignature) 82 else 74,
                        "ظهرت بيانات ملف عام مع تطابق دقيق لاسم المستخدم.")
                }
                return hit(ProbeStatus.UNKNOWN, 0,
                    if (bodyHasExactUsername) "ظهر المعرّف في الصفحة لكن دون بصمة كافية لتأكيد أنها صفحة الحساب."
                    else "استجابة 200 لم تتضمن بصمة ملف شخصي وتطابقاً دقيقاً؛ لا تُصنف كحساب غير موجود.")
            }
        }
        return if (ambiguous) {
            hit(ProbeStatus.UNKNOWN, 0, "استجابة 200 لا تحتوي بصمة كافية لتأكيد وجود الحساب أو غيابه.")
        } else {
            hit(ProbeStatus.FOUND, 82, "المصدر أعاد هوية حساب عامة لهذا الاسم.")
        }
    }

    private fun extractProfileUsername(rawUrl: String): String? = runCatching {
        val uri = java.net.URI(rawUrl)
        val pathParts = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val candidate = when {
            pathParts.isEmpty() -> uri.rawQuery.orEmpty()
            pathParts.size >= 2 && pathParts[pathParts.lastIndex - 1].lowercase(Locale.ROOT) in
                setOf("user", "users", "people", "in", "profile", "member", "members", "add") ->
                pathParts.last()
            else -> pathParts.last()
        }.removePrefix("@")
        URLDecoder.decode(candidate, StandardCharsets.UTF_8.name())
            .takeIf { it.isNotBlank() && it != "xml=1" }
    }.getOrNull()

    private fun containsExplicitHandleSignature(body: String, username: String): Boolean {
        val needle = username.trim().removePrefix("@")
        if (needle.length < 2) return false
        val escaped = Regex.escape(needle)
        val atHandle = Regex("(?i)@" + escaped + "($|[^A-Za-z0-9._-])").containsMatchIn(body)
        val structuredUsername = Regex(
            """(?i)["'](?:username|screen_name|uniqueId|handle)["']\\s*:\\s*["']$escaped["']"""
        ).containsMatchIn(body)
        return atHandle || structuredUsername
    }

    private fun containsExactProfileToken(body: String, username: String): Boolean {
        val needle = username.trim().removePrefix("@")
        if (needle.length < 2) return false
        val decoded = body
            .replace("&commat;", "@", ignoreCase = true)
            .replace("&#64;", "@")
            .replace("&#x40;", "@")
        val pattern = Regex(
            "(?i)(^|[^A-Za-z0-9._-])" + Regex.escape(needle) + "($|[^A-Za-z0-9._-])"
        )
        return pattern.containsMatchIn(decoded)
    }

    private fun fetch(url: String, kind: ProbeKind): Pair<Int, String> {
        val accept = if (kind == ProbeKind.HTML || kind == ProbeKind.TELEGRAM) {
            "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5"
        } else "application/json, text/plain, */*"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 7_000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Language", "en-US,en;q=0.8,ar;q=0.6")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
            )
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                var total = 0
                while (total < 220_000) {
                    val read = input.read(buffer, 0, minOf(buffer.size, 220_000 - total))
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    total += read
                }
                out.toString(StandardCharsets.UTF_8.name())
            }.orEmpty()
            return status to body
        } finally {
            connection.disconnect()
        }
    }
}
