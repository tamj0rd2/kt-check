package com.tamj0rd2.ktcheck.contracts

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull

internal interface MapGeneratorContract : BaseContract {
    override val exampleGen get() = int(-100..100).map { it * 2 }

    @Test
    fun `maps the original value and shrinks`() {
        val originalGen = int(0..10)
        val doublingGen = originalGen.map { it * 2 }

        repeatTest { seed ->
            val originalResult = originalGen.generate(tree(seed))
            val doubledResult = doublingGen.generate(tree(seed))

            expectThat(doubledResult.value).isEqualTo(originalResult.value * 2)
            expectThat(doubledResult).shrunkValues.isEqualTo(originalResult.shrunkValues.map { it * 2 })
        }
    }

    @Test
    fun `propagates mapped versions of the underlying edge cases and their shrinks`() {
        val originalGen = int(0..10)
        val doublingGen = originalGen.map { it * 2 }

        repeatTest { seed ->
            val originalEdgeCase = originalGen.edgeCase(seed)
            val doubledEdgeCase = doublingGen.edgeCase(seed)
            if (originalEdgeCase == null) skipIteration()

            expectThat(doubledEdgeCase).isNotNull().and {
                value.isEqualTo(originalEdgeCase.value * 2)
                shrunkValues.isEqualTo(originalEdgeCase.shrunkValues.map { it * 2 })
            }
        }
    }
}
