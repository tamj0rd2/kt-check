package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenerationException
import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asSuccess

internal data class IntGen(
    private val range: IntRange,
    private val shrinkTarget: Int,
) : GenProvider<Int> {
    init {
        require(shrinkTarget in range) { "shrinkTarget $shrinkTarget not in range $range" }
    }

    private val edgeCases = setOf(range.first, range.first + 1, -1, 0, 1, range.last - 1, range.last)
        .filter { it in range }
        .distinct()

    override fun generate(rootCtx: GenContext): Result4k<GeneratedValue<Int>, GenerationException> {
        val value = if (rootCtx.generateEdgeCase) {
            edgeCases[rootCtx.primitives.int(edgeCases.indices)]
        } else {
            rootCtx.primitives.int(range)
        }

        return GeneratedValue(
            ctx = rootCtx,
            value = value,
            shrinks = IntShrinker.shrink(value, range, shrinkTarget).map { rootCtx.withShrunkPrimitive(it) }
        ).asSuccess()
    }
}
