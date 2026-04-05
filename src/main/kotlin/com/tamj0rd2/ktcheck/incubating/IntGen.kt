package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker

internal data class IntGen(
    private val range: IntRange,
    private val shrinkTarget: Int,
) : GenProvider<Int> {
    init {
        require(shrinkTarget in range) { "shrinkTarget $shrinkTarget not in range $range" }
    }

    private val edgeCases = setOf(range.first, range.first + 1, -1, 0, 1, range.last - 1, range.last)
        .filter { it in range }
        .distinct()

    override fun generate(ctx: GenContext): GenResult<Int> {
        val value = if (ctx.generateEdgeCase) {
            edgeCases.random(ctx.random)
        } else {
            range.random(ctx.random)
        }

        return buildResult(value)
    }

    private fun buildResult(value: Int): GenResult<Int> = GenResult(
        value = value,
        shrinks = IntShrinker.shrink(value, range, shrinkTarget).map { buildResult(it) }
    )
}
