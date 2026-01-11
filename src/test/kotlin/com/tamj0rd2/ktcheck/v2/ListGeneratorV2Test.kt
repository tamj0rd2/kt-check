package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.contracts.ListGeneratorTestContract
import com.tamj0rd2.ktcheck.Gen

internal class ListGeneratorV2Test : BaseGenV2Test(), ListGeneratorTestContract {
    override fun <T : Any> Gen<T>.generateWithShrunkValuesForListGen(rngValues: List<Any>): Pair<T, List<T>> {
        return generateWithShrunkValues(rngValues)
    }
}
