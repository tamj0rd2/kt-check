package com.tamj0rd2.ktcheck

import com.tamj0rd2.ktcheck.gen.CombinerContext
import java.util.UUID
import kotlin.reflect.KClass

interface GenBuilder {
    fun <T> constant(value: T): Gen<T>

    fun bool(): Gen<Boolean>

    fun int(range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE): Gen<Int>

    fun long(range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE): Gen<Long>

    // todo: I can implement it here once I have a basic combinator generator
    fun uuid(): Gen<UUID>

    fun char(
        chars: Iterable<Char> = Char.MIN_VALUE..Char.MAX_VALUE,
    ): Gen<Char> = oneOf(chars.distinct().sorted())

    /** Shrinks towards the first generator */
    fun <T> oneOf(vararg gens: Gen<T>): Gen<T> = oneOf(gens.toList())

    /** Shrinks toward the first generator */
    fun <T> oneOf(gens: Collection<Gen<T>>): Gen<T>

    /** Shrinks toward the first value. Individual values will not be shrunk. */
    fun <T> oneOf(values: Iterable<T>): Gen<T> {
        val options = values.toList()
        if (options.isEmpty()) throw GenerationException.OneOfEmpty()
        return int(0..<options.size).map { options[it] }
    }

    // todo: at this point, some kind of builder would help with optional parameters
    fun <T> Gen<T>.list(size: IntRange = 0..100, distinct: Boolean = false): Gen<List<T>>

    fun <T> Gen<T>.list(size: Int, distinct: Boolean = false): Gen<List<T>> =
        list(size..size, distinct)

    fun <T> Gen<T>.set(size: IntRange = 0..100): Gen<Set<T>> =
        list(size, distinct = true).map { it.toSet() }

    fun <T> Gen<T>.set(size: Int): Gen<Set<T>> =
        set(size..size)

    fun Gen<Char>.string(size: IntRange): Gen<String>

    fun Gen<Char>.string(size: Int): Gen<String>

    fun <T> Gen<T>.filter(predicate: (T) -> Boolean): Gen<T> = filter(100, predicate)

    fun <T> Gen<T>.filter(threshold: Int, predicate: (T) -> Boolean): Gen<T>

    fun <T> Gen<T>.ignoreExceptions(klass: KClass<out Exception>, threshold: Int = 100): Gen<T>

    /**
     * Combines multiple generators into a single generator using a builder-style DSL.
     * Each generator in the block is bound sequentially, and their shrinks are combined.
     *
     * Example:
     * ```
     * val gen = Gen.combine {
     *     val x = Gen.int().bind()
     *     val y = Gen.int().bind()
     *     x to y
     * }
     * ```
     *
     * This is equivalent to using [plus] but with a more convenient syntax:
     * ```
     * val gen = (Gen.int() + Gen.bool()).map { (x, y) -> x + y }
     * ```
     *
     * **Warning about conditionals:** The combiner requires that bind functions will be called in the same order each time.
     * Conditionals that affect whether trailing [CombinerContext.bind] calls are called will shrink correctly.
     * However, conditionals that skip non-trailing [CombinerContext.bind] calls will cause invalid shrinks.
     */
    fun <T> combine(block: CombinerContext.() -> T): Gen<T>

    /**
     * Combines two independent generators into a single generator that produces a tuple of both values.
     * Shrinking is performed independently on each component.
     *
     * Example:
     * ```
     * // Gen<Pair<Int, Boolean>>
     * val gen2 = Gen.int() + Gen.boolean()
     * // Gen<Triple<Int, Boolean, String>>
     * val gen3 = Gen.int() + Gen.boolean() + Gen.string()
     * ```
     *
     * To combine more than 2 generators, use [com.tamj0rd2.ktcheck.gen.GenV1.Companion.combine] instead.
     *
     * For dependent generation (where the second generator depends on the first value),
     * use [flatMap] or [com.tamj0rd2.ktcheck.gen.GenV1.Companion.combine] instead.
     */
    infix operator fun <T1, T2> Gen<T1>.plus(nextGen: Gen<T2>): Gen<Pair<T1, T2>>
}
