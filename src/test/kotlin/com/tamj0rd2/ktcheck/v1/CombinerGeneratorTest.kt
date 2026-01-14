package com.tamj0rd2.ktcheck.v1

import com.tamj0rd2.ktcheck.contracts.CombinerGeneratorRecursiveShrinkingTests
import com.tamj0rd2.ktcheck.contracts.CombinerGeneratorTestContract

internal class CombinerGeneratorTest :
    BaseGenTest(),
    CombinerGeneratorTestContract,
    CombinerGeneratorRecursiveShrinkingTests
