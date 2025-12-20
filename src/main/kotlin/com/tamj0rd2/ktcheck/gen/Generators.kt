package com.tamj0rd2.ktcheck.gen

fun <T> Gen.Companion.constant(value: T): Gen<T> = sample().map { value }

// todo:does this distribute appropriately?
fun Gen.Companion.boolean(): Gen<Boolean> =
    sample().map { sample -> (sample.value and 1UL) == 1UL }

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

