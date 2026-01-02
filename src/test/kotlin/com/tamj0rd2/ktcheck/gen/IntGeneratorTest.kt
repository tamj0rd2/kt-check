package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.gen.Gen.Companion.samples
import com.tamj0rd2.ktcheck.gen.ListGeneratorTest.Companion.generateAllIncludingShrinks
import com.tamj0rd2.ktcheck.producer.ProducerTree
import com.tamj0rd2.ktcheck.stats.Counter.Companion.withCounter
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.contains
import strikt.assertions.doesNotContain
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isIn
import strikt.assertions.isLessThan
import strikt.assertions.isNotEmpty
import kotlin.math.abs
import kotlin.random.Random

class IntGeneratorTest {
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
                    Gen.int(range)
                        .samples()
                        .take(10000)
                        .forEach { expectThat(it).isIn(range) }
                }
            }
        }

        @Test
        fun `generates both positive and negative integers over multiple runs`() {
            withCounter {
                Gen.int(-100..100).samples().take(10000).forEach { value ->
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
            val gen = Gen.int(-1000..1000)
            val firstRun = gen.samples(seed).take(100).toList()
            val secondRun = gen.samples(seed).take(100).toList()
            expectThat(secondRun).isEqualTo(firstRun)
        }
    }

    @Nested
    inner class Shrinking {
        @Test
        fun `shrinks appropriately for a fixed seed`() {
            val gen = Gen.int(0..10)
            val (originalValue, shrinks) = gen.generate(ProducerTree.fromSeed(0))
            expectThat(originalValue).isEqualTo(10)

            val shrunkValues = shrinks.map { gen.generate(it).value }.toList()
            expectThat(shrunkValues).isEqualTo(listOf(0, 5, 8, 9))
        }

        @Test
        fun `shrinking zero produces no shrinks`() {
            val (originalValue, shrinks) = Gen.int().generateAllIncludingShrinks(ProducerTree.fromSeed(0).withValue(0))
            expectThat(originalValue).isEqualTo(0)
            expectThat(shrinks).isEmpty()
        }

        @Test
        fun `shrinks for non-zero numbers always include 0`() {
            val gen = Gen.int()

            generateSequence { ProducerTree.fromSeed(Random.nextLong()) }
                .map { gen.generateAllIncludingShrinks(it) }
                .filter { (originalValue) -> originalValue != 0 }
                .take(100)
                .forEach { (_, shrunkValues) -> expectThat(shrunkValues).isNotEmpty().contains(0) }
        }

        @Test
        fun `the original generated number is not included in shrinks`() {
            val gen = Gen.int()

            generateSequence { ProducerTree.fromSeed(Random.nextLong()) }
                .map { gen.generateAllIncludingShrinks(it) }
                .take(100)
                .forEach { (originalValue, shrunkValues) ->
                    expectThat(shrunkValues).isNotEmpty().doesNotContain(originalValue)
                }
        }

        @Test
        fun `when 0 is in range, shrinks are closer to 0 than the original generated number`() {
            val gen = Gen.int(-50..50)

            withCounter {
                generateSequence { ProducerTree.fromSeed(Random.nextLong()) }
                    .map { gen.generateAllIncludingShrinks(it) }
                    .filter { (originalValue) -> originalValue != 0 }
                    .take(100)
                    .forEach { (originalValue, shrunkValues) ->
                        collect("positive", originalValue > 0)

                        expectThat(shrunkValues)
                            .isNotEmpty()
                            .doesNotContain(originalValue)
                            .all {
                                get { abs(this) }.describedAs("shrunk distance from 0").isLessThan(abs(originalValue))
                            }
                    }
            }
        }
    }
}
