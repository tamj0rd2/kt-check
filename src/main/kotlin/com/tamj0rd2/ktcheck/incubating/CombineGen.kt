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
    override fun generate(ctx: GenContext): Result4k<GeneratedValue<R>, GenerationException> {
        val left = leftGen.generate(ctx.left).onFailure { return it }
        val right = rightGen.generate(ctx.right).onFailure { return it }

        return buildResult(left, right).asSuccess()
    }

    private fun buildResult(
        left: GeneratedValue<T1>,
        right: GeneratedValue<T2>,
    ): GeneratedValue<R> {
        val leftBasedShrinks = left.shrinks.map { left -> buildResult(left, right) }
        val rightBasedShrinks = right.shrinks.map { right -> buildResult(left, right) }

        return GeneratedValue(
            value = combine(left.value, right.value),
            shrinks = leftBasedShrinks + rightBasedShrinks
        )
    }
}
