package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.v2.IntGenerator.Companion.int

private class ListGenerator<T>(
    private val gen: Gen<T>,
    private val sizeRange: IntRange,
) : Gen<List<T>>() {
    private val sizeGen = Gen.int(sizeRange)

    override fun GenContext.generate(): GenResult<List<T>> {
        val sizeResult = sizeGen.generate(producer)
        val size = sizeResult.value

        val elementResults = List(size) { gen.generate(producer) }

        return GenResult(
            value = elementResults.map { it.value },
            shrinks = generateListShrinks(size, elementResults),
        )
    }

    private fun generateListShrinks(
        size: Int,
        elementResults: List<GenResult<T>>,
    ): Sequence<GenResult<List<T>>> = sequence {
        IntGenerator.shrink(size, sizeRange).forEach { newSize ->
            when {
                newSize == 0 -> {
                    // Empty list has no further shrinks
                    yield(GenResult(emptyList(), emptySequence()))
                }

                newSize < size -> {
                    // Tail removal - recursively shrink the resulting list
                    val tailRemovalElements = elementResults.take(newSize)
                    yield(
                        GenResult(
                            value = tailRemovalElements.map { it.value },
                            shrinks = generateListShrinks(newSize, tailRemovalElements)
                        )
                    )

                    // Head removal - recursively shrink the resulting list
                    val headRemovalElements = elementResults.takeLast(newSize)
                    yield(
                        GenResult(
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
                    GenResult(
                        value = newElementResults.map { it.value },
                        shrinks = generateListShrinks(size, newElementResults)
                    )
                )
            }
        }
    }
}

fun <T> Gen<T>.list(size: IntRange = 0..100): Gen<List<T>> =
    ListGenerator(this, size)

fun <T> Gen<T>.list(size: Int): Gen<List<T>> = list(size..size)
