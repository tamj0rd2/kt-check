package com.tamj0rd2.ktcheck.genv2

import com.tamj0rd2.ktcheck.gen.deriveSeed
import kotlin.random.Random
import kotlin.random.nextInt

internal sealed interface Choice {
    fun int(range: IntRange): Int

    data class Undetermined(val seed: Long) : Choice {
        private val random get() = Random(seed)

        override fun int(range: IntRange): Int = random.nextInt(range)
    }

    data class Predetermined(val value: Any?) : Choice {
        override fun int(range: IntRange): Int = when (value) {
            !is Int -> TODO("handle this")
            !in range -> TODO("handle int out of range")
            else -> value
        }
    }
}

@ConsistentCopyVisibility
internal data class ChoiceTree private constructor(
    private val choice: Choice,
    private val lazyLeft: Lazy<ChoiceTree>,
    private val lazyRight: Lazy<ChoiceTree>,
) {
    constructor(seed: Long) : this(
        choice = Choice.Undetermined(seed),
        lazyLeft = lazy { ChoiceTree(deriveSeed(seed, 1)) },
        lazyRight = lazy { ChoiceTree(deriveSeed(seed, 2)) },
    )

    val left: ChoiceTree by lazyLeft
    val right: ChoiceTree by lazyRight

    fun int(range: IntRange): Int = when (choice) {
        is Choice.Predetermined ->
            when (val value = choice.value) {
                !is Int -> TODO("handle this")
                !in range -> TODO("handle int out of range")
                else -> value
            }

        is Choice.Undetermined -> Random(choice.seed).nextInt(range)
    }

    internal fun withChoice(value: Any?) = copy(choice = Choice.Predetermined(value))

    internal fun withLeft(left: ChoiceTree) = copy(lazyLeft = lazyOf(left))

    internal fun withRight(right: ChoiceTree) = copy(lazyRight = lazyOf(right))

    @Suppress("unused")
    internal fun visualise(maxDepth: Int = 3, forceEval: Boolean = false): String {
        fun visualise(
            tree: ChoiceTree,
            indent: String,
            prefix: String,
            isLast: Boolean?,
            currentDepth: Int,
        ): String {
            if (currentDepth >= maxDepth) return "${indent}${prefix}...\n"

            val displayValue = when (val choice = tree.choice) {
                is Choice.Undetermined -> "seed(${choice.seed})"
                is Choice.Predetermined -> "choice(${choice.value})"
            }

            fun visualiseBranch(lazyTree: Lazy<ChoiceTree>, side: String): String? {
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
                appendLine("${indent}${prefix}${displayValue}")
                visualiseBranch(tree.lazyLeft, "L")?.let(::append)
                visualiseBranch(tree.lazyRight, "R")?.let(::append)
            }
        }

        return visualise(tree = this, indent = "", prefix = "", isLast = null, currentDepth = 0)
    }
}
