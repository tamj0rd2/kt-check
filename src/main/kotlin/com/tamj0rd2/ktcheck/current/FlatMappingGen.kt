package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.onFailure

internal data class FlatMappingGen<T, R>(
    private val gen: Gen<T>,
    private val fn: (T) -> Gen<R>,
) : GenProvider<R> {
    override fun generate(rootCtx: GenContext): Result4k<GeneratedValue<R>, GenerationException> {
        val left = gen.generate(rootCtx.left).onFailure { return it }
        val right = fn(left.value).generate(rootCtx.right).onFailure { return it }

        val leftBasedShrinks = left.shrinks.map { left -> rootCtx.withShrunkLeft(left) }
        val rightBasedShrinks = right.shrinks.map { right -> rootCtx.withShrunkRight(right) }

        return GeneratedValue(
            ctx = rootCtx,
            value = right.value,
            shrinks = leftBasedShrinks + rightBasedShrinks,
        ).asSuccess()
    }
}
