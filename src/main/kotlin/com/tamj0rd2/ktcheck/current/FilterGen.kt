package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asFailure
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.onFailure

internal data class FilterGen<T>(
    private val delegate: Gen<T>,
    private val threshold: Int,
    private val predicate: (T) -> Boolean,
) : GenProvider<T> {
    override fun generate(rootCtx: GenContext): Result4k<GeneratedValue<T>, GenerationException> {
        val contexts = rootCtx.traverseRight()
            .take(threshold)
            .takeWhile { !it.hasMetadata(terminator) }

        for (ctx in contexts) {
            val generatedValue = delegate.generate(ctx.left).onFailure { return it }
            if (!predicate(generatedValue.value)) continue

            return GeneratedValue(
                ctx = rootCtx,
                value = generatedValue.value,
                shrinks = generatedValue.shrinks.map { ctx ->
                    rootCtx.withShrunkLeft(ctx).withShrunkRight(rootCtx.right.withMetadata(terminator))
                }
            ).asSuccess()
        }

        return GenerationException.FilterLimitReached(threshold).asFailure()
    }

    private val terminator = "${this::class.simpleName}.terminate"
}
