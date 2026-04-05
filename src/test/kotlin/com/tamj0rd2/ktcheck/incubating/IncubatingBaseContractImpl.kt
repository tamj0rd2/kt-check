package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenBuilders
import com.tamj0rd2.ktcheck.contracts.BaseContract
import com.tamj0rd2.ktcheck.contracts.GenResults
import com.tamj0rd2.ktcheck.core.GenerationContext
import com.tamj0rd2.ktcheck.core.Seed
import dev.forkhandles.result4k.orThrow

internal abstract class IncubatingBaseContractImpl : BaseContract, GenBuilders by Gen.Companion {
    override fun ctx(seed: Seed): GenerationContext = GenContext.new(seed)

    override fun <T> com.tamj0rd2.ktcheck.Gen<T>.generate(ctx: GenerationContext): GenResults<T> {
        return buildGenResults((this as Gen).generate(ctx as GenContext).orThrow())
    }

    override fun <T> com.tamj0rd2.ktcheck.Gen<T>.edgeCase(seed: Seed): GenResults<T> {
        return generate(GenContext.new(seed, ShouldGenerateEdgeCase.Always))
    }

    private fun <T> buildGenResults(result: GeneratedValue<T>): GenResults<T> = GenResults(
        value = result.value,
        shrinks = result.shrinks.map { buildGenResults(it) }
    )
}
