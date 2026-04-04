package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.stats.Percentage.Companion.percent
import com.tamj0rd2.ktcheck.stats.withCounter
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.getValue
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThanOrEqualTo

internal interface BooleanGeneratorContract : BaseContract {
    override val exampleGen get() = bool()

    // todo: should a boolean have edge cases? maybe the edge case is whichever value we're not shrinking toward?
    //  check what other property based testing libraries do.
    override val genSupportsEdgeCases get() = false

    @Test
    fun `generates a reasonable distribution of values over multiple runs`() {
        val counter = withCounter {
            bool()
                .samples()
                .take(100_000)
                .forEach { collect(it) }
        }

        expectThat(counter.asMap()).getValue(true).get { percentage }.isGreaterThanOrEqualTo(49.percent)
        expectThat(counter.asMap()).getValue(true).get { percentage }.isGreaterThanOrEqualTo(49.percent)

        counter.checkPercentages(mapOf(true to 49.percent, false to 49.percent))
    }

    @Test
    fun `using the same tree generates the same value`() {
        val gen = bool()
        val tree = ctx()
        val values = List(1000) { gen.generate(tree).value }
        val firstValue = values.first()
        expectThat(values.drop(1)).all { isEqualTo(firstValue) }
    }

    @TestFactory
    fun `shrinks correctly`(): List<DynamicTest> {
        data class TestCase(
            val value: Boolean,
            val shrinkTarget: Boolean,
            val expectedShrinks: List<Boolean>,
        )

        val testCases = listOf(
            TestCase(value = true, shrinkTarget = false, expectedShrinks = listOf(false)),
            TestCase(value = true, shrinkTarget = true, expectedShrinks = emptyList()),
            TestCase(value = false, shrinkTarget = false, expectedShrinks = emptyList()),
            TestCase(value = false, shrinkTarget = true, expectedShrinks = listOf(true))
        )

        return testCases.map {
            DynamicTest.dynamicTest(it.toString()) {
                val result = bool(it.shrinkTarget).generating(it.value)
                expectThat(result).shrunkValues.isEqualTo(it.expectedShrinks)
            }
        }
    }
}
