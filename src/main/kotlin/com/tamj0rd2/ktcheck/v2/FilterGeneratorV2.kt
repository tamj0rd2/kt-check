package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.gen.FilterLimitReached

private class FilterGeneratorV2<T>(
    private val gen: GenV2<T>,
    private val threshold: Int,
    private val predicate: (T) -> Boolean,
) : GenV2<T>() {
    override fun GenContextV2.generate(): GenResultV2<T> {
        var attempts = 0

        while (attempts < threshold) {
            val result = gen.generate(producer)

            if (predicate(result.value)) {
                // Filter shrinks to only include values that pass predicate
                val validShrinks = filterValidShrinks(result.shrinks)
                return result.copy(shrinks = validShrinks)
            }

            attempts++
        }

        throw FilterLimitReached(threshold, null)
    }

    private fun filterValidShrinks(
        shrinks: Sequence<GenResultV2<T>>,
    ): Sequence<GenResultV2<T>> {
        return shrinks
            .filter { shrunkResult -> predicate(shrunkResult.value) }
            .map { shrunkResult ->
                // Recursively filter nested shrinks
                shrunkResult.copy(
                    shrinks = filterValidShrinks(shrunkResult.shrinks)
                )
            }
    }
}

fun <T> GenV2<T>.filter(predicate: (T) -> Boolean): GenV2<T> =
    filter(100, predicate)

fun <T> GenV2<T>.filter(threshold: Int, predicate: (T) -> Boolean): GenV2<T> =
    FilterGeneratorV2(gen = this, threshold = threshold, predicate = predicate)

