package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.contracts.IgnoreExceptionsGeneratorContract
import com.tamj0rd2.ktcheck.core.Seed
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isNull

internal class IgnoreExceptionsGeneratorTest : BaseContractImpl(), IgnoreExceptionsGeneratorContract {
    @Test
    fun `does not produce any edge cases`() {
        expectThat(exampleGen.edgeCase(Seed.random())).isNull()
    }

    @Test
    fun `ignoreExceptions propagates edge cases from underlying generator`() {
        runIfGenSupportsEdgeCases()
        TODO("write this test")
    }
}
