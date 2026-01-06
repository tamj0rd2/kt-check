package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.gen.PredicateResult.Failed
import com.tamj0rd2.ktcheck.gen.PredicateResult.Succeeded
import kotlin.reflect.KClass

private sealed interface PredicateResult<T> {
    @JvmInline
    value class Succeeded<T>(val genResult: GenResult<T>) : PredicateResult<T> {
        operator fun component1() = genResult
    }

    @JvmInline
    value class Failed<T>(val failure: Exception? = null) : PredicateResult<T>
}

private class FilterGenerator<T>(
    private val threshold: Int,
    private val getResult: GenContext.() -> PredicateResult<T>,
) : Gen<T>() {
    override fun GenContext.generate(): GenResult<T> {
        var lastFailure: Exception? = null

        return generateSequence(tree) { it.right }
            .take(threshold)
            .map { getResult(GenContext(it.left, mode)) }
            .onEach { if (it is Failed) lastFailure = it.failure }
            .filterIsInstance<Succeeded<T>>()
            .map { (genResult) ->
                val shrinks = genResult.shrinks.map { tree.withLeft(it) }
                genResult.copy(shrinks = shrinks)
            }
            .firstOrNull()
            ?: throw FilterLimitReached(threshold, lastFailure)
    }
}

class FilterLimitReached(threshold: Int, cause: Throwable?) :
    GenerationException("Filter failed after $threshold misses", cause)

/**
 * Filters generated values using the given [predicate]. Shrinking is not supported when using this generator. Instead,
 * use generators that only generate valid values.
 */
fun <T> Gen<T>.filter(predicate: (T) -> Boolean) = filter(100, predicate)

/**
 * Filters generated values using the given [predicate]. Shrinking is not supported when using this generator. Instead,
 * use generators that only generate valid values.
 */
fun <T> Gen<T>.filter(threshold: Int, predicate: (T) -> Boolean): Gen<T> =
    FilterGenerator(threshold) {
        val result = generate(tree, mode)
        if (predicate(result.value)) Succeeded(result) else Failed()
    }

/**
 * Ignores exceptions of type [klass] thrown during generation. Although this generator supports shrinking, it is very
 * inefficient. Instead of using this generator, consider using generators that do not throw exceptions.
 */
fun <T> Gen<T>.ignoreExceptions(klass: KClass<out Exception>, threshold: Int = 100): Gen<T> =
    FilterGenerator(threshold) {
        try {
            val (value, shrinks) = generate(tree, mode)
            val validShrinks = shrinks.filter { tree ->
                try {
                    generate(tree, GenMode.Shrinking)
                    true
                } catch (e: Exception) {
                    false
                }
            }
            Succeeded(GenResult(value, validShrinks))
        } catch (e: Exception) {
            when {
                !klass.isInstance(e) -> throw e
                else -> Failed(e)
            }
        }
    }
