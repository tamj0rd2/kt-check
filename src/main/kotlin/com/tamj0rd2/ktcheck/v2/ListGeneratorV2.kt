package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.GenerationException.DistinctCollectionSizeImpossible
import com.tamj0rd2.ktcheck.v1.ProducerTree
import com.tamj0rd2.ktcheck.v2.IntGeneratorV2.Companion.shrinkInt

internal data class ListGeneratorV2<T>(
    private val gen: GenV2<T>,
    private val sizeRange: IntRange,
    private val distinct: Boolean = false,
) : GenV2<List<T>>() {
    override fun GenContextV2.generate(): GenResultV2<List<T>> {
        val size = tree.left.producer.int(sizeRange)

        val elementResults = if (distinct) {
            generateDistinctElements(size, tree.right)
        } else {
            generateElements(size, tree.right)
        }

        return elementResults.asRecursivelyShrinkingGenResult()
    }

    private fun List<GenResultV2<T>>.asRecursivelyShrinkingGenResult() = GenResultV2(
        value = map { it.value },
        shrinks = createSizeShrinks(this) + createElementShrinks(this)
    )

    private fun generateElements(size: Int, tree: ProducerTree): List<GenResultV2<T>> {
        val elementResults = mutableListOf<GenResultV2<T>>()
        var tree = tree

        repeat(size) {
            val elemResult = gen.generate(tree.left)
            tree = tree.right
            elementResults.add(elemResult)
        }

        return elementResults
    }

    private fun generateDistinctElements(size: Int, tree: ProducerTree): List<GenResultV2<T>> {
        val elementResults = mutableListOf<GenResultV2<T>>()
        val seenValues = mutableSetOf<T>()
        var retriesRemaining = MAX_DISTINCT_ATTEMPTS

        var tree = tree
        while (elementResults.size < size) {
            val elemResult = gen.generate(tree.left)
            tree = tree.right

            // Check for duplicates
            if (elemResult.value in seenValues) {
                if (retriesRemaining <= 0) {
                    throw DistinctCollectionSizeImpossible(
                        targetSize = size,
                        achievedSize = elementResults.size,
                        attempts = MAX_DISTINCT_ATTEMPTS,
                    )
                }
                retriesRemaining--
                continue
            }

            elementResults.add(elemResult)
            seenValues.add(elemResult.value)
        }

        return elementResults
    }

    private fun createSizeShrinks(elementResults: List<GenResultV2<T>>): Sequence<GenResultV2<List<T>>> =
        shrinkInt(elementResults.size, sizeRange).flatMap { newSize ->
            when {
                newSize == 0 -> sequenceOf(GenResultV2(emptyList(), emptySequence()))

                newSize < elementResults.size -> {
                    sequence {
                        yield(elementResults.take(newSize).asRecursivelyShrinkingGenResult())
                        yield(elementResults.takeLast(newSize).asRecursivelyShrinkingGenResult())
                    }
                }

                else -> emptySequence()
            }
        }

    private fun createElementShrinks(elementResults: List<GenResultV2<T>>): Sequence<GenResultV2<List<T>>> =
        elementResults.indices.asSequence()
            .flatMap { index ->
                elementResults[index].shrinks.map { shrunkElementResult ->
                    elementResults.mapIndexed { i, elemResult ->
                        if (i == index) shrunkElementResult else elemResult
                    }
                }
            }
            .flatMap { elementsWithOneShrunk ->
                if (distinct) {
                    val uniqueResults = elementsWithOneShrunk.distinctBy { it.value }
                    val hasDuplicates = uniqueResults.size < elementsWithOneShrunk.size

                    if (hasDuplicates) {
                        return@flatMap uniqueResults
                            .takeIf { it.size in sizeRange }
                            ?.let { sequenceOf(it.asRecursivelyShrinkingGenResult()) }
                            .orEmpty()
                    }
                }

                sequenceOf(elementsWithOneShrunk.asRecursivelyShrinkingGenResult())
            }

    companion object {
        private const val MAX_DISTINCT_ATTEMPTS = 1000
    }
}
