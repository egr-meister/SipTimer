package com.egrmeister.siptimer.domain

/**
 * Protects one activation (a tap) from being processed twice — e.g. a double dispatch of the
 * same button within [windowMs] — while still allowing deliberate, separate activations.
 */
class ActivationGuard(private val windowMs: Long = 600L) {
    private val last = HashMap<String, Long>()
    private val inFlight = HashSet<String>()

    @Synchronized
    fun tryBegin(key: String, nowMs: Long): Boolean {
        if (key in inFlight) return false
        val previous = last[key]
        if (previous != null && nowMs - previous in 0 until windowMs) return false
        inFlight += key
        last[key] = nowMs
        return true
    }

    @Synchronized
    fun end(key: String) {
        inFlight -= key
    }
}
