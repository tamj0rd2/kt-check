package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker

internal class IntGen(
    private val range: IntRange,
    private val shrinkTarget: Int,
) : GenImpl<Int>() {
    init {
        require(shrinkTarget in range) { "Shrink target must be within the specified range." }
    }

    override fun generate(tree: RandomTree): GenResultV2<Int> {
        val value = tree.data.int(range)
        return buildResult(value, tree)
    }

    override fun edgeCases(): List<GenResultV2<Int>> {
        return listOf(0, shrinkTarget, range.first, range.last)
            .flatMap { listOf(it, it + 1, it - 1) }
            .distinct()
            .filter { it in range }
            .map { buildResult(it, edgeCaseTree) }
    }

    private fun buildResult(value: Int, tree: RandomTree): GenResultV2<Int> = GenResultV2(
        value = value,
        shrinks = IntShrinker.shrink(
            value = value,
            range = range,
            target = shrinkTarget
        ).map { tree.withData(ValueProvider.Shrunk(it)) },
    )
}
