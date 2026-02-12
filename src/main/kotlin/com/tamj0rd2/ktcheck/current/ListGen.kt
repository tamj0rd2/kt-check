package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker

internal class ListGen<T>(
    private val gen: GenImpl<T>,
    private val sizeRange: IntRange,
) : GenImpl<List<T>>() {
    private val sizeGen = IntGen(sizeRange, IntShrinker.defaultShrinkTarget(sizeRange))

    override fun edgeCases(): List<GenResultV2<List<T>>> {
        // todo: re-implement this.
        return emptyList()
    }

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
}
