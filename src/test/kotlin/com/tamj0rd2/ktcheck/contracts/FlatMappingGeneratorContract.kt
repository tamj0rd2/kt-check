package com.tamj0rd2.ktcheck.contracts

import org.junit.jupiter.api.Test
import strikt.api.expectDoesNotThrow
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.isIn

internal interface FlatMappingGeneratorContract : BaseContract {
    override val exampleGen get() = int(0..5).flatMap { int(10..10 + it) }

    @Test
    fun `all generated values and shrinks fall within the possible range of the generators`() {
        val gen = int(6..10).flatMap { int(IntRange(it + 2, it + 6)) }
        val minValuePossible = 6 + 2
        val maxPossibleValue = 10 + 6
        repeatTest { seed ->
            val (value, shrinks) = gen.collectShrunkValues(seed)
            expectThat(value).isIn(minValuePossible..maxPossibleValue)
            expectThat(shrinks).all { isIn(minValuePossible..maxPossibleValue) }
        }
    }

    @Test
    fun `shrinking is tolerant of the constraints of the inner generator being changed due to the outer value`() {
        val gen = int(0..5).flatMap { int(10..10 + it) }

        repeatTest { seed ->
            expectDoesNotThrow { gen.collectShrunkValues(seed) }
        }
    }
}
