package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.TestConfig
import com.tamj0rd2.ktcheck.contracts.repeatTest
import com.tamj0rd2.ktcheck.core.Seed
import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker.shrink
import com.tamj0rd2.ktcheck.full
import com.tamj0rd2.ktcheck.incubating.Gen.Companion.defaultEdgeCaseProbability
import com.tamj0rd2.ktcheck.incubating.Probability.Companion.percent
import com.tamj0rd2.ktcheck.stats.CountAndPercentage
import com.tamj0rd2.ktcheck.stats.Counter
import com.tamj0rd2.ktcheck.stats.LabelledCounter
import com.tamj0rd2.ktcheck.stats.withLabelledCounter
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import strikt.assertions.isGreaterThanOrEqualTo
import strikt.assertions.isIn
import strikt.assertions.isNotEmpty

// most important features: deterministic random generation, shrinking, default edge case providers, then finally use edge case injection

class Playground {
    @Test
    fun `generates ints within the given range`() {
        val range = 0..10
        val gen = Gen.int(range)

        repeatTest { seed ->
            val generatedValue = gen.generate(seed)
            expectThat(generatedValue).value.isIn(range)
        }
    }

    // todo: add a couple tests for different shrink targets
    @Test
    fun `generated values can be shrunk`() {
        val range = 0..10
        val gen = Gen.int(range = range, shrinkTarget = range.first)

        repeatTest { seed ->
            val original = gen.generate(seed)
            val shrinks = shrink(original.value, range, range.first)
            expectThat(original).shrunkValues.isEqualTo(shrinks.toList())
        }
    }

    @Test
    fun `generation is deterministic`() {
        val gen = Gen.int(0..10)

        repeatTest { seed ->
            val original = gen.generate(seed)
            val regenerated = gen.generate(seed)
            expectThat(regenerated).value.isEqualTo(original.value)
            expectThat(regenerated).shrunkValues.isEqualTo(original.shrunkValues.toList())
        }
    }

    @Test
    fun `includes default edge cases based on the range to help catch off-by-one errors`() {
        val gen = Gen.int(-10..10)
        expectThat(gen.edgeCases).containsExactlyInAnyOrder(-10, -9, -1, 0, 1, 9, 10)
    }

    @TestFactory
    fun `generated values include all edge cases most of the time`(): List<DynamicTest> =
        interestingRanges.map { range ->
            DynamicTest.dynamicTest(range.toString()) {
                val gen = Gen.int(range = range)
                val possibleEdgeCases = gen.edgeCases
                expectThat(possibleEdgeCases).isNotEmpty()

                val counter = withLabelledCounter {
                    repeatTest { seed ->
                        val values = gen.samples(seed.value).take(TestConfig.DEFAULT_ITERATIONS)
                        val seenEdgeCases = values.toSet().filter { it in possibleEdgeCases }

                        collect(
                            "category",
                            when (seenEdgeCases.size) {
                                0 -> "none"
                                possibleEdgeCases.size -> "all"
                                else -> "some"
                            }
                        )
                        collect("edgeCasesSeen", seenEdgeCases.sorted())
                    }
                }

                expectThat(counter).withLabel("category").and {
                    didNotRecord("none")
                    didRecord("all").percentage.isGreaterThanOrEqualTo(75.0)
                }
            }
        }

    @TestFactory
    fun `the proportion of generated edge cases is not influenced for small ranges`(): List<DynamicTest> =
        interestingRanges
            .map { it to Gen.int(it).withEdgeCaseProbability(2.percent) }
            .filter { (range, gen) -> gen.edgeCases.size / range.size.toDouble() > defaultEdgeCaseProbability.value }
            .map { (range, gen) ->
                DynamicTest.dynamicTest(range.toString()) {
                    val measuredEdgeCaseProportions = mutableListOf<Double>()
                    val possibleEdgeCases = gen.edgeCases
                    expectThat(possibleEdgeCases).isNotEmpty()

                    repeatTest { seed ->
                        val valueCounts = gen
                            .samples(seed.value)
                            .take(TestConfig.DEFAULT_ITERATIONS)
                            .groupingBy { it }
                            .eachCount()

                        val totalValues = valueCounts.values.sum()
                        val totalEdgeCases = valueCounts.filter { it.key in possibleEdgeCases }.values.sum()

                        val proportion = (totalEdgeCases.toDouble() / totalValues) * 100
                        expectThat(proportion).isGreaterThan(0.0)
                        measuredEdgeCaseProportions.add(proportion)
                    }

                    val edgeCaseProbability = Probability.of(possibleEdgeCases.size / range.size.toDouble())
                    val min = edgeCaseProbability.asPercentage - (edgeCaseProbability.asPercentage * 0.40)
                    val max = edgeCaseProbability.asPercentage + (edgeCaseProbability.asPercentage * 0.40)
                    expectThat(measuredEdgeCaseProportions.median()).isIn(min..max)
                }
            }

