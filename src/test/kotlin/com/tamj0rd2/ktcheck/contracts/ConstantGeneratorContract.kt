package com.tamj0rd2.ktcheck.contracts

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

internal interface ConstantGeneratorContract : BaseContract {
    override val exampleGen get() = constant("hello")
    override val genSupportsShrinking get() = false
    override val genSupportsEdgeCases get() = false

    @Test
    fun `constant always produces the same value and doesn't shrink`() {
        val gen = constant(10)
        repeatTest { seed ->
            val result = gen.generate(tree(seed))
            expectThat(result.value).isEqualTo(10)
            expectThat(result).shrunkValues.isEmpty()
            expectThat(gen.edgeCase(seed)).isNull()
        }
    }
}
