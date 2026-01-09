package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.gen.DistinctCollectionSizeImpossible
import com.tamj0rd2.ktcheck.v2.IntGeneratorV2.Companion.int

private class ListGeneratorV2<T>(
    private val gen: GenV2<T>,
    private val sizeRange: IntRange,
    private val distinct: Boolean = false,
) : GenV2<List<T>>() {
    private val sizeGen = GenV2.int(sizeRange)

    override fun GenContextV2.generate(): GenResultV2<List<T>> {
        val sizeResult = sizeGen.generate(producer)
        val size = sizeResult.value

        // Generate elements with duplicate handling if distinct=true
        val elementResults = if (distinct) {
            generateDistinctElements(size)
        } else {
            List(size) { gen.generate(producer) }
        }

        return GenResultV2(
            value = elementResults.map { it.value },
            shrinks = generateListShrinks(size, elementResults),
        )
    }

    private fun GenContextV2.generateDistinctElements(size: Int): List<GenResultV2<T>> {
        val elementResults = mutableListOf<GenResultV2<T>>()
        val seenValues = mutableSetOf<T>()
        var retriesRemaining = MAX_DISTINCT_ATTEMPTS

        while (elementResults.size < size) {
            val elemResult = gen.generate(producer)

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

    private fun generateListShrinks(
        size: Int,
        elementResults: List<GenResultV2<T>>,
    ): Sequence<GenResultV2<List<T>>> = sequence {
        IntGeneratorV2.shrink(size, sizeRange).forEach { newSize ->
            when {
                newSize == 0 -> {
                    // Empty list has no further shrinks
                    yield(GenResultV2(emptyList(), emptySequence()))
                }

                newSize < size -> {
                    // Tail removal - recursively shrink the resulting list
                    val tailRemovalElements = elementResults.take(newSize)
                    yield(
                        GenResultV2(
                            value = tailRemovalElements.map { it.value },
                            shrinks = generateListShrinks(newSize, tailRemovalElements)
                        )
                    )

                    // Head removal - recursively shrink the resulting list
                    val headRemovalElements = elementResults.takeLast(newSize)
                    yield(
                        GenResultV2(
                            value = headRemovalElements.map { it.value },
                            shrinks = generateListShrinks(newSize, headRemovalElements)
                        )
                    )
                }
            }
        }

        // Element shrinks with duplicate handling
        elementResults.indices.forEach { index ->
            elementResults[index].shrinks.forEach { shrunkElementResult ->
                val newElementResults = elementResults.mapIndexed { i, elemResult ->
                    if (i == index) shrunkElementResult else elemResult
                }

                // Handle duplicates if distinct mode
                if (distinct) {
                    val newValues = newElementResults.map { it.value }
                    val hasDuplicates = newValues.size != newValues.toSet().size

                    if (hasDuplicates) {
                        // Remove duplicates and accept if size still in range
                        val uniqueResults = newElementResults.distinctBy { it.value }
                        if (uniqueResults.size in sizeRange) {
                            yield(
                                GenResultV2(
                                    value = uniqueResults.map { it.value },
                                    shrinks = generateListShrinks(uniqueResults.size, uniqueResults)
                                )
                            )
                        }
                        return@forEach  // Skip this shrink
                    }
                }

                yield(
                    GenResultV2(
                        value = newElementResults.map { it.value },
                        shrinks = generateListShrinks(size, newElementResults)
                    )
                )
            }
        }
    }

    companion object {
        private const val MAX_DISTINCT_ATTEMPTS = 1000
    }
}


fun <T> GenV2<T>.list(size: IntRange = 0..100, distinct: Boolean = false): GenV2<List<T>> =
    ListGeneratorV2(gen = this, sizeRange = size, distinct = distinct)

fun <T> GenV2<T>.list(size: Int, distinct: Boolean = false): GenV2<List<T>> =
    list(size..size, distinct)

fun <T> GenV2<T>.set(size: IntRange = 0..100): GenV2<Set<T>> =
    list(size, distinct = true).map { it.toSet() }
