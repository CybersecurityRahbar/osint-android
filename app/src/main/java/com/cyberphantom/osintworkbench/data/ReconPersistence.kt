package com.cyberphantom.osintworkbench.data

import com.cyberphantom.osintworkbench.domain.ReconRecord
import com.cyberphantom.osintworkbench.domain.ScanReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persists one recon report as entities, source-scoped edges, findings and a scan payload.
 * Values that are properties of the target (status codes, titles, headers) keep a contextual
 * kind key so they never become global hubs between unrelated targets.
 */
suspend fun persistReconReport(store: LocalStore, report: ScanReport) = withContext(Dispatchers.IO) {
    val rootId = store.upsertEntity(report.target, "DOMAIN", "User supplied", 100)
    report.records.forEach { item ->
        val entityValue = when (item.kind) {
            "DNS_NS" -> item.value.trim().trimEnd('.')
            "DNS_MX" -> item.value.trim().substringAfter(' ', item.value).trim().trimEnd('.')
            else -> item.value
        }
        val stableEntity = item.kind in setOf(
            "DNS_A", "DNS_AAAA", "DNS_NS", "DNS_MX", "SUBDOMAIN", "RDAP_REGISTRAR",
            "EMAIL_DKIM_SELECTOR"
        )
        val nodeKind = if (stableEntity || item.kind == "WAYBACK_SNAPSHOT") {
            item.kind
        } else {
            "${item.kind}@${report.target}"
        }
        val nodeId = store.upsertEntity(entityValue, nodeKind, item.source, item.confidence)
        val relation = relationFor(item.kind)
        val rootToNode = item.kind in ROOT_TO_NODE
        store.addEdge(
            fromId = if (rootToNode) rootId else nodeId,
            toId = if (rootToNode) nodeId else rootId,
            relation = relation,
            source = item.source,
            confidence = item.confidence
        )
        store.addFinding(
            entityId = rootId,
            title = item.kind.replace('_', ' '),
            detail = "${item.value}\n${item.detail}\n${item.sourceUrl}",
            source = item.source,
            severity = if (item.kind.endsWith("_RISK") || item.kind == "POTENTIAL_SENSITIVE_EXPOSURE" || item.kind.endsWith("_PERMISSIVE")) {
                "warning"
            } else "info"
        )
    }
    report.warnings.forEach { warning ->
        store.addFinding(rootId, "ملاحظة الاستطلاع", warning, "Recon engine", "warning")
    }
    store.addScan(
        target = report.target,
        status = if (report.records.isEmpty()) "no_results" else "completed",
        startedAt = report.startedAt,
        summary = "${report.records.size} records · ${report.sourceCount} sources · ${report.warnings.size} warnings",
        records = report.records.size,
        payloadJson = report.toJson()
    )
}

internal fun relationFor(kind: String): String = when (kind) {
    "DNS_A", "DNS_AAAA" -> "RESOLVES_TO"
    "DNS_NS" -> "USES_NAMESERVER"
    "DNS_MX" -> "USES_MAIL_EXCHANGE"
    "SUBDOMAIN" -> "SUBDOMAIN_OF"
    "SUBDOMAIN_ADDRESS" -> "SUBDOMAIN_RESOLVES_TO"
    "SUBDOMAIN_DNS_STATUS" -> "SUBDOMAIN_DNS_LOOKUP_STATUS"
    "WAYBACK_SNAPSHOT" -> "ARCHIVED_VERSION_OF"
    "WAYBACK_IMAGE" -> "ARCHIVED_PUBLIC_IMAGE_OF"
    "ARCHIVE_ORG_ITEM" -> "CATALOGUED_IN_ARCHIVE_ORG"
    "PUBLIC_WEB_RESULT" -> "LISTED_IN_PUBLIC_WEB_INDEX"
    "WAYBACK_STATUS" -> "ARCHIVE_LOOKUP_STATUS"
    "ROBOTS_TXT" -> "HAS_ROBOTS_POLICY"
    "ROBOTS_DISALLOW" -> "MENTIONS_CRAWLER_PATH"
    "SITEMAP_SUMMARY" -> "HAS_PUBLIC_SITEMAP"
    "SITEMAP_URL" -> "LISTS_PUBLIC_URL"
    "SECURITY_TXT_METADATA" -> "PUBLISHES_SECURITY_CONTACT_METADATA"
    "RDAP_REGISTRAR" -> "REGISTERED_THROUGH"
    "RDAP_STATUS", "RDAP_REGISTERED", "RDAP_EXPIRES", "RDAP_UPDATED" -> "REGISTRATION_METADATA_FOR"
    "HTTPS_REDIRECT" -> "REDIRECTS_TO"
    "IP_GEOLOCATION" -> "GEOLOCATED_AT_APPROXIMATE"
    "IP_GEOLOCATION_UNAVAILABLE" -> "GEOLOCATION_LOOKUP_STATUS"
    "OPEN_TCP_PORT" -> "EXPOSES_TCP_PORT"
    "POTENTIAL_SENSITIVE_EXPOSURE" -> "POTENTIAL_PUBLIC_EXPOSURE_INDICATOR"
    "PORT_SCAN_SUMMARY", "EXPOSURE_CHECK_SUMMARY" -> "HAS_CHECK_SUMMARY"
    "WEB_IMAGE", "WEB_PREVIEW_IMAGE" -> "HAS_PUBLIC_IMAGE"
    "WEB_TITLE" -> "HAS_PAGE_TITLE"
    "DNS_TXT" -> "HAS_DNS_TEXT_RECORD"
    "EMAIL_SPF" -> "PUBLISHES_SPF"
    "EMAIL_SPF_RISK", "EMAIL_SPF_PERMISSIVE" -> "HAS_EMAIL_POLICY_WEAKNESS"
    "EMAIL_DMARC" -> "PUBLISHES_DMARC"
    "EMAIL_DMARC_MONITOR_ONLY" -> "HAS_EMAIL_POLICY_WEAKNESS"
    "EMAIL_DKIM_SELECTOR" -> "PUBLISHES_DKIM_SELECTOR"
    "EMAIL_POSTURE_SUMMARY" -> "HAS_EMAIL_POSTURE"
    "EMAIL_SPF_ABSENT", "EMAIL_DMARC_ABSENT", "EMAIL_DKIM_NOT_OBSERVED",
    "HTTPS_STATUS", "HTTP_SERVER_HEADER", "TECH_X_POWERED_BY", "TECH_META_GENERATOR",
    "SECURITY_HEADER_NOT_OBSERVED" -> "HAS_OBSERVED_PROPERTY"
    else -> "OBSERVED_DURING_RECON"
}

