package org.riverbank.shop

import android.os.SystemClock

/**
 * Tracks whether the session has been unlocked and for how long it may stay
 * that way. Purely in-memory: killing the app always re-locks it.
 */
object AppLock {

    private var unlockedAtElapsed = 0L
    private var backgroundedAtElapsed = 0L
    private var unlocked = false

    fun markUnlocked() {
        unlocked = true
        unlockedAtElapsed = SystemClock.elapsedRealtime()
        backgroundedAtElapsed = 0L
    }

    fun markBackgrounded() {
        if (unlocked) backgroundedAtElapsed = SystemClock.elapsedRealtime()
    }

    fun lock() {
        unlocked = false
        backgroundedAtElapsed = 0L
    }

    /** @param timeoutSeconds seconds allowed in the background, or -1 for never re-lock. */
    fun needsUnlock(timeoutSeconds: Long): Boolean {
        if (!unlocked) return true
        if (timeoutSeconds < 0) return false
        if (backgroundedAtElapsed == 0L) return false
        val away = (SystemClock.elapsedRealtime() - backgroundedAtElapsed) / 1000
        if (away >= timeoutSeconds) {
            lock()
            return true
        }
        return false
    }
}
