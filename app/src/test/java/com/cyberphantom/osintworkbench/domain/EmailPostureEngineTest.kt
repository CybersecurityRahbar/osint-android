package com.cyberphantom.osintworkbench.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailPostureEngineTest {
    private val engine = EmailPostureEngine()

    @Test
    fun `spf hard fail is parsed with includes`() {
        val spf = engine.parseSpf("v=spf1 include:_spf.google.com include:mail.example.net -all")
        assertEquals("-", spf.qualifier)
        assertEquals(2, spf.lookupCount)
        assertTrue(spf.includes.contains("_spf.google.com"))
        assertTrue(spf.includes.contains("mail.example.net"))
    }

    @Test
    fun `spf permissive all is reported as plus`() {
        val spf = engine.parseSpf("v=spf1 a mx +all")
        assertEquals("+", spf.qualifier)
    }

    @Test
    fun `spf soft fail and neutral qualifiers`() {
        assertEquals("~", engine.parseSpf("v=spf1 mx ~all").qualifier)
        assertEquals("?", engine.parseSpf("v=spf1 ?all").qualifier)
    }

    @Test
    fun `dmarc tags are parsed case insensitively`() {
        val dmarc = engine.parseDmarc("v=DMARC1; P=quarantine; sp=reject; rua=mailto:dmarc@example.org; pct=50")
        assertEquals("quarantine", dmarc.policy)
        assertEquals("reject", dmarc.subdomainPolicy)
        assertTrue(dmarc.reporting)
        assertEquals(50, dmarc.percentage)
    }

    @Test
    fun `dmarc without reporting address and percentage stays null`() {
        val dmarc = engine.parseDmarc("v=DMARC1; p=none")
        assertEquals("none", dmarc.policy)
        assertFalse(dmarc.reporting)
        assertNull(dmarc.percentage)
    }
}
