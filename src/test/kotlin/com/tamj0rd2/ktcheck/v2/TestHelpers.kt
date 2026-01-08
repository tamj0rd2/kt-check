package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.producer.Seed

internal class StubValueProducer(
    values: List<Any>,
    private val delegate: ValueProducer? = null,
) : ValueProducer {

    private val iterator = values.iterator()

    override fun int(range: IntRange): Int {
        if (!iterator.hasNext()) return delegate?.int(range) ?: error("StubProducer exhausted and no delegate provided")

        val value = iterator.next() as Int
        require(value in range) { "StubProducer produced value $value which is out of range $range" }
        return value
    }

    override fun bool(): Boolean {
        if (!iterator.hasNext()) return delegate?.bool() ?: error("StubProducer exhausted and no delegate provided")

        return iterator.next() as Boolean
    }
}

internal fun <T> Gen<T>.generateWithShrunkValues(seed: Seed) =
    generateWithShrunkValues(RandomValueProducer(seed))

internal fun <T> Gen<T>.generateWithShrunkValues(
    producer: ValueProducer = RandomValueProducer(Seed.random()),
): Pair<T, List<T>> {
    val (value, shrinks) = generate(producer)
    return value to shrinks.map { it.value }.toList()
}
