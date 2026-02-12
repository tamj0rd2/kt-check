package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.GenBuilders
import com.tamj0rd2.ktcheck.contracts.BaseContract
import com.tamj0rd2.ktcheck.contracts.GenResults
import com.tamj0rd2.ktcheck.core.Seed

internal abstract class BaseContractImpl : BaseContract, GenBuilders by GenV2Builders {
    override fun tree(seed: Seed) = randomTree(seed)

    @Suppress("UNCHECKED_CAST")
    override fun <T> Gen<T>.generate(tree: RandomTree): GenResults<T> {
        val result = (this as GenImpl).generate(tree)
        return GenResults(result.value, collectShrinksRecursively(result.shrinks))
    }

    private fun <T> Gen<T>.collectShrinksRecursively(shrinks: Sequence<RandomTree>): Sequence<GenResults<T>> =
        sequence {
            for (shrink in shrinks) {
                yield(generate(shrink))
            }
        }
}
