package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.GenerationException.FilterLimitReached
import com.tamj0rd2.ktcheck.stats.Percentage.Companion.percent
import com.tamj0rd2.ktcheck.stats.withLabelledCounter
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.all
import strikt.assertions.isLessThanOrEqualTo

internal interface FilterGeneratorContract : BaseContract {
    override val exampleGen get() = int(0..10).filter { it >= 5 }

    @Test
    fun `can filter generated values and their shrinks`() {
        val gen = int(1..10).filter { it % 2 == 0 }

        withLabelledCounter {
            fun checkResult(result: GenResults<Int>?) {
                if (result == null) skipIteration()
                expectThat(result).value.assertThat("is even") { it % 2 == 0 }
                expectThat(result).shrunkValues.all { assertThat("is even") { it % 2 == 0 } }
                collect("has-shrinks", result.shrunkValues.any())
            }

            repeatTest { seed -> checkResult(gen.generate(tree(seed))) }
            repeatTest { seed -> checkResult(gen.edgeCase(seed)) }
        }.checkPercentages("has-shrinks", mapOf(true to 10.percent))

        // todo: add some - deeply shrunk values are finite function. call it above.
        gen.expectGenerationAndShrinkingToEventuallyComplete()
    }

    // todo: will need a similar test for exception generation
    @Test
    fun `shrinks of filter are never greater than the originally generated value`() {
        withLabelledCounter {
            val gen = int(1..10).filter { it % 2 == 0 }

            fun checkResult(originalResult: GenResults<Int>?) {
                if (originalResult == null) skipIteration()
                if (originalResult.value <= 2) skipIteration()
                expectThat(originalResult).shrunkValues.all { isLessThanOrEqualTo(originalResult.value) }
                collect("has-shrinks", originalResult.shrunkValues.any())
            }

            repeatTest { seed -> checkResult(gen.generate(tree(seed))) }
            repeatTest { seed -> checkResult(gen.edgeCase(seed)) }
        }.checkPercentages("has-shrinks", mapOf(true to 10.percent))
    }

    @Test
    fun `throws if the filter threshold is exceeded`() {
        val gen = int(1..10).filter { it > 10 }
        expectThrows<FilterLimitReached> { gen.generate(tree()) }
    }
}
