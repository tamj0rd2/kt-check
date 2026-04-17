package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.map

internal data class MappingGen<T, R>(
    private val gen: Gen<T>,
    private val fn: (T) -> R,
) : GenProvider<R> {
    override fun generate(rootCtx: GenContext): Result4k<GeneratedValue<R>, GenerationException> =
        gen.generate(rootCtx).map { it.map(fn) }
}
