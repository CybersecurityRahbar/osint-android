package com.cyberphantom.osintworkbench.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceEnrichmentEngineTest {
    private val engine = OpenSourceEnrichmentEngine()

    @Test
    fun parsesPublicLemmyPostsWithSourceAndCommunityAttribution() {
        val payload = JSONObject(
            """
            {
              "posts": [
                {
                  "post": {"id": 42, "name": "A public discussion", "body": "Open community post"},
                  "creator": {"name": "public-user"},
                  "community": {"name": "technology"}
                }
              ]
            }
            """.trimIndent()
        )

        val results = engine.parseLemmy(payload, "lemmy.world", 10)

        assertEquals(1, results.size)
        assertEquals("Lemmy · lemmy.world", results.single().provider)
        assertEquals("A public discussion", results.single().title)
        assertTrue(results.single().description.contains("public-user"))
        assertTrue(results.single().description.contains("technology"))
        assertEquals("https://lemmy.world/post/42", results.single().url)
    }

    @Test
    fun ignoresMalformedLemmyEntriesWithoutStablePublicPostIds() {
        val payload = JSONObject(
            """{"posts":[{"post":{"id":0,"name":"No public id"}}]}"""
        )
        assertTrue(engine.parseLemmy(payload, "lemmy.world").isEmpty())
    }
}
