package com.tamj0rd2.ktcheck.producer

@ConsistentCopyVisibility
internal data class ProducerTree private constructor(
    internal val producer: ValueProducer,
    private val lazyLeft: Lazy<ProducerTree>,
    private val lazyRight: Lazy<ProducerTree>,
) {
    companion object {
        internal fun fromSeed(seed: Long) = fromSeed(Seed(seed))

        internal fun fromSeed(seed: Seed): ProducerTree = ProducerTree(
            producer = RandomValueProducer(seed),
            lazyLeft = lazy { fromSeed(seed.next(1)) },
            lazyRight = lazy { fromSeed(seed.next(2)) },
        )
    }

    val left: ProducerTree by lazyLeft
    val right: ProducerTree by lazyRight

    internal fun withValue(value: Int) =
        copy(producer = PredeterminedValue(Primitive.Int(value)))

    internal fun withValue(value: Boolean) =
        copy(producer = PredeterminedValue(Primitive.Bool(value)))

    internal fun withLeft(left: ProducerTree) = copy(lazyLeft = lazyOf(left))

    internal fun withRight(right: ProducerTree) = copy(lazyRight = lazyOf(right))

    internal fun combineShrinks(
        leftShrinks: Sequence<ProducerTree>,
        rightShrinks: Sequence<ProducerTree>,
    ): Sequence<ProducerTree> = leftShrinks.map { withLeft(it) } + rightShrinks.map { withRight(it) }

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
            if (currentDepth >= maxDepth) return "${indent}${prefix}...\n"

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
