package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.map

internal class MapGen<T, R>(
    private val wrappedGen: Generator<T>,
    private val fn: (T) -> R,
) : Generator<R> {
    override fun generate(root: ProviderTree, mode: GenerationMode): Result4k<GeneratedValue<R>, GenerationException> {
        return wrappedGen.generate(root, mode).map { it.map(fn) }
    }

    override fun edgeCases(root: ProviderTree): List<GeneratedValue<R>> {
        return wrappedGen.edgeCases(root).map { it.map(fn) }
    }

    fun GeneratedValue<T>.map(fn: (T) -> R): GeneratedValue<R> = GeneratedValue(
        value = fn(value),
        shrinks = shrinks,
        usedTree = usedTree,
    )
}
