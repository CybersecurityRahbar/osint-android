package com.cyberphantom.osintworkbench.domain

/** Read-only inventory of the collectors the app can contact, used for transparency in the UI. */
data class SourceDescriptor(
    val name: String,
    val host: String,
    val capability: String,
    val note: String = ""
)

object SourceRegistry {
    val recon = listOf(
        SourceDescriptor("Cloudflare DNS", "cloudflare-dns.com", "DNS over HTTPS", "A/AAAA/NS/MX/TXT/CAA"),
        SourceDescriptor("Google Public DNS", "dns.google", "DNS over HTTPS", "احتياطي DNS"),
        SourceDescriptor("Certificate Transparency", "crt.sh", "شهادات عامة", "أسماء ونطاقات فرعية"),
        SourceDescriptor("RDAP", "rdap.org", "بيانات تسجيل", "الحالة والتواريخ وجهة التسجيل"),
        SourceDescriptor("Internet Archive CDX", "web.archive.org", "أرشيف الويب", "لقطات وصور مؤرشفة"),
        SourceDescriptor("Archive.org Catalog", "archive.org", "كتالوغ الأرشيف", "نصوص ووسائط مؤرشفة"),
        SourceDescriptor("ipwho.is", "ipwho.is", "جيولوكيشن تقريبي", "دولة/مدينة/ASN"),
        SourceDescriptor("ipapi.co", "ipapi.co", "جيولوكيشن احتياطي", ""),
        SourceDescriptor("Public HTML", "target host", "بصمة صفحة", "ترويسات، عنوان، صور، مولّد"),
        SourceDescriptor("robots/sitemap/security.txt", "target host", "ملفات اكتشاف عامة", "قراءة محدودة")
    )

    val search = listOf(
        SourceDescriptor("DuckDuckGo", "duckduckgo.com", "فهرس ويب", "HTML + Lite"),
        SourceDescriptor("Bing", "bing.com", "فهرس ويب", "HTML"),
        SourceDescriptor("Google", "google.com", "فهرس ويب", "HTML؛ قد يظهر تحقق"),
        SourceDescriptor("Brave Search", "search.brave.com", "فهرس مستقل", ""),
        SourceDescriptor("Mojeek", "mojeek.com", "فهرس مستقل", ""),
        SourceDescriptor("Startpage", "startpage.com", "فهرس ويب", ""),
        SourceDescriptor("Ecosia", "ecosia.org", "فهرس ويب", ""),
        SourceDescriptor("Yandex", "yandex.com", "فهرس ويب", ""),
        SourceDescriptor("Qwant API", "api.qwant.com", "JSON", ""),
        SourceDescriptor("SearXNG", "searx.be / disroot.org", "ميتاسيرش", "JSON اختياري"),
        SourceDescriptor("DuckDuckGo Instant Answer", "api.duckduckgo.com", "ملخصات", ""),
        SourceDescriptor("GitHub API", "api.github.com", "حسابات عامة", "بلا مفتاح: سقف منخفض"),
        SourceDescriptor("Wikidata", "wikidata.org", "كيانات مرجعية", "")
    )

    val news = listOf(
        SourceDescriptor("GDELT DOC API", "api.gdeltproject.org", "أخبار عالمية", "1-2 أسبوع"),
        SourceDescriptor("Google News RSS", "news.google.com", "أخبار", "عربي/إنجليزي"),
        SourceDescriptor("Bing News RSS", "bing.com", "أخبار", "غير موثّق رسمياً"),
        SourceDescriptor("SearXNG News", "searx.be / disroot.org", "أخبار", "JSON"),
        SourceDescriptor("Publisher RSS", "10 ناشرين", "أخبار", "Reuters/BBC/الجزيرة…")
    )

    val images = listOf(
        SourceDescriptor("Bing Images", "bing.com/images", "صور مفهرسة", "murl/turl/purl"),
        SourceDescriptor("SearXNG Images", "searx.be", "صور", ""),
        SourceDescriptor("Wikimedia Commons", "commons.wikimedia.org", "صور حرة", "ترخيص مطلوب التحقق")
    )

    val all: List<SourceDescriptor> get() = recon + search + news + images
    val total: Int get() = all.size
}
