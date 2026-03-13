package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.onFailure

internal class FlatMapGen<T, R>(
    private val wrappedGen: Generator<T>,
    private val fn: (T) -> Generator<R>,
) : Generator<R> {
    override fun generate(root: RandomTree): Result4k<GeneratedValue<R>, GenerationException> {
        val outerResult = wrappedGen.generate(root.left).onFailure { return it }
        val innerResult = fn(outerResult.value).generate(root.right).onFailure { return it }
        return buildResult(root = root, outerResult = outerResult, innerResult = innerResult).asSuccess()
    }

    override fun edgeCases(root: RandomTree): List<GeneratedValue<R>> {
        return wrappedGen.edgeCases(root.left).flatMap { outerEdgeCase ->
            fn(outerEdgeCase.value).edgeCases(root.right).map { innerEdgeCase ->
                val stableRoot = root
                    .withLeft(outerEdgeCase.usedTree)
                    .withRight(innerEdgeCase.usedTree)

                buildResult(root = stableRoot, outerResult = outerEdgeCase, innerResult = innerEdgeCase)
            }
        }
    }

    private fun buildResult(
        root: RandomTree,
        outerResult: GeneratedValue<T>,
        innerResult: GeneratedValue<R>,
    ): GeneratedValue<R> {
        val outerBasedShrinks = outerResult.shrinks.map { root.withLeft(it) }
        val innerBasedShrinks = innerResult.shrinks.map { root.withRight(it) }
        val shrinks = outerBasedShrinks + innerBasedShrinks

        return GeneratedValue(
            value = innerResult.value,
            shrinks = shrinks,
            usedTree = root,
        )
    }
}
