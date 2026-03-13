package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asResultOr
import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.recover
import dev.forkhandles.result4k.valueOrNull

internal class FilterGen<T>(
    private val gen: Generator<T>,
    private val threshold: Int,
    private val predicate: (T) -> Boolean,
) : Generator<T> {
    override fun generate(root: RandomTree): Result4k<GeneratedValue<T>, GenerationException> {
        return root.traversingRight()
            .take(threshold)
            .mapNotNull { gen.generate(it.left).valueOrNull() }
            .filter { predicate(it.value) }
            .map { buildResult(root, it) }
            .firstOrNull()
            .asResultOr { GenerationException.FilterLimitReached(threshold) }
    }

    override fun edgeCases(root: RandomTree): List<GeneratedValue<T>> {
        return gen.edgeCases(root)
            .filter { predicate(it.value) }
            .map { buildResult(root, it) }
    }

    private fun buildResult(
        root: RandomTree,
        result: GeneratedValue<T>,
    ): GeneratedValue<T> {
        check(predicate(result.value)) { "internal error - value did not match the predicate" }

        return GeneratedValue(
            value = result.value,
            shrinks = result.shrinks
                .filter { gen.generate(it).map { predicate(it.value) }.recover { false } }
                .map { root.withLeft(it) },
            usedTree = root.withLeft(result.usedTree),
        )
    }
}
