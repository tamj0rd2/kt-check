package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.GenBuilders
import com.tamj0rd2.ktcheck.contracts.BaseContract
import com.tamj0rd2.ktcheck.contracts.GenResults
import com.tamj0rd2.ktcheck.core.GenerationContext
import com.tamj0rd2.ktcheck.core.Seed

internal abstract class BaseContractImplV2 : BaseContract, GenBuilders by GenV2 {
    override fun ctx(seed: Seed): GenerationContext {
        TODO("Not yet implemented")
    }

    @Deprecated("use Gen.collectShrunkValues instead, or samples if shrinks are unnecessary.")
    override fun <T> Gen<T>.generate(ctx: GenerationContext): GenResults<T> {
        TODO("Not yet implemented")
    }

    @Deprecated("todo: delete this. generate should produce edge cases now.")
    override fun <T> Gen<T>.edgeCase(seed: Seed): GenResults<T>? {
        TODO("Not yet implemented")
    }
}
