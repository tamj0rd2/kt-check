package com.tamj0rd2.ktcheck.incubating

internal data class ConstantGen<T>(val value: T) : GenProvider<T> {
    override fun generate(ctx: GenContext): GenResult<T> = GenResult(value, emptySequence())
}
