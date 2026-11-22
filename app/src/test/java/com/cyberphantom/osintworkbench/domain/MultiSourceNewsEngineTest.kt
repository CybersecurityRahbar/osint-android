package com.cyberphantom.osintworkbench.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class MultiSourceNewsEngineTest {
    private val engine = MultiSourceNewsEngine()

    private val rss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <item>
              <title>Public security update - Reuters</title>
              <link>https://www.reuters.com/world/example-story/</link>
              <pubDate>Wed, 21 Oct 2026 10:00:00 GMT</pubDate>
              <source url="https://www.reuters.com/">Reuters</source>
              <thumbnail url="https://images.example.org/story.jpg" />
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun parsesRssStoryAndKeepsPublisherAttribution() {
        val stories = engine.parseRss(rss, null, "en", "US")

        assertEquals(1, stories.size)
        assertEquals("Public security update", stories.single().title)
        assertEquals("Reuters", stories.single().publisherName)
        assertEquals("reuters.com", stories.single().domain)
        assertEquals("https://www.reuters.com/world/example-story/", stories.single().url)
        assertEquals("https://images.example.org/story.jpg", stories.single().imageUrl)
    }

    @Test
    fun imageToggleRemovesImageMetadataWhileKeepingNewsText() {
        val stories = engine.parseRss(rss, null, "en", "US", includeImages = false)

        assertEquals(1, stories.size)
        assertEquals("Public security update", stories.single().title)
        assertNull(stories.single().imageUrl)
    }

    @Test
    fun parsesGdeltJsonIntoPublisherAttributedStories() {
        val json = JSONObject(
            """
            {
              "articles": [{
                "url": "https://www.reuters.com/world/example-story/",
                "title": "Global security update",
                "seendate": "20261021T100000Z",
                "domain": "www.reuters.com",
                "language": "English",
                "sourcecountry": "United States",
                "socialimage": "https://images.example.org/gdelt.jpg"
              }]
            }
            """.trimIndent()
        )

        val stories = engine.parseGdeltResponse(json)

        assertEquals(1, stories.size)
        assertEquals("Reuters", stories.single().publisherName)
        assertEquals("reuters.com", stories.single().domain)
        assertEquals("GDELT DOC API", stories.single().provider)
        assertEquals("https://images.example.org/gdelt.jpg", stories.single().imageUrl)
    }

    @Test
    fun gdeltImageTogglePreservesStoryMetadataButHidesImageUrl() {
        val json = JSONObject(
            """
            {"articles":[{
              "url":"https://www.reuters.com/world/example-story/",
              "title":"Global security update",
              "domain":"www.reuters.com",
              "socialimage":"https://images.example.org/gdelt.jpg"
            }]}
            """.trimIndent()
        )

        val stories = engine.parseGdeltResponse(json, includeImages = false)

        assertEquals(1, stories.size)
        assertEquals("Global security update", stories.single().title)
        assertNull(stories.single().imageUrl)
    }

    @Test
    fun bingRssRedirectIsResolvedToThePublisherStoryUrl() {
        val bingRss = """
            <rss version="2.0"><channel><item>
              <title>Public security update - Reuters</title>
              <link>https://www.bing.com/news/apiclick.aspx?url=https%3A%2F%2Fwww.reuters.com%2Fworld%2Fstory&amp;aid=123</link>
              <pubDate>Wed, 21 Oct 2026 10:00:00 GMT</pubDate>
              <source url="https://www.reuters.com/">Reuters</source>
            </item></channel></rss>
        """.trimIndent()

        val stories = engine.parseRss(
            bingRss,
            null,
            "en",
            "US",
            providerLabelOverride = "Bing News RSS"
        )

        assertEquals(1, stories.size)
        assertEquals("https://www.reuters.com/world/story", stories.single().url)
        assertEquals("Reuters", stories.single().publisherName)
        assertEquals("Bing News RSS", stories.single().provider)
    }

    @Test
    fun rssProviderLabelCanDistinguishIndependentFeeds() {
        val stories = engine.parseRss(rss, null, "en", "US", providerLabelOverride = "Bing News RSS")
        assertEquals("Bing News RSS", stories.single().provider)
    }

    @Test
    fun rssParserDropsItemsWithoutValidHttpsStoryLinks() {
        val invalid = rss.replace(
            "https://www.reuters.com/world/example-story/",
            "javascript:alert(1)"
        )

        val stories = engine.parseRss(invalid, null, "en", "US")
        assertTrue(stories.isEmpty())
    }

    @Test
    fun rssParserRetainsPublisherSummaryAndAttributionOverride() {
        val feed = """
            <rss version="2.0"><channel><item>
              <title>Regional news update</title>
              <link>https://news.example.org/story/42</link>
              <pubDate>Sun, 22 Nov 2026 12:30:00 GMT</pubDate>
              <description><![CDATA[Full story summary from the publisher.]]></description>
            </item></channel></rss>
        """.trimIndent()

        val stories = engine.parseRss(
            feed,
            null,
            "en",
            "GB",
            providerLabelOverride = "Direct RSS · Test source",
            publisherNameOverride = "Test publisher",
            publisherDomainOverride = "news.example.org"
        )

        assertEquals(1, stories.size)
        assertEquals("Test publisher", stories.single().publisherName)
        assertEquals("news.example.org", stories.single().domain)
        assertEquals("Direct RSS · Test source", stories.single().provider)
        assertEquals("Full story summary from the publisher.", stories.single().description)
    }

    @Test
    fun atomFeedWithHrefLinkAndUpdatedTimestampIsParsed() {
        val atom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <title>Atom public story</title>
                <link rel="alternate" href="https://atom.example.org/story/7" />
                <updated>2026-11-22T09:45:00Z</updated>
                <summary>Atom story summary</summary>
              </entry>
            </feed>
        """.trimIndent()

        val stories = engine.parseRss(atom, null, "en", "GB")
        assertEquals(1, stories.size)
        assertEquals("Atom public story", stories.single().title)
        assertEquals("https://atom.example.org/story/7", stories.single().url)
        assertEquals("2026-11-22T09:45:00Z", stories.single().publishedAt)
        assertEquals("Atom story summary", stories.single().description)
    }

    @Test
    fun timestampParserSupportsGdeltRssAndIsoDates() {
        assertEquals(
            java.time.Instant.parse("2026-11-22T12:30:00Z").toEpochMilli(),
            engine.parsePublishedMillis("20261122T123000Z")
        )
        assertEquals(
            java.time.Instant.parse("2026-11-22T12:30:00Z").toEpochMilli(),
            engine.parsePublishedMillis("2026-11-22T12:30:00Z")
        )
        assertEquals(
            java.time.Instant.parse("2026-11-22T12:30:00Z").toEpochMilli(),
            engine.parsePublishedMillis("Sun, 22 Nov 2026 12:30:00 GMT")
        )
        assertNull(engine.parsePublishedMillis("date unknown"))
    }

    @Test
    fun preciseTimeRangeCanBeCheckedAtBothInclusiveBoundaries() {
        val start = java.time.Instant.parse("2026-11-22T12:00:00Z").toEpochMilli()
        val end = java.time.Instant.parse("2026-11-22T13:00:00Z").toEpochMilli()
        val atStart = engine.parsePublishedMillis("2026-11-22T12:00:00Z")
        val atEnd = engine.parsePublishedMillis("2026-11-22T13:00:00Z")
        val outside = engine.parsePublishedMillis("2026-11-22T13:00:01Z")

        assertTrue(atStart!! >= start && atStart <= end)
        assertTrue(atEnd!! >= start && atEnd <= end)
        assertFalse(outside!! >= start && outside <= end)
    }

}
