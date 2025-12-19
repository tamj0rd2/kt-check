package com.tamj0rd2.ktcheck.genv2

fun <T> Gen.Companion.constant(value: T): Gen<T> = sample().map { value }

fun <T> Gen<T>.list(size: IntRange = 0..100): Gen<List<T>> = list(Gen.int(size))

fun <T> Gen<T>.list(size: Gen<Int>): Gen<List<T>> = size.flatMap(::list)

fun <T> Gen<T>.list(size: Int): Gen<List<T>> = List(size) { this }
    .fold(Gen.constant(emptyList())) { listGen, elementGen ->
        listGen.flatMap { listSoFar ->
            elementGen.map { value -> listSoFar + value }
        }
    }

fun <T> Gen.Companion.oneOf(gens: List<Gen<T>>): Gen<T> {
    if (gens.isEmpty()) throw OneOfEmpty()
    return int(0..<gens.size).flatMap { gens[it] }
}

class OneOfEmpty : IllegalStateException("Gen.oneOf() called with no generators")

/** Shrinks toward the first value */
fun <T> Gen.Companion.of(values: Collection<T>): Gen<T> = Gen.oneOf(values.map(::constant))

@JvmName("ofComparable")
        /** Shrinks toward the smallest value */
fun <T : Comparable<T>> Gen.Companion.of(values: Collection<T>): Gen<T> = Gen.oneOf(values.sorted().map(::constant))

fun <T> Gen<T>.filter(predicate: (T) -> Boolean): Gen<T> = filter(100, predicate)
fun <T> Gen<T>.filter(threshold: Int, predicate: (T) -> Boolean): Gen<T> = TODO("Not yet implemented")

class FilterLimitReached(threshold: Int, override val cause: Throwable) :
    IllegalStateException("Gen.ignoreExceptions() exceeded the threshold of $threshold exceptions")

fun <T> Gen<T>.ignoreExceptions(klass: Class<out Throwable>, threshold: Int = 100): Gen<T> = TODO("Not yet implemented")

class ExceptionLimitReached(threshold: Int, override val cause: Throwable) :
    IllegalStateException("Gen.ignoreExceptions() exceeded the threshold of $threshold exceptions")

