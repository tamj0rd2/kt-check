package com.tamj0rd2.ktcheck.stats

import kotlin.math.roundToInt

data class CountAndPercentage(val count: Int, val percentage: Double)

class Counter {
    // todo: make this thread safe eventually
    private val recorded = mutableMapOf<Any?, Int>()

    fun collect(value: Any?) {
        recorded[value] = recorded.getOrDefault(value, 0) + 1
    }

    fun asMap(): Map<Any?, CountAndPercentage> {
        val totalRecordedCount = recorded.values.sum().toDouble()
        return recorded.mapValues { (_, count) ->
            CountAndPercentage(
                count = count,
                percentage = (count / totalRecordedCount) * 100
            )
        }
    }

    override fun toString(): String = toString(null)

    internal fun toString(formattedLabel: String? = null): String {
        val countsAndPercentsByKey = asMap()

        val maxKeyLength = countsAndPercentsByKey.keys.maxOfOrNull { it.toString().length } ?: 0
        val maxCountLength = countsAndPercentsByKey.values.maxOfOrNull { it.count }?.toString()?.length ?: 0

        val formattedStats = countsAndPercentsByKey.toList()
            .sortedByDescending { it.second.count }
            .joinToString("\n") { (key, data) ->
                val count = data.count
                val percentage = data.percentage.roundToInt()
                "\t%-${maxKeyLength}s (%${maxCountLength}d) : %2s%%".format(key, count, percentage)
            }

        val heading = if (formattedLabel != null) "Stats ($formattedLabel)" else "Stats"
        return "$heading:\n$formattedStats"
    }

    fun checkPercentages(expected: Map<Any?, Double>) {
        val actual = asMap()
        expected.forEach { (value, minPercent) ->
            val recording = actual[value] ?: throw AssertionError("no recorded statistics for the value '$value'")
            val actualPercent = recording.percentage
            if (actualPercent < minPercent) {
                throw AssertionError(
                    "expected the recorded percentage for 'value1' to be at least $minPercent% but was $actualPercent%"
                )
            }
        }
    }
}


class LabelledCounter {
    private val recorded = mutableMapOf<String, Counter>()

    fun collect(label: String, value: Any?) {
        recorded[label] = get(label).also { it.collect(value) }
    }

    fun get(label: String): Counter = recorded.getOrDefault(label, Counter())

    fun checkPercentages(label: String, expected: Map<Any?, Double>) {
        val counter = recorded[label] ?: throw AssertionError("No data recorded for label '$label'")
        try {
            counter.checkPercentages(expected)
        } catch (e: AssertionError) {
            throw AssertionError("label '$label': ${e.message}")
        }
    }

    override fun toString(): String = recorded.keys.joinToString("\n\n") { label ->
        get(label).toString(label)
    }
}

fun withCounter(block: Counter.() -> Unit): Counter {
    return Counter().apply(block).also(::println)
}

fun withLabelledCounter(block: LabelledCounter.() -> Unit): LabelledCounter {
    return LabelledCounter().apply(block).also(::println)
}
