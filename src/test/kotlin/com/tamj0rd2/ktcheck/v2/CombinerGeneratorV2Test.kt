package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.contracts.CombinerGeneratorRecursiveShrinkingTests
import com.tamj0rd2.ktcheck.contracts.CombinerGeneratorTestContract
import org.junit.jupiter.api.Disabled

internal class CombinerGeneratorV2Test :
    BaseGenV2Test(),
    CombinerGeneratorTestContract,
    CombinerGeneratorRecursiveShrinkingTests {

    @Disabled("the V2 generator cannot guarantee this as generation only happens once")
    override fun `conditionals fail when affecting a middle bind`() {
    }
}
