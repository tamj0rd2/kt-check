package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.contract.IGen
import com.tamj0rd2.ktcheck.contracts.ListGeneratorTestContract

internal class ListGeneratorTest : BaseGenV2Test(), ListGeneratorTestContract {
    override fun <T : Any> IGen<T>.generateWithShrunkValuesForListGen(rngValues: List<Any>): Pair<T, List<T>> {
        return generateWithShrunkValues(rngValues)
    }
}
