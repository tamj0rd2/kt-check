package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.Counter.Companion.withCounter
import com.tamj0rd2.ktcheck.GenerationException.FilterLimitReached
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

        withCounter {
            repeatTest { seed ->
                val result = gen.generate(tree(seed))
                expectThat(result).value.assertThat("is even") { it % 2 == 0 }
                expectThat(result).shrunkValues.all { assertThat("is even") { it % 2 == 0 } }
                collect("has-shrinks", result.shrunkValues.any())
            }
        }.checkPercentages("has-shrinks", mapOf(true to 10.0))

        // todo: add some - deeply shrunk values are finite function. call it above.
        gen.expectGenerationAndShrinkingToEventuallyComplete()
    }

    @Test
    fun `shrinks of filter are never greater than the originally generated value`() {
        withCounter {
            val gen = int(1..10).filter { it % 2 == 0 }

            repeatTest { seed ->
                val result = gen.generate(tree(seed))
                if (result.value <= 2) skipIteration()
                expectThat(result).shrunkValues.all { isLessThanOrEqualTo(result.value) }
                collect("has-shrinks", result.shrunkValues.any())
            }
        }.checkPercentages("has-shrinks", mapOf(true to 10.0))
    }

    @Test
    fun `throws if the filter threshold is exceeded`() {
        val gen = int(1..10).filter { it > 10 }
        expectThrows<FilterLimitReached> { gen.generate() }
    }
}
