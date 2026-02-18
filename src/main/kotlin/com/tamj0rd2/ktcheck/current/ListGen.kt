package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker

internal class ListGen<T>(
    private val gen: GenImpl<T>,
    private val sizeRange: IntRange,
) : GenImpl<List<T>>() {
    // todo: throw is sizeRange is negative or empty

    private val sizeGen = IntGen(sizeRange, IntShrinker.defaultShrinkTarget(sizeRange))

    override fun generate(tree: RandomTree): GenResultV2<List<T>> {
        val sizeResult = sizeGen.generate(tree.left)
        val elements = generateElements(tree.right, sizeResult.value)

        // Note: We re-generate sizeTree here to know how many elements to skip for "take last N".
        // Future: Could simplify to "take first N" + "drop first 1" to avoid re-generation.
        val sizeBasedShrinks = sizeResult.shrinks.flatMap { sizeTree ->
            val shrunkSize = sizeGen.generate(sizeTree).value
            val elementsToSkip = sizeResult.value - shrunkSize

            sequence {
                yield(tree.withLeft(sizeTree))

                if (elementsToSkip > 0) {
                    yield(tree.withLeft(sizeTree).withRight(tree.right.skipRight(elementsToSkip)))
                }
            }
        }

        val elementBasedShrinks = elements.asSequence().flatMapIndexed { index, element ->
            element.shrinks.map { elementTree ->
                tree.withRight(tree.right.replaceLeftAtOffset(index, elementTree))
            }
        }

        return GenResultV2(
            value = elements.map { it.value },
            shrinks = sizeBasedShrinks + elementBasedShrinks,
        )
    }

    private fun RandomTree.skipRight(n: Int): RandomTree =
        (0 until n).fold(this) { tree, _ -> tree.right }

    private fun generateElements(tree: RandomTree, size: Int): List<GenResultV2<T>> {
        val results = mutableListOf<GenResultV2<T>>()
        var currentTree = tree

        repeat(size) {
            val elementResult = gen.generate(currentTree.left)
            results.add(elementResult)
            currentTree = currentTree.right
        }

        return results
    }

    override fun edgeCases(): List<GenResultV2<List<T>>> {
        val elementEdgeCases = gen.edgeCases()
        val listEdgeCases = mutableListOf<GenResultV2<List<T>>>()

        if (0 in sizeRange) {
            listEdgeCases.add(buildResult(emptyList(), edgeCaseTree))
        }

        if (1 in sizeRange) {
            elementEdgeCases.forEach { elementEdgeCase ->
                listEdgeCases.add(buildResult(listOf(elementEdgeCase.value), edgeCaseTree))
            }
        }

        sizeRange.firstOrNull { it > 1 }?.let { size ->
            elementEdgeCases.forEach { elementEdgeCase ->
                val duplicateList = List(size) { elementEdgeCase.value }
                listEdgeCases.add(buildResult(duplicateList, edgeCaseTree))
            }
        }

        return listEdgeCases
    }

    private fun buildResult(value: List<T>, tree: RandomTree): GenResultV2<List<T>> {
        if (value.isEmpty()) return GenResultV2(value, emptySequence())

        val currentSize = value.size
        val sizeResult = sizeGen.generate(tree.withData(ValueProvider.Shrunk(currentSize)))

        val sizeShrinks = sizeResult.shrinks.mapNotNull { sizeTree ->
            val shrunkSize = sizeGen.generate(sizeTree).value
            if (shrunkSize < currentSize) {
                // Create a tree that will generate a list of the shrunk size
                tree.withLeft(sizeTree)
            } else {
                null
            }
        }

        val elementShrinks = value.asSequence().flatMapIndexed { index, element ->
            // todo: potentially buggy.
            val elementResult = gen.generate(tree.withData(ValueProvider.Shrunk(element)))
            elementResult.shrinks.map { shrinkTree ->
                tree.withRight(tree.right.replaceLeftAtOffset(index, shrinkTree))
            }
        }

        val shrinks = sizeShrinks + elementShrinks
        return GenResultV2(
            value = value,
            shrinks = shrinks
        )
    }
}
