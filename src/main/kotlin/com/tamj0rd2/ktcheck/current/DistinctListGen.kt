package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenerationException.DistinctCollectionSizeImpossible
import com.tamj0rd2.ktcheck.core.Tree
import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker

internal class DistinctListGen<T>(
    private val gen: GenImpl<T>,
    private val sizeRange: IntRange,
) : GenImpl<List<T>>() {
    private val sizeGen = IntGen(sizeRange, IntShrinker.defaultShrinkTarget(sizeRange))

    override fun generate(tree: RandomTree): GenResultV2<List<T>> {
        val sizeResult = sizeGen.generate(tree.left)
        val elementResults = generateListWithResults(
            tree = tree.right,
            minSize = sizeRange.first,
            maxSize = sizeResult.value,
        )

        val generatedList = elementResults.map { it.result.value }

        val sizeBasedShrinks = sizeResult.shrinks.map { sizeTree -> tree.withLeft(sizeTree) }
        val elementBasedShrinks = createElementBasedShrinks(elementResults, generatedList, tree)

        return GenResultV2(
            value = generatedList,
            shrinks = sizeBasedShrinks + elementBasedShrinks,
        )
    }

    private data class ElementResult<T>(
        val listIndex: Int,
        val treeOffset: Int,
        val elementTree: RandomTree,
        val result: GenResultV2<T>,
    )

    private fun generateListWithResults(
        tree: RandomTree,
        minSize: Int,
        maxSize: Int,
    ): List<ElementResult<T>> {
        val results = mutableListOf<ElementResult<T>>()
        val seenValues = mutableSetOf<T>()
        var failureCount = 0
        var treeOffset = 0
        var tree = tree

        while (results.size < maxSize) {
            if (failureCount >= MAX_FAILURES) {
                if (results.size >= minSize) break

                throw DistinctCollectionSizeImpossible(
                    minSize = minSize,
                    achievedSize = results.size,
                    attempts = failureCount,
                )
            }

            val elementTree = tree.left
            val result = gen.generate(elementTree)

            if (seenValues.add(result.value)) {
                results.add(
                    ElementResult(
                        listIndex = results.size,
                        treeOffset = treeOffset,
                        elementTree = elementTree,
                        result = result,
                    )
                )
                failureCount = 0
            } else {
                failureCount += 1
            }

            tree = tree.right
            treeOffset += 1
        }

        return results
    }

    private fun createElementBasedShrinks(
        elementResults: List<ElementResult<T>>,
        list: List<T>,
        tree: RandomTree,
    ): Sequence<Tree<ValueProvider>> = elementResults.asSequence().flatMap { elementResult ->
        val otherElements = list.filterIndexed { i, _ -> i != elementResult.listIndex }

        elementResult.result.shrinks
            .filter { gen.generate(it).value !in otherElements }
            .map { shrunkElementTree ->
                val newElementTrees = elementResults.map { er ->
                    if (er.listIndex == elementResult.listIndex) shrunkElementTree else er.elementTree
                }

                buildTreeForElements(tree, newElementTrees)
            }
    }

    /**
     * Builds a tree that will generate elements from the provided list of element trees.
     *
     * Creates a sequential tree structure where tree.right.right.right...left[i] produces
     * elementTrees[i], ensuring shrunk lists regenerate with stable element positions.
     *
     * Explicitly positioning each element tree prevents the distinctness check from accidentally accepting different
     * elements during regeneration. This is why [RandomTree.replaceLeftAtOffset] is not sufficient.
     */
    private fun buildTreeForElements(
        originalTree: RandomTree,
        elementTrees: List<RandomTree>,
    ): RandomTree {
        fun buildRightTree(index: Int): RandomTree {
            val beyondOriginalTreeStructure = index >= elementTrees.size
            if (beyondOriginalTreeStructure) return originalTree.right

            return Tree(
                data = originalTree.right.data,
                lazyLeft = lazy { elementTrees[index] },
                lazyRight = lazy { buildRightTree(index + 1) }
            )
        }

        return originalTree.withRight(buildRightTree(0))
    }

    companion object {
        private const val MAX_FAILURES = 100
    }
}
