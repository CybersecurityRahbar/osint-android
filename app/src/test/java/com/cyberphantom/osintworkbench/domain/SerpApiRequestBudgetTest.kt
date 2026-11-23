package com.cyberphantom.osintworkbench.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerpApiRequestBudgetTest {
    @Test
    fun neverReservesMoreThanConfiguredMaximum() {
        val budget = SerpApiRequestBudget(4)
        repeat(4) { assertTrue(budget.tryReserveRequest()) }
        repeat(8) { assertFalse(budget.tryReserveRequest()) }
        assertEquals(4, budget.attemptedRequests())
        assertEquals(0, budget.remainingRequests())
    }

    @Test
    fun requestLimitIsClampedToSupportedRange() {
        assertEquals(1, SerpApiRequestBudget(-5).maxRequests)
        assertEquals(10, SerpApiRequestBudget(99).maxRequests)
    }

    @Test
    fun eachSearchReceivesAnIndependentBudget() {
        val first = SerpApiRequestBudget(2)
        val second = SerpApiRequestBudget(2)
        assertTrue(first.tryReserveRequest())
        assertEquals(1, first.remainingRequests())
        assertEquals(2, second.remainingRequests())
    }
}
