package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.contract.IGen
import com.tamj0rd2.ktcheck.contracts.OneOfGeneratorTestContract

internal class OneOfGeneratorTest : BaseGenV2Test(), OneOfGeneratorTestContract {
    override fun <T : Any> IGen<T>.generateWithShrunkValuesForOneOfGens(rngValues: List<Any>): Pair<T, List<T>> {
        return generateWithShrunkValues(rngValues)
    }
}
