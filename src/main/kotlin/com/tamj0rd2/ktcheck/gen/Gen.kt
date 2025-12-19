@file:Suppress("unused")

package com.tamj0rd2.ktcheck.gen

import kotlin.random.Random
import kotlin.reflect.KClass

sealed interface Gen<T> {
    fun generate(choices: ChoiceSequence): T

    companion object
}

private class BuilderGenerator<T>(private val fn: (ChoiceSequence) -> T) : Gen<T> {
    override fun generate(choices: ChoiceSequence): T = fn(choices)
}

private class MappingGenerator<T, R>(
    private val base: Gen<T>,
    private val fn: (T) -> R,
) : Gen<R> {
    override fun generate(choices: ChoiceSequence): R = fn(base.generate(choices))
}

private class FlatMappingGenerator<T, R>(
    private val base: Gen<T>,
    private val fn: (T) -> Gen<R>,
) : Gen<R> {
    override fun generate(choices: ChoiceSequence): R = fn(base.generate(choices)).generate(choices)
}

private class FilteringGenerator<T>(
    private val base: Gen<T>,
    private val predicate: (T) -> Boolean,
) : Gen<T> {
    override fun generate(choices: ChoiceSequence): T =
        generateSequence { base.generate(choices) }.first { predicate(it) }
}

private class ExceptionSuppressingGenerator<T>(
    private val base: Gen<T>,
    private val klass: KClass<out Throwable>,
    private val threshold: Int,
) : Gen<T> {
    override fun generate(choices: ChoiceSequence): T {
        var exceptionCount = 0
        return sequence {
            while (true) {
                try {
                    yield(generate(choices))
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

private class MapNGenerator<T, R>(
    private val gens: List<Gen<T>>,
    private val fn: (List<T>) -> R,
) : Gen<R> {
    override fun generate(choices: ChoiceSequence): R =
        fn(gens.map { gen -> gen.generate(choices) })
}

private class ConstantGenerator<T>(private val value: T) : Gen<T> {
    override fun generate(choices: ChoiceSequence): T = value
}

private class IntGenerator(private val range: IntRange) : Gen<Int> {
    override fun generate(choices: ChoiceSequence): Int = choices.randomInt(range)
}

data class GenContext(private val choices: ChoiceSequence) {
    fun <T> Gen<T>.bind() = generate(choices)
}

fun <T> gen(fn: GenContext.() -> T): Gen<T> = BuilderGenerator { cs -> GenContext(cs).fn() }

fun <T> Gen<T>.sample(random: Random = Random.Default): T = generate(WritableChoiceSequence(random))

fun <T, R> Gen<T>.map(fn: (T) -> R): Gen<R> = MappingGenerator(this, fn)

fun <T, R> Gen<T>.flatMap(fn: (T) -> Gen<R>): Gen<R> = FlatMappingGenerator(this, fn)

fun <T> Gen<T>.filter(predicate: (T) -> Boolean): Gen<T> = FilteringGenerator(this, predicate)

fun <T> Gen<T>.ignoreExceptions(klass: KClass<out Throwable>, threshold: Int = 1000): Gen<T> =
    ExceptionSuppressingGenerator(this, klass, threshold)

fun <T> Gen<T>.list(size: Int): Gen<List<T>> = Gen.mapN(List(size) { this }) { it }

fun <T> Gen<T>.list(size: IntRange = 0..100): Gen<List<T>> = Gen.int(size).flatMap { size -> list(size) }

fun Gen<Char>.string(size: Int): Gen<String> = list(size).map { it.joinToString("") }

fun Gen<Char>.string(size: IntRange = 0..100): Gen<String> = Gen.int(size).flatMap { size -> string(size) }

fun <T> Gen.Companion.constant(value: T): Gen<T> = ConstantGenerator(value)

fun <T, R> Gen.Companion.mapN(gens: List<Gen<T>>, fn: (List<T>) -> R): Gen<R> = MapNGenerator(gens, fn)

fun Gen.Companion.int(range: IntRange): Gen<Int> = IntGenerator(range)

fun <T> Gen.Companion.oneOf(gens: List<Gen<T>>): Gen<T> {
    if (gens.isEmpty()) throw OneOfEmpty()
    return int(0..<gens.size).flatMap { gens[it] }
}

// shrinks toward the first value
fun <T> Gen.Companion.of(values: Collection<T>): Gen<T> = Gen.oneOf(values.map(::constant))

@JvmName("ofComparable")
// shrinks toward the smallest value
fun <T : Comparable<T>> Gen.Companion.of(values: Collection<T>): Gen<T> = Gen.oneOf(values.sorted().map(::constant))

class OneOfEmpty : IllegalStateException("Gen.oneOf() called with no generators")

class ExceptionLimitReached(threshold: Int, override val cause: Throwable) :
    IllegalStateException("Gen.ignoreExceptions() exceeded the threshold of $threshold exceptions")
