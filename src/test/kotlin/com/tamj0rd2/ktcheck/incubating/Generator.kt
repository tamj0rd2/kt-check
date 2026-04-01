package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.ExperimentalKtCheck
import com.tamj0rd2.ktcheck.core.Seed
import com.tamj0rd2.ktcheck.core.shrinkers.defaultShrinkTarget
import com.tamj0rd2.ktcheck.stats.Percentage
import com.tamj0rd2.ktcheck.stats.Percentage.Companion.percent
import kotlin.random.Random

data class GeneratedValue<T>(
    val value: T,
    val shrinks: Sequence<GeneratedValue<T>>,
)

/**
 * Note: methods should only ever be called by Gen. Never anywhere else.
 */
internal sealed interface Generator<T> {
    fun generate(seed: Seed): GeneratedValue<T>
    fun generateEdgeCase(seed: Seed, targetProbability: Percentage): GeneratedValue<T>?
}

@OptIn(ExperimentalKtCheck::class)
@ConsistentCopyVisibility
data class Gen<T> private constructor(
    private val impl: Generator<T>,
    private val edgeCaseProbability: Percentage = defaultEdgeCaseProbability,
) {
    internal fun generate(seed: Seed): GeneratedValue<T> {
        val rng = Random(seed.next(1).value)
        val nextSeed = seed.next(2)

        if (rng.nextDouble() <= edgeCaseProbability.value) {
            return impl.generateEdgeCase(nextSeed, edgeCaseProbability) ?: impl.generate(nextSeed)
        }

        return impl.generate(nextSeed)
    }

    fun samples(seed: Long): Sequence<T> = Seed.sequence(Seed(seed)).map { generate(it).value }

    fun withEdgeCaseProbability(probability: Percentage): Gen<T> = copy(edgeCaseProbability = probability)

    companion object {
        fun int(
            range: IntRange,
            shrinkTarget: Int = range.defaultShrinkTarget(),
        ): Gen<Int> = Gen(IntGenerator(range, shrinkTarget))

        val defaultEdgeCaseProbability = 3.percent
    }
}
