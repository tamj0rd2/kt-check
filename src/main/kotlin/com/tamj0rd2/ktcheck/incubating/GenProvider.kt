package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.core.GenerationContext
import com.tamj0rd2.ktcheck.core.Seed
import kotlin.random.Random

internal sealed interface GenProvider<T> {
    fun generate(ctx: GenContext): GenResult<T>
}

internal data class GenResult<T>(
    val value: T,
    val shrinks: Sequence<GenResult<T>>,
) {
    fun <R> map(fn: (T) -> R): GenResult<R> = GenResult(
        value = fn(value),
        shrinks = shrinks.map { it.map(fn) },
    )
}

@ConsistentCopyVisibility
internal data class GenContext private constructor(
    val seed: Seed,
    val generateEdgeCase: Boolean,
) : GenerationContext {
    val random get() = Random(seed.value)
    val left by lazy { new(seed.next(1)) }
    val right by lazy { new(seed.next(2)) }

    companion object {
        fun new(seed: Seed): GenContext = new(seed, ShouldGenerateEdgeCase.BasedOnRng)

        fun new(seed: Seed, shouldGenerateEdgeCase: ShouldGenerateEdgeCase): GenContext = GenContext(
            seed = seed,
            generateEdgeCase = shouldGenerateEdgeCase(seed.next(3)),
        )
    }
}

internal fun interface ShouldGenerateEdgeCase {
    operator fun invoke(seed: Seed): Boolean

    data object BasedOnRng : ShouldGenerateEdgeCase {
        override fun invoke(seed: Seed): Boolean {
            return Random(seed.value).nextBoolean()
        }
    }

    data object Always : ShouldGenerateEdgeCase {
        override fun invoke(seed: Seed): Boolean = true
    }
}
