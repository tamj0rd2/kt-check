package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.ExperimentalKtCheck
import com.tamj0rd2.ktcheck.core.Seed
import com.tamj0rd2.ktcheck.core.shrinkers.defaultShrinkTarget
import com.tamj0rd2.ktcheck.incubating.Probability.Companion.percent
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
    fun generateEdgeCase(seed: Seed, targetProbability: Probability): GeneratedValue<T>?
}

@OptIn(ExperimentalKtCheck::class)
@ConsistentCopyVisibility
data class Gen<T> private constructor(
    private val impl: Generator<T>,
    private val edgeCaseProbability: Probability = defaultEdgeCaseProbability,
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

    fun withEdgeCaseProbability(probability: Probability): Gen<T> = copy(edgeCaseProbability = probability)

    companion object {
        fun int(
            range: IntRange,
            shrinkTarget: Int = range.defaultShrinkTarget(),
        ): Gen<Int> = Gen(IntGenerator(range, shrinkTarget))

        val defaultEdgeCaseProbability = 3.percent
    }
}

@JvmInline
value class Probability private constructor(val value: Double) {
    init {
        require(value in 0.0..1.0) { "Probability must be in range 0..1 but got $value" }
    }

    val asPercentage get() = value * 100

    companion object {
        val Int.percent get() = ofPercentage(this)

        fun of(probability: Double): Probability = Probability(probability)

        fun ofPercentage(percentage: Double): Probability = Probability(percentage / 100)
        fun ofPercentage(percentage: Int): Probability = ofPercentage(percentage.toDouble())
    }
}
