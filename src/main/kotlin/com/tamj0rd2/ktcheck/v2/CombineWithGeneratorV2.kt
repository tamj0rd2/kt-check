package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.core.ProducerTree

internal class CombineWithGeneratorV2<T1, T2, R>(
    private val leftGen: GenV2<T1>,
    private val rightGen: GenV2<T2>,
    private val combine: (T1, T2) -> R,
) : GenV2<R>() {
    override fun generate(tree: ProducerTree): GenResultV2<R> {
        val leftResult = leftGen.generate(tree.left)
        val rightResult = rightGen.generate(tree.right)

        // todo: interleaving could produce better shrink results.
        val leftBasedShrinks = createLeftShrinks(leftResult, rightResult.value)
        val rightBasedShrinks = createRightShrinks(leftResult.value, rightResult)

        return GenResultV2(
            value = combine(leftResult.value, rightResult.value),
            shrinks = leftBasedShrinks + rightBasedShrinks
        )
    }

    private fun createLeftShrinks(
        leftResult: GenResultV2<T1>,
        rightValue: T2,
    ): Sequence<GenResultV2<R>> =
        leftResult.shrinks.map { leftShrink ->
            GenResultV2(
                value = combine(leftShrink.value, rightValue),
                shrinks = createLeftShrinks(leftShrink, rightValue)
            )
        }

    private fun createRightShrinks(
        leftValue: T1,
        rightResult: GenResultV2<T2>,
    ): Sequence<GenResultV2<R>> =
        rightResult.shrinks.map { rightShrink ->
            GenResultV2(
                value = combine(leftValue, rightShrink.value),
                shrinks = createRightShrinks(leftValue, rightShrink)
            )
        }
}
