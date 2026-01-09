package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.contract.IGen
import com.tamj0rd2.ktcheck.producer.ProducerTree
import com.tamj0rd2.ktcheck.producer.Seed
import kotlin.random.Random
import kotlin.random.nextInt

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
sealed class GenV2<T> : IGen<T> {
    internal abstract fun GenContextV2.generate(): GenResultV2<T>

    internal fun generate(producer: ValueProducerV2): GenResultV2<T> = GenContextV2(producer).generate()

    override fun <R> map(fn: (T) -> R): GenV2<R> = BasicGenerator { generate().map(fn) }

    override fun sample(seed: Long): T = generate(RandomValueProducerV2(Seed(seed))).value

    companion object
}

fun <T> GenV2.Companion.constant(value: T): GenV2<T> = BasicGenerator { GenResultV2(value, emptySequence()) }

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
