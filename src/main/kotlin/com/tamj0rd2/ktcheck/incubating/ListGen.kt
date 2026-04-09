package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asFailure
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.onFailure
import dev.forkhandles.result4k.recover

internal sealed class BaseListGen<T> : GenProvider<List<T>> {
    protected abstract val sizeRange: IntRange

    final override fun generate(rootCtx: GenContext): Result4k<GeneratedValue<List<T>>, GenerationException> {
        val size = Gen.int(sizeRange).generate(rootCtx.left).onFailure { return it }
        val elements = generateElements(rootCtx.right, size.value).onFailure { return it }

        val sizeBasedShrinks = size.shrinks.takeIf { size.value > sizeRange.first }.orEmpty().flatMap { ctx ->
            Gen.int(sizeRange).generate(ctx)
                .map { size ->
                    sequence {
                        yield(rootCtx.withSizeCtx(ctx).withElementsCtx(elements.map { it.ctx }.take(size.value)))
                        yield(rootCtx.withSizeCtx(ctx).withElementsCtx(elements.map { it.ctx }.takeLast(size.value)))
                    }
                }
                .recover { emptySequence() }
        }

        val individualElementShrinks = elements.asSequence().flatMapIndexed { index, element ->
            element.shrinks.map { shrunkElement ->
                rootCtx.withElementsCtx(elements.map { it.ctx }.replaceAtIndex(index, shrunkElement))
            }
        }

        val allElementShrinks = sequence {
            val allElementShrinks = elements.map { it.shrinks.iterator() }.ifEmpty { return@sequence }

            while (allElementShrinks.all { it.hasNext() }) {
                val shrunkContexts = allElementShrinks.map { it.next() }
                yield(rootCtx.withElementsCtx(shrunkContexts))
            }
        }
        return GeneratedValue(
            ctx = rootCtx,
            value = elements.map { it.value },
            shrinks = sizeBasedShrinks + individualElementShrinks + allElementShrinks,
        ).asSuccess()
    }

    protected abstract fun generateElements(
        ctx: GenContext,
        targetSize: Int,
    ): Result<List<GeneratedValue<T>>, GenerationException>

    protected open fun List<GeneratedValue<T>>.isValid(targetSize: Int): Boolean = true

    private fun GenContext.withSizeCtx(sizeShrink: GenContext) = withShrunkLeft(sizeShrink)

    private fun GenContext.withElementsCtx(elementContexts: List<GenContext>): GenContext {
        val originalTraversalNodes = right.traverseRight().take(elementContexts.size + 1).toList()

        var current = originalTraversalNodes.last().withMetadata(listTerminator)
        for (i in elementContexts.indices.reversed()) {
            current = originalTraversalNodes[i].withShrunkLeft(elementContexts[i]).withShrunkRight(current)
        }

        return withShrunkRight(current)
    }

    private fun <T> List<T>.replaceAtIndex(index: Int, replacement: T): List<T> =
        toMutableList().apply { set(index, replacement) }

    protected val listTerminator = "${this::class.simpleName}.terminate"
}

internal data class ListGen<T>(
    private val gen: Gen<T>,
    override val sizeRange: IntRange,
) : BaseListGen<T>() {
    override fun generateElements(
        ctx: GenContext,
        targetSize: Int,
    ): Result<List<GeneratedValue<T>>, GenerationException> =
        buildList {
            var ctx = ctx
            repeat(targetSize) {
                add(gen.generate(ctx.left).onFailure { return it })
                ctx = ctx.right
            }
        }.asSuccess()
}

internal data class DistinctListGen<T>(
    private val gen: Gen<T>,
    override val sizeRange: IntRange,
) : BaseListGen<T>() {
    override fun List<GeneratedValue<T>>.isValid(targetSize: Int) = distinctBy { it.value }.size == targetSize

    override fun generateElements(
        ctx: GenContext,
        targetSize: Int,
    ): Result<List<GeneratedValue<T>>, GenerationException> = buildList {
        var ctx = ctx
        var attempts = 0
        val seenValues = mutableSetOf<T>()
        while (size < targetSize) {
            if (attempts > MAX_ATTEMPTS_PER_ELEMENT || ctx.hasMetadata(listTerminator)) {
                return GenerationException.DistinctCollectionSizeImpossible(
                    minSize = targetSize,
                    achievedSize = size,
                    attempts = attempts,
                ).asFailure()
            }

            attempts += 1
            val element = gen.generate(ctx.left).onFailure { return it }
            if (seenValues.add(element.value)) {
                add(element)
                attempts = 0
            }

            ctx = ctx.right
        }
    }.asSuccess()

    private companion object {
        const val MAX_ATTEMPTS_PER_ELEMENT = 100
    }
}
