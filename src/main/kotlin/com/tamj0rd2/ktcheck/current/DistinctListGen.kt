package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asFailure
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.onFailure

internal class DistinctListGen<T>(
    elementGen: Generator<T>,
    sizeRange: IntRange,
) : AbstractListGen<T>(elementGen, sizeRange) {

    override fun generateElements(
        initialTree: RandomTree,
        size: Int,
    ): Result4k<List<GeneratedValue<T>>, GenerationException> {
        val trees = initialTree.traversingRight().iterator()
        val results = mutableListOf<GeneratedValue<T>>()
        val seenValues = mutableSetOf<T>()
        var attempts = 0

        while (results.size < size) {
            val tree = trees.next()

            if (tree.isTerminator) {
                return GenerationException.DistinctCollectionSizeImpossible(
                    minSize = size,
                    achievedSize = results.size,
                    attempts = attempts,
                ).asFailure()
            }

            attempts += 1

            val elementResult = elementGen.generate(tree.left).onFailure { return it }

            if (seenValues.add(elementResult.value)) {
                results.add(elementResult)
                attempts = 0
                continue
            }

            if (attempts >= MAX_ATTEMPTS_PER_ELEMENT) {
                return GenerationException.DistinctCollectionSizeImpossible(
                    minSize = size,
                    achievedSize = results.size,
                    attempts = attempts,
                ).asFailure()
            }
        }

        return results.asSuccess()
    }

    override fun edgeCases(root: RandomTree): List<GeneratedValue<List<T>>> {
        return sizeGen.edgeCases(root.left).map { sizeResult ->
            val elementResults = elementGen.edgeCases(root.right)
                .distinctBy { it.value }
                .take(sizeResult.value)

            val reproducibleTree = root
                .withSizeTree(sizeResult.usedTree)
                .withElementTrees(elementResults)

            buildResult(reproducibleTree, sizeResult, elementResults)
        }
    }

    private companion object {
        const val MAX_ATTEMPTS_PER_ELEMENT = 100
    }
}
