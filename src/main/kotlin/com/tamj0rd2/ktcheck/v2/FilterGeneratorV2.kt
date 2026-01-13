package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.GenerationException.FilterLimitReached

internal class FilterGeneratorV2<T>(
    private val gen: GenV2<T>,
    private val threshold: Int,
    private val predicate: (T) -> Boolean,
) : GenV2<T>() {
    override fun GenContextV2.generate(): GenResultV2<T> {
        var attempts = 0
        var currentTree = tree

        while (attempts < threshold) {
            val result = gen.generate(currentTree.left)

            if (predicate(result.value)) {
                return result.copy(shrinks = filterValidShrinks(result.shrinks))
            }

            currentTree = currentTree.right
            attempts++
        }

        throw FilterLimitReached(threshold, null)
    }

    private fun filterValidShrinks(shrinks: Sequence<GenResultV2<T>>): Sequence<GenResultV2<T>> = shrinks
        .filter { predicate(it.value) }
        .map { it.copy(shrinks = filterValidShrinks(it.shrinks)) }
}
