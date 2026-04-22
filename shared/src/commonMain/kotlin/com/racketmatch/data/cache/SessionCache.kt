package com.racketmatch.data.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * In-memory, TTL-based, read-through cache for repository-level GETs.
 *
 * Repositories are Koin singletons, so an instance of this cache survives
 * across ViewModel lifecycles — switching tabs or re-entering a screen
 * re-uses the last fetched value instead of re-hitting the network. This is
 * the cheap-win half of the caching strategy described in
 * `memory/project_data_cache_deferred.md` — it doesn't persist across app
 * cold-starts (that's SQLDelight territory, deferred) but it kills the
 * "every tab switch triggers 5 fresh network calls" problem entirely.
 *
 * Invalidation hooks:
 *   - Each repo subscribes to the relevant TokenStorage versionFlows in
 *     `init` and calls `invalidate()` when they bump. So `acceptMatch()`
 *     → `incrementMatchesVersion()` → match cache drops.
 *   - `loginVersionFlow` bumps clear everything per-repo to prevent
 *     cross-account data bleed (the failure mode we hit on the prior VM-
 *     singleton experiment).
 *   - Writes that change the cached resource call `invalidate()` directly
 *     before returning, so the next read re-fetches fresh.
 *
 * Thread-safety: [get] uses a coroutine `Mutex` to guard the map read/write
 * but releases the lock while [fetch] is in flight, so concurrent calls on
 * different keys don't serialise. Two racing callers with the same key may
 * both issue the underlying request once — acceptable for a read-through
 * cache and simpler than a full single-flight implementation.
 */
class SessionCache<K : Any, V>(private val ttlMillis: Long) {

    private val entries = mutableMapOf<K, Entry<V>>()
    private val mutex = Mutex()

    private class Entry<V>(val value: V, val fetchedAtMs: Long)

    suspend fun get(key: K, fetch: suspend () -> V): V {
        val now = Clock.System.now().toEpochMilliseconds()
        mutex.withLock {
            val existing = entries[key]
            if (existing != null && now - existing.fetchedAtMs < ttlMillis) {
                return existing.value
            }
        }
        val fresh = fetch()
        val fetchedAt = Clock.System.now().toEpochMilliseconds()
        mutex.withLock { entries[key] = Entry(fresh, fetchedAt) }
        return fresh
    }

    suspend fun invalidate() = mutex.withLock { entries.clear() }

    suspend fun invalidate(key: K) = mutex.withLock { entries.remove(key) }
}

/**
 * Single-value variant — for endpoints that don't vary by input
 * (e.g. `getMyProfile()`, `getMyMatches()`). Avoids the `Unit` key noise.
 */
class SingleValueCache<V>(ttlMillis: Long) {
    private val inner = SessionCache<Unit, V>(ttlMillis)
    suspend fun get(fetch: suspend () -> V): V = inner.get(Unit, fetch)
    suspend fun invalidate() = inner.invalidate()
}
