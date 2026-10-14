package org.wxyc.wxycapp.data.bio

/**
 * A bounded, least-recently-used cache of `proxy/entity/resolve` answers. Names are kept
 * until evicted; "no such entity" is kept for [negativeTtlSeconds], matching the backend's
 * own short negative TTL so an absence caused by a cache rebuild heals.
 *
 * @param clock the current time in epoch seconds.
 */
class EntityNameCache(
    private val maxEntries: Int,
    private val negativeTtlSeconds: Long,
    private val clock: () -> Long,
) {
    /** A cached answer; [name] is null when the entity is known not to resolve. */
    data class Hit(val name: String?)

    private class Entry(val name: String?, val cachedAt: Long)

    private val entries = object : LinkedHashMap<EntityRef, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<EntityRef, Entry>) = size > maxEntries
    }

    /** The cached answer for [ref], or null when there is none or a negative one expired. */
    @Synchronized
    fun get(ref: EntityRef): Hit? {
        val entry = entries[ref] ?: return null
        if (entry.name == null && clock() - entry.cachedAt >= negativeTtlSeconds) {
            entries.remove(ref)
            return null
        }
        return Hit(entry.name)
    }

    /** Caches [name] for [ref]; null records that it does not resolve. */
    @Synchronized
    fun put(ref: EntityRef, name: String?) {
        entries[ref] = Entry(name, clock())
    }
}
