package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.TestConfig
import com.tamj0rd2.ktcheck.contracts.repeatTest
import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker.shrink
import com.tamj0rd2.ktcheck.full
import com.tamj0rd2.ktcheck.incubating.Generator.Companion.TARGET_EDGE_CASE_PROBABILITY
import com.tamj0rd2.ktcheck.stats.CountAndPercentage
import com.tamj0rd2.ktcheck.stats.Counter
import com.tamj0rd2.ktcheck.stats.LabelledCounter
import com.tamj0rd2.ktcheck.stats.withCounter
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.contains
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
        val gen = Gens.int(range)

        repeatTest { seed ->
            val generatedValue = gen.generate(seed)
            expectThat(generatedValue).value.isIn(range)
        }
    }

    @Test
    fun `generated values can be shrunk`() {
        val gen = Gens.int(range = 0..10, shrinkTarget = 0)

        repeatTest { seed ->
            val original = gen.generate(seed)
            val shrinks = shrink(original.value, gen.range, gen.shrinkTarget)
            expectThat(original).shrunkValues.isEqualTo(shrinks.toList())
        }
    }

    @Test
    fun `generation is deterministic`() {
        val gen = Gens.int(0..10)

        repeatTest { seed ->
            val original = gen.generate(seed)
            val regenerated = gen.generate(seed)
            expectThat(regenerated).value.isEqualTo(original.value)
            expectThat(regenerated).shrunkValues.isEqualTo(original.shrunkValues.toList())
        }
    }

    @Test
    fun `includes default edge cases based on the range to help catch off-by-one errors`() {
        val gen = Gens.int(IntRange.full)
        expectThat(gen.edgeCases).contains(Int.MIN_VALUE, Int.MIN_VALUE + 1, -1, 0, 1, Int.MAX_VALUE - 1, Int.MAX_VALUE)
    }

    @Test
    fun `the user can inject edge cases, in addition to the defaults`() {
        val userProvidedEdgeCases = setOf(123, 456, 789)
        val gen = Gens.int(IntRange.full, 0, extraEdgeCases = userProvidedEdgeCases)
        expectThat(gen.edgeCases).contains(userProvidedEdgeCases)
        expectThat(gen.edgeCases).contains(Int.MIN_VALUE, Int.MIN_VALUE + 1, -1, 0, 1, Int.MAX_VALUE - 1, Int.MAX_VALUE)
    }

    @Test
    fun `user injected edge cases cannot fall outside of the generator's range`() {
        expectThrows<IllegalArgumentException> {
            Gens.int(range = 0..10, extraEdgeCases = setOf(11))
        }
    }

    @TestFactory
    fun `generated values include all edge cases most of the time`(): List<DynamicTest> =
        interestingRanges.map { range ->
            DynamicTest.dynamicTest(range.toString()) {
                val gen = Gens.int(range = range, extraEdgeCases = setOf(3))
                val expectedEdgeCases = gen.edgeCases
                expectThat(expectedEdgeCases).isNotEmpty()

                val counter = withCounter {
                    repeatTest { seed ->
                        val values = gen.samples(seed.value).take(TestConfig.DEFAULT_ITERATIONS)
                        val seenEdgeCases = values.toSet().filter { it in expectedEdgeCases }

                        collect(
                            when (seenEdgeCases.size) {
                                0 -> "none"
                                expectedEdgeCases.size -> "all"
                                else -> "some"
                            }
                        )
                    }
                }

                expectThat(counter) {
                    didNotRecord("none")
                    didRecord("all").percentage.isGreaterThanOrEqualTo(75.0)
                }
            }
        }

    @TestFactory
    fun `the proportion of generated edge cases is not influenced for small ranges`(): List<DynamicTest> =
        interestingRanges
            .map(Gens::int)
            .filter { it.edgeCases.size / it.range.size.toDouble() > TARGET_EDGE_CASE_PROBABILITY }
            .map { gen ->
                DynamicTest.dynamicTest(gen.range.toString()) {
                    val edgeCaseProbability = gen.edgeCases.size / gen.range.size.toDouble()
                    val measuredEdgeCaseProportions = mutableListOf<Double>()

                    repeatTest { seed ->
                        val valueCounts = gen
                            .samples(seed.value)
                            .take(TestConfig.DEFAULT_ITERATIONS)
                            .groupingBy { it }
                            .eachCount()

                        val totalValues = valueCounts.values.sum()
                        val totalEdgeCases = valueCounts.filter { it.key in gen.edgeCases }.values.sum()

                        val proportion = (totalEdgeCases.toDouble() / totalValues) * 100
                        expectThat(proportion).isGreaterThan(0.0)
                        measuredEdgeCaseProportions.add(proportion)
                    }

                    val floor = (edgeCaseProbability - 0.04) * 100
                    val ceiling = (edgeCaseProbability + 0.04) * 100
                    expectThat(measuredEdgeCaseProportions.average()).isIn(floor..ceiling)
                }
            }

    @TestFactory
    fun `the proportion of generated edge cases is around 3 percent for large ranges`(): List<DynamicTest> =
        interestingRanges
            .map(Gens::int)
            .filter { it.edgeCases.size / it.range.size.toDouble() <= TARGET_EDGE_CASE_PROBABILITY }
            .map { gen ->
                DynamicTest.dynamicTest(gen.range.toString()) {
                    val measuredEdgeCaseProportions = mutableListOf<Double>()

                    repeatTest { seed ->
                        val valueCounts = gen
                            .samples(seed.value)
                            .take(TestConfig.DEFAULT_ITERATIONS)
                            .groupingBy { it }
                            .eachCount()

                        val totalValues = valueCounts.values.sum()
                        val totalEdgeCases = valueCounts.filter { it.key in gen.edgeCases }.values.sum()

                        val proportion = (totalEdgeCases.toDouble() / totalValues) * 100
                        expectThat(proportion).isGreaterThan(0.0)
                        measuredEdgeCaseProportions.add(proportion)
                    }

                    val floor = targetEdgeCaseProportion - 4.0
                    val ceiling = targetEdgeCaseProportion + 4.0
                    expectThat(measuredEdgeCaseProportions.average()).isIn(floor..ceiling)
                }
            }

    private val targetEdgeCaseProportion = TARGET_EDGE_CASE_PROBABILITY * 100

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
