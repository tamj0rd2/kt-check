package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asResultOr
import dev.forkhandles.result4k.valueOrNull
import kotlin.reflect.KClass

internal class IgnoreExceptionGen<T>(
    private val wrappedGen: Generator<T>,
    private val klass: KClass<out Exception>,
    private val threshold: Int,
) : Generator<T> {
    override fun generate(root: ProviderTree): Result4k<GeneratedValue<T>, GenerationException> {
        var latestError: Throwable? = null

        return root.traversingRight()
            .take(threshold)
            .mapNotNull {
                tryGenerating(it.left)
                    .onFailure { ex -> latestError = ex }
                    .getOrNull()
            }
            .map { buildResult(root, it) }
            .firstOrNull()
            .asResultOr { GenerationException.FilterLimitReached(threshold, latestError) }
    }

    override fun edgeCases(root: ProviderTree): List<GeneratedValue<T>> {
        return emptyList()
    }

    private fun buildResult(
        root: ProviderTree,
        result: GeneratedValue<T>,
    ): GeneratedValue<T> = GeneratedValue(
        value = result.value,
        shrinks = result.shrinks
            .filter { tryGenerating(it).isSuccess }
            .map { root.withLeft(it) },
        usedTree = root,
    )

    private fun tryGenerating(tree: ProviderTree) =
        runCatching { wrappedGen.generate(tree).valueOrNull() }
            .onFailure { if (!klass.isInstance(it)) throw it }
}
