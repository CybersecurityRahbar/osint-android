package com.cyberphantom.osintworkbench.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DorkCatalogueTest {
    @Test
    fun `expand quotes the value and respects the cap`() {
        val queries = DorkCatalogue.expand("example.com", listOf(DorkCategory.SOCIAL), max = 3)
        assertEquals(3, queries.size)
        assertTrue(queries.all { it.contains("\"example.com\"") })
    }

    @Test
    fun `expand is distinct across overlapping categories`() {
        val queries = DorkCatalogue.expand("target", listOf(DorkCategory.SOCIAL, DorkCategory.CODE), max = 40)
        assertEquals(queries.distinct(), queries)
        assertTrue(queries.any { it.startsWith("site:github.com") })
        assertTrue(queries.any { it.startsWith("site:reddit.com") })
    }

    @Test
    fun `blank queries expand to nothing`() {
        assertTrue(DorkCatalogue.expand("   ").isEmpty())
    }

    @Test
    fun `forKind returns a bounded curated set`() {
        IdentifierKind.entries.forEach { kind ->
            val queries = DorkCatalogue.forKind(kind, max = 3)
            assertEquals(3, queries.size)
            assertTrue(queries.all { it.isNotBlank() })
        }
    }

    @Test
    fun `every category contributes templates`() {
        DorkCatalogue.categories().forEach { category ->
            assertTrue(category.name, DorkCatalogue.templatesFor(listOf(category)).isNotEmpty())
        }
    }
}
