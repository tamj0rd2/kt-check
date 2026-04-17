package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenBuilders
import com.tamj0rd2.ktcheck.GenerationException
import com.tamj0rd2.ktcheck.contracts.BaseContract
import com.tamj0rd2.ktcheck.contracts.GenResults
import com.tamj0rd2.ktcheck.core.GenerationContext
import com.tamj0rd2.ktcheck.core.Seed
import dev.forkhandles.result4k.orThrow

internal abstract class BaseContractImpl : BaseContract, GenBuilders by Gen.Companion {
    override fun ctx(seed: Seed): GenerationContext = GenContext.new(seed)

    override fun <T> com.tamj0rd2.ktcheck.Gen<T>.generate(ctx: GenerationContext): GenResults<T> {
        val generatedValue = (this as Gen).generate(ctx as GenContext).orThrow()
        return results(generatedValue)
    }

    private fun <T> Gen<T>.results(generatedValue: GeneratedValue<T>): GenResults<T> = GenResults(
        value = generatedValue.value,
        shrinks = generatedValue.shrinks.mapNotNull {
            try {
                generate(it as GenerationContext)
            } catch (e: GenerationException) {
                null
            }
        }
    )

    override fun <T> com.tamj0rd2.ktcheck.Gen<T>.edgeCase(seed: Seed): GenResults<T> {
        return generate(GenContext.new(seed, InfluenceGeneration.Always))
    }
}
