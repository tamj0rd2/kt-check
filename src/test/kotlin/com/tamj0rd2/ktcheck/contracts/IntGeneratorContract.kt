package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.TestConfig
import com.tamj0rd2.ktcheck.checkAll
import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker
import com.tamj0rd2.ktcheck.full
import com.tamj0rd2.ktcheck.stats.Percentage.Companion.percent
import com.tamj0rd2.ktcheck.stats.withCounter
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isIn
import kotlin.random.Random

internal interface IntGeneratorContract : BaseContract {
    override val exampleGen get() = int()

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
            dynamicTest(desc) {
                val values = int(range).samples().take(TestConfig.DEFAULT_ITERATIONS).toList()
                expectThat(values).all { isIn(range) }.hasSize(TestConfig.DEFAULT_ITERATIONS)
            }
        }
    }

    @Test
    fun `generates both positive and negative integers over multiple runs`() {
        val counter = withCounter {
            checkAll(int(IntRange.full)) {
                collect(if (it >= 0) "positive" else "negative")
            }
        }
        counter.checkPercentages(mapOf("positive" to 40.percent, "negative" to 40.percent))
    }

    @Test
    fun `shrinks the generated value`() {
        repeatTest { seed ->
            val random = Random(seed.value)
            val startOfRange = (-100..100).random(random)
            val range = startOfRange..(startOfRange + 50)
            val shrinkTarget = range.random(random)

            val gen = int(range = range, shrinkTarget = shrinkTarget)

            val (originalValue, shrinks) = gen.collectShrunkValues(
                seed = seed,
                startShrinkingOnce = { it != shrinkTarget }
            )

            expectThat(shrinks)
                .describedAs { "shrinks of $originalValue (range=$range | shrinkTarget=$shrinkTarget)" }
                .isEqualTo(IntShrinker.shrink(originalValue, range, shrinkTarget).toList())
        }
    }

    @Test
    fun `throws if shrink target not in range`() {
        assertThrows<IllegalArgumentException> {
            int(0..10, 20)
        }
    }

    @Test
    fun `creates common edge cases and their shrinks`() {
        val gen = int(-10..10)
        val expectedEdgeCases = setOf(-10, -9, -1, 0, 1, 9, 10)

        repeatTest { seed ->
            val (originalValue, shrinks) = gen.collectShrunkValues(
                seed = seed,
                startShrinkingOnce = { it in expectedEdgeCases }
            )
            expectThat(shrinks)
                .describedAs { "shrinks of $originalValue" }
                .isEqualTo(IntShrinker.shrink(originalValue, -10..10, 0).toList())
        }
    }
}
