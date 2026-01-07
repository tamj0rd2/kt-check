package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.producer.ProducerTree

private class CombinerGenerator<T>(
    private val block: CombinerContext.() -> T,
) : Gen<T>() {
    override fun GenContext.generate(): GenResult<T> {
        val initialTree = tree
        return CombinerContext(initialTree, mode).run {
            val value = block(this)
            val shrinks = initialTree.combineShrinks(shrinksByIndex)
            GenResult(value, shrinks)
        }
    }
}

class CombinerContext internal constructor(
    private var tree: ProducerTree,
    private val mode: GenMode,
) {
    internal val shrinksByIndex = mutableListOf<Sequence<ProducerTree>>()

    fun <T> Gen<T>.bind(): T {
        val (value, shrinks) = generate(tree.left, mode)
        tree = tree.right
        shrinksByIndex.add(shrinks)
        return value
    }
}

/**
 * Combines multiple generators into a single generator using a builder-style DSL.
 * Each generator in the block is bound sequentially, and their shrinks are combined.
 *
 * Example:
 * ```
 * val gen = Gen.combine {
 *     val x = Gen.int().bind()
 *     val y = Gen.int().bind()
 *     x to y
 * }
 * ```
 *
 * This is equivalent to using [plus] but with a more convenient syntax:
 * ```
 * val gen = (Gen.int() + Gen.bool()).map { (x, y) -> x + y }
 * ```
 *
 * **Warning about conditionals:** The combiner requires that bind functions will be called in the same order each time.
 * Conditionals that affect whether trailing [CombinerContext.bind] calls are called will shrink correctly.
 * However, conditionals that skip non-trailing [CombinerContext.bind] calls will cause invalid shrinks.
 */
fun <T> Gen.Companion.combine(block: CombinerContext.() -> T): Gen<T> = CombinerGenerator(block)
