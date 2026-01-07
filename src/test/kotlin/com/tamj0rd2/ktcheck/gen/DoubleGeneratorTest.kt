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

class DoubleGeneratorTest {
    @Nested
    inner class Generation {
        @TestFactory
        fun `can generate a double within a range`(): List<DynamicTest> {
            val testCases = mapOf(
                "0.0 to 0.0" to 0.0..0.0,
                "1.0 to 1.0" to 1.0..1.0,
                "-1.0 to -1.0" to -1.0..-1.0,
                "positive range" to 1.0..10.0,
                "negative range" to -10.0..-1.0,
                "mixed range" to -5.0..5.0,
                "very small range" to 0.0..0.001,
                "very large range" to -1e100..1e100,
                "full double range" to -Double.MAX_VALUE..Double.MAX_VALUE,
            )

            return testCases.map { (desc, range) ->
                DynamicTest.dynamicTest(desc) {
                    Gen.double(range)
                        .samples()
                        .take(10000)
                        .forEach {
                            expectThat(it).isIn(range)
                        }
                }
            }
        }

        @Test
        fun `generates both positive and negative doubles over multiple runs`() {
            withCounter {
                Gen.double(-100.0..100.0).samples().take(10000).forEach { value ->
                    collect(
                        when {
                            value > 0.0 -> "positive"
                            value < 0.0 -> "negative"
                            else -> "zero"
                        }
                    )
                }
            }.checkPercentages(
                mapOf(
                    "positive" to 48.0,
                    "negative" to 48.0,
                )
            )
        }

        @Test
        fun `using the same seed generates the same values`() {
            val seed = 12345L
            val gen = Gen.double(-1000.0..1000.0)
            val firstRun = gen.samples(seed).take(100).toList()
            val secondRun = gen.samples(seed).take(100).toList()
            expectThat(secondRun).isEqualTo(firstRun)
        }
    }

    @Nested
    inner class Shrinking {
        @Test
        fun `10 point 5 shrinks correctly`() {
            val gen = Gen.double(0.0..20.0)
            val tree = ProducerTree.new().withValue(10.5)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(10.5)

            // Should start with 0.0 (origin), then 10.0 (whole part), then halving
            expectThat(shrunkValues).isNotEmpty()
            expectThat(shrunkValues.first()).isEqualTo(0.0)
            expectThat(shrunkValues).contains(10.0)
        }

        @Test
        fun `-10 point 5 shrinks correctly`() {
            val gen = Gen.double(-20.0..0.0)
            val tree = ProducerTree.new().withValue(-10.5)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(-10.5)

            // Should start with 0.0 (origin), then -10.0 (whole part)
            expectThat(shrunkValues).isNotEmpty()
            expectThat(shrunkValues.first()).isEqualTo(0.0)
            expectThat(shrunkValues).contains(-10.0)
        }

        @Test
        fun `shrinking zero produces no shrinks`() {
            val tree = ProducerTree.new().withValue(0.0)
            val (originalValue, shrinks) = Gen.double().generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(0.0)
            expectThat(shrinks).isEmpty()
        }

        @Test
        fun `shrinking origin produces no shrinks`() {
            val gen = Gen.double(100.0..200.0)
            val tree = ProducerTree.new().withValue(100.0)

            val (originalValue, shrinks) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(100.0)
            expectThat(shrinks).isEmpty()
        }

        @Test
        fun `shrinks for non-zero numbers always include 0`() {
            val gen = Gen.double(-1000.0..1000.0)

            Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                .filter { (originalValue) -> originalValue != 0.0 }
                .take(100)
                .forEach { (_, shrunkValues) ->
                    expectThat(shrunkValues).isNotEmpty().contains(0.0)
                }
        }

        @Test
        fun `the original generated number is not included in shrinks`() {
            val gen = Gen.double(-100.0..100.0)

            Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                .take(100)
                .forEach { (originalValue, shrunkValues) ->
                    expectThat(shrunkValues).doesNotContain(originalValue)
                }
        }

        @Test
        fun `when 0 is in range, shrinks are closer to 0 than the original generated number`() {
            val gen = Gen.double(-50.0..50.0)

            Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                .filter { (originalValue) -> originalValue != 0.0 }
                .take(100)
                .forEach { (originalValue, shrunkValues) ->
                    expectThat(shrunkValues)
                        .isNotEmpty()
                        .doesNotContain(originalValue)
                        .all {
                            get { abs(this) }
                                .describedAs("shrunk distance from 0")
                                .isLessThan(abs(originalValue))
                        }
                }
        }

        @Test
        fun `shrinks toward range start when 0 is not in range`() {
            val gen = Gen.double(100.0..200.0)
            val tree = ProducerTree.new().withValue(200.0)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(200.0)

            // Should shrink toward 100.0 (range start, which is the origin)
            expectThat(shrunkValues).isNotEmpty()
            expectThat(shrunkValues.first()).isEqualTo(100.0)
        }


        @Test
        fun `shrinks non-whole numbers to whole number early`() {
            val gen = Gen.double(0.0..100.0)
            val tree = ProducerTree.new().withValue(5.732)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(5.732)

            // Should include both 0.0 (origin) and 5.0 (whole part)
            expectThat(shrunkValues).contains(0.0, 5.0)

            // Whole part should come early (second, after origin)
            val indexOf5 = shrunkValues.indexOf(5.0)
            expectThat(indexOf5).isEqualTo(1)
        }

        @Test
        fun `whole number does not yield duplicate whole part`() {
            val gen = Gen.double(0.0..100.0)
            val tree = ProducerTree.new().withValue(5.0)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(5.0)

            // Should not have duplicate 5.0 in shrinks
            val count5 = shrunkValues.count { it == 5.0 }
            expectThat(count5).isEqualTo(0)
        }

        @Test
        fun `very large double handles toLong overflow gracefully`() {
            val gen = Gen.double(0.0..Double.MAX_VALUE)
            val largeValue = 1e100 // Much larger than Long.MAX_VALUE
            val tree = ProducerTree.new().withValue(largeValue)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(largeValue)

            // Should still shrink (toward 0), even though can't convert to whole number
            expectThat(shrunkValues).isNotEmpty()
            expectThat(shrunkValues.first()).isEqualTo(0.0)
        }
    }
}

