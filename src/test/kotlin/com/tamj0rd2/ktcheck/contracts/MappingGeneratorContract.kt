package com.tamj0rd2.ktcheck.contracts

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal interface MappingGeneratorContract : BaseContract {
    override val exampleGen get() = int(-100..100).map { it * 2 }

    @Test
    fun `maps the original value and shrinks`() {
        val originalGen = int(0..10)
        val doublingGen = originalGen.map { it * 2 }

        repeatTest { seed ->
            val originalResult = originalGen.generate(ctx(seed))
            val doubledResult = doublingGen.generate(ctx(seed))

            expectThat(doubledResult.value).isEqualTo(originalResult.value * 2)
            expectThat(doubledResult).shrunkValues.isEqualTo(originalResult.shrunkValues.map { it * 2 })
        }
    }
}
