package dev.jdtech.jellyfin.jellyseerr

/**
 * A small thread-safe in-memory cache that keeps the [maxEntries] most recently used entries.
 * Plain JVM code (no `android.util.LruCache`) so unit tests can use it.
 */
class JellyseerrLruCache<K : Any, V : Any>(private val maxEntries: Int) {
    private val entries =
        object : LinkedHashMap<K, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                return size > maxEntries
            }
        }

    @Synchronized operator fun get(key: K): V? = entries[key]

    @Synchronized operator fun set(key: K, value: V) {
        entries[key] = value
    }

    @Synchronized fun clear() {
        entries.clear()
    }
}
