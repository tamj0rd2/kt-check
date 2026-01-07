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

class ByteGeneratorTest {
    @Nested
    inner class Generation {
        @TestFactory
        fun `can generate a byte within a range`(): List<DynamicTest> {
            val testCases = mapOf(
                "0 to 0" to (0..0),
                "1 to 1" to (1..1),
                "-1 to -1" to (-1..-1),
                "max to max" to (Byte.MAX_VALUE.toInt()..Byte.MAX_VALUE.toInt()),
                "min to min" to (Byte.MIN_VALUE.toInt()..Byte.MIN_VALUE.toInt()),
                "positive range" to (10..20),
                "negative range" to (-20..-10),
                "mixed range" to (-10..10),
                "full byte range" to (Byte.MIN_VALUE.toInt()..Byte.MAX_VALUE.toInt()),
            )

            return testCases.map { (desc, range) ->
                DynamicTest.dynamicTest(desc) {
                    Gen.byte(range)
                        .samples()
                        .take(10000)
                        .forEach { expectThat(it.toInt()).isIn(range) }
                }
            }
        }

        @Test
        fun `generates both positive and negative bytes over multiple runs`() {
            withCounter {
                Gen.byte(-100..100).samples().take(10000).forEach { value ->
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
            val gen = Gen.byte(-100..100)
            val firstRun = gen.samples(seed).take(100).toList()
            val secondRun = gen.samples(seed).take(100).toList()
            expectThat(secondRun).isEqualTo(firstRun)
        }
    }

    @Nested
    inner class Shrinking {
        @Test
        fun `10 shrinks correctly`() {
            val gen = Gen.byte(0..10)
            val tree = ProducerTree.new().withValue(10.toByte())

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(10.toByte())
            expectThat(shrunkValues).isEqualTo(listOf(0.toByte(), 5.toByte(), 8.toByte(), 9.toByte()))
        }

        @Test
        fun `-10 shrinks correctly`() {
            val gen = Gen.byte(-10..0)
            val tree = ProducerTree.new().withValue((-10).toByte())

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo((-10).toByte())
            expectThat(shrunkValues).isEqualTo(listOf(0.toByte(), (-5).toByte(), (-8).toByte(), (-9).toByte()))
        }

        @Test
        fun `shrinking zero produces no shrinks`() {
            val tree = ProducerTree.new().withValue(0.toByte())
            val (originalValue, shrinks) = Gen.byte().generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(0.toByte())
            expectThat(shrinks).isEmpty()
        }

        @Test
        fun `shrinks for non-zero numbers always include 0`() {
            val gen = Gen.byte()

            Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                .filter { (originalValue) -> originalValue != 0.toByte() }
                .take(100)
                .forEach { (_, shrunkValues) -> expectThat(shrunkValues).isNotEmpty().contains(0.toByte()) }
        }

        @Test
        fun `the original generated number is not included in shrinks`() {
            val gen = Gen.byte()

            Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                .take(100)
                .forEach { (originalValue, shrunkValues) ->
                    expectThat(shrunkValues).doesNotContain(originalValue)
                }
        }

        @Test
        fun `when 0 is in range, shrinks are closer to 0 than the original generated number`() {
            val gen = Gen.byte(-50..50)

            withCounter {
                Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                    .filter { (originalValue) -> originalValue != 0.toByte() }
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
            val gen = Gen.byte(10..20)
            val tree = ProducerTree.new().withValue(20.toByte())

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(20.toByte())
            // Should shrink toward 10 (range start)
            expectThat(shrunkValues).isEqualTo(listOf(10.toByte(), 15.toByte(), 18.toByte(), 19.toByte()))
        }
    }
}

