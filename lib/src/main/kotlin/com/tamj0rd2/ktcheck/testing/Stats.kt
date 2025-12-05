package com.tamj0rd2.ktcheck.testing

import kotlin.math.roundToInt

class Stats {
    private val recorded = mutableMapOf<String, Int>()
    private var totalStatsRecorded = 0

    fun collect(label: String) {
        recorded[label] = recorded.getOrDefault(label, 0) + 1
        totalStatsRecorded++
    }

    override fun toString(): String {
        val maxKeyLength = recorded.keys.maxOfOrNull { it.length } ?: 0
        val maxCountLength = recorded.values.maxOrNull()?.toString()?.length ?: 0

        val sortedEntries = recorded.entries.sortedByDescending { it.value }
        return "Stats:\n" +
            sortedEntries.joinToString("\n") { (label, count) ->
                val percentage = ((count.toDouble() / totalStatsRecorded) * 100).roundToInt()
                "\t%-${maxKeyLength}s (%${maxCountLength}d) : %2s%%".format(label, count, percentage)
            }
    }

    fun percentage(label: String): Double? {
        val count = recorded[label] ?: return null
        return (count.toDouble() / totalStatsRecorded) * 100
    }

    fun checkPercentages(expected: Map<String, Double>) {
        expected.forEach { (key, minPercentage) ->
            val actualPercentage = percentage(key) ?: throw AssertionError("no recorded statistics for '$key'")
            if (actualPercentage < minPercentage) {
                throw AssertionError(
                    "expected the recorded percentage for label '$key' to be at least $minPercentage% but was $actualPercentage%"
                )
            }
        }
    }

    companion object {
        fun withStats(block: (Stats) -> Unit): Stats {
            val stats = Stats()
            block(stats)
            println(stats)
            return stats
        }
    }
}
