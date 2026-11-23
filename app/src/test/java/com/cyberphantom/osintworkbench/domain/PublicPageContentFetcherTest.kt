package com.cyberphantom.osintworkbench.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicPageContentFetcherTest {
    private val fetcher = PublicPageContentFetcher()

    @Test
    fun extractsArticleAndRemovesNavigationScriptsAndFooter() {
        val html = """
            <html>
              <head><title>Example</title><script>secretTrackingCall()</script></head>
              <body>
                <nav>Primary navigation text</nav>
                <header>Site header</header>
                <main>
                  <article>
                    <h1>Research article title</h1>
                    <p>The first full paragraph has relevant material.</p>
                    <p>The second paragraph contains the remaining context.</p>
                  </article>
                </main>
                <footer>Cookie banner and footer links</footer>
              </body>
            </html>
        """.trimIndent()

        val text = fetcher.extractReadableText(html)

        assertTrue(text.contains("Research article title"))
        assertTrue(text.contains("first full paragraph"))
        assertTrue(text.contains("remaining context"))
        assertFalse(text.contains("Primary navigation text"))
        assertFalse(text.contains("Cookie banner"))
        assertFalse(text.contains("secretTrackingCall"))
    }

    @Test
    fun fallsBackToBodyWhenAnArticleContainerIsMissing() {
        val html = """
            <html><body>
              <div class="content">
                <h2>Open public page</h2>
                <p>Visible public text outside a semantic article element.</p>
              </div>
            </body></html>
        """.trimIndent()

        val text = fetcher.extractReadableText(html)

        assertTrue(text.contains("Open public page"))
        assertTrue(text.contains("Visible public text"))
    }

    @Test
    fun limitsExcessivelyLargePageText() {
        val longParagraph = "paragraph ".repeat(9_000)
        val text = fetcher.extractReadableText("<html><body><article><p>$longParagraph</p></article></body></html>")

        assertTrue(text.length <= 60_000)
        assertTrue(text.isNotBlank())
    }
}
