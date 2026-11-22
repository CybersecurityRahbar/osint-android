# OSINT Workbench

**OSINT Workbench** is an offline-first Android OSINT workbench for collecting publicly available information, preserving evidence, and building explainable technical relationships without a private backend or a bundled large language model.

> **Prototype status: v0.8.0-preview.** This build addresses field-test failures in news parsing, username classification, public search extraction, and private-source session separation. It remains an actively developed prototype, not a complete replacement for SpiderFoot, Maltego, or a dedicated vulnerability scanner.

- **Brushed-steel interface.** The dark graphite console now uses polished steel gradients and bevel-like borders, brighter legible text, metallic dividers, and functional cyan, violet, blue, green and amber accents. Home evidence, research results, reconnaissance records, case cards, statistics and mode controls share the same visual language.
- **Email authentication posture.** SPF, DMARC and eight common DKIM selectors are inspected over public DNS, with explicit "not observed" records and risk notes for `+all`, `p= none`, lookup-limit overruns and missing records.
- **Archive.org catalogue search** in addition to Wayback CDX: archived texts and media related to the domain, each with its source URL.
- **Username enumeration across ~30 platforms** (code hosts, social, package registries, identity and media). Explicit 404/410 and explicit unavailable-page markers can indicate absence; ambiguous HTTP 200, login walls, and incomplete pages remain UNKNOWN. HTML pages require exact username evidence and a profile signature before confirmation.
- **Expanded public-source discovery.** Search interleaves four focused query variants with targeted dorks for social sites, Reddit-indexed pages, code hosts, public forums, documents, paste indexes and archives. Additional open indexes include Arabic/English Wikipedia, Lemmy communities, Hacker News, Stack Exchange, OpenAlex, Crossref, GitHub public users and Wikidata. Public-source failures are isolated and attributed.
- **Elapsed-time and staged progress monitor.** Identifier and news runs display a live elapsed clock, a segmented progress track, stage status, and completed source/result counts.
- **Deterministic fusion pass** over every result set: extracted e-mails, phones, handles, domains, IPs and wallet strings, leak/breach keyword alerts, country hints and a transparent confidence score. It never asserts identity.
- **Private-source execution separated correctly.** The search workflow remains inside Research → Private Sources. Settings → Private Sources contains only source configuration and a full-height session window for login. Each source can independently use command → prompt → value, command → value, or value-only mode for number and name lookups. The retained WebView session is shared with the Research search screen and has Activity lifecycle handling. The app verifies the selected public username route, visible chat, and composer before sending; a first-time source can be started through its visible Start control.
- **Backups keep full context.** Exported entities now store the complete contextual kind (for example `HTTP_STATUS@example.com`), so restoring a backup no longer merges properties of different targets. Cases and scans can be deleted individually, and a confirmed local erase is available.

## What changed in 0.8.0

- Android XML parser compatibility: optional XInclude and entity-expansion flags are guarded for Android JAXP providers; HTML challenge pages are rejected before XML parsing. The workflow now runs RSS and Atom instrumentation tests on an Android 15 emulator, not only local JVM tests.
- News source health: attempted publishers, responding sources, article-returning sources, and failed sources are counted separately. No-result text now distinguishes unfiltered searches from exact date/time windows.
- Public search extraction: jsoup HTML5 DOM parsing replaces the brittle regex-only extraction path; JSON responses are validated before JSON decoding. Unavailable providers remain isolated instead of poisoning the complete search.
- Image discovery: Openverse joins Bing Images, Wikimedia Commons, and SearXNG image search; the result retains its public landing page, creator and license attribution where available.
- Username reliability: ambiguous HTTP 200, sign-in pages and incomplete profile markup are UNKNOWN rather than NOT_FOUND. HTML profile confirmation requires an exact identifier and explicit account signature.
- Private-source layout: the search and lookup screen is restored to Research. Settings now contains only source setup and a full-height login/session window. Each source has independent number/name modes: command then prompt, command then value, or value only. An optional actual chat title supports services whose public username and visible chat title differ.
- WebView session handling: the session is retained between Settings and Research, follows Activity lifecycle, opens the Web K @username route, verifies a resolved chat, and handles the first-time Start control. The full-height session dialog avoids the previous nested scroll container; physical-device scrolling and live-source sessions still require device validation.

