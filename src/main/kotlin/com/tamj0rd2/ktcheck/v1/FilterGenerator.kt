package com.tamj0rd2.ktcheck.v1

import com.tamj0rd2.ktcheck.GenerationException.FilterLimitReached
import com.tamj0rd2.ktcheck.core.ProducerTree
import kotlin.reflect.KClass

internal sealed class FilterGenerator<T>(
    private val threshold: Int,
) : GenV1<T>() {
    override fun GenContext.generate(): GenResult<T> {
        var lastFailure: Exception? = null

        return generateSequence(tree) { it.right }
            .take(threshold)
            .map { getResult(it.left, mode) }
            .onEach { if (it is FilterResult.Failed) lastFailure = it.failure }
            .filterIsInstance<FilterResult.Succeeded<T>>()
            .map { (genResult) ->
                val validShrinks = genResult.shrinks
                    .filter { getResult(it, GenMode.Shrinking) is FilterResult.Succeeded }
                    .map { tree.withLeft(it) }

                genResult.copy(shrinks = validShrinks)
            }
            .firstOrNull()
            ?: throw FilterLimitReached(threshold, lastFailure)
    }

    protected abstract fun getResult(tree: ProducerTree, mode: GenMode): FilterResult<T>

    protected interface FilterResult<T> {
        @JvmInline
        value class Succeeded<T>(val genResult: GenResult<T>) : FilterResult<T> {
            operator fun component1() = genResult
        }

        @JvmInline
        value class Failed<T>(val failure: Exception? = null) : FilterResult<T>
    }
}

internal class PredicateFilterGenerator<T>(
    private val gen: GenV1<T>,
    threshold: Int,
    private val predicate: (T) -> Boolean,
) : FilterGenerator<T>(threshold) {
    override fun getResult(tree: ProducerTree, mode: GenMode): FilterResult<T> {
        val result = gen.generate(tree, mode)
        return if (predicate(result.value)) FilterResult.Succeeded(result) else FilterResult.Failed()
    }
}

internal class ExceptionIgnoringGenerator<T>(
    private val gen: GenV1<T>,
    threshold: Int,
    private val klass: KClass<out Exception>,
) : FilterGenerator<T>(threshold) {
    override fun getResult(tree: ProducerTree, mode: GenMode): FilterResult<T> =
        try {
            FilterResult.Succeeded(gen.generate(tree, mode))
        } catch (e: Exception) {
            when {
                !klass.isInstance(e) -> throw e
                else -> FilterResult.Failed(e)
            }
        }
}
