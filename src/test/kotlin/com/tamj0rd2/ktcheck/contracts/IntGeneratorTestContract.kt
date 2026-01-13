package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.Counter.Companion.withCounter
import com.tamj0rd2.ktcheck.v1.ProducerTreeDsl.Companion.producerTree
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.doesNotContain
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isIn
import strikt.assertions.isLessThan
import strikt.assertions.isNotEmpty
import kotlin.math.abs

internal interface IntGeneratorTestContract : BaseGeneratorContract {
    @Test
    fun `using the same seed generates the same values`() {
        val seed = 12345L
        val gen = int(-1000..1000)
        val firstRun = gen.samples(seed).take(100).toList()
        val secondRun = gen.samples(seed).take(100).toList()
        expectThat(secondRun).isEqualTo(firstRun)
    }

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
                int(range)
                    .samples()
                    .take(10000)
                    .forEach { expectThat(it).isIn(range) }
            }
        }
    }

    @Test
    fun `generates both positive and negative integers over multiple runs`() {
        withCounter {
            int(-100..100).samples().take(10000).forEach { value ->
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
    fun `10 shrinks correctly`() {
        val gen = int(0..10)

        val (originalValue, shrinks) = gen.generateWithShrunkValues(producerTree(10))
        expectThat(originalValue).isEqualTo(10)
        expectThat(shrinks).isEqualTo(listOf(0, 5, 8, 9))
    }

    @Test
    fun `-10 shrinks correctly`() {
        val gen = int(-10..0)

        val (originalValue, shrinks) = gen.generateWithShrunkValues(producerTree(-10))
        expectThat(originalValue).isEqualTo(-10)
        expectThat(shrinks).isEqualTo(listOf(0, -5, -8, -9))
    }

    @Test
    fun `shrinking zero produces no shrinks`() {
        val gen = int(Int.MIN_VALUE..Int.MAX_VALUE)

        val (originalValue, shrinks) = gen.generateWithShrunkValues(producerTree(0))
        expectThat(originalValue).isEqualTo(0)
        expectThat(shrinks).isEmpty()
    }

    @Test
    fun `shrinks for non-zero numbers always include 0`() {
        generateSequence { int(Int.MIN_VALUE..Int.MAX_VALUE).generateWithShrunkValues() }
            .filter { (value) -> value != 0 }
            .take(100)
            .forEach { (value, shrinks) ->
                expectThat(shrinks).doesNotContain(value)
            }
    }

    @Test
    fun `the generated number is not included in shrinks`() {
        generateSequence { int(Int.MIN_VALUE..Int.MAX_VALUE).generateWithShrunkValues() }
            .take(100)
            .forEach { (value, shrinks) ->
                expectThat(shrinks).doesNotContain(value)
            }
    }

    @Test
    fun `when 0 is in range, shrinks are closer to 0 than the original generated number`() {
        generateSequence { int(-50..50).generateWithShrunkValues() }
            .filter { (value) -> value != 0 }
            .take(100)
            .forEach { (value, shrinks) ->
                expectThat(shrinks).isNotEmpty().all {
                    get { abs(this) }.describedAs("shrunk distance from 0").isLessThan(abs(value))
                }
            }
    }
}
