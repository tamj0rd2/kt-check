package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asFailure
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.orThrow
import dev.forkhandles.result4k.recover
import dev.forkhandles.result4k.resultFrom
import kotlin.reflect.KClass

internal data class IgnoreExceptionsGen<T>(
    private val delegate: Gen<T>,
    private val threshold: Int,
    private val klass: KClass<out Exception>,
) : GenProvider<T> {
    override fun generate(rootCtx: GenContext): Result4k<GeneratedValue<T>, GenerationException> {
        var lastException: Exception? = null

        val contexts = rootCtx.traverseRight()
            .take(threshold)
            .takeWhile { !it.hasMetadata(terminator) }

        for (ctx in contexts) {
            val generatedValue = resultFrom { delegate.generate(ctx.left).orThrow() }.recover {
                when {
                    klass.isInstance(it) -> {
                        lastException = it
                        continue
                    }

                    it is GenerationException -> return it.asFailure()
                    else -> throw it
                }
            }

            return GeneratedValue(
                ctx = rootCtx,
                value = generatedValue.value,
                shrinks = generatedValue.shrinks.map { ctx ->
                    rootCtx.withShrunkLeft(ctx).withShrunkRight(rootCtx.right.withMetadata(terminator))
                }
            ).asSuccess()
        }

        return GenerationException.FilterLimitReached(threshold, lastException).asFailure()
    }

    private val terminator = "${this::class.simpleName}.terminate"
}
