package org.riverbank.shop.privacy

import org.riverbank.shop.data.Prefs
import java.util.concurrent.atomic.AtomicInteger

/** A local, offline counter of how many requests were dropped. */
object BlockStats {

    private val session = AtomicInteger(0)
    private var baseline = 0
    private var loaded = false

    fun attach(prefs: Prefs) {
        if (!loaded) {
            baseline = prefs.blockedCount
            loaded = true
        }
    }

    fun record() {
        session.incrementAndGet()
    }

    fun total(): Int = baseline + session.get()

    fun persist(prefs: Prefs) {
        val drained = session.getAndSet(0)
        if (drained > 0) {
            baseline += drained
            prefs.blockedCount = baseline
        }
    }

    fun reset(prefs: Prefs) {
        session.set(0)
        baseline = 0
        prefs.blockedCount = 0
    }
}
