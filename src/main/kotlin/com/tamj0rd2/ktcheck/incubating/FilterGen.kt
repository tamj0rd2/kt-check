package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asResultOr
import dev.forkhandles.result4k.valueOrNull

internal data class FilterGen<T>(
    private val delegate: Gen<T>,
    private val threshold: Int,
    private val predicate: (T) -> Boolean,
) : GenProvider<T> {
    override fun generate(rootCtx: GenContext): Result4k<GeneratedValue<T>, GenerationException> =
        rootCtx.traverseRight()
            .take(threshold)
            // ensures that during shrinking, we don't go further than the intended shrink tree
            .takeWhile { !it.hasMetadata(terminator) }
            .mapNotNull { delegate.generate(it.left).valueOrNull() }
            .filter { predicate(it.value) }
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
            .asResultOr { GenerationException.FilterLimitReached(threshold) }

    // todo: pretty sure I'm also about to add this for exception ignoring. maybe I should just introduce a way to
    //  mark a node as terminal.
    private val terminator = "${this::class.simpleName}.terminate"
}
