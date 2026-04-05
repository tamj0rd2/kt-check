package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.checkAll
import com.tamj0rd2.ktcheck.core.Seed
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo

internal interface ConstantGeneratorContract : BaseContract {
    override val exampleGen get() = constant("hello")
    override val genSupportsShrinking get() = false
    override val genSupportsEdgeCases get() = false

    @Test
    fun `always produces the same value`() {
        checkAll(exampleGen) { expectThat(it).isEqualTo("hello") }
    }

    @Test
    fun `does not shrink`() {
        val (originalValue, shrinks) = exampleGen.collectShrunkValues(Seed.random())
        expectThat(shrinks).describedAs { "shrinks of $originalValue" }.isEmpty()
    }
}
