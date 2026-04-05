package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.onFailure

internal data class FilterGen<T>(
    private val delegate: Gen<T>,
    private val threshold: Int,
    private val predicate: (T) -> Boolean,
) : GenProvider<T> {
    override fun generate(ctx: GenContext): Result4k<GeneratedValue<T>, GenerationException> {
        var ctx = ctx
        repeat(threshold) {
            val generatedValue = delegate.generate(ctx.left).onFailure { return it }
            val filtered = generatedValue.filter(predicate)
            if (filtered != null) return filtered.asSuccess()
            ctx = ctx.right
        }

        throw GenerationException.FilterLimitReached(threshold)
    }
}
