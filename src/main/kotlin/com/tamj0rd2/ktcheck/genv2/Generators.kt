package com.tamj0rd2.ktcheck.genv2

import com.tamj0rd2.ktcheck.gen.OneOfEmpty
import kotlin.reflect.KClass

fun <T> Gen.Companion.constant(value: T): Gen<T> = sample().map { value }

fun <T> Gen.Companion.oneOf(gens: List<Gen<T>>): Gen<T> {
    if (gens.isEmpty()) throw OneOfEmpty()
    return int(0..<gens.size).flatMap { gens[it] }
}

@Suppress("unused")
class OneOfEmpty : IllegalStateException("Gen.oneOf() called with no generators")

/** Shrinks toward the first value */
fun <T> Gen.Companion.of(values: Collection<T>): Gen<T> = Gen.oneOf(values.map(::constant))

@JvmName("ofComparable")
        /** Shrinks toward the smallest value */
fun <T : Comparable<T>> Gen.Companion.of(values: Collection<T>): Gen<T> = Gen.oneOf(values.sorted().map(::constant))

@Suppress("unused")
fun <T> Gen<T>.filter(predicate: (T) -> Boolean): Gen<T> = filter(100, predicate)

@Suppress("unused", "UnusedReceiverParameter")
fun <T> Gen<T>.filter(threshold: Int, predicate: (T) -> Boolean): Gen<T> = TODO("Not yet implemented")

@Suppress("unused")
class FilterLimitReached(threshold: Int) :
    IllegalStateException("Gen.filter() exceeded the threshold of misses")

@Suppress("unused", "UnusedReceiverParameter")
fun <T> Gen<T>.ignoreExceptions(klass: KClass<out Throwable>, threshold: Int = 100): Gen<T> =
    TODO("Not yet implemented")

@Suppress("unused")
class ExceptionLimitReached(threshold: Int, override val cause: Throwable) :
    IllegalStateException("Gen.ignoreExceptions() exceeded the threshold of $threshold exceptions")

