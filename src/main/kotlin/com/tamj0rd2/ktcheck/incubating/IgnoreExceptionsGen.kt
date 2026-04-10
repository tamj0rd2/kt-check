package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asResultOr
import dev.forkhandles.result4k.orThrow
import kotlin.reflect.KClass

internal data class IgnoreExceptionsGen<T>(
    private val delegate: Gen<T>,
    private val threshold: Int,
    private val klass: KClass<out Exception>,
) : GenProvider<T> {
    override fun generate(rootCtx: GenContext): Result4k<GeneratedValue<T>, GenerationException> {
        var lastException: Throwable? = null
        return rootCtx.traverseRight()
            .take(threshold)
            // ensures that during shrinking, we don't go further than the intended shrink tree
            .takeWhile { !it.hasMetadata(terminator) }
            .mapNotNull {
                catchError { delegate.generate(it.left).orThrow() }
                    .onFailure { lastException = it }
                    .getOrNull()
            }
            .map {
                GeneratedValue(
                    ctx = rootCtx,
                    value = it.value,
                    shrinks = it.shrinks.map { ctx ->
                        rootCtx.withShrunkLeft(ctx).withShrunkRight(rootCtx.right.withMetadata(terminator))
                    }
                )
            }
            .firstOrNull()
            .asResultOr { GenerationException.FilterLimitReached(threshold, lastException) }
    }

    // todo: pretty sure I'm also about to add this for exception ignoring. maybe I should just introduce a way to
    //  mark a node as terminal.
    private val terminator = "${this::class.simpleName}.terminate"

    //private fun buildResult(
    //    makeGeneratedValue: () -> GeneratedValue<T>,
    //): Result4k<GeneratedValue<T>?, GenerationException> =
    //    try {
    //        val generatedValue = makeGeneratedValue()
    //        GeneratedValue(
    //            value = generatedValue.value,
    //            shrinks = sequence {
    //                //val iterator = generatedValue.shrinks.iterator()
    //                //while (iterator.hasNext()) {
    //                //    buildResult { iterator.next() }.valueOrNull()?.let { yield(it) }
    //                //}
    //                TODO()
    //            }
    //        ).asSuccess()
    //    } catch (e: Exception) {
    //        when {
    //            klass.isInstance(e) -> Success(null)
    //            e is GenerationException -> e.asFailure()
    //            else -> throw e
    //        }
    //    }

    private fun <T> catchError(fn: () -> T) =
        runCatching { fn() }.onFailure { if (!klass.isInstance(it)) throw it }
}
