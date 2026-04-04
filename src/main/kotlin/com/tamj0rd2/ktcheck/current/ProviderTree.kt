package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.core.GenerationContext
import com.tamj0rd2.ktcheck.core.Seed
import kotlin.random.Random
import kotlin.random.nextInt

@ConsistentCopyVisibility
internal data class ProviderTree private constructor(
    val provider: ValueProvider,
    private val lazyLeft: Lazy<ProviderTree>,
    private val lazyRight: Lazy<ProviderTree>,
) : GenerationContext {
    val left: ProviderTree get() = lazyLeft.value
    val right: ProviderTree get() = lazyRight.value

    fun traversingRight() = generateSequence(this) { it.right }

    // todo: make this a tree type of its own, rather than a provider?
    val isTerminator: Boolean get() = provider is TerminalValueProvider

    fun withPredeterminedValue(value: Int): ProviderTree =
        copy(provider = PredeterminedValueProvider(value, provider))

    fun withLeft(left: ProviderTree): ProviderTree = copy(lazyLeft = lazyOf(left))
    fun withRight(right: ProviderTree): ProviderTree = copy(lazyRight = lazyOf(right))

    companion object {
        fun new(seed: Seed = Seed.random()): ProviderTree = ProviderTree(
            provider = RandomValueProvider(seed),
            lazyLeft = lazy { new(seed.next(1)) },
            lazyRight = lazy { new(seed.next(2)) },
        )

        val terminal
            get(): ProviderTree = ProviderTree(
                provider = TerminalValueProvider,
                lazyLeft = lazy { terminal },
                lazyRight = lazy { terminal },
            )
    }

    override fun toString(): String = visualise(maxDepth = 10)

    internal fun visualise(maxDepth: Int = 3, forceEval: Boolean = false): String {
        fun visualise(
            tree: ProviderTree,
            indent: String,
            prefix: String,
            isLast: Boolean?,
            currentDepth: Int,
        ): String {
            if (currentDepth >= maxDepth) return "${indent}${prefix}...\n"

            fun visualiseBranch(lazyTree: Lazy<ProviderTree>, side: String): String? {
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
                appendLine("${indent}${prefix}${tree.provider}")
                visualiseBranch(tree.lazyLeft, "L")?.let(::append)
                visualiseBranch(tree.lazyRight, "R")?.let(::append)
            }
        }

        return visualise(tree = this, indent = "", prefix = "", isLast = null, currentDepth = 0)
    }
}

internal sealed interface ValueProvider {
    fun int(range: IntRange): Int
}

internal interface DecoratedValueProvider : ValueProvider {
    val delegate: ValueProvider
}

data object TerminalValueProvider : ValueProvider {
    override fun int(range: IntRange): Int {
        error("${TerminalValueProvider::class.simpleName} cannot produce values")
    }
}

private data class RandomValueProvider(private val seed: Seed) : ValueProvider {
    private val random get() = Random(seed.value)

    override fun int(range: IntRange): Int {
        return random.nextInt(range)
    }
}

private data class PredeterminedValueProvider(
    private val value: Any,
    override val delegate: ValueProvider,
) : DecoratedValueProvider {
    override fun int(range: IntRange): Int =
        when (value) {
            !is Int,
            !in range,
                -> delegate.int(range)

            else -> value
        }
}
