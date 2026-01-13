package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.v1.ProducerTree

internal class FlatMappingGeneratorV2<T, R>(
    val gen: GenV2<T>,
    val fn: (T) -> Gen<R>,
) : GenV2<R>() {
    override fun GenContextV2.generate(): GenResultV2<R> {
        val outerResult = gen.generate(tree.left)
        val innerGen = fn(outerResult.value) as GenV2<R>
        val innerResult = innerGen.generate(tree.right)

        return GenResultV2(
            value = innerResult.value,
            shrinks = createOuterShrinks(outerResult, tree.right) + innerResult.shrinks,
        )
    }

    private fun createOuterShrinks(outerResult: GenResultV2<T>, tree: ProducerTree): Sequence<GenResultV2<R>> =
        outerResult.shrinks.map { outerShrink ->
            val innerGen = fn(outerShrink.value) as GenV2<R>
            val innerShrink = innerGen.generate(tree)

            GenResultV2(
                value = innerShrink.value,
                shrinks = createOuterShrinks(outerShrink, tree) + innerShrink.shrinks,
            )
        }
}
