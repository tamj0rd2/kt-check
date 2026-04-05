package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asFailure
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.onFailure

internal sealed class BaseListGen<T> : GenProvider<List<T>> {
    protected abstract val sizeRange: IntRange

    final override fun generate(ctx: GenContext): Result4k<GeneratedValue<List<T>>, GenerationException> {
        val size = Gen.int(sizeRange).generate(ctx.left).onFailure { return it }
        val elements = generateElements(ctx.right, size.value).onFailure { return it }
        return buildResult(size, elements).asSuccess()
    }

    protected abstract fun generateElements(
        ctx: GenContext,
        targetSize: Int,
    ): Result<List<GeneratedValue<T>>, GenerationException>

    protected open fun List<GeneratedValue<T>>.isValid(targetSize: Int): Boolean = true

    private fun buildResult(
        size: GeneratedValue<Int>,
        elements: List<GeneratedValue<T>>,
    ): GeneratedValue<List<T>> {
        val sizeBasedShrinks = size.shrinks.flatMap { size ->
            sequenceOf(
                buildResult(size, elements.take(size.value)),
                buildResult(size, elements.takeLast(size.value)),
            )
        }

        val elementBasedShrinks = elements.indices.asSequence().flatMap { index ->
            elements[index].shrinks.mapNotNull { shrunkElement ->
                elements
                    .toMutableList()
                    .apply { set(index, shrunkElement) }
                    .takeIf { it.isValid(size.value) }
                    ?.let { buildResult(size, it) }
            }
        }

        return GeneratedValue(
            value = elements.map { it.value },
            shrinks = sizeBasedShrinks + elementBasedShrinks,
        )
    }
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
            if (attempts > MAX_ATTEMPTS_PER_ELEMENT) {
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
