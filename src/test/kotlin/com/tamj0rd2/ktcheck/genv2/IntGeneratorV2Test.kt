package com.tamj0rd2.ktcheck.genv2

import com.tamj0rd2.ktcheck.gen.Gen
import com.tamj0rd2.ktcheck.gen.filter
import com.tamj0rd2.ktcheck.gen.int
import com.tamj0rd2.ktcheck.gen.plus
import com.tamj0rd2.ktcheck.genv2.Gen.Companion.samples
import com.tamj0rd2.ktcheck.stats.Counter.Companion.withCounter
import com.tamj0rd2.ktcheck.testing.checkAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.doesNotContain
import strikt.assertions.isEqualTo
import strikt.assertions.isIn
import strikt.assertions.isLessThan
import strikt.assertions.isNotEmpty
import kotlin.math.abs

class IntGeneratorV2Test {
    @Nested
    inner class Generation {
        @TestFactory
        fun `can generate an integer within a range`(): List<DynamicTest> {
            val testCases = mapOf(
                "0 to 0" to 0..0,
                "1 to 1" to 1..1,
                "-1 to -1" to -1..-1,
                "max to max" to Int.MAX_VALUE..Int.MAX_VALUE,
                "min to min" to Int.MIN_VALUE..Int.MIN_VALUE,
                "positive range" to 10..20,
                "negative range" to -20..-10,
                "mixed range" to -10..10,
                "full int range" to Int.MIN_VALUE..Int.MAX_VALUE,
            )

            return testCases.map { (desc, range) ->
                DynamicTest.dynamicTest(desc) {
                    GenV2.int(range)
                        .samples()
                        .take(10000)
                        .forEach { expectThat(it).isIn(range) }
                }
            }
        }

        @Test
        fun `generates both positive and negative integers over multiple runs`() {
            withCounter {
                GenV2.int(-100..100).samples().take(10000).forEach { value ->
                    collect(
                        when {
                            value > 0 -> "positive"
                            value < 0 -> "negative"
                            else -> "zero"
                        }
                    )
                }
            }.checkPercentages(
                mapOf(
                    "positive" to 45.0,
                    "negative" to 45.0,
                    "zero" to 0.2
                )
            )
        }

        @Test
        fun `using the same seed generates the same values`() {
            val seed = 12345L
            val gen = GenV2.int(-1000..1000)
            val firstRun = gen.samples(seed).take(100).toList()
            val secondRun = gen.samples(seed).take(100).toList()
            expectThat(secondRun).isEqualTo(firstRun)
        }
    }

    @Nested
    inner class Shrinking {
        private fun toGenUnderTest(range: IntRange): Pair<Int, List<Int>> {
            val gen = GenV2.int(range)
            val (originalValue, shrinks) = gen.generate(ChoiceTree(0))
            return originalValue to shrinks.map { gen.generate(it).value }.toList()
        }

        @Test
        fun `shrinks appropriately for a fixed seed`() {
            val gen = GenV2.int(0..10)
            val (originalValue, shrinks) = gen.generate(ChoiceTree(0))
            expectThat(originalValue).isEqualTo(10)

            val shrunkValues = shrinks.map { gen.generate(it).value }.toList()
            expectThat(shrunkValues).isEqualTo(listOf(0, 5, 8, 9))
        }

        @Test
        fun `the original generated number is not included in shrinks`() {
            val gen = (Gen.int(Int.MIN_VALUE..-1) + Gen.int(1..Int.MAX_VALUE))
                .map { (min, max) -> toGenUnderTest(min..max) }

            checkAll(gen) { (originalValue, shrunkValues) ->
                expectThat(shrunkValues.toSet()).doesNotContain(originalValue)
            }
        }

        @Test
        fun `shrinking - when 0 is in range, shrinks get closer to 0 than the original generated number`() {
            val gen = (Gen.int(Int.MIN_VALUE..-1) + Gen.int(1..Int.MAX_VALUE))
                .filter { (min, max) -> min != max }
                .map { (min, max) -> toGenUnderTest(min..max) }
                .filter { (originalValue) -> originalValue != 0 }

            checkAll(gen) { (originalValue, shrunkValues) ->
                expectThat(shrunkValues).isNotEmpty()
                shrunkValues.forEach {
                    expectThat(it).get { abs(this) }.describedAs("distance from 0").isLessThan(abs(originalValue))
                }
            }
        }
    }

    // shrink sequence does not produce duplicates
    // shrink sequence is finite

    // Edge case tests
    // handles maximum integer without overflow
    // handles minimum integer without underflow
    // generates valid integers at type boundaries

    // Performance tests
    // generates values efficiently
    // shrink sequence is lazy and does not compute all shrinks upfront
}
