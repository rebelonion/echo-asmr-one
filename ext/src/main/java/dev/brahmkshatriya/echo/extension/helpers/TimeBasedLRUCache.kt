package dev.brahmkshatriya.echo.extension.helpers

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date

/**
 * A generic time-based LRU (Least Recently Used) cache with a maximum size limit.
 * Items are automatically evicted when the capacity is exceeded, removing the least recently accessed item.
 *
 * @param T The type of values stored in the cache
 * @param maxSize Maximum number of entries the cache can hold before evicting older entries
 */
class TimeBasedLRUCache<T : Any>(private val maxSize: Int) {
    private val cache = mutableMapOf<String, Pair<Date, T>>()
    private val mutex = Mutex()

    /**
     * Gets an item from the cache if it exists, updating its access time.
     *
     * @param key The cache key
     * @return The cached value, or null if not found
     */
    suspend fun get(key: String): T? {
        return mutex.withLock {
            cache[key]?.let { (_, value) ->
                // Update access time
                cache[key] = Pair(Date(), value)
                value
            }
        }
    }

    /**
     * Puts an item in the cache, evicting the oldest item if necessary.
     *
     * @param key The cache key
     * @param value The value to cache
     */
    suspend fun put(key: String, value: T) {
        mutex.withLock {
            cache[key] = Pair(Date(), value)

            // Evict oldest if over capacity
            if (cache.size > maxSize) {
                val oldest = cache.minByOrNull { it.value.first.time }!!.key
                cache.remove(oldest)
            }
        }
    }

    /**
     * Checks if the cache contains the specified key.
     *
     * @param key The cache key to check
     * @return True if the key exists in the cache
     */
    suspend fun contains(key: String): Boolean {
        return mutex.withLock {
            cache.containsKey(key)
        }
    }

    /**
     * Removes an item from the cache.
     *
     * @param key The cache key to remove
     * @return The removed value, or null if not found
     */
    suspend fun remove(key: String): T? {
        return mutex.withLock {
            cache.remove(key)?.second
        }
    }

    /**
     * Clears all items from the cache.
     */
    suspend fun clear() {
        mutex.withLock {
            cache.clear()
        }
    }

    /**
     * Returns the current size of the cache.
     */
    suspend fun size(): Int {
        return mutex.withLock {
            cache.size
        }
    }
}