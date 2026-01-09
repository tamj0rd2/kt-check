package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.contract.IGen
import com.tamj0rd2.ktcheck.producer.ProducerTree
import com.tamj0rd2.ktcheck.producer.Seed
import kotlin.random.Random
import kotlin.random.nextInt

internal data class GenContext(
    val producer: ValueProducer,
)

interface ValueProducer {
    fun int(range: IntRange): Int
    fun bool(): Boolean
}

class RandomValueProducer internal constructor(seed: Seed) : ValueProducer {
    private val random: Random = Random(seed.value)

    override fun int(range: IntRange): Int = random.nextInt(range)

    override fun bool(): Boolean = random.nextBoolean()
}

/**
 * A generator that can produce values of type T.
 *
 * @param T The type of values produced by this generator.
 */
sealed class Gen<T> : IGen<T> {
    internal abstract fun GenContext.generate(): GenResult<T>

    internal fun generate(producer: ValueProducer): GenResult<T> = GenContext(producer).generate()

    override fun <R> map(fn: (T) -> R): Gen<R> = BasicGenerator { generate().map(fn) }

    override fun sample(seed: Long): T = generate(RandomValueProducer(Seed(seed))).value

    companion object
}

fun <T> Gen.Companion.constant(value: T): Gen<T> = BasicGenerator { GenResult(value, emptySequence()) }

/**
 * The result of generating a value from a generator, including the generated value and its shrinks.
 * Shrinks are represented as a sequence of [ProducerTree]s, allowing for lazy evaluation and efficient traversal.
 */
internal data class GenResult<T>(val value: T, val shrinks: Sequence<GenResult<T>>) {
    fun <R> map(fn: (T) -> R): GenResult<R> = GenResult(
        value = fn(value),
        shrinks = shrinks.map { it.map(fn) },
    )
}

private class BasicGenerator<T>(
    private val generateFn: GenContext.() -> GenResult<T>,
) : Gen<T>() {
    override fun GenContext.generate(): GenResult<T> = generateFn()
}
