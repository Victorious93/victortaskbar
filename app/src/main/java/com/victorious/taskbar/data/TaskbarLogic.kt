package com.victorious.taskbar.data

/** Pure, unit-tested helpers (no Android types). */
object TaskbarLogic {

    fun <T> move(list: List<T>, item: T, delta: Int): List<T> {
        val from = list.indexOf(item)
        if (from < 0 || delta == 0) return list
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return list
        val out = list.toMutableList()
        out.removeAt(from)
        out.add(to, item)
        return out
    }

    /**
     * Fuzzy score, higher is better, 0 = no match.
     * label prefix > word prefix > initials ("gm" -> Google Maps) > substring > subsequence > package hit.
     */
    fun score(label: String, packageName: String, query: String): Int {
        val q = query.trim().lowercase().filterNot { it.isWhitespace() }
        if (q.isEmpty()) return 1
        val l = label.lowercase()
        val compact = l.filterNot { it.isWhitespace() }
        if (compact.startsWith(q)) return 1000 - compact.length
        val words = l.split(' ', '-', '_', '.').filter { it.isNotEmpty() }
        if (words.any { it.startsWith(q) }) return 800 - compact.length
        if (q.length >= 2 && words.joinToString("") { it.take(1) }.startsWith(q)) return 700 - compact.length
        val idx = compact.indexOf(q)
        if (idx >= 0) return 600 - idx
        if (isSubsequence(compact, q)) return 300
        if (packageName.lowercase().contains(q)) return 100
        return 0
    }

    fun <T> rank(items: List<T>, query: String, label: (T) -> String, pkg: (T) -> String): List<T> {
        if (query.isBlank()) return items
        return items.map { it to score(label(it), pkg(it), query) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<T, Int>> { it.second }.thenBy { label(it.first).lowercase() })
            .map { it.first }
    }

    private fun isSubsequence(s: String, q: String): Boolean {
        var i = 0
        for (c in s) if (i < q.length && c == q[i]) i++
        return i == q.length
    }

    /** First letter bucket for A–Z headers; digits/symbols go under "#". */
    fun bucket(label: String): Char {
        val c = label.trim().firstOrNull()?.uppercaseChar() ?: return '#'
        return if (c in 'A'..'Z') c else '#'
    }
}
