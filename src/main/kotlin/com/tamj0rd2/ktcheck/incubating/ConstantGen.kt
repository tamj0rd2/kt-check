package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asSuccess

internal data class ConstantGen<T>(val value: T) : GenProvider<T> {
    override fun generate(ctx: GenContext): Result4k<GeneratedValue<T>, GenerationException> =
        GeneratedValue(value, emptySequence()).asSuccess()
}
