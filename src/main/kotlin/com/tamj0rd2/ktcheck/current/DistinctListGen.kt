package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenerationException.DistinctCollectionSizeImpossible
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

        // Size-based shrinking: Only "take first N" is supported.
        // "Take last N" doesn't work because tree positions don't map to list indices
        // when duplicates are filtered. Skipping tree positions gives different values
        // than taking the last N elements.
        val sizeBasedShrinks = sizeResult.shrinks.map { sizeTree ->
            tree.withLeft(sizeTree)
        }

        val elementBasedShrinks = elementResults.asSequence().flatMapIndexed { index, element ->
            element.shrinks.map { elementTree ->
                tree.withRight(tree.right.replaceLeftAtOffset(index, elementTree))
            }
        }

        return GenResultV2(
            value = elementResults.map { it.value },
            shrinks = sizeBasedShrinks + elementBasedShrinks,
        )
    }

    private fun generateListWithResults(
        tree: RandomTree,
        minSize: Int,
        maxSize: Int,
    ): List<GenResultV2<T>> {
        val results = mutableListOf<GenResultV2<T>>()
        val seenValues = mutableSetOf<T>()
        var failureCount = 0
        val trees = generateSequence(tree) { it.right }.iterator()

        while (results.size < maxSize) {
            if (failureCount >= MAX_FAILURES) {
                if (results.size >= minSize) break

                throw DistinctCollectionSizeImpossible(
                    minSize = minSize,
                    achievedSize = results.size,
                    attempts = failureCount,
                )
            }

            val result = gen.generate(trees.next().left)

            if (seenValues.add(result.value)) {
                results.add(result)
                failureCount = 0
            } else {
                failureCount += 1
            }
        }

        return results
    }

    companion object {
        private const val MAX_FAILURES = 100
    }
}