    @TestFactory
    fun `the proportion of generated edge cases is around 3 percent for large ranges`(): List<DynamicTest> =
        interestingRanges
            .map { it to Gen.int(it).withEdgeCaseProbability(2.percent) }
            .filterNot { (range, gen) -> gen.edgeCases.size / range.size.toDouble() > defaultEdgeCaseProbability.value }
            .map { (range, gen) ->
                DynamicTest.dynamicTest(range.toString()) {
                    val measuredEdgeCaseProportions = mutableListOf<Double>()
                    val possibleEdgeCases = gen.edgeCases
                    expectThat(possibleEdgeCases).isNotEmpty()

                    repeatTest { seed ->
                        val valueCounts = gen
                            .samples(seed.value)
                            .take(TestConfig.DEFAULT_ITERATIONS)
                            .groupingBy { it }
                            .eachCount()

                        val totalValues = valueCounts.values.sum()
                        val totalEdgeCases = valueCounts.filter { it.key in possibleEdgeCases }.values.sum()

                        val proportion = (totalEdgeCases.toDouble() / totalValues) * 100
                        expectThat(proportion).isGreaterThan(0.0)
                        measuredEdgeCaseProportions.add(proportion)
                    }

                    val min = defaultEdgeCaseProbability.asPercentage - (defaultEdgeCaseProbability.asPercentage * 0.40)
                    val max = defaultEdgeCaseProbability.asPercentage + (defaultEdgeCaseProbability.asPercentage * 0.40)
                    expectThat(measuredEdgeCaseProportions.median()).isIn(min..max)
                }
            }

    private val interestingRanges = listOf(
        -10..10,
        -100..100,
        -1_000..1_000,
        -10_000..10_000,
        -100_000..100_000,
        -1_000_000..1_000_000,
        -10_000_000..10_000_000,
        -100_000_000..100_000_000,
        -1_000_000_000..1_000_000_000,
        IntRange.full,
    )

    val <T> Gen<T>.edgeCases: Set<T>
        get() = withEdgeCaseProbability(100.percent)
            .samples(Seed.random().value)
            .take(10_000)
            .toSet()
}

private fun Assertion.Builder<LabelledCounter>.withLabel(label: String) =
    get("with label $label") { get(label) }

private fun Assertion.Builder<LabelledCounter>.didNotRecord(label: String, value: Any?) =
    get("with label $label") { get(label) }.didNotRecord(value)


private fun Assertion.Builder<Counter>.didNotRecord(value: Any?) =
    assert("did not record the value $value") {
        val recording = it.asMap()[value]
        if (recording == null) pass(recording)
        else fail(recording)
    }

@Suppress("UNCHECKED_CAST")
private fun Assertion.Builder<Counter>.didRecord(value: Any?) =
    get("value $value") { asMap()[value] }.assertThat("was recorded") { it != null } as Assertion.Builder<CountAndPercentage>

private val Assertion.Builder<CountAndPercentage>.count get() = get("count (%s)") { count }
private val Assertion.Builder<CountAndPercentage>.percentage get() = get("percentage (%s%%)") { percentage }

internal val <T> Assertion.Builder<GeneratedValue<T>>.value get() = get("value") { value }

internal val <T> GeneratedValue<T>.shrunkValues get() = shrinks.toList().map { it.value }
internal val <T> Assertion.Builder<GeneratedValue<T>>.shrunkValues get() = get("shrunk values") { shrunkValues }

private fun List<Double>.median(): Double {
    if (isEmpty()) throw IllegalArgumentException("List is empty")
    val sorted = sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[middle - 1] + sorted[middle]) / 2.0
    } else {
        sorted[middle]
    }
}
