package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.onFailure
import dev.forkhandles.result4k.orThrow

internal class CombineWithGen<T1, T2, R>(
    private val leftGen: Generator<T1>,
    private val rightGen: Generator<T2>,
    private val combine: (T1, T2) -> R,
) : Generator<R> {
    override fun generate(root: ProviderTree): Result4k<GeneratedValue<R>, GenerationException> {
        val leftResult = leftGen.generate(root.left).onFailure { return it }
        val rightResult = rightGen.generate(root.right).onFailure { return it }
        return buildResult(root, leftResult, rightResult).asSuccess()
    }

    override fun edgeCases(root: ProviderTree): List<GeneratedValue<R>> {
        val leftEdgeCases = leftGen.edgeCases(root.left)
        val rightEdgeCases = rightGen.edgeCases(root.right)

        if (leftEdgeCases.isEmpty() && rightEdgeCases.isEmpty()) {
            return emptyList()
        }

        val leftEdgeCasesToUse = leftEdgeCases.ifEmpty { listOf(leftGen.generate(root.left).orThrow()) }
        val rightEdgeCasesToUse = rightEdgeCases.ifEmpty { listOf(rightGen.generate(root.right).orThrow()) }

        return leftEdgeCasesToUse.flatMap { leftEdgeCase ->
            rightEdgeCasesToUse.map { rightEdgeCase ->
                val stableRoot = root
                    .withLeft(leftEdgeCase.usedTree)
                    .withRight(rightEdgeCase.usedTree)

                buildResult(stableRoot, leftEdgeCase, rightEdgeCase)
            }
        }
    }

    private fun buildResult(
        root: ProviderTree,
        leftResult: GeneratedValue<T1>,
        rightResult: GeneratedValue<T2>,
    ): GeneratedValue<R> {
        val leftBasedShrinks = leftResult.shrinks.map { root.withLeft(it) }
        val rightBasedShrinks = rightResult.shrinks.map { root.withRight(it) }

        /**
         * Although these shrinks would be reached recursively, keep in mind that this behaviour exists in the context
         * of a property based testing library. Let's say we have a gen that gives back a pair of ints in range 0..10.
         * If we recurse over all shrinks, we'll eventually reach (5, 5), however, we'll only reach it if the test is
         * falsified before then. If all the tests that would lead to that value succeed, we'll never get to (5, 5).
         * For example, (5, 10) might succeed, meaning (5, 5) will never be attempted.
         * So we need cartesian shrinks to help reach values that we otherwise may not.
         */
        val cartesianShrinks = leftResult.shrinks.flatMap { left ->
            rightResult.shrinks.map { right ->
                root.withLeft(left).withRight(right)
            }
        }

        return GeneratedValue(
            value = combine(leftResult.value, rightResult.value),
            shrinks = cartesianShrinks + leftBasedShrinks + rightBasedShrinks,
            usedTree = root,
        )
    }
}
