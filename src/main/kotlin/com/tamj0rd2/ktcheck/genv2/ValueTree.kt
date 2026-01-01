package com.tamj0rd2.ktcheck.genv2

import com.tamj0rd2.ktcheck.gen.deriveSeed
import com.tamj0rd2.ktcheck.genv2.Value.Predetermined
import com.tamj0rd2.ktcheck.genv2.Value.Predetermined.Choice.BooleanChoice
import com.tamj0rd2.ktcheck.genv2.Value.Predetermined.Choice.IntChoice
import kotlin.random.Random
import kotlin.random.nextInt

internal sealed interface Value {
    fun int(range: IntRange): Int
    fun bool(): Boolean

    data class Undetermined(val seed: Long) : Value {
        private val random get() = Random(seed)

        override fun int(range: IntRange): Int = random.nextInt(range)

        override fun bool(): Boolean = random.nextBoolean()
    }

    data class Predetermined(private val choice: Choice) : Value {
        sealed interface Choice {
            val value: Any?

            data class IntChoice(override val value: Int) : Choice
            data class BooleanChoice(override val value: Boolean) : Choice
        }

        override fun int(range: IntRange): Int = when {
            choice !is IntChoice -> throw InvalidReplay("Expected IntChoice but got ${choice::class.simpleName}")
            choice.value !in range -> throw InvalidReplay("IntChoice value ${choice.value} not in range $range")
            else -> choice.value
        }

        override fun bool(): Boolean = when (choice) {
            !is BooleanChoice -> throw InvalidReplay("Expected BooleanChoice but got ${choice::class.simpleName}")
            else -> choice.value
        }
    }
}

internal class InvalidReplay(message: String) : IllegalStateException(message)

@ConsistentCopyVisibility
internal data class ValueTree private constructor(
    internal val value: Value,
    private val lazyLeft: Lazy<ValueTree>,
    private val lazyRight: Lazy<ValueTree>,
) {
    companion object {
        internal fun fromSeed(seed: Long): ValueTree = ValueTree(
            value = Value.Undetermined(seed),
            lazyLeft = lazy { fromSeed(deriveSeed(seed, 1)) },
            lazyRight = lazy { fromSeed(deriveSeed(seed, 2)) },
        )
    }

    val left: ValueTree by lazyLeft
    val right: ValueTree by lazyRight

    internal fun withValue(value: Int) = copy(value = Predetermined(IntChoice(value)))
    internal fun withValue(value: Boolean) = copy(value = Predetermined(BooleanChoice(value)))

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
                appendLine("${indent}${prefix}${tree.value}")
                visualiseBranch(tree.lazyLeft, "L")?.let(::append)
                visualiseBranch(tree.lazyRight, "R")?.let(::append)
            }
        }

        return visualise(tree = this, indent = "", prefix = "", isLast = null, currentDepth = 0)
    }
}
