package com.cyberphantom.osintworkbench.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameEnumerationEngineTest {
    private val engine = UsernameEnumerationEngine()
    private val github = PlatformProbe("GitHub", "شيفرة", "https://api.github.com/users/%s", ProbeKind.GITHUB)

    @Test
    fun `404 is a confident absence`() {
        val hit = engine.classify(github, "https://api.github.com/users/nobody", 404, "")
        assertEquals(ProbeStatus.NOT_FOUND, hit.status)
        assertTrue(hit.confidence >= 80)
        assertFalse(hit.found)
    }

    @Test
    fun `429 and forbidden are blocked rather than absent`() {
        assertEquals(ProbeStatus.BLOCKED, engine.classify(github, "u", 429, "").status)
        assertEquals(ProbeStatus.BLOCKED, engine.classify(github, "u", 403, "").status)
    }

    @Test
    fun `json identity marker confirms an account`() {
        val hit = engine.classify(github, "https://api.github.com/users/octocat", 200, """{"login":"octocat","id":1}""")
        assertEquals(ProbeStatus.FOUND, hit.status)
        assertTrue(hit.found)
    }

    @Test
    fun `200 without the identity marker is not counted as found`() {
        val hit = engine.classify(github, "https://api.github.com/users/octocat", 200, """{"message":"Not Found"}""")
        assertEquals(ProbeStatus.NOT_FOUND, hit.status)
        assertFalse(hit.found)
    }

    @Test
    fun `html negative marker beats a 200 response`() {
        val youtube = PlatformProbe(
            "YouTube", "فيديو", "https://www.youtube.com/@%s", ProbeKind.HTML,
            positiveMarker = "\"channelId\"",
            negativeMarkers = listOf("This page isn't available")
        )
        val hit = engine.classify(youtube, "https://www.youtube.com/@ghost", 200, "<html>This page isn't available</html>")
        assertEquals(ProbeStatus.NOT_FOUND, hit.status)
    }
}
