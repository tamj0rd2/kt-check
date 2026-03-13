package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenerationException
import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asSuccess

internal class IntGen(
    private val range: IntRange,
    private val shrinkTarget: Int,
) : Generator<Int> {
    init {
        require(shrinkTarget in range) { "Shrink target $shrinkTarget must be within the range $range" }
    }

    override fun generate(root: ProviderTree): Result4k<GeneratedValue<Int>, GenerationException> {
        val value = root.provider.int(range)
        return buildResult(root, value).asSuccess()
    }

    override fun edgeCases(root: ProviderTree): List<GeneratedValue<Int>> {
        return setOf(0, range.first, range.last)
            .flatMap { listOf(it, it - 1, it + 1) }
            .filter { it in range }
            .map { buildResult(root = root.withPredeterminedValue(it), value = it) }
    }

    private fun buildResult(
        root: ProviderTree,
        value: Int,
    ): GeneratedValue<Int> {
        val shrinks = IntShrinker.shrink(value, range, shrinkTarget).map { root.withPredeterminedValue(it) }

        return GeneratedValue(
            value = value,
            shrinks = shrinks,
            usedTree = root,
        )
    }
}
