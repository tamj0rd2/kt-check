package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenerationException

internal data class FilterGen<T>(
    private val delegate: Gen<T>,
    private val threshold: Int,
    private val predicate: (T) -> Boolean,
) : GenProvider<T> {
    override fun generate(ctx: GenContext): GenResult<T> {
        return generateSequence(ctx) { it.right }
            .take(threshold)
            .map { delegate.generate(it.left) }
            .mapNotNull { it.filter(predicate) }
            .firstOrNull()
        // todo: return result rather than throwing here.
            ?: throw GenerationException.FilterLimitReached(threshold)
    }
}
