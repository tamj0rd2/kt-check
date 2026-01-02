package com.tamj0rd2.ktcheck.gen

internal class InvalidReplay(message: String) : IllegalStateException(message)

@ConsistentCopyVisibility
internal data class ValueTree private constructor(
    internal val producer: ValueProducer,
    private val lazyLeft: Lazy<ValueTree>,
    private val lazyRight: Lazy<ValueTree>,
) {
    companion object {
        internal fun fromSeed(seed: Long): ValueTree = ValueTree(
            producer = RandomValueProducer(seed),
            lazyLeft = lazy { fromSeed(deriveSeed(seed, 1)) },
            lazyRight = lazy { fromSeed(deriveSeed(seed, 2)) },
        )
    }

    val left: ValueTree by lazyLeft
    val right: ValueTree by lazyRight

    internal fun withValue(value: Int) =
        copy(producer = PredeterminedValue(PredeterminedValue.Choice.IntChoice(value)))

    internal fun withValue(value: Boolean) =
        copy(producer = PredeterminedValue(PredeterminedValue.Choice.BooleanChoice(value)))

    internal fun withLeft(left: ValueTree) = copy(lazyLeft = lazyOf(left))

    internal fun withRight(right: ValueTree) = copy(lazyRight = lazyOf(right))

    fun combineShrinks(
        leftShrinks: Sequence<ValueTree>,
        rightShrinks: Sequence<ValueTree>,
    ): Sequence<ValueTree> = leftShrinks.map { withLeft(it) } + rightShrinks.map { withRight(it) }

    override fun toString(): String = visualise()

    @Suppress("unused")
    internal fun visualise(maxDepth: Int = 3, forceEval: Boolean = false): String {
        fun visualise(
            tree: ValueTree,
            indent: String,
            prefix: String,
            isLast: Boolean?,
            currentDepth: Int,
        ): String {
            if (currentDepth >= maxDepth) return "${indent}${prefix}...\n"

            fun visualiseBranch(lazyTree: Lazy<ValueTree>, side: String): String? {
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
