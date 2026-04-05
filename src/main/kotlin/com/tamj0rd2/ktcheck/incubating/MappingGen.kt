package com.tamj0rd2.ktcheck.incubating

internal data class MappingGen<T, R>(
    private val provider: GenProvider<T>,
    private val fn: (T) -> R,
) : GenProvider<R> {
    override fun generate(ctx: GenContext): GeneratedValue<R> = provider.generate(ctx).map(fn)
}
