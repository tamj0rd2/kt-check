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
        val maxTreeOffset = (tree.data as? MaxTreeOffsetWrapper)?.maxOffset
        val (elementResults, treeOffsets) = generateListWithResults(
            tree = tree.right,
            minSize = sizeRange.first,
            maxSize = sizeResult.value,
            maxTreeOffset = maxTreeOffset,
        )

        // Size-based shrinking: Only "take first N" is supported.
        // "Take last N" doesn't work because tree positions don't map to list indices
        // when duplicates are filtered. Skipping tree positions gives different values
        // than taking the last N elements.
        val sizeBasedShrinks = sizeResult.shrinks.map { sizeTree -> tree.withLeft(sizeTree) }

        // Element-based shrinking with max offset constraint
        // When we shrink an element, we must not traverse beyond the max offset used
        // in the original generation, otherwise we might pick up new elements with
        // larger values if the shrunk element creates a duplicate
        val originalMaxTreeOffset = treeOffsets.maxOrNull() ?: -1
        val elementBasedShrinks = elementResults.asSequence().flatMapIndexed { index, element ->
            val treeOffset = treeOffsets[index]
            element.shrinks.map { elementTree ->
                val shrunkTree = tree.withRight(tree.right.replaceLeftAtOffset(treeOffset, elementTree))
                // Attach max offset metadata to limit traversal during regeneration
                shrunkTree.withData(MaxTreeOffsetWrapper(tree.data, originalMaxTreeOffset))
            }
        }

        return GenResultV2(
            value = elementResults.map { it.value },
            shrinks = sizeBasedShrinks + elementBasedShrinks,
        )
    }

    private data class MaxTreeOffsetWrapper(
        private val delegate: ValueProvider,
        val maxOffset: Int,
    ) : ValueProvider by delegate

    private fun generateListWithResults(
        tree: RandomTree,
        minSize: Int,
        maxSize: Int,
        maxTreeOffset: Int? = null,
    ): Pair<List<GenResultV2<T>>, List<Int>> {
        val results = mutableListOf<GenResultV2<T>>()
        val treeOffsets = mutableListOf<Int>()
        val seenValues = mutableSetOf<T>()
        var failureCount = 0
        var treeOffset = 0
        val trees = generateSequence(tree) { it.right }.iterator()

        while (results.size < maxSize) {
            // Stop if we've reached the max tree offset constraint
            if (maxTreeOffset != null && treeOffset > maxTreeOffset) {
                // If we haven't met the minimum size, this is a failed shrink
                // Break here and let the result be discarded during shrinking
                break
            }

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
                treeOffsets.add(treeOffset)
                failureCount = 0
            } else {
                failureCount += 1
            }
            treeOffset += 1
        }

        return results to treeOffsets
    }

    companion object {
        private const val MAX_FAILURES = 100
    }
}
