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

class UIntGeneratorTest {
    @Nested
    inner class Generation {
        @TestFactory
        fun `can generate a uint within a range`(): List<DynamicTest> {
            val testCases = mapOf(
                "0 to 0" to 0u..0u,
                "1 to 1" to 1u..1u,
                "max to max" to UInt.MAX_VALUE..UInt.MAX_VALUE,
                "min to min" to UInt.MIN_VALUE..UInt.MIN_VALUE,
                "small range" to 10u..20u,
                "large range" to 1000u..2000u,
                "full uint range" to UInt.MIN_VALUE..UInt.MAX_VALUE,
            )

            return testCases.map { (desc, range) ->
                DynamicTest.dynamicTest(desc) {
                    Gen.uInt(range)
                        .samples()
                        .take(10000)
                        .forEach { expectThat(it).isIn(range) }
                }
            }
        }

        @Test
        fun `generates various uints over multiple runs`() {
            withCounter {
                Gen.uInt(0u..100u).samples().take(10000).forEach { value ->
                    collect(
                        when {
                            value == 0u -> "zero"
                            value < 50u -> "low"
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
            val gen = Gen.uInt(0u..1000u)
            val firstRun = gen.samples(seed).take(100).toList()
            val secondRun = gen.samples(seed).take(100).toList()
            expectThat(secondRun).isEqualTo(firstRun)
        }
    }

    @Nested
    inner class Shrinking {
        @Test
        fun `10 shrinks correctly`() {
            val gen = Gen.uInt(0u..10u)
            val tree = ProducerTree.new().withValue(10u)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(10u)
            expectThat(shrunkValues).isEqualTo(listOf(0u, 5u, 8u, 9u))
        }

        @Test
        fun `shrinking zero produces no shrinks`() {
            val tree = ProducerTree.new().withValue(0u)
            val (originalValue, shrinks) = Gen.uInt().generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(0u)
            expectThat(shrinks).isEmpty()
        }

        @Test
        fun `shrinks for non-zero numbers always include 0`() {
            val gen = Gen.uInt(0u..1000u)

            Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                .filter { (originalValue) -> originalValue != 0u }
                .take(100)
                .forEach { (_, shrunkValues) -> expectThat(shrunkValues).isNotEmpty().contains(0u) }
        }

        @Test
        fun `the original generated number is not included in shrinks`() {
            val gen = Gen.uInt(0u..100u)

            Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                .take(100)
                .forEach { (originalValue, shrunkValues) ->
                    expectThat(shrunkValues).doesNotContain(originalValue)
                }
        }

        @Test
        fun `when 0 is in range, shrinks are closer to 0 than the original generated number`() {
            val gen = Gen.uInt(0u..50u)

            withCounter {
                Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                    .filter { (originalValue) -> originalValue != 0u }
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
            val gen = Gen.uInt(100u..200u)
            val tree = ProducerTree.new().withValue(200u)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(200u)
            // Should shrink toward 100u (range start)
            expectThat(shrunkValues).isEqualTo(listOf(100u, 150u, 175u, 188u, 194u, 197u, 199u))
        }
    }
}

