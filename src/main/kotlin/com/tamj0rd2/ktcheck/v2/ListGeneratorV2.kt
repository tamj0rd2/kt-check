package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.v2.IntGeneratorV2.Companion.int

private class ListGeneratorV2<T>(
    private val gen: GenV2<T>,
    private val sizeRange: IntRange,
) : GenV2<List<T>>() {
    private val sizeGen = GenV2.int(sizeRange)

    override fun GenContextV2.generate(): GenResultV2<List<T>> {
        val sizeResult = sizeGen.generate(producer)
        val size = sizeResult.value

        val elementResults = List(size) { gen.generate(producer) }

        return GenResultV2(
            value = elementResults.map { it.value },
            shrinks = generateListShrinks(size, elementResults),
        )
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

        elementResults.indices.forEach { index ->
            elementResults[index].shrinks.forEach { shrunkElementResult ->
                val newElementResults = elementResults.mapIndexed { i, elemResult ->
                    if (i == index) shrunkElementResult else elemResult
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
}

fun <T> GenV2<T>.list(size: IntRange = 0..100): GenV2<List<T>> =
    ListGeneratorV2(this, size)

fun <T> GenV2<T>.list(size: Int): GenV2<List<T>> = list(size..size)
