package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.gen.PredicateResult.Failed
import com.tamj0rd2.ktcheck.gen.PredicateResult.Succeeded
import com.tamj0rd2.ktcheck.producer.ProducerTree
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
    private val getResult: (ProducerTree) -> PredicateResult<T>,
) : Gen<T>() {
    override fun generate(tree: ProducerTree): GenResult<T> {
        var lastFailure: Exception? = null

        return generateSequence(tree) { it.right }
            .take(threshold)
            .map { getResult(it.left) }
            .onEach { if (it is Failed) lastFailure = it.failure }
            .filterIsInstance<Succeeded<T>>()
            .map { (genResult) -> genResult.copy(shrinks = emptySequence()) }
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
fun <T> Gen<T>.filter(threshold: Int, predicate: (T) -> Boolean): Gen<T> = FilterGenerator(threshold) { tree ->
    val result = generate(tree)
    if (predicate(result.value)) Succeeded(result) else Failed()
}

/**
 * Ignores exceptions of type [klass] thrown during generation. Shrinking is not supported when using this generator.
 * Instead, use generators that only generate valid values.
 */
fun <T> Gen<T>.ignoreExceptions(klass: KClass<out Exception>, threshold: Int = 100): Gen<T> =
    FilterGenerator(threshold) { tree ->
        try {
            val result = generate(tree)
            Succeeded(result)
        } catch (e: Exception) {
            when {
                !klass.isInstance(e) -> throw e
                else -> Failed(e)
            }
        }
    }