internal val ROOT_TO_NODE = setOf(
    "DNS_A", "DNS_AAAA", "DNS_NS", "DNS_MX", "HTTPS_REDIRECT",
    "WAYBACK_STATUS", "WAYBACK_IMAGE", "PUBLIC_WEB_RESULT", "ARCHIVE_ORG_ITEM",
    "IP_GEOLOCATION", "IP_GEOLOCATION_UNAVAILABLE",
    "ROBOTS_TXT", "ROBOTS_DISALLOW", "SITEMAP_SUMMARY", "SITEMAP_URL",
    "SECURITY_TXT_METADATA", "SUBDOMAIN_ADDRESS", "SUBDOMAIN_DNS_STATUS",
    "OPEN_TCP_PORT", "POTENTIAL_SENSITIVE_EXPOSURE",
    "PORT_SCAN_SUMMARY", "EXPOSURE_CHECK_SUMMARY",
    "WEB_IMAGE", "WEB_PREVIEW_IMAGE", "WEB_TITLE", "DNS_TXT",
    "HTTPS_STATUS", "HTTP_SERVER_HEADER", "TECH_X_POWERED_BY",
    "TECH_META_GENERATOR", "SECURITY_HEADER_NOT_OBSERVED", "RDAP_REGISTRAR",
    "EMAIL_SPF", "EMAIL_SPF_RISK", "EMAIL_SPF_PERMISSIVE", "EMAIL_SPF_ABSENT",
    "EMAIL_DMARC", "EMAIL_DMARC_MONITOR_ONLY", "EMAIL_DMARC_ABSENT",
    "EMAIL_DKIM_SELECTOR", "EMAIL_DKIM_NOT_OBSERVED", "EMAIL_POSTURE_SUMMARY"
)

/** Category used by the recon result filter chips. */
fun reconCategory(kind: String): String = when {
    kind.startsWith("DNS_") -> "DNS"
    kind.startsWith("EMAIL_") -> "EMAIL"
    kind.startsWith("SUBDOMAIN") || kind.startsWith("CERTIFICATE") -> "SURFACE"
    kind.startsWith("WAYBACK") || kind.startsWith("ARCHIVE_ORG") -> "ARCHIVE"
    kind.startsWith("ROBOTS") || kind.startsWith("SITEMAP") || kind.startsWith("SECURITY_TXT") -> "DISCOVERY"
    kind.startsWith("RDAP") -> "REGISTRATION"
    kind.startsWith("HTTPS") || kind.startsWith("HTTP_") || kind.startsWith("WEB_") ||
        kind.startsWith("TECH_") || kind.startsWith("SECURITY_HEADER") -> "WEB"
    kind.startsWith("IP_GEOLOCATION") -> "GEO"
    kind.startsWith("OPEN_TCP") || kind.startsWith("PORT_SCAN") ||
        kind.startsWith("POTENTIAL_SENSITIVE") || kind.startsWith("EXPOSURE") -> "ACTIVE"
    kind.startsWith("PUBLIC_WEB") -> "INDEX"
    else -> "OTHER"
}

fun ReconRecord.isRisk(): Boolean =
    kind.endsWith("_RISK") || kind.endsWith("_PERMISSIVE") || kind == "POTENTIAL_SENSITIVE_EXPOSURE" ||
        kind == "EMAIL_SPF_ABSENT" || kind == "EMAIL_DMARC_ABSENT"
