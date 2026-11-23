package com.cyberphantom.osintworkbench.domain

/**
 * Hard per-search ceiling for outbound SerpApi calls.
 * A slot is reserved immediately before network I/O, so failed attempts are bounded locally too.
 */
internal class SerpApiRequestBudget(maxRequests: Int) {
    val maxRequests: Int = maxRequests.coerceIn(1, 10)
    private var attempted = 0

    @Synchronized
    fun tryReserveRequest(): Boolean {
        if (attempted >= maxRequests) return false
        attempted++
        return true
    }

    @Synchronized
    fun attemptedRequests(): Int = attempted

    @Synchronized
    fun remainingRequests(): Int = (maxRequests - attempted).coerceAtLeast(0)
}

internal class SerpApiRequestBudgetExceededException(limit: Int) :
    Exception("تم بلوغ حد طلبات SerpApi لهذه العملية ($limit)؛ لم يُرسل طلب إضافي.")
