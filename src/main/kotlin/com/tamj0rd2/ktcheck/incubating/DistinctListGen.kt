package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asFailure
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.onFailure

internal data class DistinctListGen<T>(
    private val gen: Gen<T>,
    private val sizeRange: IntRange,
) : GenProvider<List<T>> {
    override fun generate(ctx: GenContext): Result4k<GeneratedValue<List<T>>, GenerationException> {
        val size = Gen.int(sizeRange).generate(ctx.left).onFailure { return it }
        val elements = generateElements(ctx.right, size.value).onFailure { return it }
        return buildResult(size, elements).asSuccess()
    }

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
                val updatedElements = elements
                    .toMutableList()
                    .apply { set(index, shrunkElement) }
                    .distinctBy { it.value }

                if (updatedElements.size == size.value) buildResult(size, updatedElements)
                else null
            }
        }

        return GeneratedValue(
            value = elements.map { it.value },
            shrinks = sizeBasedShrinks + elementBasedShrinks,
        )
    }

    private fun generateElements(
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
