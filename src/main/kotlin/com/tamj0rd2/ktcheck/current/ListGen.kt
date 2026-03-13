package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.onFailure

internal class ListGen<T>(
    sizeGen: Generator<Int>,
    elementGen: Generator<T>,
) : AbstractListGen<T>(sizeGen, elementGen) {

    override fun generateElements(
        initialTree: ProviderTree,
        size: Int,
    ): Result4k<List<GeneratedValue<T>>, GenerationException> = buildList {
        for (tree in initialTree.traversingRight().take(size)) {
            add(elementGen.generate(tree.left).onFailure { return it })
        }
    }.asSuccess()

    override fun edgeCases(root: ProviderTree): List<GeneratedValue<List<T>>> {
        return sizeGen.edgeCases(root.left).flatMap { sizeResult ->
            elementGen.edgeCases(root.right).map { elementResult ->
                val elementResults = List(sizeResult.value) { elementResult }

                val reproducibleTree = root
                    .withSizeTree(sizeResult.usedTree)
                    .withElementTrees(elementResults)

                buildResult(reproducibleTree, sizeResult, elementResults)
            }
        }
    }
}
