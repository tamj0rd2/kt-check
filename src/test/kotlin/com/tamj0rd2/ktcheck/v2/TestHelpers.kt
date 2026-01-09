package com.tamj0rd2.ktcheck.v2

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
