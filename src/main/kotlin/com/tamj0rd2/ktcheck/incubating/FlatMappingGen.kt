package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.onFailure
import dev.forkhandles.result4k.orThrow

internal data class FlatMappingGen<T, R>(
    private val gen: Gen<T>,
    private val fn: (T) -> Gen<R>,
) : GenProvider<R> {
    override fun generate(ctx: GenContext): Result4k<GeneratedValue<R>, GenerationException> {
        val outer = gen.generate(ctx.left).onFailure { return it }
        val inner = fn(outer.value).generate(ctx.right).onFailure { return it }
        return buildResult(ctx, outer, inner).asSuccess()
    }

    private fun buildResult(
        ctx: GenContext,
        outer: GeneratedValue<T>,
        inner: GeneratedValue<R>,
    ): GeneratedValue<R> {
        val outerBasedShrinks = outer.shrinks.map { outer ->
            val inner = fn(outer.value).generate(ctx.right).orThrow()
            buildResult(ctx, outer, inner)
        }

        return GeneratedValue(
            value = inner.value,
            shrinks = outerBasedShrinks + inner.shrinks,
        )
    }
}