## Domain intelligence

- DNS over HTTPS for A, AAAA, NS, MX, TXT, and CAA records using Cloudflare and Google Public DNS fallback.
- Certificate Transparency lookup through crt.sh.
- Optional RDAP registration metadata.
- Internet Archive Wayback CDX snapshot discovery, retry of transient failures, a secondary Availability API fallback, and a visible no-snapshot / source-unavailable state.
- Separate Wayback CDX media lookup for archived images, with a direct replay-image URL and a source snapshot URL.
- Public indexed-page discovery for `site:domain` and domain-related PDF queries, in addition to DNS, RDAP, certificate, and HTML metadata.
- Best-effort extraction of social-preview / public image URLs from captured Wayback pages and the live site's public HTML.
- HTTPS status, redirects, selected response headers, HTML title and generator hints.
- Public discovery metadata from `robots.txt`, `sitemap.xml`, and `security.txt`, with bounded reads and source URLs.
- Approximate geolocation/ISP metadata for a small sample of resolved IPs using two public providers. This can locate a hosting provider or CDN, not a physical person or reliably the actual origin server.
- Optional active checks (off by default) for a fixed list of 23 TCP ports on at most two resolved IPs and seven common public exposure indicators. Enable them only for assets you own or are authorized to assess. They do not exploit services, authenticate, bypass controls, detect arbitrary custom backdoors, or prove a system is secure when no finding appears.
- Archive/public-image thumbnails where available; result cards retain source attribution and can open the underlying source.
- Per-source warnings and timeouts. A missing record is not proof of absence, and an archive capture does not mean the page is online today.

## Automatic public identifier search

- Search modes for names, usernames, phone numbers, and email addresses.
- Expands queries into multiple public-index searches (including Reddit-indexed pages and public caller-report directories for numbers).
- Attempts live public-index retrieval across DuckDuckGo HTML/Lite, Bing, Google, Brave, Mojeek, Startpage, Ecosia and a SearXNG instance using provider-specific DOM extraction and redirect normalization. HTML is parsed with jsoup rather than a page-wide regex; JSON responses are checked before decoding. CAPTCHA, HTML error pages, HTTP 403/429 and timeouts are isolated and reported per provider.
- Tries image indexes independently: Bing Images metadata (`murl`/`turl`/`purl`), SearXNG image results, Wikimedia Commons, and Openverse. Openverse results retain the landing page, creator and license attribution where available. The visible thumbnail links to the indexed source page; a thumbnail is not proof of identity or context.
- Keeps source-level warnings and bounded concurrency/timeouts. Public engines and SearXNG instances may disable HTML/JSON access, so the app reports the failed provider rather than implying that a zero-result search proves the information is absent.
- Image results are controlled by a single in-app toggle. When enabled, identifier search also calls Bing Images, SearXNG image search, Wikimedia Commons and Openverse; news feeds and page metadata may supply additional images. This remains a multi-source best-effort index, not a universal image index.
- Ranks matches deterministically: exact phone digits, exact email/username hits and title/token overlap rise above vague results. The relevance score is explicitly heuristic, not a probability.
- Combines available API results from GitHub public users search and Wikidata for suitable queries.
- Normalizes international-format phone numbers offline with Google's libphonenumber metadata and displays numbering-plan information only. It does **not** reveal a subscriber, confirm that a number is active, or identify its owner.
- Provider links remain available as fallbacks. Some result pages may be blocked, rate-limited, or changed; failure warnings remain visible.
- Does not authenticate to social networks, bypass access controls, or query private password/breach databases.

## Multi-source news

