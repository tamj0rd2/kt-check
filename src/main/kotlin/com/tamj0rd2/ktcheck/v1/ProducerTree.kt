package com.tamj0rd2.ktcheck.v1

import com.tamj0rd2.ktcheck.Seed

@ConsistentCopyVisibility
internal data class ProducerTree private constructor(
    internal val producer: ValueProducer,
    private val lazyLeft: Lazy<ProducerTree>,
    private val lazyRight: Lazy<ProducerTree>,
) {
    val left: ProducerTree by lazyLeft
    val right: ProducerTree by lazyRight

    companion object {
        internal fun new(seed: Seed = Seed.random()): ProducerTree = ProducerTree(
            producer = RandomValueProducer(seed),
            lazyLeft = lazy { new(seed.next(1)) },
            lazyRight = lazy { new(seed.next(2)) },
        )
    }

    internal fun traverseRight(steps: Int): ProducerTree =
        if (steps <= 0) this else right.traverseRight(steps - 1)

    internal fun withValue(value: Any) = copy(producer = PredeterminedValue(value))
    internal fun withLeft(left: ProducerTree) = copy(lazyLeft = lazyOf(left))
    internal fun withRight(right: ProducerTree) = copy(lazyRight = lazyOf(right))

    internal fun combineShrinks(
        leftShrinks: Sequence<ProducerTree>,
        rightShrinks: Sequence<ProducerTree>,
    ): Sequence<ProducerTree> = leftShrinks.map { withLeft(it) } + rightShrinks.map { withRight(it) }


    /**
     * Combines shrinks from values generated using the left generation, right continuation pattern.
     *
     * Each shrink applies to one element at a specific index, while keeping other elements unchanged.
     * This enables element-wise shrinking where each element can shrink independently.
     *
     * @param shrinksByIndex A list of shrink sequences, one for each list element position
     */
    internal fun combineShrinks(
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

    override fun toString(): String = visualise(maxDepth = 10)

    @Suppress("unused")
    internal fun visualise(maxDepth: Int = 3, forceEval: Boolean = false): String {
        fun visualise(
            tree: ProducerTree,
            indent: String,
            prefix: String,
            isLast: Boolean?,
            currentDepth: Int,
        ): String {
            if (currentDepth >= maxDepth) return ""

            fun visualiseBranch(lazyTree: Lazy<ProducerTree>, side: String): String? {
                val newIndent = when (isLast) {
                    null -> "" // Root level, no indentation
                    true -> "$indent    "
                    false -> "$indent│   "
                }

                if (!lazyTree.isInitialized() && !forceEval) return null

                return visualise(
                    tree = lazyTree.value,
                    indent = newIndent,
                    prefix = "├─$side: ",
                    isLast = false,
                    currentDepth = currentDepth + 1
                )
            }

            return buildString {
                appendLine("${indent}${prefix}${tree.producer}")
                visualiseBranch(tree.lazyLeft, "L")?.let(::append)
                visualiseBranch(tree.lazyRight, "R")?.let(::append)
            }
        }

        return visualise(tree = this, indent = "", prefix = "", isLast = null, currentDepth = 0)
    }
}

internal class ProducerTreeDsl(private var subject: ProducerTree) {
    fun left(tree: ProducerTree) {
        subject = subject.withLeft(tree)
    }

    fun left(value: Any) {
        left(subject.left.withValue(value))
    }

    fun left(block: ProducerTreeDsl.() -> Unit) {
        val newLeftTree = ProducerTreeDsl(subject.left).apply(block).subject
        subject = subject.withLeft(newLeftTree)
    }

    fun left(value: Any, block: ProducerTreeDsl.() -> Unit) {
        left(value)
        left(block)
    }

    fun right(tree: ProducerTree) {
        subject = subject.withRight(tree)
    }

    fun right(value: Any) {
        right(subject.right.withValue(value))
    }

    fun right(block: ProducerTreeDsl.() -> Unit) {
        val newRightTree = ProducerTreeDsl(subject.right).apply(block).subject
        subject = subject.withRight(newRightTree)
    }

    fun right(value: ProducerTree, block: ProducerTreeDsl.() -> Unit) {
        right(value)
        right(block)
    }

    fun right(value: Any, block: ProducerTreeDsl.() -> Unit) {
        right(value)
        right(block)
    }

    companion object {
        fun producerTree(seed: Seed = Seed.random(), block: ProducerTreeDsl.() -> Unit): ProducerTree =
            ProducerTree.new(seed).copy(block)

        fun producerTree(value: Any): ProducerTree =
            ProducerTree.new().withValue(value)

        fun ProducerTree.copy(block: ProducerTreeDsl.() -> Unit): ProducerTree =
            ProducerTreeDsl(this).apply(block).subject
    }
}
