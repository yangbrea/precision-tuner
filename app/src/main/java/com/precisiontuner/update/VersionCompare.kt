package com.precisiontuner.update

/**
 * Comparison of dotted version strings ("1.1.3", "v1.1.3", "1.10.0").
 * A leading "v" and any non-numeric suffix are ignored; missing segments
 * count as zero, so "1.10" > "1.9.9".
 */
object VersionCompare {

    /** True when [latest] is strictly newer than [current]. */
    fun isNewer(latest: String, current: String): Boolean = compare(latest, current) > 0

    /** -1 / 0 / 1: [a] older / equal / newer than [b]. */
    fun compare(a: String, b: String): Int {
        val pa = parse(a)
        val pb = parse(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun parse(version: String): List<Int> =
        version.trim().removePrefix("v")
            .split('.')
            .mapNotNull { segment -> segment.takeWhile(Char::isDigit).toIntOrNull() }
}
