package com.pingwin.vpn

/** Service-scoped cache; never caches socket ownership or a failed package lookup. */
internal class UidPackageCache(
    private val now: () -> Long,
    private val lookup: (Int) -> List<String>,
    private val lifetimeMillis: Long = 5_000L,
    private val capacity: Int = 128
) {
    private data class Entry(val expires: Long, val packages: List<String>)
    private val entries = LinkedHashMap<Int, Entry>()

    @Synchronized
    fun packages(uid: Int): List<String> {
        val time = now()
        entries[uid]?.takeIf { time < it.expires }?.let { return it.packages }
        entries.remove(uid)
        val packages = lookup(uid).toList()
        if (packages.isNotEmpty()) {
            if (entries.size >= capacity) entries.remove(entries.keys.first())
            entries[uid] = Entry(time + lifetimeMillis, packages)
        }
        return packages
    }
}
