package com.cyberphantom.osintworkbench.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelFusionTest {
    private fun result(provider: String, title: String, description: String, url: String) =
        PublicProfileResult(provider = provider, title = title, description = description, url = url, confidence = 60)

    @Test
    fun `identifiers are extracted across results`() {
        val results = listOf(
            result("DuckDuckGo", "contact", "analyst@example.org · +967 771 234 567", "https://one.example/page"),
            result("Bing", "leak mention", "breach dump mentions analyst@example.org and @analyst", "https://two.example/page")
        )
        val profile = IntelFusion.analyze("analyst", IdentifierKind.USERNAME, results)

        assertEquals(2, profile.resultCount)
        assertEquals(2, profile.providerCount)
        assertTrue(profile.emails.any { it.value == "analyst@example.org" })
        assertTrue(profile.handles.any { it.value == "analyst" })
        assertTrue(profile.alerts.isNotEmpty())
        assertTrue(profile.confidence in 0..100)
        assertTrue(profile.notes.any { it.contains("إرشادية") })
    }

    @Test
    fun `empty result set yields zero confidence and an explanatory note`() {
        val profile = IntelFusion.analyze("unknown", IdentifierKind.NAME, emptyList())
        assertEquals(0, profile.resultCount)
        assertEquals(0, profile.confidence)
        assertTrue(profile.notes.isNotEmpty())
        assertTrue(profile.verdict.isNotBlank())
    }

    @Test
    fun `single provider is flagged as not cross-confirmed`() {
        val results = listOf(result("Bing", "t", "nothing special", "https://only.example/x"))
        val profile = IntelFusion.analyze("value", IdentifierKind.EMAIL, results)
        assertEquals(1, profile.providerCount)
        assertTrue(profile.notes.any { it.contains("مصدر واحد") })
    }

    @Test
    fun `ipv4 plausibility rejects out of range octets`() {
        assertTrue(IntelFusion.isPlausibleIpv4("203.0.113.9"))
        assertTrue(!IntelFusion.isPlausibleIpv4("999.1.1.1"))
        assertTrue(!IntelFusion.isPlausibleIpv4("1.2.3"))
    }
}
