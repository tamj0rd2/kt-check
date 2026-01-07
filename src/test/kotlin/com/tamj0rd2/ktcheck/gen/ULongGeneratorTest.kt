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

class ULongGeneratorTest {
    @Nested
    inner class Generation {
        @TestFactory
        fun `can generate a ulong within a range`(): List<DynamicTest> {
            val testCases = mapOf(
                "0 to 0" to 0uL..0uL,
                "1 to 1" to 1uL..1uL,
                "max to max" to ULong.MAX_VALUE..ULong.MAX_VALUE,
                "min to min" to ULong.MIN_VALUE..ULong.MIN_VALUE,
                "small range" to 10uL..20uL,
                "large range" to 1000uL..2000uL,
                "very large range" to 1000000000uL..2000000000uL,
                "full ulong range" to ULong.MIN_VALUE..ULong.MAX_VALUE,
            )

            return testCases.map { (desc, range) ->
                DynamicTest.dynamicTest(desc) {
                    Gen.uLong(range)
                        .samples()
                        .take(10000)
                        .forEach { expectThat(it).isIn(range) }
                }
            }
        }

        @Test
        fun `generates various ulongs over multiple runs`() {
            withCounter {
                Gen.uLong(0uL..100uL).samples().take(10000).forEach { value ->
                    collect(
                        when {
                            value == 0uL -> "zero"
                            value < 50uL -> "low"
                            else -> "high"
                        }
                    )
                }
            }.checkPercentages(
                mapOf(
                    "low" to 45.0,
                    "high" to 45.0,
                    "zero" to 0.2
                )
            )
        }

        @Test
        fun `using the same seed generates the same values`() {
            val seed = 12345L
            val gen = Gen.uLong(0uL..1000uL)
            val firstRun = gen.samples(seed).take(100).toList()
            val secondRun = gen.samples(seed).take(100).toList()
            expectThat(secondRun).isEqualTo(firstRun)
        }
    }

    @Nested
    inner class Shrinking {
        @Test
        fun `10 shrinks correctly`() {
            val gen = Gen.uLong(0uL..10uL)
            val tree = ProducerTree.new().withValue(10uL)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(10uL)
            expectThat(shrunkValues).isEqualTo(listOf(0uL, 5uL, 8uL, 9uL))
        }

        @Test
        fun `shrinking zero produces no shrinks`() {
            val tree = ProducerTree.new().withValue(0uL)
            val (originalValue, shrinks) = Gen.uLong().generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(0uL)
            expectThat(shrinks).isEmpty()
        }

        @Test
        fun `shrinks for non-zero numbers always include 0`() {
            val gen = Gen.uLong(0uL..1000uL)

            Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                .filter { (originalValue) -> originalValue != 0uL }
                .take(100)
                .forEach { (_, shrunkValues) -> expectThat(shrunkValues).isNotEmpty().contains(0uL) }
        }

        @Test
        fun `the original generated number is not included in shrinks`() {
            val gen = Gen.uLong(0uL..100uL)

            Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                .take(100)
                .forEach { (originalValue, shrunkValues) ->
                    expectThat(shrunkValues).doesNotContain(originalValue)
                }
        }

        @Test
        fun `when 0 is in range, shrinks are closer to 0 than the original generated number`() {
            val gen = Gen.uLong(0uL..50uL)

            withCounter {
                Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                    .filter { (originalValue) -> originalValue != 0uL }
                    .take(100)
                    .forEach { (originalValue, shrunkValues) ->
                        expectThat(shrunkValues)
                            .isNotEmpty()
                            .doesNotContain(originalValue)
                            .all { isLessThan(originalValue) }
                    }
            }
        }

        @Test
        fun `shrinks toward range start when 0 is not in range`() {
            val gen = Gen.uLong(100uL..200uL)
            val tree = ProducerTree.new().withValue(200uL)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(200uL)
            // Should shrink toward 100uL (range start)
            expectThat(shrunkValues).isEqualTo(listOf(100uL, 150uL, 175uL, 188uL, 194uL, 197uL, 199uL))
        }

        @Test
        fun `shrinks large values correctly`() {
            val gen = Gen.uLong(0uL..ULong.MAX_VALUE)
            val largeValue = 1000000000uL
            val tree = ProducerTree.new().withValue(largeValue)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(largeValue)

            // Should start with 0
            expectThat(shrunkValues).isNotEmpty()
            expectThat(shrunkValues.first()).isEqualTo(0uL)

            // All shrinks should be less than original
            expectThat(shrunkValues).all { isLessThan(largeValue) }
        }
    }
}

