package com.tamj0rd2.ktcheck.genv2

import kotlin.reflect.KClass

@Suppress("unused")
fun <T> Gen<T>.filter(predicate: (T) -> Boolean): Gen<T> = filter(100, predicate)

fun <T> Gen<T>.filter(threshold: Int, predicate: (T) -> Boolean): Gen<T> = Gen { tree ->
    var attempts = 0
    var currentTree = tree

    while (attempts < threshold) {
        val (value, shrinks) = generate(currentTree.left)
        if (predicate(value)) return@Gen GenResult(value, shrinks)

        attempts++
        currentTree = currentTree.right
    }

    throw FilterLimitReached(threshold)
}

@Suppress("unused")
class FilterLimitReached(threshold: Int) :
    IllegalStateException("Gen.filter() exceeded the threshold of misses")

fun <T> Gen<T>.ignoreExceptions(klass: KClass<out Throwable>, threshold: Int = 100): Gen<T> = Gen { tree ->
    var exceptionCount = 0
    var currentTree = tree

    while (exceptionCount < threshold) {
        try {
            return@Gen generate(currentTree.left)
        } catch (e: Throwable) {
            exceptionCount++

            when {
                !klass.isInstance(e) -> throw e
                exceptionCount >= threshold -> throw ExceptionLimitReached(threshold, e)
                else -> currentTree = currentTree.right
            }
        }
    }

    throw ExceptionLimitReached(threshold, IllegalStateException("Threshold reached"))
}

@Suppress("unused")
class ExceptionLimitReached(threshold: Int, override val cause: Throwable) :
    IllegalStateException("Gen.ignoreExceptions() exceeded the threshold of $threshold exceptions")
