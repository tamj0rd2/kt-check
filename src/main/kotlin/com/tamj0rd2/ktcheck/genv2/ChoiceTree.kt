package com.tamj0rd2.ktcheck.genv2

import com.tamj0rd2.ktcheck.gen.deriveSeed
import kotlin.random.Random

internal sealed interface ChoiceTree {
    val left: ChoiceTree
    val right: ChoiceTree

    fun int(range: IntRange): Int

    companion object {
        internal fun from(seed: Long): ChoiceTree = RandomTree(
            seed = seed,
            lazyLeft = lazy { from(deriveSeed(seed, 1)) },
            lazyRight = lazy { from(deriveSeed(seed, 2)) },
        )

        @Suppress("unused")
        internal fun ChoiceTree.visualise(maxDepth: Int = 3, forceEval: Boolean = false): String = visualise(
            indent = "",
            prefix = "",
            isLast = null,
            currentDepth = 0,
            maxDepth = maxDepth,
            forceEval = forceEval,
        )

        private fun ChoiceTree.visualise(
            indent: String,
            prefix: String,
            isLast: Boolean?,
            currentDepth: Int,
            maxDepth: Int,
            forceEval: Boolean,
        ): String {
            if (currentDepth >= maxDepth) return "${indent}${prefix}...\n"

            val displayValue = when (this) {
                is RandomTree -> "seed(${seed})"
                is RecordedChoiceTree<*> -> "choice(${choice})"
            }

            val lazyLeft = when (this) {
                is RandomTree -> lazyLeft
                is RecordedChoiceTree<*> -> lazyLeft
            }

            val lazyRight = when (this) {
                is RandomTree -> lazyRight
                is RecordedChoiceTree<*> -> lazyRight
            }

            fun visualise(lazyTree: Lazy<ChoiceTree>, side: String): String? {
                val newIndent = when (isLast) {
                    null -> "" // Root level, no indentation
                    true -> "$indent    "
                    false -> "$indent│   "
                }

                if (!lazyTree.isInitialized() && !forceEval) return null

                return lazyTree.value.visualise(newIndent, "├─$side: ", false, currentDepth + 1, maxDepth, forceEval)
            }

            return buildString {
                appendLine("${indent}${prefix}${displayValue}")
                visualise(lazyLeft, "L")?.let(::append)
                visualise(lazyRight, "R")?.let(::append)
            }
        }
    }
}

internal data class RandomTree(
    internal val seed: Long,
    internal val lazyLeft: Lazy<ChoiceTree>,
    internal val lazyRight: Lazy<ChoiceTree>,
) : ChoiceTree {
    private val random get() = Random(seed)

    override val left: ChoiceTree by lazyLeft
    override val right: ChoiceTree by lazyRight

    override fun int(range: IntRange) = range.random(random)
}

internal data class RecordedChoiceTree<out T>(
    internal val choice: T,
    internal val lazyLeft: Lazy<ChoiceTree>,
    internal val lazyRight: Lazy<ChoiceTree>,
) : ChoiceTree {
    override val left: ChoiceTree by lazyLeft
    override val right: ChoiceTree by lazyRight

    override fun int(range: IntRange): Int {
        if (choice !is Int) TODO("handle this")
        if (choice !in range) TODO("handle int out of range")
        return choice
    }
}

internal fun <T> ChoiceTree.withChoice(value: T): ChoiceTree = when (this) {
    is RandomTree -> RecordedChoiceTree(
        choice = value,
        lazyLeft = lazyLeft,
        lazyRight = lazyRight,
    )

    is RecordedChoiceTree<*> -> RecordedChoiceTree(
        choice = value,
        lazyLeft = lazyLeft,
        lazyRight = lazyRight,
    )
}

internal fun ChoiceTree.withLeft(left: ChoiceTree): ChoiceTree = when (this) {
    is RandomTree -> copy(lazyLeft = lazyOf(left))
    is RecordedChoiceTree<*> -> copy(lazyLeft = lazyOf(left))
}

internal fun ChoiceTree.withRight(right: ChoiceTree): ChoiceTree = when (this) {
    is RandomTree -> copy(lazyRight = lazyOf(right))
    is RecordedChoiceTree<*> -> copy(lazyRight = lazyOf(right))
}
