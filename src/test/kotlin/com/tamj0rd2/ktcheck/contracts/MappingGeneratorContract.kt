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
            val (originalValue, originalShrinks) = originalGen.collectShrunkValues(seed)
            val (doubledValue, doubledShrinks) = doublingGen.collectShrunkValues(seed)

            expectThat(doubledValue).isEqualTo(originalValue * 2)
            expectThat(doubledShrinks).isEqualTo(originalShrinks.map { it * 2 })
        }
    }
}
