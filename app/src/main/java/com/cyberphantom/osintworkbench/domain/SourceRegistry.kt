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
        SourceDescriptor("Wikidata", "wikidata.org", "كيانات مرجعية", ""),
        SourceDescriptor("Wikipedia EN/AR", "wikipedia.org", "بحث موسوعي مباشر", "عربي وإنجليزي"),
        SourceDescriptor("Lemmy", "lemmy.world / lemmy.ml", "مجتمعات عامة مفتوحة", "منشورات عامة فقط"),
        SourceDescriptor("Hacker News / Algolia", "hn.algolia.com", "أرشيف نقاشات عامة", "قصص ومنشورات عامة"),
        SourceDescriptor("Stack Exchange", "api.stackexchange.com", "أسئلة وأجوبة عامة", "بحث متقدم"),
        SourceDescriptor("OpenAlex", "api.openalex.org", "فهارس مؤلفين علميين", "مؤسسات وأعمال منشورة"),
        SourceDescriptor("Crossref", "api.crossref.org", "فهرس منشورات علمية", "DOI ومعلومات التأليف")
    )

    val news = listOf(
        SourceDescriptor("GDELT DOC API", "api.gdeltproject.org", "أخبار عالمية", "1-2 أسبوع"),
        SourceDescriptor("Google News RSS", "news.google.com", "أخبار", "عربي/إنجليزي"),
        SourceDescriptor("Bing News RSS", "bing.com", "أخبار", "غير موثّق رسمياً"),
        SourceDescriptor("SearXNG News", "searx.be / disroot.org", "أخبار", "JSON"),
        SourceDescriptor("Publisher RSS Search", "news.google.com", "بحث موجه للناشرين", "Reuters/AP/BBC/الجزيرة وغيرها"),
        SourceDescriptor("BBC World RSS", "feeds.bbci.co.uk", "خلاصة مباشرة", "وقت النشر والرابط"),
        SourceDescriptor("Al Jazeera RSS", "aljazeera.com / aljazeera.net", "خلاصة مباشرة", "إنجليزي وعربي"),
        SourceDescriptor("The Guardian RSS", "theguardian.com", "خلاصة مباشرة", "World"),
        SourceDescriptor("DW RSS", "rss.dw.com", "خلاصة مباشرة", "إنجليزي"),
        SourceDescriptor("France 24 RSS", "france24.com", "خلاصة مباشرة", "عربي وإنجليزي"),
        SourceDescriptor("Sky News Arabia RSS", "skynewsarabia.com", "خلاصة مباشرة", "أخبار عربية"),
        SourceDescriptor("Bing News RSS", "bing.com", "خلاصة أخبار", "RSS"),
        SourceDescriptor("SearXNG News", "searx.be / disroot.org", "بحث متعدد المصادر", "JSON"),
        SourceDescriptor("GDELT DOC API", "api.gdeltproject.org", "أخبار مؤرخة", "نطاق بحث مباشر محدود بآخر ثلاثة أشهر")
    )

    val images = listOf(
        SourceDescriptor("Bing Images", "bing.com/images", "صور مفهرسة", "murl/turl/purl"),
        SourceDescriptor("SearXNG Images", "searx.be", "صور", ""),
        SourceDescriptor("Wikimedia Commons", "commons.wikimedia.org", "صور حرة", "ترخيص مطلوب التحقق")
    )

    val all: List<SourceDescriptor> get() = recon + search + news + images
    val total: Int get() = all.size
}
