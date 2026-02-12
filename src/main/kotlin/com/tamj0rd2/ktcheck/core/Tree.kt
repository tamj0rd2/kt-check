package com.tamj0rd2.ktcheck.core

internal data class Tree<V>(
    val data: V,
    private val lazyLeft: Lazy<Tree<V>>,
    private val lazyRight: Lazy<Tree<V>>,
) {
    val left get() = lazyLeft.value
    val right get() = lazyRight.value

    fun withData(data: V): Tree<V> = copy(data = data)
    fun withLeft(left: Tree<V>): Tree<V> = copy(lazyLeft = lazyOf(left))
    fun withRight(right: Tree<V>): Tree<V> = copy(lazyRight = lazyOf(right))

    override fun toString(): String = visualise(maxDepth = 10)

    internal fun visualise(maxDepth: Int = 3, forceEval: Boolean = false): String {
        fun visualise(
            tree: Tree<V>,
            indent: String,
            prefix: String,
            isLast: Boolean?,
            currentDepth: Int,
        ): String {
            if (currentDepth >= maxDepth) return "${indent}${prefix}...\n"

            fun visualiseBranch(lazyTree: Lazy<Tree<V>>, side: String): String? {
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
                appendLine("${indent}${prefix}${tree.data}")
                visualiseBranch(tree.lazyLeft, "L")?.let(::append)
                visualiseBranch(tree.lazyRight, "R")?.let(::append)
            }
        }

        return visualise(tree = this, indent = "", prefix = "", isLast = null, currentDepth = 0)
    }
}
