package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.GenBuilder
import com.tamj0rd2.ktcheck.Seed
import com.tamj0rd2.ktcheck.v1.CombinerContext
import com.tamj0rd2.ktcheck.v1.ProducerTree
import java.util.*
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.reflect.KClass

internal data class GenContextV2(
    val producer: ValueProducerV2,
)

interface ValueProducerV2 {
    fun int(range: IntRange): Int
    fun bool(): Boolean
}

class RandomValueProducerV2 internal constructor(seed: Seed) : ValueProducerV2 {
    private val random: Random = Random(seed.value)

    override fun int(range: IntRange): Int = random.nextInt(range)

    override fun bool(): Boolean = random.nextBoolean()
}

/**
 * A generator that can produce values of type T.
 *
 * @param T The type of values produced by this generator.
 */
sealed class GenV2<T> : Gen<T> {
    internal abstract fun GenContextV2.generate(): GenResultV2<T>

    internal fun generate(producer: ValueProducerV2): GenResultV2<T> = GenContextV2(producer).generate()

    override fun <R> map(fn: (T) -> R): GenV2<R> = BasicGenerator { generate().map(fn) }

    override fun sample(seed: Long): T = generate(RandomValueProducerV2(Seed(seed))).value

    internal companion object : GenBuilder {
        override fun <T> constant(value: T): GenV2<T> {
            return BasicGenerator { GenResultV2(value, emptySequence()) }
        }

        override fun bool(): GenV2<Boolean> {
            return BooleanGeneratorV2
        }

        override fun int(range: IntRange): GenV2<Int> {
            return IntGeneratorV2(range)
        }

        override fun long(range: IntRange): GenV2<Long> {
            // todo: implement this properly, and update to use LongRange.
            return int(range).map { it.toLong() }
        }

        override fun uuid(): GenV2<UUID> {
            TODO("Not yet implemented")
        }

        override fun <T> oneOf(gens: Collection<Gen<T>>): GenV2<T> {
            return OneOfGeneratorV2(gens.toList().map { it as GenV2<T> })
        }

        override fun <T> Gen<T>.list(
            size: IntRange,
            distinct: Boolean,
        ): GenV2<List<T>> {
            return ListGeneratorV2(
                gen = this as GenV2<T>,
                sizeRange = size,
                distinct = distinct
            )
        }

        override fun Gen<Char>.string(size: IntRange): GenV2<String> {
            TODO("Not yet implemented")
        }

        override fun Gen<Char>.string(size: Int): GenV2<String> {
            TODO("Not yet implemented")
        }

        override fun <T> Gen<T>.filter(
            threshold: Int,
            predicate: (T) -> Boolean,
        ): GenV2<T> {
            return FilterGeneratorV2(gen = this as GenV2<T>, threshold = threshold, predicate = predicate)
        }

        override fun <T> Gen<T>.ignoreExceptions(klass: KClass<out Exception>, threshold: Int): Gen<T> {
            TODO("Not yet implemented")
        }

        override fun <T> combine(block: CombinerContext.() -> T): GenV2<T> {
            TODO("Not yet implemented")
        }

        override fun <T1, T2> Gen<T1>.plus(nextGen: Gen<T2>): Gen<Pair<T1, T2>> {
            TODO("Not yet implemented")
        }
    }
}

/**
 * The result of generating a value from a generator, including the generated value and its shrinks.
 * Shrinks are represented as a sequence of [ProducerTree]s, allowing for lazy evaluation and efficient traversal.
 */
internal data class GenResultV2<T>(val value: T, val shrinks: Sequence<GenResultV2<T>>) {
    fun <R> map(fn: (T) -> R): GenResultV2<R> = GenResultV2(
        value = fn(value),
        shrinks = shrinks.map { it.map(fn) },
    )
}

private class BasicGenerator<T>(
    private val generateFn: GenContextV2.() -> GenResultV2<T>,
) : GenV2<T>() {
    override fun GenContextV2.generate(): GenResultV2<T> = generateFn()
}
