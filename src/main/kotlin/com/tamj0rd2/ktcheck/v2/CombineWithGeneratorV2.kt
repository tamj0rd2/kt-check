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

        return GenResultV2(
            value = combine(leftResult.value, rightResult.value),
            shrinks = createShrinks(leftResult, rightResult)
        )
    }

    private fun createShrinks(
        leftResult: GenResultV2<T1>,
        rightResult: GenResultV2<T2>,
        depth: Int = 0,
    ): Sequence<GenResultV2<R>> {
        // Shrink left, recursively shrink right for each left shrink
        val leftShrinks = leftResult.shrinks.flatMap { leftShrink ->
            sequenceOf(
                GenResultV2(
                    value = combine(leftShrink.value, rightResult.value),
                    shrinks = createShrinks(leftShrink, rightResult, depth + 1)
                )
            ) + rightResult.shrinks.map { rightShrink ->
                GenResultV2(
                    value = combine(leftShrink.value, rightShrink.value),
                    shrinks = createShrinks(leftShrink, rightShrink, depth + 1)
                )
            }
        }

        // Shrink right with original left
        val rightShrinks = rightResult.shrinks.map { rightShrink ->
            GenResultV2(
                value = combine(leftResult.value, rightShrink.value),
                shrinks = createShrinks(leftResult, rightShrink, depth + 1)
            )
        }

        return leftShrinks + rightShrinks
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
