package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.onFailure

internal data class CombineGen<T1, T2, R>(
    val leftGen: Gen<T1>,
    val rightGen: Gen<T2>,
    val combine: (T1, T2) -> R,
) : GenProvider<R> {
    override fun generate(rootCtx: GenContext): Result4k<GeneratedValue<R>, GenerationException> {
        val left = leftGen.generate(rootCtx.left).onFailure { return it }
        val right = rightGen.generate(rootCtx.right).onFailure { return it }

        val leftBasedShrinks = left.shrinks.map { left -> rootCtx.withShrunkLeft(left) }
        val rightBasedShrinks = right.shrinks.map { right -> rootCtx.withShrunkRight(right) }

        return GeneratedValue(
            ctx = rootCtx,
            value = combine(left.value, right.value),
            shrinks = leftBasedShrinks + rightBasedShrinks,
        ).asSuccess()
    }
}
