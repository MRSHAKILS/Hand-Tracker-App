package com.handtracker.fingerspark

/** Rejects stale camera work while allowing only one asynchronous inference at a time. */
internal class TrackingSession {
    private var generation = 0L
    private var active = false
    private var inFlight: Long? = null

    @Synchronized fun start(): Long {
        active = true
        return ++generation
    }

    @Synchronized fun stop() {
        active = false
        generation++
    }

    @Synchronized fun isCurrent(session: Long): Boolean = active && generation == session

    @Synchronized fun currentSession(): Long? = if (active) generation else null

    @Synchronized fun beginFrame(session: Long): Boolean {
        if (!isCurrent(session) || inFlight != null) return false
        inFlight = session
        return true
    }

    @Synchronized fun completeFrame(): Long? {
        val completed = inFlight
        inFlight = null
        return completed
    }
}
