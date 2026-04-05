package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import dev.forkhandles.result4k.asFailure
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.onFailure
import dev.forkhandles.result4k.orThrow
import dev.forkhandles.result4k.valueOrNull
import kotlin.reflect.KClass

internal data class IgnoreExceptionsGen<T>(
    private val delegate: Gen<T>,
    private val threshold: Int,
    private val klass: KClass<out Exception>,
) : GenProvider<T> {
    override fun generate(ctx: GenContext): Result4k<GeneratedValue<T>, GenerationException> {
        var ctx = ctx

        repeat(threshold) {
            val generatedValue = buildResult { delegate.generate(ctx.left).orThrow() }.onFailure { return it }
            if (generatedValue != null) return generatedValue.asSuccess()
            ctx = ctx.right
        }

        throw GenerationException.FilterLimitReached(threshold)
    }

    private fun buildResult(
        makeGeneratedValue: () -> GeneratedValue<T>,
    ): Result4k<GeneratedValue<T>?, GenerationException> =
        try {
            val generatedValue = makeGeneratedValue()
            GeneratedValue(
                value = generatedValue.value,
                shrinks = sequence {
                    val iterator = generatedValue.shrinks.iterator()
                    while (iterator.hasNext()) {
                        buildResult { iterator.next() }.valueOrNull()?.let { yield(it) }
                    }
                }
            ).asSuccess()
        } catch (e: Exception) {
            when {
                klass.isInstance(e) -> Success(null)
                e is GenerationException -> e.asFailure()
                else -> throw e
            }
        }

    private fun <T> catchError(fn: () -> T) =
        runCatching { fn() }.onFailure { if (!klass.isInstance(it)) throw it }
}
