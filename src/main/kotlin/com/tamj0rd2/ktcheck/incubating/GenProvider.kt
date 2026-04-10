package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenerationException
import com.tamj0rd2.ktcheck.core.GenerationContext
import com.tamj0rd2.ktcheck.core.Seed
import dev.forkhandles.result4k.Result4k
import kotlin.random.Random

internal sealed interface GenProvider<T> {
    fun generate(rootCtx: GenContext): Result4k<GeneratedValue<T>, GenerationException>
}

internal data class GeneratedValue<T>(
    val ctx: GenContext,
    val value: T,
    val shrinks: Sequence<GenContext>,
) {
    override fun toString(): String {
        return "value $value produced from:\n$ctx"
    }

    fun <R> map(fn: (T) -> R): GeneratedValue<R> = GeneratedValue(
        ctx = ctx,
        value = fn(value),
        shrinks = shrinks,
    )
}

sealed interface PrimitiveProvider {
    fun int(range: IntRange): Int
}

private data class RngBasedPrimitiveProvider(
    private val seed: Seed,
) : PrimitiveProvider {
    private val random get() = Random(seed.value)

    override fun int(range: IntRange): Int {
        return range.random(random)
    }
}

private data class PredeterminedPrimitiveProvider(
    private val primitive: Any,
) : PrimitiveProvider {
    override fun int(range: IntRange): Int = when (primitive) {
        !is Int -> error("$primitive is not an int")
        !in range -> error("$primitive is out of range $range")
        else -> primitive
    }
}

@ConsistentCopyVisibility
internal data class GenContext private constructor(
    val primitives: PrimitiveProvider,
    private val lazyLeft: Lazy<GenContext>,
    private val lazyRight: Lazy<GenContext>,
    val generateEdgeCase: Boolean,
    private val metadata: Set<String>,
) : GenerationContext {
    val left get() = lazyLeft.value
    val right get() = lazyRight.value

    fun withShrunkPrimitive(primitive: Any): GenContext = copy(primitives = PredeterminedPrimitiveProvider(primitive))
    fun withShrunkLeft(newLeft: GenContext) = copy(lazyLeft = lazyOf(newLeft))
    fun withShrunkRight(newRight: GenContext) = copy(lazyRight = lazyOf(newRight))

    fun traverseRight() = generateSequence(this) { it.right }

    fun withMetadata(key: String) = copy(metadata = metadata + key)
    fun hasMetadata(key: String): Boolean = key in metadata

    companion object {
        fun new(
            seed: Seed,
            influenceEdgeCases: InfluenceGeneration = InfluenceGeneration.BasedOnRng,
        ): GenContext = GenContext(
            primitives = RngBasedPrimitiveProvider(seed),
            lazyLeft = lazy { new(seed.next(1), influenceEdgeCases) },
            lazyRight = lazy { new(seed.next(2), influenceEdgeCases) },
            generateEdgeCase = influenceEdgeCases(seed.next(3)),
            metadata = emptySet(),
        )
    }

    override fun toString(): String = visualise(maxDepth = 10)

    private fun formatNode() = "$primitives | generateEdgeCase=$generateEdgeCase"

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

internal fun interface InfluenceGeneration {
    operator fun invoke(seed: Seed): Boolean

    data object BasedOnRng : InfluenceGeneration {
        override fun invoke(seed: Seed): Boolean {
            return Random(seed.value).nextDouble() <= 0.1
        }
    }

    data object Always : InfluenceGeneration {
        override fun invoke(seed: Seed): Boolean = true
    }
}
