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
        val left = rootCtx.left
            .let { if (rootCtx.generateEdgeCase) it.generateEdgeCase() else it }
            .let { leftGen.generate(it) }
            .onFailure { return it }

        val right = rootCtx.right
            .let { if (rootCtx.generateEdgeCase) it.generateEdgeCase() else it }
            .let { rightGen.generate(it) }
            .onFailure { return it }

        val leftBasedShrinks = left.shrinks.map { left -> rootCtx.withShrunkLeft(left) }
        val rightBasedShrinks = right.shrinks.map { right -> rootCtx.withShrunkRight(right) }

        /**
         * allows important properties of the data to be maintained during shrinking. For example, if falsifying the
         * property requires both values to be the same, shrinking left+right separately will never allow it.
         */
        val cartesianShrinks = left.shrinks.flatMap { left ->
            right.shrinks.map { right ->
                rootCtx.withShrunkLeft(left).withShrunkRight(right)
            }
        }

        return GeneratedValue(
            ctx = rootCtx,
            value = combine(left.value, right.value),
            shrinks = cartesianShrinks + leftBasedShrinks + rightBasedShrinks,
        ).asSuccess()
    }
}