- Concurrent collection combines GDELT DOC API, regional Google News RSS editions, Bing News RSS, SearXNG News, and direct publisher feeds from BBC World, Al Jazeera Arabic and English, The Guardian, DW, France 24 Arabic and English, and Sky News Arabia.
- Publisher-scoped Google News RSS searches broaden coverage across additional outlets such as Reuters, AP, BBC, Al Jazeera, Al Arabiya, Asharq, Sky News Arabia, Al Hurra, Al Mayadeen, The New York Times and NPR. These are indexed publisher searches, not claims that every publisher exposes a direct RSS feed.
- Each article displays its publisher, source domain, article link, publication timestamp, language, publisher summary when provided, and preview image when available. Duplicate article URLs are removed before display.
- A custom date/time window lets the user choose year, month, day, hour and minute for both the start and end. Results with known timestamps are filtered against the exact requested interval; records with unknown publication dates are excluded from a constrained query rather than silently included.
- GDELT's DOC API is used for precise timestamp windows only within its recent searchable window (approximately three months). Older intervals continue through other date-capable search feeds and are clearly marked when GDELT is skipped.
- Each source is queried independently with bounded concurrency and timeouts. Failures and empty responses are visible; a single failing publisher does not abort the remaining sources.
- The news results can contain partial or no records when feeds are blocked, rate-limited, unavailable, or contain no matching stories. A zero-result search is not proof that no article exists.

## Evidence and local intelligence

- SQLite persists entities, evidence, scan payloads, relationships, and case workspaces locally.
- Relationship types distinguish DNS resolution, nameservers, mail exchanges, subdomains, archive captures, redirects, images, registration metadata, and observed web properties.
- SQLite schema v3 preserves the same relation when it is supported by multiple recorded sources instead of overwriting the prior source. The graph groups matching edges and displays the stored sources and observations.
- Repeated generic values (for example, an HTTP status or a page title) are scoped to the observed target to reduce accidental global “hub” relationships.
- Entity backups store the full contextual kind key, and JSON restore accepts both the new full key and the older base kind.
- An explainable, offline rule engine highlights shared-IP observations, stale/weak evidence, missing data, and suggested next verification steps. It does not invent identity links from name similarity.
- Confidence values are heuristic source-quality indicators, not calibrated statistical probabilities.

## Privacy and limitations

- No private backend, bundled large LLM, or huge local breach database is required.
- Live collection needs network access. Carrier, country, DNS, source, and rate-limit restrictions may still cause empty results.
- Automatic identifier lookup is based on public indexes and permitted public APIs, not on a universal, authoritative phone-owner database. A zero-result response does not prove that information does not exist.
- The app's SQLite database **is not encrypted at rest yet**; avoid storing highly sensitive data in the prototype.
- GitHub Actions builds the **v0.8.0 stable-signed preview channel** with application ID `com.cyberphantom.osintworkbench.preview051`. Future preview builds keep this same application ID and signing certificate, allowing Android to update the app in place without clearing its WebView session or local app data. The older `.preview050` prototype used a temporary CI key, so this first transition is intentionally installed side-by-side; keep that older app until the new one is verified and any local data has been exported/imported. Configure the two GitHub Actions secrets `PREVIEW_KEYSTORE_BASE64` and `PREVIEW_KEYSTORE_PASSWORD` as documented in [Stable preview signing](docs/STABLE_PREVIEW_SIGNING.md). CI does not publish an APK artifact until stable signing is configured. The downloadable artifact is a ZIP containing `app-debug.apk`; install the APK, not the ZIP itself.
- CI verifies compilation/tests and APK packaging; it does not validate each source's live response on every carrier or phone.

## Search-engine reference

`ALKASER_OSINT.py` in this repository was reviewed as an implementation reference for provider-specific adapters, bounded parallel requests, image metadata extraction, dork expansion, and Wayback CDX media lookups. The Android app does not execute the Python script or bundle its Python dependencies; equivalent Kotlin adapters are implemented separately and remain subject to each provider's access rules.

## Build

Requirements: JDK 17 and Android SDK API 36. The Gradle Wrapper downloads the pinned Gradle version on first use:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions runs unit tests, an Android emulator RSS/Atom parser suite against the actual Android XML provider, and builds the preview APK. It uploads `osint-workbench-v0.8.0-preview-apk` only when the stable signing secrets are configured; otherwise it deliberately publishes no installable artifact.

## Cumulative context

See [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) for the user's requirements, implementation decisions, CI results, limitations, and next steps.
