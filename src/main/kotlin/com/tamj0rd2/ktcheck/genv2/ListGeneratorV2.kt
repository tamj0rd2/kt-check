package com.tamj0rd2.ktcheck.genv2

private class ListGenerator<T>(
    private val sizeRange: IntRange,
    private val gen: Gen<T>,
) : Gen<List<T>>() {
    override fun generate(tree: ValueTree): GenResult<List<T>> {
        val size = tree.left.producer.int(sizeRange)
        val (list, listValueShrinks) = listN(rootTree = tree.right, targetSize = size)

        return GenResult(
            value = list,
            shrinks = sequence {
                val sizeShrinks = shrink(size).filter { it in sizeRange }

                // reduce size - elements are "removed" from the end of the list
                yieldAll(sizeShrinks.map { tree.withLeft(tree.left.withValue(it)) })
                yieldAll(
                    sizeShrinks.map {
                        // reduces size by "removing" elements from the start of the list
                        tree.withLeft(tree.left.withValue(it)).withRight(tree.traverseRight(1 + size - it))
                    }
                )

                yieldAll(listValueShrinks.map { tree.withRight(it) })
            }
        )
    }

    private fun ValueTree.traverseRight(steps: Int): ValueTree =
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
        rootTree: ValueTree,
        targetSize: Int,
        currentTree: ValueTree = rootTree,
        index: Int = 0,
        values: List<T> = emptyList(),
        shrinksByIndex: List<Sequence<ValueTree>> = emptyList(),
    ): GenResult<List<T>> {
        if (index == targetSize) return GenResult(value = values, shrinks = rootTree.combineShrinks(shrinksByIndex))

        val (value, shrinks) = gen.generate(currentTree.left)

        return listN(
            rootTree = rootTree,
            targetSize = targetSize,
            currentTree = currentTree.right,
            index = index + 1,
            values = values + value,
            shrinksByIndex = shrinksByIndex + listOf(shrinks)
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
    private fun ValueTree.combineShrinks(
        shrinksByIndex: List<Sequence<ValueTree>>,
    ): Sequence<ValueTree> {
        fun reconstructTreeWithShrinkAtIndex(
            tree: ValueTree,
            index: Int,
            shrunkTree: ValueTree,
        ): ValueTree =
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

fun <T> Gen<T>.list(size: IntRange = 0..100): Gen<List<T>> = ListGenerator(size, this)

fun <T> Gen<T>.list(size: Int): Gen<List<T>> = list(size..size)
