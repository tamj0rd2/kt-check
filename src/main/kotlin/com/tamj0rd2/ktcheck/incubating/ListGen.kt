package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.onFailure

internal data class ListGen<T>(
    private val gen: Gen<T>,
    private val sizeRange: IntRange,
) : GenProvider<List<T>> {
    override fun generate(ctx: GenContext): Result4k<GeneratedValue<List<T>>, GenerationException> {
        val size = Gen.int(sizeRange).generate(ctx.left).onFailure { return it }
        val elements = buildList {
            var ctx = ctx.right
            repeat(size.value) {
                add(gen.generate(ctx.left).onFailure { return it })
                ctx = ctx.right
            }
        }

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
            elements[index].shrinks.map { shrunkElement ->
                val updatedElements = elements.toMutableList().apply { set(index, shrunkElement) }
                buildResult(size, updatedElements)
            }
        }

        return GeneratedValue(
            value = elements.map { it.value },
            shrinks = sizeBasedShrinks + elementBasedShrinks,
        )
    }
}
