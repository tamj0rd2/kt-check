package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asSuccess

internal data class ConstantGen<T>(
    private val value: T,
) : GenProvider<T> {
    override fun generate(rootCtx: GenContext): Result4k<GeneratedValue<T>, GenerationException> =
        GeneratedValue(
            ctx = rootCtx,
            value = value,
            shrinks = emptySequence()
        ).asSuccess()
}
