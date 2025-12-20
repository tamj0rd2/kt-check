package com.tamj0rd2.ktcheck.genv2

import com.tamj0rd2.ktcheck.genv2.PredicateResult.Failed
import com.tamj0rd2.ktcheck.genv2.PredicateResult.Succeeded
import kotlin.reflect.KClass

private sealed interface PredicateResult<T> {
    data class Succeeded<T>(val genResult: GenResult<T>) : PredicateResult<T>
    data class Failed<T>(val failure: Exception? = null) : PredicateResult<T>
}

private class FilterGenerator<T>(
    private val threshold: Int,
    private val getResult: (SampleTree) -> PredicateResult<T>,
) : Gen<T>() {
    override fun generate(tree: SampleTree): GenResult<T> {
        var attempts = 0
        var currentTree = tree
        var lastFailure: Exception? = null

        while (attempts < threshold) {
            attempts++

            when (val result = getResult(currentTree.left)) {
                is Succeeded<T> -> return result.genResult
                is Failed -> {
                    currentTree = currentTree.right
                    lastFailure = result.failure
                }
            }
        }

        throw FilterLimitReached(threshold, lastFailure)
    }
}

class FilterLimitReached(threshold: Int, cause: Throwable?) :
    IllegalStateException("Filter failed $threshold times", cause)

fun <T> Gen<T>.filter(predicate: (T) -> Boolean) = filter(100, predicate)

fun <T> Gen<T>.filter(threshold: Int, predicate: (T) -> Boolean): Gen<T> = FilterGenerator(threshold) { tree ->
    val result = generate(tree)
    if (predicate(result.value)) Succeeded(result) else Failed()
}

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
