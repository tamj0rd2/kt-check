package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.contracts.OneOfGeneratorTestContract
import com.tamj0rd2.ktcheck.core.IGen

internal class OneOfGeneratorV2Test : BaseGenV2Test(), OneOfGeneratorTestContract {
    override fun <T : Any> IGen<T>.generateWithShrunkValuesForOneOfGens(rngValues: List<Any>): Pair<T, List<T>> {
        return generateWithShrunkValues(rngValues)
    }
}
