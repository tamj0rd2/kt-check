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

    private fun ChoiceTree.combineShrinks(
        shrinksByIndex: List<Sequence<ChoiceTree>>,
    ): Sequence<ChoiceTree> = shrinksByIndex.asSequence().flatMapIndexed { i, shrinks ->
        shrinks.map { shrunkTree ->
            reconstructTreeWithShrinkAtIndex(this, i, shrunkTree)
        }
    }

    private fun reconstructTreeWithShrinkAtIndex(
        rootTree: ChoiceTree,
        index: Int,
        shrunkTree: ChoiceTree,
    ): ChoiceTree =
        if (index == 0) {
            rootTree.withLeft(shrunkTree)
        } else {
            rootTree.withRight(reconstructTreeWithShrinkAtIndex(rootTree.right, index - 1, shrunkTree))
        }
}

/**
 * Generates a list of values, shrinking both the size and elements.
 */
fun <T> Gen<T>.list(size: IntRange = 0..100): Gen<List<T>> = list(Gen.int(size))

fun <T> Gen<T>.list(size: Gen<Int>): Gen<List<T>> = size.flatMap(::list)

fun <T> Gen<T>.list(size: Int): Gen<List<T>> = ListGenerator(size, this)
