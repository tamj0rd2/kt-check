package com.tamj0rd2.ktcheck.genv2

private class ListGenerator<T>(
    private val size: Int,
    private val gen: Gen<T>,
) : Gen<List<T>>() {
    init {
        require(size >= 0) { "Size must be non-negative" }
    }

    override fun generate(tree: ChoiceTree): GenResult<List<T>> = when (size) {
        0 -> GenResult(emptyList(), emptySequence())
        1 -> list1().generate(tree)
        2 -> list2().generate(tree)
        else -> listN(rootTree = tree)
    }

    private fun list1() = gen.map(::listOf)

    private fun list2() = (gen + gen).map { (a, b) -> listOf(a, b) }

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
        rootTree: ChoiceTree,
        currentTree: ChoiceTree = rootTree,
        index: Int = 0,
        values: List<T> = emptyList(),
        shrinksByIndex: List<Sequence<ChoiceTree>> = emptyList(),
    ): GenResult<List<T>> {
        if (index == size) return GenResult(value = values, shrinks = rootTree.combineShrinks(shrinksByIndex))

        val (value, shrinks) = gen.generate(currentTree.left)

        return listN(
            rootTree = rootTree,
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
     * @return A sequence of choice trees, each representing the original tree with one element shrunk
     */
    private fun ChoiceTree.combineShrinks(
        shrinksByIndex: List<Sequence<ChoiceTree>>,
    ): Sequence<ChoiceTree> {
        fun reconstructTreeWithShrinkAtIndex(
            tree: ChoiceTree,
            index: Int,
            shrunkTree: ChoiceTree,
        ): ChoiceTree =
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

fun <T> Gen<T>.list(size: IntRange = 0..100): Gen<List<T>> = list(Gen.int(size))

fun <T> Gen<T>.list(size: Gen<Int>): Gen<List<T>> = size.flatMap(::list)

fun <T> Gen<T>.list(size: Int): Gen<List<T>> = ListGenerator(size, this)
