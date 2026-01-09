package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.contract.IGen
import com.tamj0rd2.ktcheck.contracts.ListGeneratorTestContract
import com.tamj0rd2.ktcheck.gen.GenTests.Companion.generateWithShrunkValues

internal class ListGeneratorTest : BaseGenTest(), ListGeneratorTestContract {
    override fun <T : Any> IGen<T>.generateWithShrunkValuesForListGen(rngValues: List<Any>): Pair<T, List<T>> {
        val tree = buildListTree(rngValues)
        return (this as Gen<T>).generateWithShrunkValues(tree)
    }
}
