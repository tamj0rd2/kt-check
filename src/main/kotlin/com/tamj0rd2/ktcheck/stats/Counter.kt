package com.tamj0rd2.ktcheck.stats

import kotlin.math.roundToInt

class Counter {
    private val recorded = mutableMapOf<String?, Map<Any?, Int>>()

    fun collect(value: Any?) = collect(null, value)

    fun collect(label: String?, value: Any?) {
        val existing = recorded.getOrDefault(label, emptyMap())
        val newCount = existing.getOrDefault(value, 0) + 1
        recorded[label] = existing + (value to newCount)
    }

    fun checkPercentages(expected: Map<Any?, Double>) = checkPercentages(null, expected)

    fun checkPercentages(label: String?, expected: Map<Any?, Double>) = apply {
        expected.forEach { (key, minPercentage) ->
            val actualPercentage =
                percentage(label, key) ?: throw AssertionError("no recorded statistics for the value '$key'")

            if (actualPercentage < minPercentage) {
                throw AssertionError(
                    "expected the recorded percentage for '$key' to be at least $minPercentage% but was $actualPercentage%"
                )
            }
        }
    }

    private fun percentage(label: String?, key: Any?): Double? {
        val recorded = recorded[label] ?: return null
        val totalStatsRecorded = recorded.values.sum()
        val count = recorded[key] ?: return null
        return (count.toDouble() / totalStatsRecorded) * 100
    }

    override fun toString(): String {
        val sections = recorded.map { (label, recorded) ->
            val maxKeyLength = recorded.keys.maxOfOrNull { it.toString().length } ?: 0
            val maxCountLength = recorded.values.maxOrNull()?.toString()?.length ?: 0

            val sortedEntries = recorded.entries.sortedByDescending { it.value }
            val heading = if (label == null) "Stats (unlabelled)" else "Stats ($label)"

            "$heading:\n" +
                    sortedEntries.joinToString("\n") { (key, count) ->
                        val totalStatsRecorded = recorded.values.sum()
                        val percentage = ((count.toDouble() / totalStatsRecorded) * 100).roundToInt()
                        "\t%-${maxKeyLength}s (%${maxCountLength}d) : %2s%%".format(key, count, percentage)
                    }

        }

        return sections.joinToString("\n\n")
    }

    companion object {
        fun withCounter(block: Counter.() -> Unit): Counter {
            return Counter().apply(block).also(::println)
        }
    }
}
