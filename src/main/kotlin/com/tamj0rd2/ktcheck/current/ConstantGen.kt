package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asSuccess

internal class ConstantGen<T>(
    private val value: T,
) : Generator<T> {
    override fun generate(root: RandomTree): Result4k<GeneratedValue<T>, GenerationException> =
        GeneratedValue(value = value, shrinks = emptySequence(), usedTree = root).asSuccess()

    override fun edgeCases(root: RandomTree): List<GeneratedValue<T>> {
        return emptyList()
    }
}
