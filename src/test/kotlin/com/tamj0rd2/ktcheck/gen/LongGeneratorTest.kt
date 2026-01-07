package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.gen.Gen.Companion.samples
import com.tamj0rd2.ktcheck.gen.GenTests.Companion.generateWithShrunkValues
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

class LongGeneratorTest {
    @Nested
    inner class Generation {
        @TestFactory
        fun `can generate a long within a range`(): List<DynamicTest> {
            val testCases = mapOf(
                "0 to 0" to 0L..0L,
                "1 to 1" to 1L..1L,
                "-1 to -1" to -1L..-1L,
                "max to max" to Long.MAX_VALUE..Long.MAX_VALUE,
                "min to min" to Long.MIN_VALUE..Long.MIN_VALUE,
                "positive range" to 10L..20L,
                "negative range" to -20L..-10L,
                "mixed range" to -10L..10L,
                "full long range" to Long.MIN_VALUE..Long.MAX_VALUE,
            )

            return testCases.map { (desc, range) ->
                DynamicTest.dynamicTest(desc) {
                    Gen.long(range)
                        .samples()
                        .take(10000)
                        .forEach { expectThat(it).isIn(range) }
                }
            }
        }

        @Test
        fun `generates both positive and negative longs over multiple runs`() {
            withCounter {
                Gen.long(-100L..100L).samples().take(10000).forEach { value ->
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
            val gen = Gen.long(-1000L..1000L)
            val firstRun = gen.samples(seed).take(100).toList()
            val secondRun = gen.samples(seed).take(100).toList()
            expectThat(secondRun).isEqualTo(firstRun)
        }
    }

    @Nested
    inner class Shrinking {
        @Test
        fun `10 shrinks correctly`() {
            val gen = Gen.long(0L..10L)
            val tree = ProducerTree.new().withValue(10L)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(10L)
            expectThat(shrunkValues).isEqualTo(listOf(0L, 5L, 8L, 9L))
        }

        @Test
        fun `-10 shrinks correctly`() {
            val gen = Gen.long(-10L..0L)
            val tree = ProducerTree.new().withValue(-10L)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(-10L)
            expectThat(shrunkValues).isEqualTo(listOf(0L, -5L, -8L, -9L))
        }

        @Test
        fun `shrinking zero produces no shrinks`() {
            val tree = ProducerTree.new().withValue(0L)
            val (originalValue, shrinks) = Gen.long().generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(0L)
            expectThat(shrinks).isEmpty()
        }

        @Test
        fun `shrinks for non-zero numbers always include 0`() {
            val gen = Gen.long()

            Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                .filter { (originalValue) -> originalValue != 0L }
                .take(100)
                .forEach { (_, shrunkValues) -> expectThat(shrunkValues).isNotEmpty().contains(0L) }
        }

        @Test
        fun `the original generated number is not included in shrinks`() {
            val gen = Gen.long()

            Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                .take(100)
                .forEach { (originalValue, shrunkValues) ->
                    expectThat(shrunkValues).isNotEmpty().doesNotContain(originalValue)
                }
        }

        @Test
        fun `when 0 is in range, shrinks are closer to 0 than the original generated number`() {
            val gen = Gen.long(-50L..50L)

            withCounter {
                Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                    .filter { (originalValue) -> originalValue != 0L }
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

