package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.GenerationException.FilterLimitReached
import com.tamj0rd2.ktcheck.stats.Percentage.Companion.percent
import com.tamj0rd2.ktcheck.stats.withLabelledCounter
import org.junit.jupiter.api.Test
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.all
import strikt.assertions.isLessThanOrEqualTo

internal interface FilterGeneratorContract : BaseContract {
    override val exampleGen get() = int(0..10).filter { it >= 5 }

    @Test
    fun `can filter generated values and their shrinks`() {
        fun Assertion.Builder<Int>.isEven() = assertThat("is even") { it % 2 == 0 }

        val gen = int(1..10).filter { it % 2 == 0 }

        withLabelledCounter {
            repeatTest { seed ->
                val (originalValue, shrinks) = gen.collectShrunkValues(seed)
                expectThat(originalValue).isEven()
                expectThat(shrinks).describedAs("shrinks of $originalValue").all { isEven() }
                collect("has-shrinks", shrinks.isNotEmpty())
            }
        }.checkPercentages("has-shrinks", mapOf(true to 10.percent))

        gen.expectGenerationAndShrinkingToEventuallyComplete()
    }

    @Test
    fun `shrinks of filter are never greater than the originally generated value`() {
        withLabelledCounter {
            val gen = int(1..10).filter { it % 2 == 0 }

            repeatTest { seed ->
                val (originalValue, shrinks) = gen.collectShrunkValues(seed)
                if (originalValue <= 2) skipIteration()
                // todo: I've written this in lots of places. just introduce an assertion builder.
                expectThat(shrinks).describedAs("shrinks of $originalValue").all { isLessThanOrEqualTo(originalValue) }
                collect("has-shrinks", shrinks.isNotEmpty())
            }
        }.checkPercentages("has-shrinks", mapOf(true to 10.percent))
    }

    @Test
    fun `throws if the filter threshold is exceeded`() {
        val gen = int(1..10).filter { it > 10 }
        expectThrows<FilterLimitReached> { gen.generate(ctx()) }
    }
}
