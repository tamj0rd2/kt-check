package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asFailure
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.onFailure

internal sealed class BaseListGen<T> : GenProvider<List<T>> {
    protected abstract val sizeRange: IntRange

    final override fun generate(rootCtx: GenContext): Result4k<GeneratedValue<List<T>>, GenerationException> {
        val size = Gen.int(sizeRange).generate(rootCtx.left).onFailure { return it }
        val elements = generateElements(rootCtx.right, size.value).onFailure { return it }
        val elementContexts by lazy { elements.map { it.ctx } }

        val sizeBasedShrinks = size.shrinks.takeIf { size.value > sizeRange.first }.orEmpty().flatMap { ctx ->
            sequence {
                yield(rootCtx.withSizeCtx(ctx).withElementsCtx(elementContexts))

                val size = Gen.int(sizeRange).generate(ctx).onFailure { return@sequence }.value
                if (elements.size == size) return@sequence
                yield(rootCtx.withSizeCtx(ctx).withElementsCtx(elementContexts.takeLast(size)))
            }
        }

        val individualElementShrinks = elements.asSequence().flatMapIndexed { index, element ->
            element.shrinks.map { shrunkElement ->
                rootCtx.withElementsCtx(elementContexts.replaceAtIndex(index, shrunkElement))
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
            shrinks = sizeBasedShrinks + sequence {
                val iterators = listOf(allElementShrinks, individualElementShrinks).map { it.iterator() }
                while (iterators.any { it.hasNext() }) {
                    for (iterator in iterators) {
                        if (iterator.hasNext()) yield(iterator.next())
                    }
                }
            },
        ).asSuccess()
    }

    protected abstract fun generateElements(
        root: GenContext,
        targetSize: Int,
    ): Result<List<GeneratedValue<T>>, GenerationException>

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
        root: GenContext,
        targetSize: Int,
    ): Result<List<GeneratedValue<T>>, GenerationException> =
        buildList {
            val contexts = root.traverseRight().take(targetSize).map {
                if (root.generateEdgeCase) it.generateEdgeCase() else it
            }

            for (ctx in contexts) {
                add(gen.generate(ctx.left).onFailure { return it })
            }
        }.asSuccess()
}

internal data class DistinctListGen<T>(
    private val gen: Gen<T>,
    override val sizeRange: IntRange,
) : BaseListGen<T>() {
    override fun generateElements(
        root: GenContext,
        targetSize: Int,
    ): Result<List<GeneratedValue<T>>, GenerationException> = buildList {
        var ctx = root
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
