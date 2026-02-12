package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenerationException

internal class PredicateFilterGen<T>(
    private val gen: GenImpl<T>,
    private val threshold: Int,
    private val predicate: (T) -> Boolean,
) : GenImpl<T>() {
    override fun edgeCases(): List<GenResultV2<T>> {
        return gen.edgeCases().mapNotNull { it.filter(predicate) }
    }

    override fun generate(tree: RandomTree): GenResultV2<T> {
        return generateSequence(tree) { it.right }
            .take(threshold)
            .mapIndexedNotNull { index, offsetTree ->
                val result = gen.generate(offsetTree.left)
                if (!predicate(result.value)) return@mapIndexedNotNull null

                GenResultV2(
                    value = result.value,
                    shrinks = result.shrinks.map { tree.replaceLeftAtOffset(index, it) },
                )
            }
            .firstOrNull()
            ?: throw GenerationException.FilterLimitReached(threshold)
    }

    private fun GenResultV2<T>.filter(predicate: (T) -> Boolean): GenResultV2<T>? {
        if (!predicate(value)) return null

        return GenResultV2(
            value = value,
            shrinks = emptySequence(),
        )
    }
}
