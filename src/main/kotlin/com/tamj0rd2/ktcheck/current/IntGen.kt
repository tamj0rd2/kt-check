package com.tamj0rd2.ktcheck.current

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

    override fun generate(rootCtx: GenContext): Result4k<GeneratedValue<Int>, GenerationException> {
        // todo: passing something from rootContext into something from rootContext feels wrong.
        val value = rootCtx.primitives.int(range, rootCtx.generateEdgeCase)

        return GeneratedValue(
            ctx = rootCtx,
            value = value,
            shrinks = IntShrinker.shrink(value, range, shrinkTarget).map { rootCtx.withShrunkPrimitive(it) }
        ).asSuccess()
    }
}
