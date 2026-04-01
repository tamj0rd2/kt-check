package com.tamj0rd2.ktcheck.stats

import com.tamj0rd2.ktcheck.stats.Percentage.Companion.asPercentageOf

@ConsistentCopyVisibility
data class Percentage private constructor(val value: Double) : Comparable<Percentage> {
    override fun toString(): String = "${value * 100}%"

    override fun compareTo(other: Percentage) = value.compareTo(other.value)

    operator fun plus(other: Percentage) = Percentage(value + other.value)
    operator fun minus(other: Percentage) = Percentage(value - other.value)
    operator fun times(amount: Double) = Percentage(value * amount)

    companion object {
        val Int.percent get() = Percentage(this / 100.0)
        val Double.percent get() = Percentage(this / 100.0)
        val Double.asPercentage get() = Percentage(this)

        infix fun Number.asPercentageOf(whole: Number) = Percentage(toDouble() / whole.toDouble())
    }
}

data class CountAndPercentage(
    val count: Int,
    val percentage: Percentage,
)

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
                percentage = count asPercentageOf totalRecordedCount
            )
        }
    }

    override fun toString(): String = toString(null)

    internal fun toString(formattedLabel: String? = null): String {
        val countsAndPercentsByKey = asMap()

        val maxKeyLength = countsAndPercentsByKey.keys.maxOfOrNull { it.toString().length } ?: 0
        val maxCountLength = countsAndPercentsByKey.values.maxOfOrNull { it.count }?.toString()?.length ?: 0
        val maxDecimals = countsAndPercentsByKey.values
            .map { (it.percentage.value * 100).toString().substringAfter(".") }
            .filter { it != "0" }
            .maxOfOrNull { it.length }
            ?.coerceAtMost(2) ?: 0

        val formattedStats = countsAndPercentsByKey.toList()
            .sortedByDescending { it.second.count }
            .joinToString("\n") { (key, data) ->
                val formattedKey = key.toString().padEnd(maxKeyLength)
                val formattedCount = data.count.toString().padStart(maxCountLength)
                val formattedPercentage =
                    "%.${maxDecimals}f".format(data.percentage.value * 100).padStart(maxDecimals + 3)
                "\t$formattedKey ($formattedCount) : $formattedPercentage%"
            }
        val heading = if (formattedLabel != null) "Stats ($formattedLabel)" else "Stats"
        return "$heading:\n$formattedStats"
    }

    fun checkPercentages(expected: Map<Any?, Percentage>) {
        val actual = asMap()
        expected.forEach { (value, minPercent) ->
            val recording = actual[value] ?: throw AssertionError("no recorded statistics for the value '$value'")
            val actualPercent = recording.percentage
            if (actualPercent < minPercent) {
                throw AssertionError(
                    "expected the recorded percentage for 'value1' to be at least $minPercent but was $actualPercent"
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

    fun checkPercentages(label: String, expected: Map<Any?, Percentage>) {
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
