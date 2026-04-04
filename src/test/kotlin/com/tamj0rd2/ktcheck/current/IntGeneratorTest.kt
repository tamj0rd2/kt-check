package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.contracts.IntGeneratorContract
import com.tamj0rd2.ktcheck.contracts.value
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isIn

internal class IntGeneratorTest : BaseContractImpl(), IntGeneratorContract {
    @Test
    fun `falls back to random generation if the predetermined value falls outside of the generator's range`() {
        val gen = int(0..10)
        val tree = ctx().withPredeterminedValue(100)
        expectThat(gen.generate(tree)).value.isIn(0..10)
    }
}
