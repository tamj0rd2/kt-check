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

class ShortGeneratorTest {
    @Nested
    inner class Generation {
        @TestFactory
        fun `can generate a short within a range`(): List<DynamicTest> {
            val testCases = mapOf(
                "0 to 0" to (0..0),
                "1 to 1" to (1..1),
                "-1 to -1" to (-1..-1),
                "max to max" to (Short.MAX_VALUE.toInt()..Short.MAX_VALUE.toInt()),
                "min to min" to (Short.MIN_VALUE.toInt()..Short.MIN_VALUE.toInt()),
                "positive range" to (10..20),
                "negative range" to (-20..-10),
                "mixed range" to (-10..10),
                "large range" to (1000..2000),
                "full short range" to (Short.MIN_VALUE.toInt()..Short.MAX_VALUE.toInt()),
            )

            return testCases.map { (desc, range) ->
                DynamicTest.dynamicTest(desc) {
                    Gen.short(range)
                        .samples()
                        .take(10000)
                        .forEach { expectThat(it.toInt()).isIn(range) }
                }
            }
        }

        @Test
        fun `generates both positive and negative shorts over multiple runs`() {
            withCounter {
                Gen.short(-100..100).samples().take(10000).forEach { value ->
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
            val gen = Gen.short(-1000..1000)
            val firstRun = gen.samples(seed).take(100).toList()
            val secondRun = gen.samples(seed).take(100).toList()
            expectThat(secondRun).isEqualTo(firstRun)
        }
    }

    @Nested
    inner class Shrinking {
        @Test
        fun `10 shrinks correctly`() {
            val gen = Gen.short(0..10)
            val tree = ProducerTree.new().withValue(10.toShort())

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(10.toShort())
            expectThat(shrunkValues).isEqualTo(listOf(0.toShort(), 5.toShort(), 8.toShort(), 9.toShort()))
        }

        @Test
        fun `-10 shrinks correctly`() {
            val gen = Gen.short(-10..0)
            val tree = ProducerTree.new().withValue((-10).toShort())

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo((-10).toShort())
            expectThat(shrunkValues).isEqualTo(listOf(0.toShort(), (-5).toShort(), (-8).toShort(), (-9).toShort()))
        }

        @Test
        fun `shrinking zero produces no shrinks`() {
            val tree = ProducerTree.new().withValue(0.toShort())
            val (originalValue, shrinks) = Gen.short().generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(0.toShort())
            expectThat(shrinks).isEmpty()
        }

        @Test
        fun `shrinks for non-zero numbers always include 0`() {
            val gen = Gen.short()

            Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                .filter { (originalValue) -> originalValue != 0.toShort() }
                .take(100)
                .forEach { (_, shrunkValues) -> expectThat(shrunkValues).isNotEmpty().contains(0.toShort()) }
        }

        @Test
        fun `the original generated number is not included in shrinks`() {
            val gen = Gen.short()

            Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                .take(100)
                .forEach { (originalValue, shrunkValues) ->
                    expectThat(shrunkValues).doesNotContain(originalValue)
                }
        }

        @Test
        fun `when 0 is in range, shrinks are closer to 0 than the original generated number`() {
            val gen = Gen.short(-50..50)

            withCounter {
                Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                    .filter { (originalValue) -> originalValue != 0.toShort() }
                    .take(100)
                    .forEach { (originalValue, shrunkValues) ->
                        collect("positive", originalValue > 0)

                        expectThat(shrunkValues)
                            .isNotEmpty()
                            .doesNotContain(originalValue)
                            .all {
                                get { abs(this.toInt()) }.describedAs("shrunk distance from 0")
                                    .isLessThan(abs(originalValue.toInt()))
                            }
                    }
            }
        }

        @Test
        fun `shrinks toward range start when 0 is not in range`() {
            val gen = Gen.short(100..200)
            val tree = ProducerTree.new().withValue(200.toShort())

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(200.toShort())
            // Should shrink toward 100 (range start)
            expectThat(shrunkValues).isEqualTo(
                listOf(
                    100.toShort(),
                    150.toShort(),
                    175.toShort(),
                    188.toShort(),
                    194.toShort(),
                    197.toShort(),
                    199.toShort()
                )
            )
        }

        @Test
        fun `shrinks large values correctly`() {
            val gen = Gen.short(0..10000)
            val largeValue = 1000.toShort()
            val tree = ProducerTree.new().withValue(largeValue)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(largeValue)

            // Should start with 0
            expectThat(shrunkValues).isNotEmpty()
            expectThat(shrunkValues.first()).isEqualTo(0.toShort())

            // All shrinks should be less than original (in absolute value)
            expectThat(shrunkValues).all {
                get { abs(this.toInt()) }.isLessThan(abs(largeValue.toInt()))
            }
        }
    }
}

