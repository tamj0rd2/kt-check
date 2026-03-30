package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.core.Seed
import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker
import kotlin.random.Random

data class IntGenerator(
    private val range: IntRange,
    private val shrinkTarget: Int,
) : Generator<Int> {
    override fun generate(seed: Seed): GeneratedValue<Int> {
        return buildGeneratedValue(range.random(Random(seed.value)))
    }

    private val edgeCases = listOf(range.first, shrinkTarget, range.last)
        .flatMap { listOf(it - 1, it, it + 1) }
        .filter { it in range }
        .toSet()

    private val trueEdgeCaseProbability = edgeCases.size / range.size.toDouble()

    override fun generateEdgeCase(seed: Seed, targetProbability: Probability): GeneratedValue<Int>? {
        val edgeCasesAreLikelyNaturally = trueEdgeCaseProbability >= targetProbability.value
        if (edgeCasesAreLikelyNaturally) return null

        return buildGeneratedValue(edgeCases.random(Random(seed.value)))
    }

    private fun buildGeneratedValue(value: Int): GeneratedValue<Int> = GeneratedValue(
        value = value,
        shrinks = IntShrinker.shrink(value, range, shrinkTarget).map(::buildGeneratedValue),
    )
}

val IntRange.size get() = last.toLong() - first.toLong()
