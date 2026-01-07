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

class FloatGeneratorTest {
    @Nested
    inner class Generation {
        @TestFactory
        fun `can generate a float within a range`(): List<DynamicTest> {
            val testCases = mapOf(
                "0.0 to 0.0" to 0.0f..0.0f,
                "1.0 to 1.0" to 1.0f..1.0f,
                "-1.0 to -1.0" to -1.0f..-1.0f,
                "positive range" to 1.0f..10.0f,
                "negative range" to -10.0f..-1.0f,
                "mixed range" to -5.0f..5.0f,
                "very small range" to 0.0f..0.001f,
                "very large range" to -1e20f..1e20f,
                "full float range" to -Float.MAX_VALUE..Float.MAX_VALUE,
            )

            return testCases.map { (desc, range) ->
                DynamicTest.dynamicTest(desc) {
                    Gen.float(range)
                        .samples()
                        .take(10000)
                        .forEach {
                            expectThat(it).isIn(range)
                        }
                }
            }
        }

        @Test
        fun `generates both positive and negative floats over multiple runs`() {
            withCounter {
                Gen.float(-100.0f..100.0f).samples().take(10000).forEach { value ->
                    collect(
                        when {
                            value > 0.0f -> "positive"
                            value < 0.0f -> "negative"
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
            val gen = Gen.float(-1000.0f..1000.0f)
            val firstRun = gen.samples(seed).take(100).toList()
            val secondRun = gen.samples(seed).take(100).toList()
            expectThat(secondRun).isEqualTo(firstRun)
        }
    }

    @Nested
    inner class Shrinking {
        @Test
        fun `10 point 5 shrinks correctly`() {
            val gen = Gen.float(0.0f..20.0f)
            val tree = ProducerTree.new().withValue(10.5f)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(10.5f)

            // Should start with 0.0 (origin), then 10.0 (whole part), then halving
            expectThat(shrunkValues).isNotEmpty()
            expectThat(shrunkValues.first()).isEqualTo(0.0f)
            expectThat(shrunkValues).contains(10.0f)
        }

        @Test
        fun `-10 point 5 shrinks correctly`() {
            val gen = Gen.float(-20.0f..0.0f)
            val tree = ProducerTree.new().withValue(-10.5f)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(-10.5f)

            // Should start with 0.0 (origin), then -10.0 (whole part)
            expectThat(shrunkValues).isNotEmpty()
            expectThat(shrunkValues.first()).isEqualTo(0.0f)
            expectThat(shrunkValues).contains(-10.0f)
        }

        @Test
        fun `shrinking zero produces no shrinks`() {
            val tree = ProducerTree.new().withValue(0.0f)
            val (originalValue, shrinks) = Gen.float().generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(0.0f)
            expectThat(shrinks).isEmpty()
        }

        @Test
        fun `shrinking origin produces no shrinks`() {
            val gen = Gen.float(100.0f..200.0f)
            val tree = ProducerTree.new().withValue(100.0f)

            val (originalValue, shrinks) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(100.0f)
            expectThat(shrinks).isEmpty()
        }

        @Test
        fun `shrinks for non-zero numbers always include 0`() {
            val gen = Gen.float(-1000.0f..1000.0f)

            Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                .filter { (originalValue) -> originalValue != 0.0f }
                .take(100)
                .forEach { (_, shrunkValues) ->
                    expectThat(shrunkValues).isNotEmpty().contains(0.0f)
                }
        }

        @Test
        fun `the original generated number is not included in shrinks`() {
            val gen = Gen.float(-100.0f..100.0f)

            Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                .take(100)
                .forEach { (originalValue, shrunkValues) ->
                    expectThat(shrunkValues).doesNotContain(originalValue)
                }
        }

        @Test
        fun `when 0 is in range, shrinks are closer to 0 than the original generated number`() {
            val gen = Gen.float(-50.0f..50.0f)

            Gen.tree().samples().map { gen.generateWithShrunkValues(it) }
                .filter { (originalValue) -> originalValue != 0.0f }
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
            val gen = Gen.float(100.0f..200.0f)
            val tree = ProducerTree.new().withValue(200.0f)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(200.0f)

            // Should shrink toward 100.0 (range start, which is the origin)
            expectThat(shrunkValues).isNotEmpty()
            expectThat(shrunkValues.first()).isEqualTo(100.0f)
        }


        @Test
        fun `shrinks non-whole numbers to whole number early`() {
            val gen = Gen.float(0.0f..100.0f)
            val tree = ProducerTree.new().withValue(5.732f)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(5.732f)

            // Should include both 0.0 (origin) and 5.0 (whole part)
            expectThat(shrunkValues).contains(0.0f, 5.0f)

            // Whole part should come early (second, after origin)
            val indexOf5 = shrunkValues.indexOf(5.0f)
            expectThat(indexOf5).isEqualTo(1)
        }

        @Test
        fun `whole number does not yield duplicate whole part`() {
            val gen = Gen.float(0.0f..100.0f)
            val tree = ProducerTree.new().withValue(5.0f)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(5.0f)

            // Should not have duplicate 5.0 in shrinks
            val count5 = shrunkValues.count { it == 5.0f }
            expectThat(count5).isEqualTo(0)
        }

        @Test
        fun `very large float handles toInt overflow gracefully`() {
            val gen = Gen.float(0.0f..Float.MAX_VALUE)
            val largeValue = 1e20f // Much larger than Int.MAX_VALUE
            val tree = ProducerTree.new().withValue(largeValue)

            val (originalValue, shrunkValues) = gen.generateWithShrunkValues(tree)
            expectThat(originalValue).isEqualTo(largeValue)

            // Should still shrink (toward 0), even though can't convert to whole number
            expectThat(shrunkValues).isNotEmpty()
            expectThat(shrunkValues.first()).isEqualTo(0.0f)
        }
    }
}

