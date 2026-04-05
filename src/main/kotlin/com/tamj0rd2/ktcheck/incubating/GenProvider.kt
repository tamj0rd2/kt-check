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

@ConsistentCopyVisibility
internal data class GenContext private constructor(
    val seed: Seed,
    val generateEdgeCase: Boolean,
    private val lazyLeft: Lazy<GenContext>,
    private val lazyRight: Lazy<GenContext>,
) : GenerationContext {
    val random get() = Random(seed.value)
    val left by lazyLeft
    val right by lazyRight

    override fun toString(): String {
        return """
            Root: ${formatNode()}
            Left: ${left.formatNode()}
            Rght: ${right.formatNode()}
        """.trimIndent()
    }

    private fun formatNode() = "$seed | generateEdgeCase=$generateEdgeCase"

    companion object {
        fun new(seed: Seed): GenContext = new(seed, ShouldGenerateEdgeCase.BasedOnRng)

        fun new(seed: Seed, shouldGenerateEdgeCase: ShouldGenerateEdgeCase): GenContext = GenContext(
            seed = seed,
            lazyLeft = lazy { new(seed.next(1), shouldGenerateEdgeCase) },
            lazyRight = lazy { new(seed.next(2), shouldGenerateEdgeCase) },
            generateEdgeCase = shouldGenerateEdgeCase(seed.next(3)),
        )
    }
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
