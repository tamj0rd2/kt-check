package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.core.Seed
import com.tamj0rd2.ktcheck.core.shrinkers.defaultShrinkTarget

data class GeneratedValue<T>(
    val value: T,
    val shrinks: Sequence<GeneratedValue<T>>,
)

interface Generator<T> {
    fun generate(seed: Seed): GeneratedValue<T>

    companion object {
        const val TARGET_EDGE_CASE_PROBABILITY = 0.03
    }
}

fun <T> Generator<T>.samples(seed: Long) = Seed.sequence(Seed(seed)).map { generate(it).value }

object Gens {
    fun int(
        range: IntRange,
        shrinkTarget: Int = range.defaultShrinkTarget(),
        extraEdgeCases: Set<Int> = emptySet(),
    ) = IntGenerator(range, shrinkTarget, extraEdgeCases)
}
