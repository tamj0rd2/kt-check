@file:Suppress("unused")

package com.tamj0rd2.ktcheck.gen

import kotlin.random.Random
import kotlin.reflect.KClass

fun interface Gen<T> {
    fun generate(cs: ChoiceSequence): T

    companion object
}

fun <T> Gen<T>.sample(random: Random = Random.Default): T = generate(WritableChoiceSequence(random))

fun <T, R> Gen<T>.map(fn: (T) -> R) = Gen { cs -> fn(generate(cs)) }

fun <T, R> Gen<T>.flatMap(fn: (T) -> Gen<R>): Gen<R> = Gen { cs -> fn(generate(cs)).generate(cs) }

fun <T> Gen<T>.filter(predicate: (T) -> Boolean) = Gen { cs -> generateSequence { generate(cs) }.filter { predicate(it) }.first() }

fun <T> Gen<T>.ignoreExceptions(klass: KClass<out Throwable>, threshold: Int = 1000): Gen<T> {
    var exceptionCount = 0
    return Gen { cs ->
        sequence {
                while (true) {
                    try {
                        yield(this@ignoreExceptions.generate(cs))
                    } catch (e: Throwable) {
                        exceptionCount++
                        when {
                            !klass.isInstance(e) -> throw e
                            exceptionCount >= threshold -> throw ExceptionLimitReached(threshold, e)
                        }
                    }
                }
            }
            .first()
    }
}

fun <T> Gen<T>.list(size: Int): Gen<List<T>> = Gen.mapN(List(size) { this }) { it }

fun <T> Gen<T>.list(size: IntRange = 0..100): Gen<List<T>> = Gen.int(size).flatMap { size -> list(size) }

fun Gen<Char>.string(size: Int): Gen<String> = list(size).map { it.joinToString("") }

fun Gen<Char>.string(size: IntRange = 0..100): Gen<String> = Gen.int(size).flatMap { size -> string(size) }

fun <T> Gen.Companion.constant(value: T) = Gen { value }

fun <T, R> Gen.Companion.mapN(gens: List<Gen<T>>, fn: (List<T>) -> R): Gen<R> = Gen { cs -> fn(gens.map { gen -> gen.generate(cs) }) }

fun Gen.Companion.int(range: IntRange) = Gen { cs -> cs.randomInt(range) }

fun <T> Gen.Companion.oneOf(gens: List<Gen<T>>): Gen<T> {
    if (gens.isEmpty()) throw OneOfEmpty()
    return int(0..<gens.size).flatMap { gens[it] }
}

// shrinks toward the first value
fun <T> Gen.Companion.of(values: Collection<T>): Gen<T> = Gen.oneOf(values.map(::constant))

@JvmName("ofComparable")
// shrinks toward the smallest value
fun <T : Comparable<T>> Gen.Companion.of(values: Collection<T>): Gen<T> = Gen.oneOf(values.sorted().map(::constant))

class SizeExceeded : IllegalStateException()

class OneOfEmpty : IllegalStateException("Gen.oneOf() called with no generators")

class ExceptionLimitReached(threshold: Int, override val cause: Throwable) :
    IllegalStateException("Gen.ignoreExceptions() exceeded the threshold of $threshold exceptions")

@JvmInline
value class Size(val value: Int) {
    init {
        if (value < 0) throw SizeExceeded()
    }

    operator fun plus(other: Size) = Size(this.value + other.value)

    operator fun compareTo(other: Size): Int = this.value.compareTo(other.value)

    operator fun minus(other: Size): Size = Size(this.value - other.value)

    companion object {
        val zero = Size(0)
    }
}

operator fun Size?.minus(size: Size): Size? = this?.let { it - size }
