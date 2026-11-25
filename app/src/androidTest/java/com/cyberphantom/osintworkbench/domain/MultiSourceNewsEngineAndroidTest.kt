package com.cyberphantom.osintworkbench.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyberphantom.osintworkbench.ui.telegram.TelegramSnapshot
import com.cyberphantom.osintworkbench.ui.telegram.TelegramPeerMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultiSourceNewsEngineAndroidTest {
    private val engine = MultiSourceNewsEngine()

    @Test
    fun rssFeedParsesUsingTheActualAndroidXmlProvider() {
        val feed = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Android parser fixture</title>
                <item>
                  <title>First Android feed story</title>
                  <link>https://news.example.org/story/1</link>
                  <pubDate>Sun, 22 Nov 2026 12:30:00 GMT</pubDate>
                  <description>Story summary from the feed.</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val stories = engine.parseRss(feed, null, "en", "US")

        assertEquals(1, stories.size)
        assertEquals("First Android feed story", stories.single().title)
        assertEquals("news.example.org", stories.single().domain)
        assertEquals("Sun, 22 Nov 2026 12:30:00 GMT", stories.single().publishedAt)
        assertEquals("Story summary from the feed.", stories.single().description)
    }

    @Test
    fun atomFeedParsesOnAndroidAndPreservesHrefAndSummary() {
        val feed = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <title>Android Atom story</title>
                <link rel="alternate" href="https://atom.example.org/story/8" />
                <updated>2026-11-22T12:45:00Z</updated>
                <summary>Atom feed summary.</summary>
              </entry>
            </feed>
        """.trimIndent()

        val stories = engine.parseRss(feed, null, "en", "US")

        assertEquals(1, stories.size)
        assertEquals("Android Atom story", stories.single().title)
        assertEquals("https://atom.example.org/story/8", stories.single().url)
        assertEquals("Atom feed summary.", stories.single().description)
    }

    @Test
    fun unsupportedXIncludeToggleDoesNotBreakTheAndroidFeedParser() {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        val xincludeConfiguration = runCatching { factory.isXIncludeAware = false }
        // The parser must work regardless of whether this optional JAXP feature is supported.
        assertTrue(xincludeConfiguration.isSuccess || xincludeConfiguration.exceptionOrNull() is UnsupportedOperationException)

        val feed = """
            <rss version="2.0"><channel><item>
              <title>Parser remains available</title>
              <link>https://feed.example.org/story</link>
            </item></channel></rss>
        """.trimIndent()
        assertEquals(1, engine.parseRss(feed, null, "en", "US").size)
    }

    @Test
    fun directUsernameRouteUsesTheConfiguredVisibleChatTitle() {
        val snapshot = TelegramSnapshot(
            url = "https://web.telegram.org/k/#@tellwhoobot",
            title = "Tell Whoo",
            hasComposer = true,
            hasSendButton = true,
            editorText = "",
            readyState = "complete",
            messages = emptyList(),
            peerRoute = "@tellwhoobot",
            isAuthenticated = true,
            hasAppShell = true
        )
        // A direct @username route remains usable with the default friendly source label.
        // A user-supplied chatTitle, when present, must still match the visible title.
        assertTrue(TelegramPeerMatcher.chatMatches(snapshot, "tellwhoobot", ""))
        assertTrue(!TelegramPeerMatcher.chatMatches(snapshot, "tellwhoobot", "كاشف الأرقام"))
    }

    @Test
    fun numericPeerRouteRequiresTheConfiguredVisibleChatTitle() {
        val snapshot = TelegramSnapshot(
            url = "https://web.telegram.org/k/#123456789",
            title = "Tell Whoo",
            hasComposer = true,
            hasSendButton = true,
            editorText = "",
            readyState = "complete",
            messages = emptyList(),
            peerRoute = "123456789",
            isAuthenticated = true,
            hasAppShell = true
        )
        assertTrue(TelegramPeerMatcher.chatMatches(snapshot, "tellwhoobot", "Tell Whoo"))
        assertTrue(!TelegramPeerMatcher.chatMatches(snapshot, "tellwhoobot", ""))
    }

}
