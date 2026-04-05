package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.map

internal data class MappingGen<T, R>(
    private val provider: GenProvider<T>,
    private val fn: (T) -> R,
) : GenProvider<R> {
    override fun generate(ctx: GenContext): Result4k<GeneratedValue<R>, GenerationException> =
        provider.generate(ctx).map { it.map(fn) }
}
