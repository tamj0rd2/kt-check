package com.tamj0rd2.ktcheck.v1

import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.core.ProducerTree

internal class CombinerGenerator<T>(
    private val block: CombinerContext.() -> T,
) : GenV1<T>() {
    override fun GenContext.generate(): GenResult<T> {
        val initialTree = tree
        return CombinerContext(initialTree, mode).run {
            val value = block(this)
            val shrinks = initialTree.combineShrinks(shrinksByIndex)
            GenResult(value, shrinks)
        }
    }
}

class CombinerContext internal constructor(
    private var tree: ProducerTree,
    private val mode: GenMode,
) {
    internal val shrinksByIndex = mutableListOf<Sequence<ProducerTree>>()

    fun <T> Gen<T>.bind(): T {
        // todo: I hate this casting between different Gen impls stuff
        val (value, shrinks) = (this as GenV1<T>).generate(tree.left, mode)
        tree = tree.right
        shrinksByIndex.add(shrinks)
        return value
    }
}
