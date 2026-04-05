package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenerationException
import com.tamj0rd2.ktcheck.core.GenerationContext
import com.tamj0rd2.ktcheck.core.Seed
import dev.forkhandles.result4k.Result4k
import kotlin.random.Random

internal sealed interface GenProvider<T> {
    fun generate(ctx: GenContext): Result4k<GeneratedValue<T>, GenerationException>
}

internal data class GeneratedValue<T>(
    val value: T,
    val shrinks: Sequence<GeneratedValue<T>>,
) {
    fun <R> map(fn: (T) -> R): GeneratedValue<R> = GeneratedValue(
        value = fn(value),
        shrinks = shrinks.map { it.map(fn) },
    )

    fun filter(fn: (T) -> Boolean): GeneratedValue<T>? {
        if (!fn(value)) return null
        return copy(shrinks = shrinks.mapNotNull { it.filter(fn) })
    }
}

internal sealed class GenContext : GenerationContext {
    abstract val generateEdgeCase: Boolean

    protected abstract val lazyLeft: Lazy<GenContext>
    protected abstract val lazyRight: Lazy<GenContext>

    val left get() = lazyLeft.value
    val right get() = lazyRight.value

    abstract fun int(range: IntRange): Int

    companion object {
        fun new(seed: Seed): GenContext = new(seed, ShouldGenerateEdgeCase.BasedOnRng)

        fun new(seed: Seed, shouldGenerateEdgeCase: ShouldGenerateEdgeCase): GenContext = InitialGenContext(
            seed = seed,
            lazyLeft = lazy { new(seed.next(1), shouldGenerateEdgeCase) },
            lazyRight = lazy { new(seed.next(2), shouldGenerateEdgeCase) },
            generateEdgeCase = shouldGenerateEdgeCase(seed.next(3)),
        )
    }

    protected abstract fun formatNode(): String

    internal fun visualise(maxDepth: Int = 3, forceEval: Boolean = false): String {
        fun visualise(
            tree: GenContext,
            indent: String,
            prefix: String,
            isLast: Boolean?,
            currentDepth: Int,
        ): String {
            if (currentDepth >= maxDepth) return "${indent}${prefix}...\n"

            fun visualiseBranch(lazyTree: Lazy<GenContext>, side: String): String? {
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
                appendLine("${indent}${prefix}${tree.formatNode()}")
                visualiseBranch(tree.lazyLeft, "L")?.let(::append)
                visualiseBranch(tree.lazyRight, "R")?.let(::append)
            }
        }

        return visualise(tree = this, indent = "", prefix = "", isLast = null, currentDepth = 0)
    }
}

private data class InitialGenContext(
    val seed: Seed,
    override val generateEdgeCase: Boolean,
    override val lazyLeft: Lazy<GenContext>,
    override val lazyRight: Lazy<GenContext>,
) : GenContext() {
    private val random get() = Random(seed.value)

    override fun int(range: IntRange): Int {
        return range.random(random)
    }

    override fun toString(): String = visualise(maxDepth = 10)
    override fun formatNode() = "$seed | generateEdgeCase=$generateEdgeCase"
}

internal fun interface ShouldGenerateEdgeCase {
    operator fun invoke(seed: Seed): Boolean

    data object BasedOnRng : ShouldGenerateEdgeCase {
        override fun invoke(seed: Seed): Boolean {
            return Random(seed.value).nextDouble() <= 0.1
        }
    }

    data object Always : ShouldGenerateEdgeCase {
        override fun invoke(seed: Seed): Boolean = true
    }
}
