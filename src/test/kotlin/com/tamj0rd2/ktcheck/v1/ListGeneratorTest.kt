package com.tamj0rd2.ktcheck.v1

import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.contracts.ListGeneratorTestContract
import com.tamj0rd2.ktcheck.v1.GenTests.Companion.generateWithShrunkValues

internal class ListGeneratorTest : BaseGenTest(), ListGeneratorTestContract {
    override fun <T : Any> Gen<T>.generateWithShrunkValuesForListGen(rngValues: List<Any>): Pair<T, List<T>> {
        val tree = buildListTree(rngValues)
        return (this as GenV1<T>).generateWithShrunkValues(tree)
    }
}
