package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.producer.ProducerTree
import com.tamj0rd2.ktcheck.producer.ProducerTreeDsl.Companion.copy

private class ListGenerator<T>(
    private val sizeRange: IntRange,
    private val distinct: Boolean,
    private val gen: Gen<T>,
) : Gen<List<T>>() {
    override fun generate(tree: ProducerTree): GenResult<List<T>> {
        val size = tree.left.producer.int(sizeRange)
        val (list, listValueShrinks) = listN(rootTree = tree.right, targetSize = size)

        return GenResult(
            value = list,
            shrinks = sequence {
                val sizeShrinks = shrink(size, sizeRange).filter { it in sizeRange && it != 0 }

                // this and the condition above prevents yielding duplicate empty list shrinks
                if (size != 0 && 0 in sizeRange) yield(tree.copy { left(value = 0) })

                // reduce size - elements are "removed" from the end of the list
                yieldAll(sizeShrinks.map { tree.copy { left(value = it) } })
                yieldAll(
                    sizeShrinks.map {
                        // reduces size by "removing" elements from the start of the list
                        tree.copy {
                            left(value = it)
                            right(tree = tree.traverseRight(1 + size - it))
                        }
                    }
                )

                yieldAll(listValueShrinks.map { tree.withRight(it) })
            }
        )
    }

    private fun ProducerTree.traverseRight(steps: Int): ProducerTree =
        if (steps == 0) this else right.traverseRight(steps - 1)

    /**
     * Generates a list of N elements using a left-right tree traversal pattern, where left is used to generate data,
     * and right is used for continuation.
     *
     * This creates a traversal pattern like:
     * ```
     *   root
     *   ├─L: element[0] generated here (data)
     *   └─R: continuation for remaining elements
     *      ├─L: element[1] generated here (data)
     *      └─R: continuation for remaining elements
     *         ├─L: element[2] generated here (data)
     *         └─R: continuation... (and so on)
     *```
     *
     * This pattern allows us to:
     * - Deterministically recreate the same list from the same tree
     * - Shrink individual elements by replacing their subtree while keeping others unchanged
     */
    private tailrec fun listN(
        rootTree: ProducerTree,
        targetSize: Int,
        currentTree: ProducerTree = rootTree,
        index: Int = 0,
        values: List<T> = emptyList(),
        shrinksByIndex: List<Sequence<ProducerTree>> = emptyList(),
        retriesRemaining: Int = 1000,
    ): GenResult<List<T>> {
        if (index == targetSize) return GenResult(value = values, shrinks = rootTree.combineShrinks(shrinksByIndex))

        if (distinct && retriesRemaining == 0) {
            throw ImpossibleSetSize(targetSize, values.size, 1000)
        }

        val (value, shrinks) = gen.generate(currentTree.left)

        // Handle duplicates when distinct=true
        if (distinct && value in values) {
            // If the current list size is within the acceptable range, accept the smaller list
            // This prevents generating more complex values to fill back up to target size
            if (values.size in sizeRange) {
                return GenResult(value = values, shrinks = rootTree.combineShrinks(shrinksByIndex))
            }

            // Otherwise, skip duplicate and try to reach target size
            return listN(
                rootTree = rootTree,
                targetSize = targetSize,
                currentTree = currentTree.right,
                index = index,
                values = values,
                shrinksByIndex = shrinksByIndex,
                retriesRemaining = retriesRemaining - 1,
            )
        }

        return listN(
            rootTree = rootTree,
            targetSize = targetSize,
            currentTree = currentTree.right,
            index = index + 1,
            values = values + value,
            shrinksByIndex = shrinksByIndex + listOf(shrinks),
            retriesRemaining = retriesRemaining,
        )
    }

    /**
     * Combines shrinks from values generated using the left-right tree traversal pattern.
     *
     * Each shrink applies to one element at a specific index, while keeping other elements unchanged.
     * This enables element-wise shrinking where each element can shrink independently.
     *
     * @param shrinksByIndex A list of shrink sequences, one for each list element position
     */
    private fun ProducerTree.combineShrinks(
        shrinksByIndex: List<Sequence<ProducerTree>>,
    ): Sequence<ProducerTree> {
        fun reconstructTreeWithShrinkAtIndex(
            tree: ProducerTree,
            index: Int,
            shrunkTree: ProducerTree,
        ): ProducerTree =
            if (index == 0) {
                tree.withLeft(shrunkTree)
            } else {
                tree.withRight(reconstructTreeWithShrinkAtIndex(tree.right, index - 1, shrunkTree))
            }

        return shrinksByIndex.asSequence().flatMapIndexed { i, shrinks ->
            shrinks.map { shrunkTree ->
                reconstructTreeWithShrinkAtIndex(this, i, shrunkTree)
            }
        }
    }
}

class ImpossibleSetSize(targetSize: Int, achievedSize: Int, attempts: Int) : GenerationException(
    "Failed to generate a list of size $targetSize with distinct elements after $attempts attempts. Only achieved size $achievedSize."
)

// todo: at this point, some kind of builder would help with optional parameters
fun <T> Gen<T>.list(size: IntRange = 0..100, distinct: Boolean = false): Gen<List<T>> =
    ListGenerator(sizeRange = size, distinct = distinct, gen = this)

fun <T> Gen<T>.list(size: Int, distinct: Boolean = false): Gen<List<T>> = list(size..size, distinct)

fun <T> Gen<T>.set(size: IntRange = 0..100): Gen<Set<T>> =
    ListGenerator(sizeRange = size, distinct = true, gen = this).map { it.toSet() }

fun <T> Gen<T>.set(size: Int): Gen<Set<T>> = set(size..size)
