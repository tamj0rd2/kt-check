package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.core.Seed
import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker
import com.tamj0rd2.ktcheck.incubating.Generator.Companion.TARGET_EDGE_CASE_PROBABILITY
import kotlin.random.Random

data class IntGenerator(
    internal val range: IntRange,
    internal val shrinkTarget: Int,
    private val userProvidedEdgeCases: Set<Int>,
) : Generator<Int> {
    init {
        val illegalEdgeCases = userProvidedEdgeCases.filter { it !in range }
        require(illegalEdgeCases.isEmpty()) {
            "Expected edge cases to be within range $range, but these weren't: $illegalEdgeCases"
        }
    }

    internal val edgeCases = getDefaultEdgeCases(range, shrinkTarget) + userProvidedEdgeCases
    private val edgeCasesAreNaturallyUnlikely = (edgeCases.size / range.size.toDouble()) < TARGET_EDGE_CASE_PROBABILITY

    override fun generate(seed: Seed): GeneratedValue<Int> {
        val random = Random(seed.value)
        val generateEdgeCase = random.nextDouble() <= TARGET_EDGE_CASE_PROBABILITY && edgeCasesAreNaturallyUnlikely
        val value = if (generateEdgeCase) edgeCases.random(random) else range.random(random)
        return buildGeneratedValue(value)
    }

    private fun buildGeneratedValue(value: Int): GeneratedValue<Int> = GeneratedValue(
        value = value,
        shrinks = IntShrinker.shrink(value, range, shrinkTarget).map(::buildGeneratedValue),
    )

    companion object {
        private fun getDefaultEdgeCases(range: IntRange, shrinkTarget: Int) =
            listOf(range.first, shrinkTarget, range.last)
                .flatMap { listOf(it - 1, it, it + 1) }
                .filter { it in range }
                .toSet()
    }
}

val IntRange.size get() = last.toLong() - first.toLong()
